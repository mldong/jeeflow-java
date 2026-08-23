package com.mldong.jeeflow.handler.impl;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.CountersignTypeEnum;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
import com.mldong.jeeflow.enums.ProcessTaskStateEnum;
import com.mldong.jeeflow.handler.IHandler;
import com.mldong.jeeflow.model.TaskModel;
import com.mldong.jeeflow.spi.IExpressionEvaluator;
import com.mldong.jeeflow.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 会签任务处理器
 *
 * @author mldong
 */
public class CountersignHandler implements IHandler {

    private final TaskModel taskModel;

    public CountersignHandler(TaskModel taskModel) {
        this.taskModel = taskModel;
    }

    @Override
    public void handle(Execution execution) {
        ProcessInstance instance = execution.getProcessInstance();

        // 获取所有属于该节点的任务（包括 DOING 和 FINISHED）
        List<ProcessTask> allTasks = instance.getTasks()
                .stream()
                .filter(t -> taskModel.getName().equals(t.getTaskName()))
                .collect(Collectors.toList());

        long finishedCount = allTasks.stream()
                .filter(t -> ProcessTaskStateEnum.FINISHED.getCode().equals(t.getTaskState()))
                .count();

        // 会签拒绝（submitType=20）：仅当节点配置一票否决开关（countersignCompletionCondition
        // == ONE_VOTE_VETO，忽略大小写）时否决生效、会签节点立即推进；否则为软拒绝——
        // 否决者任务正常完成、countersignDisagreeFlag=1 已记录为变量（供下游节点参考），
        // 流程不阻断，继续走常规完成判定（issues/91，对齐 mldong 内置引擎）
        Integer submitType = execution.getArgs().getInt(FlowConst.SUBMIT_TYPE);
        if (ProcessSubmitTypeEnum.COUNTERSIGN_DISAGREE.getCode().equals(submitType)
                && isOneVoteVeto(taskModel.getCountersignCompletionCondition())) {
            execution.setMerged(true);
            abandonRemainingCountersignTasks(instance, execution.getOperator());
            return;
        }

        CountersignTypeEnum countersignType = taskModel.getCountersignType();

        boolean merged;
        if (CountersignTypeEnum.SEQUENTIAL.equals(countersignType)) {
            // 串行会签：全部成员完成才流转（issues/44 E16——此前无条件 merged 导致
            // 每次成员完成都重复流转后续节点）。对齐内置版"最后一人完成才 merged"
            // 与 Node/Go/Python"doing 空才流转"语义：流转时机 = 全部完成
            merged = finishedCount >= allTasks.size();
        } else {
            // 并行会签：检查条件
            String cond = taskModel.getCountersignCompletionCondition();
            if (StringUtils.isEmpty(cond)) {
                // 无特殊条件 → 全部完成
                merged = finishedCount >= allTasks.size();
            } else {
                IExpressionEvaluator evaluator = ServiceContext.find(IExpressionEvaluator.class);
                if (evaluator != null) {
                    FlowData vars = buildCountersignVars(instance, taskModel, allTasks);
                    vars.putAll(execution.getArgs());
                    try {
                        Object result = evaluator.eval(cond, vars);
                        merged = Boolean.TRUE.equals(result);
                    } catch (Exception ignored) {
                        merged = false;
                    }
                } else {
                    merged = false;
                }
            }
        }

        if (merged) {
            // 会签节点推进后废弃本节点剩余 DOING 会签任务（issues/91，对齐内置版
            // abandonProcessTask），不留孤儿待办
            abandonRemainingCountersignTasks(instance, execution.getOperator());
        }
        execution.setMerged(merged);
    }

    /** 一票否决开关：节点 countersignCompletionCondition == ONE_VOTE_VETO（忽略大小写） */
    private boolean isOneVoteVeto(String condition) {
        return condition != null && FlowConst.ONE_VOTE_VETO.equalsIgnoreCase(condition.trim());
    }

    /** 废弃本节点剩余 DOING 会签任务（merged 时调用）。
     *  刚完成的任务此刻已置 FINISHED，isDoing 过滤不会误伤 */
    private void abandonRemainingCountersignTasks(ProcessInstance instance, String operator) {
        for (ProcessTask task : instance.getTasks()) {
            if (taskModel.getName().equals(task.getTaskName()) && task.isDoing()) {
                task.abandon(operator);
            }
        }
    }

    private FlowData buildCountersignVars(ProcessInstance instance, TaskModel taskModel,
                                           List<ProcessTask> allTasks) {
        String prefix = FlowConst.COUNTERSIGN_VARIABLE_PREFIX + taskModel.getName() + "_";
        FlowData vars = FlowData.create();
        vars.setAll(instance.getVariables());
        vars.put(prefix + FlowConst.NR_OF_INSTANCES, allTasks.size());
        vars.put(prefix + FlowConst.NR_OF_ACTIVATE_INSTANCES,
                allTasks.stream().filter(ProcessTask::isDoing).count());
        vars.put(prefix + FlowConst.NR_OF_COMPLETED_INSTANCES,
                allTasks.stream().filter(ProcessTask::isFinished).count());
        return vars;
    }
}
