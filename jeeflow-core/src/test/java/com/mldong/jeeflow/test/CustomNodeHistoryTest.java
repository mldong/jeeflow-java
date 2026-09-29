package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.Context;
import com.mldong.jeeflow.context.SimpleContext;
import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessInstanceStateEnum;
import com.mldong.jeeflow.enums.ProcessTaskStateEnum;
import com.mldong.jeeflow.event.ProcessEvent;
import com.mldong.jeeflow.event.ProcessEventListener;
import com.mldong.jeeflow.handler.IHandler;
import com.mldong.jeeflow.spi.IOrgUserProvider;
import com.mldong.jeeflow.spi.IUserProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 记录类（custom）节点的两条硬要求回归 —— issues/142 · spec 02-flow-definition.md §6.2
 * （owner 2026-09-30 逐条拍）。
 *
 * <p><b>本类钉的两个缺陷</b>：</p>
 * <ol>
 *   <li><b>历史行必须真落库</b>（§6.2 第 1 条）。旧形状：{@code CustomModel.exec} 丢弃
 *       {@code ProcessInstance.createHistoryTask} 的返回值，行只进了聚合的 {@code tasks}；
 *       引擎的 {@code persistTasks} 只遍历 {@code exec.getProcessTaskList()}，
 *       {@code updateInstance} 的级联又只对 {@code taskId != null} 的行发 UPDATE，
 *       而 {@code ProcessTask.create} 从不赋 taskId ⇒ 那条 {@code task_state=20} 的行
 *       <b>永远没有 INSERT</b>。自家 {@code JeeflowFullTest#test08CustomNode} 对历史行零断言，
 *       所以测试照不出来。</li>
 *   <li><b>{@code clazz} 解析不了不许打断建单</b>（§6.2 第 2 条）。旧形状是三条显式
 *       {@code RuntimeException}（实例化失败／无法找到方法名称／方法调用失败）；
 *       跟改成"记 WARNING + 照常落历史行 + 令牌继续流转"，与 spec 04
 *       「误配的到期档落穿→NULL、节点属性配错不该把流程炸掉」（java 6bdf41b）同一条哲学。</li>
 * </ol>
 *
 * <p><b>同时钉住"落库"与"fire 码 3"的解耦</b>（实施约束 1）：§6.1 定性记录类"不该有待办"，
 * 而码 3 {@code PROCESS_TASK_START} 表达的事实是"新待办产生"（spec §11.3）⇒ 历史行落库了
 * 也<b>不许</b> fire 码 3。这里的判据是 recorder 收到的<b>规范名全序列</b>逐位相等，
 * 不是"包含"——多一支码 3 必须照出来。</p>
 *
 * <p>负向对照（防止把豁免做成"什么都吞"）：处理器<b>自身</b>抛异常（{@code IHandler.handle}
 * 内部／被反射方法的<b>方法体</b>）照旧外抛，两条用例各自钉死。</p>
 *
 * <p>夹具形状：流程 JSON 一律本类内联（不往共享 {@code src/test/resources/flows/} 加文件——
 * 那目录的份数是跨栈对账的读数，加一格要七仓跟着动，判据见 issues/126 §"附带"）。
 * 处理器类是本类的 public static 嵌套类，JVM 里按二元名 {@code 外部类$嵌套类} 反射可达。</p>
 *
 * <p>上下文隔离同 {@link EventContractRecorderTest}：{@code ServiceContext} 是静态的，
 * 兄弟测试类会往里塞捕获监听器 ⇒ 每个用例换一枚干净的 {@code SimpleContext}，
 * {@code @After} 还原，序列断言才不受串味。</p>
 */
public class CustomNodeHistoryTest {

    private static final String START = "PROCESS_INSTANCE_START";       // 1
    private static final String INSTANCE_END = "PROCESS_INSTANCE_END";  // 2
    private static final String TASK_START = "PROCESS_TASK_START";      // 3

    /** 本类嵌套处理器类的二元名前缀（Class.forName 认这个写法） */
    private static final String HANDLER_PREFIX = "com.mldong.jeeflow.test.CustomNodeHistoryTest$";

    private Context savedContext;
    private MemoryProcessRepository repo;
    private JeeflowEngine engine;

    /** 事件 recorder：按 fire 顺序落档 */
    private final List<ProcessEvent> events = new CopyOnWriteArrayList<>();
    /** CustomModel 的 WARNING 捕获（豁免只许"记日志继续"，日志本身要可诊断 ⇒ 断文案） */
    private final List<String> warnings = new CopyOnWriteArrayList<>();

    private Logger customLogger;
    private Handler logCapture;

    @Before
    public void setUp() {
        savedContext = ServiceContext.getContext();
        // 只加一个 handler 抓 WARNING，不改 useParentHandlers（控制台照旧可看）
        customLogger = Logger.getLogger("com.mldong.jeeflow.model.CustomModel");
        logCapture = new Handler() {
            @Override public void publish(LogRecord record) {
                if (record != null && record.getMessage() != null) {
                    warnings.add(record.getMessage());
                }
            }
            @Override public void flush() { }
            @Override public void close() { }
        };
        logCapture.setLevel(Level.ALL);
        customLogger.addHandler(logCapture);
        freshStack();
    }

    @After
    public void tearDown() {
        if (customLogger != null && logCapture != null) {
            customLogger.removeHandler(logCapture);
        }
        if (savedContext != null) {
            ServiceContext.setContext(savedContext);
        }
    }

    /** 干净上下文（repo/json/expr/user/org ＋ 唯一事件 recorder） */
    private void freshStack() {
        repo = new MemoryProcessRepository();
        events.clear();
        warnings.clear();
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
        ctx.put("customNodeEventCapture", (ProcessEventListener) e -> events.add(e));
        engine = new JeeflowEngineImpl();
        engine.configure(config);
    }

    // ═══════════════════════════════════════════
    // 一档：历史行真落库（发起腿）＋ 不 fire 码 3
    // ═══════════════════════════════════════════

    /**
     * 走<b>发起腿</b>的短流（start → custom → end，中间一个待办都没有）：
     * {@code startProcessInstanceById} 自己有一套"任务落库 → updateInstance → 码 2 flush"的腿，
     * 与 {@code persistTasks} 是两条路——病灶只补一条腿的话，这条路上的历史行依然进不了库。
     *
     * <p>断言三件：① 历史行在<b>仓储</b>里查得到（state 20、分到 taskId、参与者＝操作人、
     * 首节点标记按真实拓扑＝true、{@code task_parent_id} 按建单不变量＝0）；
     * ② 实例走到 end（state 20）；③ 事件序列是 {@code [1, 2]}——<b>没有码 3</b>，
     * 且待办数为 0（它没把待办列表抬高）。</p>
     */
    @Test
    public void historyRowIsPersistedOnStartLegAndFiresNoTaskStart() {
        ProcessInstance.ProcessDefine def = addDefine(
                flowWithSingleNode("\"clazz\":\"" + HANDLER_PREFIX + "RecordingHandler\","
                        + "\"methodName\":\"execute\",\"args\":\"\",\"val\":\"customResult\""));

        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "zhangsan", FlowData.create());

        ProcessTask history = findRowByTaskName(inst.getInstanceId(), "c1");
        assertNotNull("custom 节点的历史行必须落到仓储（§6.2 第 1 条：只在聚合里 append 一条不算做到）", history);
        assertNotNull("历史行必须经 INSERT 腿分到 taskId（缺陷原形：ProcessTask.create 从不赋 taskId ⇒ updateInstance 级联永远不碰它）",
                history.getTaskId());
        assertEquals("记录类节点建行即已完成态", ProcessTaskStateEnum.FINISHED.getCode(), history.getTaskState());
        assertEquals("历史行参与者＝当前操作人（留痕主体，不是待办收单人）",
                java.util.Arrays.asList("zhangsan"), repo.findTaskActors(history.getTaskId()));
        assertEquals("发起腿没有当前任务 ⇒ 建单不变量落 0（issues/121 P1，不许在改造时丢掉）",
                Long.valueOf(0L), history.getParentTaskId());
        assertEquals("行级首节点标记按真实拓扑算（本流 custom 就是 start 的直接后继 ⇒ true）",
                Boolean.TRUE, history.getVariables().get(FlowConst.IS_FIRST_TASK_NODE));
        assertTrue("记录类节点不产生待办：仓储里不许有 task_state=10 的行（待办数不因它增加）",
                repo.findDoingTasks(inst.getInstanceId(), null).isEmpty());
        assertEquals("令牌必须继续流转到结束节点", ProcessInstanceStateEnum.FINISHED.getCode(),
                repo.findInstanceById(inst.getInstanceId()).getState());
        assertEquals("落库了也不许 fire 码 3（PROCESS_TASK_START 表达\"新待办产生\"，§6.1 定性记录类不该有待办）",
                java.util.Arrays.asList(START, INSTANCE_END), names());
        assertEquals("码 3 一支都不许出现：" + names(), 0, countOf(TASK_START));
    }

    // ═══════════════════════════════════════════
    // 二档：clazz 解析不了 ⇒ 记 WARNING + 照常落历史行 + 令牌继续
    // ═══════════════════════════════════════════

    /** clazz 填了类路径上不存在／未注册的类：不抛错、历史行照落、流程继续、WARNING 文案可诊断到"未注册" */
    @Test
    public void unregisteredClazzLogsWarningAndFlowContinues() {
        ProcessInstance.ProcessDefine def = addDefine(
                flowWithSingleNode("\"clazz\":\"com.mldong.jeeflow.test.NoSuchHandler\","
                        + "\"methodName\":\"execute\",\"args\":\"\",\"val\":\"customResult\""));

        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "lisi", FlowData.create());

        ProcessTask history = findRowByTaskName(inst.getInstanceId(), "c1");
        assertNotNull("clazz 解析不了也要照常落历史行（§6.2 第 2 条：记日志 + 落行 + 继续）", history);
        assertNotNull(history.getTaskId());
        assertEquals(ProcessInstanceStateEnum.FINISHED.getCode(),
                repo.findInstanceById(inst.getInstanceId()).getState());
        assertEquals(java.util.Arrays.asList(START, INSTANCE_END), names());
        assertEquals("记日志继续只许留一条 WARNING", 1, warnings.size());
        String warning = warnings.get(0);
        assertTrue("WARNING 要能诊断到\"处理器未注册\"这一档：" + warning,
                warning.contains("找不到") || warning.contains("未注册"));
        assertTrue("WARNING 要带上实得的 clazz 串：" + warning,
                warning.contains("com.mldong.jeeflow.test.NoSuchHandler"));
    }

    /**
     * {@code clazz} 为空串：与"未注册"<b>分档</b>——两条都记日志继续，但文案要分别可诊断
     * （§6.2 第 2 条点名 c# 把两者合成同一个异常的覆盖面问题）。
     */
    @Test
    public void emptyClazzLogsItsOwnDiagnosisAndFlowContinues() {
        ProcessInstance.ProcessDefine def = addDefine(
                flowWithSingleNode("\"clazz\":\"\","
                        + "\"methodName\":\"execute\",\"args\":\"\",\"val\":\"customResult\""));

        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "wangwu", FlowData.create());

        assertNotNull("clazz 为空同样要落历史行", findRowByTaskName(inst.getInstanceId(), "c1"));
        assertEquals(ProcessInstanceStateEnum.FINISHED.getCode(),
                repo.findInstanceById(inst.getInstanceId()).getState());
        assertEquals(1, warnings.size());
        assertTrue("空串档的文案要写清是\"没配 clazz\"，不能与\"未注册\"混成一条：" + warnings.get(0),
                warnings.get(0).contains("未配置 clazz"));
    }

    /**
     * 纯空白的 {@code clazz}（{@code "   "}）也要落到"没配 clazz"那一档，不许漂到"未注册"那一档——
     * 这格钉的正是 §6.2 那条"两档分别可诊断"：判据若用 {@code isEmpty} 而不是 {@code isBlank}，
     * {@code Class.forName("")} 抛 {@code ClassNotFoundException} ⇒ 文案变成"类路径上找不到"，
     * 两档当场合成一档（c# 现在就是那个形状）。
     */
    @Test
    public void blankClazzUsesTheUnconfiguredDiagnosisNotUnregistered() {
        ProcessInstance.ProcessDefine def = addDefine(
                flowWithSingleNode("\"clazz\":\"   \","
                        + "\"methodName\":\"execute\",\"args\":\"\",\"val\":\"customResult\""));

        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "chenjiu", FlowData.create());

        assertNotNull(findRowByTaskName(inst.getInstanceId(), "c1"));
        assertEquals(1, warnings.size());
        assertTrue("空白 clazz 的文案必须是\"未配置 clazz\"那一档，不许漂成\"找不到/未注册\"：" + warnings.get(0),
                warnings.get(0).contains("未配置 clazz"));
        assertFalse("两档不许合成一条：" + warnings, warnings.get(0).contains("找不到"));
    }

    /**
     * {@code methodName} 在处理器类上找不到 ⇒ 本实现<b>判它归"配错形状"</b>（判据全文写在
     * {@code CustomModel#invokeHandler} 的方法注释）：处理器一行业务代码都没跑，
     * 失败原因和用户把 {@code clazz} 写错、把到期档写成落穿的同族，都是节点属性配错。
     * ⇒ 记 WARNING、历史行照落、令牌继续。
     */
    @Test
    public void missingMethodNameIsMisconfigurationAndFlowContinues() {
        ProcessInstance.ProcessDefine def = addDefine(
                flowWithSingleNode("\"clazz\":\"" + HANDLER_PREFIX + "PlainPojo\","
                        + "\"methodName\":\"noSuchMethod\",\"args\":\"\",\"val\":\"customResult\""));

        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "zhaoliu", FlowData.create());

        assertNotNull("方法找不到也只配记日志，不许把整条建单打掉",
                findRowByTaskName(inst.getInstanceId(), "c1"));
        assertEquals(ProcessInstanceStateEnum.FINISHED.getCode(),
                repo.findInstanceById(inst.getInstanceId()).getState());
        assertEquals(1, warnings.size());
        assertTrue("WARNING 要带上找不到的是哪个方法名：" + warnings.get(0),
                warnings.get(0).contains("noSuchMethod"));
    }

    // ═══════════════════════════════════════════
    // 三档：负向对照——处理器自身执行失败照旧外抛（豁免不是"什么都吞"）
    // ═══════════════════════════════════════════

    /** {@code IHandler.handle} 自己抛异常 ⇒ 业务错误，照旧外抛，且不记"配错"档的 WARNING */
    @Test
    public void handlerBusinessExceptionStillPropagates() {
        ProcessInstance.ProcessDefine def = addDefine(
                flowWithSingleNode("\"clazz\":\"" + HANDLER_PREFIX + "BoomIHandler\","
                        + "\"methodName\":\"handle\",\"args\":\"\",\"val\":\"customResult\""));

        try {
            engine.startProcessInstanceById(def.getId(), "qianqi", FlowData.create());
            fail("处理器自身抛业务异常必须外抛（§6.2 明写不在豁免内）");
        } catch (IllegalStateException expected) {
            assertEquals("业务炸了", expected.getMessage());
        }
        assertTrue("业务异常不许被记成\"配置形状错\"的 WARNING：" + warnings, warnings.isEmpty());
        assertEquals("异常外抛 ⇒ 豁免没有变成吞异常", java.util.Arrays.asList(START), names());
    }

    /** 反射到的方法<b>方法体</b>里抛 ⇒ {@code InvocationTargetException} 解包后照旧外抛（保持既有包装文案） */
    @Test
    public void targetMethodBusinessExceptionStillPropagates() {
        ProcessInstance.ProcessDefine def = addDefine(
                flowWithSingleNode("\"clazz\":\"" + HANDLER_PREFIX + "PlainPojo\","
                        + "\"methodName\":\"boom\",\"args\":\"\",\"val\":\"customResult\""));

        try {
            engine.startProcessInstanceById(def.getId(), "sunba", FlowData.create());
            fail("方法体抛异常必须外抛，这里是业务错误不是配错形状");
        } catch (RuntimeException e) {
            assertTrue("外抛的包装文案保持既有的\"方法调用失败\"：" + e.getMessage(),
                    e.getMessage() != null && e.getMessage().contains("方法调用失败"));
            assertNotNull("cause 要带上方法体里那个原始业务异常，集成层按它判型", e.getCause());
            assertEquals("业务炸了", e.getCause().getMessage());
        }
        assertTrue(warnings.isEmpty());
    }

    // ═══ 处理器夹具（public static 嵌套类：Class.forName 按二元名可达，且有无参构造）═══

    /** 只留痕的 IHandler 夹具 */
    public static class RecordingHandler implements IHandler {
        @Override
        public void handle(Execution execution) {
            execution.getArgs().put(FlowConst.CUSTOM_RETURN_VAL, "customExecuted");
        }
    }

    /** 非 IHandler 的普通类夹具：走 methodName 反射那一支 */
    public static class PlainPojo {
        public String ok() {
            return "ok";
        }

        public void boom() {
            throw new IllegalStateException("业务炸了");
        }
    }

    /** 处理器自身抛业务异常的 IHandler 夹具 */
    public static class BoomIHandler implements IHandler {
        @Override
        public void handle(Execution execution) {
            throw new IllegalStateException("业务炸了");
        }
    }

    // ═══ 工具 ═══

    /** start → custom(c1) → end：一条待办都不产生的短流 */
    private static String flowWithSingleNode(String customProperties) {
        String json = ("{'name':'custom-history','displayName':'记录类节点流程','type':'approval','nodes':["
                + "{'id':'start','type':'snaker:start','x':100,'y':200,'properties':{},'text':{'value':'开始'}},"
                + "{'id':'c1','type':'snaker:custom','x':300,'y':200,'properties':PROPS,'text':{'value':'通知外部系统'}},"
                + "{'id':'end','type':'snaker:end','x':500,'y':200,'properties':{},'text':{'value':'结束'}}],"
                + "'edges':["
                + "{'id':'e1','sourceNodeId':'start','targetNodeId':'c1','properties':{}},"
                + "{'id':'e2','sourceNodeId':'c1','targetNodeId':'end','properties':{}}]}")
                .replace("PROPS", "{" + customProperties + "}")
                .replace('\'', '"');
        return json;
    }

    private ProcessInstance.ProcessDefine addDefine(String json) {
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName("custom-history-" + System.nanoTime());
        def.setDisplayName("记录类节点流程");
        def.setType("approval");
        def.setState(1);
        def.setVersion(1);
        def.setContent(json.getBytes(StandardCharsets.UTF_8));
        repo.addDefine(def);
        assertNotNull(def.getId());
        return def;
    }

    /**
     * 判据打在<b>仓储读回来的行</b>上，不是断内存聚合对象：
     * 仓储的任务存储只由 {@code saveTask}/{@code updateTask}/{@code updateInstance} 的
     * {@code taskId != null} 级联写入 ⇒ 摘掉 INSERT 腿，这一格立刻查不到行。
     */
    private ProcessTask findRowByTaskName(Long instanceId, String taskName) {
        List<ProcessTask> rows = repo.findHistoryTasks(instanceId);
        ProcessTask found = null;
        for (ProcessTask t : rows) {
            if (taskName.equals(t.getTaskName())) {
                found = t;
            }
        }
        return found;
    }

    private List<String> names() {
        List<String> names = new ArrayList<>();
        for (ProcessEvent e : events) {
            names.add(e.getEventType().name());
        }
        return names;
    }

    /** 码 3 计数：序列断言已经钉死"不多一支"，这一格是把"落库与 fire 解耦"单独写成一个可读判据 */
    private int countOf(String name) {
        int n = 0;
        for (String s : names()) {
            if (s.equals(name)) n++;
        }
        return n;
    }
}
