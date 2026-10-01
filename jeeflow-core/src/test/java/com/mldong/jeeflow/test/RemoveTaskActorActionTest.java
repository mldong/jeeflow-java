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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 门面第 <b>47</b> 个 action {@code processTask/removeTaskActor}（issues/115 残留 · Java 基准腿，
 * 批二 §3-8）。契约逐字依据＝spec 06-facade.md <b>§processTask/removeTaskActor</b>（六条语义＋
 * 守卫次序）与 §2.11（归属值归一单点）。
 *
 * <p>它填的是 SPI 与门面之间那段空档：{@code IProcessRepository.removeTaskActor} 从第一天起就是
 * <b>必选</b>方法、八栈两仓都实现，但门面没有对应 action，摘人只能靠 {@code transfer}（摘 A <b>并</b>加 B）。
 * §3-0 前端消费面普查实测 vben5-wf 根本没有"摘人"场景（"转办"走的是 {@code processTask/surrogate}），
 * owner 10-01 拍「3.8 做吧，要不然后面又扫到这个」⇒ 契约先行补齐，八栈同批。</p>
 *
 * <p>三个兄弟 action 的分工是本文件的判据主线：
 * {@code surrogate}/{@code addCandidate} 只加、{@code transfer} 换人＋留痕、本 action <b>只摘不加零留痕</b>
 * （不写变量、不覆写任务 {@code actor_id}/{@code operator} 列、<b>不 fire 事件</b>——132 定稿事件集没有"摘人"码）。
 * 每条负向都同时断言"参与者一动不动"，因为摘人是删除操作，报错却删了一半比报错更糟。</p>
 *
 * <p>{@link #blankActorIdsNeverReachDeleteAndHistoricalDirtyRowSurvives} 对应门禁新格
 * 「带空格入参可删 ∧ 空值不误删 {@code actor_id=''} 脏行」：内存仓写侧归一后建不出空串行，
 * 故用 {@link DirtyRowSpyRepo} 复刻历史脏行与 {@code DELETE ... actor_id IN (...)} 的语义，
 * 判据打在<b>喂给 DELETE 的那一份实参</b>上（空串/null 绝不能出现）＋脏行必须还在。</p>
 */
public class RemoveTaskActorActionTest {

    /** start → apply(assignee=applicant) → approval(assignee=leader) → end。 */
    private static final String TWO_TASK_FLOW =
            ("{'name':'remove-actor-115','displayName':'摘除参与人','type':'approval','nodes':["
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
    private DirtyRowSpyRepo repo;
    private JeeflowEngineImpl engine;
    private JeeflowFacade facade;
    private final List<ProcessEvent> events = new CopyOnWriteArrayList<>();

    @Before
    public void setUp() {
        savedContext = ServiceContext.getContext();
        repo = new DirtyRowSpyRepo();
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
        ctx.put("recorder", (ProcessEventListener) event -> events.add(event));
        engine = new JeeflowEngineImpl();
        engine.configure(config);
        facade = new JeeflowFacade(engine, repo, new MemoryProcessExtRepository());
    }

    @After
    public void tearDown() {
        events.clear();
        if (savedContext != null) {
            ServiceContext.setContext(savedContext);
        }
    }

    // ═══ 夹具与取证辅助 ═══

    private ProcessInstance.ProcessDefine addDefine() {
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName("remove-actor-115-" + System.nanoTime());
        def.setDisplayName("摘除参与人");
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
        return inst.getInstanceId();
    }

    private ProcessTask task(Long instanceId, String taskName) {
        for (ProcessTask t : repo.findDoingTasks(instanceId, null)) {
            if (taskName.equals(t.getTaskName())) return t;
        }
        throw new IllegalStateException("夹具里没有进行中任务: " + taskName);
    }

    /** 加签成人手（用兄弟 action 造多参与者现场，不直接塞仓储）。 */
    private void addActors(Long taskId, String... actors) {
        assertEquals(Integer.valueOf(0), facade.flow("processTask/addCandidate",
                args("processTaskId", taskId, "actorIds", Arrays.asList((Object[]) actors))).get("code"));
    }

    /** 办结 apply ⇒ 该任务离开 DOING（历史任务那一档的夹具）。 */
    private void finishApply(Long instanceId) {
        engine.executeProcessTask(task(instanceId, "apply").getTaskId(), "zhangsan",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.APPLY.getCode()));
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

    private Map<String, Object> remove(Object taskId, Object actorIds, String operator) {
        return facade.flow("processTask/removeTaskActor",
                args("processTaskId", taskId, "actorIds", actorIds, "operator", operator));
    }

    private void assertOk(Map<String, Object> resp) {
        assertEquals("应成功: " + resp, Integer.valueOf(0), resp.get("code"));
    }

    // ═══ 语义 1「只摘不加」＋ 正向核心：摘掉点名的人，其余参与人一动不动 ═══

    @Test
    public void removesOnlyTheNamedActorAndKeepsTheRest() {
        Long instanceId = startInstance();
        Long taskId = task(instanceId, "apply").getTaskId();
        addActors(taskId, "9001", "9002");
        assertEquals(Arrays.asList("zhangsan", "9001", "9002"), actors(taskId));

        Map<String, Object> resp = remove(taskId, Arrays.asList("9001"), "flow.admin");

        assertOk(resp);
        assertEquals("只删点名的 9001，其余参与人原样保留（含顺序）",
                Arrays.asList("zhangsan", "9002"), actors(taskId));
        assertNull("data 出 null（spec 同节：前端消费面不读 data）", resp.get("data"));
    }

    /** 多支一起摘（集合语义，不是"一次只能摘一个人"）。 */
    @Test
    public void removesSeveralActorsInOneCall() {
        Long instanceId = startInstance();
        Long taskId = task(instanceId, "apply").getTaskId();
        addActors(taskId, "9001", "9002", "9003");

        assertOk(remove(taskId, Arrays.asList("9001", "9002"), "flow.admin"));

        assertEquals(Arrays.asList("zhangsan", "9003"), actors(taskId));
    }

    /** 逗号串腿与数组腿同判据（§2.11 第 1 行"两形一把尺子"，摘人腿不得另抄一份）。 */
    @Test
    public void commaStringShapeRemovesTheSamePeople() {
        Long instanceId = startInstance();
        Long taskId = task(instanceId, "apply").getTaskId();
        addActors(taskId, "9001", "9002");

        assertOk(remove(taskId, "9001, 9002 ", "flow.admin"));

        assertEquals("逗号串带空格照样命中", Arrays.asList("zhangsan"), actors(taskId));
    }

    // ═══ 语义 3「归属判据同 transfer」：只能摘自己那一票，auto/admin 例外 ═══

    /** 本人摘自己的那一票：无需特权。 */
    @Test
    public void selfRemovalNeedsNoPrivilege() {
        Long instanceId = startInstance();
        Long taskId = task(instanceId, "apply").getTaskId();
        addActors(taskId, "9001");

        assertOk(remove(taskId, Arrays.asList("9001"), "9001"));

        assertEquals(Arrays.asList("zhangsan"), actors(taskId));
    }

    /** 借道摘他人必须拦下（transfer 能"摘 A 加 B"是因为 A＝操作人本人，本 action 同理）。 */
    @Test
    public void removingSomeoneElseWithoutPrivilegeIsRejected() {
        Long instanceId = startInstance();
        Long taskId = task(instanceId, "apply").getTaskId();
        addActors(taskId, "9001");

        Map<String, Object> resp = remove(taskId, Arrays.asList("9001"), "zhangsan");

        assertEquals("无权限摘除该任务参与人", resp.get("msg"));
        assertEquals("报错后一条都不许删", Arrays.asList("zhangsan", "9001"), actors(taskId));
    }

    /** {@code flow.auto} 与 {@code flow.admin} 同档放行（isPrivilegedOperator 既有口径）。 */
    @Test
    public void autoSystemOperatorIsAlsoPrivileged() {
        Long instanceId = startInstance();
        Long taskId = task(instanceId, "apply").getTaskId();
        addActors(taskId, "9001");

        assertOk(remove(taskId, Arrays.asList("9001"), "flow.auto"));

        assertEquals(Arrays.asList("zhangsan"), actors(taskId));
    }

    // ═══ 语义 5「不得摘空」：判据是集合差，不是入参条数 ═══

    @Test
    public void neverEmptiesTheTask() {
        Long instanceId = startInstance();
        Long taskId = task(instanceId, "apply").getTaskId();
        assertEquals(Arrays.asList("zhangsan"), actors(taskId));

        Map<String, Object> resp = remove(taskId, Arrays.asList("zhangsan"), "zhangsan");

        assertEquals("摘空会造出无人可办又无法重派的死单", "至少需保留一名参与人", resp.get("msg"));
        assertEquals("人还在", Arrays.asList("zhangsan"), actors(taskId));
    }

    /**
     * 绕过档：{@code actorIds} 里混进非参与者 id，"入参条数 &lt; 参与人数"这种判据会放过去，
     * 集合差判据必须照样拦下（spec 语义 5 的第二句）。
     */
    @Test
    public void mixedNonParticipantIdCannotBypassTheKeepOneFloor() {
        Long instanceId = startInstance();
        Long taskId = task(instanceId, "apply").getTaskId();
        addActors(taskId, "9001");

        Map<String, Object> resp = remove(taskId, Arrays.asList("zhangsan", "9001", "ghost"), "zhangsan");

        assertEquals("至少需保留一名参与人", resp.get("msg"));
        assertEquals(Arrays.asList("zhangsan", "9001"), actors(taskId));
    }

    // ═══ 语义 4「只作用于进行中任务」：历史参与人行是审批链的取证依据 ═══

    @Test
    public void finishedTaskIsProtected() {
        Long instanceId = startInstance();
        Long taskId = task(instanceId, "apply").getTaskId();
        finishApply(instanceId);                       // apply 转 FINISHED，approval 起新任务

        Map<String, Object> resp = remove(taskId, Arrays.asList("zhangsan"), "flow.admin");

        assertEquals("历史参与人行是审批链的取证依据，非 DOING 一律拦下",
                "任务非进行中，不可摘除参与人", resp.get("msg"));
        assertEquals("已办结任务的参与人行不得被改写历史", Arrays.asList("zhangsan"), actors(taskId));
    }

    // ═══ 语义 2「不留痕、不 fire 事件」═══

    @Test
    public void leavesNoTraceAndFiresNoEvent() {
        Long instanceId = startInstance();
        Long taskId = task(instanceId, "apply").getTaskId();
        addActors(taskId, "9001");
        // 任务行留痕列的"改前读数"：建单路径本来就会写 update_user（不是摘人写的），
        // 判据只能是"摘人这一步没动它"，不能假定它是 null。
        String updateUserBefore = repo.findTaskById(taskId).getUpdateUser();
        Object updateTimeBefore = repo.findTaskById(taskId).getUpdateTime();
        events.clear();

        assertOk(remove(taskId, Arrays.asList("9001"), "flow.admin"));

        assertTrue("摘人不在 132 定稿事件集里，一律不 fire（码 7 的语义是「参与者被替换」）: " + names(),
                events.isEmpty());
        ProcessTask after = repo.findTaskById(taskId);
        FlowData vars = after.getVariables();
        assertNull("不写 tf_transferHistory", vars == null ? null : vars.get(FlowConst.TRANSFER_HISTORY));
        assertNull("不写 tf_transferTo", vars == null ? null : vars.get(FlowConst.TRANSFER_TO));
        assertNull("不置 submitType", vars == null ? null : vars.get(FlowConst.SUBMIT_TYPE));
        assertEquals("不覆写任务留痕列 update_user", updateUserBefore, after.getUpdateUser());
        assertEquals("不覆写任务留痕列 update_time", updateTimeBefore, after.getUpdateTime());
    }

    // ═══ 语义 6「幂等」：非参与者静默忽略，重放第二次仍成功 ═══

    @Test
    public void removingANonParticipantIsIdempotent() {
        Long instanceId = startInstance();
        Long taskId = task(instanceId, "apply").getTaskId();
        addActors(taskId, "9001");

        assertOk(remove(taskId, Arrays.asList("9001"), "flow.admin"));
        assertEquals(Arrays.asList("zhangsan"), actors(taskId));

        assertOk("同一次摘人重放第二次应得成功信封（前端双点/集成层重放）",
                remove(taskId, Arrays.asList("9001"), "flow.admin"));
        assertEquals(Arrays.asList("zhangsan"), actors(taskId));
    }

    // ═══ 必填档与守卫次序（spec 同节钉死，八栈不接受自行排序）═══

    /**
     * 三档逐字文案：operator 缺省/纯空白 ⇒ {@code operator 必填}（严禁回落 user1）；
     * 主键缺失或 {@code actorIds} 丢完为空 ⇒ 与 {@code surrogate} 同一文案
     * {@code processTaskId/actorIds 缺失}；任务不存在 ⇒ {@code 任务不存在}。
     */
    @Test
    public void missingArmsReuseTheExistingErrorEnvelope() {
        Long instanceId = startInstance();
        Long taskId = task(instanceId, "apply").getTaskId();
        // 取证快照必须是副本：内存仓 findTaskActors 返回内部活列表，拿引用比引用会恒真
        List<String> before = new ArrayList<>(actors(taskId));

        assertEquals("缺省 operator ⇒ 必填档（严禁回落 user1）",
                "operator 必填", remove(taskId, Arrays.asList("zhangsan"), null).get("msg"));
        assertEquals("纯空白 operator 也不给过",
                "operator 必填", remove(taskId, Arrays.asList("zhangsan"), "   ").get("msg"));
        assertEquals("主键空串 ⇒ 兄弟 action 同文案",
                "processTaskId/actorIds 缺失", remove("", Arrays.asList("9001"), "flow.admin").get("msg"));
        assertEquals("actorIds 丢完为空 ⇒ 兄弟 action 同文案",
                "processTaskId/actorIds 缺失",
                remove(taskId, Arrays.asList("", "  ", null), "flow.admin").get("msg"));
        assertEquals("任务不存在",
                "任务不存在", remove(424242L, Arrays.asList("9001"), "flow.admin").get("msg"));

        assertEquals("五个报错档一条都不许删", before, actors(taskId));
    }

    /**
     * 守卫次序（spec 同节末尾那段）：{@code operator 必填} 排在缺参数之前——
     * 否则"参数全缺"会先报主键缺失，把鉴权缺口藏进参数报错里；
     * 权限档排在 DOING 档之前。
     */
    @Test
    public void guardOrderIsFixedAcrossStacks() {
        Long instanceId = startInstance();
        Long taskId = task(instanceId, "apply").getTaskId();

        assertEquals("operator 必填排在主键缺失档之前（否则鉴权缺口会被参数报错藏起来）",
                "operator 必填", remove("", Arrays.asList("9001"), null).get("msg"));

        finishApply(instanceId);   // apply 已非 DOING，operator 又不是参与者
        assertEquals("权限档先于非进行中档（否则外人可以靠「任务已完成」探到别人的任务状态）",
                "无权限摘除该任务参与人",
                remove(taskId, Arrays.asList("zhangsan"), "outsider").get("msg"));
    }

    // ═══ 门禁新格：带空格入参可删 ∧ 空值不误删 actor_id='' 脏行 ═══

    /**
     * {@code " 9001 "} 必须命中库里的人（硬要求②"落库与比较一律取 trim 后的值"）；
     * 同时喂进 DELETE 的实参永不能含空串/{@code null}——历史 {@code actor_id=''} 脏行
     * 是 {@code DELETE ... actor_id IN (?)} 的受害者，判据打在实参与脏行存活两处。
     */
    @Test
    public void whitespacePaddedIdsAreRemovedAndDirtyRowsSurvive() {
        Long instanceId = startInstance();
        Long taskId = task(instanceId, "apply").getTaskId();
        addActors(taskId, "9001", "9002");
        repo.seedDirtyRow(taskId, "");                 // 复刻历史脏行（内存仓写侧归一后建不出来）
        repo.seedDirtyRow(taskId, "   ");              // 纯空白那一支也算脏行

        assertOk(remove(taskId, Arrays.asList(" 9001 ", "", null, "   ", "9002"), "flow.admin"));

        assertEquals("带空格的入参删得掉真人，其余参与人不动",
                Arrays.asList("zhangsan"), new ArrayList<>(repo.findRealActors(taskId)));
        assertEquals("空串/纯空白绝不能喂进 DELETE ⇒ 历史脏行必须原样还在",
                Arrays.asList("", "   "), repo.dirtyRemaining(taskId));
        for (List<String> call : repo.removeCalls) {
            for (String actor : call) {
                assertTrue("喂给 DELETE 的实参不得含空串/纯空白: " + call,
                        actor != null && !actor.trim().isEmpty());
            }
        }
    }

    // ═══ 语义 6「匹配取归一值、DELETE 取行上的原值」＋语义 5「脏行不算一个人」═══

    /**
     * 库里的行是修复前落下的未 trim 原值 {@code " 9101 "}，入参给 {@code "9101"}：
     * 判据必须把它当成同一个人<b>并真删掉</b>，且喂进 DELETE 的实参是<b>那一行的原值</b>。
     * 反面形状＝拿归一值去删：判成同一人却一条没删，门面报成功而被摘的人待办还在（假成功）。
     */
    @Test
    public void untrimmedHistoricalRowIsMatchedAndDeletedByRowValue() {
        Long instanceId = startInstance();
        Long taskId = task(instanceId, "apply").getTaskId();
        repo.seedDirtyRow(taskId, " 9101 ");          // 历史未 trim 行（写侧归一后正常路径造不出来）

        assertOk(remove(taskId, Arrays.asList("9101"), "flow.admin"));

        assertEquals("未 trim 的历史行被归一匹配命中并删除", Arrays.asList("zhangsan"),
                repo.findRealActors(taskId));
        assertEquals("脏行清单里那一行确实没了", Arrays.asList(), repo.dirtyRemaining(taskId));
        List<String> last = repo.removeCalls.get(repo.removeCalls.size() - 1);
        assertEquals("DELETE 的实参是行上的原值，不是归一后的值（否则删不掉）: " + last,
                Arrays.asList(" 9101 "), last);
    }

    /**
     * 「至少剩一人」的下限按<b>能办单的人数</b>算：库里只剩 {@code actor_id=''} 脏行时，
     * 摘走最后一个真人必须报错——脏行谁也办不了，拿它撑住下限等于让"摘空"伪装成成功。
     */
    @Test
    public void dirtyRowsDoNotPropUpTheKeepOneFloor() {
        Long instanceId = startInstance();
        Long taskId = task(instanceId, "apply").getTaskId();
        repo.seedDirtyRow(taskId, "");
        repo.seedDirtyRow(taskId, "   ");

        Map<String, Object> resp = remove(taskId, Arrays.asList("zhangsan"), "flow.admin");

        assertEquals("脏行不算一个人", "至少需保留一名参与人", resp.get("msg"));
        assertEquals("报错后真人那行还在", Arrays.asList("zhangsan"), repo.findRealActors(taskId));
    }

    @Test
    public void siblingActionsKeepTheirOwnSemantics() {
        Long instanceId = startInstance();
        Long taskId = task(instanceId, "apply").getTaskId();

        assertOk(facade.flow("processTask/surrogate",
                args("processTaskId", taskId, "actorIds", Arrays.asList("9101"))));
        assertEquals("surrogate 仍旧只加不摘", Arrays.asList("zhangsan", "9101"), actors(taskId));

        assertOk(remove(taskId, Arrays.asList("9101"), "flow.admin"));
        assertEquals("摘人不带加人", Arrays.asList("zhangsan"), actors(taskId));

        finishApply(instanceId);                     // 推进出 approval 节点（参与者＝leader）
        Long approvalTask = task(instanceId, "approval").getTaskId();
        assertOk(facade.flow("processTask/transfer", args(
                "processTaskId", approvalTask, "operator", "leader",
                "fromActor", "leader", "toActor", "boss")));
        assertEquals("transfer 换人语义不变", Arrays.asList("boss"), actors(approvalTask));
        assertEquals("transfer 仍写 submitType=7 留痕", Integer.valueOf(7),
                repo.findTaskById(approvalTask).getVariables().get(FlowConst.SUBMIT_TYPE));
    }

    // ── 辅助 ──

    private List<String> names() {
        List<String> out = new ArrayList<>();
        for (ProcessEvent e : events) out.add(String.valueOf(e.getEventType()));
        return out;
    }

    private void assertOk(String message, Map<String, Object> resp) {
        assertEquals(message + ": " + resp, Integer.valueOf(0), resp.get("code"));
    }

    /**
     * 复刻"库里已经存在的历史脏行"与 {@code DELETE ... actor_id IN (?)} 的逐字语义：
     * 脏行只能从外部塞进来（内存仓写侧归一后建不出空串行），{@link #findTaskActors} 把真人那一半
     * 与脏行并起来返回（与 JDBC 一条裸 {@code SELECT} 同形——脏行本来就会被读出来），
     * {@link #removeTaskActor} 记录每一次喂进 DELETE 的实参并按 {@code IN} 精确命中删除。
     *
     * <p>本用例的"至少剩一人"下限不靠脏行撑起（发起人 {@code zhangsan} 全程在场），脏行在这里
     * 只当被保护的对象用；"读侧要不要把空串参与者剔掉"是 §2.11 读侧的另一档判据，不在摘人这条里现挂。</p>
     */
    private static class DirtyRowSpyRepo extends MemoryProcessRepository {
        private final Map<Long, List<String>> dirty = new LinkedHashMap<>();
        private final List<List<String>> removeCalls = new ArrayList<>();

        void seedDirtyRow(Long taskId, String actorId) {
            dirty.computeIfAbsent(taskId, k -> new ArrayList<>()).add(actorId);
        }

        List<String> dirtyRemaining(Long taskId) {
            return new ArrayList<>(dirty.getOrDefault(taskId, new ArrayList<>()));
        }

        /** 只取"真人"那一半（脏行不进 {@link #findTaskActors} 的返回值会干扰其它用例？会——故这里分离取证）。 */
        List<String> findRealActors(Long taskId) {
            return super.findTaskActors(taskId);
        }

        @Override
        public List<String> findTaskActors(Long taskId) {
            List<String> out = new ArrayList<>(super.findTaskActors(taskId));
            out.addAll(dirty.getOrDefault(taskId, new ArrayList<>()));
            return out;
        }

        @Override
        public void removeTaskActor(Long taskId, List<String> actors) {
            removeCalls.add(new ArrayList<>(actors));
            List<String> rows = dirty.get(taskId);
            if (rows != null) rows.removeAll(actors);   // DELETE ... actor_id IN (...) 的逐字语义
            super.removeTaskActor(taskId, actors);
        }
    }
}
