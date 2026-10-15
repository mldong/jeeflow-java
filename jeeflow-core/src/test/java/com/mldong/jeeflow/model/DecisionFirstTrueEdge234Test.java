package com.mldong.jeeflow.model;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.Context;
import com.mldong.jeeflow.context.SimpleContext;
import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.spi.IExpressionEvaluator;
import com.mldong.jeeflow.test.TestDecisionHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * issues/234 丙-9（R90 拍 A）：决策节点「首条真边获胜」——命中即 break。
 *
 * <p>改前病灶：java 循环无 break，多条真边全部执行＝多路并行流转；spec 02 §5 与多数派
 * （go/py/node/rust/moon）均为首条真边独走。变异＝摘 break，两格应复红。</p>
 */
public class DecisionFirstTrueEdge234Test {

    /** 记录型目标节点：只记被谁执行，不做任何流转。 */
    static final class RecordingNode extends NodeModel {
        private final String tag;
        private final List<String> hits;
        RecordingNode(String tag, List<String> hits) {
            this.tag = tag;
            this.hits = hits;
        }
        @Override
        public void execute(Execution execution) {
            hits.add(tag);
        }
        @Override
        void exec(Execution execution) {
            // 不会被走到：execute 已被覆写
        }
    }

    private Context savedContext;

    @Before
    public void setUp() {
        savedContext = ServiceContext.getContext();
        SimpleContext ctx = new SimpleContext();
        new Configuration(ctx);
        ctx.put("eval-true", (IExpressionEvaluator) (expression, context) -> Boolean.TRUE);
    }

    @After
    public void tearDown() {
        if (savedContext != null) {
            ServiceContext.setContext(savedContext);
        }
    }

    private static TransitionModel edge(DecisionModel from, String to, String expr, NodeModel target) {
        TransitionModel tm = new TransitionModel();
        tm.setSource(from);
        tm.setTo(to);
        tm.setExpr(expr);
        tm.setTarget(target);
        from.getOutputs().add(tm);
        return tm;
    }

    /** expr 分支：两条边表达式同时为真 ⇒ 只有第一条边执行（改前两条都执行）。 */
    @Test
    public void firstTrueExprEdgeWins() {
        List<String> hits = new ArrayList<>();
        DecisionModel decision = new DecisionModel();
        TransitionModel e1 = edge(decision, "t1", "1==1", new RecordingNode("t1", hits));
        TransitionModel e2 = edge(decision, "t2", "2==2", new RecordingNode("t2", hits));
        decision.exec(new Execution());
        assertEquals("首条真边获胜：只有第一条边被执行（改前 hits=[t1, t2]）",
                Arrays.asList("t1"), hits);
        assertTrue(e1.isEnabled());
    }

    /** handleClass 分支：同 to 名两边只走第一条（变异＝摘 break 时第二边也执行）。 */
    @Test
    public void handleClassBranchAlsoStopsAtFirstMatch() {
        List<String> hits = new ArrayList<>();
        DecisionModel decision = new DecisionModel();
        decision.setHandleClass(TestDecisionHandler.class.getName());
        // args 为空 ⇒ TestDecisionHandler 返回 "task3"；两条边 to 均为 task3、目标不同
        TransitionModel e1 = edge(decision, "task3", null, new RecordingNode("first", hits));
        TransitionModel e2 = edge(decision, "task3", null, new RecordingNode("second", hits));
        decision.exec(new Execution());
        assertEquals("handleClass 分支同样首条命中即停（改前 hits=[first, second]）",
                Arrays.asList("first"), hits);
        assertTrue(e1.isEnabled());
    }
}
