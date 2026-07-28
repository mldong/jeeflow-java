package com.mldong.jeeflow.spi;

import java.util.Map;

/**
 * 表达式求值提供者 SPI（可选）
 *
 * <p>决策节点和会签完成条件需要表达式求值。
 * 如果不注册，使用决策/会签功能时会抛异常。</p>
 *
 * @author mldong
 */
public interface IExpressionEvaluator {

    /** 在指定上下文中求值表达式，返回结果 */
    Object eval(String expression, Map<String, Object> context);
}
