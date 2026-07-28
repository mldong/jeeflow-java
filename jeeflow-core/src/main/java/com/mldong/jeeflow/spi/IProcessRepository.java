package com.mldong.jeeflow.spi;

import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;

import java.util.List;

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
    ProcessInstance findInstanceById(Long instanceId);
    void saveInstance(ProcessInstance instance);
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
}
