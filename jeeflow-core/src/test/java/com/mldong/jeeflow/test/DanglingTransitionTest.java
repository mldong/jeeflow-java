package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.Context;
import com.mldong.jeeflow.context.SimpleContext;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.ProcessInstanceStateEnum;
import com.mldong.jeeflow.model.NodeModel;
import com.mldong.jeeflow.model.ProcessModel;
import com.mldong.jeeflow.model.TransitionModel;
import com.mldong.jeeflow.parser.ModelParser;
import com.mldong.jeeflow.spi.IOrgUserProvider;
import com.mldong.jeeflow.spi.IUserProvider;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 「未知档节点在链路中间」时，指向它的那条边必须<b>落穿成不推进</b>，不得把办理打崩
 * —— issues/143（G4 义务 2 的后半，由 php 会话在 issues/142 A 批普查时撞出来，
 * 本栈逐字同形故同样中招；见 {@code TransitionModel.java:23-32} 与
 * {@code ModelParser.java:99-109}）。
 *
 * <p><b>病灶形状</b>：解析期类型表查不到解析器时记一条 WARNING 后 {@code continue}
 * （G4 义务 2，本仓 3d1fc98 才把日志补上），节点不进模型；但<b>指向它的边</b>照样挂在
 * 上游节点的 {@code outputs} 上，而 {@code setTarget} 只在目标节点存在于模型里时才发生
 * ⇒ 边留着、{@code target} 恒 {@code null}。令牌走到上游节点并办结 ⇒
 * {@code runOutTransition} → {@code TransitionModel.execute} 的 else 分支裸调
 * {@code target.execute(execution)} ⇒ <b>NullPointerException</b>：
 * 一次配置写错（大小写、拼错、设计器脏数据）把整次办理打崩，
 * 正面违反 spec 02 §6.2 第 2 条「严禁抛错打断建单」（owner 2026-09-30 拍）与 spec 04
 * 「节点属性配错不该把流程炸掉」。</p>
 *
 * <p><b>为什么"记日志再跳过"这句话不够</b>：义务 2 只写了"再决定跳过"，没写"指向它的那条边
 * 怎么办"。本栈的对象图形状（先建节点、再按名字回链 target）里，丢节点必然留悬边；
 * 按 id 现查目标的那几栈（go/python/node/moon，以及把未知节点留在模型里标 Unknown 的 rust）
 * 不会炸，只是停住 ⇒ 八栈在这一格上分成"停住"与"崩"两派。
 * 判据取与多数派且可诊断的那一派：<b>记日志 + 停住</b>，不选"越过它继续"——
 * 未知档没被解析成任何模型，越过它等于用一条臆造的通路把跑不通的定义跑成功。</p>
 *
 * <p>夹具一律本类内联（不往共享 {@code src/test/resources/flows/} 加文件——那目录的份数
 * 是跨栈对账的读数）。日志捕获姿势同 {@link NodeTypeKeyAliasTest}／{@link CustomNodeHistoryTest}。</p>
 */
public class DanglingTransitionTest {

    private Context savedContext;
    private MemoryProcessRepository repo;
    private JeeflowEngine engine;
    private final List<LogRecord> transitionLogs = new ArrayList<LogRecord>();
    private Handler capture;
    private Logger transitionLogger;
    private Level savedLevel;
    private boolean savedParent;

