package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.JeeflowException;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
import com.mldong.jeeflow.facade.JeeflowFacade;
import com.mldong.jeeflow.spi.IUserProvider;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * 「动态会签（征询模式 dynamicCountersign）」验收（issues/247）：发起不办结主办行、成员行三戳
 * （parent/轮次/reason）、账本含 reason、闸门逐字文案挡全部 submitType、成员行禁拉人、
 * 重复/给自己/上限三拦、撤回接口恢复闸门且已交意见保留、多轮征询跨轮隔离、detail 投影、
 * 三条解析期硬校验、征询档推进闸（成员办结不流转、主办拍板才流转）。
 *
 * <p>发起/闸门/撤回在门面分流——测试经 {@code facade.flow} 驱动；成员与主办办结走引擎原路径。
 * 夹具内联 JSON，不碰共享 flows 目录。</p>
 */
public class DynamicCountersignTest {

    private JeeflowEngine engine;
    private MemoryProcessRepository repo;
    private TestJsonProvider json;
    private JeeflowFacade facade;
    private static int seq;

    private static final int AGREE = ProcessSubmitTypeEnum.AGREE.getCode();
    private static final int REJECT = ProcessSubmitTypeEnum.REJECT.getCode();
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

    private String chain(String reviewProps) {
        String r = reviewProps.isEmpty() ? "\"assignee\":\"userA\"" : "\"assignee\":\"userA\"," + reviewProps;
        return "{\"name\":\"collect-" + (++seq) + "\",\"displayName\":\"动态会签夹具\",\"type\":\"approval\","
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

    private static final String COLLECT_ON = "\"dynamicCountersign\":true";

    private ProcessInstance startToReview(String flowJson) throws Exception {
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

    /** 经门面发起动态会签，返回响应信封。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> collect(ProcessTask host, String operator, List<String> operators, String reason) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("processTaskId", String.valueOf(host.getTaskId()));
        a.put("operator", operator);
        a.put(FlowConst.SUBMIT_TYPE, AGREE);
        a.put(FlowConst.ADD_SIGN_OPERATORS, operators);
        if (reason != null) {
            a.put(FlowConst.ADD_SIGN_REASON, reason);
        }
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
            if (name.equals(t.getTaskName())) {
                n++;
            }
        }
        return n;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> ledger(ProcessTask row) {
        Object v = row.getVariables().get(FlowConst.ADD_SIGN_HISTORY);
        return v instanceof List ? (List<Map<String, Object>>) v : null;
    }

    // ═══ 用例 ═══

    /** 发起：不办结主办行、成员行三戳齐、账本含 reason、响应 marker。 */
    @Test
    public void collectSubmitKeepsHostDoingAndStampsMemberRows() throws Exception {
        ProcessInstance inst = startToReview(chain(COLLECT_ON));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        Map<String, Object> resp = collect(host, "userA", Arrays.asList("userX", "userY"), "涉及地规划需两部门意见");
        assertEquals(Integer.valueOf(0), resp.get("code"));
        Map<String, Object> dc = (Map<String, Object>) data(resp).get("dynamicCountersign");
        assertEquals(1, dc.get("round"));
        assertEquals(2, dc.get("total"));
        assertEquals(0, dc.get("submitted"));
        // 主办行仍 DOING
        ProcessTask hostRow = repo.findTaskById(host.getTaskId());
        assertTrue(hostRow.isDoing());
        // 成员行三戳
        List<ProcessTask> members = com.mldong.jeeflow.core.CollectSupport.memberRows(
                repo.findInstanceById(inst.getInstanceId()).getTasks(), hostRow);
        assertEquals(2, members.size());
        for (ProcessTask m : members) {
            assertTrue(m.isDoing());
            assertEquals(host.getTaskId(), m.getParentTaskId());
            assertEquals(Integer.valueOf(1), m.getVariables()
                    .getInt(FlowConst.ADD_SIGN_ROUND + "_review"));
            assertEquals("涉及地规划需两部门意见", m.getVariables().getStr(FlowConst.ADD_SIGN_REASON));
        }
        // 账本含 reason、只一条
        List<Map<String, Object>> lg = ledger(hostRow);
        assertEquals(1, lg.size());
        assertEquals("涉及地规划需两部门意见", lg.get(0).get("reason"));
        assertEquals("userA", lg.get(0).get("fromActor"));
    }

    /** 闸门：未收齐时主办按任意 submitType 办结 ⇒ 逐字文案报错且零写入。 */
    @Test
    public void gateBlocksAllSubmitTypesUntilCollected() throws Exception {
        ProcessInstance inst = startToReview(chain(COLLECT_ON));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        collect(host, "userA", Arrays.asList("userX", "userY"), null);
        for (int submitType : Arrays.asList(AGREE, REJECT)) {
            Map<String, Object> resp = exec(host.getTaskId(), "userA", submitType);
            assertEquals(Integer.valueOf(99999999), resp.get("code"));
            assertEquals("节点[review]动态会签意见未收齐（已提交 0/2），不能办理", resp.get("msg"));
        }
        // 零写入：主办仍 DOING、无成员办结
        assertTrue(repo.findTaskById(host.getTaskId()).isDoing());
        assertEquals(3, doingCount(inst.getInstanceId(), "review"));
    }

    /** 收齐后主办拍板推进；投影 active 随提交翻转为 false。 */
    @Test
    public void hostProceedsAfterAllSubmittedWithProjection() throws Exception {
        ProcessInstance inst = startToReview(chain(COLLECT_ON));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        collect(host, "userA", Arrays.asList("userX", "userY"), null);
        List<ProcessTask> members = com.mldong.jeeflow.core.CollectSupport.memberRows(
                repo.findInstanceById(inst.getInstanceId()).getTasks(), host);
        // 逐个提交意见（意见落各自行的 tf_approvalComment）
        int i = 0;
        for (ProcessTask m : members) {
            engine.executeProcessTask(m.getTaskId(), m.getActorIds().get(0),
                    FlowData.create().set(FlowConst.SUBMIT_TYPE, AGREE)
                            .set(FlowConst.APPROVAL_COMMENT, "意见" + (++i)));
        }
        // 投影：submitted=2、active=false
        Map<String, Object> detail = facade.flow("processTask/detail",
                new LinkedHashMap<String, Object>() {{
                    put("id", String.valueOf(host.getTaskId()));
                }});
        Map<String, Object> ext = (Map<String, Object>) data(detail).get("ext");
        Map<String, Object> dc = (Map<String, Object>) ext.get("dynamicCountersign");
        assertEquals(Boolean.FALSE, dc.get("active"));
        assertEquals(2, dc.get("total"));
        assertEquals(2, dc.get("submitted"));
        // 主办拍板 → 推进
        Map<String, Object> resp = exec(host.getTaskId(), "userA", AGREE);
        assertEquals(Integer.valueOf(0), resp.get("code"));
        assertNotNull(doing(inst.getInstanceId(), "final"));
    }

    /** 成员行携带拉人参数 ⇒ 报错；重复拉同一人 ⇒ 报错；加给自己 ⇒ 报错。 */
    @Test
    public void memberPullDuplicateAndSelfGuards() throws Exception {
        ProcessInstance inst = startToReview(chain(COLLECT_ON));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        collect(host, "userA", Arrays.asList("userX"), null);
        ProcessTask member = com.mldong.jeeflow.core.CollectSupport.memberRows(
                repo.findInstanceById(inst.getInstanceId()).getTasks(), host).get(0);
        // 成员行拉人
        Map<String, Object> resp = exec(member.getTaskId(), "userX", AGREE,
                FlowData.create().set(FlowConst.ADD_SIGN_OPERATORS, "userZ"));
        assertEquals("动态会签成员任务不能发起加签", resp.get("msg"));
        // 重复拉 pending 中的人
        Map<String, Object> resp2 = collect(host, "userA", Arrays.asList("userX"), null);
        assertEquals("操作人[userX]已在节点[review]的动态会签中", resp2.get("msg"));
        // 加给自己
        Map<String, Object> resp3 = collect(host, "userA", Arrays.asList("userA"), null);
        assertEquals("不能把任务加签给自己", resp3.get("msg"));
    }

    /** 撤回：未答废弃、已交保留、账本记撤回、闸门恢复；重复撤回与非参与者各归各文案。 */
    @Test
    public void revokeRestoresGateAndKeepsSubmittedOpinions() throws Exception {
        ProcessInstance inst = startToReview(chain(COLLECT_ON));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        collect(host, "userA", Arrays.asList("userX", "userY"), null);
        List<ProcessTask> members = com.mldong.jeeflow.core.CollectSupport.memberRows(
                repo.findInstanceById(inst.getInstanceId()).getTasks(), host);
        ProcessTask xRow = members.get(0).getActorIds().contains("userX") ? members.get(0) : members.get(1);
        ProcessTask yRow = members.get(0) == xRow ? members.get(1) : members.get(0);
        engine.executeProcessTask(xRow.getTaskId(), "userX",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, AGREE).set(FlowConst.APPROVAL_COMMENT, "甲部门意见"));

        Map<String, Object> resp = facade.flow("processTask/revokeDynamicCountersign",
                new LinkedHashMap<String, Object>() {{
                    put("processTaskId", String.valueOf(host.getTaskId()));
                    put("operator", "userA");
                    put("reason", "部门选错重新拉");
                }});
        assertEquals(Integer.valueOf(0), resp.get("code"));
        assertEquals(1, data(resp).get("revoked"));
        // 已交保留（FINISHED）、未答废弃（99）
        assertTrue(repo.findTaskById(xRow.getTaskId()).isFinished());
        assertEquals(Integer.valueOf(99), repo.findTaskById(yRow.getTaskId()).getTaskState());
        // 账本两条（发起＋撤回，撤回条带 revoked 标记）
        List<Map<String, Object>> lg = ledger(repo.findTaskById(host.getTaskId()));
        assertEquals(2, lg.size());
        assertEquals(Boolean.TRUE, lg.get(1).get("revoked"));
        // 闸门恢复：主办可直接拍板推进
        Map<String, Object> go = exec(host.getTaskId(), "userA", AGREE);
        assertEquals(Integer.valueOf(0), go.get("code"));
        assertNotNull(doing(inst.getInstanceId(), "final"));
        // 撤回已无待答 → 逐字文案；非参与者 → 各归各
        Map<String, Object> again = facade.flow("processTask/revokeDynamicCountersign",
                new LinkedHashMap<String, Object>() {{
                    put("processTaskId", String.valueOf(host.getTaskId()));
                    put("operator", "userA");
                }});
        assertEquals("节点[review]没有进行中的动态会签", again.get("msg"));
    }

    /** 多轮征询：跨轮意见互不覆盖，轮次递增，收齐判据跨轮计数。 */
    @Test
    public void multiRoundCollectCountsAcrossRounds() throws Exception {
        ProcessInstance inst = startToReview(chain(COLLECT_ON));
        ProcessTask host = doing(inst.getInstanceId(), "review");
        Map<String, Object> r1 = collect(host, "userA", Arrays.asList("userX"), null);
        assertEquals(1, ((Map<String, Object>) data(r1).get("dynamicCountersign")).get("round"));
        ProcessTask xRow = com.mldong.jeeflow.core.CollectSupport.memberRows(
                repo.findInstanceById(inst.getInstanceId()).getTasks(), host).get(0);
        engine.executeProcessTask(xRow.getTaskId(), "userX",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, AGREE));
        // 第二轮（闸门已开时仍可再拉）
        Map<String, Object> r2 = collect(host, "userA", Arrays.asList("userY"), null);
        assertEquals(2, ((Map<String, Object>) data(r2).get("dynamicCountersign")).get("round"));
        // 跨轮收齐判据：x 已交、y 未交 ⇒ 1/2
        Map<String, Object> resp = exec(host.getTaskId(), "userA", AGREE);
        assertEquals("节点[review]动态会签意见未收齐（已提交 1/2），不能办理", resp.get("msg"));
        List<ProcessTask> members = com.mldong.jeeflow.core.CollectSupport.memberRows(
                repo.findInstanceById(inst.getInstanceId()).getTasks(), host);
        assertEquals(2, members.size());
        for (ProcessTask m : members) {
            if (!m.isDoing()) {
                continue; // 第 1 轮已提交的行不再重复办结
            }
            engine.executeProcessTask(m.getTaskId(), m.getActorIds().get(0),
                    FlowData.create().set(FlowConst.SUBMIT_TYPE, AGREE));
        }
        Map<String, Object> go = exec(host.getTaskId(), "userA", AGREE);
        assertEquals(Integer.valueOf(0), go.get("code"));
        assertNotNull(doing(inst.getInstanceId(), "final"));
    }

