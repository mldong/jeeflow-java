package com.mldong.jeeflow.spi;

import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 聚合仓储 SPI——集成方实现此接口以对接持久层
 *
 * @author mldong
 */
public interface IProcessRepository {

    // ═══════════════════════════════════════
    // 引擎运行时方法
    // ═══════════════════════════════════════

    ProcessInstance.ProcessDefine findDefineById(Long defineId);

    // ── 流程定义写操作（v1.0.1 新增：设计器部署/启停/删除）──
    /** 新增流程定义（id 为空时由实现生成） */
    void saveDefine(ProcessInstance.ProcessDefine define);
    /** 更新流程定义（name/displayName/type/state/content/version 等） */
    void updateDefine(ProcessInstance.ProcessDefine define);
    /** 启用/禁用流程定义（state: 1 可用 / 0 不可用） */
    void updateDefineState(Long defineId, int state);
    /** 删除流程定义 */
    void removeDefine(Long defineId);

    ProcessInstance findInstanceById(Long instanceId);
    void saveInstance(ProcessInstance instance);
    /** 更新实例并**级联持久化聚合内任务状态**（撤回/挂起/激活等变更随同落库，v1.0.1） */
    void updateInstance(ProcessInstance instance);

    ProcessTask findTaskById(Long taskId);
    void saveTask(ProcessTask task);
    void updateTask(ProcessTask task);

    List<ProcessTask> findDoingTasks(Long instanceId, String[] taskNames);
    List<ProcessTask> findDoneTasks(Long instanceId, String[] taskNames);
    List<ProcessTask> findHistoryTasks(Long instanceId);

    /**
     * 建 cc 行的最底层写入口（{@code wf_process_cc_instance}）。
     *
     * <p>issues/141 G10「空不创建行」（spec 06 §2.10）：入参里的<b>空串、纯空白、{@code null}
     * 一律丢弃</b>，落库值取 trim 后的串。判据要落在这一层而不只落在引擎漏斗里——
     * 绕过 {@code handleCcActors} 直连仓储的调用方（集成层、第三方仓储消费者）同样不得
     * 把空归属值灌进 {@code actor_id}，那正是 issues/129 那族"空 operator 读全库"的病根。</p>
     */
    void createCcInstance(Long instanceId, String creator, String... actorIds);
    void updateCcStatus(Long instanceId, String actorId);

    /**
     * issues/141 G2 写侧判重（spec 06 §4「抄送写侧判重＝幂等空操作」）：读某实例<b>已存在</b>的
     * cc 行 actor id，供建 cc 的三条入口（发起 {@code f_ccActors}／办理 {@code tf_ccActors}／
     * 门面手动 {@code createCCInstance}）判重用。
     *
     * <p>default 返回空集＝不判重，未覆写的第三方仓储维持旧行为（全量建行、全量 fire），
     * SPI 源码兼容不破。jeeflow 自带的两仓（JDBC 仓 / 内存仓）<b>必须</b>覆写：
     * 否则 issues/141 G1 那条「同一栈 SQL 仓与内存仓两个答案」的分叉在写侧重演一遍。</p>
     */
    default List<String> findCcActorIds(Long instanceId) {
        return java.util.Collections.emptyList();
    }

    /**
     * issues/141 G2：写侧幂等建 cc 行。同一 {@code (instanceId, actorId)} 已有 cc 行时<b>跳过</b>——
     * ①不新增行、②不重置未读状态（{@code state}）、③不更新原行时间，重复抄送同一个人
     * 在数据面上是 no-op（owner 2026-09-29 明确「不需要重置」，不产生"再提醒一次"语义）；
     * 返回<b>实际新建</b>的 actor 子集（顺序与入参一致，同一次调用内的重复也折叠）。
     *
     * <p>为什么要返回子集而不是 void：spec 11.2 原则 1「码值表达发生了什么事实」⇒
     * 没发生"创建"就不得 fire {@code CC_CREATE}（码 4）。逐人 fire 的入参一律换成这个子集，
     * 子集为空则整支不 fire（见 {@code ProcessPublisher#notifyCcCreate} 的两个调用点）。</p>
     *
     * <p>未覆写 {@link #findCcActorIds} 的第三方仓储走本 default ⇒ 与旧
     * {@code void createCcInstance} 逐字一致（全量插入、全量返回），不静默改变既有集成方行为。</p>
     */
    default List<String> createCcInstanceIfAbsent(Long instanceId, String creator, String... actorIds) {
        List<String> existing = findCcActorIds(instanceId);
        List<String> fresh = new java.util.ArrayList<>();
        // issues/141 G10「空不创建行」（spec 06 §2.10）：先过归一腿——空串/纯空白/null 丢弃，
        // 值取 trim 后的串（" 123 " 与 "123" 是同一个人，也才与上面的判重咬合）。
        for (String actorId : com.mldong.jeeflow.util.StringUtils.normalizeCcActors(actorIds)) {
            if (existing != null && existing.contains(actorId)) continue;
            if (!fresh.contains(actorId)) fresh.add(actorId);
        }
        if (!fresh.isEmpty()) {
            createCcInstance(instanceId, creator, fresh.toArray(new String[0]));
        }
        return fresh;
    }