    @Before
    public void setUp() {
        savedContext = ServiceContext.getContext();
        repo = new MemoryProcessRepository();
        SimpleContext ctx = new SimpleContext();
        Configuration config = new Configuration(ctx);
        ctx.put("repository", repo);
        ctx.put("json", new TestJsonProvider());
        ctx.put("expr", new TestExpressionEvaluator());
        ctx.put("user", new IUserProvider() {
            @Override
            public IUserProvider.UserInfo getUser(String userId) {
                IUserProvider.UserInfo u = new IUserProvider.UserInfo();
                u.setUserId(userId);
                u.setRealName("用户" + userId);
                return u;
            }
        });
        ctx.put("org", new IOrgUserProvider() {
            @Override public List<String> findDeptLeaders(String deptId) { return null; }
            @Override public List<String> findDeptMainLeaders(String deptId) { return null; }
            @Override public List<String> findByRole(String roleCode) { return null; }
        });
        engine = new JeeflowEngineImpl();
        engine.configure(config);

        transitionLogger = Logger.getLogger(TransitionModel.class.getName());
        savedLevel = transitionLogger.getLevel();
        savedParent = transitionLogger.getUseParentHandlers();
        transitionLogs.clear();
        capture = new Handler() {
            @Override public void publish(LogRecord record) { if (record != null) transitionLogs.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        transitionLogger.addHandler(capture);
        transitionLogger.setLevel(Level.ALL);
        transitionLogger.setUseParentHandlers(false);
    }

    @After
    public void tearDown() {
        if (transitionLogger != null && capture != null) {
            transitionLogger.removeHandler(capture);
            transitionLogger.setLevel(savedLevel);
            transitionLogger.setUseParentHandlers(savedParent);
        }
        if (savedContext != null) {
            ServiceContext.setContext(savedContext);
        }
    }

    // ═══ 1. 现场形状：节点不在模型里，而那条悬边的 target 就是 null ═══

    /**
     * 不是判据，是"为什么会崩"的取证：未知档节点被解析期跳过（义务 2 要求的行为），
     * 而 apply 的那条出边<b>仍在</b> {@code outputs} 里、{@code getTarget()} 是 null。
     * {@code runOutTransition} 会把它逐条 execute ⇒ else 分支裸调就是 NPE。
     */
    @Test
    public void danglingEdgeShapeIsReproducedAtParseTime() {
        ProcessModel model = ModelParser.parse(
                chain("dangling-shape", "snaker:notRegisteredAtAll").getBytes(StandardCharsets.UTF_8));

        assertNull("夹具前提：未建档的类型不进模型（G4 义务 2 要求的是可诊断，不是保留节点）",
                model.getNode("odd"));
        NodeModel apply = model.getNode("apply");
        assertNotNull("夹具前提：apply 正常入模型", apply);
        assertEquals("指向未知节点的边仍挂在 apply 的 outputs 上", 1, apply.getOutputs().size());
        TransitionModel dangling = apply.getOutputs().get(0);
        assertNull("该边的 target 没被接上（解析期只在目标节点存在时 setTarget）⇒ 崩的那一枚 null",
                dangling.getTarget());
        assertEquals("边上的 to 仍写着未知节点的 id（可诊断要用它）", "odd", dangling.getTo());
    }

    // ═══ 2. 核心判据：办理不得被打崩，实例停在未知节点处 ═══

    /**
     * 发起 → 办理 apply（令牌随即撞上指向未知节点的那条边）。修复后四件都要成立：
     * ① 不抛任何 Throwable（改前这里是 NPE）；② 实例仍 DOING（停住＝不办结，
     * 未知节点没被"越过"）；③ 库里不为 {@code odd} 产生任何行（既不建待办也不建留痕）；
     * ④ 落穿那一刻留一条可诊断记录，带 {@code from=apply} 与 {@code to=odd}。
     */
    @Test
    public void executingUpstreamTaskDoesNotCrashOnDanglingEdge() {
        ProcessInstance.ProcessDefine def = addDefine(chain("dangling-execute", "snaker:notRegisteredAtAll"));

        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "user1", FlowData.create());
        List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), null);
        assertTrue("夹具前提：发起后 apply 有一条待办", doing != null && !doing.isEmpty());
        Long applyTaskId = doing.get(0).getTaskId();

        try {
            engine.executeProcessTask(applyTaskId, "user1", FlowData.create());
        } catch (Throwable ex) {
            fail("办理撞上「指向未知节点的边」不得把流程打崩（spec 02 §6.2 第 2 条严禁抛错打断建单／"
                    + "spec 04 节点属性配错不该把流程炸掉），实得 "
                    + ex.getClass().getName() + ": " + ex.getMessage());
        }

        ProcessInstance fresh = repo.findInstanceById(inst.getInstanceId());
        assertNotNull(fresh);
        assertEquals("令牌停在未知节点处（不办结＝不假装那条边跑得通）",
                ProcessInstanceStateEnum.DOING.getCode(), fresh.getState());

        List<String> names = new ArrayList<String>();
        for (ProcessTask t : repo.findHistoryTasks(inst.getInstanceId())) {
            names.add(t.getTaskName());
        }
        assertTrue("未知档不许产生任何行（既不建待办也不建留痕），实际行名=" + names,
                !names.contains("odd"));

