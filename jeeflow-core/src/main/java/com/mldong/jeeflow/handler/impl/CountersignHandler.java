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

        // 一票否决检查
        Integer submitType = execution.getArgs().getInt(FlowConst.SUBMIT_TYPE);
        if (ProcessSubmitTypeEnum.COUNTERSIGN_DISAGREE.getCode().equals(submitType)) {
            execution.setMerged(true);
            return;
        }

        CountersignTypeEnum countersignType = taskModel.getCountersignType();

        if (CountersignTypeEnum.SEQUENTIAL.equals(countersignType)) {
            // 串行会签：全部成员完成才流转（issues/44 E16——此前无条件 merged 导致
            // 每次成员完成都重复流转后续节点）。对齐内置版"最后一人完成才 merged"
            // 与 Node/Go/Python"doing 空才流转"语义：流转时机 = 全部完成
            execution.setMerged(finishedCount >= allTasks.size());
        } else {
            // 并行会签：检查条件
            String cond = taskModel.getCountersignCompletionCondition();
            if (StringUtils.isEmpty(cond)) {
                // 无特殊条件 → 全部完成
                execution.setMerged(finishedCount >= allTasks.size());
            } else {
                IExpressionEvaluator evaluator = ServiceContext.find(IExpressionEvaluator.class);
                if (evaluator != null) {
                    FlowData vars = buildCountersignVars(instance, taskModel, allTasks);
                    vars.putAll(execution.getArgs());
                    try {
                        Object result = evaluator.eval(cond, vars);
                        execution.setMerged(Boolean.TRUE.equals(result));
                    } catch (Exception ignored) {
                        execution.setMerged(false);
                    }
                } else {
                    execution.setMerged(false);
                }
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
