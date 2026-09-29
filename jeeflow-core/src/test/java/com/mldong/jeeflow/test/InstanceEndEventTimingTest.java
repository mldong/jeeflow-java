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
import com.mldong.jeeflow.event.ProcessEvent;
import com.mldong.jeeflow.event.ProcessEventListener;
import com.mldong.jeeflow.event.ProcessPublisher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 码 2 {@code PROCESS_INSTANCE_END} 的<b>落库时机</b>回归（spec §11.2 原则 3／§11.3 码 2
 * 「实例 state 更新为 20/30/40/45/50/99 之一并<b>落库之后</b>」／08-compliance 场景 32）。
 *
 * <p>本案要还的债：{@code EndProcessHandler} 曾在 {@code execution.getProcessInstance()
 * .finish()/reject()} 之后<b>立刻</b> fire——那一刻只是内存聚合根改了 state，实例那一行要等
 * 引擎 {@code persistTasks} 的 {@code updateInstance} 才落库。监听器（站内信反查、待办角标、
 * persist 回写）在回调当下反查实例会读到旧 state，与 issues/121／126 两轮"回写序"同族。</p>
 *
 * <p><b>判据形状</b>：recorder 在<b>回调那一刻</b>用仓储反查实例，读到的 {@code state} 必须
 * 已经等于事件载荷里的 {@code state}（fire-before-persist 的旧形状在这条断言下必红：那一刻
 * 那一行还是 10）。</p>
 *
 * <p><b>为什么自带 {@link WriteOrderRepository} 而不是复用兄弟测试的内存仓</b>：
 * {@link MemoryProcessRepository#findInstanceById} 返回聚合根的<b>活引用</b>，内存里改完 state
 * 立刻可读 ⇒ 证不了写序（{@code EventContractRecorderTest} 的类注释即记着这一点，它只能拿
 * "参与者集合／cc 行数"这类独立存储的读值当证据）。真实 SQL 仓的语义是"SELECT 出的是落库那一行的
 * 值"，故本用例的探针仓把 {@code findInstanceById} 改成返回<b>最后一次写库时刻的快照副本</b>，
 * 只有 {@code saveInstance/updateInstance} 会推进它——这才是"用仓储反查"能照出写序的形状。</p>
 *
 * <p>上下文隔离同兄弟测试：{@code ServiceContext} 是静态的，每个用例换一枚干净的
 * {@code SimpleContext}（唯一监听器＝本类 recorder），{@code @After} 还原。</p>
 *
 * @author mldong
 */
public class InstanceEndEventTimingTest {

    private static final String START = "PROCESS_INSTANCE_START";
    private static final String INSTANCE_END = "PROCESS_INSTANCE_END";
    private static final String TASK_START = "PROCESS_TASK_START";
    private static final String TASK_COMPLETE = "TASK_COMPLETE";
    private static final String TASK_REJECT = "TASK_REJECT";

    /** 单任务流：start → approval(leader) → end（与 recorder 的 ONE_TASK_FLOW 同形）。 */
    private static final String ONE_TASK_FLOW =
            "{\"name\":\"one-task\",\"displayName\":\"终态时机流程\",\"type\":\"approval\",\"nodes\":["
            + "{\"id\":\"start\",\"type\":\"snaker:start\",\"x\":100,\"y\":200,\"properties\":{},\"text\":{\"value\":\"开始\"}},"
            + "{\"id\":\"approval\",\"type\":\"snaker:task\",\"x\":300,\"y\":200,\"properties\":{\"form\":\"leave-form\","
            + "\"assignee\":\"leader\",\"taskType\":0,\"performType\":0},\"text\":{\"value\":\"审批\"}},"
            + "{\"id\":\"end\",\"type\":\"snaker:end\",\"x\":500,\"y\":200,\"properties\":{},\"text\":{\"value\":\"结束\"}}],"
            + "\"edges\":["
            + "{\"id\":\"e1\",\"sourceNodeId\":\"start\",\"targetNodeId\":\"approval\",\"properties\":{}},"
            + "{\"id\":\"e2\",\"sourceNodeId\":\"approval\",\"targetNodeId\":\"end\",\"properties\":{}}]}";

    /** 无待办短流：start → end —— 覆盖发起路径（{@code startProcessInstanceById}）的 flush 收口。 */
    private static final String DIRECT_END_FLOW =
            "{\"name\":\"direct-end\",\"displayName\":\"发起即办结\",\"type\":\"approval\",\"nodes\":["
            + "{\"id\":\"start\",\"type\":\"snaker:start\",\"x\":100,\"y\":200,\"properties\":{},\"text\":{\"value\":\"开始\"}},"
            + "{\"id\":\"end\",\"type\":\"snaker:end\",\"x\":300,\"y\":200,\"properties\":{},\"text\":{\"value\":\"结束\"}}],"
            + "\"edges\":["
            + "{\"id\":\"e1\",\"sourceNodeId\":\"start\",\"targetNodeId\":\"end\",\"properties\":{}}]}";

    /** 父流程：start → approval(leader) → <b>subprocess</b> → end —— 子实例办结后级联走这一段。 */
    private static final String PARENT_FLOW_WITH_SUBPROCESS =
            "{\"name\":\"parent-sub\",\"displayName\":\"父流程(含子流程)\",\"type\":\"approval\",\"nodes\":["
            + "{\"id\":\"start\",\"type\":\"snaker:start\",\"x\":100,\"y\":200,\"properties\":{},\"text\":{\"value\":\"开始\"}},"
            + "{\"id\":\"approval\",\"type\":\"snaker:task\",\"x\":260,\"y\":200,\"properties\":{\"assignee\":\"leader\","
            + "\"taskType\":0,\"performType\":0},\"text\":{\"value\":\"父审批\"}},"
            + "{\"id\":\"subprocess\",\"type\":\"snaker:subProcess\",\"x\":420,\"y\":200,\"properties\":{},"
            + "\"text\":{\"value\":\"子流程\"}},"
            + "{\"id\":\"end\",\"type\":\"snaker:end\",\"x\":580,\"y\":200,\"properties\":{},\"text\":{\"value\":\"结束\"}}],"
            + "\"edges\":["
            + "{\"id\":\"e1\",\"sourceNodeId\":\"start\",\"targetNodeId\":\"approval\",\"properties\":{}},"
            + "{\"id\":\"e2\",\"sourceNodeId\":\"approval\",\"targetNodeId\":\"subprocess\",\"properties\":{}},"
            + "{\"id\":\"e3\",\"sourceNodeId\":\"subprocess\",\"targetNodeId\":\"end\",\"properties\":{}}]}";

    /** 子流程：start → childTask(leader) → end —— 它办结时把父实例一路推到 end。 */
    private static final String CHILD_FLOW =
            "{\"name\":\"child\",\"displayName\":\"子流程\",\"type\":\"approval\",\"nodes\":["
            + "{\"id\":\"start\",\"type\":\"snaker:start\",\"x\":100,\"y\":200,\"properties\":{},\"text\":{\"value\":\"开始\"}},"
            + "{\"id\":\"childTask\",\"type\":\"snaker:task\",\"x\":300,\"y\":200,\"properties\":{\"assignee\":\"leader\","
            + "\"taskType\":0,\"performType\":0},\"text\":{\"value\":\"子审批\"}},"
            + "{\"id\":\"end\",\"type\":\"snaker:end\",\"x\":500,\"y\":200,\"properties\":{},\"text\":{\"value\":\"结束\"}}],"
            + "\"edges\":["
            + "{\"id\":\"e1\",\"sourceNodeId\":\"start\",\"targetNodeId\":\"childTask\",\"properties\":{}},"
            + "{\"id\":\"e2\",\"sourceNodeId\":\"childTask\",\"targetNodeId\":\"end\",\"properties\":{}}]}";

    private Context savedContext;
    private WriteOrderRepository repo;
    private JeeflowEngine engine;

    /** recorder：按 fire 顺序逐条落档 ＋ <b>回调那一刻</b>的仓储读值。 */
    private final List<Recorded> recorded = new CopyOnWriteArrayList<>();

    private static final class Recorded {
        final ProcessEvent event;
        /** 回调当下 {@code findInstanceById(payload.instanceId)} 读到的 state —— 即"那一行此刻的值" */
        final Integer rowStateAtCallback;
        /** 回调当下该实例行已被写过几次（0 ⇒ 一次都没落库就播了） */
        final int writesAtCallback;

        Recorded(ProcessEvent event, Integer rowStateAtCallback, int writesAtCallback) {
            this.event = event;
            this.rowStateAtCallback = rowStateAtCallback;
            this.writesAtCallback = writesAtCallback;
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

    private void freshStack() {
        repo = new WriteOrderRepository();
        recorded.clear();
        SimpleContext ctx = new SimpleContext();
        Configuration config = new Configuration(ctx);
        ctx.put("repository", repo);
        ctx.put("json", new TestJsonProvider());
        ctx.put("expr", new TestExpressionEvaluator());
        ctx.put("user", new com.mldong.jeeflow.spi.IUserProvider() {
            @Override
            public UserInfo getUser(String userId) {
                UserInfo u = new UserInfo();
                u.setUserId(userId);
                u.setRealName("用户" + userId);
                return u;
            }
        });
        ctx.put("org", new com.mldong.jeeflow.spi.IOrgUserProvider() {
            @Override public List<String> findDeptLeaders(String deptId) { return null; }
            @Override public List<String> findDeptMainLeaders(String deptId) { return null; }
            @Override public List<String> findByRole(String roleCode) { return null; }
        });
        ctx.put("recorder", (ProcessEventListener) this::record);
        engine = new JeeflowEngineImpl();
        engine.configure(config);
    }

    private void record(ProcessEvent event) {
        Long instanceId = event.getData().getLong(ProcessPublisher.KEY_INSTANCE_ID);
        Integer rowState = null;
        int writes = 0;
        if (instanceId != null) {
            // 「用仓储反查实例」——探针仓这里给的是落库快照的副本（SELECT 语义），
            // 所以读到的是"这一刻那一行是什么"，不是"内存聚合根正在被改成什么"
            ProcessInstance row = repo.findInstanceById(instanceId);
            rowState = row == null ? null : row.getState();
            writes = repo.writeCount(instanceId);
        }
        recorded.add(new Recorded(event, rowState, writes));
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

    private Recorded firstOf(String name) {
        List<Recorded> list = allOf(name);
        assertTrue("应收到事件 " + name + "，实际序列=" + names(), !list.isEmpty());
        return list.get(0);
    }

    private Object payload(Recorded r, String key) {
        return r.event.getData().get(key);
    }

    /**
     * 时机判据（本类的全部要点）：回调那一刻反查实例那一行的 state，必须<b>已经等于</b>
     * 载荷里承诺的 state；且那一行至少被写过一次。fire-before-persist 时读到的还是上一档
     * （10 进行中），本断言必红。
     */
    private void assertEndEventFiresAfterRowWritten(Recorded end, Long expectedInstanceId, Integer expectState) {
        assertEquals("码 2 sourceId 应为 instanceId", expectedInstanceId, end.event.getSourceId());
        assertEquals("码 2 载荷 instanceId", expectedInstanceId,
                payload(end, ProcessPublisher.KEY_INSTANCE_ID));
        assertEquals("码 2 载荷 state 应为承诺的终态整数", expectState,
                payload(end, ProcessPublisher.KEY_STATE));
        assertTrue("码 2 回调时该实例行必须已被写过（" + expectedInstanceId + " 写库次数="
                        + end.writesAtCallback + "）——写在播之后 ⇒ 次数至少 1",
                end.writesAtCallback >= 1);
        assertEquals("§11.2 原则 3／场景 32：监听器回调那一刻反查实例，读到的 state 必须已等于载荷 state"
                        + "（旧形状 fire 在 updateInstance 之前 ⇒ 这一刻读到的还是 10，本断言必红）",
                payload(end, ProcessPublisher.KEY_STATE), end.rowStateAtCallback);
        assertEquals("反查值还必须是承诺的那个终态（不是恰好相等的旧值）", expectState, end.rowStateAtCallback);
    }

    private ProcessInstance.ProcessDefine addDefine(String json) {
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName("end-timing-" + System.nanoTime());
        def.setDisplayName("实例终态时机流程");
        def.setType("approval");
        def.setState(1);
        def.setVersion(1);
        def.setContent(json.getBytes(StandardCharsets.UTF_8));
        repo.addDefine(def);
        return def;
    }

    private Long doingTaskId(Long instanceId) {
        List<ProcessTask> doing = repo.findDoingTasks(instanceId, new String[]{});
        assertTrue("夹具前提：应有进行中任务", !doing.isEmpty());
        return doing.get(0).getTaskId();
    }

    // ═══════════════════════════════════════════════════════════════════
    // 码 2 时机：正常办理路径（办结 20 / 拒绝 45）
    // ═══════════════════════════════════════════════════════════════════

    /** 办结：码 2 排在实例行 {@code updateInstance(20)} 之后，且名字序列不被这次改动打散。 */
    @Test
    public void finishEndEventFiresAfterInstanceRowIsWritten() {
        freshStack();
        ProcessInstance.ProcessDefine def = addDefine(ONE_TASK_FLOW);
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "zhangsan", FlowData.create());
        Long instanceId = inst.getInstanceId();
        Long taskId = doingTaskId(instanceId);
        recorded.clear();

        engine.executeProcessTask(taskId, "leader", FlowData.create()
                .set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));

        assertEquals("规范名序列：任务办结 → 实例终态（本次只挪码 2，不新增不丢支）",
                Arrays.asList(TASK_COMPLETE, INSTANCE_END), names());
        assertEquals("实例行落库快照应为 20 已完成", ProcessInstanceStateEnum.FINISHED.getCode(),
                repo.rowState(instanceId));
        assertEndEventFiresAfterRowWritten(firstOf(INSTANCE_END), instanceId,
                ProcessInstanceStateEnum.FINISHED.getCode());
    }

    /** 拒绝：同一支码 2（规范名不拆，§11.6），载荷 45 ＋ 回调时那一行也已是 45。 */
    @Test
    public void rejectEndEventFiresAfterRejectedRowIsWritten() {
        freshStack();
        ProcessInstance.ProcessDefine def = addDefine(ONE_TASK_FLOW);
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "zhangsan", FlowData.create());
        Long instanceId = inst.getInstanceId();
        Long taskId = doingTaskId(instanceId);
        recorded.clear();

        engine.executeProcessTask(taskId, "leader", FlowData.create()
                .set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.REJECT.getCode()));

        assertEquals("拒绝这一次只发 6，随后实例终态 2", Arrays.asList(TASK_REJECT, INSTANCE_END), names());
        assertEquals("实例行落库快照应为 45 已拒绝", ProcessInstanceStateEnum.REJECT.getCode(),
                repo.rowState(instanceId));
        assertEndEventFiresAfterRowWritten(firstOf(INSTANCE_END), instanceId,
                ProcessInstanceStateEnum.REJECT.getCode());
    }

    /** 发起即办结的短流：码 2 排在发起路径那次 {@code updateInstance} 之后（另一个 flush 点）。 */
    @Test
    public void startStraightToEndFiresEndAfterRowWritten() {
        freshStack();
        ProcessInstance.ProcessDefine def = addDefine(DIRECT_END_FLOW);

        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "zhangsan", FlowData.create());

        assertEquals("短流序列：发起 → 直接终态", Arrays.asList(START, INSTANCE_END), names());
        assertEquals(ProcessInstanceStateEnum.FINISHED.getCode(), repo.rowState(inst.getInstanceId()));
        assertEndEventFiresAfterRowWritten(firstOf(INSTANCE_END), inst.getInstanceId(),
                ProcessInstanceStateEnum.FINISHED.getCode());
    }

    // ═══════════════════════════════════════════════════════════════════
    // 码 2 时机：子流程父实例路径（前一会话点名"不能简单挪位"的那一支）
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 子实例办结 → 级联把父实例也推到 end ⇒ <b>两支</b>码 2，且父实例那一支同样排在
     * <b>父实例行落库之后</b>。
     *
     * <p>历史缺口有两处，本用例一并钉住：① 父实例的终态事件曾"顺路"在子流程级联里就地 fire，
     * 那一刻父实例的行根本没被写过（父实例不走子流程这次的 {@code updateInstance}，
     * 终态只改内存 ⇒ SQL 仓里父实例永远停在 10）；② 把 fire 简单挪到子流程的 updateInstance
     * 之后，会把父实例这一支整丢掉。现在 flush 对"不是本次 execution  own 的实例"先补写再播。</p>
     *
     * <p>夹具说明：{@code StartSubProcessHandler} 以 {@code defineId=null} 调引擎（源码里就标着
     * "简化处理"），正向"父 → 子"起不来 ⇒ 本用例按级联的反方向直造子实例
     * （{@code startProcessInstanceById(child, parentId, "subprocess")}，正是那个处理器本该做的事），
     * 要照的"子办结 → 父级联"这一段与生产同形。</p>
     */
    @Test
    public void subProcessParentEndEventFiresOnceAfterParentRowIsWritten() {
        freshStack();
        ProcessInstance.ProcessDefine parentDef = addDefine(PARENT_FLOW_WITH_SUBPROCESS);
        ProcessInstance.ProcessDefine childDef = addDefine(CHILD_FLOW);

        ProcessInstance parent = engine.startProcessInstanceById(parentDef.getId(), "zhangsan", FlowData.create());
        Long parentId = parent.getInstanceId();
        ProcessInstance child = engine.startProcessInstanceById(childDef.getId(), "zhangsan",
                FlowData.create(), parentId, "subprocess");
        Long childId = child.getInstanceId();
        Long childTaskId = doingTaskId(childId);
        recorded.clear();

        engine.executeProcessTask(childTaskId, "leader", FlowData.create()
                .set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));

        assertEquals("子办结这一支的序列：任务办结 → 子实例终态 → 父实例终态（父实例一支都不能少）",
                Arrays.asList(TASK_COMPLETE, INSTANCE_END, INSTANCE_END), names());

        List<Recorded> ends = allOf(INSTANCE_END);
        assertEquals("两支码 2", 2, ends.size());
        assertEquals("先子后父（登记顺序＝级联顺序，与修复前的 fire 顺序一致）",
                Arrays.asList(childId, parentId),
                Arrays.asList(ends.get(0).event.getSourceId(), ends.get(1).event.getSourceId()));

        assertEndEventFiresAfterRowWritten(ends.get(0), childId, ProcessInstanceStateEnum.FINISHED.getCode());
        assertEndEventFiresAfterRowWritten(ends.get(1), parentId, ProcessInstanceStateEnum.FINISHED.getCode());

        // 父实例的终态不再"只改内存"：行真的落到 20（此前 SQL 语义下永远停在 10）
        assertEquals("父实例行落库快照应为 20", ProcessInstanceStateEnum.FINISHED.getCode(),
                repo.rowState(parentId));
        ProcessInstance reloadedParent = repo.findInstanceById(parentId);
        assertNotNull(reloadedParent);
        assertEquals("重查父实例也应是 20", ProcessInstanceStateEnum.FINISHED.getCode(),
                reloadedParent.getState());
        assertEquals("父实例一支只 fire 一次，不重复播", 1, countOf(ends, parentId));
        assertEquals("子实例一支只 fire 一次", 1, countOf(ends, childId));
    }

    private int countOf(List<Recorded> ends, Long instanceId) {
        int n = 0;
        for (Recorded r : ends) {
            if (instanceId.equals(r.event.getSourceId())) n++;
        }
        return n;
    }

    // ═══════════════════════════════════════════════════════════════════
    // 写序探针仓
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 只认"写库"的仓储探针（本类判据的支点）：
     * <ul>
     *   <li>{@code saveInstance}/{@code updateInstance} ⇒ 记下该行的<b>落库快照</b>并累加写次数；</li>
     *   <li>{@code findInstanceById} ⇒ 返回<b>快照的副本</b>（真 SQL 仓的 SELECT 语义：
     *       读到的是最后一次写进去的值，改内存聚合根不会泄漏进读结果）。</li>
     * </ul>
     * 其余方法全部继承 {@link MemoryProcessRepository}（任务行／cc 行／分页等口径不变）。
     */
    private static final class WriteOrderRepository extends MemoryProcessRepository {

        private final Map<Long, ProcessInstance> rows = new ConcurrentHashMap<>();
        private final Map<Long, Integer> instanceWrites = new ConcurrentHashMap<>();

        @Override
        public void saveInstance(ProcessInstance instance) {
            super.saveInstance(instance);
            writeRow(instance);
        }

        @Override
        public void updateInstance(ProcessInstance instance) {
            super.updateInstance(instance);
            writeRow(instance);
        }

        private void writeRow(ProcessInstance instance) {
            if (instance == null || instance.getInstanceId() == null) {
                return;
            }
            rows.put(instance.getInstanceId(), snapshotOf(instance));
            Integer prev = instanceWrites.get(instance.getInstanceId());
            instanceWrites.put(instance.getInstanceId(), prev == null ? 1 : prev + 1);
        }

        @Override
        public ProcessInstance findInstanceById(Long instanceId) {
            ProcessInstance row = rows.get(instanceId);
            if (row == null) {
                return null;
            }
            ProcessInstance detached = snapshotOf(row);
            // 任务行按基类口径给最新的（与真库 JOIN 出来的行为一致），实例列只认落库快照
            ProcessInstance live = super.findInstanceById(instanceId);
            detached.setTasks(live == null || live.getTasks() == null
                    ? new ArrayList<ProcessTask>() : new ArrayList<>(live.getTasks()));
            return detached;
        }

        /** 直接读"库里那一行"的 state（用例侧的独立证据，不经聚合根内存值） */
        Integer rowState(Long instanceId) {
            ProcessInstance row = rows.get(instanceId);
            return row == null ? null : row.getState();
        }

        int writeCount(Long instanceId) {
            Integer n = instanceId == null ? null : instanceWrites.get(instanceId);
            return n == null ? 0 : n;
        }

        private static ProcessInstance snapshotOf(ProcessInstance src) {
            ProcessInstance c = new ProcessInstance();
            c.setInstanceId(src.getInstanceId());
            c.setParentId(src.getParentId());
            c.setDefineId(src.getDefineId());
            c.setState(src.getState());
            c.setParentNodeName(src.getParentNodeName());
            c.setBusinessNo(src.getBusinessNo());
            c.setOperator(src.getOperator());
            c.setExpireTime(src.getExpireTime());
            c.setVariables(src.getVariables() == null ? FlowData.create() : src.getVariables().copy());
            c.setCreateTime(src.getCreateTime());
            c.setCreateUser(src.getCreateUser());
            c.setUpdateTime(src.getUpdateTime());
            c.setUpdateUser(src.getUpdateUser());
            c.setTasks(src.getTasks() == null
                    ? Collections.<ProcessTask>emptyList() : new ArrayList<>(src.getTasks()));
            return c;
        }
    }
}
