package com.mldong.jeeflow.test;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.handler.IHandler;
import com.mldong.jeeflow.enums.FlowConst;

/**
 * 自定义节点测试处理器
 */
public class TestCustomHandler implements IHandler {

    @Override
    public void handle(Execution execution) {
        // 将自定义节点返回值写入执行上下文
        execution.getArgs().put(FlowConst.CUSTOM_RETURN_VAL, "customExecuted");
        execution.getProcessInstance().addVariable(
                com.mldong.jeeflow.domain.FlowData.create().set("customNodeRan", true)
        );
    }
}
