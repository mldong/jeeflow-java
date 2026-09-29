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
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessInstanceStateEnum;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
import com.mldong.jeeflow.enums.ProcessTaskStateEnum;
import com.mldong.jeeflow.event.ProcessEvent;
import com.mldong.jeeflow.event.ProcessEventListener;
import com.mldong.jeeflow.event.ProcessPublisher;
import com.mldong.jeeflow.facade.JeeflowFacade;
import com.mldong.jeeflow.spi.IOrgUserProvider;
import com.mldong.jeeflow.spi.IUserProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 事件契约 recorder 回归（issues/127 ＋ issues/132 的事件代码腿 · Java 栈）。
 *
 * <p>唯一权威判据＝<b>规范 11 · 事件契约</b>（{@code jeeflow-doc/docs/spec/11-events.md}）
 * §11.3 码表 ＋ §11.8 验收（镜像层跨栈格 L2-30）：一条流从发起到办结<b>按顺序</b>收到
 * {@code [PROCESS_INSTANCE_START, PROCESS_TASK_START, TASK_COMPLETE, PROCESS_INSTANCE_END]}
 * ＋抄送支 {@code CC_CREATE}；逐码值可判定条目见规范 08 场景 28~36。</p>
 *
 * <p><b>顺序与缺支正是本案两个病灶</b>（§11.8「只断'出现过'不算过」），故每个用例都对
 * recorder 收到的<b>规范名全序列</b>做逐位相等断言，不做"包含"断言。{@code ProcessPublisher}
 * 按注册顺序逐个回调、fire 点全在引擎同步路径上 ⇒ recorder 收到顺序＝fire 顺序。</p>
 *
 * <p>上下文隔离：{@code ServiceContext} 是静态的，兄弟测试类会往里塞捕获监听器。本测试
 * 每个用例换一枚干净的 {@code SimpleContext}（唯一监听器＝本类 recorder），{@code @After}
 * 还原，顺序断言才不受串味。</p>
 *
 * <p>快照取证（§11.2 原则 3「只在落库之后 fire」）：recorder 在回调当下按载荷里的
 * {@code taskId}/{@code instanceId} 反查仓储，记下"监听器看到的样子"——参与人集合、cc 行数
 * 这类**独立存储**的读值才真能区分 fire 在写库前还是写库后（内存仓的实例/任务对象是同一引用，
 * state 快照只能证"行存在"，证不了写序；写序在 jdbc 栈由 T1 覆盖）。</p>
 */
public class EventContractRecorderTest {

    // ═══ 规范名（spec §11.3 权威名＝跨栈判据；码值只是本栈内部附带数值）═══
    private static final String START = "PROCESS_INSTANCE_START";       // 1
    private static final String INSTANCE_END = "PROCESS_INSTANCE_END";  // 2
    private static final String TASK_START = "PROCESS_TASK_START";      // 3
    private static final String CC_CREATE = "CC_CREATE";                // 4
    private static final String TASK_COMPLETE = "TASK_COMPLETE";        // 5
    private static final String TASK_REJECT = "TASK_REJECT";            // 6
    private static final String TASK_TRANSFER = "TASK_TRANSFER";        // 7
    private static final String TASK_WITHDRAW = "TASK_WITHDRAW";        // 8

    /**
     * 单任务流（start → approval(leader) → end）：让"发起→办理→办结"的名字序列正好等于
     * §11.8 钉的四元序列，不多一支任务、不掺多余的 {@code PROCESS_TASK_START}。
     */
    private static final String ONE_TASK_FLOW =
            "{\"name\":\"one-task\",\"displayName\":\"单任务抄送流程\",\"type\":\"approval\",\"nodes\":["
            + "{\"id\":\"start\",\"type\":\"snaker:start\",\"x\":100,\"y\":200,\"properties\":{},\"text\":{\"value\":\"开始\"}},"
            + "{\"id\":\"approval\",\"type\":\"snaker:task\",\"x\":300,\"y\":200,\"properties\":{\"form\":\"leave-form\","
            + "\"assignee\":\"leader\",\"taskType\":0,\"performType\":0},\"text\":{\"value\":\"审批\"}},"
            + "{\"id\":\"end\",\"type\":\"snaker:end\",\"x\":500,\"y\":200,\"properties\":{},\"text\":{\"value\":\"结束\"}}],"
            + "\"edges\":["
            + "{\"id\":\"e1\",\"sourceNodeId\":\"start\",\"targetNodeId\":\"approval\",\"properties\":{}},"
            + "{\"id\":\"e2\",\"sourceNodeId\":\"approval\",\"targetNodeId\":\"end\",\"properties\":{}}]}";

