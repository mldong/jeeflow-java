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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * 任务参与者写侧归属值归一（issues/142 B 批 · owner 2026-09-30 拍「八栈一起收：两形同判据＋
 * 写侧兜底＋trim＋哨兵」· Java 栈内存仓一路）。
 *
 * <p>立法逐字依据＝spec 06-facade.md <b>§2.11</b>（§2.10 的同一条尺子换到任务侧）。
 * {@code wf_process_task_actor.actor_id} 是归属列，空串／纯空白／{@code null} 一旦落进去，
 * 就是 §2.5 与 issues/129 那族"空归属值读全库"的进水口。java 的反面形状（普查 142 §2 B 表实读）：
 * {@code toStringList} 逗号串腿 trim＋丢空、<b>数组腿走 {@code String.valueOf(o)}</b>
 * ⇒ 不 trim、不丢空、{@code null} 变成字符串 {@code "null"} 照落；transfer 腿 {@code toStr}
 * 不 trim ⇒ {@code " 123 "} 原样进 {@code addTaskActor}；内存仓 {@code addTaskActor}
 * 判重不判空不 trim。</p>
 *
 * <p>四条硬要求逐条对应本文件的用例：① <b>两层都挡</b>（漏斗 ＋ 仓储写侧，见
 * {@link #repoWritePathAlsoDropsBlankActors} 与 {@link #fullOverwriteLegAlsoDropsBlankActors}）；
 * ② <b>落库与比较一律取 trim 后的值</b>（{@link #transferTrimsFromActorAndToActor} 的删除腿、
 * {@link #ccStatusReadComparesTrimmedValue} 的比较腿）；③ <b>空入参档沿用既有"缺参数"错误信封</b>
 * （不新造文案，见 {@link #blankAndMissingArmsReuseTheExistingErrorEnvelope}）；
 * ④ <b>反向哨兵</b>：{@code "0"} 这类"看起来像空"的正常 id 不得被丢掉，判空一律
 * {@code trim().isEmpty()}（{@link #zeroLikeIdsSurviveAndAreNotCollapsedTogether}）。</p>
 *
 * <p>另有 {@link #ccLegAndTaskLegShareOneRuler}：§2.11 尾注要求"复用 §2.10 已落地的那一枚单点，
 * 不要再抄第二份"——同一份入参在抄送腿与任务腿必须得到同一个答案。</p>
 *
 * <p>SQL 仓一路（含"历史 {@code actor_id=''} 脏行不得被空 operator 打上已读"这一档）在
 * {@code jeeflow-repository-jdbc} 的 {@code JdbcTaskActorWriteNormalizeTest}，两仓同判据。</p>
 */
public class TaskActorWriteNormalizeTest {

    /** start → apply(assignee=applicant) → approval(assignee=leader) → end：两节点，办理腿能推进出下一任务。 */
    private static final String TWO_TASK_FLOW =
            ("{'name':'task-actor-142','displayName':'任务参与者归属值归一','type':'approval','nodes':["
            + "{'id':'start','type':'snaker:start','x':100,'y':200,'properties':{},'text':{'value':'开始'}},"
            + "{'id':'apply','type':'snaker:task','x':200,'y':200,'properties':{'form':'apply-form',"
            + "'assignee':'applicant','taskType':0,'performType':0},'text':{'value':'发起申请'}},"
            + "{'id':'approval','type':'snaker:task','x':300,'y':200,'properties':{'form':'leave-form',"
            + "'assignee':'leader','taskType':0,'performType':0},'text':{'value':'审批'}},"
            + "{'id':'end','type':'snaker:end','x':400,'y':200,'properties':{},'text':{'value':'结束'}}],"
            + "'edges':["
            + "{'id':'e1','sourceNodeId':'start','targetNodeId':'apply','properties':{}},"
            + "{'id':'e2','sourceNodeId':'apply','targetNodeId':'approval','properties':{}},"
            + "{'id':'e3','sourceNodeId':'approval','targetNodeId':'end','properties':{}}]}").replace('\'', '"');

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

    // ═══ 夹具与取证辅助 ═══

    private ProcessInstance.ProcessDefine addDefine() {
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName("task-actor-142-" + System.nanoTime());
        def.setDisplayName("任务参与者归属值归一");
        def.setType("approval");
        def.setState(1);
        def.setVersion(1);
        def.setContent(TWO_TASK_FLOW.getBytes(StandardCharsets.UTF_8));
        repo.addDefine(def);
        return def;
    }

    /** 发起一条实例，停在 apply 节点（参与者＝发起人 zhangsan）。 */
    private Long startInstance() {
        ProcessInstance inst = engine.startProcessInstanceById(addDefine().getId(), "zhangsan",
                FlowData.create());
        assertNotNull(inst.getInstanceId());
        return inst.getInstanceId();
    }

    private ProcessTask task(Long instanceId, String taskName) {
        for (ProcessTask t : repo.findDoingTasks(instanceId, null)) {
            if (taskName.equals(t.getTaskName())) return t;
        }
        throw new IllegalStateException("夹具里没有进行中任务: " + taskName);
    }

    /** 办理 apply ⇒ 推进出 approval 节点（nextNodeOperator 的消费点）。 */
    private ProcessTask advanceToApproval(Long instanceId, Object nextNodeOperator) {
        FlowData args = FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.APPLY.getCode());
        if (nextNodeOperator != null) args.set(FlowConst.NEXT_NODE_OPERATOR, nextNodeOperator);
        engine.executeProcessTask(task(instanceId, "apply").getTaskId(), "zhangsan", args);
        return task(instanceId, "approval");
    }

    /** 参与者取证走仓储而不是返回值——判据必须打在"落库的值"上。 */
    private List<String> actors(Long taskId) {
        return repo.findTaskActors(taskId);
    }

    private Map<String, Object> args(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i].toString(), kv[i + 1]);
        return m;
    }

    private Map<String, Object> addCandidate(Long taskId, Object actorIds) {
        return facade.flow("processTask/addCandidate",
                args("processTaskId", taskId, "actorIds", actorIds));
    }

    private void assertOk(Map<String, Object> resp) {
        assertEquals("应成功: " + resp, Integer.valueOf(0), resp.get("code"));
    }

    // ═══ §2.11 第 1 行：addCandidate／surrogate 集合解析（数组腿与逗号串腿同判据） ═══

    /**
     * 数组腿的三种垃圾形状必须一次堵掉：{@code null} 元素<b>不得</b>被 {@code String.valueOf}
     * 串化成 {@code "null"} 落进归属列；空串/纯空白丢弃；带空格的值取 trim 后的串；
     * 同一次调用内的重复折叠。
     */
    @Test
    public void arrayLegDropsNullAndBlankAndStoresTrimmedValues() {
        Long instanceId = startInstance();
        Long taskId = task(instanceId, "apply").getTaskId();

        assertOk(addCandidate(taskId, Arrays.asList(" 9001 ", null, "", "   ", "9002", "9001")));

        assertEquals("数组腿：null/空串/纯空白丢弃、值取 trim 后的串、同次调用折叠",
                Arrays.asList("zhangsan", "9001", "9002"), actors(taskId));
        assertTrueNoDirtyValue(taskId);
    }

    /** 加签（surrogate）与 addCandidate 同体，必须同判据（spec §2.11 第 1 行点名两条 action）。 */
    @Test
    public void surrogateLegSharesTheSameRulerAsAddCandidate() {
        Long instanceId = startInstance();
        Long taskId = task(instanceId, "apply").getTaskId();

        assertOk(facade.flow("processTask/surrogate", args("processTaskId", taskId,
                "actorIds", Arrays.asList("9101", null, " 9102 ", "9102"))));

        assertEquals(Arrays.asList("zhangsan", "9101", "9102"), actors(taskId));
        assertTrueNoDirtyValue(taskId);
    }

    /**
     * 两形同判据：同一群人分别以数组形态与逗号串形态给，落库结果必须逐字相同。
     * java 现状正是"逗号串腿 trim＋丢空、数组腿不 trim 不丢空"——两把尺子在这一格当场分叉。
     */
    @Test
    public void commaStringLegAndArrayLegGiveTheSameAnswer() {
        Long instanceId = startInstance();
        Long arrayTask = task(instanceId, "apply").getTaskId();
        Long stringTask = task(startInstance(), "apply").getTaskId();

        assertOk(addCandidate(arrayTask, Arrays.asList("9101", " 9101 ", "", null, "9102")));
        assertOk(addCandidate(stringTask, "9101, 9101, ,9102"));

        List<String> fromArray = new ArrayList<>(actors(arrayTask));
        List<String> fromString = new ArrayList<>(actors(stringTask));
        fromArray.remove("zhangsan");
        fromString.remove("zhangsan");
        assertEquals("逗号串与数组两形必须同判据（spec §2.11 第 1 行）", fromString, fromArray);
        assertEquals(Arrays.asList("9101", "9102"), fromArray);
    }

    /** 数字元素收敛成字符串<b>但要 trim</b>（§2.11 第 3 行"数组元素不得被静默丢弃或串化成类型名"）。 */
    @Test
    public void numericElementsConvergeToStringsAndStillGetTrimmed() {
        Long instanceId = startInstance();
        Long taskId = task(instanceId, "apply").getTaskId();

        assertOk(addCandidate(taskId, Arrays.asList(9201, " 9202 ", 9201L)));

        assertEquals("数字元素收敛为字符串、带空格的元素照样 trim、同次调用折叠",
                Arrays.asList("zhangsan", "9201", "9202"), actors(taskId));
    }

    /**
     * 反向哨兵（§2.11 硬要求④）：判据只吃"trim 后为空"，不吃"看起来像空"的正常 id。
     * {@code "0"} 与 {@code "00"} 是<b>两个不同的人</b>（php 本轮实测到松散 {@code in_array}
     * 把第二个人静默丢掉），{@code "a"} 照落；只有纯空白那一个丢弃。
     */
    @Test
    public void zeroLikeIdsSurviveAndAreNotCollapsedTogether() {
        Long instanceId = startInstance();
        Long taskId = task(instanceId, "apply").getTaskId();

        assertOk(addCandidate(taskId, Arrays.asList("0", "00", "  ", "a")));

        assertEquals("哨兵：'0'/'00'/'a' 都得留下且各算一个人，只有纯空白丢弃",
                Arrays.asList("zhangsan", "0", "00", "a"), actors(taskId));
    }

    // ═══ §2.11 第 2 行：transfer 的 fromActor／toActor ═══

    /**
     * 转办两条腿都必须先归一再用作删除与插入：{@code " leader "} 与 {@code "leader"} 是同一个人
     * （硬要求②"落库与比较一律取 trim 后的值"），摘人判据才打得上；插入的是 trim 后的串，
     * 留痕 {@code tf_transferTo}/{@code tf_transferHistory} 也记 trim 后的串。
     */
    @Test
    public void transferTrimsFromActorAndToActor() {
        Long instanceId = startInstance();
        Long taskId = advanceToApproval(instanceId, null).getTaskId();   // approval 参与者＝leader
        assertEquals(Arrays.asList("leader"), actors(taskId));

        assertOk(facade.flow("processTask/transfer", args(
                "processTaskId", taskId, "operator", "leader",
                "fromActor", " leader ", "toActor", " boss ")));

        assertEquals("摘原人＋加新人都在 trim 后的值上", Arrays.asList("boss"), actors(taskId));
        assertTrueNoDirtyValue(taskId);
        ProcessTask transferred = repo.findTaskById(taskId);
        assertEquals("留痕单跳键取 trim 后的值", "boss",
                transferred.getVariables().getStr(FlowConst.TRANSFER_TO));
        List<?> history = (List<?>) transferred.getVariables().get(FlowConst.TRANSFER_HISTORY);
        assertNotNull("转办账本必须还在", history);
        Map<?, ?> hop = (Map<?, ?>) history.get(0);
        assertEquals("账本 fromActor 归一", "leader", hop.get("fromActor"));
        assertEquals("账本 toActor 归一", "boss", hop.get("toActor"));
    }

    /** 归一后同人不得并存两行：{@code fromActor="leader"} 转给 {@code " leader "} 之外的同一人 ⇒ 明确报"已是参与人"。 */
    @Test
    public void transferTargetAlreadyParticipantIsDetectedAfterTrim() {
        Long instanceId = startInstance();
        Long taskId = advanceToApproval(instanceId, null).getTaskId();

        Map<String, Object> resp = facade.flow("processTask/transfer", args(
                "processTaskId", taskId, "operator", "leader",
                "fromActor", "leader", "toActor", " leader "));

        assertEquals("trim 后 toActor 与 fromActor 同人 ⇒ 契约第 6 条去重档", "目标人已是该任务参与人", resp.get("msg"));
        assertEquals("原参与者一行不动", Arrays.asList("leader"), actors(taskId));
    }

    // ═══ §2.11 硬要求③ ＋ 主键档：沿用既有"缺参数"信封，主键必须响亮报错 ═══

    /**
     * 空入参档沿用既有文案（不新造错误码/错误语义）：加签丢完为空 ⇒ {@code processTaskId/actorIds 缺失}；
     * 转办缺 from/to ⇒ {@code fromActor 必填}/{@code toActor 必填}。
     * 主键档另判一级：{@code processTaskId} 缺失或空串<b>必须响亮报错</b>，
     * 不得拿 {@code ''}/{@code 0} 当 id 往下落库（这条与"归属值为空 ⇒ 丢弃"是两件事）。
     */
    @Test
    public void blankAndMissingArmsReuseTheExistingErrorEnvelope() {
        Long instanceId = startInstance();
        Long taskId = task(instanceId, "apply").getTaskId();

        Map<String, Object> allBlank = addCandidate(taskId, Arrays.asList("", "  ", null));
        assertEquals("丢完为空 ⇒ 既有缺参数档，不新造文案",
                "processTaskId/actorIds 缺失", allBlank.get("msg"));
        assertEquals("空入参不得动到既有参与者", Arrays.asList("zhangsan"), actors(taskId));

        Map<String, Object> noTaskId = facade.flow("processTask/addCandidate",
                args("processTaskId", "", "actorIds", Arrays.asList("9701")));
        assertEquals("主键空串 ⇒ 响亮报错（不得拿 '' 当 id 落库）",
                "processTaskId/actorIds 缺失", noTaskId.get("msg"));
        assertEquals("报错后一条参与者都不许落", Arrays.asList("zhangsan"), actors(taskId));

        Map<String, Object> noFrom = facade.flow("processTask/transfer",
                args("processTaskId", taskId, "operator", "zhangsan", "toActor", "boss"));
        assertEquals("fromActor 必填", noFrom.get("msg"));
        Map<String, Object> blankTo = facade.flow("processTask/transfer",
                args("processTaskId", taskId, "operator", "zhangsan", "fromActor", "zhangsan", "toActor", "   "));
        assertEquals("toActor 必填（纯空白＝必填档）", "toActor 必填", blankTo.get("msg"));
        assertEquals("两个报错档都不得改动作参与者", Arrays.asList("zhangsan"), actors(taskId));
    }

    // ═══ §2.11 第 3 行：f_nextNodeOperator／tf_nextNodeOperator 消费腿 ═══

    /**
     * 发起人指定下一节点参与者的<b>数组腿</b>：{@code null} 元素绝不能再变成 {@code "null"}
     * （普查 B 表 java 行点名的反面形状），空串/纯空白丢弃，带空格 trim，同次调用折叠。
     */
    @Test
    public void nextNodeOperatorArrayLegDoesNotStringifyNull() {
        Long instanceId = startInstance();

        ProcessTask approval = advanceToApproval(instanceId, Arrays.asList("A001", null, "", "  A002 ", "A001"));

        assertEquals("数组腿与逗号串腿同判据：null 丢、trim、折叠",
                Arrays.asList("A001", "A002"), actors(approval.getTaskId()));
        assertTrueNoDirtyValue(approval.getTaskId());
    }

    /** 消费腿两形同判据：逗号串与数组（含数字元素）给出同一群人。 */
    @Test
    public void nextNodeOperatorBothShapesAgree() {
        Long instanceId = startInstance();
        ProcessTask fromString = advanceToApproval(instanceId, "B001, B002,,B001");

        Long other = startInstance();
        ProcessTask fromArray = advanceToApproval(other, Arrays.asList("B001", " B002 ", "", "B001"));

        assertEquals("两形同判据", actors(fromString.getTaskId()), actors(fromArray.getTaskId()));
        assertEquals(Arrays.asList("B001", "B002"), actors(fromString.getTaskId()));
    }

    /** 发起腿 {@code f_nextNodeOperator}（门面收敛成 {@code tf_} 后走同一支消费腿）：数组带 null 同样不落 {@code "null"}。 */
    @Test
    public void startLegNextNodeOperatorSharesTheRuler() {
        Long defineId = addDefine().getId();

        Map<String, Object> resp = facade.flow("processDefine/startAndExecute", args(
                "processDefineId", defineId, "operator", "zhangsan",
                FlowConst.PROCESS_START_NEXT_NODE_OPERATOR, Arrays.asList("F001", null, "  F001 ", "")));
        assertOk(resp);
        Long instanceId = Long.valueOf(String.valueOf(
                ((Map<?, ?>) resp.get("data")).get(FlowConst.PROCESS_INSTANCE_ID_KEY)));

        ProcessTask approval = task(instanceId, "approval");
        assertEquals(Arrays.asList("F001"), actors(approval.getTaskId()));
        assertTrueNoDirtyValue(approval.getTaskId());
    }

    // ═══ §2.11 第 5 行：仓储写侧自己再挡一次（内存仓一路，绕过门面/引擎直连仓储） ═══

    /**
     * 只修漏斗不修写侧 ⇒ 绕过门面直连仓储的调用方照样灌空值（§2.10 硬要求①"两层都挡"，
     * 逐字搬到任务侧）。追加腿：空串/纯空白/null 丢弃、落库值 trim、与既有参与者判重。
     */
    @Test
    public void repoWritePathAlsoDropsBlankActors() {
        Long instanceId = startInstance();
        Long taskId = task(instanceId, "apply").getTaskId();

        repo.addTaskActor(taskId, Arrays.asList("", "   ", null, " 9301 ", "9301", "9302"));

        assertEquals("内存仓写侧空串/纯空白/null 都不落，值取 trim 后的串",
                Arrays.asList("zhangsan", "9301", "9302"), actors(taskId));
        assertTrueNoDirtyValue(taskId);
    }

    /**
     * 全量覆写腿（{@code saveTask} 随任务落 {@code task.getActorIds()}，对应 JDBC 仓的
     * {@code saveTaskActors}）也必须在插入前挡一次——委托并入、聚合副本回写都走这一支。
     */
    @Test
    public void fullOverwriteLegAlsoDropsBlankActors() {
        Long instanceId = startInstance();
        ProcessTask apply = task(instanceId, "apply");

        apply.setActorIds(new ArrayList<>(Arrays.asList("zhangsan", "", " 9401 ", null, "9401", "   ")));
        repo.saveTask(apply);

        assertEquals("全量覆写腿同样 trim＋丢空＋折叠", Arrays.asList("zhangsan", "9401"), actors(apply.getTaskId()));
        assertTrueNoDirtyValue(apply.getTaskId());
    }

    // ═══ §2.11 尾注：cc 腿与任务腿复用同一枚单点（严禁第二份判据） ═══

    /**
     * 同一份入参（含 {@code null}／空串／带空格／重复／{@code "0"}）分别喂抄送腿与加签腿，
     * 两边新增的人必须逐字相同。若两处各抄一份判据（php 本轮实测到的"两把尺子"），这一格当场红。
     */
    @Test
    public void ccLegAndTaskLegShareOneRuler() {
        Long instanceId = startInstance();
        Long taskId = task(instanceId, "apply").getTaskId();
        List<Object> sameInput = Arrays.<Object>asList(" 9601 ", "", null, "9601", "0", "   ", "0");
        ccEvents.clear();

        assertOk(facade.flow("processInstance/createCCInstance",
                args("processInstanceId", instanceId, "operator", "zhangsan", "actorIds", sameInput)));
        assertOk(addCandidate(taskId, sameInput));

        List<String> ccAdded = new ArrayList<>(repo.ccActorsForTest(instanceId));
        List<String> taskAdded = new ArrayList<>(actors(taskId));
        taskAdded.remove("zhangsan");
        assertEquals("任务侧新增的人必须与抄送侧同一批（同一枚归一单点）", ccAdded, taskAdded);
        assertEquals(Arrays.asList("9601", "0"), ccAdded);
    }

    // ═══ §2.11 第 4 行：processInstance/updateCCStatus 的 operator 入参归一后再比 ═══

    /**
     * 已读判据必须落在 trim 后的值上：cc 行落的是 {@code "9501"}，入参给 {@code " 9501 "}
     * 必须打得上（硬要求②）。现状是拿未 trim 的原值比较 ⇒ 打不上，用户点了"已读"库里还是未读。
     */
    @Test
    public void ccStatusReadComparesTrimmedValue() {
        Long instanceId = startInstance();
        assertOk(facade.flow("processInstance/createCCInstance",
                args("processInstanceId", instanceId, "operator", "zhangsan", "actorIds", Arrays.asList(" 9501 "))));
        assertEquals(Arrays.asList("9501"), repo.ccActorsForTest(instanceId));

        assertOk(facade.flow("processInstance/updateCCStatus",
                args("processInstanceId", instanceId, "operator", " 9501 ")));

        List<MemoryProcessRepository.CcRow> rows = repo.ccRowsForTest(instanceId);
        assertEquals(1, rows.size());
        assertEquals("带空格的 operator 必须打上 trim 后那一行（state 0→1）", 1, rows.get(0).state);
    }

    /**
     * 写侧兜底（第二层）：直连仓储给空串/纯空白 operator ⇒ 一行都不许动。
     * 内存仓新数据已经建不出 {@code actor_id=''} 的行，历史脏行那一档在 SQL 仓一路钉
     * （{@code JdbcTaskActorWriteNormalizeTest#blankOperatorDoesNotMarkHistoricalDirtyRowAsRead}）。
     */
    @Test
    public void blankCcStatusOperatorChangesNoRows() {
        Long instanceId = startInstance();
        repo.createCcInstance(instanceId, "zhangsan", "9551", "9552");

        repo.updateCcStatus(instanceId, "   ");
        repo.updateCcStatus(instanceId, "");
        repo.updateCcStatus(instanceId, null);

        for (MemoryProcessRepository.CcRow row : repo.ccRowsForTest(instanceId)) {
            assertEquals("空 operator 不得把任何 cc 行打成已读: " + row.actorId, 0, row.state);
        }
    }

    // ── 取证辅助 ──

    /** 归属列里一个脏值都不许有：空串、纯空白、{@code null}、{@code "null"}、带空格的串。 */
    private void assertTrueNoDirtyValue(Long taskId) {
        List<String> stored = actors(taskId);
        for (String actor : stored) {
            assertNotNull("actor_id 不得为 null（null 元素必须丢弃，§2.11 第 1 行）", actor);
            assertEquals("actor_id 不得是空串/纯空白: [" + actor + "]", actor.trim(), actor);
            assertEquals("actor_id 不得是 null 串化出来的 \"null\"", -1, stored.indexOf("null"));
            assertEquals("同一次调用内的重复必须折叠: " + stored, stored.indexOf(actor), stored.lastIndexOf(actor));
        }
    }
}
