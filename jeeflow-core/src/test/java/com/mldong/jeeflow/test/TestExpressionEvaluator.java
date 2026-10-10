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

        // issues/165：`#变量` 引用按**生产 SpEL 形状**精确查表替换（`#key` ⇔ context 里的
        // `key`）。旧形状是 endsWith 后缀桥（`#nrOfCompletedInstances` 桥接到
        // `csv_<node>_nrOfCompletedInstances`），会签门控裸名没进原料的单测也能绿——
        // "测试绿生产红"由此而来，桥拆除后裸名格只有在 handler 真挂了裸名才可能绿。
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            String ref = "#" + entry.getKey();
            if (entry.getValue() != null && expression.contains(ref)) {
                expression = expression.replace(ref, entry.getValue().toString());
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
