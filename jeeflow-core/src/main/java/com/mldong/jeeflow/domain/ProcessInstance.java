package com.mldong.jeeflow.domain;

import com.mldong.jeeflow.enums.ProcessInstanceStateEnum;
import com.mldong.jeeflow.enums.ProcessTaskStateEnum;
import com.mldong.jeeflow.enums.CountersignTypeEnum;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.JeeflowException;
import com.mldong.jeeflow.enums.WfErrEnum;
import com.mldong.jeeflow.model.NodeModel;
import com.mldong.jeeflow.model.ProcessModel;
import com.mldong.jeeflow.model.TaskModel;
import com.mldong.jeeflow.model.CustomModel;
import com.mldong.jeeflow.util.FlowUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 流程实例——DDD 聚合根（充血模型）
 *
 * <p>流程实例是一个独立的事务边界，包含所有子任务。
 * 所有状态修改都通过聚合根的方法完成，外部不直接操作子实体。</p>
 *
 * @author mldong
 */
public class ProcessInstance {

    private Long instanceId;
    private Long parentId;
    private Long defineId;
    private Integer state;
    private String parentNodeName;
    private String businessNo;
    private String operator;       // 发起人
    private LocalDateTime expireTime;
    private FlowData variables = FlowData.create();
    private List<ProcessTask> tasks = new ArrayList<>();
    private LocalDateTime createTime;
    private String createUser;
    private LocalDateTime updateTime;
    private String updateUser;

    // ═══ 内嵌值对象：流程定义 ═══

    public static class ProcessDefine {
        private Long id;
        private String name;
        private String displayName;
        private String type;
        private Integer state;
        private byte[] content;
        private Integer version;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Integer getState() { return state; }
        public void setState(Integer state) { this.state = state; }
        public byte[] getContent() { return content; }
        public void setContent(byte[] content) { this.content = content; }
        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }

