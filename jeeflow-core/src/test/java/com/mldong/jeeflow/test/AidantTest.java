package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.core.AidantSupport;
import com.mldong.jeeflow.core.CollectSupport;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessSurrogate;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessEventTypeEnum;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
import com.mldong.jeeflow.enums.ProcessTaskPerformTypeEnum;
import com.mldong.jeeflow.enums.ProcessTaskTypeEnum;
import com.mldong.jeeflow.event.ProcessEvent;
import com.mldong.jeeflow.event.ProcessEventListener;
import com.mldong.jeeflow.facade.JeeflowFacade;
import com.mldong.jeeflow.spi.DefaultActionPermissionProvider;
import com.mldong.jeeflow.spi.IUserProvider;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.*;

/**
 * 「动态协办（aidant）」验收（issues/253，规范 04 · 动态协办 / 规范 06 §processTask/aidant）。
 *
 * <p>覆盖：派生形状（task_type=1／parent＝主办行／同节点同名／performType=NORMAL／expire_time 同尺／
 * 委托并入／码 3 既有腿）、推进闸（协办行办结不驱动，execute 与 jump 两路同闸）、等待闸两档
 * （waitCompletion=true 逐字文案＋零写入／缺省 false 随时推进＋离开节点清场记账）、两跳账本存活、
 * detail 投影形状、协办行能力边界（可加/摘人、可被转办、禁递归）、负向六文案＋越权＋非 DOING、
 * 回归（会签票齐即推进不被协办挡／与征询并存两闸各数／撤回清场／transfer·surrogate·addCandidate 不变）。</p>
 *
 * <p>夹具内联 JSON（不碰共享 flows 目录，与 {@link DynamicCountersignTest}／{@link RecallTest} 同款纪律）；
 * Memory 替身与 setUp 形状照这两个兄弟用例——既有测试期望值一字未改。</p>
 *
 * @author mldong
 */
public class AidantTest {

    private JeeflowEngine engine;
    private MemoryProcessRepository repo;
    private TestJsonProvider json;
    private JeeflowFacade facade;
    private static int seq;

    /** 事件捕获（码 3/5 既有腿取证；不新增事件码） */
    private final List<ProcessEvent> events = new CopyOnWriteArrayList<>();

    private static final int AGREE = ProcessSubmitTypeEnum.AGREE.getCode();
    private static final int REJECT = ProcessSubmitTypeEnum.REJECT.getCode();
    private static final int APPLY = ProcessSubmitTypeEnum.APPLY.getCode();
    private static final int JUMP = ProcessSubmitTypeEnum.JUMP.getCode();
    private static final int TO_OPERATOR = ProcessSubmitTypeEnum.ROLLBACK_TO_OPERATOR.getCode();

    @Before
    public void setUp() {
        Configuration config = new Configuration();
        repo = new MemoryProcessRepository();
        json = new TestJsonProvider();
        events.clear();
        ServiceContext.put("repository", repo);
        ServiceContext.put("json", json);
        ServiceContext.put("expr", new TestExpressionEvaluator());
        ServiceContext.put("user", new IUserProvider() {
            @Override
            public UserInfo getUser(String userId) {
                UserInfo u = new UserInfo();
                u.setUserId(userId);
                u.setRealName("用户" + userId);
                u.setDeptId("D01");
                u.setDeptName("测试部门");
                return u;
            }
        });
        ServiceContext.put("aidantCapture", (ProcessEventListener) event -> events.add(event));
        engine = new JeeflowEngineImpl();
        engine.configure(config);
        facade = new JeeflowFacade(engine, repo, null);
    }

    // ═══ 夹具 ═══

    private static String node(String id, String props, String text) {
        return "{\"id\":\"" + id + "\",\"type\":\"snaker:task\",\"x\":0,\"y\":0,"
                + "\"properties\":{" + props + "},\"text\":{\"value\":\"" + text + "\"}}";
    }

    private static String edge(String id, String from, String to) {
        return "{\"id\":\"" + id + "\",\"type\":\"snaker:transition\",\"sourceNodeId\":\"" + from
                + "\",\"targetNodeId\":\"" + to + "\",\"properties\":{}}";
    }

    /** 通用五节点链：start→apply→review→final→end；reviewProps 控制 review 的 properties。 */
    private String chain(String reviewProps) {
        String r = reviewProps.isEmpty() ? "\"assignee\":\"userA\"" : "\"assignee\":\"userA\"," + reviewProps;
        return "{\"name\":\"aidant-" + (++seq) + "\",\"displayName\":\"动态协办夹具\",\"type\":\"approval\","
                + "\"nodes\":["
                + "{\"id\":\"start\",\"type\":\"snaker:start\",\"x\":0,\"y\":0,\"properties\":{},\"text\":{\"value\":\"开始\"}},"
                + node("apply", "\"form\":\"apply-form\",\"taskType\":0,\"performType\":0,\"assignee\":\"applicant\"", "发起申请") + ","
                + node("review", "\"form\":\"form-r\",\"taskType\":0,\"performType\":0," + r, "审批") + ","
                + node("final", "\"form\":\"form-f\",\"assignee\":\"userF\",\"taskType\":0,\"performType\":0", "终审") + ","
                + "{\"id\":\"end\",\"type\":\"snaker:end\",\"x\":0,\"y\":0,\"properties\":{},\"text\":{\"value\":\"结束\"}}"
                + "],\"edges\":["
                + edge("e1", "start", "apply") + ","
                + edge("e2", "apply", "review") + ","
                + edge("e3", "review", "final") + ","
                + edge("e4", "final", "end")
                + "]}";
    }

    /** 并行会签链：start→apply→cs（userA,userB）→end。 */
    private String countersignChain() {
        return "{\"name\":\"aidant-cs-" + (++seq) + "\",\"displayName\":\"协办×会签夹具\",\"type\":\"approval\","
                + "\"nodes\":["
                + "{\"id\":\"start\",\"type\":\"snaker:start\",\"x\":0,\"y\":0,\"properties\":{},\"text\":{\"value\":\"开始\"}},"
                + node("apply", "\"form\":\"apply-form\",\"taskType\":0,\"performType\":0,\"assignee\":\"applicant\"", "发起申请") + ","
                + node("cs", "\"form\":\"form-c\",\"taskType\":0,\"performType\":1,"
                        + "\"assignee\":\"userA,userB\",\"countersignType\":\"PARALLEL\"", "会签审批") + ","
                + "{\"id\":\"end\",\"type\":\"snaker:end\",\"x\":0,\"y\":0,\"properties\":{},\"text\":{\"value\":\"结束\"}}"
                + "],\"edges\":["
                + edge("e1", "start", "apply") + ","
                + edge("e2", "apply", "cs") + ","
                + edge("e3", "cs", "end")
                + "]}";
    }

