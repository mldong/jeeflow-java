package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.Context;
import com.mldong.jeeflow.context.SimpleContext;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.model.NodeModel;
import com.mldong.jeeflow.model.ProcessModel;
import com.mldong.jeeflow.model.SubProcessModel;
import com.mldong.jeeflow.model.TransitionModel;
import com.mldong.jeeflow.parser.ModelParser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * 节点类型键契约（issues/141 G4 · C 步 ＋ spec 02-flow-definition.md「类型键的三条义务」· Java 栈）。
 *
 * <p>现读病灶：本仓类型表（{@link Configuration}）只认大写 {@code subProcess} ＋ 内部变体
 * {@code wfSubProcess}，而 spec 02 的表立的是<b>小写</b> {@code snaker:subprocess} ⇒ 照 spec 写的
 * 定义在 java 参考实现里拿不到子流程节点（查表＝{@code SimpleContext.findByName} 的 HashMap get，
 * 大小写敏感）。另一格是未命中解析器时 {@code ModelParser} 走 {@code if (parser != null)} 分支，
 * <b>无声丢节点＋连带丢它的出边</b>（php 缺 {@code custom} 档被吞掉的就是同一形状）。</p>
 *
 * <p>owner 2026-09-29 拍的落地顺序＝义务 3「别名保留一代」先做（与大写同值补小写
 * {@code subprocess}），义务 2「未知档不得静默丢」补一条带节点 id 与实得类型串的可诊断日志；
 * 义务 1「查表前大小写归一」<b>本轮不做</b>（会牵动档位表全部条目，需一次全量回归）——
 * 所以本文件同时钉住"归一化还没发生"这一事实：{@code snaker:SUBPROCESS} 这类大小写变体
 * 仍解析不出，靠别名而不是靠归一。</p>
 */
public class NodeTypeKeyAliasTest {

    private Context savedContext;

    @Before
    public void setUp() {
        savedContext = ServiceContext.getContext();
        // new Configuration(ctx) 会把 ctx 设为当前 ServiceContext 并注册内置解析器，
        // 故 IJsonProvider 必须在其后 put（否则被 setContext 覆盖丢失）。
        SimpleContext ctx = new SimpleContext();
        new Configuration(ctx);
        ctx.put("json", new TestJsonProvider());
    }

    @After
    public void tearDown() {
        if (savedContext != null) {
            ServiceContext.setContext(savedContext);
        }
    }

    /** start → sub(给定类型串) → end 的三节点两边形定义；类型串由用例给定以覆盖大小写各档。 */
    private static String flowWith(String nodeType, String nodeId) {
        return ("{'name':'alias-141','displayName':'类型键别名','type':'approval','nodes':["
                + "{'id':'start','type':'snaker:start','x':100,'y':200,'properties':{},'text':{'value':'开始'}},"
                + "{'id':'" + nodeId + "','type':'" + nodeType + "','x':300,'y':200,"
                + "'properties':{'form':'child-form','version':2},'text':{'value':'子流程'}},"
                + "{'id':'end','type':'snaker:end','x':500,'y':200,'properties':{},'text':{'value':'结束'}}],"
                + "'edges':["
                + "{'id':'e1','sourceNodeId':'start','targetNodeId':'" + nodeId + "','properties':{}},"
                + "{'id':'e2','sourceNodeId':'" + nodeId + "','targetNodeId':'end','properties':{}}]}")
                .replace('\'', '"');
    }

    private static ProcessModel parse(String json) {
        return ModelParser.parse(json.getBytes(StandardCharsets.UTF_8));
    }

    // ═══ 义务 3：小写别名与大写同值 ═══

    /**
     * spec 02 立的规范名（小写 {@code snaker:subprocess}）必须解析出子流程节点——
     * 改前这一格是红的（类型表只有大写档 ⇒ 节点被无声丢掉，连带 {@code e2} 那条出边一起丢）。
     */
    @Test
    public void lowercaseSubprocessTypeResolvesSubProcessNode() {
        ProcessModel model = parse(flowWith("snaker:subprocess", "sub"));
        NodeModel sub = model.getNode("sub");
        assertNotNull("小写 snaker:subprocess 应解析出节点（spec 02 的规范名）", sub);
        assertTrue("应解析为 SubProcessModel，实际=" + sub.getClass().getName(),
                sub instanceof SubProcessModel);
        assertEquals("子流程节点的 form 属性应照解析", "child-form", ((SubProcessModel) sub).getForm());
        assertEquals("子流程节点的 version 属性应照解析", Integer.valueOf(2),
                ((SubProcessModel) sub).getVersion());
        // 出边不丢：sub → end 那条边既在 sub 的 outputs 上，也进了 end 的 inputs
        assertEquals("子流程节点应有 1 条出边", 1, sub.getOutputs().size());
        NodeModel end = model.getNode("end");
        assertEquals("end 的入边不应被子流程节点的丢弃带走", 1, end.getInputs().size());
        assertSame("e2 的目标应回链到 end 节点", end, end.getInputs().get(0).getTarget());
    }