    private Context savedContext;
    private MemoryProcessRepository repo;
    private JeeflowEngine engine;
    private JeeflowFacade facade;

    /** recorder：按 fire 顺序逐条落档 ＋ 回调当下的仓储快照。 */
    private final List<Recorded> recorded = new CopyOnWriteArrayList<>();

    private static final class Recorded {
        final ProcessEvent event;
        final Integer taskState;
        final Integer instanceState;
        final List<String> actors;
        final List<String> ccActors;

        Recorded(ProcessEvent event, Integer taskState, Integer instanceState,
                 List<String> actors, List<String> ccActors) {
            this.event = event;
            this.taskState = taskState;
            this.instanceState = instanceState;
            this.actors = actors;
            this.ccActors = ccActors;
        }
    }

    @Before
    public void setUp() {
        savedContext = ServiceContext.getContext();
    }

    @After
    public void tearDown() {
        if (savedContext != null) {
            ServiceContext.setContext(savedContext);
        }
    }

    /** 干净上下文（repo/json/expr/user/org）；注：{@code new Configuration(ctx)} 会 setContext，SPI 必须在其后 put。 */
    private SimpleContext cleanContext() {
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
        // 唯一监听器：顺序敏感 ⇒ 上下文里不能再有别人的捕获项
        ctx.put("recorder", (ProcessEventListener) this::record);
        engine = new JeeflowEngineImpl();
        engine.configure(config);
        facade = new JeeflowFacade(engine, repo, new MemoryProcessExtRepository());
        return ctx;
    }

    private void freshStack() {
        repo = new MemoryProcessRepository();
        recorded.clear();
        cleanContext();
    }

    private void record(ProcessEvent event) {
        Long taskId = event.getData().getLong(ProcessPublisher.KEY_TASK_ID);
        Long instanceId = event.getData().getLong(ProcessPublisher.KEY_INSTANCE_ID);
        Integer taskState = null;
        List<String> actors = null;
        if (taskId != null) {
            ProcessTask t = repo.findTaskById(taskId);
            taskState = t == null ? null : t.getTaskState();
            // 拷贝一份：内存仓的参与者集合是原地增删的活引用，不拷贝就照不出"fire 当时"的形状
            // （把 fire 挪到 removeTaskActor/addTaskActor 之前也会被后续 mutation 抹平）
            List<String> live = repo.findTaskActors(taskId);
            actors = live == null ? null : new ArrayList<>(live);
        }
        Integer instanceState = null;
        List<String> ccActors = null;
        if (instanceId != null) {
            ProcessInstance inst = repo.findInstanceById(instanceId);
            instanceState = inst == null ? null : inst.getState();
            List<String> liveCc = repo.ccActorsForTest(instanceId);
            ccActors = liveCc == null ? null : new ArrayList<>(liveCc);
        }
        recorded.add(new Recorded(event, taskState, instanceState, actors, ccActors));
    }

    // ── 读档工具 ──

    private List<String> names() {
        List<String> names = new ArrayList<>();
        for (Recorded r : recorded) names.add(r.event.getEventType().name());
        return names;
    }

    private List<Recorded> allOf(String name) {
        List<Recorded> list = new ArrayList<>();
        for (Recorded r : recorded) {
            if (r.event.getEventType().name().equals(name)) list.add(r);
        }
        return list;
    }

    private Recorded byName(String name) {
        List<Recorded> list = allOf(name);
        assertTrue("应收到事件 " + name + "，实际序列=" + names(), !list.isEmpty());
        return list.get(0);
    }

    private int countOf(String name) {
        return allOf(name).size();
    }

    private Object payload(Recorded r, String key) {
        return r.event.getData().get(key);
    }

    private ProcessInstance.ProcessDefine addDefine(String json) {
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName("recorder-" + System.nanoTime());
        def.setDisplayName("事件契约录制流程");
        def.setType("approval");
        def.setState(1);
        def.setVersion(1);
        def.setContent(json.getBytes(StandardCharsets.UTF_8));
        repo.addDefine(def);
        return def;
    }

