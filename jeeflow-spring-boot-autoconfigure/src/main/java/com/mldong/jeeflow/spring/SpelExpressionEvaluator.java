package com.mldong.jeeflow.spring;

import com.mldong.jeeflow.spi.IExpressionEvaluator;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;

/**
 * 基于 Spring EL 的表达式求值器
 *
 * @author mldong
 */
public class SpelExpressionEvaluator implements IExpressionEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();

    @Override
    public Object eval(String expression, Map<String, Object> context) {
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        if (context != null) {
            ctx.setVariables(context);
            // 同时设置为根对象属性，支持 amount > 1000 这类表达式
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                ctx.setVariable(entry.getKey(), entry.getValue());
            }
        }
        // SpEL 需要 # 前缀引用变量
        String expr = expression;
        if (!expression.startsWith("#")) {
            // 简单变量替换：amount > 1000 → #amount > 1000
            for (String key : context.keySet()) {
                expr = expr.replace(key, "#" + key);
            }
        }
        try {
            return parser.parseExpression(expr).getValue(ctx);
        } catch (Exception e) {
            return false;
        }
    }
}