    List<String> findTaskActors(Long taskId);
    void addTaskActor(Long taskId, List<String> actors);
    void removeTaskActor(Long taskId, List<String> actors);

    // ═══════════════════════════════════════
    // 前端分页查询方法（jeeflow 2.0+）
    // ═══════════════════════════════════════

    /** 我的待办 */
    PageResult<TaskRow> pageTodoTasks(PageQuery query);

    /** 我的已办 */
    PageResult<TaskRow> pageDoneTasks(PageQuery query);

    /** 我发起的流程实例 */
    PageResult<InstanceRow> pageInstances(PageQuery query);

    /**
     * 我的抄送。
     *
     * <p><b>归属条件必填</b>（issues/141 G1 · spec 06 §2.5）：查询必须带 {@code cc.actor_id} 的
     * 有效归属条件；<b>条件缺失或为空值时返回空页</b>，严禁退化成"这条条件不加"而返回全部实例。
     * SQL 仓与内存仓在同一条判据上必须给同一个答案（issues/117 场景 27 那把尺子扩到 ccList）。</p>
     */
    PageResult<InstanceRow> pageCcInstances(PageQuery query);

    /** 流程定义分页 */
    PageResult<DefineRow> pageDefines(PageQuery query);

    /** 我的待办计数 */
    int countTodoTasks(Long userId);

    // ═══════════════════════════════════════
    // 统计查询方法（issues/103）
    // ═══════════════════════════════════════

    /** 查询实例列表（stats 用，轻量级 —— 不加载关联任务）。stateIn/timeField+start/end 均可空 */
    List<InstanceStatsRow> queryInstancesForStats(List<Integer> stateIn, String timeField, LocalDateTime start, LocalDateTime end);

    /** 查询任务列表（stats 用）。state/start/end 均可空 */
    List<TaskStatsRow> queryTasksForStats(Integer state, LocalDateTime start, LocalDateTime end);

    /** 已完成实例平均时长（秒）：MAX(task.finish_time) - instance.create_time，state=20 */
    int statsAvgCompletedDurationSeconds(LocalDateTime start, LocalDateTime end);

    /** 进行中任务数 + 逾期未办任务数 → [pending, overdue] */
    int[] statsPendingAndOverdueCount();

    /** 已完成任务聚合：[total, countersign(perform_type=1), onTime(finish<=expire且expire非空), onTimeDenom(expire非空)] */
    int[] statsCompletedTaskAggregate();

    /** 当前积压节点（task_state=10 按 display_name 分组）→ [{key, count}] 降序 limit N */
    List<Map<String, Object>> statsStuckNodeGroup(int limit);

    /** 当前积压人（task_state=10 经 actor 表展开）→ [{key, count}] 降序 limit N */
    List<Map<String, Object>> statsStuckApproverGroup(int limit);

    /** 按流程定义分组统计实例数量（时间范围内创建且状态为 10/20 的实例）→ [{key, label, count, avgDurationSeconds}] 降序 limit N */
    List<Map<String, Object>> statsDefineGroup(LocalDateTime start, LocalDateTime end, int limit);

    /** 已完成实例的耗时列表（秒），用于 Facade 层分桶。state=20，create_time 在 [start,end] 内 */
    List<Integer> statsCompletedInstanceDurations(LocalDateTime start, LocalDateTime end);

    // ═══════════════════════════════════════
    // 行数据传输对象
    // ═══════════════════════════════════════