        LogRecord hit = null;
        for (LogRecord r : transitionLogs) {
            if (r.getMessage() != null && r.getMessage().contains("to=odd")) {
                hit = r;
            }
        }
        assertNotNull("落穿那一刻也要留可诊断记录（带 from/to），实际条数=" + transitionLogs.size(), hit);
        assertTrue("诊断记录至少 WARNING 级，实际=" + hit.getLevel(),
                hit.getLevel().intValue() >= Level.WARNING.intValue());
        assertTrue("记录要指出上游节点，实际文案=" + hit.getMessage(), hit.getMessage().contains("apply"));
    }

    // ═══ 3. 反向对照：已知档链路照旧跑得通（守卫不得做成什么都吞）═══

    /**
     * 同一条流把未知档换成正常 {@code snaker:custom}（并让 end 接上）：办理后实例必须办结，
     * 且<b>不得</b>冒出落穿记录。这一格钉的是"加了守卫但没把正常出边一起吞掉"。
     */
    @Test
    public void knownChainStillFlowsToEndAfterGuard() {
        String json = ("{'name':'dangling-known-ok','displayName':'已知档对照','type':'approval','nodes':["
                + "{'id':'start','type':'snaker:start','x':100,'y':200,'properties':{},'text':{'value':'开始'}},"
                + "{'id':'apply','type':'snaker:task','x':260,'y':200,"
                + "'properties':{'assignee':'applicant','taskType':0,'performType':0},'text':{'value':'发起申请'}},"
                + "{'id':'c1','type':'snaker:custom','x':420,'y':200,"
                + "'properties':{'clazz':'com.mldong.jeeflow.test.TestCustomHandler','methodName':'execute','args':'param1'},'text':{'value':'通知外部系统'}},"
                + "{'id':'end','type':'snaker:end','x':620,'y':200,'properties':{},'text':{'value':'结束'}}],"
                + "'edges':["
                + "{'id':'e0','sourceNodeId':'start','targetNodeId':'apply','properties':{}},"
                + "{'id':'e1','sourceNodeId':'apply','targetNodeId':'c1','properties':{}},"
                + "{'id':'e2','sourceNodeId':'c1','targetNodeId':'end','properties':{}}]}")
                .replace('\'', '"');
        ProcessInstance.ProcessDefine def = addDefine(json);

        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "user1", FlowData.create());
        List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), null);
        assertFalse("夹具前提：发起后有 apply 待办", doing.isEmpty());
        engine.executeProcessTask(doing.get(0).getTaskId(), "user1", FlowData.create());

        ProcessInstance fresh = repo.findInstanceById(inst.getInstanceId());
        assertNotNull(fresh);
        assertEquals("已知档链路必须照旧走到终点：守卫不得把正常出边也吞掉",
                ProcessInstanceStateEnum.FINISHED.getCode(), fresh.getState());
        for (LogRecord r : transitionLogs) {
            String m = r.getMessage();
            assertTrue("已知档不该产生落穿记录，实际=" + m, m == null || !m.contains("目标节点不在模型里"));
        }
    }

    /** 结束节点的入边仍回链（对照 3d1fc98 那条"别名落地后出边目标引用完整"的判据方向） */
    @Test
    public void knownEdgeTargetIsWiredSoGuardIsNotTaken() {
        ProcessModel model = ModelParser.parse(
                chain("dangling-wired", "snaker:task").getBytes(StandardCharsets.UTF_8));
        NodeModel apply = model.getNode("apply");
        assertNotNull(apply);
        assertSame("已知档的出边 target 必须已接上（守卫这一支不该被正常路径走到）",
                model.getNode("odd"), apply.getOutputs().get(0).getTarget());
    }

    // ═══ 夹具 ═══

    /** start → apply(snaker:task) → odd($oddType) → end 的线性链。 */
    private static String chain(String name, String oddType) {
        return ("{'name':'" + name + "','displayName':'未知档落穿用例','type':'approval','nodes':["
                + "{'id':'start','type':'snaker:start','x':100,'y':200,'properties':{},'text':{'value':'开始'}},"
                + "{'id':'apply','type':'snaker:task','x':260,'y':200,"
                + "'properties':{'assignee':'applicant','taskType':0,'performType':0},'text':{'value':'发起申请'}},"
                + "{'id':'odd','type':'" + oddType + "','x':420,'y':200,'properties':{},'text':{'value':'没登记的类型'}},"
                + "{'id':'end','type':'snaker:end','x':620,'y':200,'properties':{},'text':{'value':'结束'}}],"
                + "'edges':["
                + "{'id':'e0','sourceNodeId':'start','targetNodeId':'apply','properties':{}},"
                + "{'id':'e1','sourceNodeId':'apply','targetNodeId':'odd','properties':{}},"
                + "{'id':'e2','sourceNodeId':'odd','targetNodeId':'end','properties':{}}]}")
                .replace('\'', '"');
    }

    private ProcessInstance.ProcessDefine addDefine(String json) {
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName("dangling-" + System.nanoTime());
        def.setDisplayName("未知档落穿用例");
        def.setType("approval");
        def.setState(1);
        def.setVersion(1);
        def.setContent(json.getBytes(StandardCharsets.UTF_8));
        repo.addDefine(def);
        assertNotNull(def.getId());
        return def;
    }
}