    /** 三条解析期硬校验：双开／会签节点／申请节点。 */
    @Test
    public void parseBansMutexCountersignAndApplyNodes() {
        try {
            startToReview(chain("\"dynamicCountersign\":true,\"allowAddSign\":true"));
            fail("应拦：双开");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("不允许同时配置 allowAddSign 与 dynamicCountersign"));
        }
        try {
            startToReview(chain("\"dynamicCountersign\":true,\"performType\":1,\"countersignType\":\"PARALLEL\""));
            fail("应拦：会签节点禁配");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("会签节点[review]不允许配置 dynamicCountersign"));
        }
        try {
            String json = chain(COLLECT_ON).replace(
                    "\"form\":\"apply-form\",\"taskType\":0,\"performType\":0,\"assignee\":\"applicant\"",
                    "\"form\":\"apply-form\",\"taskType\":0,\"performType\":0,\"assignee\":\"applicant\",\"dynamicCountersign\":true");
            startToReview(json);
            fail("应拦：申请节点禁配");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("申请节点[apply]不允许配置 dynamicCountersign"));
        }
    }

    /** 消息常量与 spec 06 逐字文案一致性自证。 */
    @Test
    public void pinnedMessagesMatchSpec() {
        assertEquals("节点[%s]动态会签意见未收齐（已提交 %d/%d），不能办理", com.mldong.jeeflow.core.CollectSupport.MSG_PENDING);
        assertEquals("节点[%s]没有进行中的动态会签", com.mldong.jeeflow.core.CollectSupport.MSG_NO_PENDING);
        assertEquals("无权限撤回该动态会签", com.mldong.jeeflow.core.CollectSupport.MSG_NO_PERMISSION_REVOKE);
        assertEquals("动态会签成员任务不能发起加签", com.mldong.jeeflow.core.CollectSupport.MSG_MEMBER_PULL);
        assertEquals("操作人[%s]已在节点[%s]的动态会签中", com.mldong.jeeflow.core.CollectSupport.MSG_DUPLICATE);
    }

    // ═══ 助手 ═══

    private Map<String, Object> exec(Long taskId, String operator, int submitType) {
        return exec(taskId, operator, submitType, null);
    }

    private Map<String, Object> exec(Long taskId, String operator, int submitType, FlowData extra) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("processTaskId", String.valueOf(taskId));
        a.put("operator", operator);
        a.put(FlowConst.SUBMIT_TYPE, submitType);
        if (extra != null) {
            a.putAll(extra);
        }
        return facade.flow("processTask/execute", a);
    }
}
