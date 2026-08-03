package com.mldong.jeeflow.interceptor.impl;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.interceptor.AssignmentHandler;

/**
 * 流程发起人参与者（内置，issues/16）——纯引擎语义，无外部依赖。
 * 节点 assignmentHandler 配置：com.mldong.jeeflow.interceptor.impl.OperatorAssignmentHandler
 */
public class OperatorAssignmentHandler implements AssignmentHandler {

    @Override
    public String assign(Execution execution) {
        if (execution.getProcessInstance() != null
                && execution.getProcessInstance().getOperator() != null
                && !execution.getProcessInstance().getOperator().isEmpty()) {
            return execution.getProcessInstance().getOperator();
        }
        return "apply.operator";
    }
}
