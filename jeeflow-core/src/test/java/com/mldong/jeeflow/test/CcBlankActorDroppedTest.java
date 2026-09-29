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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * 空抄送人不建 cc 行（issues/141 G10 · 2026-09-29 owner 拍「空不创建行」· Java 栈）。
 *
 * <p>立法逐字依据＝spec 06-facade.md §2.10：三条入口（发起 {@code f_ccActors}／办理
 * {@code tf_ccActors}／门面手动 {@code createCCInstance}）解析抄送人集合时，
 * <b>空串、纯空白、数组里的空元素一律丢弃</b>；丢完为空 ⇒ 不建任何 cc 行、也<b>不 fire 码 4</b>；
 * 逗号串与数组两种形态必须同判据。</p>
 *
 * <p>java 的旧形状正是这一条要堵的那类洞：{@code "".split(",")} 在 Java 里得到
 * <b>一个空元素</b>而不是零个 ⇒ 建出一条 {@code actor_id=''} 的 cc 行。空归属值就是
 * issues/129 那族"空 operator 读全库"的病根，不能从抄送侧继续往里灌。因此判据落在
 * <b>漏斗（{@code handleCcActors}）＋ 写侧（两仓的 {@code createCcInstance}）</b>两层：
 * 绕过门面/引擎直连仓储也建不出空行。</p>
 *
 * <p>与 {@code CcWriteIdempotentTest} 一样用 {@code ServiceContext} 存还原隔离事件计数，
 * 免得兄弟测试注册的捕获监听器串味。</p>
 */
public class CcBlankActorDroppedTest {

