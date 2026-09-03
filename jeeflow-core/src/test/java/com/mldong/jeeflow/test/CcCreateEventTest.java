package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.Context;
import com.mldong.jeeflow.context.SimpleContext;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessEventTypeEnum;
import com.mldong.jeeflow.event.ProcessEvent;
import com.mldong.jeeflow.event.ProcessEventListener;
import com.mldong.jeeflow.spi.IOrgUserProvider;
import com.mldong.jeeflow.spi.IUserProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * CC_CREATE（抄送知会）事件契约（issues/102 六语言统一 · Java 栈）。
 *
 * <p>语义标准 = PHP 参考实现（jeeflow-php v1.3.8 {@code 381bed0}）+ spec 04-extensions §4.4：
 * 引擎在 {@code createCcInstance}（逐行 INSERT）后<b>逐抄送人</b> fire {@code CC_CREATE}，
 * {@code sourceId=instanceId}、{@code ccActorId=抄送人 id}（直传事件体，监听器免反查 cc 表），
 * fire 在发起事务内。码值：4 号位复用（原 {@code PROCESS_TASK_END} 从不 fire 的死码），
 * 与 PHP {@code CC_CREATE=4} 对齐，1/2/3 不变。
 *
 * <p>本测试钉死两条契约：
 * <ol>
 *   <li>带抄送人发起 → 逐抄送人 fire CC_CREATE，事件数=抄送人数、ccActorId 顺序一致、
 *       sourceId=instanceId、与 cc 实例逐行粒度一一对应；</li>
 *   <li><b>纯增量零副作用</b>：未装配任何监听器时，cc 实例照常落库、fire 侧不抛错、
 *       不产生任何监听器副作用（引擎行为与该栈上一版对未 fire 的事件逐字节一致）。</li>
 * </ol>
 *
 * <p>注：{@code ServiceContext} 是静态单例，本测试在 {@code @Before} 保存当前上下文、
 * {@code @After} 还原，使「未装配监听器」断言不受兄弟测试注册到的捕获监听器污染。</p>
 */
public class CcCreateEventTest {

    private Context savedContext;
    private MemoryProcessRepository repo;
    /** 捕获的 CC_CREATE 事件列表（仅 withCapture 时填充）。 */
    private final List<ProcessEvent> ccEvents = new CopyOnWriteArrayList<>();

    @Before
    public void setUp() {
        savedContext = ServiceContext.getContext();
        repo = new MemoryProcessRepository();
    }

    @After
    public void tearDown() {
        if (savedContext != null) {
            ServiceContext.setContext(savedContext);
        }
    }

    /**
     * 建一个干净上下文（repo/json/expr/user/org，无 TransactionTemplate → runInTx 内联执行）。
     * 注：{@code new Configuration(ctx)} 会把 ctx 设为当前 ServiceContext 并注册内置解析器，
     * 故 repo 等 SPI 须在 Configuration 构造之后 put（否则被 setContext 覆盖丢失）。
     */
    private JeeflowEngineImpl freshEngine(boolean withCapture) {
        SimpleContext ctx = new SimpleContext();
        Configuration config = new Configuration(ctx);
        ctx.put("repository", repo);
        ctx.put("json", new TestJsonProvider());
        ctx.put("expr", new TestExpressionEvaluator());
        ctx.put("user", new IUserProvider() {
            @Override
            public UserInfo getUser(String userId) {
                UserInfo u = new UserInfo();
                u.setUserId(userId);
                u.setRealName("用户" + userId);
                return u;
            }
        });
        ctx.put("org", new IOrgUserProvider() {
            @Override
            public List<String> findDeptLeaders(String deptId) { return null; }
            @Override
            public List<String> findDeptMainLeaders(String deptId) { return null; }
            @Override
            public List<String> findByRole(String roleCode) { return null; }
        });
        if (withCapture) {
            // findList(ProcessEventListener.class) 按 isInstance 扫描全部注册项，
            // 故字符串名注册即可被发现（与集成层 @PostConstruct put 的装配方式一致）。
            ctx.put("ccCreateCapture", (ProcessEventListener) event -> {
                if (event.getEventType() == ProcessEventTypeEnum.CC_CREATE) {
                    ccEvents.add(event);
                }
            });
        }
        JeeflowEngineImpl engine = new JeeflowEngineImpl();
        engine.configure(config);
        return engine;
    }

    private ProcessInstance.ProcessDefine addSimpleDefine() throws Exception {
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName("ccsimple");
        def.setDisplayName("抄送契约流程");
        def.setType("approval");
        def.setState(1);
        def.setVersion(1);
        def.setContent(Files.readAllBytes(Paths.get("src/test/resources/flows/01-simple.json")));
        repo.addDefine(def);
        return def;
    }

