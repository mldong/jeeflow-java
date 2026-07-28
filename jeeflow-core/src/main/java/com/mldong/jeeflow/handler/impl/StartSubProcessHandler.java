package com.mldong.jeeflow.handler.impl;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.handler.IHandler;
import com.mldong.jeeflow.model.SubProcessModel;
import com.mldong.jeeflow.parser.ModelParser;

import java.util.List;

/**
 * 启动子流程处理器
 *
 * @author mldong
 */
public class StartSubProcessHandler implements IHandler {

    private final SubProcessModel subProcessModel;

    public StartSubProcessHandler(SubProcessModel subProcessModel) {
        this.subProcessModel = subProcessModel;
    }

    @Override
    public void handle(Execution execution) {
        // 启动子流程
        ProcessInstance child = execution.getEngine().startProcessInstanceById(
                null, // 这里需要根据 name 查找 defineId，简化处理
                execution.getOperator(),
                execution.getArgs(),
                execution.getProcessInstanceId(),
                subProcessModel.getName()
        );
        if (child != null) {
            execution.addTasks(child.getDoingTasks());
        }
    }
}
