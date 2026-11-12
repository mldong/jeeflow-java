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
    /** 退回发起人后的重提方式；缺省 NORMAL */
    private com.mldong.jeeflow.enums.ReapplyModeEnum reapplyMode = com.mldong.jeeflow.enums.ReapplyModeEnum.NORMAL;
    /** 后加签开关；缺省 false（节点不可加签，行为与既有普通任务逐字一致） */
    private boolean allowAddSign;
    /** 后加签轮次上限；0＝不限（缺省） */
    private int addSignMaxRounds;
    /** 已办撤回开关（issues/246）；缺省 false；会签/申请节点解析期禁配（ModelParser） */
    private boolean allowRecall;
    /** 动态会签（征询模式）开关（issues/247）；缺省 false；与 allowAddSign 互斥（ModelParser 解析期报错） */
    private boolean dynamicCountersign;

    @Override
    public void exec(Execution execution) {
        if (ProcessTaskPerformTypeEnum.COUNTERSIGN.equals(performType)) {
            fire(new CountersignHandler(this), execution);
            if (execution.isMerged()) {
                abandonAidantOnLeave(execution);
                runOutTransition(execution);
            }
        } else if (allowAddSign) {
            // 后加签档：推进闸交给 AddSignHandler 裁决（本次点人→同节点续建且不流转；
            // 没点人但本节点还有兄弟待办→不流转；两者皆否→放行出边）。
            fire(new com.mldong.jeeflow.handler.impl.AddSignHandler(this), execution);
            if (execution.isMerged()) {
                abandonAidantOnLeave(execution);
                runOutTransition(execution);
            }
        } else if (dynamicCountersign) {
            // 征询档（issues/247）：发起腿在门面（带 tf_addSignOperators 的提交不办结主办行，
            // 到不了这里）；能走进执行闸的只有成员行办结与主办行收齐后的拍板——两者复用
            // AddSignHandler 推进闸：主办还 DOING ⇒ merged=false 节点不动；主办办结且成员清零 ⇒ 放行。
            fire(new com.mldong.jeeflow.handler.impl.AddSignHandler(this), execution);
            if (execution.isMerged()) {
                abandonAidantOnLeave(execution);
                runOutTransition(execution);
            }
        } else {
            abandonAidantOnLeave(execution);
            runOutTransition(execution);
        }
    }

    /**
     * 离开节点时清场未办结协办行（issues/253，规范 04「动态协办」）：{@code waitCompletion=false}
     * （缺省）时主办可随时办结推进，未办结协办行随废弃腿清场、账本记废弃，不留孤儿待办。
     *
     * <p>只在<b>真要离开本节点</b>（merged／普通档放行出边）时调用——串行会签/加签续建/征询未收齐
     * 那些"留在本节点"的分支不调，否则会把还在办的协办行提前废弃。幂等：已废弃行不再命中，
     * 会签档 {@code CountersignHandler} 先清过一遍也不会重复记账。</p>
     */
    private void abandonAidantOnLeave(Execution execution) {
        com.mldong.jeeflow.core.AidantSupport.abandonPendingOnNode(
                execution.getProcessInstance(), getName(), execution.getOperator());
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
    public com.mldong.jeeflow.enums.ReapplyModeEnum getReapplyMode() { return reapplyMode; }
    public void setReapplyMode(com.mldong.jeeflow.enums.ReapplyModeEnum reapplyMode) { this.reapplyMode = reapplyMode; }
    public boolean isAllowAddSign() { return allowAddSign; }
    public void setAllowAddSign(boolean allowAddSign) { this.allowAddSign = allowAddSign; }
    public int getAddSignMaxRounds() { return addSignMaxRounds; }
    public void setAddSignMaxRounds(int addSignMaxRounds) { this.addSignMaxRounds = addSignMaxRounds; }
    public boolean isAllowRecall() { return allowRecall; }
    public void setAllowRecall(boolean allowRecall) { this.allowRecall = allowRecall; }
    public boolean isDynamicCountersign() { return dynamicCountersign; }
    public void setDynamicCountersign(boolean dynamicCountersign) { this.dynamicCountersign = dynamicCountersign; }
}
