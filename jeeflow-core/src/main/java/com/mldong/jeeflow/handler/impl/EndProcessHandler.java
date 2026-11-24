package com.mldong.jeeflow.handler.impl;

import com.mldong.jeeflow.JeeflowException;
import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.core.StartAndExecuteSupport;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
import com.mldong.jeeflow.enums.ProcessTaskTypeEnum;
import com.mldong.jeeflow.event.PendingInstanceEnd;
import com.mldong.jeeflow.handler.IHandler;
import com.mldong.jeeflow.model.EndModel;
import com.mldong.jeeflow.model.NodeModel;
import com.mldong.jeeflow.model.ProcessModel;
import com.mldong.jeeflow.model.SubProcessModel;
import com.mldong.jeeflow.parser.ModelParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class EndProcessHandler implements IHandler {

    private final EndModel endModel;

    public EndProcessHandler(EndModel endModel) {
        this.endModel = endModel;
    }

    @Override
    public void handle(Execution execution) {
        ProcessInstance instance = execution.getProcessInstance();
        // 当前聚合根优先：会签合并/拒绝刚改掉的兄弟任务尚未落库，只查仓储会误判成进行中。
        List<ProcessTask> active = new ArrayList<>(instance.getDoingTasks());
        Set<Long> knownTaskIds = instance.getTasks().stream()
                .map(ProcessTask::getTaskId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        for (ProcessTask task : execution.getEngine().getRepository()
                .findDoingTasks(instance.getInstanceId(), null)) {
            if (!knownTaskIds.contains(task.getTaskId())) active.add(task);
        }
        for (ProcessTask task : active) {
            if (task.getTaskType() != ProcessTaskTypeEnum.SECONDARY) {
                throw new JeeflowException("存在未完成的主办任务,请确认.");
            }
        }
        Set<Long> activeTaskIds = active.stream().map(ProcessTask::getTaskId).collect(Collectors.toSet());
        for (ProcessTask task : active) {
            FlowData args = execution.getArgs() != null ? execution.getArgs().copy() : FlowData.create();
            args.put(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode());
            execution.getEngine().executeProcessTask(task.getTaskId(), FlowConst.AUTO_ID, args);
        }
        if (!active.isEmpty()) {
            ProcessInstance refreshed = execution.getEngine().getRepository()
                    .findInstanceById(instance.getInstanceId());
            for (ProcessTask task : refreshed.getTasks()) {
                int index = -1;
                for (int i = 0; i < instance.getTasks().size(); i++) {
                    if (java.util.Objects.equals(instance.getTasks().get(i).getTaskId(), task.getTaskId())) {
                        index = i;
                        break;
                    }
                }
                if (activeTaskIds.contains(task.getTaskId()) || index < 0) {
                    if (index < 0) instance.getTasks().add(task);
                    else instance.getTasks().set(index, task);
                }
            }
            instance.getVariables().putAll(refreshed.getVariables());
        }

        Integer submitType = execution.getArgs().getInt(FlowConst.SUBMIT_TYPE,
                ProcessSubmitTypeEnum.AGREE.getCode());
        if (ProcessSubmitTypeEnum.REJECT.getCode().equals(submitType)) {
            instance.reject();
        } else {
            instance.finish();
        }
        execution.addPendingEnd(new PendingInstanceEnd(
                execution.getProcessInstanceId(), instance.getState(), instance));

        if (instance.getParentId() != null) {
            ProcessInstance parentInstance = execution.getEngine().getRepository()
                    .findInstanceById(instance.getParentId());
            if (parentInstance == null) return;
            ProcessInstance.ProcessDefine parentDefine = execution.getEngine().getRepository()
                    .findDefineById(parentInstance.getDefineId());
            if (parentDefine == null) return;
            ProcessModel parentModel = ModelParser.parse(parentDefine.getContent());
            if (parentModel == null) return;
            NodeModel parentNode = parentModel.getNode(instance.getParentNodeName());
            if (!(parentNode instanceof SubProcessModel)) return;

            Execution parentExecution = new Execution();
            parentExecution.setEngine(execution.getEngine());
            parentExecution.setProcessModel(parentModel);
            parentExecution.setProcessInstance(parentInstance);
            parentExecution.setProcessInstanceId(parentInstance.getInstanceId());
            parentExecution.setArgs(execution.getArgs());
            parentExecution.setOperator(StartAndExecuteSupport.realOperator(
                    execution.getOperator(), instance));
            ((SubProcessModel) parentNode).execute(parentExecution);
            execution.addContinuation(parentExecution);
        }
    }
}
