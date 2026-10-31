package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.JeeflowException;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ReapplySupport;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessInstanceStateEnum;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
import com.mldong.jeeflow.spi.IUserProvider;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * 「退回修改、重提直返」验收：普通直返、条件分支、未汇合并行等待、多分支分别退回、
 * 重复退回、并发重提、回归（无标记走原路）与非法配置。
 */
public class ReturnToSourceTest {

    private JeeflowEngine engine;
    private MemoryProcessRepository repo;
    private TestJsonProvider json;
    private Configuration config;

    @Before
    public void setUp() {
        config = new Configuration();
        repo = new MemoryProcessRepository();
        json = new TestJsonProvider();
        ServiceContext.put("repository", repo);
        ServiceContext.put("json", json);
        ServiceContext.put("expr", new TestExpressionEvaluator());
        ServiceContext.put("user", new IUserProvider() {
            @Override public UserInfo getUser(String userId) {
                UserInfo u = new UserInfo();
                u.setUserId(userId);
                u.setRealName("用户" + userId);
                u.setDeptId("D01");
                u.setDeptName("测试部门");
                u.setPostId("P01");
                u.setPostName("测试岗位");
                return u;
            }
        });
        engine = new JeeflowEngineImpl();
        engine.configure(config);
    }

    private ProcessInstance.ProcessDefine registerFlow(String filename) throws Exception {
        return registerFlowFrom("flows", filename);
    }

