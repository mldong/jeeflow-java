package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.JeeflowException;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.RecallSupport;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
import com.mldong.jeeflow.spi.IUserProvider;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * 「已办撤回（recall）」验收（issues/246）：复活三件套（原行留 FINISHED＋tf_recallHistory、
 * 下游 DOING 废弃、同节点新行名册照旧）、窗口两档（宽口径放行／下游已办结拦）、
 * 四条逐字文案、后加签行连带废弃、并行分支全废弃、解析期禁配、实例非 DOING。
 *
 * <p>夹具走内联 JSON（不碰共享 flows 目录，与 {@link AddSignAfterTest} 同款纪律）。</p>
 */
public class RecallTest {

    private JeeflowEngine engine;
    private MemoryProcessRepository repo;
    private TestJsonProvider json;
    private static int seq;

    private static final int AGREE = ProcessSubmitTypeEnum.AGREE.getCode();
    private static final int APPLY = ProcessSubmitTypeEnum.APPLY.getCode();

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
        return "{\"name\":\"recall-" + (++seq) + "\",\"displayName\":\"已办撤回夹具\",\"type\":\"approval\","
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

    /** 六节点链：start→apply→review→final→pay→end，供"下游已办结"窗口档。 */
    private String chainDeep(String reviewProps) {
        String r = reviewProps.isEmpty() ? "\"assignee\":\"userA\"" : "\"assignee\":\"userA\"," + reviewProps;
        return "{\"name\":\"recalldeep-" + (++seq) + "\",\"displayName\":\"已办撤回深链\",\"type\":\"approval\","
                + "\"nodes\":["
                + "{\"id\":\"start\",\"type\":\"snaker:start\",\"x\":0,\"y\":0,\"properties\":{},\"text\":{\"value\":\"开始\"}},"
                + node("apply", "\"form\":\"apply-form\",\"taskType\":0,\"performType\":0,\"assignee\":\"applicant\"", "发起申请") + ","
                + node("review", "\"form\":\"form-r\",\"taskType\":0,\"performType\":0," + r, "审批") + ","
                + node("final", "\"form\":\"form-f\",\"assignee\":\"userF\",\"taskType\":0,\"performType\":0", "终审") + ","
                + node("pay", "\"form\":\"form-p\",\"assignee\":\"userP\",\"taskType\":0,\"performType\":0", "支付") + ","
                + "{\"id\":\"end\",\"type\":\"snaker:end\",\"x\":0,\"y\":0,\"properties\":{},\"text\":{\"value\":\"结束\"}}"
                + "],\"edges\":["
                + edge("e1", "start", "apply") + ","
                + edge("e2", "apply", "review") + ","
                + edge("e3", "review", "final") + ","
                + edge("e4", "final", "pay") + ","
                + edge("e5", "pay", "end")
                + "]}";
    }

    /** 并行双支：review 出两条边到 payA/payB，各自汇入 end；供"分支全废弃"档。 */
    private String chainFork(String reviewProps) {
        String r = reviewProps.isEmpty() ? "\"assignee\":\"userA\"" : "\"assignee\":\"userA\"," + reviewProps;
        return "{\"name\":\"recallfork-" + (++seq) + "\",\"displayName\":\"已办撤回并行\",\"type\":\"approval\","
                + "\"nodes\":["
                + "{\"id\":\"start\",\"type\":\"snaker:start\",\"x\":0,\"y\":0,\"properties\":{},\"text\":{\"value\":\"开始\"}},"
                + node("apply", "\"form\":\"apply-form\",\"taskType\":0,\"performType\":0,\"assignee\":\"applicant\"", "发起申请") + ","
                + node("review", "\"form\":\"form-r\",\"taskType\":0,\"performType\":0," + r, "审批") + ","
                + node("payA", "\"form\":\"form-a\",\"assignee\":\"userPA\",\"taskType\":0,\"performType\":0", "支付甲") + ","
                + node("payB", "\"form\":\"form-b\",\"assignee\":\"userPB\",\"taskType\":0,\"performType\":0", "支付乙") + ","
                + "{\"id\":\"end\",\"type\":\"snaker:end\",\"x\":0,\"y\":0,\"properties\":{},\"text\":{\"value\":\"结束\"}}"
                + "],\"edges\":["
                + edge("e1", "start", "apply") + ","
                + edge("e2", "apply", "review") + ","
                + edge("e3", "review", "payA") + ","
                + edge("e4", "review", "payB") + ","
                + edge("e5", "payA", "end") + ","
                + edge("e6", "payB", "end")
                + "]}";
    }

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