    /** 任务行数据 */
    class TaskRow {
        private Long id;
        private Long processInstanceId;
        private String taskName;
        private String displayName;
        private Integer taskType;
        private Integer performType;
        private Integer taskState;
        private String operator;
        private java.time.LocalDateTime finishTime;
        private java.time.LocalDateTime expireTime;
        private String formKey;
        private Long taskParentId;
        private String variable;
        private java.time.LocalDateTime createTime;
        private String createUser;
        private java.time.LocalDateTime updateTime;
        private String updateUser;
        // 关联字段
        private String processDefineName;
        private String processDefineDisplayName;
        private Integer processDefineVersion;
        private String instanceVariable;
        private java.time.LocalDateTime instanceCreateTime;

        public Long getId() { return id; } public void setId(Long id) { this.id = id; }
        public Long getProcessInstanceId() { return processInstanceId; } public void setProcessInstanceId(Long v) { this.processInstanceId = v; }
        public String getTaskName() { return taskName; } public void setTaskName(String v) { this.taskName = v; }
        public String getDisplayName() { return displayName; } public void setDisplayName(String v) { this.displayName = v; }
        public Integer getTaskType() { return taskType; } public void setTaskType(Integer v) { this.taskType = v; }
        public Integer getPerformType() { return performType; } public void setPerformType(Integer v) { this.performType = v; }
        public Integer getTaskState() { return taskState; } public void setTaskState(Integer v) { this.taskState = v; }
        public String getOperator() { return operator; } public void setOperator(String v) { this.operator = v; }
        public java.time.LocalDateTime getFinishTime() { return finishTime; } public void setFinishTime(java.time.LocalDateTime v) { this.finishTime = v; }
        public java.time.LocalDateTime getExpireTime() { return expireTime; } public void setExpireTime(java.time.LocalDateTime v) { this.expireTime = v; }
        public String getFormKey() { return formKey; } public void setFormKey(String v) { this.formKey = v; }
        public Long getTaskParentId() { return taskParentId; } public void setTaskParentId(Long v) { this.taskParentId = v; }
        public String getVariable() { return variable; } public void setVariable(String v) { this.variable = v; }
        public java.time.LocalDateTime getCreateTime() { return createTime; } public void setCreateTime(java.time.LocalDateTime v) { this.createTime = v; }
        public String getCreateUser() { return createUser; } public void setCreateUser(String v) { this.createUser = v; }
        public java.time.LocalDateTime getUpdateTime() { return updateTime; } public void setUpdateTime(java.time.LocalDateTime v) { this.updateTime = v; }
        public String getUpdateUser() { return updateUser; } public void setUpdateUser(String v) { this.updateUser = v; }
        public String getProcessDefineName() { return processDefineName; } public void setProcessDefineName(String v) { this.processDefineName = v; }
        public String getProcessDefineDisplayName() { return processDefineDisplayName; } public void setProcessDefineDisplayName(String v) { this.processDefineDisplayName = v; }
        public Integer getProcessDefineVersion() { return processDefineVersion; } public void setProcessDefineVersion(Integer v) { this.processDefineVersion = v; }
        public String getInstanceVariable() { return instanceVariable; } public void setInstanceVariable(String v) { this.instanceVariable = v; }
        public java.time.LocalDateTime getInstanceCreateTime() { return instanceCreateTime; } public void setInstanceCreateTime(java.time.LocalDateTime v) { this.instanceCreateTime = v; }
    }

    /** 实例行数据 */
    class InstanceRow {
        private Long id;
        private Long parentId;
        private Long processDefineId;
        private Integer state;
        private String parentNodeName;
        private String businessNo;
        private String operator;
        private java.time.LocalDateTime expireTime;
        private String variable;
        private java.time.LocalDateTime createTime;
        private String createUser;
        private java.time.LocalDateTime updateTime;
        private String updateUser;
        // 关联字段
        private String processDefineName;
        private String processDefineDisplayName;
        private Integer processDefineVersion;