    /** start → approval(assignee=leader) → end：办理腿需要一条真实待办。 */
    private static final String ONE_TASK_FLOW =
            ("{'name':'cc-blank-141','displayName':'空抄送人不建行','type':'approval','nodes':["
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
        def.setName("cc-blank-141-" + System.nanoTime());
        def.setDisplayName("空抄送人不建行");
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

    /** 实例上真实存在的 cc 行 actor 集合（取证走仓储而不是返回值）。 */
    private List<String> ccActorIds(Long instanceId) {
        return repo.ccActorsForTest(instanceId);
    }

    private int ccRowCount(Long instanceId) {
        return repo.ccRowsForTest(instanceId).size();
    }

    private static List<String> firedActorIds(List<ProcessEvent> events) {
        List<String> ids = new ArrayList<>();
        for (ProcessEvent event : events) ids.add(event.getCcActorId());
        return ids;
    }

    private Map<String, Object> args(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i].toString(), kv[i + 1]);
        return m;
    }

    /** 手动腿原始返回（全空白档要看它是不是与"空集合"同档，不能假定成功）。 */
    private Map<String, Object> manualCcRaw(Long instanceId, String... actorIds) {
        return facade.flow("processInstance/createCCInstance", args(
                "processInstanceId", instanceId, "operator", "zhangsan",
                "actorIds", Arrays.asList((Object[]) actorIds)));
    }

    /** 手动腿：断言成功（非空档才用）。 */
    private void manualCc(Long instanceId, String... actorIds) {
        Map<String, Object> resp = manualCcRaw(instanceId, actorIds);
        assertEquals("手动抄送应成功: " + resp, Integer.valueOf(0), resp.get("code"));
    }

    /** 办理腿：给流程实例上的唯一待办办理并带 tf_ccActors。 */
    private void executeWithCc(Long instanceId, Object ccActors) {
        ProcessTask approval = repo.findDoingTasks(instanceId, null).get(0);
        engine.executeProcessTask(approval.getTaskId(), "leader", FlowData.create()
                .set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode())
                .set(FlowConst.CC_ACTORS, ccActors));
    }

    // ═══ 正向对照：非空抄送人照旧建行＋逐人 fire ═══

    @Test
    public void nonBlankCcActorsStillCreateRowsAndFire() {
        Long instanceId = startInstance();
        ccEvents.clear();

        manualCc(instanceId, "7501", "7502");

        assertEquals("正向对照：非空抄送人照旧逐人落行", Arrays.asList("7501", "7502"), ccActorIds(instanceId));
        assertEquals("正向对照：照旧逐人 fire 码 4", Arrays.asList("7501", "7502"), firedActorIds(ccEvents));
    }

    // ═══ 手动腿 ═══

    /** 全空白集合 ⇒ 不建行、不 fire；与"空集合"同档（既有 {@code actorIds 缺失} 那支），不新造文案。 */
    @Test
    public void manualLegAllBlankCreatesNoRowAndFiresNothing() {
        Long instanceId = startInstance();
        ccEvents.clear();

        manualCcRaw(instanceId, "", "   ");

        assertEquals("G10：全空白不得建 cc 行", 0, ccRowCount(instanceId));
        assertEquals("G10：cc 行集合应为空", Collections.emptyList(), ccActorIds(instanceId));
        assertEquals("G10：全空白不得 fire 码 4", 0, ccEvents.size());
    }

    /** 混着给 ⇒ 只丢空元素，有效的人照旧建行＋fire。 */
    @Test
    public void manualLegDropsBlankElementsKeepsValidOnes() {
        Long instanceId = startInstance();
        ccEvents.clear();

        manualCc(instanceId, "7601", "", "  ", "7602");

        assertEquals("G10：数组里的空元素丢弃、有效元素保留", Arrays.asList("7601", "7602"), ccActorIds(instanceId));
        assertEquals("G10：不得落出 actor_id='' 的行", 2, ccRowCount(instanceId));
        assertEquals("G10：fire 的入参只含有效的人", Arrays.asList("7601", "7602"), firedActorIds(ccEvents));
    }

    // ═══ 发起腿 f_ccActors（逗号串 / 数组两种形态同判据） ═══

    /**
     * java 旧形状的正面对手戏：{@code "".split(",")} 得到一个空元素 ⇒ 建一条 {@code actor_id=''} 的行。
     * 按 G10 必须"丢完为空 ⇒ 不建行、不 fire"。
     */
    @Test
    public void startLegEmptyStringCreatesNoRow() {
        ccEvents.clear();
        ProcessInstance inst = engine.startProcessInstanceById(addDefine().getId(), "zhangsan",
                FlowData.create().set(FlowConst.CC_ACTORS_START, ""));

        assertEquals("G10：f_ccActors 给空串不得建 cc 行", 0, ccRowCount(inst.getInstanceId()));
        assertEquals("G10：也不得 fire 码 4", 0, ccEvents.size());
    }

    /** 逗号串里的空元素（{@code "7701,,7702"}）丢弃，两个人照旧。 */
    @Test
    public void startLegCommaStringDropsEmptyElement() {
        ccEvents.clear();
        ProcessInstance inst = engine.startProcessInstanceById(addDefine().getId(), "zhangsan",
                FlowData.create().set(FlowConst.CC_ACTORS_START, "7701,,7702"));
        Long instanceId = inst.getInstanceId();

        assertEquals("G10：逗号串空元素丢弃", Arrays.asList("7701", "7702"), ccActorIds(instanceId));
        assertEquals("G10：只有两行", 2, ccRowCount(instanceId));
        assertEquals("G10：逐有效人 fire", Arrays.asList("7701", "7702"), firedActorIds(ccEvents));
    }

    /** 数组形态给空元素 ⇒ 同一判据（逗号串修好了、数组腿漏修＝本条要抓的形状）。 */
    @Test
    public void startLegCollectionDropsBlankElements() {
        ccEvents.clear();
        ProcessInstance inst = engine.startProcessInstanceById(addDefine().getId(), "zhangsan",
                FlowData.create().set(FlowConst.CC_ACTORS_START, Arrays.asList("7801", "", "  ")));

        assertEquals("G10：数组形态与逗号串同判据", Arrays.asList("7801"), ccActorIds(inst.getInstanceId()));
        assertEquals("G10：数组形态只 fire 有效的人", Arrays.asList("7801"), firedActorIds(ccEvents));
    }

    // ═══ 办理腿 tf_ccActors ═══

    /** 办理腿给纯空白串 ⇒ 不建行、不 fire。 */
    @Test
    public void executeLegBlankStringCreatesNoRow() {
        Long instanceId = startInstance();
        ccEvents.clear();

        executeWithCc(instanceId, "   ");

        assertEquals("G10：tf_ccActors 纯空白不得建 cc 行", 0, ccRowCount(instanceId));
        assertEquals("G10：也不得 fire 码 4", 0, ccEvents.size());
    }

    /** 办理腿混给空元素 ⇒ 只丢空的。 */
    @Test
    public void executeLegDropsBlankKeepsValidActors() {
        Long instanceId = startInstance();
        ccEvents.clear();

        executeWithCc(instanceId, "8201,");

        assertEquals("G10：办理腿尾随逗号不得建空行", Arrays.asList("8201"), ccActorIds(instanceId));
        assertEquals("G10：办理腿只 fire 有效的人", Arrays.asList("8201"), firedActorIds(ccEvents));
    }

    // ═══ trim 后同值＝同一个人（与 G2 判重咬合） ═══

    /** 落库值取 trim 后的串：带空格的人与不带空格的人是同一个人。 */
    @Test
    public void ccActorValuesAreTrimmed() {
        Long instanceId = startInstance();
        ccEvents.clear();

        manualCc(instanceId, " 8301 ", "8302");

        assertEquals("G10：入库值应是 trim 后的串", Arrays.asList("8301", "8302"), ccActorIds(instanceId));
    }

    /** 先抄 "8401" 再抄 " 8401 " ⇒ 判重命中，仍是 1 行、0 新 fire（trim 与 G2 同一条尺子）。 */
    @Test
    public void paddedValueHitsTheDedupRule() {
        Long instanceId = startInstance();
        manualCc(instanceId, "8401");
        ccEvents.clear();

        manualCc(instanceId, " 8401 ");

        assertEquals("G10：带空格的同一人不得再建第二行", Arrays.asList("8401"), ccActorIds(instanceId));
        assertEquals("G10：判重命中 ⇒ 不 fire 码 4", 0, ccEvents.size());
    }

    // ═══ 写侧兜底：绕过引擎/门面直连仓储也建不出空行 ═══

    /** 漏斗修了但写侧没修 ⇒ 直连仓储照样灌空值；本条钉两层里的第二层。 */
    @Test
    public void repoWritePathAlsoDropsBlankActors() {
        Long instanceId = startInstance();

        repo.createCcInstance(instanceId, "zhangsan", "", "   ", null, "8501");

        assertEquals("G10：仓储写侧空串/纯空白/null 都不建行", Arrays.asList("8501"), ccActorIds(instanceId));
        assertEquals("G10：写侧只落那一行", 1, ccRowCount(instanceId));
    }

    /** {@code createCcInstanceIfAbsent} 返回的子集也不得含空值（子集拿去 fire）。 */
    @Test
    public void ifAbsentSubsetExcludesBlankActors() {
        Long instanceId = startInstance();

        List<String> created = repo.createCcInstanceIfAbsent(instanceId, "zhangsan", "", "8601", "  ");

        assertEquals("G10：实际新建子集只含有效的人", Arrays.asList("8601"), created);
        assertEquals("G10：子集与落库行一致", Arrays.asList("8601"), ccActorIds(instanceId));
    }

    /** 反向哨兵：判据只吃空值，不吃"看起来像空"的正常 id。 */
    @Test
    public void normalActorIdsAreNotMistakenForBlank() {
        Long instanceId = startInstance();
        ccEvents.clear();

        manualCc(instanceId, "0", "user-1");

        assertEquals("G10 只丢空串/纯空白：'0' 这类正常 id 不得被吃掉",
                Arrays.asList("0", "user-1"), ccActorIds(instanceId));
        assertEquals("反向哨兵：照旧逐人 fire", 2, ccEvents.size());
    }
}