        private String updateUser;
        public String getUpdateUser() { return updateUser; }
        public void setUpdateUser(String updateUser) { this.updateUser = updateUser; }
    }

    // ═══ 工厂方法 ═══

    public static ProcessInstance create(ProcessDefine define, String operator, FlowData args) {
        return create(define, operator, args, null, null);
    }

    public static ProcessInstance create(ProcessDefine define, String operator, FlowData args,
                                          Long parentId, String parentNodeName) {
        ProcessInstance instance = new ProcessInstance();
        instance.parentId = parentId;
        instance.parentNodeName = parentNodeName;
        instance.defineId = define.getId();
        instance.operator = operator;
        instance.state = ProcessInstanceStateEnum.DOING.getCode();
        instance.businessNo = args != null ? args.getStr(FlowConst.BUSINESS_NO) : null;
        instance.variables = args != null ? args.copy() : FlowData.create();
        instance.tasks = new ArrayList<>();
        instance.createTime = LocalDateTime.now();
        instance.createUser = operator;
        instance.updateTime = LocalDateTime.now();
        instance.updateUser = operator;
        return instance;
    }

    // ═══ 命令方法 ═══

    /** 完成指定任务 */
    public void completeTask(Long taskId, String operator, FlowData args) {
        ProcessTask task = findDoingTask(taskId);
        task.finish(operator, args);
        if (args != null) {
            this.variables.putAll(args);
            // 提取 f_ 前缀变量持久化到流程变量
            FlowData formData = FlowData.create();
            for (String key : args.keySet()) {
                if (key.startsWith(FlowConst.FORM_DATA_PREFIX)) {
                    formData.put(key, args.get(key));
                }
            }
            if (!formData.isEmpty()) {
                this.addVariable(formData);
            }
        }
    }

    /** 废弃指定任务 → 流程实例也废弃 */
    public void abandonTask(Long taskId, String operator) {
        ProcessTask task = findDoingTask(taskId);
        task.abandon(operator);
        this.state = ProcessInstanceStateEnum.ABANDON.getCode();
        this.updateTime = LocalDateTime.now();
        this.updateUser = operator;
    }

    /** 流程完成 */
    public void finish() {
        this.state = ProcessInstanceStateEnum.FINISHED.getCode();
        this.updateTime = LocalDateTime.now();
    }

    /** 流程拒绝 */
    public void reject() {
        this.state = ProcessInstanceStateEnum.REJECT.getCode();
        this.updateTime = LocalDateTime.now();
    }

    /** 强行终止 */
    public void interrupt(String operator) {
        for (ProcessTask task : tasks) {
            task.interrupt(operator);
        }
        this.state = ProcessInstanceStateEnum.INTERRUPT.getCode();
        this.updateTime = LocalDateTime.now();
        this.updateUser = operator;
    }

    /** 唤醒 */
    public void resume(String operator) {
        for (ProcessTask task : tasks) {
            task.resume(operator);
        }
        this.state = ProcessInstanceStateEnum.DOING.getCode();
        this.updateTime = LocalDateTime.now();
        this.updateUser = operator;
    }

    /** 挂起 */
    public void pending(String operator) {
        for (ProcessTask task : tasks) {
            task.pending(operator);
        }
        this.state = ProcessInstanceStateEnum.PENDING.getCode();
        this.updateTime = LocalDateTime.now();
        this.updateUser = operator;
    }

    /** 激活 */
    public void activate(String operator) {
        for (ProcessTask task : tasks) {
            task.resume(operator);
        }
        this.state = ProcessInstanceStateEnum.DOING.getCode();
        this.updateTime = LocalDateTime.now();
        this.updateUser = operator;
    }

    /** 撤回（issues/114）：作用于整单——全部**进行中**任务置 WITHDRAW(30)，
     *  已完成(20)/已终止(40) 任务行不改写；实例与被撤任务的 `update_user` 均回写撤回人 */
    public void withdraw(String operator) {
        for (ProcessTask task : tasks) {
            if (task.isDoing()) {
                task.withdraw(operator);
            }
        }
        this.state = ProcessInstanceStateEnum.WITHDRAW.getCode();
        this.updateTime = LocalDateTime.now();
        this.updateUser = operator;
    }

    /** 追加变量 */
    public void addVariable(FlowData args) {
        this.variables.putAll(args);
        this.updateTime = LocalDateTime.now();
    }

    /** 移除变量 */
    public void removeVariable(String... keys) {
        for (String key : keys) {
            this.variables.remove(key);
        }
        this.updateTime = LocalDateTime.now();
    }

    /** 创建普通任务 */
    public ProcessTask createTask(TaskModel taskModel, String displayName,
                                   List<String> actorIds, String operator,
                                   Long parentTaskId, boolean isFirstTaskNode) {
        ProcessTask task = ProcessTask.create(
                this.instanceId,
                taskModel.getName(),
                displayName,
                taskModel.getTaskType(),
                taskModel.getPerformType(),
                taskModel.getForm(),
                actorIds,
                operator,
                parentTaskId,
                isFirstTaskNode
        );
        if (taskModel.getExpireTime() != null) {
            task.setExpireTime(LocalDateTime.now()); // will be overridden by util later
        }
        this.tasks.add(task);
        return task;
    }

    /** 创建会签任务 */
    public List<ProcessTask> createCountersignTasks(TaskModel taskModel, List<String> actorIds,
                                                     String operator, Long parentTaskId,
                                                     boolean isFirstTaskNode) {
        List<ProcessTask> list = new ArrayList<>();
        // 串行会签（issues/93）：仅创建第一位成员任务，并把会签计数状态写入该任务变量
        // （operatorList_{node} 全量办理人 / loopCounter_{node} 当前序号 / nrOfInstances_{node} 总数），
        // 由 CountersignHandler 在每位成员完成时推进创建下一位——任意时刻仅 1 个 DOING 会签任务，
        // 对齐 mldong 内置引擎 createCountersignTask 与 Go/Python/Node 的串行逐个创建。
        // PARALLEL / 未配置类型保持全员预创建。
        if (CountersignTypeEnum.SEQUENTIAL.equals(taskModel.getCountersignType())) {
            String node = taskModel.getName();
            ProcessTask first = ProcessTask.create(
                    this.instanceId,
                    node,
                    taskModel.getDisplayName(),
                    taskModel.getTaskType(),
                    taskModel.getPerformType(),
                    taskModel.getForm(),
                    new ArrayList<>(java.util.Collections.singletonList(actorIds.get(0))),
                    operator,
                    parentTaskId,
                    isFirstTaskNode
            );
            first.getVariables().put(FlowConst.COUNTERSIGN_OPERATOR_LIST + "_" + node, new ArrayList<>(actorIds));
            first.getVariables().put(FlowConst.LOOP_COUNTER + "_" + node, 0);
            first.getVariables().put(FlowConst.NR_OF_INSTANCES + "_" + node, actorIds.size());
            list.add(first);
            this.tasks.add(first);
            return list;
        }
        for (int i = 0; i < actorIds.size(); i++) {
            String actorId = actorIds.get(i);
            ProcessTask task = ProcessTask.create(
                    this.instanceId,
                    taskModel.getName(),
                    taskModel.getDisplayName(),
                    taskModel.getTaskType(),
                    taskModel.getPerformType(),
                    taskModel.getForm(),
                    new ArrayList<>(java.util.Collections.singletonList(actorId)),
                    operator,
                    parentTaskId,
                    isFirstTaskNode
            );
            list.add(task);
            this.tasks.add(task);
        }
        return list;
    }

    /**
     * 退回上一步（血缘版，规范 04 · 退回上一步）：上一步来源＝当前行的 task_parent_id，
     * 复活那条历史行，**不按模型入边拓扑推**（拓扑版在分支/回环流会回到本实例没走过的节点，
     * 且历史上还会在首任务节点上因 {@code (TaskModel) getNode("start")} 抛 ClassCastException）。
     *
     * @throws JeeflowException 20010007 无血缘（parent 为空或 0，含 P1 之前落的老行）；
     *                          20010008 canRejected 守卫不过（上一步是 fork/join/subprocess/会签）
     */
    public ProcessTask rejectTask(ProcessModel model, ProcessTask currentTask) {
        Long parentTaskId = currentTask.getParentTaskId();
        ProcessTask history = null;
        if (parentTaskId != null && parentTaskId != 0L) {
            for (ProcessTask t : this.tasks) {
                if (parentTaskId.equals(t.getTaskId())) {
                    history = t;
                    break;
                }
            }
        }
        if (history == null) {
            throw new JeeflowException(WfErrEnum.ROLLBACK_PARENT_TASK_ID_EMPTY);
        }
        NodeModel current = model.getNode(currentTask.getTaskName());
        NodeModel parent = model.getNode(history.getTaskName());
        if (current == null || parent == null || !NodeModel.canRejected(current, parent)) {
            throw new JeeflowException(WfErrEnum.ROLLBACK_PARENT_NOT_REJECTABLE);
        }

        FlowData vars = lineageVariables(history.getVariables());
        // 首任务节点那条不是"本人办的"（发起人提交），参与者取发起人快照。
        // 老行没有该键 ⇒ 按 false 处理：宁可派给该行 actorId，也不用带"仅进行中"判定的现算值。
        boolean isFirstRow = Boolean.TRUE.equals(vars.get(FlowConst.IS_FIRST_TASK_NODE));
        vars.put(FlowConst.IS_FIRST_TASK_NODE, isFirstRow);
        String operator = history.getActorId();
        if (isFirstRow) {
            Object uid = vars.get(FlowConst.USER_USER_ID);
            operator = uid != null ? String.valueOf(uid) : getOperator();
        }
        ProcessTask newTask = ProcessTask.create(
                this.instanceId,
                history.getTaskName(),
                history.getDisplayName(),
                history.getTaskType(),
                history.getPerformType(),
                history.getFormKey(),
                Collections.singletonList(operator),
                history.getCreateUser(),
                history.getParentTaskId(),   // 随行拷贝＝"上一步的上一步"，与 mldong-boot2 一致
                isFirstRow
        );
        newTask.setVariables(vars);
        // 到期时间按"被回退掉的那个"节点（＝当前节点）的表达式重算，同 mldong-boot2
        if (current instanceof TaskModel) {
            String expire = ((TaskModel) current).getExpireTime();
            if (expire != null && !expire.isEmpty()) {
                newTask.setExpireTime(FlowUtil.processTime(expire, vars));
            }
        }
        this.tasks.add(newTask);
        return newTask;
    }

    /**
     * 复活行的变量：只带数据类键。剔控制类残留是刻意为之——非必填字段第一次填了、第二次不填时，
     * 整包克隆会把上次提交值带进新待办，用户会看到"我没提交这个怎么显示了"；会签计数簿记
     * （loopCounter/nrOfInstances/operatorList）同理，留着会让复活的会签节点从错位的序号继续推进。
     * 保留 {@code f_*}、{@code u_*}、{@code autoGenTitle}、{@code isFirstTaskNode}。
     */
    private static FlowData lineageVariables(FlowData src) {
        FlowData out = FlowData.create();
        if (src == null) {
            return out;
        }
        for (java.util.Map.Entry<String, Object> e : src.entrySet()) {
            String k = e.getKey();
            if (k == null
                    || FlowConst.SUBMIT_TYPE.equals(k)
                    || "taskName".equals(k)
                    || k.startsWith(FlowConst.TASK_FORM_DATA_PREFIX)
                    || k.startsWith(FlowConst.COUNTERSIGN_VARIABLE_PREFIX)
                    || k.startsWith(FlowConst.LOOP_COUNTER)
                    || k.startsWith(FlowConst.NR_OF_INSTANCES)
                    || k.startsWith(FlowConst.COUNTERSIGN_OPERATOR_LIST)) {
                continue;
            }
            out.put(k, e.getValue());
        }
        return out;
    }

    /** 创建历史任务记录（自定义节点用） */
    public ProcessTask createHistoryTask(CustomModel customModel, String operator,
                                          Long parentTaskId, boolean isFirstTaskNode) {
        ProcessTask task = ProcessTask.create(
                this.instanceId,
                customModel.getName(),
                customModel.getDisplayName(),
                null,
                null,
                null,
                Collections.singletonList(operator),
                operator,
                parentTaskId,
                isFirstTaskNode
        );
        task.setTaskState(ProcessTaskStateEnum.FINISHED.getCode());
        this.tasks.add(task);
        return task;
    }

    // ═══ 查询方法 ═══

    public List<ProcessTask> getDoingTasks() {
        return tasks.stream().filter(ProcessTask::isDoing).collect(Collectors.toList());
    }

    public List<ProcessTask> getDoingTasks(String[] taskNames) {
        if (taskNames == null || taskNames.length == 0) return getDoingTasks();
        List<String> nameList = java.util.Arrays.asList(taskNames);
        return tasks.stream()
                .filter(t -> t.isDoing() && nameList.contains(t.getTaskName()))
                .collect(Collectors.toList());
    }

    public List<ProcessTask> getFinishedTasks() {
        return tasks.stream().filter(ProcessTask::isFinished).collect(Collectors.toList());
    }

    public List<ProcessTask> getDoneTasks(String[] taskNames) {
        if (taskNames == null || taskNames.length == 0) return getFinishedTasks();
        List<String> nameList = java.util.Arrays.asList(taskNames);
        return tasks.stream()
                .filter(t -> t.isFinished() && nameList.contains(t.getTaskName()))
                .collect(Collectors.toList());
    }

    public boolean isAllTasksFinished() {
        return tasks.stream().noneMatch(ProcessTask::isDoing);
    }

    public boolean isDoing() {
        return ProcessInstanceStateEnum.DOING.getCode().equals(this.state);
    }

    public boolean isFinished() {
        return ProcessInstanceStateEnum.FINISHED.getCode().equals(this.state);
    }

    private ProcessTask findDoingTask(Long taskId) {
        for (ProcessTask task : tasks) {
            if (taskId.equals(task.getTaskId())) {
                if (!task.isDoing()) {
                    throw new RuntimeException("任务[" + taskId + "]不是进行中状态");
                }
                return task;
            }
        }
        throw new RuntimeException("未找到任务[" + taskId + "]或不在聚合根中");
    }

    // ═══ getters/setters ═══

    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Long getDefineId() { return defineId; }
    public void setDefineId(Long defineId) { this.defineId = defineId; }
    public Integer getState() { return state; }
    public void setState(Integer state) { this.state = state; }
    public String getParentNodeName() { return parentNodeName; }
    public void setParentNodeName(String parentNodeName) { this.parentNodeName = parentNodeName; }
    public String getBusinessNo() { return businessNo; }
    public void setBusinessNo(String businessNo) { this.businessNo = businessNo; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }
    public FlowData getVariables() { return variables; }
    public void setVariables(FlowData variables) { this.variables = variables; }
    public List<ProcessTask> getTasks() { return tasks; }
    public void setTasks(List<ProcessTask> tasks) { this.tasks = tasks; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public String getCreateUser() { return createUser; }
    public void setCreateUser(String createUser) { this.createUser = createUser; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public String getUpdateUser() { return updateUser; }
    public void setUpdateUser(String updateUser) { this.updateUser = updateUser; }
}
