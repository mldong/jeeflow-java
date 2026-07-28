package com.mldong.jeeflow.test;

import com.mldong.jeeflow.spi.IExpressionEvaluator;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.Map;

/**
 * 基于 JDK ScriptEngine 的表达式求值器（测试用）
 */
public class TestExpressionEvaluator implements IExpressionEvaluator {

    private final ScriptEngine scriptEngine;

    public TestExpressionEvaluator() {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine se = manager.getEngineByName("nashorn");
        if (se == null) {
            se = manager.getEngineByName("JavaScript");
        }
        this.scriptEngine = se;
    }

    @Override
    public Object eval(String expression, Map<String, Object> context) {
        if (scriptEngine == null) {
            // JDK 15+ 无内置 JS 引擎，使用简单解析
            return evalSimple(expression, context);
        }
        try {
            // 将 context 变量注入引擎
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                scriptEngine.put(entry.getKey(), entry.getValue());
            }
            return scriptEngine.eval(expression);
        } catch (Exception e) {
            return evalSimple(expression, context);
        }
    }

    /** 简单表达式解析：支持 amount > 1000 这类比较 */
    private static Object evalSimple(String expression, Map<String, Object> context) {
        expression = expression.trim();

        // 处理 #nrOfCompletedInstances==2 这类会签条件
        if (expression.contains("#nrOfCompletedInstances")) {
            for (String key : context.keySet()) {
                if (key.endsWith("nrOfCompletedInstances")) {
                    Object val = context.get(key);
                    String expr = expression.replace("#nrOfCompletedInstances",
                            val != null ? val.toString() : "0");
                    return evaluateComparison(expr);
                }
            }
        }
        if (expression.contains("#nrOfInstances")) {
            for (String key : context.keySet()) {
                if (key.endsWith("nrOfInstances")) {
                    Object val = context.get(key);
                    String expr = expression.replace("#nrOfInstances",
                            val != null ? val.toString() : "0");
                    return evaluateComparison(expr);
                }
            }
        }

        // 直接替换变量
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            if (expression.contains(entry.getKey()) && entry.getValue() != null) {
                expression = expression.replace(entry.getKey(), entry.getValue().toString());
            }
        }
        return evaluateComparison(expression);
    }

    private static Boolean evaluateComparison(String expr) {
        try {
            if (expr.contains(">=")) {
                String[] parts = expr.split(">=");
                return Double.parseDouble(parts[0].trim()) >= Double.parseDouble(parts[1].trim());
            } else if (expr.contains("<=")) {
                String[] parts = expr.split("<=");
                return Double.parseDouble(parts[0].trim()) <= Double.parseDouble(parts[1].trim());
            } else if (expr.contains("==")) {
                String[] parts = expr.split("==");
                return parts[0].trim().equals(parts[1].trim());
            } else if (expr.contains(">")) {
                String[] parts = expr.split(">");
                return Double.parseDouble(parts[0].trim()) > Double.parseDouble(parts[1].trim());
            } else if (expr.contains("<")) {
                String[] parts = expr.split("<");
                return Double.parseDouble(parts[0].trim()) < Double.parseDouble(parts[1].trim());
            } else {
                return Boolean.parseBoolean(expr);
            }
        } catch (Exception e) {
            return false;
        }
    }
}