    private ProcessInstance start(String flowJson) throws Exception {
        byte[] bytes = flowJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        com.mldong.jeeflow.model.logicflow.LfModel lfModel =
                json.getMapper().readValue(flowJson, com.mldong.jeeflow.model.logicflow.LfModel.class);
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName(lfModel.getName());
        def.setDisplayName(lfModel.getDisplayName());
        def.setType(lfModel.getType());
        def.setState(1);
        def.setVersion(1);
        def.setContent(bytes);
        repo.addDefine(def);
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "applicant", FlowData.create());
        for (ProcessTask t : repo.findDoingTasks(inst.getInstanceId(), null)) {
            engine.executeProcessTask(t.getTaskId(), "applicant",
                    FlowData.create().set(FlowConst.SUBMIT_TYPE, APPLY));
        }
        return repo.findInstanceById(inst.getInstanceId());
    }

    private ProcessInstance startToReview(String flowJson) throws Exception {
        return start(flowJson);
    }

    // ═══ 驱动助手 ═══

    private Map<String, Object> aidant(ProcessTask host, String operator, Object actorIds,
                                       String reason, Boolean wait) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("processTaskId", String.valueOf(host.getTaskId()));
        a.put("operator", operator);
        if (actorIds != null) a.put("actorIds", actorIds);
        if (reason != null) a.put("reason", reason);
        if (wait != null) a.put("waitCompletion", wait);
        return facade.flow("processTask/aidant", a);
    }

    private Map<String, Object> execute(ProcessTask row, String operator, int submitType) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("processTaskId", String.valueOf(row.getTaskId()));
        a.put("operator", operator);
        a.put(FlowConst.SUBMIT_TYPE, submitType);
        return facade.flow("processTask/execute", a);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(Map<String, Object> resp) {
        return (Map<String, Object>) resp.get("data");
    }

    private ProcessTask doing(Long instanceId, String name) {
        for (ProcessTask t : repo.findDoingTasks(instanceId, null)) {
            if (name.equals(t.getTaskName())) {
                return t;
            }
        }
        return null;
    }

    private int doingCount(Long instanceId, String name) {
        int n = 0;
        for (ProcessTask t : repo.findDoingTasks(instanceId, null)) {
            if (name.equals(t.getTaskName())) n++;
        }
        return n;
    }

    private List<ProcessTask> aidantRows(Long instanceId, ProcessTask host) {
        return AidantSupport.aidantRows(repo.findInstanceById(instanceId).getTasks(),
                repo.findTaskById(host.getTaskId()));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> ledger(ProcessTask row) {
        Object v = repo.findTaskById(row.getTaskId()).getVariables().get(FlowConst.AIDANT_HISTORY);
        return v instanceof List ? (List<Map<String, Object>>) v : new ArrayList<Map<String, Object>>();
    }

    private void assertMsg(Map<String, Object> resp, String msg) {
        assertEquals(Integer.valueOf(99999999), resp.get("code"));
        assertEquals(msg, resp.get("msg"));
    }

    private List<Integer> codes() {
        List<Integer> out = new ArrayList<>();
        for (ProcessEvent e : events) out.add(e.getEventType().getCode());
        return out;
    }

    // ═══ 正向：派生形状 ═══

    /** 派生：每人一行、task_type=1、parent＝主办行、同节点同名、performType=NORMAL、主办保留 DOING、账本键序。 */
    @Test
    public void deriveShapesSecondaryRowsAndKeepsHostDoing() throws Exception {
        ProcessInstance inst = startToReview(chain(""));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        events.clear();

        Map<String, Object> resp = aidant(host, "userA", Arrays.asList("userX", "userY"), "需两部门协办", null);
        assertEquals(Integer.valueOf(0), resp.get("code"));
        assertNull("成功 data → null（规范 06）", resp.get("data"));

        // 主办行原样保留 DOING，名册不动
        ProcessTask hostRow = repo.findTaskById(host.getTaskId());
        assertTrue("主办行保留 DOING", hostRow.isDoing());
        assertEquals(Arrays.asList("userA"), hostRow.getActorIds());
        assertNull("严禁覆写任务 actor_id 列（转办那条 ⚠️ 同尺）", hostRow.getActorId());

        List<ProcessTask> rows = aidantRows(inst.getInstanceId(), host);
        assertEquals("多人每人一行", 2, rows.size());
        List<String> actors = new ArrayList<>();
        for (ProcessTask r : rows) {
            assertTrue(r.isDoing());
            assertEquals(ProcessTaskTypeEnum.SECONDARY, r.getTaskType());
            assertEquals("task_type=1", Integer.valueOf(1), r.getTaskType().getCode());
            assertEquals(host.getTaskId(), r.getParentTaskId());
            assertEquals("同节点", host.getTaskName(), r.getTaskName());
            assertEquals("同名", host.getDisplayName(), r.getDisplayName());
            assertEquals("performType=NORMAL", ProcessTaskPerformTypeEnum.NORMAL, r.getPerformType());
            assertEquals("需两部门协办", r.getVariables().getStr(FlowConst.AIDANT_REASON));
            assertNull("协办行不占 tf_approvalComment", r.getVariables().get(FlowConst.APPROVAL_COMMENT));
            assertNull("进行中任务 actor_id 列恒无值", r.getActorId());
            actors.add(r.getActorIds().get(0));
            assertEquals("参与者表真落库", 1, repo.findTaskActors(r.getTaskId()).size());
        }
        assertEquals(Arrays.asList("userX", "userY"), actors);

        // 账本：一条、六键锁定键序、time 为 §2.4 字符串
        List<Map<String, Object>> lg = ledger(host);
        assertEquals(1, lg.size());
        Map<String, Object> e0 = lg.get(0);
        assertEquals(Arrays.asList("round", "fromActor", "toActors", "reason", "time", "operator"),
                new ArrayList<>(e0.keySet()));
        assertEquals(1, e0.get("round"));
        assertEquals("userA", e0.get("fromActor"));
        assertEquals(Arrays.asList("userX", "userY"), e0.get("toActors"));
        assertEquals("需两部门协办", e0.get("reason"));
        assertEquals("userA", e0.get("operator"));
        assertTrue("time 一律 yyyy-MM-dd HH:mm:ss",
                String.valueOf(e0.get("time")).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));

        // 事件：只走既有码 3（逐协办行一次），不新增事件码
        assertEquals(Arrays.asList(3, 3), codes());
        assertEquals(Integer.valueOf(3), ProcessEventTypeEnum.PROCESS_TASK_START.getCode());
    }

    /** reason 选填：空串兜底进账本，协办行不盖空戳。 */
    @Test
    public void reasonOptionalFallsBackToEmptyString() throws Exception {
        ProcessInstance inst = startToReview(chain(""));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        assertEquals(Integer.valueOf(0), aidant(host, "userA", "userX", null, null).get("code"));
        assertEquals("", ledger(host).get(0).get("reason"));
        assertNull(aidantRows(inst.getInstanceId(), host).get(0).getVariables().get(FlowConst.AIDANT_REASON));
        assertNull("主办行也不占审批意见", repo.findTaskById(host.getTaskId())
                .getVariables().get(FlowConst.APPROVAL_COMMENT));
    }

    /** actorIds 逗号串与数组两形同归一（§2.11 单点）；空白元素丢弃。 */
    @Test
    public void actorIdsAcceptsCommaStringAndArray() throws Exception {
        ProcessInstance inst = startToReview(chain(""));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        assertEquals(Integer.valueOf(0), aidant(host, "userA", " userX , userY ,", null, null).get("code"));
        assertEquals(2, aidantRows(inst.getInstanceId(), host).size());
    }

    /** expire_time 同尺重算（节点表达式），委托照既有腿并入协办行名册。 */
    @Test
    public void expireTimeRecomputedAndSurrogateMerged() throws Exception {
        MemoryProcessExtRepository ext = new MemoryProcessExtRepository();
        ProcessSurrogate s = new ProcessSurrogate();
        s.setId(1L);
        s.setOperator("userX");
        s.setSurrogate("userS");
        s.setProcessName("");
        s.setStartTime(LocalDateTime.now().minusHours(1));
        s.setEndTime(LocalDateTime.now().plusHours(1));
        s.setEnabled(1);
        ext.saveSurrogate(s);
        ServiceContext.put("ext", ext);

        ProcessInstance inst = startToReview(chain("\"expireTime\":\"2d\""));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        assertEquals(Integer.valueOf(0), aidant(host, "userA", "userX", null, null).get("code"));
        ProcessTask child = aidantRows(inst.getInstanceId(), host).get(0);
        assertNotNull("expire_time 按节点表达式同尺重算", child.getExpireTime());
        assertTrue(child.getExpireTime().isAfter(LocalDateTime.now().plusHours(47)));
        assertTrue("委托并入不摘原人", child.getActorIds().contains("userX"));
        assertTrue("委托照既有腿并入", child.getActorIds().contains("userS"));
        assertEquals("落库参与者表也真带上", 2, repo.findTaskActors(child.getTaskId()).size());
    }

    // ═══ 正向：推进闸 ═══

    /** 协办行办结不产生执行对象、不跑输出变迁；主办行仍 DOING；只 fire 既有码 5。 */
    @Test
    public void aidantCompletionDoesNotDrive() throws Exception {
        ProcessInstance inst = startToReview(chain(""));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        aidant(host, "userA", "userX", null, null);
        ProcessTask child = aidantRows(inst.getInstanceId(), host).get(0);
        events.clear();

        List<ProcessTask> created = engine.executeProcessTask(child.getTaskId(), "userX",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, AGREE));
        assertTrue("协办办结不产生执行对象（无新任务）", created.isEmpty());
        assertTrue(repo.findTaskById(child.getTaskId()).isFinished());
        assertTrue("主办行保留 DOING", repo.findTaskById(host.getTaskId()).isDoing());
        assertNull("不跑输出变迁：下游节点无待办", doing(inst.getInstanceId(), "final"));
        assertEquals(Integer.valueOf(10), repo.findInstanceById(inst.getInstanceId()).getState());
        assertEquals("只走既有码 5，不新增事件码", Arrays.asList(5), codes());
    }

    /** 两路同闸：executeAndJumpTask（JUMP／ROLLBACK）对协办行同样不驱动。 */
    @Test
    public void aidantJumpPathAlsoGated() throws Exception {
        ProcessInstance inst = startToReview(chain(""));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        aidant(host, "userA", Arrays.asList("userX", "userY"), null, null);
        List<ProcessTask> rows = aidantRows(inst.getInstanceId(), host);

        // JUMP 路（门面 submitType=4）
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("processTaskId", String.valueOf(rows.get(0).getTaskId()));
        a.put("operator", "userX");
        a.put(FlowConst.SUBMIT_TYPE, JUMP);
        a.put(FlowConst.TASK_NAME, "final");
        assertEquals(Integer.valueOf(0), facade.flow("processTask/execute", a).get("code"));
        assertTrue(repo.findTaskById(rows.get(0).getTaskId()).isFinished());
        assertNull("跳转路同闸：不得凭空建出 final 待办", doing(inst.getInstanceId(), "final"));

        // ROLLBACK 路（引擎直调，nodeName=null）
        List<ProcessTask> created = engine.executeAndJumpTask(rows.get(1).getTaskId(), "userY",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.ROLLBACK.getCode()), null);
        assertTrue(created.isEmpty());
        assertTrue(repo.findTaskById(rows.get(1).getTaskId()).isFinished());
        assertTrue("主办行仍在", repo.findTaskById(host.getTaskId()).isDoing());
        assertEquals(1, doingCount(inst.getInstanceId(), "review"));
    }

    /** 协办行拒绝／退回发起人同样不驱动（任何 submitType 都只办结自己那一行）。 */
    @Test
    public void aidantRejectAndReturnDoNotDrive() throws Exception {
        ProcessInstance inst = startToReview(chain(""));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        aidant(host, "userA", Arrays.asList("userX", "userY"), null, null);
        List<ProcessTask> rows = aidantRows(inst.getInstanceId(), host);

        assertEquals(Integer.valueOf(0), execute(rows.get(0), "userX", REJECT).get("code"));
        assertEquals("拒绝不得结束整单", Integer.valueOf(10),
                repo.findInstanceById(inst.getInstanceId()).getState());
        assertEquals(Integer.valueOf(0), execute(rows.get(1), "userY", TO_OPERATOR).get("code"));
        assertEquals(Integer.valueOf(10), repo.findInstanceById(inst.getInstanceId()).getState());
        assertTrue(repo.findTaskById(host.getTaskId()).isDoing());
        assertNull(doing(inst.getInstanceId(), "apply"));
    }

    /** 主办再办结照常驱动（协办已办结）。 */
    @Test
    public void hostCompletionAfterAidantDrives() throws Exception {
        ProcessInstance inst = startToReview(chain(""));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        aidant(host, "userA", "userX", null, null);
        ProcessTask child = aidantRows(inst.getInstanceId(), host).get(0);
        execute(child, "userX", AGREE);
        assertEquals(Integer.valueOf(0), execute(host, "userA", AGREE).get("code"));
        assertNotNull("主办办结才推进", doing(inst.getInstanceId(), "final"));
        assertTrue(repo.findTaskById(host.getTaskId()).isFinished());
    }

    // ═══ 正向／负向：等待闸两档 ═══

    /** waitCompletion=true：未齐 ⇒ 逐字文案＋零写入；齐了 ⇒ 放行推进。 */
    @Test
    public void waitCompletionTrueBlocksHostUntilAidantDone() throws Exception {
        ProcessInstance inst = startToReview(chain(""));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        assertEquals(Integer.valueOf(0),
                aidant(host, "userA", Arrays.asList("userX", "userY"), null, true).get("code"));
        List<ProcessTask> rows = aidantRows(inst.getInstanceId(), host);
        int ledgerBefore = ledger(host).size();

        Map<String, Object> blocked = execute(host, "userA", AGREE);
        assertMsg(blocked, "节点[review]协办未办结（已办结 0/2），不能办理");
        // 零写入：主办仍 DOING、无下游待办、账本不增、协办行不动
        assertTrue(repo.findTaskById(host.getTaskId()).isDoing());
        assertNull(doing(inst.getInstanceId(), "final"));
        assertEquals(ledgerBefore, ledger(host).size());
        assertEquals(2, aidantRows(inst.getInstanceId(), host).size());
        for (ProcessTask r : aidantRows(inst.getInstanceId(), host)) assertTrue(r.isDoing());

        // 办结一条 ⇒ 计数 1/2，仍拦
        execute(rows.get(0), "userX", AGREE);
        assertMsg(execute(host, "userA", AGREE), "节点[review]协办未办结（已办结 1/2），不能办理");

        // 全办结 ⇒ 放行
        execute(rows.get(1), "userY", AGREE);
        assertEquals(Integer.valueOf(0), execute(host, "userA", AGREE).get("code"));
        assertNotNull(doing(inst.getInstanceId(), "final"));
    }

    /** 等待闸挡全部 submitType（含拒绝／退回），不只挡同意。 */
    @Test
    public void waitGateBlocksEverySubmitType() throws Exception {
        ProcessInstance inst = startToReview(chain(""));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        aidant(host, "userA", "userX", null, true);
        for (int st : new int[]{AGREE, REJECT, TO_OPERATOR}) {
            assertMsg(execute(host, "userA", st), "节点[review]协办未办结（已办结 0/1），不能办理");
            assertTrue(repo.findTaskById(host.getTaskId()).isDoing());
        }
        assertEquals(Integer.valueOf(10), repo.findInstanceById(inst.getInstanceId()).getState());
    }

    /** waitCompletion 缺省 false：主办可随时推进，未办结协办行随废弃腿清场＋账本记废弃。 */
    @Test
    public void waitDefaultFalseCleansPendingAidantOnLeave() throws Exception {
        ProcessInstance inst = startToReview(chain(""));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        aidant(host, "userA", Arrays.asList("userX", "userY"), null, null);
        List<ProcessTask> rows = aidantRows(inst.getInstanceId(), host);
        // 只办结一条，另一条仍 DOING
        execute(rows.get(0), "userX", AGREE);

        assertEquals("缺省 false 主办可随时推进", Integer.valueOf(0), execute(host, "userA", AGREE).get("code"));
        assertNotNull(doing(inst.getInstanceId(), "final"));
        ProcessTask pending = repo.findTaskById(rows.get(1).getTaskId());
        assertEquals("未办结协办行清场为废弃(99)", Integer.valueOf(99), pending.getTaskState());
        assertEquals("已办结那条不改写", Integer.valueOf(20),
                repo.findTaskById(rows.get(0).getTaskId()).getTaskState());
        // 账本记废弃：末条带 abandoned 标记、toActors＝被废弃的人
        List<Map<String, Object>> lg = ledger(host);
        assertEquals(2, lg.size());
        Map<String, Object> last = lg.get(1);
        assertEquals(Boolean.TRUE, last.get("abandoned"));
        assertEquals(Arrays.asList("userY"), last.get("toActors"));
        assertEquals("清场不消耗轮次", 1, last.get("round"));
        assertEquals("不留孤儿待办", 0, doingCount(inst.getInstanceId(), "review"));
    }

    /** waitCompletion=false 显式传值同缺省；字符串 "true" 两形也认。 */
    @Test
    public void waitCompletionAcceptsStringForm() throws Exception {
        ProcessInstance inst = startToReview(chain(""));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("processTaskId", String.valueOf(host.getTaskId()));
        a.put("operator", "userA");
        a.put("actorIds", "userX");
        a.put("waitCompletion", "true");
        assertEquals(Integer.valueOf(0), facade.flow("processTask/aidant", a).get("code"));
        assertMsg(execute(host, "userA", AGREE), "节点[review]协办未办结（已办结 0/1），不能办理");
    }

    /** 拒绝／退回发起人离开节点：协办行同样清场（废弃腿覆盖）。 */
    @Test
    public void rejectAndReturnToOperatorCleanAidant() throws Exception {
        ProcessInstance inst1 = startToReview(chain(""));
        ProcessTask h1 = doing(inst1.getInstanceId(), "review");
        aidant(h1, "userA", "userX", null, null);
        ProcessTask c1 = aidantRows(inst1.getInstanceId(), h1).get(0);
        assertEquals(Integer.valueOf(0), execute(h1, "userA", REJECT).get("code"));
        assertEquals(Integer.valueOf(99), repo.findTaskById(c1.getTaskId()).getTaskState());
        assertEquals(Boolean.TRUE, ledger(h1).get(1).get("abandoned"));

        ProcessInstance inst2 = startToReview(chain(""));
        ProcessTask h2 = doing(inst2.getInstanceId(), "review");
        aidant(h2, "userA", "userX", null, null);
        ProcessTask c2 = aidantRows(inst2.getInstanceId(), h2).get(0);
        assertEquals(Integer.valueOf(0), execute(h2, "userA", TO_OPERATOR).get("code"));
        assertEquals(Integer.valueOf(99), repo.findTaskById(c2.getTaskId()).getTaskState());
        assertNotNull("退回发起人后申请节点复活", doing(inst2.getInstanceId(), "apply"));
    }

    // ═══ 正向：账本与投影 ═══

    /** 两跳账本＝2，且主办办结后仍在（variables 合并序保证存活）。 */
    @Test
    public void twoRoundsLedgerSurvivesHostFinish() throws Exception {
        ProcessInstance inst = startToReview(chain(""));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        aidant(host, "userA", "userX", "第一轮", false);
        ProcessTask first = aidantRows(inst.getInstanceId(), host).get(0);
        execute(first, "userX", AGREE);
        aidant(host, "userA", "userY", "第二轮", false);

        List<Map<String, Object>> lg = ledger(host);
        assertEquals("两跳账本＝2", 2, lg.size());
        assertEquals(1, lg.get(0).get("round"));
        assertEquals(2, lg.get(1).get("round"));
        assertEquals("第一轮", lg.get(0).get("reason"));
        assertEquals("第二轮", lg.get(1).get("reason"));
        assertEquals(Arrays.asList("userX"), lg.get(0).get("toActors"));
        assertEquals(Arrays.asList("userY"), lg.get(1).get("toActors"));

        ProcessTask second = aidantRows(inst.getInstanceId(), host).get(1);
        execute(second, "userY", AGREE);
        assertEquals(Integer.valueOf(0), execute(host, "userA", AGREE).get("code"));
        assertTrue(repo.findTaskById(host.getTaskId()).isFinished());
        assertEquals("办结后账本仍在", 2, ledger(host).size());
    }

    /** 投影 ext.aidant＝{active, round, total, submitted, actors, wait}，从 parent＋task_type 现算。 */
    @Test
    @SuppressWarnings("unchecked")
    public void detailProjectionShape() throws Exception {
        ProcessInstance inst = startToReview(chain(""));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        aidant(host, "userA", Arrays.asList("userX", "userY"), "协办原因", true);
        List<ProcessTask> rows = aidantRows(inst.getInstanceId(), host);

        Map<String, Object> detail = facade.flow("processTask/detail",
                new LinkedHashMap<String, Object>() {{ put("id", String.valueOf(host.getTaskId())); }});
        assertEquals(Integer.valueOf(0), detail.get("code"));
        Map<String, Object> ext = (Map<String, Object>) data(detail).get("ext");
        Map<String, Object> p = (Map<String, Object>) ext.get("aidant");
        assertNotNull("主办行输出 ext.aidant", p);
        assertEquals(Arrays.asList("active", "round", "total", "submitted", "actors", "wait"),
                new ArrayList<>(p.keySet()));
        assertEquals(Boolean.TRUE, p.get("active"));
        assertEquals(1, p.get("round"));
        assertEquals(2, p.get("total"));
        assertEquals(0, p.get("submitted"));
        assertEquals(Boolean.TRUE, p.get("wait"));
        List<Map<String, Object>> actors = (List<Map<String, Object>>) p.get("actors");
        assertEquals(2, actors.size());
        assertEquals(Arrays.asList("actor", "taskId", "state"), new ArrayList<>(actors.get(0).keySet()));
        assertEquals("userX", actors.get(0).get("actor"));
        assertEquals(rows.get(0).getTaskId(), actors.get(0).get("taskId"));
        assertEquals(Integer.valueOf(10), actors.get(0).get("state"));

        // 办结一条 ⇒ submitted 现算推进；清场行不进投影
        execute(rows.get(0), "userX", AGREE);
        Map<String, Object> d2 = facade.flow("processTask/detail",
                new LinkedHashMap<String, Object>() {{ put("id", String.valueOf(host.getTaskId())); }});
        Map<String, Object> p2 = (Map<String, Object>) ((Map<String, Object>) data(d2).get("ext")).get("aidant");
        assertEquals(1, p2.get("submitted"));
        assertEquals(Boolean.TRUE, p2.get("active"));

        // 无协办行的普通任务不输出该投影
        ProcessInstance other = startToReview(chain(""));
        ProcessTask plain = doing(other.getInstanceId(), "review");
        Map<String, Object> d3 = facade.flow("processTask/detail",
                new LinkedHashMap<String, Object>() {{ put("id", String.valueOf(plain.getTaskId())); }});
        assertNull(((Map<String, Object>) data(d3).get("ext")).get("aidant"));
    }

    // ═══ 负向：六文案＋越权＋非 DOING ═══

    @Test
    public void negativeActorIdsRequired() throws Exception {
        ProcessInstance inst = startToReview(chain(""));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        assertMsg(aidant(host, "userA", null, null, null), "actorIds 必填");
        assertMsg(aidant(host, "userA", new ArrayList<String>(), null, null), "actorIds 必填");
        assertMsg(aidant(host, "userA", "  ,  ", null, null), "actorIds 必填");
        assertEquals("零写入", 0, aidantRows(inst.getInstanceId(), host).size());
    }

    @Test
    public void negativeOperatorRequired() throws Exception {
        ProcessInstance inst = startToReview(chain(""));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        assertMsg(aidant(host, null, "userX", null, null), "operator 必填");
        assertMsg(aidant(host, "   ", "userX", null, null), "operator 必填");
    }

    @Test
    public void negativeTaskNotFound() throws Exception {
        startToReview(chain(""));
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("processTaskId", "999999");
        a.put("operator", "userA");
        a.put("actorIds", "userX");
        assertMsg(facade.flow("processTask/aidant", a), "任务不存在");
    }

    @Test
    public void negativeUnauthorizedOperator() throws Exception {
        ProcessInstance inst = startToReview(chain(""));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        assertMsg(aidant(host, "userZ", "userX", null, null), "无权限派生协办任务");
        assertEquals("零写入", 0, aidantRows(inst.getInstanceId(), host).size());
        assertTrue(repo.findTaskById(host.getTaskId()).isDoing());
    }

    /** 名册内参与人（含加签进来的）可派生；flow.auto／flow.admin 例外放行。 */
    @Test
    public void rosterMemberAndPrivilegedAllowed() throws Exception {
        ProcessInstance inst = startToReview(chain(""));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        // 加签进来的第二参与人同样在名册内
        facade.flow("processTask/addCandidate", new LinkedHashMap<String, Object>() {{
            put("processTaskId", String.valueOf(host.getTaskId()));
            put("actorIds", Arrays.asList("userB"));
        }});
        assertEquals(Integer.valueOf(0), aidant(host, "userB", "userX", null, null).get("code"));
        assertEquals(Integer.valueOf(0), aidant(host, "flow.auto", "userY", null, null).get("code"));
        assertEquals(Integer.valueOf(0), aidant(host, "flow.admin", "userZ", null, null).get("code"));
        assertEquals(3, aidantRows(inst.getInstanceId(), host).size());
    }

    @Test
    public void negativeHostNotDoing() throws Exception {
        ProcessInstance inst = startToReview(chain(""));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        execute(host, "userA", AGREE);
        assertTrue(repo.findTaskById(host.getTaskId()).isFinished());
        assertMsg(aidant(host, "userA", "userX", null, null), "任务非进行中，不可派生协办");
    }

    @Test
    public void negativeDuplicateActor() throws Exception {
        ProcessInstance inst = startToReview(chain(""));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        aidant(host, "userA", "userX", null, null);
        assertMsg(aidant(host, "userA", "userX", null, null), "该人已有未办结的协办任务");
        assertEquals("零写入：不重复建行", 1, aidantRows(inst.getInstanceId(), host).size());
        assertEquals(1, ledger(host).size());

        // 已办结的那位可以再派生（去重只认未办结）
        ProcessTask child = aidantRows(inst.getInstanceId(), host).get(0);
        execute(child, "userX", AGREE);
        assertEquals(Integer.valueOf(0), aidant(host, "userA", "userX", null, null).get("code"));
        assertEquals(2, aidantRows(inst.getInstanceId(), host).size());
    }

    @Test
    public void negativeRecursionForbidden() throws Exception {
        ProcessInstance inst = startToReview(chain(""));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        aidant(host, "userA", "userX", null, null);
        ProcessTask child = aidantRows(inst.getInstanceId(), host).get(0);
        assertMsg(aidant(child, "userX", "userY", null, null), "协办任务不能再派生协办");
        assertEquals("零写入", 1, aidantRows(inst.getInstanceId(), host).size());
        assertEquals(0, AidantSupport.aidantRows(
                repo.findInstanceById(inst.getInstanceId()).getTasks(), child).size());
    }

    // ═══ 协办行能力边界 ═══

    /** 协办行可加/摘参与人、可被转办（走既有 actor 写腿／transfer，不造特例）。 */
    @Test
    public void aidantRowSupportsActorWritesAndTransfer() throws Exception {
        ProcessInstance inst = startToReview(chain(""));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        aidant(host, "userA", "userX", null, null);
        ProcessTask child = aidantRows(inst.getInstanceId(), host).get(0);
        Long cid = child.getTaskId();

        // 加参与人（surrogate / addCandidate 同一腿）
        assertEquals(Integer.valueOf(0), facade.flow("processTask/surrogate",
                new LinkedHashMap<String, Object>() {{
                    put("processTaskId", String.valueOf(cid));
                    put("actorIds", Arrays.asList("userK"));
                }}).get("code"));
        assertTrue(repo.findTaskActors(cid).contains("userK"));
        assertEquals(Integer.valueOf(0), facade.flow("processTask/addCandidate",
                new LinkedHashMap<String, Object>() {{
                    put("processTaskId", String.valueOf(cid));
                    put("actorIds", Arrays.asList("userL"));
                }}).get("code"));
        assertTrue(repo.findTaskActors(cid).contains("userL"));

        // 摘参与人（只能摘自己那一票）
        assertEquals(Integer.valueOf(0), facade.flow("processTask/removeTaskActor",
                new LinkedHashMap<String, Object>() {{
                    put("processTaskId", String.valueOf(cid));
                    put("operator", "userK");
                    put("actorIds", Arrays.asList("userK"));
                }}).get("code"));
        assertFalse(repo.findTaskActors(cid).contains("userK"));

        // 转办（摘原人＋换新参与人，同一行不新建）
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("processTaskId", String.valueOf(cid));
        t.put("operator", "userX");
        t.put("fromActor", "userX");
        t.put("toActor", "userT");
        t.put("reason", "协办转办");
        assertEquals(Integer.valueOf(0), facade.flow("processTask/transfer", t).get("code"));
        assertFalse(repo.findTaskActors(cid).contains("userX"));
        assertTrue(repo.findTaskActors(cid).contains("userT"));
        assertEquals("转办不新建任务行", cid, repo.findTaskById(cid).getTaskId());
        assertTrue(repo.findTaskById(cid).isDoing());
        assertEquals(ProcessTaskTypeEnum.SECONDARY, repo.findTaskById(cid).getTaskType());
        // 转办后新办理人可办结，且仍不驱动流程
        assertEquals(Integer.valueOf(0), execute(repo.findTaskById(cid), "userT", AGREE).get("code"));
        assertTrue(repo.findTaskById(host.getTaskId()).isDoing());
        assertNull(doing(inst.getInstanceId(), "final"));
    }

    // ═══ 回归：会签合并 ═══

    /** 会签票齐即推进，不被协办行挡（合并判据显式排除 task_type=1）。 */
    @Test
    public void countersignMergeNotBlockedByAidant() throws Exception {
        ProcessInstance inst = start(countersignChain());
        List<ProcessTask> members = repo.findDoingTasks(inst.getInstanceId(), null);
        assertEquals(2, members.size());
        ProcessTask rowA = null;
        ProcessTask rowB = null;
        for (ProcessTask t : members) {
            if (t.getActorIds().contains("userA")) rowA = t;
            if (t.getActorIds().contains("userB")) rowB = t;
        }
        assertNotNull(rowA);
        assertNotNull(rowB);
        // 在会签成员行上派生协办（协办行同节点同名，但 task_type=1）
        assertEquals(Integer.valueOf(0), aidant(rowA, "userA", "userX", null, null).get("code"));
        ProcessTask child = AidantSupport.aidantRows(
                repo.findInstanceById(inst.getInstanceId()).getTasks(), repo.findTaskById(rowA.getTaskId())).get(0);
        assertTrue(child.isDoing());
        assertEquals("cs", child.getTaskName());

        // 两票齐 ⇒ 推进（改前协办行会被当第三张票，永远合并不了）
        assertEquals(Integer.valueOf(0), execute(rowA, "userA", AGREE).get("code"));
        assertEquals(Integer.valueOf(0), execute(rowB, "userB", AGREE).get("code"));
        assertEquals("票齐即推进：实例办结", Integer.valueOf(20),
                repo.findInstanceById(inst.getInstanceId()).getState());
        assertEquals("协办行随离开节点清场", Integer.valueOf(99),
                repo.findTaskById(child.getTaskId()).getTaskState());
        assertEquals(0, repo.findDoingTasks(inst.getInstanceId(), null).size());
    }

    /** 会签未齐时协办行办结也不得推进（推进闸＋合并判据两头都守住）。 */
    @Test
    public void aidantOnCountersignDoesNotAdvanceEarly() throws Exception {
        ProcessInstance inst = start(countersignChain());
        List<ProcessTask> members = repo.findDoingTasks(inst.getInstanceId(), null);
        ProcessTask rowA = members.get(0).getActorIds().contains("userA") ? members.get(0) : members.get(1);
        aidant(rowA, "userA", "userX", null, null);
        ProcessTask child = AidantSupport.aidantRows(
                repo.findInstanceById(inst.getInstanceId()).getTasks(), repo.findTaskById(rowA.getTaskId())).get(0);
        execute(child, "userX", AGREE);
        assertEquals("协办办结不推进", Integer.valueOf(10),
                repo.findInstanceById(inst.getInstanceId()).getState());
        assertEquals(2, doingCount(inst.getInstanceId(), "cs"));
    }

    // ═══ 回归：与征询并存 ═══

    /** 与征询同节点并存：两族子行靠 task_type 分辨，等待闸与收齐闸各数各的行。 */
    @Test
    public void coexistWithCollectTwoGatesCountOwnRows() throws Exception {
        ProcessInstance inst = startToReview(chain("\"dynamicCountersign\":true"));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        // 征询拉一人（成员行 task_type=0）
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("processTaskId", String.valueOf(host.getTaskId()));
        c.put("operator", "userA");
        c.put(FlowConst.SUBMIT_TYPE, AGREE);
        c.put(FlowConst.ADD_SIGN_OPERATORS, Arrays.asList("userC"));
        assertEquals(Integer.valueOf(0), facade.flow("processTask/execute", c).get("code"));
        // 协办派生一人（协办行 task_type=1），等待闸开
        assertEquals(Integer.valueOf(0), aidant(host, "userA", "userX", null, true).get("code"));

        ProcessTask hostRow = repo.findTaskById(host.getTaskId());
        List<ProcessTask> all = repo.findInstanceById(inst.getInstanceId()).getTasks();
        assertEquals("征询收齐闸只数成员行", 1, CollectSupport.countMembers(all, hostRow)[0]);
        assertEquals("协办等待闸只数协办行", 1, AidantSupport.countAidant(all, hostRow)[0]);
        assertEquals(1, CollectSupport.memberRows(all, hostRow).size());
        assertEquals(1, AidantSupport.aidantRows(all, hostRow).size());
        assertFalse("协办行不是征询成员行",
                CollectSupport.isMemberRow(all, AidantSupport.aidantRows(all, hostRow).get(0)));

        // 协办先办结 ⇒ 征询闸仍拦（各数各的行），文案是征询那一族
        ProcessTask aidantRow = AidantSupport.aidantRows(all, hostRow).get(0);
        execute(aidantRow, "userX", AGREE);
        assertMsg(execute(hostRow, "userA", AGREE), "节点[review]动态会签意见未收齐（已提交 0/1），不能办理");

        // 征询成员交意见 ⇒ 两闸齐过，主办推进
        ProcessTask member = CollectSupport.memberRows(
                repo.findInstanceById(inst.getInstanceId()).getTasks(), repo.findTaskById(host.getTaskId())).get(0);
        assertEquals(Integer.valueOf(0), execute(member, "userC", AGREE).get("code"));
        assertEquals(Integer.valueOf(0), execute(repo.findTaskById(host.getTaskId()), "userA", AGREE).get("code"));
        assertNotNull(doing(inst.getInstanceId(), "final"));
    }

    /** 反向：征询已收齐、协办未办结 ⇒ 报协办那一族文案（证明两闸不串味）。 */
    @Test
    public void collectDoneButAidantPendingReportsAidantMessage() throws Exception {
        ProcessInstance inst = startToReview(chain("\"dynamicCountersign\":true"));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("processTaskId", String.valueOf(host.getTaskId()));
        c.put("operator", "userA");
        c.put(FlowConst.SUBMIT_TYPE, AGREE);
        c.put(FlowConst.ADD_SIGN_OPERATORS, Arrays.asList("userC"));
        facade.flow("processTask/execute", c);
        aidant(host, "userA", "userX", null, true);
        ProcessTask member = CollectSupport.memberRows(
                repo.findInstanceById(inst.getInstanceId()).getTasks(), repo.findTaskById(host.getTaskId())).get(0);
        execute(member, "userC", AGREE);
        assertMsg(execute(repo.findTaskById(host.getTaskId()), "userA", AGREE),
                "节点[review]协办未办结（已办结 0/1），不能办理");
    }

    /** 征询节点上 waitCompletion=false：主办收齐后可推进，未办结协办行清场（加签闸不被协办行挡）。 */
    @Test
    public void collectNodeWaitFalseNotBlockedByAidant() throws Exception {
        ProcessInstance inst = startToReview(chain("\"dynamicCountersign\":true"));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("processTaskId", String.valueOf(host.getTaskId()));
        c.put("operator", "userA");
        c.put(FlowConst.SUBMIT_TYPE, AGREE);
        c.put(FlowConst.ADD_SIGN_OPERATORS, Arrays.asList("userC"));
        facade.flow("processTask/execute", c);
        aidant(host, "userA", "userX", null, false);
        ProcessTask member = CollectSupport.memberRows(
                repo.findInstanceById(inst.getInstanceId()).getTasks(), repo.findTaskById(host.getTaskId())).get(0);
        execute(member, "userC", AGREE);
        assertEquals("缺省不等协办：收齐即推进", Integer.valueOf(0),
                execute(repo.findTaskById(host.getTaskId()), "userA", AGREE).get("code"));
        assertNotNull(doing(inst.getInstanceId(), "final"));
        ProcessTask aidantRow = AidantSupport.aidantRows(
                repo.findInstanceById(inst.getInstanceId()).getTasks(), repo.findTaskById(host.getTaskId())).get(0);
        assertEquals(Integer.valueOf(99), repo.findTaskById(aidantRow.getTaskId()).getTaskState());
    }

    // ═══ 回归：撤回清场 ═══

    /** 已办撤回：下游 DOING 协办行一并废弃（血缘自然覆盖），复活行是唯一活待办。 */
    @Test
    public void recallCleansAidantRows() throws Exception {
        ProcessInstance inst = startToReview(chain("\"allowRecall\":true"));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        assertEquals(Integer.valueOf(0), execute(host, "userA", AGREE).get("code"));
        ProcessTask finalRow = doing(inst.getInstanceId(), "final");
        assertNotNull(finalRow);
        // 在下游 final 行上派生协办 ⇒ 它是 review 行的血缘后代
        assertEquals(Integer.valueOf(0), aidant(finalRow, "userF", "userX", null, null).get("code"));
        ProcessTask child = AidantSupport.aidantRows(
                repo.findInstanceById(inst.getInstanceId()).getTasks(), repo.findTaskById(finalRow.getTaskId())).get(0);
        assertTrue(child.isDoing());

        ProcessTask resurrected = engine.recallTask(host.getTaskId(), "userA");
        assertTrue(resurrected.isDoing());
        assertEquals("review", resurrected.getTaskName());
        assertEquals("下游协办行废弃", Integer.valueOf(99), repo.findTaskById(child.getTaskId()).getTaskState());
        assertEquals("下游主办行废弃", Integer.valueOf(99), repo.findTaskById(finalRow.getTaskId()).getTaskState());
        assertEquals("唯一活待办", 1, repo.findDoingTasks(inst.getInstanceId(), null).size());
    }

    /** 实例撤回：DOING 协办行随整单置 WITHDRAW(30)，不留孤儿待办。 */
    @Test
    public void instanceWithdrawCleansAidantRows() throws Exception {
        ProcessInstance inst = startToReview(chain(""));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        aidant(host, "userA", "userX", null, null);
        ProcessTask child = aidantRows(inst.getInstanceId(), host).get(0);
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("id", String.valueOf(inst.getInstanceId()));
        w.put("operator", "applicant");
        assertEquals(Integer.valueOf(0), facade.flow("processInstance/withdraw", w).get("code"));
        assertEquals(Integer.valueOf(30), repo.findTaskById(child.getTaskId()).getTaskState());
        assertEquals(0, repo.findDoingTasks(inst.getInstanceId(), null).size());
    }

    // ═══ 回归：既有 actor 写腿不变 ═══

    /** transfer／surrogate／addCandidate 在主办行上行为不变（协办存在也不影响）。 */
    @Test
    public void transferSurrogateAddCandidateUnchanged() throws Exception {
        ProcessInstance inst = startToReview(chain(""));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        Long hid = host.getTaskId();
        aidant(host, "userA", "userX", null, null);

        // surrogate：只追加，原人保留
        assertEquals(Integer.valueOf(0), facade.flow("processTask/surrogate",
                new LinkedHashMap<String, Object>() {{
                    put("processTaskId", String.valueOf(hid));
                    put("actorIds", Arrays.asList("userB"));
                }}).get("code"));
        assertEquals(Arrays.asList("userA", "userB"), repo.findTaskActors(hid));
        // addCandidate：同一腿
        assertEquals(Integer.valueOf(0), facade.flow("processTask/addCandidate",
                new LinkedHashMap<String, Object>() {{
                    put("processTaskId", String.valueOf(hid));
                    put("actorIds", Arrays.asList("userC"));
                }}).get("code"));
        assertEquals(Arrays.asList("userA", "userB", "userC"), repo.findTaskActors(hid));
        // transfer：摘原人换新参与人，同一行不新建，留痕三件照旧
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("processTaskId", String.valueOf(hid));
        t.put("operator", "userA");
        t.put("fromActor", "userA");
        t.put("toActor", "userD");
        t.put("reason", "转办原因");
        assertEquals(Integer.valueOf(0), facade.flow("processTask/transfer", t).get("code"));
        assertFalse(repo.findTaskActors(hid).contains("userA"));
        assertTrue(repo.findTaskActors(hid).contains("userD"));
        ProcessTask after = repo.findTaskById(hid);
        assertEquals(hid, after.getTaskId());
        assertEquals(Integer.valueOf(7), after.getVariables().getInt(FlowConst.SUBMIT_TYPE));
        assertNotNull(after.getVariables().get(FlowConst.TRANSFER_HISTORY));
        assertNull("转办不写 actor_id 列", after.getActorId());
        // 协办行不受主办转办影响，仍 DOING 且不驱动
        assertEquals(1, aidantRows(inst.getInstanceId(), after).size());
        assertTrue(aidantRows(inst.getInstanceId(), after).get(0).isDoing());
        // 新办理人办结照常推进
        assertEquals(Integer.valueOf(0), execute(after, "userD", AGREE).get("code"));
        assertNotNull(doing(inst.getInstanceId(), "final"));
    }

    /** 权限码复用 wf:processTask:execute（OR 规则，照 246/247 B 案惯例，不建按钮种子）。 */
    @Test
    public void permissionCodeRidesExecute() {
        DefaultActionPermissionProvider provider = new DefaultActionPermissionProvider();
        assertArrayEquals(new String[]{"wf:processTask:execute"},
                provider.permissionCodes("processTask/aidant"));
        // 哨兵：没进表的 action 仍走默认派生
        assertArrayEquals(new String[]{"wf:processTask:transfer"},
                provider.permissionCodes("processTask/transfer"));
    }

    /** 节点级 taskType=1 仍解析期禁配（issues/252 不因本轮运行期派生而放宽）。 */
    @Test
    public void nodeLevelSecondaryStillBanned() {
        try {
            startToReview(chain("\"taskType\":1"));
            fail("节点级协办配置必须解析期报错");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("不允许配置协办任务类型"));
        }
    }
}