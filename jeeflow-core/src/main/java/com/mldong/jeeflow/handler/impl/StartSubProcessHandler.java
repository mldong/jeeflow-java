package com.mldong.jeeflow.handler.impl;

import com.mldong.jeeflow.JeeflowException;
import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.core.StartAndExecuteSupport;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.WfErrEnum;
import com.mldong.jeeflow.handler.IHandler;
import com.mldong.jeeflow.model.SubProcessModel;
import com.mldong.jeeflow.spi.IProcessRepository;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StartSubProcessHandler implements IHandler {

    private final SubProcessModel subProcessModel;

    public StartSubProcessHandler(SubProcessModel subProcessModel) {
        this.subProcessModel = subProcessModel;
    }

    @Override
    public void handle(Execution execution) {
        IProcessRepository repository = execution.getEngine().getRepository();
        ProcessInstance.ProcessDefine define = repository.findDefineByNameAndVersion(
                subProcessModel.getProcessName(), subProcessModel.getVersion());
        if (define == null) {
            throw new JeeflowException(WfErrEnum.NOT_FOUND_PROCESS_DEFINE.getCode(),
                    "子流程" + subProcessModel.getProcessName() + "定义不存在");
        }

        ProcessInstance parent = execution.getProcessInstance();
        String operator = StartAndExecuteSupport.realOperator(execution.getOperator(), parent);
        FlowData childArgs = StartAndExecuteSupport.childArgs(execution.getArgs());
        Set<Long> parentTasksBefore = taskIds(repository.findDoingTasks(parent.getInstanceId(), null));

        ProcessInstance child = execution.getEngine().startProcessInstanceById(
                define.getId(), operator, childArgs, parent.getInstanceId(), subProcessModel.getName());

        Map<Long, ProcessTask> result = new LinkedHashMap<>();
        addUnique(result, StartAndExecuteSupport.applyInitialTasks(
                execution.getEngine(), child.getInstanceId(), operator, childArgs));
        addUnique(result, repository.findDoingTasks(child.getInstanceId(), null));
        for (ProcessTask task : repository.findDoingTasks(parent.getInstanceId(), null)) {
            if (!parentTasksBefore.contains(task.getTaskId()) && task.getTaskId() != null) {
                result.putIfAbsent(task.getTaskId(), task);
            }
        }
        execution.addPersistedTasks(result.values());
    }

    private static Set<Long> taskIds(List<ProcessTask> tasks) {
        Set<Long> ids = new HashSet<>();
        for (ProcessTask task : tasks) ids.add(task.getTaskId());
        return ids;
    }

    private static void addUnique(Map<Long, ProcessTask> result, List<ProcessTask> tasks) {
        for (ProcessTask task : tasks) {
            if (task != null && task.getTaskId() != null) result.putIfAbsent(task.getTaskId(), task);
        }
    }
}
