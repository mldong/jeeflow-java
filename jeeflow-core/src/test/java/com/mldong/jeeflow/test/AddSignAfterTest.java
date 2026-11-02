package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.JeeflowException;
import com.mldong.jeeflow.core.AddSignSupport;
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
import com.mldong.jeeflow.parser.ModelParser;
import com.mldong.jeeflow.spi.IUserProvider;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * 「后加签（可重复）」验收：链式续办、多选等同圈兄弟、四类写入口报错且零写入、
 * 三条解析期硬校验、拒绝/退回发起人废弃未完成加签、回归（不开开关逐字不变）、并发只放行一次。
 *
 * <p>夹具走内联 JSON（{@link #registerFlowJson}），<b>不碰共享 flows 目录</b>——
 * 共享流程 JSON 的第八仓镜像与漂移门禁是另一条腿（任务 #4），在这里加文件会把它提前拖进十仓同步。</p>
 */
public class AddSignAfterTest {

    private JeeflowEngine engine;
    private MemoryProcessRepository repo;
    private TestJsonProvider json;
    private static int seq;

    private static final int AGREE = ProcessSubmitTypeEnum.AGREE.getCode();
    private static final int REJECT = ProcessSubmitTypeEnum.REJECT.getCode();
    private static final int APPLY = ProcessSubmitTypeEnum.APPLY.getCode();
    private static final int RETURN = ProcessSubmitTypeEnum.ROLLBACK_TO_OPERATOR.getCode();

    @Before
    public void setUp() {
        Configuration config = new Configuration();
        repo = new MemoryProcessRepository();
        json = new TestJsonProvider();
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
        engine = new JeeflowEngineImpl();
        engine.configure(config);
    }

    // ═══ 夹具 ═══

    private static String node(String id, String type, String props, String text) {
        return "{\"id\":\"" + id + "\",\"type\":\"" + type + "\",\"x\":0,\"y\":0,"
                + "\"properties\":{" + props + "},\"text\":{\"value\":\"" + text + "\"}}";
    }

    private static String edge(String id, String from, String to) {
        return "{\"id\":\"" + id + "\",\"type\":\"snaker:transition\",\"sourceNodeId\":\"" + from
                + "\",\"targetNodeId\":\"" + to + "\",\"properties\":{}}";
    }

    /** start → apply → review → final → end；两参数分别控制申请节点与审批节点的 properties。 */
    private String chain(String applyProps, String reviewProps) {
        String a = applyProps.isEmpty() ? "\"assignee\":\"applicant\"" : applyProps;
        String r = reviewProps.isEmpty() ? "" : "," + reviewProps;
        return "{\"name\":\"addsign-" + (++seq) + "\",\"displayName\":\"后加签夹具\",\"type\":\"approval\","
                + "\"nodes\":["
                + node("start", "snaker:start", "", "开始") + ","
                + node("apply", "snaker:task", "\"form\":\"apply-form\",\"taskType\":0,\"performType\":0," + a, "发起申请") + ","
                + node("review", "snaker:task", "\"form\":\"form-r\",\"assignee\":\"userA\",\"taskType\":0,\"performType\":0" + r, "审批") + ","
                + node("final", "snaker:task", "\"form\":\"form-f\",\"assignee\":\"userF\",\"taskType\":0,\"performType\":0", "终审") + ","
                + node("end", "snaker:end", "", "结束")
                + "],\"edges\":["
                + edge("e1", "start", "apply") + ","
                + edge("e2", "apply", "review") + ","
                + edge("e3", "review", "final") + ","
                + edge("e4", "final", "end")
                + "]}";
    }

    private static final String ADD_SIGN_ON = "\"allowAddSign\":true";

    private ProcessInstance.ProcessDefine registerFlowJson(String jsonStr) throws Exception {
        byte[] bytes = jsonStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        com.mldong.jeeflow.model.logicflow.LfModel lfModel =
                json.getMapper().readValue(jsonStr, com.mldong.jeeflow.model.logicflow.LfModel.class);
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName(lfModel.getName());
        def.setDisplayName(lfModel.getDisplayName());
        def.setType(lfModel.getType());
        def.setState(1);
        def.setVersion(1);
        def.setContent(bytes);
        repo.addDefine(def);
        return def;
    }

    /** 发起并办结申请节点，返回停在 review 的实例；参数是审批节点的 properties 片段。 */
    private ProcessInstance startToReview(String reviewProps) throws Exception {
        ProcessInstance.ProcessDefine def = registerFlowJson(chain("\"assignee\":\"applicant\"", reviewProps));
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "applicant", FlowData.create());
        for (ProcessTask t : repo.findDoingTasks(inst.getInstanceId(), null)) {
            engine.executeProcessTask(t.getTaskId(), "applicant",
                    FlowData.create().set(FlowConst.SUBMIT_TYPE, APPLY));
        }
        return repo.findInstanceById(inst.getInstanceId());
    }

    private ProcessTask doing(Long instanceId, String name) {
        for (ProcessTask t : repo.findDoingTasks(instanceId, null)) {
            if (name.equals(t.getTaskName())) {
                return t;
            }
        }
        return null;
    }

    /** 进行中任务的 {@code actor_id} 列恒空是家族不变量（办结才写），测试取办理人要用参与者列表。 */
    private String firstActor(ProcessTask t) {
        List<String> ids = t.getActorIds();
        return ids == null || ids.isEmpty() ? null : ids.get(0);
    }

    private int doingCount(Long instanceId, String name) {
        int n = 0;
        for (ProcessTask t : repo.findDoingTasks(instanceId, null)) {
            if (name.equals(t.getTaskName())) {
                n++;
            }
        }
        return n;
    }

    private long rows(Long instanceId, String name) {
        return repo.findHistoryTasks(instanceId).stream().filter(t -> name.equals(t.getTaskName())).count();
    }

    private FlowData agree(String operators) {
        return FlowData.create().set(FlowConst.SUBMIT_TYPE, AGREE)
                .set(FlowConst.ADD_SIGN_OPERATORS, operators);
    }

    private FlowData plain(int submitType) {
        return FlowData.create().set(FlowConst.SUBMIT_TYPE, submitType);
    }

    // ═══ 正向 ═══

    /** 链式续办：A→B→C→不点人即流出；血缘串成链、轮次递增、账本落在已完成行。 */
    @Test
    public void test01_chainThenStop() throws Exception {
        ProcessInstance inst = startToReview(ADD_SIGN_ON);
        Long iid = inst.getInstanceId();
        ProcessTask a = doing(iid, "review");
        assertNotNull(a);

        engine.executeProcessTask(a.getTaskId(), "userA", agree("userB"));
        ProcessTask b = doing(iid, "review");
        assertNotNull("被加签人应在同一节点收到新待办", b);
        assertEquals("血缘 parent＝刚办结那条", a.getTaskId(), b.getParentTaskId());
        assertEquals("轮次 1", Integer.valueOf(1), b.getVariables().getInt(FlowConst.ADD_SIGN_ROUND + "_review"));
        assertNull("令牌不得流出本节点", doing(iid, "final"));
        assertEquals("本节点只应有 1 条待办", 1, doingCount(iid, "review"));

        List<Map<String, Object>> histA = AddSignSupport.historyOf(repo.findTaskById(a.getTaskId()));
        assertEquals(1, histA.size());
        Map<String, Object> e0 = histA.get(0);
        assertEquals("userA", e0.get("fromActor"));
        assertEquals(Arrays.asList("userB"), e0.get("toActors"));
        assertEquals(1, e0.get("round"));
        assertEquals("userA", e0.get("operator"));
        assertTrue("时间须是全生态格式串", String.valueOf(e0.get("time")).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));

        engine.executeProcessTask(b.getTaskId(), "userB", agree("userC"));
        ProcessTask c = doing(iid, "review");
        assertNotNull(c);
        assertEquals("轮次递增", Integer.valueOf(2), c.getVariables().getInt(FlowConst.ADD_SIGN_ROUND + "_review"));
        assertEquals(b.getTaskId(), c.getParentTaskId());

        // C 不再点人 → 停止条件成立，流出本节点
        engine.executeProcessTask(c.getTaskId(), "userC", plain(AGREE));
        assertNull("不点人后不得再有本节点待办", doing(iid, "review"));
        ProcessTask fin = doing(iid, "final");
        assertNotNull("应流转到下一审批节点", fin);
        assertEquals("review 共三行", 3, rows(iid, "review"));

        engine.executeProcessTask(fin.getTaskId(), "userF", plain(AGREE));
        assertEquals("实例办结", ProcessInstanceStateEnum.FINISHED.getCode(),
                repo.findInstanceById(iid).getState());
    }

    /** 数组两形同判据（spec 06 §2.11）：一次点两人 ⇒ 同圈建两行，最后一位办结且没点人才放行。 */
    @Test
    public void test02_multiSelectWaitsSiblings() throws Exception {
        ProcessInstance inst = startToReview(ADD_SIGN_ON);
        Long iid = inst.getInstanceId();
        ProcessTask a = doing(iid, "review");

        engine.executeProcessTask(a.getTaskId(), "userA",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, AGREE)
                        .set(FlowConst.ADD_SIGN_OPERATORS, Arrays.asList("userB", "userC")));
        assertEquals("同圈应建两行待办", 2, doingCount(iid, "review"));
        assertNull(doing(iid, "final"));

        ProcessTask b = doing(iid, "review");
        engine.executeProcessTask(b.getTaskId(), firstActor(b), plain(AGREE));
        assertNull("兄弟待办未完成时不得放行", doing(iid, "final"));
        assertEquals(1, doingCount(iid, "review"));

        ProcessTask c = doing(iid, "review");
        engine.executeProcessTask(c.getTaskId(), firstActor(c), plain(AGREE));
        ProcessTask fin = doing(iid, "final");
        assertNotNull("最后一位办结且没点人才放行", fin);
        assertEquals("放行只能发生一次", 1, doingCount(iid, "final"));
    }

    /** 加给已办过的人合法（A→B→A 乒乓），轮次继续计数。 */
    @Test
    public void test03_reAddPreviouslyHandledIsLegal() throws Exception {
        ProcessInstance inst = startToReview(ADD_SIGN_ON);
        Long iid = inst.getInstanceId();
        ProcessTask a = doing(iid, "review");
        engine.executeProcessTask(a.getTaskId(), "userA", agree("userB"));
        ProcessTask b = doing(iid, "review");
        engine.executeProcessTask(b.getTaskId(), "userB", agree("userA"));
        ProcessTask back = doing(iid, "review");
        assertTrue("加给已办过的人应合法", back.getActorIds().contains("userA"));
        assertEquals(Integer.valueOf(2), back.getVariables().getInt(FlowConst.ADD_SIGN_ROUND + "_review"));
    }

    // ═══ 负向：写入口报错且零写入 ═══

    /** 与 tf_nextNodeOperator 同传 ⇒ 报错，且任务行/实例零改动。 */
    @Test
    public void test04_mutexWithNextNodeOperatorRejected() throws Exception {
        ProcessInstance inst = startToReview(ADD_SIGN_ON);
        Long iid = inst.getInstanceId();
        ProcessTask a = doing(iid, "review");
        long before = rows(iid, "review");
        try {
            engine.executeProcessTask(a.getTaskId(), "userA",
                    agree("userB").set(FlowConst.NEXT_NODE_OPERATOR, "userF"));
            fail("同传两键必须报错");
        } catch (JeeflowException e) {
            assertEquals(AddSignSupport.MSG_MUTEX, e.getMessage());
        }
        assertTrue("报错时任务行不得被办结", repo.findTaskById(a.getTaskId()).isDoing());
        assertEquals("不得新增行", before, rows(iid, "review"));
        assertEquals(ProcessInstanceStateEnum.DOING.getCode(), repo.findInstanceById(iid).getState());
    }

    /** 节点未开开关却传键 ⇒ 报错（不得静默忽略），零写入。 */
    @Test
    public void test05_switchOffRejected() throws Exception {
        ProcessInstance inst = startToReview("");   // review 不开 allowAddSign
        Long iid = inst.getInstanceId();
        ProcessTask a = doing(iid, "review");
        try {
            engine.executeProcessTask(a.getTaskId(), "userA", agree("userB"));
            fail("开关没开却传键必须报错");
        } catch (JeeflowException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("未开启 allowAddSign"));
        }
        assertTrue(repo.findTaskById(a.getTaskId()).isDoing());
        assertNull(doing(iid, "final"));
    }

    /** 加给自己 ⇒ 报错（死循环入口），零写入。 */
    @Test
    public void test06_selfRejected() throws Exception {
        ProcessInstance inst = startToReview(ADD_SIGN_ON);
        Long iid = inst.getInstanceId();
        ProcessTask a = doing(iid, "review");
        try {
            engine.executeProcessTask(a.getTaskId(), "userA", agree("userA"));
            fail("加给自己必须报错");
        } catch (JeeflowException e) {
            assertEquals(AddSignSupport.MSG_SELF, e.getMessage());
        }
        assertTrue(repo.findTaskById(a.getTaskId()).isDoing());
        assertEquals(0, doingCount(iid, "review") - 1);
    }

    /** 超上限 ⇒ 报错不强制放行（放行＝伪造"审批完成"），零写入。 */
    @Test
    public void test07_maxRoundsRejected() throws Exception {
        ProcessInstance inst = startToReview(ADD_SIGN_ON + ",\"addSignMaxRounds\":1");
        Long iid = inst.getInstanceId();
        ProcessTask a = doing(iid, "review");
        engine.executeProcessTask(a.getTaskId(), "userA", agree("userB"));   // 第 1 轮，合法
        ProcessTask b = doing(iid, "review");
        try {
            engine.executeProcessTask(b.getTaskId(), "userB", agree("userC")); // 第 2 轮，越界
            fail("超过 addSignMaxRounds 必须报错");
        } catch (JeeflowException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("已达上限 1"));
        }
        assertTrue("越界那次不得办结任务行", repo.findTaskById(b.getTaskId()).isDoing());
        assertNull(doing(iid, "final"));
        // 但第 1 轮那位不点人可以正常流出
        engine.executeProcessTask(b.getTaskId(), "userB", plain(AGREE));
        assertNotNull(doing(iid, "final"));
    }

    // ═══ 解析期硬校验 ═══

    @Test
    public void test08_countersignNodeRejectedAtParse() {
        String bad = chain("\"assignee\":\"applicant\"",
                ADD_SIGN_ON + ",\"performType\":1,\"countersignType\":\"PARALLEL\"");
        assertParseFails(bad, "会签节点");
    }

    @Test
    public void test09_firstTaskNodeRejectedAtParse() {
        String bad = chain("\"assignee\":\"applicant\"," + ADD_SIGN_ON, ADD_SIGN_ON);
        assertParseFails(bad, "首任务节点");
    }

    @Test
    public void test10_invalidConfigValuesRejectedAtParse() {
        assertParseFails(chain("\"assignee\":\"applicant\"", "\"allowAddSign\":\"yes\""), "allowAddSign 配置非法");
        assertParseFails(chain("\"assignee\":\"applicant\"", ADD_SIGN_ON + ",\"addSignMaxRounds\":-1"), "不得为负");
        assertParseFails(chain("\"assignee\":\"applicant\"", ADD_SIGN_ON + ",\"addSignMaxRounds\":\"1.5\""), "必须是整数");
    }

    private void assertParseFails(String flowJson, String expectedFragment) {
        try {
            ModelParser.parse(flowJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            fail("应报解析期配置错误：" + expectedFragment);
        } catch (JeeflowException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(expectedFragment));
        }
    }

    // ═══ 废弃未完成加签任务 ═══

    /** 不同意(2)：实例结束，本节点未完成的兄弟加签任务全部废弃，不留孤儿待办。 */
    @Test
    public void test11_rejectAbandonsSiblings() throws Exception {
        ProcessInstance inst = startToReview(ADD_SIGN_ON);
        Long iid = inst.getInstanceId();
        ProcessTask a = doing(iid, "review");
        engine.executeProcessTask(a.getTaskId(), "userA",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, AGREE)
                        .set(FlowConst.ADD_SIGN_OPERATORS, Arrays.asList("userB", "userC")));
        assertEquals(2, doingCount(iid, "review"));

        ProcessTask b = doing(iid, "review");
        engine.executeAndJumpToEnd(b.getTaskId(), firstActor(b), plain(REJECT));

        assertEquals("实例应已结束", Integer.valueOf(45), repo.findInstanceById(iid).getState());
        assertEquals("未完成的加签兄弟不得留待办", 0, doingCount(iid, "review"));
        long abandoned = repo.findHistoryTasks(iid).stream()
                .filter(t -> "review".equals(t.getTaskName()))
                .filter(t -> ProcessTaskStateEnum.ABANDON.getCode().equals(t.getTaskState()))
                .count();
        assertEquals("被废弃的那条应留废弃态", 1, abandoned);
    }

    /** 退回发起人(6)：申请待办生成，本节点未完成加签任务废弃。 */
    @Test
    public void test12_returnToApplicantAbandonsSiblings() throws Exception {
        ProcessInstance inst = startToReview(ADD_SIGN_ON);
        Long iid = inst.getInstanceId();
        ProcessTask a = doing(iid, "review");
        engine.executeProcessTask(a.getTaskId(), "userA",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, AGREE)
                        .set(FlowConst.ADD_SIGN_OPERATORS, Arrays.asList("userB", "userC")));
        ProcessTask b = doing(iid, "review");
        engine.executeAndJumpToFirstTaskNode(b.getTaskId(), firstActor(b), plain(RETURN));

        assertNotNull("应生成申请待办", doing(iid, "apply"));
        assertEquals("本节点未完成加签任务应被废弃", 0, doingCount(iid, "review"));
        assertNull(doing(iid, "final"));
    }

    // ═══ 回归与并发 ═══

    /** 不开开关的节点行为逐字不变：同意即流出，且不因新判据受影响。 */
    @Test
    public void test13_plainNodeUnchanged() throws Exception {
        ProcessInstance inst = startToReview("");
        Long iid = inst.getInstanceId();
        ProcessTask a = doing(iid, "review");
        engine.executeProcessTask(a.getTaskId(), "userA", plain(AGREE));
        assertNotNull(doing(iid, "final"));
        assertEquals(1, rows(iid, "review"));
        assertTrue(AddSignSupport.historyOf(repo.findTaskById(a.getTaskId())).isEmpty());
    }

    /** 两个同圈加签任务并发办结：只放行一次（后续节点恰好一条待办，不重复创建）。 */
    @Test
    public void test14_concurrentFinishReleasesOnce() throws Exception {
        ProcessInstance inst = startToReview(ADD_SIGN_ON);
        Long iid = inst.getInstanceId();
        ProcessTask a = doing(iid, "review");
        engine.executeProcessTask(a.getTaskId(), "userA",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, AGREE)
                        .set(FlowConst.ADD_SIGN_OPERATORS, Arrays.asList("userB", "userC")));
        final List<Long> ids = new java.util.ArrayList<>();
        final List<String> actors = new java.util.ArrayList<>();
        for (ProcessTask t : repo.findDoingTasks(iid, null)) {
            ids.add(t.getTaskId());
            actors.add(firstActor(t));
        }
        assertEquals(2, ids.size());

        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(2);
        final AtomicInteger errors = new AtomicInteger();
        for (int i = 0; i < ids.size(); i++) {
            final Long taskId = ids.get(i);
            final String actor = actors.get(i);
            Thread th = new Thread(() -> {
                try {
                    start.await();
                    engine.executeProcessTask(taskId, actor, plain(AGREE));
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
            th.start();
        }
        start.countDown();
        done.await();

        assertEquals("并发办结不应报错", 0, errors.get());
        assertEquals("后续节点只应有一条待办", 1, doingCount(iid, "final"));
        assertEquals(0, doingCount(iid, "review"));
    }

    /**
     * 根因守卫：控制类 {@code tf_*} 参数不得进实例变量（跨请求复活的病根），
     * 但任务行留痕必须完整——两者是同一枚判据的两面。
     */
    @Test
    public void test15_controlKeysNeverEnterInstanceVariables() throws Exception {
        ProcessInstance inst = startToReview(ADD_SIGN_ON);
        Long iid = inst.getInstanceId();
        ProcessTask a = doing(iid, "review");
        engine.executeProcessTask(a.getTaskId(), "userA",
                agree("userB").set(FlowConst.APPROVAL_COMMENT, "请 B 复核"));

        FlowData instanceVars = repo.findInstanceById(iid).getVariables();
        assertNull("tf_addSignOperators 不得进实例变量", instanceVars.get(FlowConst.ADD_SIGN_OPERATORS));
        assertNull("tf_approvalComment 不得进实例变量", instanceVars.get(FlowConst.APPROVAL_COMMENT));
        for (String key : instanceVars.keySet()) {
            assertFalse("实例变量里不应有任何 tf_ 键: " + key,
                    key.startsWith(FlowConst.TASK_FORM_DATA_PREFIX));
        }
        // 留痕仍在任务行上（修根因不得把审计信息一起改掉）
        FlowData rowVars = repo.findTaskById(a.getTaskId()).getVariables();
        assertEquals("请 B 复核", rowVars.get(FlowConst.APPROVAL_COMMENT));
        assertEquals("userB", String.valueOf(rowVars.get(FlowConst.ADD_SIGN_OPERATORS)));
        assertEquals(1, AddSignSupport.historyOf(repo.findTaskById(a.getTaskId())).size());
    }

    /**
     * 共享夹具 {@code flows/19-add-sign-after.json} 必须真被参考实现消费——
     * 七语言仓 flows/ 是它的入库副本，各语言移植后加签时读的就是这一份，
     * 没有生产者核对过的夹具会在传播轮里各语言各自"改一份能跑的"。
     */
    @Test
    public void test16_sharedFixtureChainRuns() throws Exception {
        byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                "src/test/resources/flows/19-add-sign-after.json"));
        ProcessInstance.ProcessDefine def =
                registerFlowJson(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "applicant", FlowData.create());
        Long iid = inst.getInstanceId();
        for (ProcessTask t : repo.findDoingTasks(iid, null)) {
            engine.executeProcessTask(t.getTaskId(), "applicant", plain(APPLY));
        }

        ProcessTask a = doing(iid, "review");
        assertNotNull("共享夹具的申请节点办结后应到审批节点", a);
        engine.executeProcessTask(a.getTaskId(), "userA", agree("userB,userC"));
        assertEquals("逗号串两形应建两行", 2, doingCount(iid, "review"));
        assertNull(doing(iid, "final"));

        // 先取快照再逐条办结：办结会改写仓储里的进行中集合，边遍历边办是并发修改
        final List<Long> siblingIds = new java.util.ArrayList<>();
        final List<String> siblingActors = new java.util.ArrayList<>();
        for (ProcessTask sib : repo.findDoingTasks(iid, null)) {
            siblingIds.add(sib.getTaskId());
            siblingActors.add(firstActor(sib));
        }
        for (int i = 0; i < siblingIds.size(); i++) {
            engine.executeProcessTask(siblingIds.get(i), siblingActors.get(i), plain(AGREE));
        }
        ProcessTask fin = doing(iid, "final");
        assertNotNull("同圈兄弟全部办结后应放行", fin);
        assertEquals(1, rows(iid, "final"));
        engine.executeProcessTask(fin.getTaskId(), "userF", plain(AGREE));
        assertEquals(ProcessInstanceStateEnum.FINISHED.getCode(), repo.findInstanceById(iid).getState());
    }
}
