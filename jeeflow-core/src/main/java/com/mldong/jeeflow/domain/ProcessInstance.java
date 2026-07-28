package com.mldong.jeeflow.domain;

import com.mldong.jeeflow.enums.ProcessInstanceStateEnum;
import com.mldong.jeeflow.enums.ProcessTaskStateEnum;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.model.ProcessModel;
import com.mldong.jeeflow.model.TaskModel;
import com.mldong.jeeflow.model.CustomModel;

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

    /** 撤回 */
    public void withdraw(String operator) {
        for (ProcessTask task : tasks) {
            if (task.isDoing()) {
                task.withdraw();
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
                                   List<String> actorIds, String operator) {
        ProcessTask task = ProcessTask.create(
                this.instanceId,
                taskModel.getName(),
                displayName,
                taskModel.getTaskType(),
                taskModel.getPerformType(),
                taskModel.getForm(),
                actorIds,
                operator
        );
        if (taskModel.getExpireTime() != null) {
            task.setExpireTime(LocalDateTime.now()); // will be overridden by util later
        }
        this.tasks.add(task);
        return task;
    }

    /** 创建会签任务 */
    public List<ProcessTask> createCountersignTasks(TaskModel taskModel, List<String> actorIds,
                                                     String operator) {
        List<ProcessTask> list = new ArrayList<>();
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
                    operator
            );
            list.add(task);
            this.tasks.add(task);
        }
        return list;
    }

    /** 驳回任务（退回上一步） */
    public ProcessTask rejectTask(ProcessModel model, ProcessTask currentTask) {
        // 找到上一个任务节点并创建新任务
        String previousTaskName = getPreviousTaskName(model, currentTask.getTaskName());
        if (previousTaskName != null) {
            TaskModel prevModel = (TaskModel) model.getNode(previousTaskName);
            ProcessTask newTask = ProcessTask.create(
                    this.instanceId,
                    prevModel.getName(),
                    prevModel.getDisplayName(),
                    prevModel.getTaskType(),
                    prevModel.getPerformType(),
                    prevModel.getForm(),
                    Collections.singletonList(currentTask.getActorId()),
                    currentTask.getCreateUser()
            );
            this.tasks.add(newTask);
            return newTask;
        }
        return null;
    }

    /** 创建历史任务记录（自定义节点用） */
    public ProcessTask createHistoryTask(CustomModel customModel, String operator) {
        ProcessTask task = ProcessTask.create(
                this.instanceId,
                customModel.getName(),
                customModel.getDisplayName(),
                null,
                null,
                null,
                Collections.singletonList(operator),
                operator
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

    private String getPreviousTaskName(ProcessModel model, String currentTaskName) {
        // 简单实现：找输入边的 source 节点
        com.mldong.jeeflow.model.NodeModel node = model.getNode(currentTaskName);
        if (node != null && !node.getInputs().isEmpty()) {
            return node.getInputs().get(0).getSource().getName();
        }
        return null;
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
