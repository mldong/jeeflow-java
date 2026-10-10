package com.mldong.jeeflow.spring;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * issues/165 · 生产求值器判据——SpEL 上 `#裸名` 与 `#前缀键` 的求值形状。
 *
 * <p>本模块的 {@link SpelExpressionEvaluator} 是 java 栈的<b>生产</b>表达式求值器；
 * core 测试里的 {@code TestExpressionEvaluator} 曾带 endsWith 后缀桥，把"裸名没进原料"
 * 的缺陷在单测侧遮住了（测试绿、生产红）。本类直接实例化生产求值器，
 * 与 {@code CountersignHandler} 挂裸名（165 A 案）配套：</p>
 *
 * <ul>
 *   <li>handler 挂了裸名 ⇒ `#nrOfCompletedInstances`（文档/设计器形状）求得出真；</li>
 *   <li>handler 只挂前缀键（旧形状）⇒ 裸名恒 false——本类用"上下文只有前缀键"那一格
 *       把病根本形钉死，回归时若有人把挂裸名拆了，配合 core 侧
 *       {@code CountersignBareNameI165Test} 一起红。</li>
 * </ul>
 */
public class SpelExpressionEvaluatorTest {

    private final SpelExpressionEvaluator evaluator = new SpelExpressionEvaluator();

    private Map<String, Object> vars(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    public void bareNameMountedReleases() {
        // handler 修复后 buildCountersignVars 的上下文形状：裸名+前缀键同挂
        Map<String, Object> ctx = vars(
                "csv_cs1_nrOfInstances", 3,
                "csv_cs1_nrOfCompletedInstances", 2,
                "nrOfInstances", 3,
                "nrOfCompletedInstances", 2);
        assertEquals(Boolean.TRUE, evaluator.eval("#nrOfCompletedInstances>=2", ctx));
    }

    @Test
    public void bareNameBelowThresholdStays() {
        Map<String, Object> ctx = vars(
                "csv_cs1_nrOfCompletedInstances", 1,
                "nrOfCompletedInstances", 1);
        assertEquals(Boolean.FALSE, evaluator.eval("#nrOfCompletedInstances>=2", ctx));
    }

    @Test
    public void prefixKeyStillReleases() {
        // 既有兼容面：前缀键形状照常求值
        Map<String, Object> ctx = vars("csv_cs1_nrOfCompletedInstances", 2);
        assertEquals(Boolean.TRUE, evaluator.eval("#csv_cs1_nrOfCompletedInstances>=2", ctx));
    }

    @Test
    public void bareNameMissingNeverTrue() {
        // 165 病根本形：条件引用裸名、上下文只有前缀键 ⇒ SpEL 查不到变量，恒 false。
        // （改前 handler 只产这个形状的上下文 ⇒ 文档形状在产线上永不放行）
        Map<String, Object> ctx = vars("csv_cs1_nrOfCompletedInstances", 2);
        assertEquals(Boolean.FALSE, evaluator.eval("#nrOfCompletedInstances>=2", ctx));
    }

    @Test
    public void plainVarReplacementArmUnchanged() {
        // 非 # 开头的替换臂（amount > 1000 这类）不受本轮影响
        assertEquals(Boolean.TRUE, evaluator.eval("amount > 1000", vars("amount", 2000)));
        assertEquals(Boolean.FALSE, evaluator.eval("amount > 1000", vars("amount", 500)));
    }
}
