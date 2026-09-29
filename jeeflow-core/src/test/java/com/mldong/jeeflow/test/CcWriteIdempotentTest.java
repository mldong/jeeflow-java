package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.Context;
import com.mldong.jeeflow.context.SimpleContext;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessEventTypeEnum;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
import com.mldong.jeeflow.event.ProcessEvent;
import com.mldong.jeeflow.event.ProcessEventListener;
import com.mldong.jeeflow.facade.JeeflowFacade;
import com.mldong.jeeflow.spi.IOrgUserProvider;
import com.mldong.jeeflow.spi.IUserProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 抄送写侧判重＝幂等空操作（issues/141 G2 · Java 栈，内存仓一路 ＋ 引擎/门面两条腿）。
 *
 * <p>立法逐字依据＝spec 06-facade.md §4「写侧判重＝幂等空操作（owner 2026-09-29 拍）」＋
 * spec 11.2 原则 1「码值表达发生了什么事实」。同一 {@code (实例, 被抄送人)} 已存在 cc 行时，
 * 再次抄送必须：①不新增行 ②不重置未读状态 ③不更新原行时间 ④<b>不 fire CC_CREATE（码 4）</b>。
 * 逐人 fire 的入参换成<b>实际新建的子集</b>，子集为空整支不 fire（不空转、也不照旧全量 fire）。
 * 查询侧不引入 DISTINCT、历史重复行不清理（owner 明确接受既成事实），故这里只钉写侧。</p>
 *
 * <p>判重义务覆盖三条入口（spec §11.7「同一支」）：发起 {@code f_ccActors}、办理
 * {@code tf_ccActors}（同走 {@code JeeflowEngineImpl#handleCcActors}）、门面手动
 * {@code processInstance/createCCInstance}。SQL 仓一路见 {@code JdbcCcOwnershipIdempotentTest}，
 * 两仓必须给同一个答案（issues/117 场景 27 那把尺子）。</p>
 *
 * <p>上下文隔离同 {@code CcCreateEventTest}：{@code ServiceContext} 是静态单例，
 * {@code @Before} 存、{@code @After} 还原，事件计数才不受兄弟测试注册的捕获监听器串味。</p>
 */
public class CcWriteIdempotentTest {

    /** start → approval(assignee=leader) → end：一条待办就够办理腿用。 */
    private static final String ONE_TASK_FLOW =
            ("{'name':'cc-dedup-141','displayName':'抄送判重流程','type':'approval','nodes':["
            + "{'id':'start','type':'snaker:start','x':100,'y':200,'properties':{},'text':{'value':'开始'}},"
            + "{'id':'approval','type':'snaker:task','x':300,'y':200,'properties':{'form':'leave-form',"
            + "'assignee':'leader','taskType':0,'performType':0},'text':{'value':'审批'}},"
            + "{'id':'end','type':'snaker:end','x':500,'y':200,'properties':{},'text':{'value':'结束'}}],"
            + "'edges':["
            + "{'id':'e1','sourceNodeId':'start','targetNodeId':'approval','properties':{}},"
            + "{'id':'e2','sourceNodeId':'approval','targetNodeId':'end','properties':{}}]}").replace('\'', '"');

    private Context savedContext;
    private MemoryProcessRepository repo;
    private JeeflowEngineImpl engine;
    private JeeflowFacade facade;
    /** 事件 sink：只收 CC_CREATE，"第二次抄送没有新事件"就断在这里。 */
    private final List<ProcessEvent> ccEvents = new CopyOnWriteArrayList<>();

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
            public UserInfo getUser(String userId) {
                UserInfo u = new UserInfo();
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
        ctx.put("ccCapture", (ProcessEventListener) event -> {
            if (event.getEventType() == ProcessEventTypeEnum.CC_CREATE) ccEvents.add(event);
        });
        engine = new JeeflowEngineImpl();
        engine.configure(config);
        facade = new JeeflowFacade(engine, repo, new MemoryProcessExtRepository());
    }

    @After
    public void tearDown() {
        ccEvents.clear();
        if (savedContext != null) {
            ServiceContext.setContext(savedContext);
        }
    }

    // ── 夹具辅助 ──

    private ProcessInstance.ProcessDefine addDefine() {
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName("cc-dedup-141-" + System.nanoTime());
        def.setDisplayName("抄送判重流程");
        def.setType("approval");
        def.setState(1);
        def.setVersion(1);
        def.setContent(ONE_TASK_FLOW.getBytes(StandardCharsets.UTF_8));
        repo.addDefine(def);
        return def;
    }

    private Long startInstance() {
        ProcessInstance inst = engine.startProcessInstanceById(addDefine().getId(), "zhangsan", FlowData.create());
        assertNotNull(inst.getInstanceId());
        return inst.getInstanceId();
    }

    private Map<String, Object> args(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i].toString(), kv[i + 1]);
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String action, Map<String, Object> a) {
        return facade.flow(action, a);
    }

    private void manualCc(Long instanceId, String... actorIds) {
        Map<String, Object> resp = call("processInstance/createCCInstance", args(
                "processInstanceId", instanceId, "operator", "zhangsan",
                "actorIds", Arrays.asList((Object[]) actorIds)));
        assertEquals("手动抄送应成功: " + resp, Integer.valueOf(0), resp.get("code"));
    }

    private List<String> ccActorIds(Long instanceId) {
        return repo.ccActorsForTest(instanceId);
    }

    private MemoryProcessRepository.CcRow ccRow(Long instanceId, String actorId) {
        for (MemoryProcessRepository.CcRow row : repo.ccRowsForTest(instanceId)) {
            if (row.actorId.equals(actorId)) return row;
        }
        return null;
    }

    /** 睡一小段，让"原行时间被刷新"与"没被刷新"在断言上分得开（LocalDateTime.now 逐次取值）。 */
    private static void tick() throws InterruptedException {
        Thread.sleep(10L);
    }

    // ═══ 正向对照：全新的一次抄送照旧建行＋逐人 fire ═══

    @Test
    public void firstCcStillCreatesRowsAndFiresPerActor() throws Exception {
        Long instanceId = startInstance();
        ccEvents.clear();
        tick();

        manualCc(instanceId, "6101", "6102");

        assertEquals("全新抄送应逐人落行", Arrays.asList("6101", "6102"), ccActorIds(instanceId));
        assertEquals("全新抄送应逐人 fire CC_CREATE（码 4）", 2, ccEvents.size());
        assertEquals("ccActorId 顺序与入参一致", Arrays.asList("6101", "6102"),
                Arrays.asList(ccEvents.get(0).getCcActorId(), ccEvents.get(1).getCcActorId()));
        for (ProcessEvent event : ccEvents) {
            assertEquals("码 4 的 sourceId 应为 instanceId", instanceId, event.getSourceId());
        }
        MemoryProcessRepository.CcRow row = ccRow(instanceId, "6101");
        assertNotNull(row);
        assertEquals("新行应是未读（state=0）", 0, row.state);
    }

    // ═══ 四档：重复抄送是幂等空操作 ═══

    /** ①不新增行 ＋ ④不 fire 码 4：手动腿连发两次同一个人。 */
    @Test
    public void repeatCcForSameActorAddsNoRowAndFiresNothing() throws Exception {
        Long instanceId = startInstance();
        manualCc(instanceId, "6201");
        assertEquals("首次抄送落 1 行", Arrays.asList("6201"), ccActorIds(instanceId));
        assertEquals("首次抄送 fire 1 次", 1, ccEvents.size());

        ccEvents.clear();
        tick();
        manualCc(instanceId, "6201");

        assertEquals("①重复抄送不得新增行", Arrays.asList("6201"), ccActorIds(instanceId));
        assertEquals("①重复抄送后行数仍是 1", 1, repo.ccRowsForTest(instanceId).size());
        assertEquals("④没发生创建就不得发码 4（spec 11.2 原则 1「码=事实」）", 0, ccEvents.size());
    }

    /** ②不重置未读：先置已读，再重复抄送，state 必须仍是已读。 */
    @Test
    public void repeatCcDoesNotResetUnreadState() throws Exception {
        Long instanceId = startInstance();
        manualCc(instanceId, "6301");
        Map<String, Object> read = call("processInstance/updateCCStatus",
                args("processInstanceId", instanceId, "operator", "6301"));
        assertEquals("已读应成功: " + read, Integer.valueOf(0), read.get("code"));
        assertEquals("置读后 state 应为 1", 1, ccRow(instanceId, "6301").state);

        tick();
        manualCc(instanceId, "6301");

        assertEquals("②重复抄送不得把已读抹回未读", 1, ccRow(instanceId, "6301").state);
    }

    /** ③不更新原行时间：createTime 与 updateTime 逐字不变。 */
    @Test
    public void repeatCcDoesNotTouchOriginalRowTimes() throws Exception {
        Long instanceId = startInstance();
        manualCc(instanceId, "6401");
        MemoryProcessRepository.CcRow before = ccRow(instanceId, "6401");
        LocalDateTime createTime = before.createTime;
        LocalDateTime updateTime = before.updateTime;
        assertNotNull(createTime);

        tick();
        manualCc(instanceId, "6401");

        MemoryProcessRepository.CcRow after = ccRow(instanceId, "6401");
        assertEquals("③重复抄送不得刷新原行 createTime", createTime, after.createTime);
        assertEquals("③重复抄送不得刷新原行 updateTime", updateTime, after.updateTime);
    }

    /** ④的子集档：第二次同时给「已知人＋新人」⇒ 只为新人建行、只为新人 fire 一次。 */
    @Test
    public void repeatCcFiresOnlyForNewlyCreatedSubset() throws Exception {
        Long instanceId = startInstance();
        manualCc(instanceId, "6501", "6502");
        assertEquals("首轮 2 行", Arrays.asList("6501", "6502"), ccActorIds(instanceId));
        assertEquals("首轮 fire 2 次", 2, ccEvents.size());

        ccEvents.clear();
        tick();
        manualCc(instanceId, "6501", "6503");

        assertEquals("逐人 fire 的入参应是实际新建的子集", Arrays.asList("6503"),
                actorIdsOf(ccEvents));
        assertEquals("子集只有 1 人 ⇒ 只 fire 1 次", 1, ccEvents.size());
        assertEquals("实际新建的 cc 行也应只有那一行", Arrays.asList("6501", "6502", "6503"),
                ccActorIds(instanceId));
    }

    /** 同一次调用里重复给同一个人 ⇒ 也按幂等处理（一行一次提醒）。 */
    @Test
    public void duplicateWithinOneCallCollapses() throws Exception {
        Long instanceId = startInstance();
        ccEvents.clear();

        manualCc(instanceId, "6601", "6601");

        assertEquals("同一次调用内的重复不应新增第二行", Arrays.asList("6601"), ccActorIds(instanceId));
        assertEquals("同一次调用内的重复只 fire 一次", 1, ccEvents.size());
    }

    // ═══ 引擎腿（f_ccActors ／ tf_ccActors）同一条判据 ═══

    /** 办理腿与发起腿重叠的那个人不得再建行、不得再 fire；新人照旧。 */
    @Test
    public void engineCcLegsShareTheSameDedupRule() throws Exception {
        ProcessInstance inst = engine.startProcessInstanceById(addDefine().getId(), "zhangsan",
                FlowData.create().set(FlowConst.CC_ACTORS_START, "7001"));
        Long instanceId = inst.getInstanceId();
        assertEquals("发起腿 fire 1 次", 1, ccEvents.size());
        assertEquals("发起腿落 1 行", Arrays.asList("7001"), ccActorIds(instanceId));

        ccEvents.clear();
        tick();
        ProcessTask approval = repo.findDoingTasks(instanceId, null).get(0);
        engine.executeProcessTask(approval.getTaskId(), "leader", FlowData.create()
                .set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode())
                .set(FlowConst.CC_ACTORS, "7001,7002"));

        assertEquals("办理腿只为新人 7002 建行（7001 已有行）", Arrays.asList("7001", "7002"),
                ccActorIds(instanceId));
        assertEquals("办理腿只 fire 实际新建的子集", Arrays.asList("7002"), actorIdsOf(ccEvents));
    }

    /**
     * 两形态入参（{@code Collection} 逐元素 / 逗号串 {@code String}）共用同一条判重腿：
     * 发起腿给集合、办理腿给逗号串，重叠的人仍只有一行、只 fire 一次。
     */
    @Test
    public void stringAndCollectionFormsShareTheDedupRule() throws Exception {
        ProcessInstance inst = engine.startProcessInstanceById(addDefine().getId(), "zhangsan",
                FlowData.create().set(FlowConst.CC_ACTORS_START, Arrays.asList("7101", "7102")));
        Long instanceId = inst.getInstanceId();
        assertEquals("集合形态照旧逐人建行", Arrays.asList("7101", "7102"), ccActorIds(instanceId));
        assertEquals("集合形态照旧逐人 fire", 2, ccEvents.size());

        ccEvents.clear();
        tick();
        ProcessTask approval = repo.findDoingTasks(instanceId, null).get(0);
        engine.executeProcessTask(approval.getTaskId(), "leader", FlowData.create()
                .set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode())
                .set(FlowConst.CC_ACTORS, "7101,7103"));

        assertEquals("逗号串形态与集合形态判重同一条", Arrays.asList("7101", "7102", "7103"),
                ccActorIds(instanceId));
        assertEquals("两形态混用也只为新人 fire", Arrays.asList("7103"), actorIdsOf(ccEvents));
    }

    private static List<String> actorIdsOf(List<ProcessEvent> events) {
        List<String> ids = new ArrayList<>();
        for (ProcessEvent event : events) ids.add(event.getCcActorId());
        return ids;
    }

    /** 反向哨兵：判重不得把"没抄送过的人"也吃掉——不同实例上的同一个人各自建行。 */
    @Test
    public void dedupIsScopedToInstanceNotGlobal() throws Exception {
        Long first = startInstance();
        Long second = startInstance();
        ccEvents.clear();

        manualCc(first, "6701");
        tick();
        manualCc(second, "6701");

        assertEquals("实例一应有自己的 cc 行", Arrays.asList("6701"), ccActorIds(first));
        assertEquals("实例二不受实例一影响，同一个人照样建行", Arrays.asList("6701"), ccActorIds(second));
        assertEquals("两个实例各 fire 一次", 2, ccEvents.size());
        assertTrue("两个实例 id 必须不同（判重作用域是按实例）", !first.equals(second));
    }
}
