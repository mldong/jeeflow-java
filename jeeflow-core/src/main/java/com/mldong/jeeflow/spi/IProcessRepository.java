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

    void createCcInstance(Long instanceId, String creator, String... actorIds);
    void updateCcStatus(Long instanceId, String actorId);

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

    /** 我的抄送 */
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
