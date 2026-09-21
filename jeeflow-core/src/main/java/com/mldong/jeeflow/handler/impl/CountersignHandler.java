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

import java.util.ArrayList;
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
            // 串行会签逐个创建（issues/93，对齐内置版 createCountersignTask + Go/Python/Node）：
            // 每次仅 1 个 DOING 成员任务。本位（最后一位）完成 → 流转；否则创建下一位成员任务并停留。
            // 会签计数状态在首位任务变量（createCountersignTasks 写入），prepareExecution 已把
            // 刚完成任务变量同步到 execution.getProcessTask()，由此读取 loopCounter/operatorList。
            merged = false;
            ProcessTask completed = execution.getProcessTask();
            List<String> operatorList = null;
            int loopCounter = 0;
            if (completed != null && completed.getVariables() != null) {
                Object ol = completed.getVariables().get(
                        FlowConst.COUNTERSIGN_OPERATOR_LIST + "_" + taskModel.getName());
                operatorList = toStringList(ol);
                loopCounter = completed.getVariables().getInt(
                        FlowConst.LOOP_COUNTER + "_" + taskModel.getName(), 0);
            }
            if (operatorList != null && !operatorList.isEmpty() && loopCounter + 1 < operatorList.size()) {
                createNextCountersignTask(instance, taskModel, operatorList.get(loopCounter + 1),
                        loopCounter + 1, operatorList.size(), execution);
            } else {
                // 最后一位完成 → 流转（全部成员完成才推进，issues/44 E16）
                merged = true;
            }
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

    /** 串行会签推进：创建下一位成员任务（issues/93）。
     *  新任务同时加入聚合根（instance.tasks，供 updateInstance 级联/后续查询）与 execution
     *  （供 persistTasks 经 saveTask 落库并分配任务 id——saveTask 对 null id 自动分配）。
     *  会签计数状态随任务变量持久化（loopCounter+1 / operatorList 全量 / nrOfInstances），
     *  下一位完成时由本处理器再次读取推进，直至最后一位完成才 merged。 */
    private void createNextCountersignTask(ProcessInstance instance, TaskModel taskModel,
                                           String nextActor, int nextLoopCounter, int total,
                                           Execution execution) {
        String node = taskModel.getName();
        ProcessTask next = ProcessTask.create(
                instance.getInstanceId(),
                node,
                taskModel.getDisplayName(),
                taskModel.getTaskType(),
                taskModel.getPerformType(),
                taskModel.getForm(),
                new ArrayList<>(java.util.Collections.singletonList(nextActor)),
                execution.getOperator(),
                // 建单不变量：串行会签的下一位成员，parent＝刚完成的那一位（execution 当前任务）
                execution.getProcessTaskId(),
                com.mldong.jeeflow.util.FlowUtil.isFirstTaskName(execution.getProcessModel(), node)
        );
        next.getVariables().put(FlowConst.COUNTERSIGN_OPERATOR_LIST + "_" + node,
                readOperatorList(execution, node));
        next.getVariables().put(FlowConst.LOOP_COUNTER + "_" + node, nextLoopCounter);
        next.getVariables().put(FlowConst.NR_OF_INSTANCES + "_" + node, total);
        instance.getTasks().add(next);
        execution.addTask(next);
    }

    /** 从当前 execution 的已完成任务变量读取会签全量办理人（operatorList_{node}） */
    private List<String> readOperatorList(Execution execution, String node) {
        ProcessTask completed = execution.getProcessTask();
        if (completed != null && completed.getVariables() != null) {
            return toStringList(completed.getVariables().get(
                    FlowConst.COUNTERSIGN_OPERATOR_LIST + "_" + node));
        }
        return new ArrayList<>();
    }

    /** 会签办理人列表取值兼容（JSON 反序列化后可能是 List / 其他可迭代） */
    private List<String> toStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value == null) return result;
        if (value instanceof java.util.Collection) {
            for (Object o : (java.util.Collection<?>) value) {
                String s = o == null ? null : o.toString().trim();
                if (s != null && !s.isEmpty()) result.add(s);
            }
        } else {
            String s = value.toString().trim();
            if (!s.isEmpty()) result.add(s);
        }
        return result;
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