    @Test
    public void ccCreateFiresPerActorMatchingCcRows() throws Exception {
        JeeflowEngineImpl engine = freshEngine(true);
        ProcessInstance.ProcessDefine def = addSimpleDefine();

        FlowData args = FlowData.create().set(FlowConst.CC_ACTORS_START, "1001,1002");
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "zhangsan", args);
        assertNotNull("实例应已创建", inst.getInstanceId());

        // 契约①：逐抄送人 fire，事件数 = 抄送人数（2）
        assertEquals("应逐抄送人 fire 2 个 CC_CREATE 事件", 2, ccEvents.size());
        // 契约②：ccActorId 顺序与发起顺序一致
        assertEquals("第 1 个事件 ccActorId 应为 1001", "1001", ccEvents.get(0).getCcActorId());
        assertEquals("第 2 个事件 ccActorId 应为 1002", "1002", ccEvents.get(1).getCcActorId());
        // 契约③：sourceId 恒为 instanceId（监听器 findInstanceById 反查发起人/流程名）
        for (ProcessEvent e : ccEvents) {
            assertEquals("CC_CREATE.sourceId 应为 instanceId", inst.getInstanceId(), e.getSourceId());
            assertEquals("事件类型应为 CC_CREATE", ProcessEventTypeEnum.CC_CREATE, e.getEventType());
        }
        // 契约④：与 cc 实例逐行粒度一一对应（2 抄送人 → 2 行 cc 实例）
        assertEquals("cc 实例应落 2 行（与 fire 粒度对应）",
                Arrays.asList("1001", "1002"), repo.ccActorsForTest(inst.getInstanceId()));
    }

    @Test
    public void ccCreateWithNoListenerIsPureIncremental() throws Exception {
        JeeflowEngineImpl engine = freshEngine(false);
        ProcessInstance.ProcessDefine def = addSimpleDefine();

        // 契约⑤：未装配监听器时发起带抄送人流程——不抛错（fire 侧对空监听器列表安全）
        ProcessInstance inst = engine.startProcessInstanceById(
                def.getId(), "zhangsan", FlowData.create().set(FlowConst.CC_ACTORS_START, "2001,2002"));
        assertNotNull(inst.getInstanceId());

        // 契约⑥：cc 实例照常落库（纯增量：未装监听器不改变 cc 数据行为）
        assertEquals("未装监听器时 cc 实例仍应落 2 行",
                Arrays.asList("2001", "2002"), repo.ccActorsForTest(inst.getInstanceId()));

        // 契约⑦：零监听器副作用——干净上下文里没有任何监听器，故无消息写入
        List<ProcessEventListener> listeners = ServiceContext.findList(ProcessEventListener.class);
        assertTrue("干净上下文中不应有任何监听器（保证零副作用断言前提）",
                listeners == null || listeners.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void publisherListenerExceptionIsolated() throws Exception {
        // issues/104 P2 兜底语义：单监听器异常只记 warn 不传播——
        // 不得影响引擎主流程，也不得中断后续监听器（对齐 PHP per-listener catch）
        SimpleContext ctx = new SimpleContext();
        Configuration config = new Configuration(ctx);
        ctx.put("repository", repo);
        ctx.put("json", new TestJsonProvider());
        ctx.put("expr", new TestExpressionEvaluator());
        ctx.put("user", new IUserProvider() {
            @Override
            public UserInfo getUser(String userId) {
                UserInfo u = new UserInfo();
                u.setUserId(userId);
                return u;
            }
        });
        ctx.put("org", new IOrgUserProvider() {
            @Override
            public List<String> findDeptLeaders(String deptId) { return null; }
            @Override
            public List<String> findDeptMainLeaders(String deptId) { return null; }
            @Override
            public List<String> findByRole(String roleCode) { return null; }
        });
        java.util.List<String> seen = new CopyOnWriteArrayList<>();
        ctx.put("badListener", (ProcessEventListener) event -> {
            throw new RuntimeException("boom");
        });
        ctx.put("goodListener", (ProcessEventListener) event -> seen.add("second"));
        JeeflowEngineImpl engine = new JeeflowEngineImpl();
        engine.configure(config);

        ProcessInstance.ProcessDefine def = addSimpleDefine();
        FlowData args = FlowData.create().set(FlowConst.CC_ACTORS_START, "3001");
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "zhangsan", args);
        assertNotNull("监听器异常不应影响发起主流程", inst.getInstanceId());
        assertTrue("异常后后续监听器应仍被调用", !seen.isEmpty());
    }
}