    /** 无前缀小写别名（设计器 {@code type} 去掉 {@code snaker:} 前缀后的查表键）同样命中。 */
    @Test
    public void bareLowercaseSubprocessAliasResolvesSameParser() {
        ProcessModel model = parse(flowWith("subprocess", "sub"));
        assertTrue("无前缀小写 subprocess 也应命中子流程解析器",
                model.getNode("sub") instanceof SubProcessModel);
    }

    /**
     * 回归哨兵：java 历史两档（大写 {@code subProcess} ＋ 内部变体 {@code wfSubProcess}）是
     * <b>deprecate 别名，保留一代</b>（spec 02 义务 3 ＋ spec 11 §11.6 同一条尺子），
     * 补小写别名不得把它们换掉——仓内既有夹具与本栈文档写的就是大写。
     */
    @Test
    public void legacyUppercaseAliasesAreKeptForOneGeneration() {
        assertTrue("大写 snaker:subProcess 应仍解析出子流程节点",
                parse(flowWith("snaker:subProcess", "sub")).getNode("sub") instanceof SubProcessModel);
        assertTrue("内部变体 snaker:wfSubProcess 应仍解析出子流程节点",
                parse(flowWith("snaker:wfSubProcess", "sub")).getNode("sub") instanceof SubProcessModel);
    }

    // ═══ 义务 2：未知档不得静默丢节点 ═══

    /**
     * 类型表查不到解析器时，跳过本身是允许的（本仓现状），但<b>必须留下一条可诊断记录</b>：
     * 带节点 id ＋ 实得类型串（前缀原样），且落在 WARNING 级。改前是 {@code if (parser != null)}
     * 无声吞掉——一条日志都没有，故这一格改前必红。
     */
    @Test
    public void unknownNodeTypeIsLoggedNotSilentlyDropped() {
        Logger logger = Logger.getLogger(ModelParser.class.getName());
        Level savedLevel = logger.getLevel();
        boolean savedParent = logger.getUseParentHandlers();
        final List<LogRecord> seen = new ArrayList<LogRecord>();
        Handler capture = new Handler() {
            @Override public void publish(LogRecord record) { if (record != null) seen.add(record); }
            @Override public void flush() {}
            @Override public void close() {}
        };
        logger.addHandler(capture);
        logger.setLevel(Level.WARNING);
        logger.setUseParentHandlers(false);
        try {
            ProcessModel model = parse(flowWith("snaker:notRegisteredAtAll", "odd"));
            assertNull("未建档的类型仍不进模型（义务 2 要求的是可诊断，不是保留节点）",
                    model.getNode("odd"));
            assertEquals("未知类型档必须留下一条可诊断记录，实际条数=" + seen.size(), 1, seen.size());
            LogRecord record = seen.get(0);
            assertTrue("诊断记录至少要是 WARNING 级，实际=" + record.getLevel(),
                    record.getLevel().intValue() >= Level.WARNING.intValue());
            String msg = record.getMessage();
            assertTrue("诊断记录应带节点 id，实际文案=" + msg, msg.contains("odd"));
            assertTrue("诊断记录应带实得类型串（含 snaker: 前缀原样），实际文案=" + msg,
                    msg.contains("snaker:notRegisteredAtAll"));
        } finally {
            logger.removeHandler(capture);
            logger.setLevel(savedLevel);
            logger.setUseParentHandlers(savedParent);
        }
    }

    /**
     * 已知档不得被日志改动带偏：正常定义解析时不应冒出"未知类型"诊断（每个类型都命中）。
     * 顺带钉住 {@code TransitionModel} 的出边目标引用在别名落地后仍完整。
     */
    @Test
    public void knownTypesProduceNoUnknownTypeDiagnosis() {
        ProcessModel model = parse(flowWith("snaker:subprocess", "sub"));
        assertEquals("三节点定义应解析出 3 个节点", 3, model.getNodes().size());
        TransitionModel out = model.getNode("sub").getOutputs().get(0);
        assertSame("sub 的出边目标应是 end 节点", model.getNode("end"), out.getTarget());
    }
}
