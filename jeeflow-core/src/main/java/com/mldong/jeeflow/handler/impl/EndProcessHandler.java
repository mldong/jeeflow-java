package com.mldong.jeeflow.handler.impl;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.event.PendingInstanceEnd;
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

        // 实例终态事件（spec §11.3 码 2 PROCESS_INSTANCE_END）：办结与拒绝共用这一支，
        // 规范名不拆，靠载荷 state 分（§11.6 收口口径）。直传载荷键 instanceId + state。
        //
        // **只登记、不就地 fire**（§11.2 原则 3／08-compliance 场景 32「state 落库之后」）：
        // 上面 finish()/reject() 只改了内存聚合根，实例那一行要等调用方——
        // JeeflowEngineImpl.persistTasks（或发起路径）——的 updateInstance 才落库。
        // 在这里 fire 就是"先播后写"，监听器（站内信反查、待办角标、persist 回写）当下
        // 反查实例读到的是旧 state，issues/121／126 两轮"回写序"教训的同一族。
        // 真正的 fire 收口在 JeeflowEngineImpl.flushInstanceEndEvents。
        ProcessInstance instance = execution.getProcessInstance();
        execution.addPendingEnd(new PendingInstanceEnd(
                execution.getProcessInstanceId(), instance.getState(), instance));

        // 处理子流程：如果当前流程有父流程，则继续执行父流程的子流程节点
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
                            // 父实例这一支流转里若有记录类（custom）节点，它的历史行挂在 newExec 上，
                            // 同 addTasks 这条腿一起上收（issues/142 · §6.2 第 1 条）——漏一行
                            // 就是"父实例的那条留痕整支丢掉"，而且 newExec 是局部对象，随即丢弃、无从补写。
                            execution.addHistoryTasks(newExec.getHistoryTasks());
                            // 父实例若被这一支流转带到终态，**它的**子流程节点会再进一次本处理器，
                            // 登记挂在 newExec 上；newExec 是这里的局部对象，随即丢弃 ⇒ 待播事件
                            // 必须与任务一起上收到外层 execution（同 addTasks 那条腿），漏一行
                            // 就是"父实例终态事件整支丢掉"。
                            execution.addPendingEnds(newExec.getPendingEnds());
                        }
                    }
                }
            }
        }
    }
}
