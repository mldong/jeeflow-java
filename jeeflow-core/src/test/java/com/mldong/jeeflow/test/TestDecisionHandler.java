package com.mldong.jeeflow.test;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.interceptor.DecisionHandler;

/**
 * 决策处理器测试实现
 */
public class TestDecisionHandler implements DecisionHandler {

    @Override
    public String decide(Execution execution) {
        // 根据 amount 决定下一个节点
        Object amount = execution.getArgs().get("amount");
        if (amount != null) {
            double val = Double.parseDouble(amount.toString());
            return val > 1000 ? "task2" : "task3";
        }
        return "task3";
    }
}