    private ProcessInstance.ProcessDefine registerFlowFrom(String dir, String filename) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get("src/test/resources/" + dir + "/" + filename));
        String jsonStr = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
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

    private ProcessInstance startFlow(ProcessInstance.ProcessDefine def, FlowData args) throws Exception {
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "applicant", args);
        for (ProcessTask task : repo.findDoingTasks(inst.getInstanceId(), null)) {
            engine.executeProcessTask(task.getTaskId(), "applicant",
                    FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.APPLY.getCode()));
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

    private List<ProcessTask> doingAll(Long instanceId) {
        return repo.findDoingTasks(instanceId, null);
    }

    private long rows(Long instanceId, String name) {
        return repo.findHistoryTasks(instanceId).stream()
                .filter(t -> name.equals(t.getTaskName())).count();
    }

    private FlowData submit(int type) {
        return FlowData.create().set(FlowConst.SUBMIT_TYPE, type);
    }

    private static final int RETURN = ProcessSubmitTypeEnum.ROLLBACK_TO_OPERATOR.getCode();
    private static final int REAPPLY = ProcessSubmitTypeEnum.RE_APPLY.getCode();
    private static final int AGREE = ProcessSubmitTypeEnum.AGREE.getCode();

    // ═══ 10.1 基本直返 ═══

    @Test
    public void test01_simpleReturnToSource() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("14-return-to-source.json");
        ProcessInstance inst = startFlow(def, FlowData.create());
        Long iid = inst.getInstanceId();
        ProcessTask taskA = doing(iid, "taskA");
        assertNotNull(taskA);
        Long originalInstance = iid;

        engine.executeAndJumpToFirstTaskNode(taskA.getTaskId(), "userA", submit(RETURN));

        // 原来源任务完成并留痕，实例仍是同一个且进行中
        ProcessTask finishedA = repo.findTaskById(taskA.getTaskId());
        assertTrue("来源任务应已完成", finishedA.isFinished());
        assertEquals("退回类型留痕", Integer.valueOf(RETURN), finishedA.getVariables().getInt(FlowConst.SUBMIT_TYPE));
        ProcessInstance afterReturn = repo.findInstanceById(iid);
        assertEquals(originalInstance, afterReturn.getInstanceId());
        assertEquals(ProcessInstanceStateEnum.DOING.getCode(), afterReturn.getState());

        // 生成新的申请待办，并携带直返上下文
        ProcessTask apply = doing(iid, "apply");
        assertNotNull("退回后应有申请待办", apply);
        Map<String, Object> ctx = ReapplySupport.reapplyOf(apply);
        assertNotNull("申请待办应带直返上下文", ctx);
        assertEquals(String.valueOf(taskA.getTaskId()), String.valueOf(ctx.get("sourceTaskId")));
        assertEquals("taskA", ctx.get("sourceNode"));
        assertEquals(1, doingAll(iid).size());
        assertNull("退回不生成后续节点任务", doing(iid, "taskB"));

        // 重提：直接回到来源节点，不经过来源之前的节点
        engine.executeProcessTask(apply.getTaskId(), "applicant", submit(REAPPLY));
        assertTrue(repo.findTaskById(apply.getTaskId()).isFinished());
        ProcessTask newA = doing(iid, "taskA");
        assertNotNull("重提应在来源节点新建待办", newA);
        assertEquals("新 C 的血缘指向本轮申请任务", apply.getTaskId(), newA.getParentTaskId());
        assertEquals(1, doingAll(iid).size());
        assertNull("重提不得生成来源之后的节点", doing(iid, "taskB"));
        assertEquals("旧来源任务保留为历史行", 2, rows(iid, "taskA"));

        // 新 C 办理通过后继续后续流程
        engine.executeProcessTask(newA.getTaskId(), "userA", submit(AGREE));
        ProcessTask taskB = doing(iid, "taskB");
        assertNotNull("C 办理通过后进入后续节点", taskB);
        engine.executeProcessTask(taskB.getTaskId(), "userB", submit(AGREE));
        assertEquals(ProcessInstanceStateEnum.FINISHED.getCode(), repo.findInstanceById(iid).getState());
    }

    @Test
    public void test02_reapplyRejectsOtherSubmitTypes() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("14-return-to-source.json");
        Long iid = startFlow(def, FlowData.create()).getInstanceId();
        ProcessTask taskA = doing(iid, "taskA");
        engine.executeAndJumpToFirstTaskNode(taskA.getTaskId(), "userA", submit(RETURN));
        ProcessTask apply = doing(iid, "apply");

        try {
            engine.executeProcessTask(apply.getTaskId(), "applicant", submit(AGREE));
            fail("直返申请待办不得用普通同意绕过");
        } catch (JeeflowException e) {
            assertTrue(e.getMessage().contains("重新提交"));
        }
        assertTrue("拒绝后申请待办仍可处理", doing(iid, "apply") != null);
        assertNull("拒绝即不建来源任务", doing(iid, "taskA"));
    }

    @Test
    public void test03_reapplyRejectsNonApplicant() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("14-return-to-source.json");
        Long iid = startFlow(def, FlowData.create()).getInstanceId();
        ProcessTask taskA = doing(iid, "taskA");
        engine.executeAndJumpToFirstTaskNode(taskA.getTaskId(), "userA", submit(RETURN));
        ProcessTask apply = doing(iid, "apply");
        try {
            engine.executeProcessTask(apply.getTaskId(), "userB", submit(REAPPLY));
            fail("非办理人不得重提");
        } catch (JeeflowException e) {
            assertNotNull(doing(iid, "apply"));
        }
    }

    // ═══ 10.2 条件分支 ═══

    @Test
    public void test04_conditionNotReevaluated() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("16-return-to-source-decision.json");
        Long iid = startFlow(def, FlowData.create().set("amount", 5000)).getInstanceId();
        ProcessTask high = doing(iid, "taskHigh");
        assertNotNull("金额>1000 应走 taskHigh", high);
        assertNull(doing(iid, "taskLow"));

        engine.executeAndJumpToFirstTaskNode(high.getTaskId(), "userH", submit(RETURN));
        // 申请人把条件字段改成 100，重提仍回 taskHigh（不重算来源前的条件）
        FlowData reapplyArgs = submit(REAPPLY).set("amount", 100);
        engine.executeProcessTask(doing(iid, "apply").getTaskId(), "applicant", reapplyArgs);
        ProcessTask again = doing(iid, "taskHigh");
        assertNotNull("重提必须回原来源节点", again);
        assertNull("不得按修改后的条件改走 taskLow", doing(iid, "taskLow"));

        // 来源之后的二级条件按正常数据求值：level>1 走 taskX
        engine.executeProcessTask(again.getTaskId(), "userH", submit(AGREE).set("level", 2));
        assertNotNull("来源后的条件节点正常执行", doing(iid, "taskX"));
        assertNull(doing(iid, "taskY"));
    }

    // ═══ 10.3 并行分支 ═══

    @Test
    public void test05_joinWaitsWhileApplicantEditing() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("15-return-to-source-fork.json");
        Long iid = startFlow(def, FlowData.create()).getInstanceId();
        ProcessTask taskA = doing(iid, "taskA");
        ProcessTask taskB = doing(iid, "taskB");
        assertNotNull(taskA);
        assertNotNull(taskB);

        engine.executeAndJumpToFirstTaskNode(taskA.getTaskId(), "userA", submit(RETURN));
        assertNotNull(doing(iid, "apply"));
        assertNotNull("另一分支可继续", doing(iid, "taskB"));

        // D 办完到汇合点：申请人还在修改，不得放行汇合
        engine.executeProcessTask(taskB.getTaskId(), "userB", submit(AGREE));
        assertNull("来源分支仍在申请人手里，汇合不得放行", doing(iid, "taskC"));
        assertEquals(ProcessInstanceStateEnum.DOING.getCode(), repo.findInstanceById(iid).getState());

        // A 重提只创建新 C，不重建 D、不重触发分叉
        engine.executeProcessTask(doing(iid, "apply").getTaskId(), "applicant", submit(REAPPLY));
        ProcessTask newA = doing(iid, "taskA");
        assertNotNull(newA);
        assertNull("不重建 D", doing(iid, "taskB"));
        assertEquals("D 已办记录不变", 1, rows(iid, "taskB"));
        assertNull("重提本身不放行汇合", doing(iid, "taskC"));

        // 新 C 办完才汇合，且只放行一次
        engine.executeProcessTask(newA.getTaskId(), "userA", submit(AGREE));
        assertEquals("汇合后只建一条负责人待办", 1, doingAll(iid).size());
        assertEquals("taskC", doingAll(iid).get(0).getTaskName());
        assertEquals(1, rows(iid, "taskC"));
    }

    @Test
    public void test06_joinAfterSourceCompletesFirst() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("15-return-to-source-fork.json");
        Long iid = startFlow(def, FlowData.create()).getInstanceId();
        ProcessTask taskA = doing(iid, "taskA");
        ProcessTask taskB = doing(iid, "taskB");
        engine.executeAndJumpToFirstTaskNode(taskA.getTaskId(), "userA", submit(RETURN));
        engine.executeProcessTask(doing(iid, "apply").getTaskId(), "applicant", submit(REAPPLY));
        ProcessTask newA = doing(iid, "taskA");
        engine.executeProcessTask(newA.getTaskId(), "userA", submit(AGREE));
        assertNull("另一支未办完，不得先放行", doing(iid, "taskC"));
        engine.executeProcessTask(taskB.getTaskId(), "userB", submit(AGREE));
        assertEquals("另一种完成顺序同样只放行一次", 1, rows(iid, "taskC"));
    }

    @Test
    public void test07_twoBranchesReturnIndependently() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("15-return-to-source-fork.json");
        Long iid = startFlow(def, FlowData.create()).getInstanceId();
        engine.executeAndJumpToFirstTaskNode(doing(iid, "taskA").getTaskId(), "userA", submit(RETURN));
        engine.executeAndJumpToFirstTaskNode(doing(iid, "taskB").getTaskId(), "userB", submit(RETURN));

        List<ProcessTask> applies = repo.findDoingTasks(iid, new String[]{"apply"});
        assertEquals("两条分支分别退回产生两条申请待办", 2, applies.size());
        Long applyA = applyFor(applies, "taskA");
        Long applyB = applyFor(applies, "taskB");
        assertNotNull(applyA);
        assertNotNull(applyB);

        // 只恢复 A 的来源：B 仍在等待
        engine.executeProcessTask(applyA, "applicant", submit(REAPPLY));
        assertEquals("B 的申请待办不受影响", 1, repo.findDoingTasks(iid, new String[]{"apply"}).size());
        engine.executeProcessTask(doing(iid, "taskA").getTaskId(), "userA", submit(AGREE));
        assertNull("B 仍在修改，汇合不得放行", doing(iid, "taskC"));

        engine.executeProcessTask(applyB, "applicant", submit(REAPPLY));
        ProcessTask newB = doing(iid, "taskB");
        assertNotNull(newB);
        engine.executeProcessTask(newB.getTaskId(), "userB", submit(AGREE));
        assertEquals(1, rows(iid, "taskC"));
    }

    private Long applyFor(List<ProcessTask> applies, String sourceNode) {
        for (ProcessTask t : applies) {
            Map<String, Object> ctx = ReapplySupport.reapplyOf(t);
            if (ctx != null && sourceNode.equals(ctx.get("sourceNode"))) {
                return t.getTaskId();
            }
        }
        return null;
    }

    // ═══ 重复退回 / 并发 / 重启 / 回归 / 非法配置 ═══

    @Test
    public void test08_repeatedReturnBindsItsOwnRound() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("14-return-to-source.json");
        Long iid = startFlow(def, FlowData.create()).getInstanceId();
        engine.executeAndJumpToFirstTaskNode(doing(iid, "taskA").getTaskId(), "userA", submit(RETURN));
        engine.executeProcessTask(doing(iid, "apply").getTaskId(), "applicant", submit(REAPPLY));
        ProcessTask round2 = doing(iid, "taskA");

        // 第二轮再退回，绑定本轮来源
        engine.executeAndJumpToFirstTaskNode(round2.getTaskId(), "userA", submit(RETURN));
        ProcessTask apply2 = doing(iid, "apply");
        Map<String, Object> ctx = ReapplySupport.reapplyOf(apply2);
        assertEquals(String.valueOf(round2.getTaskId()), String.valueOf(ctx.get("sourceTaskId")));
        // 已消费的旧申请任务不能再次恢复分支
        assertFalse(repo.findTaskById(apply2.getTaskId()).isFinished());
        assertEquals(2, rows(iid, "taskA"));
    }

    @Test
    public void test09_concurrentReapplyOnlyOnce() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("14-return-to-source.json");
        Long iid = startFlow(def, FlowData.create()).getInstanceId();
        engine.executeAndJumpToFirstTaskNode(doing(iid, "taskA").getTaskId(), "userA", submit(RETURN));
        final Long applyId = doing(iid, "apply").getTaskId();

        final CountDownLatch gate = new CountDownLatch(1);
        final AtomicInteger ok = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();
        Runnable r = () -> {
            try {
                gate.await();
                engine.executeProcessTask(applyId, "applicant", submit(REAPPLY));
                ok.incrementAndGet();
            } catch (Exception e) {
                failed.incrementAndGet();
            }
        };
        Thread t1 = new Thread(r);
        Thread t2 = new Thread(r);
        t1.start(); t2.start(); gate.countDown();
        t1.join(); t2.join();

        assertEquals("同一申请任务并发重提至多一个成功", 1, ok.get());
        assertEquals(1, failed.get());
        assertEquals("只产生一组新待办", 1, doingAll(iid).size());
        assertEquals(1, repo.findDoingTasks(iid, new String[]{"taskA"}).size());
    }

    @Test
    public void test10_stateSurvivesEngineRecreate() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("14-return-to-source.json");
        Long iid = startFlow(def, FlowData.create()).getInstanceId();
        engine.executeAndJumpToFirstTaskNode(doing(iid, "taskA").getTaskId(), "userA", submit(RETURN));
        Long applyId = doing(iid, "apply").getTaskId();

        // 新引擎实例（所有状态只能从仓储恢复，验证不依赖引擎内存）
        JeeflowEngine restarted = new JeeflowEngineImpl();
        restarted.configure(config);
        restarted.executeProcessTask(applyId, "applicant", submit(REAPPLY));
        assertNotNull(restarted);
        assertEquals("重启后仍能回到来源节点", "taskA", doingAll(iid).get(0).getTaskName());
    }

    @Test
    public void test11_legacyTwoStepReapplyUnchanged() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("04-fork-join.json");
        Long iid = startFlow(def, FlowData.create()).getInstanceId();
        engine.executeAndJumpToFirstTaskNode(doing(iid, "taskA").getTaskId(), "userA", submit(RETURN));
        ProcessTask apply = doing(iid, "apply");
        assertNull("未配置直返的节点不得生成直返上下文", ReapplySupport.reapplyOf(apply));

        engine.executeProcessTask(apply.getTaskId(), "applicant", submit(REAPPLY));
        // 原 taskB 仍在办（另一分支），普通重提重新分叉再建 A/B ⇒ 3 条进行中
        assertEquals("普通重提仍按流程重新审批（重新分叉）", 3, doingAll(iid).size());
    }

    @Test
    public void test12_forgedReservedKeyIgnored() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("04-fork-join.json");
        Long iid = startFlow(def, FlowData.create()).getInstanceId();
        engine.executeAndJumpToFirstTaskNode(doing(iid, "taskA").getTaskId(), "userA", submit(RETURN));
        FlowData forged = submit(REAPPLY);
        forged.set(ReapplySupport.VAR_REAPPLY_CTX, FlowData.create().set("sourceTaskId", "999"));
        forged.set(ReapplySupport.VAR_BRANCH_CTX, Arrays.asList(FlowData.create().set("join", "join1")));
        engine.executeProcessTask(doing(iid, "apply").getTaskId(), "applicant", forged);
        assertEquals("伪造控制键不得改变路由", 3, doingAll(iid).size());
        for (ProcessTask t : doingAll(iid)) {
            assertNull("控制键不得写入业务任务的直返上下文", ReapplySupport.reapplyOf(t));
            // 分支归属只能是引擎按模型算出的完整档（fork+join），不是客户端伪造的半档
            for (Map<String, Object> scope : ReapplySupport.scopesOf(t)) {
                assertEquals("fork1", scope.get("fork"));
                assertEquals("join1", scope.get("join"));
            }
        }
    }

    @Test
    public void test13_invalidConfigFailsLoudly() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlowFrom("flows-invalid", "17-return-to-source-bad-config.json");
        try {
            engine.startProcessInstanceById(def.getId(), "applicant", FlowData.create());
            fail("非法 reapplyMode 必须在解析期报错");
        } catch (JeeflowException e) {
            assertTrue(e.getMessage().contains("reapplyMode"));
        }
    }

    @Test
    public void test14_assigneeReResolvedOnReapply() throws Exception {
        String jsonStr = "{\"name\":\"rj-assignee\",\"displayName\":\"\u91cd\u63d0\u6362\u4eba\",\"type\":\"approval\",\"nodes\":["
            + "{\"id\":\"start\",\"type\":\"snaker:start\",\"properties\":{},\"text\":{\"value\":\"\u5f00\u59cb\"}},"
            + "{\"id\":\"apply\",\"type\":\"snaker:task\",\"properties\":{\"assignee\":\"applicant\",\"taskType\":0,\"performType\":0},\"text\":{\"value\":\"\u53d1\u8d77\"}},"
            + "{\"id\":\"taskA\",\"type\":\"snaker:task\",\"properties\":{\"assignee\":\"f_approver\",\"taskType\":0,\"performType\":0,\"reapplyMode\":\"RETURN_TO_SOURCE\"},\"text\":{\"value\":\"\u8d22\u52a1\u5ba1\u6279\"}},"
            + "{\"id\":\"end\",\"type\":\"snaker:end\",\"properties\":{},\"text\":{\"value\":\"\u7ed3\u675f\"}}],"
            + "\"edges\":[{\"id\":\"e0\",\"sourceNodeId\":\"start\",\"targetNodeId\":\"apply\",\"properties\":{}},"
            + "{\"id\":\"e1\",\"sourceNodeId\":\"apply\",\"targetNodeId\":\"taskA\",\"properties\":{}},"
            + "{\"id\":\"e2\",\"sourceNodeId\":\"taskA\",\"targetNodeId\":\"end\",\"properties\":{}}]}";
        ProcessInstance.ProcessDefine def = registerFlowJson(jsonStr);
        Long iid = startFlow(def, FlowData.create().set("f_approver", "userA")).getInstanceId();
        ProcessTask taskA = doing(iid, "taskA");
        assertNotNull(taskA);
        assertTrue("首次审批人来自 f_approver", repo.findTaskActors(taskA.getTaskId()).contains("userA"));

        engine.executeAndJumpToFirstTaskNode(taskA.getTaskId(), "userA", submit(RETURN));
        ProcessTask apply = doing(iid, "apply");
        assertNotNull(apply);
        // 重提时把审批人字段改为 userZ：新来源待办应按目标节点规则重新解析，不锁定原退回人
        engine.executeProcessTask(apply.getTaskId(), "applicant", submit(REAPPLY).set("f_approver", "userZ"));
        ProcessTask newA = doing(iid, "taskA");
        assertNotNull(newA);
        java.util.List<String> actors = repo.findTaskActors(newA.getTaskId());
        assertTrue("新审批人应重新解析为 userZ, 实际=" + actors, actors.contains("userZ"));
        assertFalse("不应锁定原退回人 userA, 实际=" + actors, actors.contains("userA"));
    }
    @Test
    public void test15_nestedForkKeepsOwnership() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("18-return-to-source-nested.json");
        Long iid = startFlow(def, FlowData.create()).getInstanceId();
        ProcessTask taskA = doing(iid, "taskA");
        ProcessTask taskB = doing(iid, "taskB");
        assertNotNull(taskA);
        assertNotNull(taskB);

        // 外层分支 A 进入内层并行组
        engine.executeProcessTask(taskA.getTaskId(), "userA", submit(AGREE));
        ProcessTask taskX = doing(iid, "taskX");
        ProcessTask taskY = doing(iid, "taskY");
        assertNotNull(taskX);
        assertNotNull(taskY);

        // 内层分支 X 退回发起人
        engine.executeAndJumpToFirstTaskNode(taskX.getTaskId(), "userX", submit(RETURN));
        assertNotNull(doing(iid, "apply"));

        // 内层另一支 Y 办完：内层汇合必须等 X 分支
        engine.executeProcessTask(taskY.getTaskId(), "userY", submit(AGREE));
        assertNull("内层汇合不得在来源分支修改中放行", doing(iid, "taskA2"));

        // 重提只重建 X，不重建 Y，也不重放内层分叉
        engine.executeProcessTask(doing(iid, "apply").getTaskId(), "applicant", submit(REAPPLY));
        ProcessTask newX = doing(iid, "taskX");
        assertNotNull(newX);
        assertNull("不重建 Y", doing(iid, "taskY"));
        assertNull("重提本身不放行内层汇合", doing(iid, "taskA2"));

        engine.executeProcessTask(newX.getTaskId(), "userX", submit(AGREE));
        assertEquals("内层汇合只放行一次", 1, rows(iid, "taskA2"));

        // 外层：A 分支已到外层汇合，但 B 分支仍在办
        engine.executeProcessTask(doing(iid, "taskA2").getTaskId(), "userA", submit(AGREE));
        assertNull("外层汇合等待 B 分支", doing(iid, "taskC"));
        engine.executeProcessTask(taskB.getTaskId(), "userB", submit(AGREE));
        assertEquals("外层汇合只放行一次", 1, rows(iid, "taskC"));
    }

    // ═══ 10.4 负向：带标记申请待办不得走其他办理/跳转入口 ═══

    @Test
    public void test16_markedApplicantRejectsReturnAndJumps() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("14-return-to-source.json");
        Long iid = startFlow(def, FlowData.create()).getInstanceId();
        Long sourceId = doing(iid, "taskA").getTaskId();
        engine.executeAndJumpToFirstTaskNode(sourceId, "userA", submit(RETURN));
        ProcessTask app = doing(iid, "apply");
        assertNotNull(app);
        int before = doingAll(iid).size();
        long histBefore = repo.findHistoryTasks(iid).size();

        assertMarkedRejected(() -> engine.executeAndJumpToFirstTaskNode(
                app.getTaskId(), "applicant", submit(RETURN)));
        assertMarkedRejected(() -> engine.executeAndJumpTask(
                app.getTaskId(), "applicant", submit(ProcessSubmitTypeEnum.ROLLBACK.getCode()), null));
        assertMarkedRejected(() -> engine.executeAndJumpTask(
                app.getTaskId(), "applicant", submit(ProcessSubmitTypeEnum.JUMP.getCode()), "taskB"));
        assertMarkedRejected(() -> engine.executeAndJumpToEnd(
                app.getTaskId(), "applicant", submit(ProcessSubmitTypeEnum.REJECT.getCode())));

        assertEquals("拒绝后不新增任务", before, doingAll(iid).size());
        assertEquals("拒绝后不新增历史行", histBefore, repo.findHistoryTasks(iid).size());
        ProcessTask still = doing(iid, "apply");
        assertNotNull("原申请待办仍在", still);
        assertEquals("原等待上下文仍在", sourceId.toString(),
                ReapplySupport.reapplyOf(repo.findTaskById(app.getTaskId())).get("sourceTaskId").toString());
        assertEquals("来源任务保持完成态", 1, rows(iid, "taskA"));

        // 正常重提仍然可用
        engine.executeProcessTask(app.getTaskId(), "applicant", submit(REAPPLY));
        assertNotNull(doing(iid, "taskA"));
    }

    private void assertMarkedRejected(Runnable action) {
        try {
            action.run();
            fail("带直返标记的申请待办不应被其他入口接受");
        } catch (JeeflowException e) {
            assertTrue("错误应说明只接受重提: " + e.getMessage(),
                    e.getMessage().contains("只接受重新提交"));
        }
    }

    // ═══ 5.2 第 6 条：建单拦截器与完成节点 post 拦截器 ═══

    public static class CountingFlowInterceptor implements com.mldong.jeeflow.interceptor.FlowInterceptor {
        static final AtomicInteger COUNT = new AtomicInteger();
        @Override public void intercept(com.mldong.jeeflow.core.Execution execution) {
            COUNT.incrementAndGet();
        }
    }

    public static class CountingPostInterceptor implements com.mldong.jeeflow.interceptor.FlowInterceptor {
        static final AtomicInteger COUNT = new AtomicInteger();
        @Override public void intercept(com.mldong.jeeflow.core.Execution execution) {
            COUNT.incrementAndGet();
        }
    }

    @Test
    public void test17_directPathsRunInterceptorsAndPostHooks() throws Exception {
        String cls = CountingPostInterceptor.class.getName();
        String json = new String(Files.readAllBytes(Paths.get(
                "src/test/resources/flows/14-return-to-source.json")), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\"form\": \"apply-form\"", "\"form\": \"apply-form\", \"postInterceptors\": \"" + cls + "\"")
                .replace("\"form\": \"form-a\"", "\"form\": \"form-a\", \"postInterceptors\": \"" + cls + "\"");
        ProcessInstance.ProcessDefine def = registerFlowJson(json);
        ServiceContext.put("countingFlowInterceptor", new CountingFlowInterceptor());

        Long iid = startFlow(def, FlowData.create()).getInstanceId();
        CountingFlowInterceptor.COUNT.set(0);
        CountingPostInterceptor.COUNT.set(0);

        engine.executeAndJumpToFirstTaskNode(doing(iid, "taskA").getTaskId(), "userA", submit(RETURN));
        assertEquals("直返建申请待办触发一次建单拦截器", 1, CountingFlowInterceptor.COUNT.get());
        assertEquals("来源节点办结触发一次 post 拦截器", 1, CountingPostInterceptor.COUNT.get());

        engine.executeProcessTask(doing(iid, "apply").getTaskId(), "applicant", submit(REAPPLY));
        assertEquals("重提建新来源待办再触发一次建单拦截器", 2, CountingFlowInterceptor.COUNT.get());
        assertEquals("申请节点办结触发一次 post 拦截器", 2, CountingPostInterceptor.COUNT.get());
        assertNotNull("重提确实建出新来源待办", doing(iid, "taskA"));
    }
}
