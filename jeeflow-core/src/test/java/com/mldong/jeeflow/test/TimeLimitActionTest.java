package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
import com.mldong.jeeflow.enums.ProcessTaskStateEnum;
import com.mldong.jeeflow.spi.IExpressionEvaluator;
import com.mldong.jeeflow.spi.IInstanceLockRepository;
import com.mldong.jeeflow.spi.ITimeActionSink;
import com.mldong.jeeflow.spi.ITimeProvider;
import com.mldong.jeeflow.spi.IUserProvider;
import com.mldong.jeeflow.spi.TimeAction;
import com.mldong.jeeflow.util.FlowUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * 任务时限动作 T0（spec 04 · 任务时限动作；工作流时限方案 §8 的引擎侧判据）：
 * 建行（autoExecute/reminderTime）、旁路覆盖（主建单/串行会签补建）、不继承档（协办）、
 * 失效联动（办结/实例终态取消）、完成回调事实范围、可注入时钟、实例级排他锁调用面。
 */
public class TimeLimitActionTest {

    private JeeflowEngineImpl engine;
    private LockTrackingRepo repo;
    private FakeSink sink;
    private LocalDateTime now;

    static class FakeSink implements ITimeActionSink {
        final List<TimeAction> actions = new ArrayList<>();
        final List<Long> cancelledTasks = new ArrayList<>();
        final List<Long> cancelledInstances = new ArrayList<>();

        @Override public void insertTimeAction(TimeAction action) { actions.add(action); }
        @Override public void cancelTaskTimeActions(Long taskId) { cancelledTasks.add(taskId); }
        @Override public void cancelInstanceTimeActions(Long instanceId) { cancelledInstances.add(instanceId); }

        List<TimeAction> ofType(String type) {
            List<TimeAction> out = new ArrayList<>();
            for (TimeAction a : actions) if (type.equals(a.getType())) out.add(a);
            return out;
        }
    }

    static class LockTrackingRepo extends MemoryProcessRepository implements IInstanceLockRepository {
        final List<Long> locked = new ArrayList<>();
        @Override public void lockInstance(Long instanceId) { locked.add(instanceId); }
    }

    @Before
    public void setUp() {
        Configuration config = new Configuration();
        repo = new LockTrackingRepo();
        sink = new FakeSink();
        now = LocalDateTime.of(2026, 11, 19, 9, 0, 0);

        ServiceContext.put("repository", repo);
        ServiceContext.put("json", new TestJsonProvider());
        ServiceContext.put("expr", new TestExpressionEvaluator());
        ServiceContext.put("user", new IUserProvider() {
            @Override public UserInfo getUser(String userId) {
                UserInfo u = new UserInfo();
                u.setUserId(userId);
                u.setRealName("用户" + userId);
                return u;
            }
        });
        engine = new JeeflowEngineImpl();
        engine.configure(config);
        engine.setTimeActionSink(sink);
        FlowUtil.setTimeProvider(new ITimeProvider() {
            @Override public LocalDateTime now() { return now; }
        });
    }

    @After
    public void tearDown() {
        FlowUtil.setTimeProvider(null);
    }

    // ── 夹具 ──

    private static final String NODE_FMT =
            "{\"id\":\"%s\",\"type\":\"%s\",\"x\":0,\"y\":0,\"properties\":{%s},\"text\":{\"value\":\"%s\"}}";
    private static final String EDGE_FMT =
            "{\"id\":\"%s\",\"type\":\"snaker:transition\",\"sourceNodeId\":\"%s\",\"targetNodeId\":\"%s\",\"properties\":{}}";

    private static String node(String id, String type, String props, String text) {
        return String.format(NODE_FMT, id, type, props, text);
    }

    private static String edge(String id, String from, String to) {
        return String.format(EDGE_FMT, id, from, to);
    }

