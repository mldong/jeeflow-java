package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.domain.ProcessDesign;
import com.mldong.jeeflow.domain.ProcessDesignHis;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
import com.mldong.jeeflow.facade.JeeflowFacade;
import com.mldong.jeeflow.spi.IExpressionEvaluator;
import com.mldong.jeeflow.spi.IProcessExtRepository;
import com.mldong.jeeflow.spi.IProcessRepository;
import com.mldong.jeeflow.spi.IOrgUserProvider;
import com.mldong.jeeflow.spi.IUserProvider;
import com.mldong.jeeflow.spi.IUserProvider.UserInfo;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * JeeflowFacade 统一门面测试（v1.1.0）——flow(action, map) 全 action 路由
 */
public class JeeflowFacadeTest {

    private JeeflowFacade facade;
    private JeeflowEngine engine;
    private MemoryProcessRepository repo;
    private MemoryProcessExtRepository extRepo;
    private IProcessRepository rawRepo;

    @Before
    public void setUp() {
        Configuration config = new Configuration();
        repo = new MemoryProcessRepository();
        extRepo = new MemoryProcessExtRepository();
        TestJsonProvider json = new TestJsonProvider();

        ServiceContext.put("repository", repo);
        ServiceContext.put("json", json);
        ServiceContext.put("expr", new TestExpressionEvaluator());
        ServiceContext.put("user", new IUserProvider() {
            @Override public UserInfo getUser(String userId) {
                UserInfo u = new UserInfo();
                u.setUserId(userId);
                u.setRealName("用户" + userId);
                return u;
            }
        });

        ServiceContext.put("org", new IOrgUserProvider() {
            @Override public List<String> findDeptLeaders(String deptId) { return null; }
            @Override public List<String> findDeptMainLeaders(String deptId) { return null; }
            @Override public List<String> findByRole(String roleCode) {
                if ("finance".equals(roleCode)) return Arrays.asList("finA", "finB");
                return null;
            }
        });

        engine = new JeeflowEngineImpl();
        engine.configure(config);
        rawRepo = repo;
        facade = new JeeflowFacade(engine, repo, extRepo);
    }

    private ProcessInstance.ProcessDefine registerFlow(String filename) throws Exception {
        byte[] bytes = Files.readAllBytes(
                Paths.get("src/test/resources/flows/" + filename));
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName(filename.replace(".json", ""));
        def.setDisplayName(filename);
        def.setType("approval");
        def.setState(1);
        def.setVersion(1);
        def.setContent(bytes);
        repo.addDefine(def);
        return def;
    }

