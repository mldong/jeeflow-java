package com.mldong.jeeflow.domain;

import com.mldong.jeeflow.enums.ProcessTaskStateEnum;
import com.mldong.jeeflow.enums.ProcessTaskTypeEnum;
import com.mldong.jeeflow.enums.ProcessTaskPerformTypeEnum;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 流程任务——聚合根 ProcessInstance 的子实体（充血模型）
 *
 * <p>任务自己知道如何完成、废弃、判断权限。</p>
 *
 * @author mldong
 */
public class ProcessTask {

    private Long taskId;
    private Long processInstanceId;
    private String taskName;
    private String displayName;
    private ProcessTaskTypeEnum taskType;
    private ProcessTaskPerformTypeEnum performType;
    private Integer taskState;
    private String actorId;          // 实际操作人
    private List<String> actorIds = new ArrayList<>(); // 参与者列表
    private LocalDateTime finishTime;
    private LocalDateTime expireTime;
    private String formKey;
    private Long parentTaskId;
    private FlowData variables = FlowData.create();
    private LocalDateTime createTime;
    private String createUser;
    private LocalDateTime updateTime;
    private String updateUser;

    // ═══ 工厂方法 ═══

    public static ProcessTask create(Long instanceId, String taskName, String displayName,
                                      ProcessTaskTypeEnum taskType, ProcessTaskPerformTypeEnum performType,
                                      String formKey, List<String> actorIds, String operator) {
        ProcessTask task = new ProcessTask();
        task.processInstanceId = instanceId;
        task.taskName = taskName;
        task.displayName = displayName;
        task.taskType = taskType;
        task.performType = performType;
        task.taskState = ProcessTaskStateEnum.DOING.getCode();
        task.formKey = formKey;
        task.actorIds = actorIds != null ? new ArrayList<>(actorIds) : new ArrayList<String>();
        task.variables = FlowData.create();
        task.createTime = LocalDateTime.now();
        task.createUser = operator;
        task.updateTime = LocalDateTime.now();
        task.updateUser = operator;
        return task;
    }

    // ═══ 命令方法（修改状态） ═══

    /** 完成任务 */
    public void finish(String operator, FlowData args) {
        if (!ProcessTaskStateEnum.DOING.getCode().equals(this.taskState)) {
            throw new RuntimeException("任务[" + taskName + "]不是进行中状态，无法完成");
        }
        if (!isAllowed(operator)) {
            throw new RuntimeException("操作人[" + operator + "]不在任务参与者列表中");
        }
        this.taskState = ProcessTaskStateEnum.FINISHED.getCode();
        this.actorId = operator;
        if (args != null) {
            this.variables.putAll(args);
        }
        this.finishTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
        this.updateUser = operator;
    }

    /** 废弃任务 */
    public void abandon(String operator) {
        if (!ProcessTaskStateEnum.DOING.getCode().equals(this.taskState)) {
            throw new RuntimeException("任务[" + taskName + "]不是进行中状态，无法废弃");
        }
        this.taskState = ProcessTaskStateEnum.ABANDON.getCode();
        this.updateTime = LocalDateTime.now();
        this.updateUser = operator;
    }

    /** 退回任务（恢复到进行中） */
    public void withdraw() {
        this.taskState = ProcessTaskStateEnum.WITHDRAW.getCode();
    }

    /** 强行终止 */
    public void interrupt(String operator) {
        if (ProcessTaskStateEnum.DOING.getCode().equals(this.taskState)) {
            this.taskState = ProcessTaskStateEnum.INTERRUPT.getCode();
            this.updateTime = LocalDateTime.now();
            this.updateUser = operator;
        }
    }

    /** 挂起 */
    public void pending(String operator) {
        if (ProcessTaskStateEnum.DOING.getCode().equals(this.taskState)) {
            this.taskState = ProcessTaskStateEnum.PENDING.getCode();
            this.updateTime = LocalDateTime.now();
            this.updateUser = operator;
        }
    }

    /** 唤醒（挂起恢复） */
    public void resume(String operator) {
        if (ProcessTaskStateEnum.INTERRUPT.getCode().equals(this.taskState)) {
            this.taskState = ProcessTaskStateEnum.DOING.getCode();
            this.updateTime = LocalDateTime.now();
            this.updateUser = operator;
        }
    }

    // ═══ 查询方法（不修改状态） ═══

    public boolean isDoing() {
        return ProcessTaskStateEnum.DOING.getCode().equals(this.taskState);
    }

    public boolean isFinished() {
        return ProcessTaskStateEnum.FINISHED.getCode().equals(this.taskState);
    }

    public boolean isAllowed(String operator) {
        return isDoing() && this.actorIds != null && this.actorIds.contains(operator);
    }

    // ═══ getters/setters ═══

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(Long processInstanceId) { this.processInstanceId = processInstanceId; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public ProcessTaskTypeEnum getTaskType() { return taskType; }
    public void setTaskType(ProcessTaskTypeEnum taskType) { this.taskType = taskType; }
    public ProcessTaskPerformTypeEnum getPerformType() { return performType; }
    public void setPerformType(ProcessTaskPerformTypeEnum performType) { this.performType = performType; }
    public Integer getTaskState() { return taskState; }
    public void setTaskState(Integer taskState) { this.taskState = taskState; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public List<String> getActorIds() { return actorIds; }
    public void setActorIds(List<String> actorIds) { this.actorIds = actorIds; }
    public LocalDateTime getFinishTime() { return finishTime; }
    public void setFinishTime(LocalDateTime finishTime) { this.finishTime = finishTime; }
    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }
    public String getFormKey() { return formKey; }
    public void setFormKey(String formKey) { this.formKey = formKey; }
    public Long getParentTaskId() { return parentTaskId; }
    public void setParentTaskId(Long parentTaskId) { this.parentTaskId = parentTaskId; }
    public FlowData getVariables() { return variables; }
    public void setVariables(FlowData variables) { this.variables = variables; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public String getCreateUser() { return createUser; }
    public void setCreateUser(String createUser) { this.createUser = createUser; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public String getUpdateUser() { return updateUser; }
    public void setUpdateUser(String updateUser) { this.updateUser = updateUser; }
}