    /** start→review→end；review 节点 properties 由入参控制。 */
    private Long define(String reviewProps) {
        String json = "{\"name\":\"tl_" + System.nanoTime() + "\",\"displayName\":\"时限夹具\",\"type\":\"approval\",\"nodes\":["
                + node("start", "snaker:start", "", "开始") + ","
                + node("review", "snaker:task", "\"form\":\"f\",\"taskType\":0,\"performType\":0," + reviewProps, "审批") + ","
                + node("end", "snaker:end", "", "结束")
                + "],\"edges\":["
                + edge("e1", "start", "review") + ","
                + edge("e2", "review", "end")
                + "]}";
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName("tl_" + System.nanoTime());
        def.setDisplayName("时限夹具");
        def.setType("approval");
        def.setState(1);
        def.setVersion(1);
        def.setContent(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        repo.saveDefine(def);
        return def.getId();
    }

    private ProcessInstance start(Long defineId) {
        return engine.startProcessInstanceById(defineId, "u1", FlowData.create());
    }

    private ProcessTask doing(ProcessInstance inst, String nodeName) {
        for (ProcessTask t : inst.getTasks()) {
            if (nodeName.equals(t.getTaskName()) && t.isDoing()) return t;
        }
        return null;
    }

    private ProcessTask findTask(Long taskId) {
        return repo.findTaskById(taskId);
    }

    // ── 建行与时钟 ──

    @Test
    public void autoAndReminderOnCreate() {
        Long defId = define("\"assignee\":\"u1\",\"expireTime\":\"2h\",\"autoExecute\":\"Y\",\"reminderTime\":\"30m\"");
        ProcessInstance inst = start(defId);
        ProcessTask review = doing(inst, "review");
        assertNotNull(review);

        List<TimeAction> auto = sink.ofType(TimeAction.TYPE_AUTO_COMPLETE);
        List<TimeAction> remind = sink.ofType(TimeAction.TYPE_REMIND);
        assertEquals(1, auto.size());
        assertEquals(1, remind.size());
        assertEquals("wf:auto:" + review.getTaskId() + ":0", auto.get(0).getIdempotencyKey());
        assertEquals("wf:remind:" + review.getTaskId() + ":1", remind.get(0).getIdempotencyKey());
        assertEquals(1, remind.get(0).getOccurrenceNo());
        assertEquals(review.getTaskId(), auto.get(0).getTaskId());
        assertEquals(inst.getInstanceId(), auto.get(0).getInstanceId());
        // 注入时钟：createTime=09:00 ⇒ 提醒 09:30、到期 11:00
        assertEquals(now.plusMinutes(30).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                remind.get(0).getDueAt());
        assertEquals(now.plusHours(2).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                auto.get(0).getDueAt());
    }

    @Test
    public void notConfiguredCreatesNoAction() {
        Long defId = define("\"assignee\":\"u1\"");
        start(defId);
        assertTrue(sink.actions.isEmpty());
    }

    @Test
    public void autoExecuteOnlyExactY() {
        for (String v : new String[]{"N", "n", "y", "true"}) {
            sink.actions.clear();
            Long defId = define("\"assignee\":\"u1\",\"expireTime\":\"2h\",\"autoExecute\":\"" + v + "\"");
            start(defId);
            assertTrue("autoExecute=" + v + " 不得开启自动办理", sink.ofType(TimeAction.TYPE_AUTO_COMPLETE).isEmpty());
        }
    }

    @Test
    public void reminderTimeModes() {
        // 相对量锚定 createTime
        Long defId = define("\"assignee\":\"u1\",\"reminderTime\":\"30m\"");
        ProcessInstance inst = start(defId);
        assertEquals(now.plusMinutes(30).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                sink.ofType(TimeAction.TYPE_REMIND).get(0).getDueAt());

        // 严格 HH:mm：09:00 建单 ⇒ 当天 12:00
        sink.actions.clear();
        Long defId2 = define("\"assignee\":\"u1\",\"reminderTime\":\"12:00\"");
        start(defId2);
        LocalDateTime want = now.toLocalDate().atTime(12, 0);
        assertEquals(want.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                sink.ofType(TimeAction.TYPE_REMIND).get(0).getDueAt());

        // 已过创建时刻 ⇒ 次日
        sink.actions.clear();
        now = now.withHour(13);
        Long defId3 = define("\"assignee\":\"u1\",\"reminderTime\":\"12:00\"");
        start(defId3);
        LocalDateTime nextDay = now.toLocalDate().plusDays(1).atTime(12, 0);
        assertEquals(nextDay.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                sink.ofType(TimeAction.TYPE_REMIND).get(0).getDueAt());
    }

    @Test
    public void invalidReminderDoesNotBreakFlow() {
        Long defId = define("\"assignee\":\"u1\",\"reminderTime\":\"2.5h\"");
        ProcessInstance inst = start(defId); // 不抛错即通过
        assertTrue(sink.ofType(TimeAction.TYPE_REMIND).isEmpty());
        assertNotNull(doing(inst, "review"));
    }

    // ── 旁路覆盖与不继承档 ──

    @Test
    public void parallelCountersignEveryMemberHasAction() {
        Long defId = define("\"assignee\":\"u1,u2\",\"performType\":1,\"countersignType\":\"PARALLEL\",\"reminderTime\":\"1h\"");
        ProcessInstance inst = start(defId);
        List<ProcessTask> members = new ArrayList<>();
        for (ProcessTask t : inst.getTasks()) if ("review".equals(t.getTaskName())) members.add(t);
        assertEquals(2, members.size());
        List<TimeAction> reminds = sink.ofType(TimeAction.TYPE_REMIND);
        assertEquals(2, reminds.size());
        for (ProcessTask m : members) {
            boolean hit = false;
            for (TimeAction a : reminds) if (m.getTaskId().equals(a.getTaskId())) hit = true;
            assertTrue("成员任务 " + m.getTaskId() + " 缺提醒行", hit);
        }
    }

    @Test
    public void sequentialCountersignNextMemberGetsAction() {
        Long defId = define("\"assignee\":\"u1,u2\",\"performType\":1,\"countersignType\":\"SEQUENTIAL\","
                + "\"expireTime\":\"2h\",\"autoExecute\":\"Y\"");
        ProcessInstance inst = start(defId);
        ProcessTask first = doing(inst, "review");
        assertNotNull(first);
        assertTrue(hasAction(TimeAction.TYPE_AUTO_COMPLETE, first.getTaskId()));
        // 首位办结 ⇒ 推进出第二位（新 taskId），同样建动作行
        engine.executeProcessTask(first.getTaskId(), "u1", submit(ProcessSubmitTypeEnum.AGREE.getCode()));
        ProcessInstance after = repo.findInstanceById(inst.getInstanceId());
        ProcessTask second = null;
        for (ProcessTask t : after.getTasks()) {
            if ("review".equals(t.getTaskName()) && t.isDoing() && !t.getTaskId().equals(first.getTaskId())) second = t;
        }
        assertNotNull("串行会签应推进出下一位", second);
        assertTrue("推进出的下一位缺自动办理行（旁路覆盖）", hasAction(TimeAction.TYPE_AUTO_COMPLETE, second.getTaskId()));
        assertTrue("办结的首位任务应取消 pending 动作行", sink.cancelledTasks.contains(first.getTaskId()));
    }

    @Test
    public void aidantRowInheritsNoAction() {
        Long defId = define("\"assignee\":\"u1\",\"expireTime\":\"2h\",\"autoExecute\":\"Y\",\"reminderTime\":\"1h\"");
        ProcessInstance inst = start(defId);
        ProcessTask review = doing(inst, "review");
        int before = sink.actions.size();
        engine.aidantTask(review.getTaskId(), Collections.singletonList("aid1"), "", false, "u1");
        assertEquals("协办行不得继承时限动作", before, sink.actions.size());
    }

    // ── 完成回调与失效联动 ──

    @Test
    public void callbackOnCompleteOnly() {
        Long defId = define("\"assignee\":\"u1\",\"callback\":\"demoHandler\",\"reminderTime\":\"1h\"");
        ProcessInstance inst = start(defId);
        ProcessTask review = doing(inst, "review");
        engine.executeProcessTask(review.getTaskId(), "u1", submit(ProcessSubmitTypeEnum.AGREE.getCode()));
        List<TimeAction> cbs = sink.ofType(TimeAction.TYPE_CALLBACK);
        assertEquals(1, cbs.size());
        assertEquals("wf:callback:" + review.getTaskId() + ":0", cbs.get(0).getIdempotencyKey());
        assertTrue(sink.cancelledTasks.contains(review.getTaskId()));

        // 退回族不触发回调
        sink.actions.clear();
        Long defId2 = define("\"assignee\":\"u1\",\"callback\":\"demoHandler\"");
        ProcessInstance inst2 = start(defId2);
        ProcessTask review2 = doing(inst2, "review");
        engine.executeAndJumpToEnd(review2.getTaskId(), "u1", submit(ProcessSubmitTypeEnum.REJECT.getCode()));
        assertTrue("退回不得触发完成回调", sink.ofType(TimeAction.TYPE_CALLBACK).isEmpty());
    }

    @Test
    public void instanceTerminalCancelsInstanceActions() {
        Long defId = define("\"assignee\":\"u1\",\"reminderTime\":\"1h\"");
        ProcessInstance inst = start(defId);
        ProcessTask review = doing(inst, "review");
        engine.executeProcessTask(review.getTaskId(), "u1", submit(ProcessSubmitTypeEnum.AGREE.getCode()));
        assertTrue("实例终态应取消该实例 pending 动作行", sink.cancelledInstances.contains(inst.getInstanceId()));
    }

    // ── 实例级排他锁调用面 ──

    @Test
    public void lockInstanceCalledOnExecute() {
        Long defId = define("\"assignee\":\"u1\"");
        ProcessInstance inst = start(defId);
        ProcessTask review = doing(inst, "review");
        engine.executeProcessTask(review.getTaskId(), "u1", submit(ProcessSubmitTypeEnum.AGREE.getCode()));
        assertTrue("仓储实现 IInstanceLockRepository 时，办理路径必须取得实例级排他保护",
                repo.locked.contains(inst.getInstanceId()));
    }

    private boolean hasAction(String type, Long taskId) {
        for (TimeAction a : sink.ofType(type)) if (taskId.equals(a.getTaskId())) return true;
        return false;
    }

    private static FlowData submit(int submitType) {
        FlowData data = FlowData.create();
        data.put("submitType", submitType);
        return data;
    }
}