        public Long getId() { return id; } public void setId(Long id) { this.id = id; }
        public Long getParentId() { return parentId; } public void setParentId(Long v) { this.parentId = v; }
        public Long getProcessDefineId() { return processDefineId; } public void setProcessDefineId(Long v) { this.processDefineId = v; }
        public Integer getState() { return state; } public void setState(Integer v) { this.state = v; }
        public String getParentNodeName() { return parentNodeName; } public void setParentNodeName(String v) { this.parentNodeName = v; }
        public String getBusinessNo() { return businessNo; } public void setBusinessNo(String v) { this.businessNo = v; }
        public String getOperator() { return operator; } public void setOperator(String v) { this.operator = v; }
        public java.time.LocalDateTime getExpireTime() { return expireTime; } public void setExpireTime(java.time.LocalDateTime v) { this.expireTime = v; }
        public String getVariable() { return variable; } public void setVariable(String v) { this.variable = v; }
        public java.time.LocalDateTime getCreateTime() { return createTime; } public void setCreateTime(java.time.LocalDateTime v) { this.createTime = v; }
        public String getCreateUser() { return createUser; } public void setCreateUser(String v) { this.createUser = v; }
        public java.time.LocalDateTime getUpdateTime() { return updateTime; } public void setUpdateTime(java.time.LocalDateTime v) { this.updateTime = v; }
        public String getUpdateUser() { return updateUser; } public void setUpdateUser(String v) { this.updateUser = v; }
        public String getProcessDefineName() { return processDefineName; } public void setProcessDefineName(String v) { this.processDefineName = v; }
        public String getProcessDefineDisplayName() { return processDefineDisplayName; } public void setProcessDefineDisplayName(String v) { this.processDefineDisplayName = v; }
        public Integer getProcessDefineVersion() { return processDefineVersion; } public void setProcessDefineVersion(Integer v) { this.processDefineVersion = v; }
    }

    /** 定义行数据 */
    class DefineRow {
        private Long id;
        private String name;
        private String displayName;
        private String type;
        private Integer state;
        private Integer version;
        private java.time.LocalDateTime createTime;
        private String createUser;
        private java.time.LocalDateTime updateTime;
        private String updateUser;

        public Long getId() { return id; } public void setId(Long id) { this.id = id; }
        public String getName() { return name; } public void setName(String v) { this.name = v; }
        public String getDisplayName() { return displayName; } public void setDisplayName(String v) { this.displayName = v; }
        public String getType() { return type; } public void setType(String v) { this.type = v; }
        public Integer getState() { return state; } public void setState(Integer v) { this.state = v; }
        public Integer getVersion() { return version; } public void setVersion(Integer v) { this.version = v; }
        public java.time.LocalDateTime getCreateTime() { return createTime; } public void setCreateTime(java.time.LocalDateTime v) { this.createTime = v; }
        public String getCreateUser() { return createUser; } public void setCreateUser(String v) { this.createUser = v; }
        public java.time.LocalDateTime getUpdateTime() { return updateTime; } public void setUpdateTime(java.time.LocalDateTime v) { this.updateTime = v; }
        public String getUpdateUser() { return updateUser; } public void setUpdateUser(String v) { this.updateUser = v; }
    }

    /** 实例统计行（轻量级，不加载关联任务） */
    class InstanceStatsRow {
        private Long id;
        private Integer state;
        private LocalDateTime createTime;
        private Long processDefineId;
        private String operator;

        public Long getId() { return id; } public void setId(Long v) { this.id = v; }
        public Integer getState() { return state; } public void setState(Integer v) { this.state = v; }
        public LocalDateTime getCreateTime() { return createTime; } public void setCreateTime(LocalDateTime v) { this.createTime = v; }
        public Long getProcessDefineId() { return processDefineId; } public void setProcessDefineId(Long v) { this.processDefineId = v; }
        public String getOperator() { return operator; } public void setOperator(String v) { this.operator = v; }
    }

    /** 任务统计行 */
    class TaskStatsRow {
        private Long id;
        private Long processInstanceId;
        private Integer taskState;
        private Integer performType;
        private String operator;
        private String displayName;
        private LocalDateTime createTime;
        private LocalDateTime finishTime;
        private LocalDateTime expireTime;

        public Long getId() { return id; } public void setId(Long v) { this.id = v; }
        public Long getProcessInstanceId() { return processInstanceId; } public void setProcessInstanceId(Long v) { this.processInstanceId = v; }
        public Integer getTaskState() { return taskState; } public void setTaskState(Integer v) { this.taskState = v; }
        public Integer getPerformType() { return performType; } public void setPerformType(Integer v) { this.performType = v; }
        public String getOperator() { return operator; } public void setOperator(String v) { this.operator = v; }
        public String getDisplayName() { return displayName; } public void setDisplayName(String v) { this.displayName = v; }
        public LocalDateTime getCreateTime() { return createTime; } public void setCreateTime(LocalDateTime v) { this.createTime = v; }
        public LocalDateTime getFinishTime() { return finishTime; } public void setFinishTime(LocalDateTime v) { this.finishTime = v; }
        public LocalDateTime getExpireTime() { return expireTime; } public void setExpireTime(LocalDateTime v) { this.expireTime = v; }
    }
}