    private ProcessInstance.ProcessDefine addFlowFile(String filename) throws Exception {
        return addDefine(new String(Files.readAllBytes(Paths.get("src/test/resources/flows/" + filename)),
                StandardCharsets.UTF_8));
    }

    private Long doingTaskId(Long instanceId) {
        List<ProcessTask> doing = repo.findDoingTasks(instanceId, new String[]{});
        assertTrue("夹具前提：应有进行中任务", !doing.isEmpty());
        return doing.get(0).getTaskId();
    }

    private Map<String, Object> args(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        return Long.parseLong(v.toString());
    }

    private Long startedInstanceId() throws Exception {
        Map<String, Object> started = facade.flow("processInstance/startAndExecute", args(
                "processDefineId", addFlowFile("04-fork-join.json").getId(), "operator", "zhangsan"));
        assertEquals("发起应成功: " + started, Integer.valueOf(0), started.get("code"));
        return toLong(((Map<?, ?>) started.get("data")).get("processInstanceId"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // §11.8 / 08-compliance 场景 28~33：名字序列 ＋ 载荷键 ＋ 落库时机
    // ═══════════════════════════════════════════════════════════════════

    /**
     * <b>主用例</b>：发起 → 办理（带 {@code tf_ccActors}）→ 办结，规范名序列<b>逐位相等</b>。
     *
     * <p>期望 {@code [PROCESS_INSTANCE_START, PROCESS_TASK_START, TASK_COMPLETE,
     * PROCESS_INSTANCE_END, CC_CREATE, CC_CREATE]}：前四位正是 §11.8 钉的 {@code [1,3,5,2]}，
     * 抄送支 {@code [4]} 逐抄送人两支。CC 支排在办结之后是引擎既有写序
     * （{@code executeProcessTask} 里 {@code handleCcActors} 跟在 {@code node.execute} 之后），
     * 规范只钉"每支各自落在自己那次写库之后"，不钉跨事件相对次序。</p>
     */
    @Test
    public void startExecuteWithCcFinishSequenceIsOrderedAndCarriesPayloadKeys() {
        freshStack();
        ProcessInstance.ProcessDefine def = addDefine(ONE_TASK_FLOW);

        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "zhangsan", FlowData.create());
        Long instanceId = inst.getInstanceId();
        Long taskId = doingTaskId(instanceId);
        engine.executeProcessTask(taskId, "leader", FlowData.create()
                .set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode())
                .set(FlowConst.CC_ACTORS, "9001,9002"));

        assertEquals("规范名序列必须逐位相等（§11.8：只断出现过不算过）",
                Arrays.asList(START, TASK_START, TASK_COMPLETE, INSTANCE_END, CC_CREATE, CC_CREATE),
                names());

        // 码 1：sourceId＝instanceId，直传键 instanceId
        Recorded start = byName(START);
        assertEquals("码 1 sourceId 应为 instanceId", instanceId, start.event.getSourceId());
        assertEquals("码 1 直传载荷键 instanceId", instanceId,
                payload(start, ProcessPublisher.KEY_INSTANCE_ID));

        // 码 3：sourceId＝taskId，直传键 instanceId/taskId/actors，落库后可反查
        Recorded taskStart = byName(TASK_START);
        assertEquals("码 3 sourceId 应为 taskId", taskId, taskStart.event.getSourceId());
        assertEquals("码 3 直传载荷键 taskId", taskId, payload(taskStart, ProcessPublisher.KEY_TASK_ID));
        assertEquals("码 3 直传载荷键 instanceId", instanceId,
                payload(taskStart, ProcessPublisher.KEY_INSTANCE_ID));
        assertEquals("码 3 直传载荷键 actors＝该待办的参与者列表",
                Collections.singletonList("leader"), payload(taskStart, ProcessPublisher.KEY_ACTORS));
        assertEquals("码 3 须在任务行落库（分到 taskId）之后 fire：反查应见 10 进行中行",
                ProcessTaskStateEnum.DOING.getCode(), taskStart.taskState);

        // 码 5：sourceId＝taskId，直传键 instanceId/taskId/operator/submitType
        Recorded complete = byName(TASK_COMPLETE);
        assertEquals("码 5 sourceId 应为 taskId", taskId, complete.event.getSourceId());
        assertEquals("码 5 直传载荷键 operator", "leader", payload(complete, ProcessPublisher.KEY_OPERATOR));
        assertEquals("码 5 直传载荷键 submitType", ProcessSubmitTypeEnum.AGREE.getCode(),
                payload(complete, ProcessPublisher.KEY_SUBMIT_TYPE));
        assertEquals("码 5 直传载荷键 taskId", taskId, payload(complete, ProcessPublisher.KEY_TASK_ID));
        assertEquals("码 5 直传载荷键 instanceId", instanceId,
                payload(complete, ProcessPublisher.KEY_INSTANCE_ID));
        assertEquals("码 5 须在任务行 state 落库之后 fire（反查应已置 20 已完成）",
                ProcessTaskStateEnum.FINISHED.getCode(), complete.taskState);

        // 码 2：sourceId＝instanceId，直传键 instanceId/state
        Recorded end = byName(INSTANCE_END);
        assertEquals("码 2 sourceId 应为 instanceId", instanceId, end.event.getSourceId());
        assertEquals("码 2 直传载荷键 instanceId", instanceId, payload(end, ProcessPublisher.KEY_INSTANCE_ID));
        assertEquals("码 2 直传载荷键 state＝落定并写库的实例终态整数",
                ProcessInstanceStateEnum.FINISHED.getCode(), payload(end, ProcessPublisher.KEY_STATE));

        // 码 4：逐抄送人 fire 一次，ccActorId 直传，cc 行已落库
        List<Recorded> cc = allOf(CC_CREATE);
        assertEquals("两位抄送人应各 fire 一次 CC_CREATE", 2, cc.size());
        assertEquals("ccActorId 顺序应与抄送顺序一致", Arrays.asList("9001", "9002"),
                Arrays.asList(cc.get(0).event.getCcActorId(), cc.get(1).event.getCcActorId()));
        for (Recorded r : cc) {
            assertEquals("码 4 sourceId 应为 instanceId", instanceId, r.event.getSourceId());
            assertTrue("码 4 须在 cc 行落库之后 fire（反查应见被抄送人）",
                    r.ccActors != null && r.ccActors.contains(r.event.getCcActorId()));
        }
    }

    /**
     * §11.7 办理腿（issues/127 病灶）：办理带 {@code tf_ccActors} ⇒ 建 cc 行 ＋ 逐人 fire
     * {@code CC_CREATE}，与发起 {@code f_ccActors} 同一条腿；不抄送时一支不发。
     */
    @Test
    public void executeWithCcActorsPersistsCcRowsAndFiresPerActor() {
        freshStack();
        ProcessInstance.ProcessDefine def = addDefine(ONE_TASK_FLOW);
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "zhangsan", FlowData.create());
        Long instanceId = inst.getInstanceId();

        engine.executeProcessTask(doingTaskId(instanceId), "leader", FlowData.create()
                .set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));
        assertEquals("不抄送 ⇒ CC_CREATE 一支都不发（§11.4 反向档）", 0, countOf(CC_CREATE));

