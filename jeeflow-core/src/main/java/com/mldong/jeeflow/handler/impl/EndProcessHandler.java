package com.mldong.jeeflow.handler.impl;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.enums.ProcessEventTypeEnum;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.event.ProcessEvent;
import com.mldong.jeeflow.event.ProcessPublisher;
import com.mldong.jeeflow.handler.IHandler;
import com.mldong.jeeflow.model.EndModel;
import com.mldong.jeeflow.model.ProcessModel;
import com.mldong.jeeflow.model.SubProcessModel;
import com.mldong.jeeflow.parser.ModelParser;

/**
 * 结束流程实例处理器
 *
 * @author mldong
 */
public class EndProcessHandler implements IHandler {

    private final EndModel endModel;

    public EndProcessHandler(EndModel endModel) {
        this.endModel = endModel;
    }

    @Override
    public void handle(Execution execution) {
        Integer submitType = execution.getArgs().getInt(FlowConst.SUBMIT_TYPE,
                ProcessSubmitTypeEnum.AGREE.getCode());

        if (ProcessSubmitTypeEnum.REJECT.getCode().equals(submitType)) {
            execution.getProcessInstance().reject();
        } else {
            execution.getProcessInstance().finish();
        }

        // 发布流程结束事件
        ProcessPublisher.notify(ProcessEvent.builder()
                .eventType(ProcessEventTypeEnum.PROCESS_INSTANCE_END)
                .sourceId(execution.getProcessInstanceId())
                .build());

        // 处理子流程：如果当前流程有父流程，则继续执行父流程的子流程节点
        ProcessInstance instance = execution.getProcessInstance();
        if (instance.getParentId() != null) {
            ProcessInstance parentInstance = execution.getEngine().getRepository()
                    .findInstanceById(instance.getParentId());
            if (parentInstance != null) {
                ProcessInstance.ProcessDefine parentDefine = execution.getEngine().getRepository()
                        .findDefineById(parentInstance.getDefineId());
                if (parentDefine != null) {
                    ProcessModel pm = ModelParser.parse(parentDefine.getContent());
                    if (pm != null) {
                        SubProcessModel spm = (SubProcessModel) pm.getNode(instance.getParentNodeName());
                        if (spm != null) {
                            Execution newExec = new Execution();
                            newExec.setEngine(execution.getEngine());
                            newExec.setProcessModel(pm);
                            newExec.setProcessInstance(parentInstance);
                            newExec.setProcessInstanceId(parentInstance.getInstanceId());
                            newExec.setArgs(execution.getArgs());
                            spm.execute(newExec);
                            execution.addTasks(newExec.getProcessTaskList());
                        }
                    }
                }
            }
        }
    }
}