    /** 发起并办结申请节点，返回停在 review 的实例。 */
    private ProcessInstance startToReview(String flowJson) throws Exception {
        ProcessInstance.ProcessDefine def = registerFlowJson(flowJson);
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
        return repo.findHistoryTasks(instanceId).stream()
                .filter(t -> name.equals(t.getTaskName())).count();
    }

    private ProcessTask finishedRow(Long instanceId, String name) {
        for (ProcessTask t : repo.findHistoryTasks(instanceId)) {
            if (name.equals(t.getTaskName()) && t.isFinished()) {
                return t;
            }
        }
        return null;
    }

    /** 不按状态滤——撤回后的下游行是 ABANDON(99)，不走 FINISHED。 */
    private ProcessTask anyRow(Long instanceId, String name) {
        for (ProcessTask t : repo.findHistoryTasks(instanceId)) {
            if (name.equals(t.getTaskName())) {
                return t;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> recallLedger(ProcessTask row) {
        Object v = row.getVariables().get(FlowConst.RECALL_HISTORY);
        return v instanceof List ? (List<Map<String, Object>>) v : null;
    }

    // ═══ 用例 ═══

    /** 正向：办结→撤回→原行留账本、复活行名册照旧、下游废弃、重办后再推进。 */
    @Test
    public void recallRevivesAbandonsDownstreamAndAllowsRedo() throws Exception {
        ProcessInstance inst = startToReview(chain("\"allowRecall\":true"));
        ProcessTask review = doing(inst.getInstanceId(), "review");
        engine.executeProcessTask(review.getTaskId(), "userA", FlowData.create().set(FlowConst.SUBMIT_TYPE, AGREE));
        ProcessTask oldReview = finishedRow(inst.getInstanceId(), "review");
        assertNotNull(oldReview);
        assertEquals(1, doingCount(inst.getInstanceId(), "final"));

        ProcessTask resurrected = engine.recallTask(oldReview.getTaskId(), "userA");

        assertEquals("review", resurrected.getTaskName());
        assertTrue(resurrected.isDoing());
        assertTrue(resurrected.getActorIds().contains("userA"));
        // 下游废弃
        ProcessTask finalRow = anyRow(inst.getInstanceId(), "final");
        assertEquals(Integer.valueOf(99), finalRow.getTaskState());
        assertEquals(0, doingCount(inst.getInstanceId(), "final"));
        // 原行不改写＋账本指向复活行
        assertTrue(oldReview.isFinished());
        List<Map<String, Object>> ledger = recallLedger(oldReview);
        assertNotNull(ledger);
        assertEquals(1, ledger.size());
        assertEquals("userA", ledger.get(0).get("operator"));
        assertEquals(resurrected.getTaskId(), ledger.get(0).get("recalledTaskId"));
        assertNotNull(ledger.get(0).get("time"));
        // 实例仍进行中，重办后再推进
        assertEquals(Integer.valueOf(10), repo.findInstanceById(inst.getInstanceId()).getState());
        engine.executeProcessTask(resurrected.getTaskId(), "userA", FlowData.create().set(FlowConst.SUBMIT_TYPE, AGREE));
        assertNotNull(doing(inst.getInstanceId(), "final"));
        assertEquals(2, rows(inst.getInstanceId(), "final"));
    }

    /** 窗口：下游已办结 ⇒ 逐字文案报错且零写入。 */
    @Test
    public void recallBlockedWhenDownstreamFinishedWithZeroWrite() throws Exception {
        ProcessInstance inst = startToReview(chainDeep("\"allowRecall\":true"));
        ProcessTask review = doing(inst.getInstanceId(), "review");
        engine.executeProcessTask(review.getTaskId(), "userA", FlowData.create().set(FlowConst.SUBMIT_TYPE, AGREE));
        ProcessTask finalTask = doing(inst.getInstanceId(), "final");
        engine.executeProcessTask(finalTask.getTaskId(), "userF", FlowData.create().set(FlowConst.SUBMIT_TYPE, AGREE));
        ProcessTask oldReview = finishedRow(inst.getInstanceId(), "review");
        assertNotNull(oldReview);
        assertNotNull(doing(inst.getInstanceId(), "pay"));

        try {
            engine.recallTask(oldReview.getTaskId(), "userA");
            fail("应拦：下游 final 已办结");
        } catch (JeeflowException e) {
            assertEquals("任务[review]的后续节点已办理，不能撤回", e.getMessage());
        }
        // 零写入：无账本、无复活行、下游原样
        assertNull(recallLedger(oldReview));
        assertTrue(finishedRow(inst.getInstanceId(), "final").isFinished());
        assertNotNull(doing(inst.getInstanceId(), "pay"));
        assertEquals(0, doingCount(inst.getInstanceId(), "review"));
    }

    /** 开关未开 ⇒ 逐字文案。 */
    @Test
    public void recallBlockedWhenSwitchOff() throws Exception {
        ProcessInstance inst = startToReview(chain(""));
        ProcessTask review = doing(inst.getInstanceId(), "review");
        engine.executeProcessTask(review.getTaskId(), "userA", FlowData.create().set(FlowConst.SUBMIT_TYPE, AGREE));
        ProcessTask oldReview = finishedRow(inst.getInstanceId(), "review");
        try {
            engine.recallTask(oldReview.getTaskId(), "userA");
            fail("应拦：开关未开");
        } catch (JeeflowException e) {
            assertEquals("节点[review]未开启 allowRecall，不能撤回", e.getMessage());
        }
    }

    /** 非参与者拦、特权放行；进行中行拦。 */
    @Test
    public void recallPermissionAndNotFinishedGuards() throws Exception {
        ProcessInstance inst = startToReview(chain("\"allowRecall\":true"));
        ProcessTask review = doing(inst.getInstanceId(), "review");
        engine.executeProcessTask(review.getTaskId(), "userA", FlowData.create().set(FlowConst.SUBMIT_TYPE, AGREE));
        ProcessTask oldReview = finishedRow(inst.getInstanceId(), "review");
        try {
            engine.recallTask(oldReview.getTaskId(), "userZ");
            fail("应拦：非参与者");
        } catch (JeeflowException e) {
            assertEquals("无权限撤回该任务", e.getMessage());
        }
        // flow.admin 特权放行
        ProcessTask resurrected = engine.recallTask(oldReview.getTaskId(), "flow.admin");
        assertTrue(resurrected.isDoing());
        // 进行中行拦（复活行还没办结）
        try {
            engine.recallTask(resurrected.getTaskId(), "userA");
            fail("应拦：目标行未办结");
        } catch (JeeflowException e) {
            assertEquals("任务非已完成状态，不可撤回", e.getMessage());
        }
    }

    /** 实例已走完 ⇒ 实例非进行中档。 */
    @Test
    public void recallBlockedWhenInstanceFinished() throws Exception {
        ProcessInstance inst = startToReview(chain("\"allowRecall\":true"));
        ProcessTask review = doing(inst.getInstanceId(), "review");
        engine.executeProcessTask(review.getTaskId(), "userA", FlowData.create().set(FlowConst.SUBMIT_TYPE, AGREE));
        ProcessTask finalTask = doing(inst.getInstanceId(), "final");
        engine.executeProcessTask(finalTask.getTaskId(), "userF", FlowData.create().set(FlowConst.SUBMIT_TYPE, AGREE));
        assertEquals(Integer.valueOf(20), repo.findInstanceById(inst.getInstanceId()).getState());
        ProcessTask oldReview = finishedRow(inst.getInstanceId(), "review");
        try {
            engine.recallTask(oldReview.getTaskId(), "userA");
            fail("应拦：实例已结束");
        } catch (JeeflowException e) {
            assertEquals("流程实例非进行中状态，不可撤回", e.getMessage());
        }
    }

    /** 后加签行属下游 DOING：撤回连带废弃，不留孤儿待办；复活后可正常推进。 */
    @Test
    public void recallAbandonsAddSignRowsToo() throws Exception {
        ProcessInstance inst = startToReview(chain("\"allowRecall\":true,\"allowAddSign\":true"));
        ProcessTask review = doing(inst.getInstanceId(), "review");
        engine.executeProcessTask(review.getTaskId(), "userA", FlowData.create()
                .set(FlowConst.SUBMIT_TYPE, AGREE)
                .set(FlowConst.ADD_SIGN_OPERATORS, "userX"));
        // 加签行 userX 在 review 节点续办
        ProcessTask addSignRow = doing(inst.getInstanceId(), "review");
        assertNotNull(addSignRow);
        assertTrue(addSignRow.getActorIds().contains("userX"));
        ProcessTask oldReview = finishedRow(inst.getInstanceId(), "review");

        engine.recallTask(oldReview.getTaskId(), "userA");

        // 加签行废弃、复活行是节点上唯一 DOING
        assertEquals(Integer.valueOf(99), addSignRow.getTaskState());
        assertEquals(1, doingCount(inst.getInstanceId(), "review"));
        // 复活行照常推进（无兄弟待办 ⇒ merged 出边）
        ProcessTask resurrected = doing(inst.getInstanceId(), "review");
        engine.executeProcessTask(resurrected.getTaskId(), "userA", FlowData.create().set(FlowConst.SUBMIT_TYPE, AGREE));
        assertNotNull(doing(inst.getInstanceId(), "final"));
    }

    /** 并行分支：撤回后两支全部废弃。 */
    @Test
    public void recallAbandonsBothForkBranches() throws Exception {
        ProcessInstance inst = startToReview(chainFork("\"allowRecall\":true"));
        ProcessTask review = doing(inst.getInstanceId(), "review");
        engine.executeProcessTask(review.getTaskId(), "userA", FlowData.create().set(FlowConst.SUBMIT_TYPE, AGREE));
        assertNotNull(doing(inst.getInstanceId(), "payA"));
        assertNotNull(doing(inst.getInstanceId(), "payB"));
        ProcessTask oldReview = finishedRow(inst.getInstanceId(), "review");

        engine.recallTask(oldReview.getTaskId(), "userA");

        ProcessTask payARow = anyRow(inst.getInstanceId(), "payA");
        assertEquals(Integer.valueOf(99), payARow.getTaskState());
        ProcessTask payBRow = anyRow(inst.getInstanceId(), "payB");
        assertEquals(Integer.valueOf(99), payBRow.getTaskState());
        assertEquals(1, doingCount(inst.getInstanceId(), "review"));
    }

    /** 解析期禁配：会签节点/申请节点配 allowRecall 直接报错。 */
    @Test
    public void parseBansAllowRecallOnCountersignAndApplyNodes() {
        try {
            startToReview(chain("\"allowRecall\":true,\"performType\":1,\"countersignType\":\"PARALLEL\""));
            fail("应拦：会签节点禁配");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("不允许配置 allowRecall"));
            assertTrue(e.getMessage().contains("会签节点"));
        }
        try {
            // 申请节点＝start 直接后继：apply 上配 allowRecall 应被解析期拦
            String json = chain("").replace(
                    "\"form\":\"apply-form\",\"taskType\":0,\"performType\":0,\"assignee\":\"applicant\"",
                    "\"form\":\"apply-form\",\"taskType\":0,\"performType\":0,\"assignee\":\"applicant\",\"allowRecall\":true");
            startToReview(json);
            fail("应拦：申请节点禁配");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("首任务节点"));
            assertTrue(e.getMessage().contains("allowRecall"));
        }
    }

    /** 消息常量与 spec 06 逐字文案一致性自证（防常量被改后测试仍绿）。 */
    @Test
    public void pinnedMessagesMatchSpec() {
        assertEquals("任务[%s]的后续节点已办理，不能撤回", RecallSupport.MSG_WINDOW);
        assertEquals("节点[%s]未开启 allowRecall，不能撤回", RecallSupport.MSG_SWITCH_OFF);
        assertEquals("任务非已完成状态，不可撤回", RecallSupport.MSG_NOT_FINISHED);
        assertEquals("无权限撤回该任务", RecallSupport.MSG_NO_PERMISSION);
        assertEquals("流程实例非进行中状态，不可撤回", RecallSupport.MSG_INSTANCE_NOT_DOING);
    }
}