        recorded.clear();
        ProcessInstance inst2 = engine.startProcessInstanceById(def.getId(), "zhangsan", FlowData.create());
        engine.executeProcessTask(doingTaskId(inst2.getInstanceId()), "leader", FlowData.create()
                .set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode())
                .set(FlowConst.CC_ACTORS, Arrays.asList("7001", "7002", "7003")));

        assertEquals("办理带 3 位抄送人应逐人 fire CC_CREATE", 3, countOf(CC_CREATE));
        assertEquals("cc 行应与 fire 同粒度落库 3 行",
                Arrays.asList("7001", "7002", "7003"), repo.ccActorsForTest(inst2.getInstanceId()));
    }

    // ═══════════════════════════════════════════════════════════════════
    // 码 5 / 码 6 互斥（08-compliance 场景 30/31）
    // ═══════════════════════════════════════════════════════════════════

    /** 拒绝（submitType=2）⇒ 发 6 不发 5；实例终态 45 另发 2，两支不互相替代。 */
    @Test
    public void rejectFiresTaskRejectAndNeverTaskComplete() {
        freshStack();
        ProcessInstance.ProcessDefine def = addDefine(ONE_TASK_FLOW);
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "zhangsan", FlowData.create());
        Long instanceId = inst.getInstanceId();
        Long taskId = doingTaskId(instanceId);
        recorded.clear();

        Map<String, Object> executed = facade.flow("processTask/execute", args("processTaskId", taskId,
                "operator", "leader", "submitType", ProcessSubmitTypeEnum.REJECT.getCode()));
        assertEquals("拒绝应成功: " + executed, Integer.valueOf(0), executed.get("code"));

        assertEquals("名字序列：退回这一次只发 6，紧接实例终态 2",
                Arrays.asList(TASK_REJECT, INSTANCE_END), names());
        assertEquals("与 5 互斥：走 reject 的这一次严禁再发 TASK_COMPLETE（§11.3 码 6）",
                0, countOf(TASK_COMPLETE));

        Recorded reject = byName(TASK_REJECT);
        assertEquals("码 6 sourceId 应为 taskId", taskId, reject.event.getSourceId());
        assertEquals("码 6 靠载荷 submitType 区分动作（§11.2 原则 2）",
                ProcessSubmitTypeEnum.REJECT.getCode(), payload(reject, ProcessPublisher.KEY_SUBMIT_TYPE));
        assertEquals("码 6 载荷 operator", "leader", payload(reject, ProcessPublisher.KEY_OPERATOR));
        assertEquals("码 6 载荷 taskId", taskId, payload(reject, ProcessPublisher.KEY_TASK_ID));
        assertEquals("码 6 载荷 instanceId", instanceId, payload(reject, ProcessPublisher.KEY_INSTANCE_ID));

        assertEquals("拒绝的实例终态应为 45 已拒绝（码 2 载荷 state）",
                ProcessInstanceStateEnum.REJECT.getCode(),
                payload(byName(INSTANCE_END), ProcessPublisher.KEY_STATE));
    }

    /** 退回上一步（submitType=3）⇒ 该任务发 6；同实例里先前办结的任务只发过 5，两支按 taskId 互斥。 */
    @Test
    public void rollbackFiresTaskRejectForThatTaskOnly() throws Exception {
        freshStack();
        ProcessInstance.ProcessDefine def = addFlowFile("01-simple.json");
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "zhangsan", FlowData.create());
        Long instanceId = inst.getInstanceId();

        Long applyTaskId = doingTaskId(instanceId);   // apply（assignee=applicant → zhangsan）
        engine.executeProcessTask(applyTaskId, "zhangsan", FlowData.create()
                .set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.APPLY.getCode()));
        Long task1Id = doingTaskId(instanceId);        // task1（assignee=leader）
        engine.executeAndJumpTask(task1Id, "leader", FlowData.create()
                .set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.ROLLBACK.getCode()), null);

        assertEquals("名字序列：退回后回退复活行再发一支 3",
                Arrays.asList(START, TASK_START, TASK_COMPLETE, TASK_START, TASK_REJECT, TASK_START), names());
        assertEquals("办理 apply 应发一次 5", 1, countOf(TASK_COMPLETE));
        assertEquals("退回上一步应发一次 6", 1, countOf(TASK_REJECT));
        assertEquals("5 只属于被办结的 apply", applyTaskId, byName(TASK_COMPLETE).event.getSourceId());
        assertEquals("6 只属于被退回的 task1", task1Id, byName(TASK_REJECT).event.getSourceId());
        assertEquals("退回的 submitType 靠载荷分（§11.2 原则 2）",
                ProcessSubmitTypeEnum.ROLLBACK.getCode(),
                payload(byName(TASK_REJECT), ProcessPublisher.KEY_SUBMIT_TYPE));
        for (Recorded r : allOf(TASK_COMPLETE)) {
            assertTrue("同一任务严禁既进 5 又进 6（互斥判据）",
                    !task1Id.equals(r.event.getSourceId()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 码 7 转办 / 码 8 撤回（08-compliance 场景 34）
    // ═══════════════════════════════════════════════════════════════════

    /** 转办：参与者替换<b>落库之后</b> fire 码 7，载荷 fromActor/toActor/operator；不新建任务行。 */
    @Test
    public void transferFiresTaskTransferAfterActorsReplaced() {
        freshStack();
        ProcessInstance.ProcessDefine def = addDefine(ONE_TASK_FLOW);
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "zhangsan", FlowData.create());
        Long instanceId = inst.getInstanceId();
        Long taskId = doingTaskId(instanceId);
        recorded.clear();

        Map<String, Object> resp = facade.flow("processTask/transfer", args("processTaskId", taskId,
                "operator", "leader", "fromActor", "leader", "toActor", "boss", "reason", "出差"));
        assertEquals("转办应成功: " + resp, Integer.valueOf(0), resp.get("code"));

        assertEquals("转办 fire 一次码 7", 1, countOf(TASK_TRANSFER));
        Recorded transfer = byName(TASK_TRANSFER);
        assertEquals("码 7 sourceId 应为 taskId（§11.3 表）", taskId, transfer.event.getSourceId());
        assertEquals("码 7 载荷 fromActor", "leader", payload(transfer, ProcessPublisher.KEY_FROM_ACTOR));
        assertEquals("码 7 载荷 toActor", "boss", payload(transfer, ProcessPublisher.KEY_TO_ACTOR));
        assertEquals("码 7 载荷 operator", "leader", payload(transfer, ProcessPublisher.KEY_OPERATOR));
        assertEquals("码 7 载荷 taskId", taskId, payload(transfer, ProcessPublisher.KEY_TASK_ID));
        assertEquals("码 7 载荷 instanceId", instanceId, payload(transfer, ProcessPublisher.KEY_INSTANCE_ID));
        assertEquals("码 7 须在参与者被替换并落库之后 fire：反查应只剩 boss",
                Collections.singletonList("boss"), transfer.actors);
        assertEquals("转办不新建任务 ⇒ 不得伴随码 3", 0, countOf(TASK_START));
        assertEquals("转办不办结 ⇒ 不得伴随码 5", 0, countOf(TASK_COMPLETE));
    }

    /** 撤回：实例 state=30 落库之后 fire，<b>每轮只 fire 一次</b>（并行两单也不逐任务）；被守卫拒掉的那轮不发。 */
    @Test
    public void withdrawFiresTaskWithdrawOncePerRound() throws Exception {
        freshStack();
        Long instanceId = startedInstanceId();
        assertEquals("夹具前提：fork 后应有两条并行进行中任务", 2,
                repo.findDoingTasks(instanceId, new String[]{}).size());
        recorded.clear();

        Map<String, Object> first = facade.flow("processInstance/withdraw",
                args("id", instanceId, "operator", "zhangsan"));
        assertEquals("发起人撤回应成功: " + first, Integer.valueOf(0), first.get("code"));
        assertEquals("并行两条任务撤回也只 fire 一次码 8（不逐任务）", 1, countOf(TASK_WITHDRAW));

        Recorded withdraw = byName(TASK_WITHDRAW);
        assertEquals("码 8 sourceId 应为 instanceId", instanceId, withdraw.event.getSourceId());
        assertEquals("码 8 载荷 instanceId", instanceId, payload(withdraw, ProcessPublisher.KEY_INSTANCE_ID));
        assertEquals("码 8 载荷 operator 应为真实撤回人", "zhangsan",
                payload(withdraw, ProcessPublisher.KEY_OPERATOR));
        assertEquals("码 8 须在实例 state 写 30 落库之后 fire",
                ProcessInstanceStateEnum.WITHDRAW.getCode(), withdraw.instanceState);
        int withdrawnRows = 0;
        for (ProcessTask t : repo.findHistoryTasks(instanceId)) {
            if (ProcessTaskStateEnum.WITHDRAW.getCode().equals(t.getTaskState())) withdrawnRows++;
        }
        assertEquals("两条任务行都该落 30，但事件只发一次", 2, withdrawnRows);

        recorded.clear();
        Map<String, Object> second = facade.flow("processInstance/withdraw",
                args("id", instanceId, "operator", "zhangsan"));
        assertEquals("已撤回(30) 二次撤回应被 issues/134 守卫拒掉: " + second,
                Integer.valueOf(99999999), second.get("code"));
        assertEquals("被拒的那轮严禁发码 8", 0, countOf(TASK_WITHDRAW));
    }

    // ═══════════════════════════════════════════════════════════════════
    // 码 4 手动腿（§11.2 原则 1 ＋ §11.6 java 段：本案唯一一处基准要向 go/py/node 学）
    // ═══════════════════════════════════════════════════════════════════

    /** 门面手动 {@code processInstance/createCCInstance} 也要逐人 fire CC_CREATE（java 此前静默）。 */
    @Test
    public void manualCreateCcInstanceFiresCcCreatePerActor() {
        freshStack();
        ProcessInstance.ProcessDefine def = addDefine(ONE_TASK_FLOW);
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "zhangsan", FlowData.create());
        Long instanceId = inst.getInstanceId();
        recorded.clear();

        Map<String, Object> resp = facade.flow("processInstance/createCCInstance", args(
                "processInstanceId", instanceId, "operator", "zhangsan",
                "actorIds", Arrays.asList("9101", "9102")));
        assertEquals("手动抄送应成功: " + resp, Integer.valueOf(0), resp.get("code"));

        assertEquals("手动支同样逐抄送人 fire（§11.2 原则 1：码值表达事实，不表达谁触发）",
                2, countOf(CC_CREATE));
        List<Recorded> cc = allOf(CC_CREATE);
        assertEquals("ccActorId 顺序与入参一致", Arrays.asList("9101", "9102"),
                Arrays.asList(cc.get(0).event.getCcActorId(), cc.get(1).event.getCcActorId()));
        for (Recorded r : cc) {
            assertEquals("码 4 sourceId 应为 instanceId", instanceId, r.event.getSourceId());
            assertTrue("码 4 须在 cc 行落库之后 fire",
                    r.ccActors != null && r.ccActors.contains(r.event.getCcActorId()));
        }
        assertEquals("cc 行应落 2 行", Arrays.asList("9101", "9102"), repo.ccActorsForTest(instanceId));

        recorded.clear();
        Map<String, Object> missing = facade.flow("processInstance/createCCInstance",
                args("processInstanceId", instanceId, "operator", "zhangsan"));
        assertEquals("actorIds 缺失应报错: " + missing, Integer.valueOf(99999999), missing.get("code"));
        assertEquals("没落库就严禁 fire", 0, countOf(CC_CREATE));
    }

    // ═══════════════════════════════════════════════════════════════════
    // §11.4 不发清单（08-compliance 场景 35）＋ §11.5 无监听器安全
    // ═══════════════════════════════════════════════════════════════════

    /** 定义生命周期（saveDefine/updateDefine/updateDefineState）不 fire 流程事件。 */
    @Test
    public void defineLifecycleWritesFireNothing() {
        freshStack();
        ProcessInstance.ProcessDefine def = addDefine(ONE_TASK_FLOW);
        recorded.clear();

        repo.updateDefineState(def.getId(), 0);
        def.setState(0);
        repo.updateDefine(def);
        ProcessInstance.ProcessDefine another = new ProcessInstance.ProcessDefine();
        another.setName("other-" + System.nanoTime());
        another.setState(1);
        repo.saveDefine(another);
        assertEquals("定义生命周期严禁 fire（§11.4 第 2 条）", Collections.emptyList(), names());
    }

    /** 零注册时各 fire 点必须安全返回，且主流程照旧落库（§11.5「无监听器」行）。 */
    @Test
    public void fireWithNoListenerIsSafe() {
        repo = new MemoryProcessRepository();
        recorded.clear();
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
        JeeflowEngine bare = new JeeflowEngineImpl();
        bare.configure(config);

        ProcessInstance.ProcessDefine def = addDefine(ONE_TASK_FLOW);
        ProcessInstance inst = bare.startProcessInstanceById(def.getId(), "zhangsan", FlowData.create());
        Long taskId = doingTaskId(inst.getInstanceId());
        bare.executeProcessTask(taskId, "leader", FlowData.create()
                .set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode())
                .set(FlowConst.CC_ACTORS, "9001"));

        assertEquals("零监听器 ⇒ recorder 一条都收不到", 0, recorded.size());
        assertEquals("零监听器不得影响主流程：实例照旧办结",
                ProcessInstanceStateEnum.FINISHED.getCode(),
                repo.findInstanceById(inst.getInstanceId()).getState());
        assertEquals("cc 行照旧落库", Collections.singletonList("9001"),
                repo.ccActorsForTest(inst.getInstanceId()));
    }
}