    private Map<String, Object> args(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i].toString(), kv[i + 1]);
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String action, Map<String, Object> a) {
        return (Map<String, Object>) facade.flow(action, a);
    }

    private void assertOk(Map<String, Object> r) {
        assertEquals("code 应为 0: " + r, Integer.valueOf(0), r.get("code"));
    }

    // ═══ 流程定义 ═══

    @Test
    public void testDefinePageAndDeployVersion() throws Exception {
        // deploy：新定义 version=0
        String content = new String(Files.readAllBytes(
                Paths.get("src/test/resources/flows/01-simple.json")), StandardCharsets.UTF_8);
        Map<String, Object> r = call("processDefine/deploy", args("content", content));
        assertOk(r);
        Long defineId = toLong(((Map<String, Object>) r.get("data")).get("processDefineId"));
        assertNotNull(defineId);
        ProcessInstance.ProcessDefine def = repo.findDefineById(defineId);
        assertEquals(Integer.valueOf(0), def.getVersion());

        // 再次 deploy：version+1
        r = call("processDefine/deploy", args("content", content));
        assertOk(r);
        Long defineId2 = toLong(((Map<String, Object>) r.get("data")).get("processDefineId"));
        assertNotEquals(defineId, defineId2);
        assertEquals(Integer.valueOf(1), repo.findDefineById(defineId2).getVersion());

        // 分页
        r = call("processDefine/page", args("pageNum", 1, "pageSize", 10));
        assertOk(r);
        Map<String, Object> data = (Map<String, Object>) r.get("data");
        assertEquals(Integer.valueOf(2), data.get("recordCount"));

        // 启停
        r = call("processDefine/upAndDown", args("id", defineId, "state", 0));
        assertOk(r);
        assertEquals(Integer.valueOf(0), repo.findDefineById(defineId).getState());

        // 删除
        r = call("processDefine/remove", args("id", defineId2));
        assertOk(r);
        assertNull(repo.findDefineById(defineId2));
    }

    // ═══ 流程实例 + 任务 ═══

    @Test
    public void testInstanceTaskAndWithdraw() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("01-simple.json");
        Long defineId = def.getId();

        // startAndExecute：发起并自动完成 apply → task1(leader)
        Map<String, Object> r = call("processInstance/startAndExecute",
                args("processDefineId", defineId, "operator", "zhangsan", "amount", "1000"));
        assertOk(r);
        Long instanceId = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));
        ProcessInstance inst = repo.findInstanceById(instanceId);
        assertNotNull(inst);

        // todoList：leader 有待办
        r = call("processTask/todoList", args("operator", "leader"));
        assertOk(r);
        Map<String, Object> data = (Map<String, Object>) r.get("data");
        assertEquals(Integer.valueOf(1), data.get("recordCount"));

        // execute（AGREE=1）：leader 完成任务 → 实例完成
        List<com.mldong.jeeflow.domain.ProcessTask> doing = rawRepo.findDoingTasks(inst.getInstanceId(), null);
        r = call("processTask/execute", args("processTaskId", doing.get(0).getTaskId(),
                "operator", "leader", "submitType", 1));
        assertOk(r);
        assertEquals(com.mldong.jeeflow.enums.ProcessInstanceStateEnum.FINISHED.getCode(),
                repo.findInstanceById(inst.getInstanceId()).getState());

        // withdraw：完成前撤回（发起人自己撤，合规用例 24 判据①）
        ProcessInstance.ProcessDefine def2 = registerFlow("01-simple.json");
        r = call("processInstance/startAndExecute", args("processDefineId", def2.getId(), "operator", "zhangsan"));
        Long instanceId2 = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));
        r = call("processInstance/withdraw", args("id", instanceId2, "operator", "zhangsan"));
        assertOk(r);
        assertEquals(com.mldong.jeeflow.enums.ProcessInstanceStateEnum.WITHDRAW.getCode(),
                repo.findInstanceById(instanceId2).getState());
        // 断持久值而非"doing 列表为空"——30(撤回) 与 99(废弃) 都满足后者（issues/113 的根因）；
        // 已完成(20) 的 apply 行不得被撤回改写
        for (com.mldong.jeeflow.domain.ProcessTask t : rawRepo.findHistoryTasks(instanceId2)) {
            assertEquals("apply".equals(t.getTaskName())
                    ? "已完成(20) 任务行不得被撤回改写：apply"
                    : "进行中任务须落 30(WITHDRAW)，不能是 99(废弃码)：" + t.getTaskName(),
                    "apply".equals(t.getTaskName())
                            ? com.mldong.jeeflow.enums.ProcessTaskStateEnum.FINISHED.getCode()
                            : com.mldong.jeeflow.enums.ProcessTaskStateEnum.WITHDRAW.getCode(),
                    t.getTaskState());
        }
        assertEquals("实例 update_user 回写真实撤回人", "zhangsan", repo.findInstanceById(instanceId2).getUpdateUser());
        // 级联：doing 任务全部撤回（v1.0.1）
        List<com.mldong.jeeflow.domain.ProcessTask> after = rawRepo.findDoingTasks(instanceId2, null);
        assertEquals(0, after.size());
    }

    // ═══ 撤回鉴权（issues/114 · 合规用例 24）═══

    /** 断言负向：code=99999999 且 msg 含跨栈统一关键字。 */
    private void assertRejected(Map<String, Object> r, String msgKeyword) {
        assertEquals("负向应报错（禁止静默成功）: " + r, Integer.valueOf(99999999), r.get("code"));
        assertTrue("msg 应含「" + msgKeyword + "」: " + r, String.valueOf(r.get("msg")).contains(msgKeyword));
    }

    /** 某人的待办任务 id 集合（走门面真实查询路径，不直接翻仓储） */
    private List<Object> todoIds(String operator) {
        Map<String, Object> r = call("processTask/todoList", args("operator", operator));
        assertOk(r);
        List<Object> ids = new ArrayList<>();
        for (Object o : (List<?>) ((Map<String, Object>) r.get("data")).get("rows")) {
            ids.add(((Map<String, Object>) o).get("id"));
        }
        return ids;
    }

    /** 某人的已办任务 id 集合（走门面真实查询路径；pageDoneTasks 按 state<>10 AND operator=? 过滤） */
    private List<Object> doneIds(String operator) {
        Map<String, Object> r = call("processTask/doneList", args("operator", operator));
        assertOk(r);
        List<Object> ids = new ArrayList<>();
        for (Object o : (List<?>) ((Map<String, Object>) r.get("data")).get("rows")) {
            ids.add(((Map<String, Object>) o).get("id"));
        }
        return ids;
    }

    /**
     * 合规用例 24（issues/114）：撤回鉴权矩阵 + 审计回写。
     *
     * <p>三条归属判据逐条验（发起人 / 任一进行中任务参与者 / flow.auto+flow.admin），
     * operator 硬必填（缺失与空串都拒，且**绝不回落 user1**）；全部断言打在读回的持久值上：
     * 实例 state+update_user、每条任务行 task_state+update_user，已完成(20) 行不得被改写。</p>
     */
    @Test
    public void testWithdrawAuthorizationMatrixAndAuditFields() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("02-multi-task.json");
        Map<String, Object> r = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "zhangsan"));
        assertOk(r);
        Long instanceId = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));
        Long task1Id = rawRepo.findDoingTasks(instanceId, new String[]{}).get(0).getTaskId();
        assertEquals("task1", rawRepo.findTaskById(task1Id).getTaskName());

        // 负向①：缺 operator → 明确报错；且实例/任务一行未动，撤回人也没被记成 user1
        assertRejected(call("processInstance/withdraw", args("id", instanceId)), "operator 必填");
        // 负向①b：空串 operator 同样拒绝（契约"缺失或空串"）
        assertRejected(call("processInstance/withdraw", args("id", instanceId, "operator", "   ")), "operator 必填");
        ProcessInstance untouched = repo.findInstanceById(instanceId);
        assertEquals("缺 operator 的撤回不得改动实例状态",
                com.mldong.jeeflow.enums.ProcessInstanceStateEnum.DOING.getCode(), untouched.getState());
        assertNotEquals("严禁缺省回落为固定账号 user1（审计链失真）", "user1", untouched.getUpdateUser());
        assertEquals("缺 operator 的撤回不得改动任务状态",
                com.mldong.jeeflow.enums.ProcessTaskStateEnum.DOING.getCode(),
                repo.findTaskById(task1Id).getTaskState());

        // 负向②：无关第三人（既非发起人，也不在任何进行中任务的参与者里）
        assertRejected(call("processInstance/withdraw",
                args("id", instanceId, "operator", "nobody")), "无权限撤回该流程实例");
        assertEquals("越权撤回不得改状态",
                com.mldong.jeeflow.enums.ProcessInstanceStateEnum.DOING.getCode(),
                repo.findInstanceById(instanceId).getState());
        assertEquals(com.mldong.jeeflow.enums.ProcessTaskStateEnum.DOING.getCode(),
                repo.findTaskById(task1Id).getTaskState());

        // 正向：进行中任务的参与者（判据②）撤回 → 作用于整单 + update_user 回写真实撤回人
        assertOk(call("processInstance/withdraw", args("id", instanceId, "operator", "leader")));
        ProcessInstance after = repo.findInstanceById(instanceId);
        assertEquals(com.mldong.jeeflow.enums.ProcessInstanceStateEnum.WITHDRAW.getCode(), after.getState());
        assertEquals("实例 update_user 须回写真实撤回人（不是发起人、不是 user1）",
                "leader", after.getUpdateUser());
        int withdrawnRows = 0;
        for (com.mldong.jeeflow.domain.ProcessTask t : rawRepo.findHistoryTasks(instanceId)) {
            if ("apply".equals(t.getTaskName())) {
                assertEquals("已完成(20) 任务行不得被撤回改写",
                        com.mldong.jeeflow.enums.ProcessTaskStateEnum.FINISHED.getCode(), t.getTaskState());
                assertEquals("已完成行的 update_user 保持办理人", "zhangsan", t.getUpdateUser());
            } else {
                assertEquals("进行中任务须落 30(WITHDRAW)，不能是 99(废弃码)",
                        com.mldong.jeeflow.enums.ProcessTaskStateEnum.WITHDRAW.getCode(), t.getTaskState());
                assertEquals("被撤任务的 update_user 同样回写撤回人", "leader", t.getUpdateUser());
                withdrawnRows++;
            }
        }
        assertEquals(1, withdrawnRows);
    }

    /** 合规用例 24 补全：撤回作用于整单（一个参与者撤掉全部会签任务）+ 发起人 / flow.admin / flow.auto 放行。 */
    @Test
    public void testWithdrawWholeInstanceAndPrivilegedOperators() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("05-countersign-parallel.json");

        // 判据②（整单）：userA 只是三条会签任务里一条的参与者，撤回的是整单不是自己那条
        Map<String, Object> r = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "zhangsan"));
        assertOk(r);
        Long inst1 = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));
        assertEquals("并行会签应有 3 条进行中任务", 3, rawRepo.findDoingTasks(inst1, new String[]{}).size());
        assertOk(call("processInstance/withdraw", args("id", inst1, "operator", "userA")));
        assertEquals(com.mldong.jeeflow.enums.ProcessInstanceStateEnum.WITHDRAW.getCode(),
                repo.findInstanceById(inst1).getState());
        assertEquals("整单撤回后不应残留进行中任务", 0, rawRepo.findDoingTasks(inst1, new String[]{}).size());
        for (com.mldong.jeeflow.domain.ProcessTask t : rawRepo.findHistoryTasks(inst1)) {
            assertEquals("apply".equals(t.getTaskName())
                            ? com.mldong.jeeflow.enums.ProcessTaskStateEnum.FINISHED.getCode()
                            : com.mldong.jeeflow.enums.ProcessTaskStateEnum.WITHDRAW.getCode(),
                    t.getTaskState());
        }

        // 判据③：flow.admin / flow.auto 放行沿用 isAllowed 既有约定（回归），update_user 记真实操作人
        for (String privileged : Arrays.asList(com.mldong.jeeflow.enums.FlowConst.ADMIN_ID,
                com.mldong.jeeflow.enums.FlowConst.AUTO_ID)) {
            r = call("processInstance/startAndExecute", args("processDefineId", def.getId(), "operator", "zhangsan"));
            assertOk(r);
            Long inst = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));
            assertOk(call("processInstance/withdraw", args("id", inst, "operator", privileged)));
            ProcessInstance done = repo.findInstanceById(inst);
            assertEquals(privileged + " 应放行撤回",
                    com.mldong.jeeflow.enums.ProcessInstanceStateEnum.WITHDRAW.getCode(), done.getState());
            assertEquals(privileged, done.getUpdateUser());
        }
    }


    // ═══ 转办（issues/115 · 合规用例 25）═══

    /**
     * 合规用例 25（issues/115）：转办六条语义 + 四类明确报错 + 加签回归。
     *
     * <p>断言全部打在"读回值"上：参与者表、任务行变量（submitType=7 / tf_transferTo /
     * tf_transferReason / 文案）、update_user（actor_id/operator 列须保持无值——契约条款 4 的 ⚠️），
     * 以及 approvalRecord 出口。</p>
     */
    @Test
    public void testTaskTransferMovesTodoAndWritesTrace() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("01-simple.json");
        Map<String, Object> r = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "zhangsan"));
        assertOk(r);
        Long instanceId = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));
        Long taskId = rawRepo.findDoingTasks(instanceId, new String[]{}).get(0).getTaskId();
        assertTrue("转办前 leader 有该待办", todoIds("leader").contains(taskId));

        // 负向①：operator 必填
        assertRejected(call("processTask/transfer",
                args("processTaskId", taskId, "fromActor", "leader", "toActor", "lisi")), "operator 必填");
        // 负向②：第三人转别人的待办（只能转自己那条，非 auto/admin）
        assertRejected(call("processTask/transfer", args("processTaskId", taskId,
                "operator", "nobody", "fromActor", "leader", "toActor", "lisi")), "无权限转办该任务");
        assertEquals("越权转办后参与者一行未动", Arrays.asList("leader"), rawRepo.findTaskActors(taskId));
        // 负向③：fromActor 不在参与者里
        assertRejected(call("processTask/transfer", args("processTaskId", taskId,
                "operator", "wangwu", "fromActor", "wangwu", "toActor", "lisi")), "原办理人不是该任务参与人");
        // 回归：加签（surrogate/addCandidate）仍是"只追加"，原人保留可办——它不是转办
        assertOk(call("processTask/surrogate",
                args("processTaskId", taskId, "actorIds", Arrays.asList("lisi"))));
        assertTrue("加签后原办理人保留", rawRepo.findTaskActors(taskId).contains("leader"));
        // 负向④：toActor 已是参与者 → 明确报错，不静默成功
        assertRejected(call("processTask/transfer", args("processTaskId", taskId,
                "operator", "leader", "fromActor", "leader", "toActor", "lisi")), "目标人已是该任务参与人");

        // 正向：leader 把自己那条转给 xiaohe（带原因）
        assertOk(call("processTask/transfer", args("processTaskId", taskId,
                "operator", "leader", "fromActor", "leader", "toActor", "xiaohe", "reason", "临时出差")));
        assertFalse("转办后 A 待办消失", todoIds("leader").contains(taskId));
        assertTrue("转办后 B 待办出现", todoIds("xiaohe").contains(taskId));
        List<String> actors = rawRepo.findTaskActors(taskId);
        assertFalse("原办理人被摘走", actors.contains("leader"));
        assertTrue("目标人加入", actors.contains("xiaohe"));
        assertTrue("同任务其他参与人（加签进来的 lisi）不受影响", actors.contains("lisi"));

        // 留痕：任务变量读回（同一 taskId，任务不新建、状态仍进行中）
        com.mldong.jeeflow.domain.ProcessTask transferred = rawRepo.findTaskById(taskId);
        assertEquals(com.mldong.jeeflow.enums.ProcessTaskStateEnum.DOING.getCode(), transferred.getTaskState());
        assertEquals("转办留痕 submitType 须为 7(TRANSFER)",
                Integer.valueOf(7), transferred.getVariables().getInt(com.mldong.jeeflow.enums.FlowConst.SUBMIT_TYPE));
        assertEquals("xiaohe", transferred.getVariables().getStr(com.mldong.jeeflow.enums.FlowConst.TRANSFER_TO));
        assertEquals("临时出差", transferred.getVariables().getStr(com.mldong.jeeflow.enums.FlowConst.TRANSFER_REASON));
        assertEquals("leader 转办给 xiaohe（临时出差）",
                transferred.getVariables().getStr(com.mldong.jeeflow.enums.FlowConst.APPROVAL_COMMENT));
        assertNull("转办不得覆写任务 actor_id/operator 列（契约条款 4 的 ⚠️：进行中任务该列恒无值是家族不变量，"
                + "写入被摘走的人会让撤回/终止后的单凭空出现在其「我已办」里）", transferred.getActorId());
        assertEquals("办理人由 update_user 承载（转办操作人）", "leader", transferred.getUpdateUser());
        // 单跳：账本一条，形状与两跳用例同一把尺子
        @SuppressWarnings("unchecked")
        List<Object> singleLedger = (List<Object>) transferred.getVariables()
                .get(com.mldong.jeeflow.enums.FlowConst.TRANSFER_HISTORY);
        assertNotNull("tf_transferHistory 应为列表", singleLedger);
        assertEquals("单跳账本应只有一条: " + singleLedger, 1, singleLedger.size());
        assertEquals("leader→xiaohe 首跳字段",
                Arrays.asList("submitType", "fromActor", "toActor", "reason", "time", "operator"),
                new ArrayList<>(((Map<String, Object>) singleLedger.get(0)).keySet()));
        assertTransferHop((Map<String, Object>) singleLedger.get(0), 1, "leader", "xiaohe", "临时出差", "leader");

        // approvalRecord 出口：同一行里既要能读到 submitType=7，也要能读出"A 转办给 B（原因）"
        r = call("processInstance/approvalRecord", args("id", instanceId));
        assertOk(r);
        Map<String, Object> record = null;
        for (Object o : (List<?>) r.get("data")) {
            Map<String, Object> row = (Map<String, Object>) o;
            if ("task1".equals(row.get("taskName"))) record = row;
        }
        assertNotNull("审批记录应含 task1 行", record);
        Map<String, Object> recordVar = (Map<String, Object>) record.get("variable");
        assertEquals("7", String.valueOf(recordVar.get(com.mldong.jeeflow.enums.FlowConst.SUBMIT_TYPE)));
        assertEquals("xiaohe", recordVar.get(com.mldong.jeeflow.enums.FlowConst.TRANSFER_TO));
        assertEquals("临时出差", recordVar.get(com.mldong.jeeflow.enums.FlowConst.TRANSFER_REASON));
        assertTrue("审批记录文案须读得出「A 转办给 B（原因）」: " + recordVar,
                String.valueOf(recordVar.get(com.mldong.jeeflow.enums.FlowConst.APPROVAL_COMMENT))
                        .contains("leader 转办给 xiaohe（临时出差）"));
        assertNull("approvalRecord 的 operator 读自任务 actor_id——转办不覆写该列（契约条款 4 的 ⚠️），"
                + "办结前应为空；转办事实由上方 submitType=7/账本/文案承载", record.get("operator"));

        // 负向⑤：任务非进行中不可转办（B 办完再转）
        assertOk(call("processTask/execute",
                args("processTaskId", taskId, "operator", "xiaohe", "submitType", 1)));
        assertRejected(call("processTask/transfer", args("processTaskId", taskId,
                "operator", "xiaohe", "fromActor", "xiaohe", "toActor", "leader")), "任务非进行中，不可转办");
    }

    /** 合规用例 25 补全：会签节点转的是"自己那一票"——只摘 fromActor 一行，其余成员不受影响。 */
    @Test
    public void testTaskTransferOnCountersignOnlyMovesOwnVote() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("05-countersign-parallel.json");
        Map<String, Object> r = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "zhangsan"));
        assertOk(r);
        Long instanceId = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));
        List<com.mldong.jeeflow.domain.ProcessTask> doing = rawRepo.findDoingTasks(instanceId, new String[]{});
        assertEquals(3, doing.size());
        Long taskA = null;
        List<Long> otherTasks = new ArrayList<>();
        for (com.mldong.jeeflow.domain.ProcessTask t : doing) {
            if (rawRepo.findTaskActors(t.getTaskId()).contains("userA")) taskA = t.getTaskId();
            else otherTasks.add(t.getTaskId());
        }
        assertNotNull(taskA);
        assertEquals(2, otherTasks.size());

        // flow.admin 代转（契约例外支：管理员/系统可代转，鉴权仍要求 operator 必填）
        assertOk(call("processTask/transfer", args("processTaskId", taskA,
                "operator", com.mldong.jeeflow.enums.FlowConst.ADMIN_ID, "fromActor", "userA", "toActor", "userD")));

        assertEquals("会签转办只摘 userA 一行", Arrays.asList("userD"), rawRepo.findTaskActors(taskA));
        List<String> othersActors = new ArrayList<>();
        for (Long other : otherTasks) othersActors.addAll(rawRepo.findTaskActors(other));
        java.util.Collections.sort(othersActors);
        assertEquals("其余成员不受影响（userB/userC 各自那票原样保留）",
                Arrays.asList("userB", "userC"), othersActors);
        assertEquals("任务不新建：仍是 3 条进行中会签任务", 3, rawRepo.findDoingTasks(instanceId, new String[]{}).size());
        assertFalse(todoIds("userA").contains(taskA));
        assertTrue(todoIds("userD").contains(taskA));
        // 变量落库形态（内存仓）：submitType=7 与 tf_transferTo 读回
        com.mldong.jeeflow.domain.ProcessTask t = rawRepo.findTaskById(taskA);
        assertEquals(Integer.valueOf(7), t.getVariables().getInt(com.mldong.jeeflow.enums.FlowConst.SUBMIT_TYPE));
        assertEquals("userD", t.getVariables().getStr(com.mldong.jeeflow.enums.FlowConst.TRANSFER_TO));
        assertEquals("无原因时文案不带括号", "userA 转办给 userD",
                t.getVariables().getStr(com.mldong.jeeflow.enums.FlowConst.APPROVAL_COMMENT));
    }

    /**
     * 合规用例 25 · 追加式账本 {@code tf_transferHistory}（契约 fc0883a 留痕三件之第三件）。
     *
     * <p>缺陷机理：本家族审批记录的槽位就是任务行本身，转办发生在任务办结前，B 办结时
     * {@code submitType} 被 B 自己的办理参数覆盖——只有单跳槽位键的话，多跳转办只剩末跳、
     * 办结后转办事实整体消失。故账本必须"每跳 append、只追加不覆盖"，且能活过办结。</p>
     *
     * <p>断言打在形状上：条数、 append 顺序、逐字段值、键名键序（供另外七栈对齐），
     * 而不是只断"读得到"。</p>
     */
    @Test
    public void testTaskTransferHistoryAppendsEachHopAndSurvivesFinish() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("01-simple.json");
        Map<String, Object> r = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "zhangsan"));
        assertOk(r);
        Long instanceId = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));
        Long taskId = rawRepo.findDoingTasks(instanceId, new String[]{}).get(0).getTaskId();

        // 跳 1：A(leader) → B(xiaohe)，跳 2：B(xiaohe) → C(lisi)
        assertOk(call("processTask/transfer", args("processTaskId", taskId,
                "operator", "leader", "fromActor", "leader", "toActor", "xiaohe", "reason", "临时出差")));
        assertOk(call("processTask/transfer", args("processTaskId", taskId,
                "operator", "xiaohe", "fromActor", "xiaohe", "toActor", "lisi", "reason", "由其代批")));

        // 两跳后：账本两条、append 顺序、逐字段；当前槽位仍是 7（C 未办结）
        com.mldong.jeeflow.domain.FlowData vars = rawRepo.findTaskById(taskId).getVariables();
        assertEquals("C 办结前当前槽位 submitType 读作转办", Integer.valueOf(7),
                vars.getInt(com.mldong.jeeflow.enums.FlowConst.SUBMIT_TYPE));
        List<Map<String, Object>> history = assertTransferLedger(vars, "两跳后");
        assertTransferHop(history.get(0), 1, "leader", "xiaohe", "临时出差", "leader");
        assertTransferHop(history.get(1), 2, "xiaohe", "lisi", "由其代批", "xiaohe");
        // 单跳便捷键与末跳文案只留末跳（全量以账本为准）
        assertEquals("lisi", vars.getStr(com.mldong.jeeflow.enums.FlowConst.TRANSFER_TO));
        assertEquals("xiaohe 转办给 lisi（由其代批）",
                vars.getStr(com.mldong.jeeflow.enums.FlowConst.APPROVAL_COMMENT));

        // C 办结（execute submitType=1）：变量走"合并"语义，账本须存活
        assertOk(call("processTask/execute",
                args("processTaskId", taskId, "operator", "lisi", "submitType", 1)));

        com.mldong.jeeflow.domain.FlowData after = rawRepo.findTaskById(taskId).getVariables();
        assertEquals("办结后当前槽位由 B/C 的办理参数覆盖为 1（契约明确的预期行为）",
                Integer.valueOf(1), after.getInt(com.mldong.jeeflow.enums.FlowConst.SUBMIT_TYPE));
        List<Map<String, Object>> afterHistory = assertTransferLedger(after, "C 办结后");
        assertTransferHop(afterHistory.get(0), 1, "leader", "xiaohe", "临时出差", "leader");
        assertTransferHop(afterHistory.get(1), 2, "xiaohe", "lisi", "由其代批", "xiaohe");

        // approvalRecord 出口（前端读取位）：variable 与 ext 两条路径都要读得出全量账本
        r = call("processInstance/approvalRecord", args("id", instanceId));
        assertOk(r);
        Map<String, Object> record = null;
        for (Object o : (List<?>) r.get("data")) {
            Map<String, Object> row = (Map<String, Object>) o;
            if ("task1".equals(row.get("taskName"))) record = row;
        }
        assertNotNull("审批记录应含 task1 行", record);
        assertTransferLedger((Map<String, Object>) record.get("variable"), "approvalRecord.variable 出口");
        assertTransferLedger((Map<String, Object>) record.get("ext"), "approvalRecord.ext 出口");
    }

    /**
     * 契约条款 4 的 ⚠️（jeeflow-doc 778340a，Node 实测纠偏）内存仓路径：转办严禁覆写任务
     * {@code actor_id}/{@code operator} 列。
     *
     * <p>进行中任务该列恒无值是家族不变量；{@code pageDoneTasks} 按 {@code state <> 10 AND operator = ?}
     * 过滤（内存仓已对齐该口径，见 MemoryProcessRepository）——转办时把被摘走的人写进这一列，该单一旦
     * 撤回/终止（离开 DOING 但列值留着），会凭空出现在他从没办过的「我已办」里（Node 实测：转办→撤回后
     * A 的 doneList 冒出 task#30，未转办对照为空）。办理人由 update_user + tf_transferHistory[].operator 承载。</p>
     */
    @Test
    public void testTaskTransferKeepsActorIdUnsetAndWithdrawOutOfDoneList() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("01-simple.json");
        Map<String, Object> r = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "zhangsan"));
        assertOk(r);
        Long instanceId = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));
        Long taskId = rawRepo.findDoingTasks(instanceId, new String[]{}).get(0).getTaskId();

        assertOk(call("processTask/transfer", args("processTaskId", taskId,
                "operator", "leader", "fromActor", "leader", "toActor", "xiaohe")));

        // ① 转办后（任务仍进行中）actor_id/operator 仍无值；办理人记在 update_user
        com.mldong.jeeflow.domain.ProcessTask transferred = rawRepo.findTaskById(taskId);
        assertNull("转办后任务 actor_id 应仍无值（契约条款 4 的 ⚠️）", transferred.getActorId());
        assertEquals("leader", transferred.getUpdateUser());

        // 发起人撤回整单：task1 落 30(WITHDRAW) 离开 DOING——若转办曾污染 operator 列，污染窗口就此打开
        assertOk(call("processInstance/withdraw", args("id", instanceId, "operator", "zhangsan")));
        assertEquals(com.mldong.jeeflow.enums.ProcessTaskStateEnum.WITHDRAW.getCode(),
                rawRepo.findTaskById(taskId).getTaskState());

        // ② 被摘走的 fromActor 从没办过这单，「我已办」不得凭空出现（回归位）；接手人 B 未办结同样不进
        assertFalse("转办→撤回后 fromActor 的 doneList 不得含该单: " + doneIds("leader"),
                doneIds("leader").contains(taskId));
        assertFalse("转办→撤回后接手人（未办结）的 doneList 也不得含该单: " + doneIds("xiaohe"),
                doneIds("xiaohe").contains(taskId));
        // 正向对照：apply 行由 zhangsan 实际办结（startAndExecute 自动完成），证明 doneList 查询非恒空
        assertFalse("对照：zhangsan 实际办结的 apply 行应在其已办列表", doneIds("zhangsan").isEmpty());
    }

    /** 断言 {@code tf_transferHistory} 的形状与条数；返回账本供逐字段断言。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> assertTransferLedger(Map<String, Object> vars, String when) {
        Object raw = vars.get(com.mldong.jeeflow.enums.FlowConst.TRANSFER_HISTORY);
        assertTrue(when + "：tf_transferHistory 应为列表，实际: " + raw, raw instanceof List);
        List<Object> list = (List<Object>) raw;
        assertEquals(when + "：账本应为两条（每跳 append，不覆盖不裁剪）", 2, list.size());
        List<Map<String, Object>> hops = new ArrayList<>();
        for (Object o : list) {
            assertTrue(when + "：账本每条应为 map，实际: " + o, o instanceof Map);
            Map<String, Object> hop = (Map<String, Object>) o;
            // 键名 + 键序锁死（六栈/七栈按此形态对齐，多一少一都算漂移）
            assertEquals(when + "：账本键序须与契约一致: " + hop,
                    Arrays.asList("submitType", "fromActor", "toActor", "reason", "time", "operator"),
                    new ArrayList<>(hop.keySet()));
            hops.add(hop);
        }
        return hops;
    }

    /** 逐字段断一条账本记录（含 append 位置）。 */
    private void assertTransferHop(Map<String, Object> hop, int position,
                                   String from, String to, String reason, String operator) {
        String at = "第 " + position + " 跳 " + from + "→" + to + ": " + hop;
        assertEquals(at + " 的 submitType", 7, ((Number) hop.get("submitType")).intValue());
        assertEquals(at + " 的 fromActor", from, hop.get("fromActor"));
        assertEquals(at + " 的 toActor", to, hop.get("toActor"));
        assertEquals(at + " 的 reason", reason, hop.get("reason"));
        assertEquals(at + " 的 operator", operator, hop.get("operator"));
        Object time = hop.get("time");
        // 跨栈同形锁：Go/Python/Node/PHP/Rust/C# 一律 yyyy-MM-dd HH:mm:ss（spec §2.4），
        // 不得是 Java LocalDateTime.toString() 的 ISO 方言（带 T/小数秒）
        assertTrue(at + " 的 time 应为 yyyy-MM-dd HH:mm:ss 串（跨栈同形），实际: " + time,
                time instanceof String && ((String) time).matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$"));
        assertNotNull(at + " 的 time 应按该格式可解析", java.time.LocalDateTime.parse((String) time,
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }

    // ═══ 流程设计 + 委托（扩展仓储） ═══

    @Test
    public void testDesignAndSurrogate() throws Exception {
        String content = new String(Files.readAllBytes(
                Paths.get("src/test/resources/flows/01-simple.json")), StandardCharsets.UTF_8);

        // 保存设计（含内容快照 + remark/icon，82-9 回显断言用）
        Map<String, Object> r = call("processDesign/save", args(
                "name", "leave", "displayName", "请假流程", "content", content, "operator", "zhangsan",
                "icon", "icon-leave", "remark", "请假流程 v1（含附件上传）"));
        assertOk(r);
        Long designId = toLong(((Map<String, Object>) r.get("data")).get("id"));
        assertNotNull(designId);

        // designPage：时间格式化（processDesign/page 应与 processDefine/page 一致 yyyy-MM-dd HH:mm:ss）
        r = call("processDesign/page", args());
        assertOk(r);
        List<?> dRows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertFalse("designPage 应有行: " + r, dRows.isEmpty());
        Object ct = ((Map<String, Object>) dRows.get(0)).get("createTime");
        assertTrue("designPage 时间应格式化为 yyyy-MM-dd HH:mm:ss: " + ct,
                ct != null && ct.toString().matches("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}"));
        // 82-9：designPage 行回显 remark/icon（设计页回显字段）
        Map<String, Object> dRow = null;
        for (Object o : dRows) {
            Map<String, Object> m = (Map<String, Object>) o;
            if ("leave".equals(m.get("name"))) { dRow = m; break; }
        }
        assertNotNull("designPage 应含 leave 设计行: " + dRows, dRow);
        assertEquals("designPage remark 应回显保存值", "请假流程 v1（含附件上传）", dRow.get("remark"));
        assertEquals("designPage icon 应回显保存值", "icon-leave", dRow.get("icon"));

        // detail：含历史
        r = call("processDesign/detail", args("id", designId));
        assertOk(r);
        Map<String, Object> data = (Map<String, Object>) r.get("data");
        assertNotNull(data.get("jsonObject"));
        assertEquals(1, ((List<?>) data.get("his")).size());

        // 发布设计 → 生成 define + isDeployed=1
        r = call("processDesign/deploy", args("id", designId, "operator", "zhangsan"));
        assertOk(r);
        assertNotNull(((Map<String, Object>) r.get("data")).get("processDefineId"));
        assertEquals(Integer.valueOf(1), extRepo.findDesignById(designId).getIsDeployed());

        // 委托：新增 + 生效查询
        r = call("processSurrogate/save", args(
                "operator", "zhangsan", "surrogate", "lisi", "processName", "leave", "operator", "zhangsan"));
        assertOk(r);
        Long surrogateId = toLong(((Map<String, Object>) r.get("data")).get("id"));
        assertNotNull(surrogateId);
        // getSurrogate 生效（无时间窗）
        assertEquals("lisi", extRepo.getSurrogate("zhangsan", "leave", java.time.LocalDateTime.now()).getSurrogate());
        // 委托分页
        r = call("processSurrogate/page", args("operator", "zhangsan"));
        assertOk(r);
        assertEquals(Integer.valueOf(1), ((Map<String, Object>) r.get("data")).get("recordCount"));

        // 删除委托
        r = call("processSurrogate/remove", args("id", surrogateId));
        assertOk(r);
        assertNull(extRepo.findSurrogateById(surrogateId));
    }

    /** issues/77：委托编辑链路 save → detail 回显 → update 改字段 → detail 再回显断言变更 */
    @Test
    public void testSurrogateDetailAndUpdate() {
        // 新增（带时间窗，用前端 RangePicker 实际提交的 yyyy-MM-dd HH:mm:ss 空格格式）
        Map<String, Object> r = call("processSurrogate/save", args(
                "operator", "zhangsan", "surrogate", "lisi", "processName", "leave",
                "startTime", "2026-08-01 00:00:00", "endTime", "2026-08-31 23:59:59", "enabled", 1));
        assertOk(r);
        Long surrogateId = toLong(((Map<String, Object>) r.get("data")).get("id"));
        assertNotNull(surrogateId);

        // detail 回显：行结构齐全 + 时间格式化（前端 form.vue 用 startTime/endTime 组 RangePicker）
        r = call("processSurrogate/detail", args("id", surrogateId));
        assertOk(r);
        Map<String, Object> d = (Map<String, Object>) r.get("data");
        assertEquals("leave", d.get("processName"));
        assertEquals("zhangsan", d.get("operator"));
        assertEquals("lisi", d.get("surrogate"));
        assertEquals(Integer.valueOf(1), d.get("enabled"));
        assertEquals("2026-08-01 00:00:00", d.get("startTime"));
        assertEquals("2026-08-31 23:59:59", d.get("endTime"));

        // update：改代理人/时间窗/启用状态（前端编辑表单不带 operator，授权人应保留）
        r = call("processSurrogate/update", args(
                "id", surrogateId, "surrogate", "wangwu", "processName", "leave",
                "startTime", "2026-09-01 00:00:00", "endTime", "2026-09-30 23:59:59", "enabled", 0));
        assertOk(r);
        assertEquals(surrogateId, toLong(((Map<String, Object>) r.get("data")).get("id")));

        // detail 再回显：变更生效 + 授权人未被清空
        r = call("processSurrogate/detail", args("id", surrogateId));
        assertOk(r);
        d = (Map<String, Object>) r.get("data");
        assertEquals("wangwu", d.get("surrogate"));
        assertEquals("zhangsan", d.get("operator"));
        assertEquals(Integer.valueOf(0), d.get("enabled"));
        assertEquals("2026-09-01 00:00:00", d.get("startTime"));
        assertEquals("2026-09-30 23:59:59", d.get("endTime"));

        // 负向：id 不存在
        r = call("processSurrogate/detail", args("id", 99999L));
        assertEquals(Integer.valueOf(99999999), r.get("code"));
        r = call("processSurrogate/update", args("id", 99999L, "surrogate", "wangwu"));
        assertEquals(Integer.valueOf(99999999), r.get("code"));
    }

    /** issues/95：前端「我的委托」行内与批量删除统一发 {ids}（行内 = 长度 1 的数组），
     *  此前六语言门面只读单数 {id} → 该页删除整体不可用；单 {id} 形态保留兼容（移动端发这个）。 */
    @Test
    public void testSurrogateRemoveBatchIds() {
        Long a = toLong(((Map<String, Object>) call("processSurrogate/save",
                args("operator", "zhangsan", "surrogate", "lisiA", "processName", "leaveA")).get("data")).get("id"));
        Long b = toLong(((Map<String, Object>) call("processSurrogate/save",
                args("operator", "zhangsan", "surrogate", "lisiB", "processName", "leaveB")).get("data")).get("id"));
        Long c = toLong(((Map<String, Object>) call("processSurrogate/save",
                args("operator", "lisiC", "surrogate", "lisiD", "processName", "leaveC")).get("data")).get("id"));
        assertNotNull(a);
        assertNotNull(b);

        // 批量删除 {ids}
        assertOk(call("processSurrogate/remove", args("ids", Arrays.asList(a, b))));
        assertNull(extRepo.findSurrogateById(a));
        assertNull(extRepo.findSurrogateById(b));

        // 行内删除：前端同样走 {ids}，长度 1
        assertOk(call("processSurrogate/remove", args("ids", Arrays.asList(c))));
        assertNull(extRepo.findSurrogateById(c));

        // 单 {id} 兼容形态回归
        Long d = toLong(((Map<String, Object>) call("processSurrogate/save",
                args("operator", "zhangsan", "surrogate", "lisiE", "processName", "leaveD")).get("data")).get("id"));
        assertOk(call("processSurrogate/remove", args("id", d)));
        assertNull(extRepo.findSurrogateById(d));
    }

    /** issues/95 §5②：{ids}/​{id} 缺失或空数组一律报错，禁止静默成功（六语言统一口径）。 */
    @Test
    public void testRemoveEmptyIdsRejected() {
        Map<String, Object> r = call("processSurrogate/remove", args("ids", new ArrayList<Long>()));
        assertEquals(Integer.valueOf(99999999), r.get("code"));
        assertEquals("id 缺失或非法", r.get("msg"));
        // 两者皆缺：此前 Java 走 toLong(null) → setLong 拆箱 NPE
        r = call("processSurrogate/remove", args("surrogate", "lisi"));
        assertEquals(Integer.valueOf(99999999), r.get("code"));
        r = call("processSurrogate/remove", args("ids", Arrays.asList(123L, null)));
        assertEquals(Integer.valueOf(99999999), r.get("code"));

        r = call("processDefine/remove", args("ids", new ArrayList<Long>()));
        assertEquals(Integer.valueOf(99999999), r.get("code"));
        r = call("processDesign/remove", args("ids", new ArrayList<Long>()));
        assertEquals(Integer.valueOf(99999999), r.get("code"));
        r = call("processDefine/upAndDown", args("ids", new ArrayList<Long>(), "opType", 0));
        assertEquals(Integer.valueOf(99999999), r.get("code"));
    }

    // ═══ issues/96 §4B：入口批量参数形态矩阵（4 action × 4 态）═══
    //
    // mldong 前端批量删除一律发 {ids:[...]}（IdsParam 惯例），引擎历史上只认单数 {id}；
    // issues/95 已把 processSurrogate/remove · processDesign/remove · processDefine/remove ·
    // processDefine/upAndDown 四个 action 收敛到 JeeflowFacade#idListArgs(Map)。
    //
    // 本矩阵钉的是「前端实际发送的载荷形状」，不是「实现恰好接受的形状」（issues/96 §3 根因 1）——
    // 既有套件全发单数 {id}，引擎完全不认 {ids} 也照样全绿，所以才放过了 issues/95。
    //
    // 四态口径（每个 action 各测一遍）：
    //   态 1 {ids:[a,b]}          批量正向：两个真实 id → 成功，且事后回查两条都取不到（不能只断 code）
    //   态 2 {id:c}               旧单值形态回归：仍成功（防修坏）
    //   态 3 {ids:[]}             空数组必须报错，禁止静默成功
    //   态 4 {ids:[""]} / {ids:[d,null]}  空串/含 null 必须报错，且 d 不被部分删除（校验先于写入）
    //
    // msg 一律用 contains 断言：各语言实现可能在「id 缺失或非法」后追加诊断后缀。
    // 启停 action 的「事后回查」不是取不到，而是 state 真的按序变了。

    /** issues/96 §4B：{@code processSurrogate/remove} 入口形态四态（本批 issues/95 才接入 idListArgs 的 action）。 */
    @Test
    public void testSurrogateRemoveParamShapeMatrix() {
        Long a = newSurrogate("shapeA");
        Long b = newSurrogate("shapeB");

        // 态 1：{ids:[a,b]} —— 前端「我的委托」勾选批量删除的真实载荷
        assertOk(call("processSurrogate/remove", args("ids", Arrays.asList(a, b))));
        assertNull("批量删除后 a 应取不到", extRepo.findSurrogateById(a));
        assertNull("批量删除后 b 应取不到", extRepo.findSurrogateById(b));
        assertGoneViaFacade("processSurrogate/detail", a);
        assertGoneViaFacade("processSurrogate/detail", b);

        // 态 2：{id:c} —— 旧单值形态（移动端仍发这个），必须继续可用
        Long c = newSurrogate("shapeC");
        assertOk(call("processSurrogate/remove", args("id", c)));
        assertNull("单 id 删除后 c 应取不到", extRepo.findSurrogateById(c));
        assertGoneViaFacade("processSurrogate/detail", c);

        // 态 3：{ids:[]} —— 空数组禁止静默成功
        assertIdsRejected("processSurrogate/remove", args("ids", new ArrayList<Long>()));

        // 态 4：{ids:[""]} 与 {ids:[d,null]} —— 非法值报错，且合法元素不被部分删除
        Long d = newSurrogate("shapeD");
        assertIdsRejected("processSurrogate/remove", args("ids", Arrays.asList("")));
        assertIdsRejected("processSurrogate/remove", args("ids", Arrays.asList(d, null)));
        assertNotNull("校验应先于写入，d 不应被部分删除", extRepo.findSurrogateById(d));
        assertOk(call("processSurrogate/remove", args("ids", Arrays.asList(d))));
    }

    /** issues/96 §4B：{@code processDesign/remove} 入口形态四态。 */
    @Test
    public void testDesignRemoveParamShapeMatrix() {
        Long a = newDesign("shapeDesignA");
        Long b = newDesign("shapeDesignB");

        // 态 1
        assertOk(call("processDesign/remove", args("ids", Arrays.asList(a, b))));
        assertNull("批量删除后设计 a 应取不到", extRepo.findDesignById(a));
        assertNull("批量删除后设计 b 应取不到", extRepo.findDesignById(b));
        assertGoneViaFacade("processDesign/detail", a);
        assertGoneViaFacade("processDesign/detail", b);

        // 态 2
        Long c = newDesign("shapeDesignC");
        assertOk(call("processDesign/remove", args("id", c)));
        assertNull("单 id 删除后设计 c 应取不到", extRepo.findDesignById(c));
        assertGoneViaFacade("processDesign/detail", c);

        // 态 3
        assertIdsRejected("processDesign/remove", args("ids", new ArrayList<Long>()));

        // 态 4
        Long d = newDesign("shapeDesignD");
        assertIdsRejected("processDesign/remove", args("ids", Arrays.asList("")));
        assertIdsRejected("processDesign/remove", args("ids", Arrays.asList(d, null)));
        assertNotNull("校验应先于写入，设计 d 不应被部分删除", extRepo.findDesignById(d));
        assertOk(call("processDesign/remove", args("ids", Arrays.asList(d))));
    }

    /** issues/96 §4B：{@code processDefine/remove} 入口形态四态（此前连 Java 自己都没有 {ids} 用例）。 */
    @Test
    public void testDefineRemoveParamShapeMatrix() {
        Long a = deployDefine("shapeDefineA");
        Long b = deployDefine("shapeDefineB");

        // 态 1
        assertOk(call("processDefine/remove", args("ids", Arrays.asList(a, b))));
        assertNull("批量删除后定义 a 应取不到", rawRepo.findDefineById(a));
        assertNull("批量删除后定义 b 应取不到", rawRepo.findDefineById(b));
        assertGoneViaFacade("processDefine/detail", a);
        assertGoneViaFacade("processDefine/detail", b);
        assertEquals("按名也应取不到定义 a", Integer.valueOf(99999999),
                call("processDefine/getLastByName", args("processDefineName", "shapeDefineA")).get("code"));
        assertEquals("按名也应取不到定义 b", Integer.valueOf(99999999),
                call("processDefine/getLastByName", args("processDefineName", "shapeDefineB")).get("code"));

        // 态 2
        Long c = deployDefine("shapeDefineC");
        assertOk(call("processDefine/remove", args("id", c)));
        assertNull("单 id 删除后定义 c 应取不到", rawRepo.findDefineById(c));
        assertGoneViaFacade("processDefine/detail", c);

        // 态 3
        assertIdsRejected("processDefine/remove", args("ids", new ArrayList<Long>()));

        // 态 4
        Long d = deployDefine("shapeDefineD");
        assertIdsRejected("processDefine/remove", args("ids", Arrays.asList("")));
        assertIdsRejected("processDefine/remove", args("ids", Arrays.asList(d, null)));
        assertNotNull("校验应先于写入，定义 d 不应被部分删除", rawRepo.findDefineById(d));
        assertOk(call("processDefine/remove", args("ids", Arrays.asList(d))));
    }

    /** issues/96 §4B：{@code processDefine/upAndDown} 入口形态四态。
     *  ⚠️ Java 现实现里 state 校验排在 ids 之前（defineUpAndDown 先 {@code Integer.parseInt(stateObj.toString())}），
     *  不带 state 时态 3/4 会被 state 的 NPE 抢先满足（返回「Cannot invoke "Object.toString()" because "stateObj" is null」），
     *  测的就不是 ids 校验了 —— 所以本用例每一态都带合法 state。
     *  该顺序属于已挂号未改的遗留偏差（issues/95/96），本轮不动它、只绕开它。 */
    @Test
    public void testDefineUpAndDownParamShapeMatrix() {
        Long a = deployDefine("shapeUpDownA");
        Long b = deployDefine("shapeUpDownB");

        // 态 1：{ids:[a,b], opType:0} —— boot3 前端批量停用的真实载荷
        assertOk(call("processDefine/upAndDown", args("ids", Arrays.asList(a, b), "opType", 0)));
        assertEquals("批量停用后 a 的 state 应为 0", Integer.valueOf(0), rawRepo.findDefineById(a).getState());
        assertEquals("批量停用后 b 的 state 应为 0", Integer.valueOf(0), rawRepo.findDefineById(b).getState());

        // 态 2：{id:c, state:0/1} —— 旧单值形态（state 键是 issues/28 前的写法），必须继续可用且真的生效
        Long c = deployDefine("shapeUpDownC");
        assertEquals("新发布定义初始 state 应为 1", Integer.valueOf(1), rawRepo.findDefineById(c).getState());
        assertOk(call("processDefine/upAndDown", args("id", c, "state", 0)));
        assertEquals("单 id 停用后 c 的 state 应为 0", Integer.valueOf(0), rawRepo.findDefineById(c).getState());
        assertOk(call("processDefine/upAndDown", args("id", c, "state", 1)));
        assertEquals("单 id 启用后 c 的 state 应为 1", Integer.valueOf(1), rawRepo.findDefineById(c).getState());

        // 态 3：{ids:[], opType:0} —— 带合法 state，报错只能来自 ids 校验
        assertIdsRejected("processDefine/upAndDown", args("ids", new ArrayList<Long>(), "opType", 0));

        // 态 4：{ids:[""], opType:0} 与 {ids:[d,null], opType:0}
        Long d = deployDefine("shapeUpDownD");
        assertIdsRejected("processDefine/upAndDown", args("ids", Arrays.asList(""), "opType", 0));
        assertIdsRejected("processDefine/upAndDown", args("ids", Arrays.asList(d, null), "opType", 0));
        assertEquals("校验应先于写入，d 的 state 不应被部分改写", Integer.valueOf(1), rawRepo.findDefineById(d).getState());
        assertOk(call("processDefine/upAndDown", args("ids", Arrays.asList(d), "opType", 0)));
        assertEquals("清理后 d 的 state 应为 0", Integer.valueOf(0), rawRepo.findDefineById(d).getState());
    }

    /** 造数（issues/96 §4B）：一条带时间窗的委托，返回 surrogateId */
    private Long newSurrogate(String processName) {
        Map<String, Object> r = call("processSurrogate/save", args(
                "operator", "shapeop", "surrogate", "shapelisi", "processName", processName,
                "startTime", "2026-08-01 00:00:00", "endTime", "2026-08-31 23:59:59", "enabled", 1));
        assertOk(r);
        Long id = toLong(((Map<String, Object>) r.get("data")).get("id"));
        assertNotNull("造数应返回委托 id: " + r, id);
        return id;
    }

    /** 造数（issues/96 §4B）：一条流程设计，返回 designId */
    private Long newDesign(String name) {
        Map<String, Object> r = call("processDesign/save", args(
                "name", name, "displayName", "形态" + name, "type", "approval", "operator", "user1"));
        assertOk(r);
        Long id = toLong(((Map<String, Object>) r.get("data")).get("id"));
        assertNotNull("造数应返回设计 id: " + r, id);
        return id;
    }

    /** 造数（issues/96 §4B）：save 设计 → updateDefine → deploy，返回 defineId，并用 getLastByName 复核可按名取到。 */
    private Long deployDefine(String name) {
        Long designId = newDesign(name);
        assertOk(call("processDesign/updateDefine", args(
                "processDesignId", designId, "operator", "user1",
                "name", name, "displayName", "形态" + name, "type", "approval",
                "nodes", new ArrayList<>(), "edges", new ArrayList<>())));
        Map<String, Object> dr = call("processDesign/deploy", args("id", designId, "operator", "user1"));
        assertOk(dr);
        Long defineId = toLong(((Map<String, Object>) dr.get("data")).get("processDefineId"));
        assertNotNull("发布应返回 processDefineId: " + dr, defineId);
        Map<String, Object> last = call("processDefine/getLastByName", args("processDefineName", name));
        assertOk(last);
        assertEquals("getLastByName 应与发布返回的 defineId 一致", defineId,
                toLong(((Map<String, Object>) last.get("data")).get("id")));
        return defineId;
    }

    /** 断言（issues/96 §4B）：批量/启停 action 的负向态 —— code=99999999 且 msg 含「id 缺失或非法」。 */
    private void assertIdsRejected(String action, Map<String, Object> payload) {
        Map<String, Object> r = call(action, payload);
        assertEquals(action + " 负向入参应报错（禁止静默成功）: " + r, Integer.valueOf(99999999), r.get("code"));
        assertTrue(action + " msg 应含「id 缺失或非法」: " + r,
                String.valueOf(r.get("msg")).contains("id 缺失或非法"));
    }

    /** 断言（issues/96 §4B）：删除后从门面这一侧回查也取不到（矩阵要求「不能只断 remove 自己的 code」）。 */
    private void assertGoneViaFacade(String detailAction, Long id) {
        Map<String, Object> r = call(detailAction, args("id", id));
        assertEquals(detailAction + " 删除后应取不到 id=" + id + ": " + r,
                Integer.valueOf(99999999), r.get("code"));
    }

    /** issues/82-12：委托生效判断——时间窗 startTime/endTime + enabled 过滤（五语言基准）。
     *  5 条委托各对应一个时间态：在窗 / 未到 / 已过 / 无窗(enabled=0) / 无窗(enabled=1)。
     *  每条查询只命中其中一条（processName 精确区分），断言结果与命中集唯一 → 不依赖仓储返回顺序。 */
    @Test
    public void testSurrogateEffectiveWindowAndEnabled() throws Exception {
        String op = "winop";
        // A 在窗（2026-08-01 ~ 08-31）
        assertOk(call("processSurrogate/save", args("operator", op, "surrogate", "sA", "processName", "winA",
                "startTime", "2026-08-01 00:00:00", "endTime", "2026-08-31 23:59:59", "enabled", 1)));
        // B 未到（2026-09-01 起）
        assertOk(call("processSurrogate/save", args("operator", op, "surrogate", "sB", "processName", "winB",
                "startTime", "2026-09-01 00:00:00", "enabled", 1)));
        // C 已过（07-31 止）
        assertOk(call("processSurrogate/save", args("operator", op, "surrogate", "sC", "processName", "winC",
                "endTime", "2026-07-31 23:59:59", "enabled", 1)));
        // D 无窗但停用（enabled=0）
        assertOk(call("processSurrogate/save", args("operator", op, "surrogate", "sD", "processName", "winD", "enabled", 0)));
        // E 无窗且启用（enabled=1）
        assertOk(call("processSurrogate/save", args("operator", op, "surrogate", "sE", "processName", "winE", "enabled", 1)));

        java.time.LocalDateTime at = java.time.LocalDateTime.of(2026, 8, 15, 12, 0, 0);
        assertEquals("在窗委托应生效", "sA", extRepo.getSurrogate(op, "winA", at).getSurrogate());
        assertEquals("未到窗委托不应生效", null, extRepo.getSurrogate(op, "winB", at));
        assertEquals("已过窗委托不应生效", null, extRepo.getSurrogate(op, "winC", at));
        assertEquals("enabled=0 不应生效", null, extRepo.getSurrogate(op, "winD", at));
        assertEquals("无窗启用委托应生效（NULL=不限）", "sE", extRepo.getSurrogate(op, "winE", at).getSurrogate());
        // 负向：查不存在的流程 → null
        assertEquals("无匹配流程应返回 null", null, extRepo.getSurrogate(op, "winZ", at));
        // 换时间验证窗口边界随时间变化：B 在 9 月生效、A 在 9 月失效
        java.time.LocalDateTime atSep = java.time.LocalDateTime.of(2026, 9, 15, 12, 0, 0);
        assertEquals("9 月：B 进入窗口应生效", "sB", extRepo.getSurrogate(op, "winB", atSep).getSurrogate());
        assertEquals("9 月：A 已出窗口不应生效", null, extRepo.getSurrogate(op, "winA", atSep));
    }

    /** issues/82-7：委托分页 m_IN_processName / m_EQ_enabled 查询形态（对齐 mldong 前端委托页搜索） */
    @Test
    public void testSurrogatePageInAndEqConditions() {
        // 3 条委托：leave(启用) / overtime(启用) / sick(停用)
        Map<String, Object> r = call("processSurrogate/save", args(
                "operator", "zhangsan", "surrogate", "lisi", "processName", "leave", "enabled", 1));
        assertOk(r);
        r = call("processSurrogate/save", args(
                "operator", "zhangsan", "surrogate", "wangwu", "processName", "overtime", "enabled", 1));
        assertOk(r);
        r = call("processSurrogate/save", args(
                "operator", "zhangsan", "surrogate", "zhaoliu", "processName", "sick", "enabled", 0));
        assertOk(r);

        // 无过滤：3 条
        r = call("processSurrogate/page", args("operator", "zhangsan"));
        assertOk(r);
        assertEquals(Integer.valueOf(3), ((Map<String, Object>) r.get("data")).get("recordCount"));

        // m_IN_processName：IN 列表命中 2 条
        r = call("processSurrogate/page", args("operator", "zhangsan",
                "m_IN_processName", Arrays.asList("leave", "overtime")));
        assertOk(r);
        Map<String, Object> inData = (Map<String, Object>) r.get("data");
        assertEquals(Integer.valueOf(2), inData.get("recordCount"));
        List<String> names = new ArrayList<>();
        for (Object row : (List<?>) inData.get("rows")) {
            names.add((String) ((Map<String, Object>) row).get("processName"));
        }
        assertTrue("IN 应命中 leave+overtime: " + names, names.contains("leave") && names.contains("overtime"));

        // m_EQ_enabled：启用过滤命中 2 条
        r = call("processSurrogate/page", args("operator", "zhangsan", "m_EQ_enabled", 1));
        assertOk(r);
        assertEquals(Integer.valueOf(2), ((Map<String, Object>) r.get("data")).get("recordCount"));

        // m_IN + m_EQ 组合：leave/overtime 中仅启用 → 仍 2 条；换成 sick/overtime → 1 条
        r = call("processSurrogate/page", args("operator", "zhangsan",
                "m_IN_processName", Arrays.asList("sick", "overtime"), "m_EQ_enabled", 1));
        assertOk(r);
        Map<String, Object> comboData = (Map<String, Object>) r.get("data");
        assertEquals(Integer.valueOf(1), comboData.get("recordCount"));
        assertEquals("overtime", ((Map<String, Object>) ((List<?>) comboData.get("rows")).get(0)).get("processName"));

        // 负向：IN 全不命中 / EQ 无匹配 → 0 条
        r = call("processSurrogate/page", args("operator", "zhangsan",
                "m_IN_processName", Arrays.asList("none1", "none2")));
        assertOk(r);
        assertEquals(Integer.valueOf(0), ((Map<String, Object>) r.get("data")).get("recordCount"));
        r = call("processSurrogate/page", args("operator", "zhangsan", "m_EQ_enabled", 2));
        assertOk(r);
        assertEquals(Integer.valueOf(0), ((Map<String, Object>) r.get("data")).get("recordCount"));
    }

    // ═══ 视图端点（v1.2.0） ═══

    @Test
    public void testViewEndpoints() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("01-simple.json");
        // getLastByName
        Map<String, Object> r = call("processDefine/getLastByName", args("processDefineName", "01-simple"));
        assertOk(r);
        Map<String, Object> data = (Map<String, Object>) r.get("data");
        assertEquals("01-simple", data.get("name"));

        // startAndExecute 后：approvalRecord / highLight / getAssigneeTextData / latest / jumpAble / detail
        r = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "zhangsan"));
        Long instanceId = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));

        r = call("processInstance/approvalRecord", args("id", instanceId));
        assertOk(r);
        List<?> records = (List<?>) r.get("data");
        assertEquals(2, records.size()); // apply 已完成 + task1 进行中（全部任务记录，对齐 boot2）

        r = call("processInstance/highLight", args("id", instanceId));
        assertOk(r);
        Map<String, Object> hl = (Map<String, Object>) r.get("data");
        assertTrue(((List<?>) hl.get("activeNodeNames")).contains("task1"));
        assertTrue(((List<?>) hl.get("historyNodeNames")).contains("apply"));

        r = call("processInstance/getAssigneeTextData", args("id", instanceId));
        assertOk(r);
        List<?> texts = (List<?>) r.get("data");
        assertEquals(1, texts.size()); // task1 参与者 leader

        r = call("processTask/latest", args("processInstanceId", instanceId));
        assertOk(r);
        assertEquals("task1", ((Map<String, Object>) r.get("data")).get("taskName"));

        List<com.mldong.jeeflow.domain.ProcessTask> doing = rawRepo.findDoingTasks(instanceId, null);
        r = call("processTask/detail", args("id", doing.get(0).getTaskId(), "operator", "leader"));
        assertOk(r);
        data = (Map<String, Object>) r.get("data");
        assertEquals("task1", data.get("taskName"));
        assertEquals(Boolean.TRUE, data.get("executable"));
        assertNotNull(data.get("taskModel"));
        // issues/62：taskModel 补 form/ext（字段权限）
        @SuppressWarnings("unchecked")
        Map<String, Object> tm = (Map<String, Object>) data.get("taskModel");
        assertEquals("leave-form", tm.get("form"));
        Map<String, Object> tmExt = (Map<String, Object>) tm.get("ext");
        assertEquals(Integer.valueOf(1), tmExt.get("PERMISSION_f_leaveType"));
        assertEquals(Integer.valueOf(2), tmExt.get("PERMISSION_days"));

        // 抄送：创建 + 我的抄送 + 已读
        r = call("processInstance/createCCInstance",
                args("processInstanceId", instanceId, "operator", "zhangsan", "actorIds", Arrays.asList("lisi", "wangwu")));
        assertOk(r);
        r = call("processInstance/ccList", args("operator", "lisi"));
        assertOk(r);
        assertEquals(Integer.valueOf(1), ((Map<String, Object>) r.get("data")).get("recordCount"));
        r = call("processInstance/updateCCStatus", args("processInstanceId", instanceId, "operator", "lisi"));
        assertOk(r);

        // jumpAbleTaskNameList（apply 完成后 task1 未办——done 只有 apply，非会签）
        r = call("processTask/jumpAbleTaskNameList", args("processInstanceId", instanceId));
        assertOk(r);
        assertEquals(1, ((List<?>) r.get("data")).size());

        // 加签/转交
        r = call("processTask/addCandidate", args("processTaskId", doing.get(0).getTaskId(), "actorIds", Arrays.asList("zhaoliu")));
        assertOk(r);
        assertTrue(rawRepo.findTaskActors(doing.get(0).getTaskId()).contains("zhaoliu"));
        r = call("processTask/surrogate", args("processTaskId", doing.get(0).getTaskId(), "actorIds", Arrays.asList("sunqi")));
        assertOk(r);
        assertTrue(rawRepo.findTaskActors(doing.get(0).getTaskId()).contains("sunqi"));

        // candidatePage：无模型候选（01-simple 无 candidateUsers 配置）→ 未配置用户搜索钩子报错
        r = call("processTask/candidatePage", args("processTaskId", doing.get(0).getTaskId()));
        assertEquals(Integer.valueOf(99999999), r.get("code"));
    }

    // ═══ 错误路径 ═══

    @Test
    public void testUnknownActionAndMissingExt() {
        Map<String, Object> r = call("foo/bar", args());
        assertEquals(Integer.valueOf(99999999), r.get("code"));

        // 未配置扩展仓储时设计 action 报错
        JeeflowFacade facadeNoExt = new JeeflowFacade(
                new JeeflowEngineImpl(), repo, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> r2 = (Map<String, Object>) facadeNoExt.flow("processDesign/page", args());
        assertEquals(Integer.valueOf(99999999), r2.get("code"));
    }

    /** bizData mock 读取器（同 MetaTableReader 契约，public 供 facade 反射调用） */
    public static class MockMetaTableReader {
        public Map<String, Object> readByProcessInstance(String tableName, Object processInstanceId) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tableName", tableName);
            m.put("processInstanceId", processInstanceId);
            m.put("title", "业务数据");
            return m;
        }
    }

    private static Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).longValue();
        return Long.parseLong(val.toString());
    }

    // ═══ highLight 决策分支表达式过滤（issues/06）═══

    @Test
    public void testHighLightFiltersDecisionBranch() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("03-decision-expr.json");
        // amount=500 → 走「amount <= 1000」分支（task3），task2 分支未执行
        Map<String, Object> r = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "zhangsan", "amount", 500));
        assertOk(r);
        Long instanceId = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));

        // 推进：task1(leader) → decision → task3(director) → end
        List<com.mldong.jeeflow.domain.ProcessTask> doing = rawRepo.findDoingTasks(instanceId, null);
        for (com.mldong.jeeflow.domain.ProcessTask t : doing) {
            if ("task1".equals(t.getTaskName())) {
                rawRepo.addTaskActor(t.getTaskId(), java.util.Arrays.asList("leader"));
                call("processTask/execute", args("processTaskId", t.getTaskId(), "operator", "leader", "submitType", 1));
            }
        }
        doing = rawRepo.findDoingTasks(instanceId, null);
        for (com.mldong.jeeflow.domain.ProcessTask t : doing) {
            if ("task3".equals(t.getTaskName())) {
                rawRepo.addTaskActor(t.getTaskId(), java.util.Arrays.asList("director"));
                call("processTask/execute", args("processTaskId", t.getTaskId(), "operator", "director", "submitType", 1));
            }
        }

        r = call("processInstance/highLight", args("id", instanceId));
        assertOk(r);
        Map<String, Object> hl = (Map<String, Object>) r.get("data");
        @SuppressWarnings("unchecked")
        List<String> historyEdges = (List<String>) hl.get("historyEdgeNames");
        @SuppressWarnings("unchecked")
        List<String> historyNodes = (List<String>) hl.get("historyNodeNames");
        // 走过的分支：e4（amount<=1000 → task3）+ e6（task3→end）
        assertTrue("应包含走过的边 e4/e6: " + historyEdges, historyEdges.contains("e4") && historyEdges.contains("e6"));
        // 未走分支：e3（amount>1000 → task2）与 e5（task2→end）不得出现
        assertFalse("未走分支 e3 不应高亮: " + historyEdges, historyEdges.contains("e3"));
        assertFalse("未走分支 e5 不应高亮: " + historyEdges, historyEdges.contains("e5"));
        assertFalse("未走节点 task2 不应高亮: " + historyNodes, historyNodes.contains("task2"));
        assertTrue("应包含走过节点 task3: " + historyNodes, historyNodes.contains("task3"));
    }


    // ═══ highLight nodeProgress 成员进度回显（issues/41，四语言对齐）═══

    @Test
    public void testHighLightNodeProgress() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("06-countersign-sequential.json");
        Map<String, Object> r = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "user1"));
        assertOk(r);
        Long instanceId = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));

        r = call("processInstance/highLight", args("id", instanceId));
        assertOk(r);
        @SuppressWarnings("unchecked")
        Map<String, Object> np = (Map<String, Object>) ((Map<String, Object>) r.get("data")).get("nodeProgress");
        // 历史节点 apply：发起人 done + name 经 IUserProvider 解析
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> applyMembers = (List<Map<String, Object>>) ((Map<String, Object>) np.get("apply")).get("members");
        assertEquals("user1", applyMembers.get(0).get("id"));
        assertEquals(Boolean.TRUE, applyMembers.get(0).get("done"));
        assertEquals("用户user1", applyMembers.get(0).get("name"));
        // 顺序会签进行中：type=SEQUENTIAL、第一位 active、第二位无标记
        @SuppressWarnings("unchecked")
        Map<String, Object> task1 = (Map<String, Object>) np.get("task1");
        assertEquals("SEQUENTIAL", task1.get("type"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> m1 = (List<Map<String, Object>>) task1.get("members");
        assertEquals("userA", m1.get(0).get("id"));
        assertEquals(Boolean.TRUE, m1.get(0).get("active"));
        assertEquals("用户userA", m1.get(0).get("name"));
        assertEquals("userB", m1.get(1).get("id"));
        assertNull("userB 不应有 done 标记", m1.get(1).get("done"));
        assertNull("userB 不应有 active 标记", m1.get(1).get("active"));
        // 推进会签：userA done → userB active
        List<com.mldong.jeeflow.domain.ProcessTask> doing = rawRepo.findDoingTasks(instanceId, null);
        for (com.mldong.jeeflow.domain.ProcessTask t : doing) {
            if ("task1".equals(t.getTaskName())) {
                rawRepo.addTaskActor(t.getTaskId(), java.util.Arrays.asList("userA"));
                call("processTask/execute", args("processTaskId", t.getTaskId(), "operator", "userA", "submitType", 1));
            }
        }
        r = call("processInstance/highLight", args("id", instanceId));
        assertOk(r);
        @SuppressWarnings("unchecked")
        Map<String, Object> np2 = (Map<String, Object>) ((Map<String, Object>) r.get("data")).get("nodeProgress");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> m2 = (List<Map<String, Object>>) ((Map<String, Object>) np2.get("task1")).get("members");
        assertEquals(Boolean.TRUE, m2.get(0).get("done"));
        assertEquals(Boolean.TRUE, m2.get(1).get("active"));
        // 全部完成 → 全部 done
        doing = rawRepo.findDoingTasks(instanceId, null);
        for (com.mldong.jeeflow.domain.ProcessTask t : doing) {
            if ("task1".equals(t.getTaskName())) {
                rawRepo.addTaskActor(t.getTaskId(), java.util.Arrays.asList("userB"));
                call("processTask/execute", args("processTaskId", t.getTaskId(), "operator", "userB", "submitType", 1));
            }
        }
        r = call("processInstance/highLight", args("id", instanceId));
        assertOk(r);
        @SuppressWarnings("unchecked")
        Map<String, Object> np3 = (Map<String, Object>) ((Map<String, Object>) r.get("data")).get("nodeProgress");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> m3 = (List<Map<String, Object>>) ((Map<String, Object>) np3.get("task1")).get("members");
        assertEquals(Boolean.TRUE, m3.get(0).get("done"));
        assertEquals(Boolean.TRUE, m3.get(1).get("done"));
        assertNull("完成后不应有 active", m3.get(1).get("active"));
    }

    // ═══ 三个 detail 返回 jsonObject（issues/05-1）═══

    @Test
    public void testDetailJsonObject() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("01-simple.json");
        Map<String, Object> r = call("processDefine/detail", args("id", def.getId()));
        assertOk(r);
        assertNotNull(((Map<String, Object>) r.get("data")).get("jsonObject"));

        r = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "zhangsan"));
        assertOk(r);
        Long instanceId = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));
        r = call("processInstance/detail", args("id", instanceId));
        assertOk(r);
        assertNotNull(((Map<String, Object>) r.get("data")).get("jsonObject"));
        // issues/05-4：activeTaskList 仅 DOING 任务 + 任务行 ext.isFirstTaskNode
        Map<String, Object> instData = (Map<String, Object>) r.get("data");
        List<?> activeList = (List<?>) instData.get("activeTaskList");
        assertEquals(1, activeList.size()); // apply 已自动完成，剩余 task1（DOING）
        Map<String, Object> task1Row = (Map<String, Object>) activeList.get(0);
        assertEquals("task1", task1Row.get("taskName"));
        @SuppressWarnings("unchecked")
        Map<String, Object> ext = (Map<String, Object>) task1Row.get("ext");
        assertNotNull(ext);
        // task1 不是首个任务节点（apply 才是），isFirstTaskNode 应为 false
        assertEquals(Boolean.FALSE, ext.get("isFirstTaskNode"));
        List<?> allTasks = (List<?>) instData.get("tasks");
        assertEquals(2, allTasks.size()); // apply + task1 全量

        List<com.mldong.jeeflow.domain.ProcessTask> doing = rawRepo.findDoingTasks(instanceId, null);
        r = call("processTask/detail", args("id", doing.get(0).getTaskId(), "operator", "zhangsan"));
        assertOk(r);
        assertNotNull(((Map<String, Object>) r.get("data")).get("jsonObject"));
    }

    // ═══ taskDetail performType/taskType 出口数字契约（issues/78）═══

    @Test
    public void testTaskDetailPerformTypeNumeric() throws Exception {
        // 普通流程：task1 performType=0、taskType=0（出口应为数字 0，非 null/非枚举 name）
        ProcessInstance.ProcessDefine simple = registerFlow("01-simple.json");
        Map<String, Object> s1 = call("processInstance/startAndExecute",
                args("processDefineId", simple.getId(), "operator", "zhangsan"));
        assertOk(s1);
        Long simpleInstId = toLong(((Map<String, Object>) s1.get("data")).get("processInstanceId"));
        Map<String, Object> d1 = call("processTask/detail",
                args("id", doingTaskId(simpleInstId, "task1"), "operator", "leader"));
        assertOk(d1);
        Map<String, Object> vo1 = (Map<String, Object>) d1.get("data");
        assertEquals("普通任务 performType 应为数字 0", Integer.valueOf(0), vo1.get("performType"));
        assertEquals("普通任务 taskType 应为数字 0", Integer.valueOf(0), vo1.get("taskType"));

        // 会签流程：task1 performType=1（出口应为数字 1，非字符串 "COUNTERSIGN"）
        ProcessInstance.ProcessDefine seq = registerFlow("06-countersign-sequential.json");
        Map<String, Object> s2 = call("processInstance/startAndExecute",
                args("processDefineId", seq.getId(), "operator", "user1"));
        assertOk(s2);
        Long seqInstId = toLong(((Map<String, Object>) s2.get("data")).get("processInstanceId"));
        Map<String, Object> d2 = call("processTask/detail",
                args("id", doingTaskId(seqInstId, "task1"), "operator", "userA"));
        assertOk(d2);
        Map<String, Object> vo2 = (Map<String, Object>) d2.get("data");
        assertEquals("会签任务 performType 应为数字 1", Integer.valueOf(1), vo2.get("performType"));
        assertEquals("会签任务 taskType 应为数字 0", Integer.valueOf(0), vo2.get("taskType"));
    }

    /**
     * issues/121 P1：出口 ext.isFirstTaskNode 的读时兜底——**行上值优先，缺键才回退现算**。
     * 差值只在**已办结的历史行**上看得见：现算带"仅进行中"判定⇒历史行恒 false，
     * 而行上值是真 true；血缘版回退要读这条历史行决定参与者，读成 false 就会把首节点回退派错人。
     */
    @Test
    public void testIsFirstTaskNodePrefersPersistedRow() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("02-multi-task.json");
        // 用引擎发起（不是 startAndExecute——它会把 apply 也一并办结，就拿不到"进行中的首节点行"了）
        Long iid = engine.startProcessInstanceById(def.getId(), "applicant", FlowData.create()).getInstanceId();
        Long applyId = doingTaskId(iid, "apply");
        assertNotNull("应有进行中的 apply 行", applyId);
        assertEquals("进行中的首节点行读行上值", Boolean.TRUE, extOfRow(iid, "apply").get("isFirstTaskNode"));

        ProcessTask apply = rawRepo.findTaskById(applyId);
        rawRepo.addTaskActor(applyId, Arrays.asList("applicant"));
        apply.getActorIds().add("applicant");
        engine.executeProcessTask(applyId, "applicant", FlowData.create()
                .set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));

        assertEquals("历史行仍应读到 true（标记随行存活）", Boolean.TRUE,
                extOfRow(iid, "apply").get("isFirstTaskNode"));

        // 存量行形状（引擎尚未写标记时落的老数据）：抹掉行上键 ⇒ 只能回退现算 ⇒ 历史行为 false
        rawRepo.findTaskById(applyId).getVariables().remove(FlowConst.IS_FIRST_TASK_NODE);
        assertEquals("缺键的历史行回退现算（这格同时说明为何引擎不能靠现算）", Boolean.FALSE,
                extOfRow(iid, "apply").get("isFirstTaskNode"));
    }

    /** 从 processInstance/detail 的 tasks 里按任务名取该行 ext（仓储/出口读回，不用内存聚合根） */
    private Map<String, Object> extOfRow(Long instanceId, String taskName) {
        Map<String, Object> r = call("processInstance/detail", args("id", instanceId));
        assertOk(r);
        Map<String, Object> data = (Map<String, Object>) r.get("data");
        for (Object o : (List<?>) data.get("tasks")) {
            Map<String, Object> vo = (Map<String, Object>) o;
            if (taskName.equals(vo.get("taskName"))) return (Map<String, Object>) vo.get("ext");
        }
        throw new AssertionError("detail.tasks 里应有 " + taskName + " 行");
    }

    /** 找指定实例下进行中、名为 name 的任务 id */
    private Long doingTaskId(Long instanceId, String name) {
        for (com.mldong.jeeflow.domain.ProcessTask t : rawRepo.findDoingTasks(instanceId, null)) {
            if (name.equals(t.getTaskName())) return t.getTaskId();
        }
        return null;
    }

    /** 找指定实例下名为 name、参与者含 actor 的进行中任务 id（会签每成员一任务，同 taskName 需按人定位） */
    private Long doingTaskIdByActor(Long instanceId, String name, String actor) {
        for (com.mldong.jeeflow.domain.ProcessTask t : rawRepo.findDoingTasks(instanceId, null)) {
            if (name.equals(t.getTaskName()) && t.getActorIds() != null
                    && t.getActorIds().contains(actor)) {
                return t.getTaskId();
            }
        }
        return null;
    }

    /** 找指定实例下名为 name、参与者含 actor 的任务（任意状态，含已废弃） */
    private com.mldong.jeeflow.domain.ProcessTask taskByActor(Long instanceId, String name, String actor) {
        for (com.mldong.jeeflow.domain.ProcessTask t : rawRepo.findHistoryTasks(instanceId)) {
            if (name.equals(t.getTaskName()) && t.getActorIds() != null
                    && t.getActorIds().contains(actor)) {
                return t;
            }
        }
        return null;
    }

    // ═══ issues/82-5：task detail 任务级 ext.isFirstTaskNode（前端 detail.vue 双兜底）═══

    @Test
    public void testTaskDetailExtIsFirstTaskNode() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("01-simple.json");
        // startAndExecute 自动完成 apply → 剩 task1（DOING，非首节点）
        Map<String, Object> s1 = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "zhangsan"));
        assertOk(s1);
        Long instanceId = toLong(((Map<String, Object>) s1.get("data")).get("processInstanceId"));
        Long task1Id = doingTaskId(instanceId, "task1");
        Map<String, Object> d = call("processTask/detail", args("id", task1Id, "operator", "leader"));
        assertOk(d);
        Map<String, Object> vo = (Map<String, Object>) d.get("data");
        Map<String, Object> ext = (Map<String, Object>) vo.get("ext");
        assertNotNull("task detail 应含 ext 容器: " + vo.keySet(), ext);
        assertEquals("task1 非首任务节点，ext.isFirstTaskNode 应为 false",
                Boolean.FALSE, ext.get("isFirstTaskNode"));
    }

    @Test
    public void testTaskDetailExtIsFirstTaskNodeTrue() throws Exception {
        // 直接启动（不自动完成 apply）→ apply 为首任务节点且 DOING → ext.isFirstTaskNode=true
        ProcessInstance.ProcessDefine def = registerFlow("01-simple.json");
        com.mldong.jeeflow.domain.ProcessInstance inst =
                engine.startProcessInstanceById(def.getId(), "zhangsan", com.mldong.jeeflow.domain.FlowData.create());
        Long applyId = doingTaskId(inst.getInstanceId(), "apply");
        assertNotNull("apply 应为进行中任务", applyId);
        Map<String, Object> d = call("processTask/detail", args("id", applyId, "operator", "zhangsan"));
        assertOk(d);
        Map<String, Object> ext = (Map<String, Object>) ((Map<String, Object>) d.get("data")).get("ext");
        assertNotNull("task detail 应含 ext 容器", ext);
        assertEquals("apply 为首任务节点且 DOING，ext.isFirstTaskNode 应为 true",
                Boolean.TRUE, ext.get("isFirstTaskNode"));
    }

    // ═══ 按 id 查"记录不存在"负向（对齐 PHP 模板，detail 页最直接报错路径）═══

    @Test
    public void testDetailByIdNotFound() throws Exception {
        // processDefine/detail：流程定义不存在
        Map<String, Object> r = call("processDefine/detail", args("id", 99999L));
        assertEquals(Integer.valueOf(99999999), r.get("code"));
        assertTrue(String.valueOf(r.get("msg")).contains("流程定义不存在"));

        // processInstance/detail：流程实例不存在
        r = call("processInstance/detail", args("id", 99999L));
        assertEquals(Integer.valueOf(99999999), r.get("code"));
        assertTrue(String.valueOf(r.get("msg")).contains("流程实例不存在"));

        // processDesign/detail：流程设计不存在
        r = call("processDesign/detail", args("id", 99999L));
        assertEquals(Integer.valueOf(99999999), r.get("code"));
        assertTrue(String.valueOf(r.get("msg")).contains("流程设计不存在"));

        // processTask/detail：任务不存在
        r = call("processTask/detail", args("id", 99999L));
        assertEquals(Integer.valueOf(99999999), r.get("code"));
        assertTrue(String.valueOf(r.get("msg")).contains("任务不存在"));
    }

    /** issues/82 负向（对齐 Go TestSnowflakeIDPrecision / Node toId / issues/38 E9）：雪花 id 精度守卫。
     *  浮点型 id 超 2^53（JSON 解析 / 调用方 Number() 已丢精度）→ 显性报错，不 longValue() 静默截断；
     *  字符串雪花 id → 精确解析（无该定义 → 报"没有流程定义"，非崩溃）。
     *  注：Java 走 Jackson，整数 JSON 本为 Long 精确，故此路径仅在显式传 Double 时触发（防御性对齐五语言）。 */
    @Test
    public void testSnowflakeIDPrecision() {
        // ① 浮点雪花 id（> 2^53，精度已丢）→ 显性报错
        Map<String, Object> r = call("processInstance/startAndExecute",
                args("processDefineId", 2084320543834124288.0, "operator", "user1"));
        assertEquals(Integer.valueOf(99999999), r.get("code"));
        assertTrue(String.valueOf(r.get("msg")).contains("超出 float64 精确范围"));

        // ② 字符串雪花 id → 精确解析（无该定义 → 没有流程定义，且不崩溃）
        r = call("processInstance/startAndExecute",
                args("processDefineId", "2084320543834124290", "operator", "user1"));
        assertEquals(Integer.valueOf(99999999), r.get("code"));
        assertTrue(String.valueOf(r.get("msg")).contains("没有流程定义"));
    }

    /** issues/82 负向：抄送空 actors 报错（对齐 PHP 基准 testCreateCCInstanceEmptyActors）。
     *  createCCInstance 空/缺失 actorIds → 99999999 + "actorIds 缺失"。 */
    @Test
    public void testCreateCCInstanceEmptyActors() {
        Map<String, Object> r = call("processInstance/createCCInstance",
                args("processInstanceId", 123L, "operator", "user1", "actorIds", Arrays.asList()));
        assertEquals(Integer.valueOf(99999999), r.get("code"));
        assertTrue(String.valueOf(r.get("msg")).contains("actorIds 缺失"));

        // 负向边界：actorIds 键完全缺失（非空 list）同样报错
        r = call("processInstance/createCCInstance",
                args("processInstanceId", 123L, "operator", "user1"));
        assertEquals(Integer.valueOf(99999999), r.get("code"));
        assertTrue(String.valueOf(r.get("msg")).contains("actorIds 缺失"));
    }

    // ═══ execute submitType 3/4/5/6/20 门面行为（issues/79，前端按钮全量暴露路径）═══

    /** 02-multi-task：发起（apply 自动完成）→ 推进到名为 name 的任务节点 */
    private Long startMultiTaskAt(String name) throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("02-multi-task.json");
        Map<String, Object> r = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "zhangsan"));
        assertOk(r);
        Long instanceId = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));
        String[] order = {"task1", "task2", "task3"};
        String[] actor = {"leader", "manager", "boss"};
        int target = 0;
        for (int i = 0; i < order.length; i++) if (order[i].equals(name)) target = i;
        for (int i = 0; i < target; i++) {
            Long tid = doingTaskId(instanceId, order[i]);
            assertNotNull("应推进到 " + order[i], tid);
            rawRepo.addTaskActor(tid, Arrays.asList(actor[i]));
            assertOk(call("processTask/execute",
                    args("processTaskId", tid, "operator", actor[i], "submitType", 1)));
        }
        return instanceId;
    }

    @Test
    public void testExecuteSubmitTypeBehavior() throws Exception {
        // ── submitType=3 ROLLBACK（血缘版 issues/121 P2）：task2 退回 → 复活 task1 那条历史行，
        //    参与者＝task1 的原办结人 leader（不再是执行回退的 manager），实例保持 DOING(10)
        Long rb = startMultiTaskAt("task2");
        Long t2 = doingTaskId(rb, "task2");
        rawRepo.addTaskActor(t2, Arrays.asList("manager"));
        assertOk(call("processTask/execute", args("processTaskId", t2, "operator", "manager", "submitType", 3)));
        Long rbTask1 = doingTaskId(rb, "task1");
        assertNotNull("ROLLBACK 应在 task1 产生新待办", rbTask1);
        assertTrue("血缘版：复活行的 actor 应为上一步的原办结人 leader，不是执行回退的 manager",
                rawRepo.findTaskActors(rbTask1).contains("leader"));
        assertTrue("执行回退的人不该被派到自己退回出来的那条待办上",
                !rawRepo.findTaskActors(rbTask1).contains("manager"));
        assertEquals("ROLLBACK 后实例应保持 DOING(10)", Integer.valueOf(10),
                rawRepo.findInstanceById(rb).getState());

        // ── submitType=4 JUMP：task3 跳转 apply（第一个任务节点 = start 直接后继，assignee=发起人）
        //   taskName 取自 jumpAbleTaskNameList（已完成任务节点清单）
        Long jp = startMultiTaskAt("task3");
        Long t3 = doingTaskId(jp, "task3");
        rawRepo.addTaskActor(t3, Arrays.asList("boss"));
        Map<String, Object> jl = call("processTask/jumpAbleTaskNameList", args("processInstanceId", jp));
        assertOk(jl);
        List<?> items = (List<?>) jl.get("data");
        assertTrue("jumpAble 应包含已完成的 task1/apply",
                items.stream().anyMatch(m -> "task1".equals(((Map<?, ?>) m).get("value")))
                        && items.stream().anyMatch(m -> "apply".equals(((Map<?, ?>) m).get("value"))));
        assertOk(call("processTask/execute", args("processTaskId", t3, "operator", "boss",
                "submitType", 4, "taskName", "apply")));
        Long jpApply = doingTaskId(jp, "apply");
        assertNotNull("JUMP 应在 apply（首任务节点）产生新待办", jpApply);
        assertEquals("跳首任务节点 assignee 强制为发起人 zhangsan",
                Arrays.asList("zhangsan"), rawRepo.findTaskActors(jpApply));
        assertEquals("JUMP 后实例应保持 DOING(10)", Integer.valueOf(10),
                rawRepo.findInstanceById(jp).getState());

        // ── 负向：JUMP taskName 不存在 → 99999999 + 「无法找到节点模型」
        Long jn = startMultiTaskAt("task2");
        Long t2n = doingTaskId(jn, "task2");
        rawRepo.addTaskActor(t2n, Arrays.asList("manager"));
        Map<String, Object> jr = call("processTask/execute", args("processTaskId", t2n, "operator", "manager",
                "submitType", 4, "taskName", "no-such-node"));
        assertEquals(Integer.valueOf(99999999), jr.get("code"));
        assertTrue("JUMP 无效节点应报「无法找到节点模型」: " + jr.get("msg"),
                String.valueOf(jr.get("msg")).contains("无法找到节点模型"));

        // ── submitType=5 RE_APPLY：task1 重新提交（前端 detail 抽屉场景，含 f_ 表单 + tf_nextNodeOperator）
        Long ra = startMultiTaskAt("task1");
        Long t1r = doingTaskId(ra, "task1");
        rawRepo.addTaskActor(t1r, Arrays.asList("leader"));
        assertOk(call("processTask/execute", args("processTaskId", t1r, "operator", "leader",
                "submitType", 5, "tf_nextNodeOperator", "manager", "f_leaveType", "annual")));
        assertEquals("RE_APPLY 后应推进到 task2", "task2",
                rawRepo.findDoingTasks(ra, null).get(0).getTaskName());
        assertEquals("tf_nextNodeOperator 应覆盖 task2 处理人",
                Arrays.asList("manager"), rawRepo.findTaskActors(doingTaskId(ra, "task2")));
        assertEquals("f_ 表单字段应落实例变量", "annual",
                rawRepo.findInstanceById(ra).getVariables().get("f_leaveType"));
        assertEquals("RE_APPLY 后实例应保持 DOING(10)", Integer.valueOf(10),
                rawRepo.findInstanceById(ra).getState());

        // ── submitType=6 ROLLBACK_TO_OPERATOR：task3 退回发起人 → apply 重执行、actor=发起人 zhangsan
        Long ro = startMultiTaskAt("task3");
        Long t3o = doingTaskId(ro, "task3");
        rawRepo.addTaskActor(t3o, Arrays.asList("boss"));
        assertOk(call("processTask/execute", args("processTaskId", t3o, "operator", "boss", "submitType", 6)));
        Long roApply = doingTaskId(ro, "apply");
        assertNotNull("ROLLBACK_TO_OPERATOR 应重执行首个任务节点 apply", roApply);
        assertEquals("退回发起人 assignee 强制为发起人 zhangsan",
                Arrays.asList("zhangsan"), rawRepo.findTaskActors(roApply));
        assertEquals("退回发起人后实例应保持 DOING(10)", Integer.valueOf(10),
                rawRepo.findInstanceById(ro).getState());

        // ── 负向：非处理人执行被拒（NOT_ALLOWED_EXECUTE）
        Long na = startMultiTaskAt("task1");
        Long t1n = doingTaskId(na, "task1");
        Map<String, Object> nr = call("processTask/execute",
                args("processTaskId", t1n, "operator", "hacker", "submitType", 1));
        assertEquals("非处理人执行应报 99999999", Integer.valueOf(99999999), nr.get("code"));
        assertTrue("非处理人执行应报参与者错误: " + nr.get("msg"),
                String.valueOf(nr.get("msg")).contains("不能执行"));
    }

    @Test
    public void testExecuteReject() throws Exception {
        // submitType=2 REJECT：task1 拒绝 → executeAndJumpToEnd → 实例 REJECT(45)
        // （issues/79：补门面级 submitType=2 参数路径，与 PHP 对齐；Go/Python/Node 同补）
        Long instId = startMultiTaskAt("task1");
        Long t1 = doingTaskId(instId, "task1");
        rawRepo.addTaskActor(t1, Arrays.asList("leader"));
        assertOk(call("processTask/execute", args("processTaskId", t1, "operator", "leader", "submitType", 2)));
        assertEquals("REJECT 后实例应为 REJECT(45)", Integer.valueOf(45),
                rawRepo.findInstanceById(instId).getState());
        assertTrue("REJECT 后应无 DOING 任务", rawRepo.findDoingTasks(instId, null).isEmpty());
    }

    @Test
    public void testExecuteCountersignDisagreeSoft() throws Exception {
        // issues/91：06-countersign-sequential 未配置 ONE_VOTE_VETO → submitType=20 为软拒绝
        // （否决者任务正常完成、flag 记录、流程不阻断，继续等其余成员）
        ProcessInstance.ProcessDefine def = registerFlow("06-countersign-sequential.json");
        Map<String, Object> r = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "user1"));
        assertOk(r);
        Long instanceId = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));
        // 串行会签逐个创建（issues/93）：发起后仅 userA 的 DOING 任务，userB 尚未创建
        Long taskA = doingTaskIdByActor(instanceId, "task1", "userA");
        assertNotNull("会签节点应有 userA 的 DOING 任务", taskA);
        assertNull("串行逐个创建：发起后 userB 不应已有任务", doingTaskIdByActor(instanceId, "task1", "userB"));
        // userA 会签不同意（未配 ONE_VOTE_VETO → 软拒绝，非否决）
        assertOk(call("processTask/execute", args("processTaskId", taskA, "operator", "userA", "submitType", 20)));
        com.mldong.jeeflow.domain.ProcessInstance inst = rawRepo.findInstanceById(instanceId);
        assertEquals("软拒绝后实例应保持 DOING(10)，继续等 userB",
                Integer.valueOf(10), inst.getState());
        assertEquals("countersignDisagreeFlag=1 应落实例变量", Integer.valueOf(1),
                inst.getVariables().get("countersignDisagreeFlag"));
        com.mldong.jeeflow.domain.ProcessTask doneA = rawRepo.findTaskById(taskA);
        assertEquals("软拒绝任务应正常完成", com.mldong.jeeflow.enums.ProcessTaskStateEnum.FINISHED.getCode(),
                doneA.getTaskState());
        assertEquals("countersignDisagreeFlag=1 应落任务变量", Integer.valueOf(1),
                doneA.getVariables().get("countersignDisagreeFlag"));
        assertEquals("否决人应记录为实际操作人", "userA", doneA.getActorId());
        // 软拒绝按常规串行推进：userA 完成后应创建下一位 userB 的 DOING 任务（不阻断、不废弃）
        Long taskB = doingTaskIdByActor(instanceId, "task1", "userB");
        assertNotNull("软拒绝后应推进创建 userB 的 DOING 任务", taskB);
        com.mldong.jeeflow.domain.ProcessTask stillB = rawRepo.findTaskById(taskB);
        assertEquals("软拒绝不应废弃其余成员任务，userB 应保持 DOING",
                com.mldong.jeeflow.enums.ProcessTaskStateEnum.DOING.getCode(), stillB.getTaskState());
    }

    @Test
    public void testExecuteCountersignOneVoteVeto() throws Exception {
        // issues/91：13-countersign-one-vote-veto（并行 + ONE_VOTE_VETO）→ 任一成员
        // submitType=20 一票否决 → 会签节点立即推进 end，其余 DOING 会签任务废弃(99)
        ProcessInstance.ProcessDefine def = registerFlow("13-countersign-one-vote-veto.json");
        Map<String, Object> r = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "user1"));
        assertOk(r);
        Long instanceId = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));
        // 并行会签全员预创建：userA/userB/userC 三个 DOING 任务
        Long taskA = doingTaskIdByActor(instanceId, "task1", "userA");
        com.mldong.jeeflow.domain.ProcessTask taskB = taskByActor(instanceId, "task1", "userB");
        com.mldong.jeeflow.domain.ProcessTask taskC = taskByActor(instanceId, "task1", "userC");
        assertNotNull("会签节点应有 userA 的 DOING 任务", taskA);
        assertNotNull("会签节点应有 userB 的 DOING 任务", taskB);
        assertNotNull("会签节点应有 userC 的 DOING 任务", taskC);
        // userA 会签不同意（已配 ONE_VOTE_VETO → 一票否决）
        assertOk(call("processTask/execute", args("processTaskId", taskA, "operator", "userA", "submitType", 20)));
        com.mldong.jeeflow.domain.ProcessInstance inst = rawRepo.findInstanceById(instanceId);
        assertEquals("一票否决后会签节点应立即推进 end（实例 FINISHED 20）",
                Integer.valueOf(20), inst.getState());
        assertEquals("countersignDisagreeFlag=1 应落实例变量", Integer.valueOf(1),
                inst.getVariables().get("countersignDisagreeFlag"));
        com.mldong.jeeflow.domain.ProcessTask doneA = rawRepo.findTaskById(taskA);
        assertEquals("否决任务应已完成", com.mldong.jeeflow.enums.ProcessTaskStateEnum.FINISHED.getCode(),
                doneA.getTaskState());
        assertEquals("否决人应记录为实际操作人", "userA", doneA.getActorId());
        assertEquals("否决应废弃其余成员 userB（ABANDON 99）",
                com.mldong.jeeflow.enums.ProcessTaskStateEnum.ABANDON.getCode(),
                rawRepo.findTaskById(taskB.getTaskId()).getTaskState());
        assertEquals("否决应废弃其余成员 userC（ABANDON 99）",
                com.mldong.jeeflow.enums.ProcessTaskStateEnum.ABANDON.getCode(),
                rawRepo.findTaskById(taskC.getTaskId()).getTaskState());
        assertTrue("否决后应无 DOING 任务", rawRepo.findDoingTasks(instanceId, null).isEmpty());
    }

    @Test
    public void testExecuteCountersignDisagreeParallelSoft() throws Exception {
        // issues/91：05-countersign-parallel 未配置 ONE_VOTE_VETO → 并行会签 submitType=20 软拒绝
        // （否决者任务完成、flag 记录、流程不阻断，其余成员仍 DOING 等待）
        ProcessInstance.ProcessDefine def = registerFlow("05-countersign-parallel.json");
        Map<String, Object> r = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "user1"));
        assertOk(r);
        Long instanceId = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));
        Long taskA = doingTaskIdByActor(instanceId, "task1", "userA");
        com.mldong.jeeflow.domain.ProcessTask taskB = taskByActor(instanceId, "task1", "userB");
        com.mldong.jeeflow.domain.ProcessTask taskC = taskByActor(instanceId, "task1", "userC");
        assertNotNull("会签节点应有 userA 的 DOING 任务", taskA);
        assertNotNull("会签节点应有 userB 的 DOING 任务", taskB);
        assertNotNull("会签节点应有 userC 的 DOING 任务", taskC);
        // userA 会签不同意（未配 ONE_VOTE_VETO → 软拒绝）
        assertOk(call("processTask/execute", args("processTaskId", taskA, "operator", "userA", "submitType", 20)));
        com.mldong.jeeflow.domain.ProcessInstance inst = rawRepo.findInstanceById(instanceId);
        assertEquals("并行软拒绝后实例应保持 DOING(10)，等 userB/userC",
                Integer.valueOf(10), inst.getState());
        assertEquals("countersignDisagreeFlag=1 应落实例变量", Integer.valueOf(1),
                inst.getVariables().get("countersignDisagreeFlag"));
        assertEquals("软拒绝任务应正常完成", com.mldong.jeeflow.enums.ProcessTaskStateEnum.FINISHED.getCode(),
                rawRepo.findTaskById(taskA).getTaskState());
        assertEquals("软拒绝不应废弃 userB",
                com.mldong.jeeflow.enums.ProcessTaskStateEnum.DOING.getCode(),
                rawRepo.findTaskById(taskB.getTaskId()).getTaskState());
        assertEquals("软拒绝不应废弃 userC",
                com.mldong.jeeflow.enums.ProcessTaskStateEnum.DOING.getCode(),
                rawRepo.findTaskById(taskC.getTaskId()).getTaskState());
    }

    // ═══ 列表字段契约 + 时间格式（issues/05-2 / 05-3）═══

    @Test
    public void testListRowContract() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("01-simple.json");
        call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "zhangsan", "amount", 500));
        Map<String, Object> r = call("processTask/todoList", args("operator", "leader"));
        assertOk(r);
        List<?> rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertFalse(rows.isEmpty());
        Map<String, Object> row = (Map<String, Object>) rows.get(0);
        // 05-2：ext（任务变量对象，空回退实例变量）+ instanceExt + version
        assertNotNull(row.get("ext"));
        assertNotNull(row.get("instanceExt"));
        assertNotNull(row.get("version"));
        // 05-3：时间字段为 yyyy-MM-dd HH:mm:ss 格式字符串
        String ct = String.valueOf(row.get("createTime"));
        assertTrue("时间应格式化为 yyyy-MM-dd HH:mm:ss（无 T）: " + ct, ct.contains(" ") && !ct.contains("T"));

        r = call("processInstance/page", args("operator", "zhangsan"));
        assertOk(r);
        rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertFalse(rows.isEmpty());
        row = (Map<String, Object>) rows.get(0);
        assertNotNull(row.get("ext"));       // 实例变量对象
        assertNotNull(row.get("displayName")); // 定义显示名
        assertNotNull(row.get("version"));     // 定义版本
        // issues/82-2：分页五键整体（pageNum/pageSize/rows/recordCount/totalPage）
        Map<String, Object> pageData = (Map<String, Object>) r.get("data");
        for (String k : new String[]{"pageNum", "pageSize", "rows", "recordCount", "totalPage"}) {
            assertTrue("分页五键应含 " + k + ": " + pageData.keySet(), pageData.containsKey(k));
        }
        assertTrue("totalPage 应为正整数", ((Number) pageData.get("totalPage")).intValue() >= 1);
    }

    // ═══ issues/05-5：m_ 前缀查询参数（前端 m_LIKE_name / m_pd_LIKE_* / m_t_LIKE_*）═══

    @Test
    public void testMQueryParams() throws Exception {
        ProcessInstance.ProcessDefine def1 = registerFlow("01-simple.json");
        registerFlow("02-multi-task.json");

        // 无别名 → 默认主表别名 t（t.name / t.display_name，对齐白名单）
        Map<String, Object> r = call("processDefine/page", args("m_LIKE_name", "simple"));
        assertOk(r);
        List<?> rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertEquals("m_LIKE_name 应过滤到 01-simple: " + r, 1, rows.size());
        assertEquals("01-simple", ((Map<String, Object>) rows.get(0)).get("name"));

        r = call("processDefine/page", args("m_LIKE_displayName", "02"));
        assertOk(r);
        rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertEquals("m_LIKE_displayName 应过滤到 02-multi-task: " + r, 1, rows.size());

        r = call("processDefine/page", args("m_LIKE_displayName", ".json"));
        assertOk(r);
        rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertEquals("m_LIKE_displayName 应匹配全部: " + r, 2, rows.size());

        // 实例列表：m_pd_LIKE_displayName（别名 pd → pd.display_name）
        call("processInstance/startAndExecute",
                args("processDefineId", def1.getId(), "operator", "zhangsan"));
        r = call("processInstance/page", args("operator", "zhangsan", "m_pd_LIKE_displayName", "simple"));
        assertOk(r);
        rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertEquals("m_pd_LIKE_displayName 应命中: " + r, 1, rows.size());

        r = call("processInstance/page", args("operator", "zhangsan", "m_pd_LIKE_displayName", "zzz"));
        assertOk(r);
        rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertEquals("m_pd_LIKE_displayName 不应命中: " + r, 0, rows.size());

        // issues/82-6：实例列表按编码搜 m_pd_LIKE_name（pd.name 白名单列）
        r = call("processInstance/page", args("operator", "zhangsan", "m_pd_LIKE_name", "simple"));
        assertOk(r);
        rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertEquals("m_pd_LIKE_name 应命中 01-simple 实例: " + r, 1, rows.size());

        r = call("processInstance/page", args("operator", "zhangsan", "m_pd_LIKE_name", "zzz"));
        assertOk(r);
        rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertEquals("m_pd_LIKE_name 不应命中: " + r, 0, rows.size());

        // 任务列表：m_t_LIKE_displayName（别名 t → t.display_name）
        r = call("processTask/todoList", args("operator", "leader", "m_t_LIKE_displayName", "审批"));
        assertOk(r);
        rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertEquals("m_t_LIKE_displayName 应命中待办: " + r, 1, rows.size());

        r = call("processTask/todoList", args("operator", "leader", "m_t_LIKE_displayName", "zzz"));
        assertOk(r);
        rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertEquals("m_t_LIKE_displayName 不应命中: " + r, 0, rows.size());
    }

    // ═══ issues/07：设计详情 jsonObject 缺失基本信息时从设计表补齐 ═══

    @Test
    public void testDesignDetailJsonObjectMerge() throws Exception {
        String content = new String(Files.readAllBytes(
                Paths.get("src/test/resources/flows/01-simple.json")), java.nio.charset.StandardCharsets.UTF_8);
        // 无 content 保存 → 无 his → jsonObject 为空对象合并基本信息
        Map<String, Object> r = call("processDesign/save", args("name", "test_display",
                "displayName", "回显测试", "operator", "zhangsan"));
        assertOk(r);
        Long designId = toLong(((Map<String, Object>) r.get("data")).get("id"));

        r = call("processDesign/detail", args("id", designId));
        assertOk(r);
        Map<String, Object> data = (Map<String, Object>) r.get("data");
        Map<String, Object> jo = (Map<String, Object>) data.get("jsonObject");
        assertNotNull("jsonObject 应始终返回（issues/07）: " + r, jo);
        assertEquals("test_display", jo.get("name"));
        assertEquals("回显测试", jo.get("displayName"));
        assertEquals(designId, jo.get("processDesignId"));

        // 已有 his（含 name/displayName）→ 保留 his 内容
        r = call("processDesign/save", args("id", designId, "name", "test_display",
                "displayName", "回显测试", "content", content, "operator", "zhangsan"));
        assertOk(r);
        r = call("processDesign/detail", args("id", designId));
        assertOk(r);
        jo = (Map<String, Object>) ((Map<String, Object>) r.get("data")).get("jsonObject");
        assertEquals("his content 的 name 优先: " + r, "simple", jo.get("name"));
    }

    // ═══ issues/08：部署/重新部署/设计稿变更的 is_deployed 状态同步 ═══

    @Test
    public void testDesignDeployRedeployIsDeployed() throws Exception {
        String content = new String(Files.readAllBytes(
                Paths.get("src/test/resources/flows/01-simple.json")), java.nio.charset.StandardCharsets.UTF_8);

        // 保存（含内容快照）→ 未部署
        Map<String, Object> r = call("processDesign/save", args("name", "leave08",
                "displayName", "请假流程08", "content", content, "operator", "zhangsan"));
        assertOk(r);
        Long designId = toLong(((Map<String, Object>) r.get("data")).get("id"));
        assertEquals(0, (int) extRepo.findDesignById(designId).getIsDeployed());

        // 部署 → is_deployed=1
        r = call("processDesign/deploy", args("id", designId, "operator", "zhangsan"));
        assertOk(r);
        Long defineId = toLong(((Map<String, Object>) r.get("data")).get("processDefineId"));
        assertEquals(1, (int) extRepo.findDesignById(designId).getIsDeployed());
        Integer versionAfterDeploy = repo.findDefineById(defineId).getVersion();

        // 重新部署 → 同一 defineId（内容替换，version 不变）+ is_deployed=1
        r = call("processDesign/redeploy", args("id", designId, "operator", "zhangsan"));
        assertOk(r);
        assertEquals(defineId, toLong(((Map<String, Object>) r.get("data")).get("processDefineId")));
        assertEquals(1, (int) extRepo.findDesignById(designId).getIsDeployed());
        // issues/59：redeploy 是替换语义，version 必须保持（JDBC 仓储曾因 def 未携带 version 兜底误写 1）
        assertEquals("redeploy 后 version 应不变", versionAfterDeploy, repo.findDefineById(defineId).getVersion());

        // 设计稿内容变更（updateDefine，不同 content）→ 新快照 + is_deployed=0
        String content2 = new String(Files.readAllBytes(
                Paths.get("src/test/resources/flows/02-multi-task.json")), java.nio.charset.StandardCharsets.UTF_8);
        r = call("processDesign/updateDefine", args("processDesignId", designId,
                "content", content2, "operator", "zhangsan"));
        assertOk(r);
        assertEquals(0, (int) extRepo.findDesignById(designId).getIsDeployed());
        assertEquals(2, extRepo.listDesignHis(designId).size());
        assertEquals("updateDefine 应同步 name: " + r, "multi-task", extRepo.findDesignById(designId).getName());

        // 基本信息修改（update）→ is_deployed 不变
        r = call("processDesign/update", args("id", designId, "displayName", "改名08", "operator", "zhangsan"));
        assertOk(r);
        assertEquals("改名08", extRepo.findDesignById(designId).getDisplayName());
        assertEquals(0, (int) extRepo.findDesignById(designId).getIsDeployed());

        // 部署 → 再置 1
        r = call("processDesign/deploy", args("id", designId, "operator", "zhangsan"));
        assertOk(r);
        assertEquals(1, (int) extRepo.findDesignById(designId).getIsDeployed());
    }

    // ═══ issues/15：formData / taskFormData / 审批记录 ext 契约 ═══

    @Test
    public void testFormDataContract() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("01-simple.json");
        Map<String, Object> r = call("processInstance/startAndExecute", args("processDefineId", def.getId(),
                "operator", "zhangsan", "f_reasonType", "休假", "f_amount", 500));
        assertOk(r);
        Long instanceId = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));

        // 实例详情：formData（f_ 前缀 + 去前缀副本）+ displayName/name/version
        r = call("processInstance/detail", args("id", instanceId));
        assertOk(r);
        Map<String, Object> data = (Map<String, Object>) r.get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> formData = (Map<String, Object>) data.get("formData");
        assertNotNull("formData 应返回（issues/15）: " + r, formData);
        assertEquals("休假", formData.get("f_reasonType"));
        assertEquals("休假", formData.get("reasonType"));
        assertEquals(500, ((Number) formData.get("f_amount")).intValue());
        assertEquals("01-simple", data.get("name"));
        assertNotNull(data.get("displayName"));
        assertNotNull(data.get("version"));

        // 执行任务（tf_ 前缀变量 → 任务变量）→ 待办/已办行 taskFormData + 审批记录 ext
        r = call("processTask/todoList", args("operator", "leader"));
        assertOk(r);
        List<?> rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertFalse(rows.isEmpty());
        Long taskId = toLong(((Map<String, Object>) rows.get(0)).get("id"));
        r = call("processTask/execute", args("processTaskId", taskId, "operator", "leader",
                "tf_approvalComment", "同意"));
        assertOk(r);

        r = call("processTask/doneList", args("operator", "leader"));
        assertOk(r);
        rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertFalse(rows.isEmpty());
        @SuppressWarnings("unchecked")
        Map<String, Object> taskFormData = (Map<String, Object>) ((Map<String, Object>) rows.get(0)).get("taskFormData");
        assertNotNull("任务行 taskFormData 应返回（issues/15）: " + r, taskFormData);
        assertEquals("同意", taskFormData.get("tf_approvalComment"));
        assertEquals("同意", taskFormData.get("approvalComment"));
        // 82-8：doneList 行 finishTime 已格式化（yyyy-MM-dd HH:mm:ss 无 T）
        Object ft = ((Map<String, Object>) rows.get(0)).get("finishTime");
        assertNotNull("doneList 行 finishTime 应非空（已办任务）: " + r, ft);
        assertTrue("doneList finishTime 应格式化 yyyy-MM-dd HH:mm:ss（无 T）: " + ft,
                ft.toString().matches("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}"));

        r = call("processInstance/approvalRecord", args("id", instanceId));
        assertOk(r);
        rows = (List<?>) r.get("data");
        boolean hasExt = false;
        for (Object o : rows) {
            if (((Map<String, Object>) o).get("ext") != null) hasExt = true;
        }
        assertTrue("审批记录行应含 ext（issues/15）: " + r, hasExt);
    }

    // ═══ candidatePage 双源候选（issues/16 GlobalCandidateHandler 语义）═══

    @Test
    public void testCandidatePageDualSource() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("12-candidate-page.json");
        Map<String, Object> r = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "user1"));
        assertOk(r);
        Long instanceId = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));

        r = call("processTask/todoList", args("operator", "leader"));
        assertOk(r);
        List<?> rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertFalse(rows.isEmpty());
        Long taskId = toLong(((Map<String, Object>) rows.get(0)).get("id"));

        // candidatePage：当前任务 apply → 后继节点 review 的候选
        // （startAndExecute 已自动完成 apply，直接用 engine 启动拿 apply 任务）
        com.mldong.jeeflow.domain.FlowData startArgs = com.mldong.jeeflow.domain.FlowData.create();
        com.mldong.jeeflow.domain.ProcessInstance inst2 =
                engine.startProcessInstanceById(def.getId(), "user1", startArgs);
        List<com.mldong.jeeflow.domain.ProcessTask> applyTasks =
                repo.findDoingTasks(inst2.getInstanceId(), new String[]{});
        assertFalse(applyTasks.isEmpty());
        assertEquals("apply", applyTasks.get(0).getTaskName());
        r = call("processTask/candidatePage", args("processTaskId", applyTasks.get(0).getTaskId()));
        assertOk(r);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>)
                ((Map<String, Object>) r.get("data")).get("rows");
        assertNotNull("candidatePage 应返回候选: " + r, candidates);
        List<String> userIds = new ArrayList<>();
        for (Map<String, Object> c : candidates) userIds.add(String.valueOf(c.get("userId")));
        // issues/80：模型候选行键对齐前端 UserSelect（valueField='id'）——断言 id 键存在且可取值，不再依赖 userId
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> c : candidates) {
            assertNotNull("候选行应有 id 键（前端 valueField='id' 取值）: " + c, c.get("id"));
            assertNotNull("候选行应有 realName 键: " + c, c.get("realName"));
            ids.add(String.valueOf(c.get("id")));
        }
        assertEquals("id 与 userId 应一一对齐（行键归一）", userIds, ids);
        assertTrue("id 应含 candidateUsers 指定人 userA: " + ids, ids.contains("userA"));
        assertTrue("应含 candidateUsers 指定人 userA: " + userIds, userIds.contains("userA"));
        assertTrue("应含 candidateUsers 指定人 userB: " + userIds, userIds.contains("userB"));
        assertTrue("应含 candidateGroups 角色成员 finA: " + userIds, userIds.contains("finA"));
        assertTrue("应含 candidateGroups 角色成员 finB: " + userIds, userIds.contains("finB"));
    }

    // ═══ startAndExecute 预指派人（f_nextNodeOperator → tf_，对齐 boot3）═══

    @Test
    public void testStartAndExecutePreAssign() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("01-simple.json");

        // 发起并预指派人：f_nextNodeOperator=userA → 自动完成 apply → task1 参与者 = userA（非 leader）
        Map<String, Object> r = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "user1",
                        com.mldong.jeeflow.enums.FlowConst.PROCESS_START_NEXT_NODE_OPERATOR, "userA"));
        assertOk(r);
        Long inst1 = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));

        r = call("processTask/todoList", args("operator", "userA"));
        assertOk(r);
        List<?> rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertFalse("userA 应收到 task1 待办（预指派人）: " + r, rows.isEmpty());
        assertEquals("task1", ((Map<String, Object>) rows.get(0)).get("taskName"));

        // 未指定时按 assignee（leader）→ 第二个实例 task1 参与者是 leader（userA 待办不应新增）
        r = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "user1"));
        assertOk(r);
        Long inst2 = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));
        java.util.List<com.mldong.jeeflow.domain.ProcessTask> t2 =
                repo.findDoingTasks(inst2, new String[]{});
        assertFalse(t2.isEmpty());
        assertEquals("未指定时 task1 参与者应为 leader: " + t2.get(0).getActorIds(),
                java.util.Arrays.asList("leader"), t2.get(0).getActorIds());
        r = call("processTask/todoList", args("operator", "userA"));
        assertOk(r);
        rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        for (Object o : rows) {
            Long pid = toLong(((Map<String, Object>) o).get("processInstanceId"));
            assertFalse("第二个实例的 task1 不应出现在 userA 待办: " + r, pid.equals(inst2));
        }
    }


    /** 测试辅助：读 01-simple 流程定义并注入 relTableName（bizData 测试用） */
    private String simpleFlowWithRelTable(String relTableName) throws Exception {
        String json = new String(Files.readAllBytes(
                Paths.get("src/test/resources/flows/01-simple.json")), StandardCharsets.UTF_8);
        return json.replace("\"type\": \"approval\"", "\"type\": \"approval\", \"relTableName\": \"" + relTableName + "\"");
    }

    /** 测试辅助：直接建一条设计记录 */
    private void saveDesign(Long id, String name, String displayName, String type) {
        ProcessDesign d = new ProcessDesign();
        d.setId(id);
        d.setName(name);
        d.setDisplayName(displayName);
        d.setType(type);
        d.setIcon(null);
        d.setRemark(null);
        d.setIsDeployed(0);
        extRepo.saveDesign(d);
    }

    // ═══ issues/27：updateDefine 兼容 boot3 顶层 JSON ═══

    @Test
    public void testUpdateDefineTopLevelJson() throws Exception {
        saveDesign(100L, "old", "旧名", "approval");
        // 保存设计（顶层 JSON 无 content）
        Map<String, Object> r = call("processDesign/updateDefine", args(
                "processDesignId", 100L, "operator", "user1",
                "name", "topjson", "displayName", "顶层JSON流程", "type", "approval",
                "relTableName", "biz_top", "persistMode", "SYNC",
                "nodes", new ArrayList<>(), "edges", new ArrayList<>()));
        assertOk(r);
        // 设计信息同步（name 来自顶层 JSON）
        ProcessDesign d = extRepo.findDesignById(100L);
        assertNotNull(d);
        assertEquals("topjson", d.getName());
        assertEquals("顶层JSON流程", d.getDisplayName());
        // 历史内容 = 顶层 JSON 序列化（含 nodes/edges，不含 processDesignId/operator）
        List<ProcessDesignHis> his = extRepo.listDesignHis(100L);
        assertFalse("应写入内容快照", his.isEmpty());
        String content = new String(his.get(0).getContent(), StandardCharsets.UTF_8);
        assertTrue("content 应含 nodes: " + content, content.contains("\"nodes\""));
        assertFalse("content 不应含 processDesignId", content.contains("processDesignId"));
        assertFalse("content 不应含 operator", content.contains("\"operator\""));
    }

    // ═══ issues/28：批量语义 + listByType + bizData ═══

    @Test
    public void testBatchRemoveAndUpDown() throws Exception {
        // 两个设计 → 发布为两个定义
        Long[] defineIds = new Long[2];
        int i = 0;
        for (Long id : Arrays.asList(201L, 202L)) {
            saveDesign(id, "batch" + id, "批量流程" + id, "approval");
            call("processDesign/updateDefine", args(
                    "processDesignId", id, "operator", "user1",
                    "name", "batch" + id, "displayName", "批量流程" + id, "type", "approval",
                    "nodes", new ArrayList<>(), "edges", new ArrayList<>()));
            Map<String, Object> dr = call("processDesign/deploy", args("id", id, "operator", "user1"));
            assertOk(dr);
            defineIds[i++] = toLong(((Map<String, Object>) dr.get("data")).get("processDefineId"));
        }
        // 批量上下架 {ids, opType}
        Map<String, Object> r = call("processDefine/upAndDown", args(
                "ids", Arrays.asList(defineIds[0], defineIds[1]), "opType", 0));
        assertOk(r);
        assertEquals(Integer.valueOf(0), rawRepo.findDefineById(defineIds[0]).getState());
        assertEquals(Integer.valueOf(0), rawRepo.findDefineById(defineIds[1]).getState());
        // 批量删除设计 {ids}（单 {id} 回归）
        r = call("processDesign/remove", args("ids", Arrays.asList(201L, 202L)));
        assertOk(r);
        assertNull(extRepo.findDesignById(201L));
        assertNull(extRepo.findDesignById(202L));
        r = call("processDesign/remove", args("id", 203L));
        assertOk(r);
    }

    @Test
    public void testDesignListByType() throws Exception {
        saveDesign(301L, "leave1", "请假", "approval");
        saveDesign(302L, "reimburse1", "报销", "finance");
        call("processDesign/updateDefine", args(
                "processDesignId", 301L, "operator", "user1",
                "name", "leave1", "displayName", "请假", "type", "approval",
                "nodes", new ArrayList<>(), "edges", new ArrayList<>()));
        call("processDesign/updateDefine", args(
                "processDesignId", 302L, "operator", "user1",
                "name", "reimburse1", "displayName", "报销", "type", "finance",
                "nodes", new ArrayList<>(), "edges", new ArrayList<>()));
        Map<String, Object> r = call("processDesign/listByType", args());
        assertOk(r);
        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> groups =
                (Map<String, List<Map<String, Object>>>) r.get("data");
        assertTrue("应含 approval 分组: " + groups.keySet(), groups.containsKey("approval"));
        assertTrue("应含 finance 分组: " + groups.keySet(), groups.containsKey("finance"));
        assertEquals("leave1", groups.get("approval").get(0).get("name"));
        assertEquals("reimburse1", groups.get("finance").get(0).get("name"));
        // jsonObject 回显（最新设计稿）
        assertNotNull(groups.get("approval").get(0).get("jsonObject"));
    }

    /** issues/81：listByType items 必含 processDefineState（前端发起按钮硬依赖），取值随定义 state 联动 */
    @Test
    public void testDesignListByTypeProcessDefineState() throws Exception {
        saveDesign(501L, "leave81", "请假81", "approval");
        call("processDesign/updateDefine", args(
                "processDesignId", 501L, "operator", "user1",
                "name", "leave81", "displayName", "请假81", "type", "approval",
                "nodes", new ArrayList<>(), "edges", new ArrayList<>()));
        // 场景 A：deploy → 定义 state=1 → 可发起
        Map<String, Object> dr = call("processDesign/deploy", args("id", 501L, "operator", "user1"));
        assertOk(dr);
        Long defineId = toLong(((Map<String, Object>) dr.get("data")).get("processDefineId"));

        Map<String, Object> r = call("processDesign/listByType", args());
        assertOk(r);
        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> groups =
                (Map<String, List<Map<String, Object>>>) r.get("data");
        Map<String, Object> itemA = groups.get("approval").stream()
                .filter(m -> "leave81".equals(m.get("name")))
                .findFirst().orElseThrow(() -> new AssertionError("缺 leave81 item: " + groups));
        assertEquals("processDefineId 应回显", defineId, itemA.get("processDefineId"));
        assertEquals("启用定义 processDefineState 应为 1（前端可发起）",
                Integer.valueOf(1), itemA.get("processDefineState"));

        // 场景 B：upAndDown 禁用 → 定义 state=0 → 前端置灰
        Map<String, Object> ur = call("processDefine/upAndDown", args("id", defineId, "opType", 0));
        assertOk(ur);
        r = call("processDesign/listByType", args());
        assertOk(r);
        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> groups2 =
                (Map<String, List<Map<String, Object>>>) r.get("data");
        Map<String, Object> itemB = groups2.get("approval").stream()
                .filter(m -> "leave81".equals(m.get("name")))
                .findFirst().orElseThrow(() -> new AssertionError("缺 leave81 item: " + groups2));
        assertEquals("禁用定义 processDefineState 应为 0（前端置灰）",
                Integer.valueOf(0), itemB.get("processDefineState"));
    }

    /** bizData：未注册 MetaTableReader → 清晰报错 */
    @Test
    public void testBizDataUnregistered() throws Exception {
        // 真实实例 + 未注册 MetaTableReader
        saveDesign(400L, "bizflow", "业务流程", "approval");
        call("processDesign/updateDefine", args(
                "processDesignId", 400L, "operator", "user1",
                "content", simpleFlowWithRelTable("biz_leave")));
        Map<String, Object> dr = call("processDesign/deploy", args("id", 400L, "operator", "user1"));
        assertOk(dr);
        Long defineId = toLong(((Map<String, Object>) dr.get("data")).get("processDefineId"));
        Map<String, Object> sr = call("processInstance/startAndExecute", args(
                "processDefineId", defineId, "operator", "user1", "title", "x"));
        assertOk(sr);
        Long instId = toLong(((Map<String, Object>) sr.get("data")).get("processInstanceId"));
        Map<String, Object> r = call("processInstance/bizData", args("processInstanceId", instId));
        assertNotEquals("未注册应报错", Integer.valueOf(0), r.get("code"));
        assertTrue("报错应提示注册: " + r, String.valueOf(r.get("msg")).contains("metaTableReader"));
    }

    /** bizData：注册读取器（mock，同 MetaTableReader 契约）→ 回显正常 */
    @Test
    public void testBizDataRegistered() throws Exception {
        // mock 读取器（public 静态类——facade 反射调用需可访问）
        ServiceContext.put("metaTableReader", new MockMetaTableReader());
        // 发布带 relTableName 的定义
        saveDesign(401L, "bizflow", "业务流程", "approval");
        call("processDesign/updateDefine", args(
                "processDesignId", 401L, "operator", "user1",
                "content", simpleFlowWithRelTable("biz_leave")));
        Map<String, Object> dr = call("processDesign/deploy", args("id", 401L, "operator", "user1"));
        assertOk(dr);
        Long defineId = toLong(((Map<String, Object>) dr.get("data")).get("processDefineId"));
        // 发起实例
        Map<String, Object> r = call("processInstance/startAndExecute", args(
                "processDefineId", defineId, "operator", "user1", "title", "x"));
        assertOk(r);
        Long instId = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));
        r = call("processInstance/bizData", args("processInstanceId", instId));
        assertOk(r);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) r.get("data");
        assertEquals("biz_leave", data.get("tableName"));
        assertEquals("业务数据", data.get("title"));
    }

    // ═══ issues/29：action 权限码 SPI 默认实现 ═══

    @Test
    public void testActionPermissionDefaultProvider() {
        com.mldong.jeeflow.spi.IActionPermissionProvider provider =
                ServiceContext.find(com.mldong.jeeflow.spi.IActionPermissionProvider.class);
        assertNotNull("引擎 configure 应注册默认实现", provider);
        // 默认规则：wf:{action:/→:}
        assertArrayEquals(new String[]{"wf:processDefine:page"}, provider.permissionCodes("processDefine/page"));
        // OR 语义：detail 任一码
        String[] or = provider.permissionCodes("processDefine/detail");
        assertTrue(or.length >= 2);
        assertTrue(Arrays.asList(or).contains("wf:processDefine:detail"));
        assertTrue(Arrays.asList(or).contains("wf:processDesign:listByType"));
        // 放行（登录即可）
        assertNull(provider.permissionCodes("processInstance/detail"));
        assertNull(provider.permissionCodes("processInstance/bizData"));
    }

}
