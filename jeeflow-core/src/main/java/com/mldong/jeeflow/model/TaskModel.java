package com.mldong.jeeflow.model;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.enums.ProcessTaskPerformTypeEnum;
import com.mldong.jeeflow.enums.ProcessTaskTypeEnum;
import com.mldong.jeeflow.enums.CountersignTypeEnum;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.handler.impl.CountersignHandler;

/**
 * 任务节点模型
 *
 * @author mldong
 */
public class TaskModel extends NodeModel {

    private String form;
    private String assignee;
    private String assignmentHandler;
    private ProcessTaskTypeEnum taskType;
    private ProcessTaskPerformTypeEnum performType;
    private String reminderTime;
    private String reminderRepeat;
    private String expireTime;
    private String autoExecute;
    private String callback;
    private FlowData ext = FlowData.create();
    private String candidateHandler;
    private CountersignTypeEnum countersignType;
    private String countersignCompletionCondition;

    @Override
    public void exec(Execution execution) {
        if (ProcessTaskPerformTypeEnum.COUNTERSIGN.equals(performType)) {
            fire(new CountersignHandler(this), execution);
            if (execution.isMerged()) {
                runOutTransition(execution);
            }
        } else {
            runOutTransition(execution);
        }
    }

    // ---- 便捷字段（从 ext 中读取） ----

    public String getCandidateUsers() {
        return ext != null ? ext.getStr("candidateUsers") : null;
    }

    public String getCandidateGroups() {
        return ext != null ? ext.getStr("candidateGroups") : null;
    }

    public String getCandidateHandler() {
        if (candidateHandler != null) return candidateHandler;
        return ext != null ? ext.getStr("candidateHandler") : null;
    }

    // ---- getters/setters ----

    public String getForm() { return form; }
    public void setForm(String form) { this.form = form; }
    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }
    public String getAssignmentHandler() { return assignmentHandler; }
    public void setAssignmentHandler(String assignmentHandler) { this.assignmentHandler = assignmentHandler; }
    public ProcessTaskTypeEnum getTaskType() { return taskType; }
    public void setTaskType(ProcessTaskTypeEnum taskType) { this.taskType = taskType; }
    public ProcessTaskPerformTypeEnum getPerformType() { return performType; }
    public void setPerformType(ProcessTaskPerformTypeEnum performType) { this.performType = performType; }
    public String getReminderTime() { return reminderTime; }
    public void setReminderTime(String reminderTime) { this.reminderTime = reminderTime; }
    public String getReminderRepeat() { return reminderRepeat; }
    public void setReminderRepeat(String reminderRepeat) { this.reminderRepeat = reminderRepeat; }
    public String getExpireTime() { return expireTime; }
    public void setExpireTime(String expireTime) { this.expireTime = expireTime; }
    public String getAutoExecute() { return autoExecute; }
    public void setAutoExecute(String autoExecute) { this.autoExecute = autoExecute; }
    public String getCallback() { return callback; }
    public void setCallback(String callback) { this.callback = callback; }
    public FlowData getExt() { return ext; }
    public void setExt(FlowData ext) { this.ext = ext; }
    public void setCandidateHandler(String candidateHandler) { this.candidateHandler = candidateHandler; }
    public CountersignTypeEnum getCountersignType() { return countersignType; }
    public void setCountersignType(CountersignTypeEnum countersignType) { this.countersignType = countersignType; }
    public String getCountersignCompletionCondition() { return countersignCompletionCondition; }
    public void setCountersignCompletionCondition(String countersignCompletionCondition) { this.countersignCompletionCondition = countersignCompletionCondition; }
}
