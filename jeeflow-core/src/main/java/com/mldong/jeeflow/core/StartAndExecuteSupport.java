package com.mldong.jeeflow.core;

import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
import com.mldong.jeeflow.spi.IProcessRepository;

import java.util.Collections;
import java.util.List;

public final class StartAndExecuteSupport {
    private StartAndExecuteSupport() {}

    public static List<ProcessTask> applyInitialTasks(JeeflowEngine engine, Long instanceId,
                                                      String applicant, FlowData args) {
        IProcessRepository repository = engine.getRepository();
        List<ProcessTask> doing = repository.findDoingTasks(instanceId, new String[]{});
        List<ProcessTask> result = new java.util.ArrayList<>();
        for (ProcessTask task : doing) {
            repository.addTaskActor(task.getTaskId(), Collections.singletonList(applicant));
            FlowData applyArgs = args != null ? args.copy() : FlowData.create();
            applyArgs.put(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.APPLY.getCode());
            Object next = applyArgs.get(FlowConst.PROCESS_START_NEXT_NODE_OPERATOR);
            if (next != null && !next.toString().isEmpty()) {
                applyArgs.put(FlowConst.NEXT_NODE_OPERATOR, next);
            }
            result.addAll(engine.executeProcessTask(task.getTaskId(), FlowConst.AUTO_ID, applyArgs));
        }
        return result;
    }

    public static FlowData childArgs(FlowData parentArgs) {
        FlowData args = parentArgs != null ? parentArgs.copy() : FlowData.create();
        args.remove(FlowConst.SUBMIT_TYPE);
        args.remove(FlowConst.PROCESS_START_NEXT_NODE_OPERATOR);
        args.remove(FlowConst.NEXT_NODE_OPERATOR);
        args.remove(ReapplySupport.VAR_BRANCH_CTX);
        args.remove(ReapplySupport.VAR_REAPPLY_CTX);
        for (String key : new java.util.ArrayList<>(args.keySet())) {
            if (key != null && key.startsWith(FlowConst.TASK_FORM_DATA_PREFIX)) {
                args.remove(key);
            }
        }
        return args;
    }

    public static String realOperator(String operator, ProcessInstance parent) {
        if (parent == null) return operator;
        if (FlowConst.AUTO_ID.equalsIgnoreCase(operator) || FlowConst.ADMIN_ID.equalsIgnoreCase(operator)) {
            return parent.getOperator();
        }
        return operator;
    }

    public static void validateSubProcessTarget(com.mldong.jeeflow.model.ProcessModel model,
                                                com.mldong.jeeflow.model.NodeModel current,
                                                IProcessRepository repository) {
        if (model == null || current == null || repository == null) return;
        for (com.mldong.jeeflow.model.TransitionModel transition : current.getOutputs()) {
            if (transition.getTarget() instanceof com.mldong.jeeflow.model.SubProcessModel) {
                com.mldong.jeeflow.model.SubProcessModel sub =
                        (com.mldong.jeeflow.model.SubProcessModel) transition.getTarget();
                if (repository.findDefineByNameAndVersion(sub.getProcessName(), sub.getVersion()) == null) {
                    throw new com.mldong.jeeflow.JeeflowException(
                            com.mldong.jeeflow.enums.WfErrEnum.NOT_FOUND_PROCESS_DEFINE.getCode(),
                            "子流程" + sub.getProcessName() + "定义不存在");
                }
            }
        }
    }
}
