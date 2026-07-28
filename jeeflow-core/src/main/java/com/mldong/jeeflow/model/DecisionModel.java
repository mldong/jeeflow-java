package com.mldong.jeeflow.model;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.enums.WfErrEnum;
import com.mldong.jeeflow.JeeflowException;
import com.mldong.jeeflow.interceptor.DecisionHandler;
import com.mldong.jeeflow.util.StringUtils;
import com.mldong.jeeflow.spi.IExpressionEvaluator;
import com.mldong.jeeflow.core.ServiceContext;

/**
 * 决策节点模型
 *
 * @author mldong
 */
public class DecisionModel extends NodeModel {

    private String expr;
    private String handleClass;

    @Override
    public void exec(Execution execution) {
        boolean found = false;
        String nextNodeName = null;

        if (StringUtils.isNotEmpty(expr)) {
            IExpressionEvaluator evaluator = ServiceContext.find(IExpressionEvaluator.class);
            if (evaluator == null) {
                throw new JeeflowException(WfErrEnum.NOT_FOUND_NEXT_NODE);
            }
            Object result = evaluator.eval(expr, execution.getArgs());
            if (result != null) nextNodeName = result.toString();
        } else if (StringUtils.isNotEmpty(handleClass)) {
            try {
                DecisionHandler handler = (DecisionHandler)
                        Class.forName(handleClass.trim()).getDeclaredConstructor().newInstance();
                nextNodeName = handler.decide(execution);
            } catch (Exception e) {
                throw new JeeflowException(WfErrEnum.NOT_FOUND_NEXT_NODE);
            }
        }

        for (TransitionModel tm : getOutputs()) {
            if (StringUtils.isNotEmpty(tm.getExpr())) {
                IExpressionEvaluator evaluator = ServiceContext.find(IExpressionEvaluator.class);
                if (evaluator != null && Boolean.TRUE.equals(evaluator.eval(tm.getExpr(), execution.getArgs()))) {
                    found = true;
                    tm.setEnabled(true);
                    tm.execute(execution);
                }
            } else if (tm.getTo().equalsIgnoreCase(nextNodeName)) {
                found = true;
                tm.setEnabled(true);
                tm.execute(execution);
            }
        }

        if (!found) {
            throw new JeeflowException(WfErrEnum.NOT_FOUND_NEXT_NODE);
        }
    }

    public String getExpr() { return expr; }
    public void setExpr(String expr) { this.expr = expr; }
    public String getHandleClass() { return handleClass; }
    public void setHandleClass(String handleClass) { this.handleClass = handleClass; }
}
