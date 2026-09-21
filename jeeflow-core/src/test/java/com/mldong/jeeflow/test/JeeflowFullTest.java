package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
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
import com.mldong.jeeflow.spi.IExpressionEvaluator;
import com.mldong.jeeflow.spi.IUserProvider;
import com.mldong.jeeflow.spi.IUserProvider.UserInfo;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * jeeflow 全场景测试 — 10 个流程定义覆盖所有节点类型
 */
public class JeeflowFullTest {

    private JeeflowEngine engine;
    private MemoryProcessRepository repo;
    private TestJsonProvider json;

    @Before
    public void setUp() {
        Configuration config = new Configuration();
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

    /** 读取 JSON 文件并注册流程定义 */
    private ProcessInstance.ProcessDefine registerFlow(String filename) throws Exception {
        byte[] bytes = Files.readAllBytes(
                Paths.get("src/test/resources/flows/" + filename));
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

    /**
     * 模拟 demo 的 startAndExecute 契约：
     * 启动后自动完成申请节点（assignee="applicant" → 发起人），流程推进到第一个业务节点。
     */
    private ProcessInstance startFlow(ProcessInstance.ProcessDefine def, FlowData args) throws Exception {
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "applicant", args);
        for (ProcessTask task : repo.findDoingTasks(inst.getInstanceId(), null)) {
            repo.addTaskActor(task.getTaskId(), Arrays.asList("applicant"));
            task.getActorIds().add("applicant");
            engine.executeProcessTask(task.getTaskId(), "applicant",
                    FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.APPLY.getCode()));
        }
        return repo.findInstanceById(inst.getInstanceId());
    }

    // ═══════════════════════════════════════════
    // 测试 1：简单线性流程 Start → Task → End
    // ═══════════════════════════════════════════
    @Test
    public void test01SimpleFlow() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("01-simple.json");

        // 启动
        ProcessInstance inst = startFlow(def, FlowData.create());
        assertNotNull(inst);
        assertEquals(ProcessInstanceStateEnum.DOING.getCode(), inst.getState());

        // 查待办
        List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), null);
        assertEquals(1, doing.size());
        ProcessTask task = doing.get(0);
        assertEquals("task1", task.getTaskName());

        // 添加参与者并完成
        repo.addTaskActor(task.getTaskId(), Arrays.asList("leader"));
        task.getActorIds().add("leader");

        FlowData taskArgs = FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode());
        engine.executeProcessTask(task.getTaskId(), "leader", taskArgs);

        // 验证流程结束
        ProcessInstance updated = repo.findInstanceById(inst.getInstanceId());
        assertEquals(ProcessInstanceStateEnum.FINISHED.getCode(), updated.getState());
    }

    // ═══════════════════════════════════════════
    // 测试 16.5：建单不变量——task_parent_id 与 variable.isFirstTaskNode（issues/121 P1）
    // 夹具是 4 个任务节点的链：两步流里"上一节点"与"首任务节点"同格，断言恒真、测不出东西
    // ═══════════════════════════════════════════
    @Test
    public void test165CreateWritesLineageColumns() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("02-multi-task.json");
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "applicant", FlowData.create());
        Long iid = inst.getInstanceId();

        ProcessTask apply = doingByName(iid, "apply");
        // 发起 execution 没有当前任务 ⇒ parent 落 0；apply 是 start 直接后继 ⇒ 标记 true
        assertEquals("发起那条 parent 应为 0", Long.valueOf(0L), apply.getParentTaskId());
        assertEquals("首任务节点行应写 isFirstTaskNode=true", Boolean.TRUE,
                apply.getVariables().get(FlowConst.IS_FIRST_TASK_NODE));
        approve(apply, "applicant");

        ProcessTask t1 = doingByName(iid, "task1");
        assertEquals("task1 的 parent 应是刚办结的 apply", apply.getTaskId(), t1.getParentTaskId());
        assertEquals("非首节点行应写 false", Boolean.FALSE,
                t1.getVariables().get(FlowConst.IS_FIRST_TASK_NODE));
        approve(t1, "leader");

        ProcessTask t2 = doingByName(iid, "task2");
        assertEquals("链式血缘：task2.parent == task1.id", t1.getTaskId(), t2.getParentTaskId());
        approve(t2, "manager");

        ProcessTask t3 = doingByName(iid, "task3");
        assertEquals("链式血缘：task3.parent == task2.id", t2.getTaskId(), t3.getParentTaskId());

        // 本案真正要的那格：血缘版回退读的是**已办结的历史行**，标记必须随行存活。
        // 门面出口现算版带"仅进行中"判定，历史行上恒 false ⇒ 首节点回退会被错判成普通回退。
        ProcessTask hisApply = repo.findTaskById(apply.getTaskId());
        assertEquals("apply 应已办结", ProcessTaskStateEnum.FINISHED.getCode(), hisApply.getTaskState());
        assertEquals("历史行的标记必须还在", Boolean.TRUE,
                hisApply.getVariables().get(FlowConst.IS_FIRST_TASK_NODE));
        assertEquals("历史行的血缘指针不能被别的路径覆写", Long.valueOf(0L), hisApply.getParentTaskId());
    }

    /** 按任务名取该实例进行中的那条（仓储读回，不用聚合根内存对象） */
    private ProcessTask doingByName(Long instanceId, String name) {
        return repo.findDoingTasks(instanceId, null).stream()
                .filter(t -> name.equals(t.getTaskName())).findFirst()
                .orElseThrow(() -> new AssertionError("应有进行中的 " + name + " 任务"));
    }

    private void approve(ProcessTask task, String actor) throws Exception {
        repo.addTaskActor(task.getTaskId(), Arrays.asList(actor));
        task.getActorIds().add(actor);
        engine.executeProcessTask(task.getTaskId(), actor, FlowData.create()
                .set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));
    }

    // ═══════════════════════════════════════════
    // 测试 16.6/16.7/16.8：退回上一步＝血缘版（issues/121 P2）
    // ═══════════════════════════════════════════

    /** 正向：复活血缘前驱那条行——落点、参与者、parent 随行拷贝、控制类残留剔除 */
    @Test
    public void test166RollbackRevivesParentRow() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("02-multi-task.json");
        Long iid = startFlow(def, FlowData.create()).getInstanceId();
        ProcessTask t1 = doingByName(iid, "task1");
        approve(t1, "leader");
        ProcessTask t2 = doingByName(iid, "task2");

        engine.executeAndJumpTask(t2.getTaskId(), "manager", FlowData.create()
                .set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.ROLLBACK.getCode()), null);

        ProcessTask revived = doingByName(iid, "task1");
        assertFalse("复活应是一条新行，不是把原行改回 DOING",
                t1.getTaskId().equals(revived.getTaskId()));
        assertEquals("参与者＝上一步的原办结人，不再是执行回退的那个人",
                java.util.Collections.singletonList("leader"), repo.findTaskActors(revived.getTaskId()));
        assertEquals("parent 随行拷贝＝上一步的上一步", t1.getParentTaskId(), revived.getParentTaskId());
        assertFalse("复活行不该带 submitType 残留",
                revived.getVariables().containsKey(FlowConst.SUBMIT_TYPE));
        assertFalse("复活行不该带 taskName 残留", revived.getVariables().containsKey("taskName"));
        for (String k : revived.getVariables().keySet()) {
            assertFalse("复活行不该带 tf_ 残留: " + k, k.startsWith(FlowConst.TASK_FORM_DATA_PREFIX));
            assertFalse("复活行不该带会签簿记残留: " + k, k.startsWith(FlowConst.LOOP_COUNTER));
        }
        assertEquals("首节点标记随行留档（本例 task1 不是首节点）", Boolean.FALSE,
                revived.getVariables().get(FlowConst.IS_FIRST_TASK_NODE));
        assertEquals("回退后实例必须仍是 DOING", ProcessInstanceStateEnum.DOING.getCode(),
                repo.findInstanceById(iid).getState());
    }

    /** 负向：无血缘（parent 为 0/NULL，含 P1 之前落的老行）⇒ 20010007，且不得静默不建单 */
    @Test
    public void test167RollbackWithoutLineageThrows() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("02-multi-task.json");
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "applicant", FlowData.create());
        Long iid = inst.getInstanceId();
        ProcessTask apply = doingByName(iid, "apply");
        assertEquals("发起那条 parent 应为 0", Long.valueOf(0L), apply.getParentTaskId());
        int rows = repo.findInstanceById(iid).getTasks().size();
        try {
            engine.executeAndJumpTask(apply.getTaskId(), "applicant", FlowData.create()
                    .set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.ROLLBACK.getCode()), null);
            fail("无血缘必须报 20010007，不能静默不建单");
        } catch (com.mldong.jeeflow.JeeflowException e) {
            assertTrue("msg 应带 20010007 的文案: " + e.getMessage(),
                    e.getMessage().contains("上一步任务ID为空"));
        }
        // 内存仓储没有事务：序言已把当前任务置 20，异常不会回滚那一步（真事务仓储/JDBC 下整笔回滚）。
        // 这里能断的是「不建单」：总行数不变，没有凭空多出的复活行。
        assertEquals("报错即不建单，任务行总数不变",
                rows, repo.findInstanceById(iid).getTasks().size());

        // 老行形状：parent 为 NULL（P1 之前落的数据）同样必须报 20010007，不得静默通过。
        // 另起一条实例：上一段那次失败已把 apply 置为 FINISHED（内存仓储无事务），复用它会撞到"任务不在进行中"。
        ProcessInstance legacy = engine.startProcessInstanceById(def.getId(), "applicant", FlowData.create());
        ProcessTask legacyApply = doingByName(legacy.getInstanceId(), "apply");
        legacyApply.setParentTaskId(null);
        repo.updateTask(legacyApply);
        try {
            engine.executeAndJumpTask(legacyApply.getTaskId(), "applicant", FlowData.create()
                    .set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.ROLLBACK.getCode()), null);
            fail("parent=NULL 必须报 20010007");
        } catch (com.mldong.jeeflow.JeeflowException expected) {
            assertTrue("msg: " + expected.getMessage(), expected.getMessage().contains("上一步任务ID为空"));
        }
    }

    /** 负向：血缘守卫 canRejected——parent 是旁支（非祖先、且中间只穿 fork/join/start）⇒ 20010008 */
    @Test
    public void test168RollbackRejectsCrossBranchLineage() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("04-fork-join.json");
        Long iid = startFlow(def, FlowData.create()).getInstanceId();
        java.util.List<ProcessTask> doing = repo.findDoingTasks(iid, null);
        assertTrue("fork 后应至少两条并行 DOING，实得 " + doing.size(), doing.size() >= 2);
        ProcessTask a = doing.get(0);
        ProcessTask b = doing.get(1);
        // 伪造血缘：把 a 的 parent 指到旁支 b（b 不是 a 的祖先）⇒ 守卫必须拦住
        a.setParentTaskId(b.getTaskId());
        repo.updateTask(a);
        repo.addTaskActor(a.getTaskId(), java.util.Arrays.asList("forker"));
        a.getActorIds().add("forker");
        try {
            engine.executeAndJumpTask(a.getTaskId(), "forker", FlowData.create()
                    .set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.ROLLBACK.getCode()), null);
            fail("血缘指向旁支（非祖先）必须报 20010008");
        } catch (com.mldong.jeeflow.JeeflowException e) {
            assertTrue("msg 应带 20010008 的文案: " + e.getMessage(),
                    e.getMessage().contains("无法驳回至上一步处理"));
        }
    }

    // ═══════════════════════════════════════════
    // 测试 2：多级审批 Start → T1 → T2 → T3 → End
    // ═══════════════════════════════════════════
    @Test
    public void test02MultiTask() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("02-multi-task.json");

        ProcessInstance inst = startFlow(def, FlowData.create());

        // 依次完成三级审批
        String[] approvers = {"leader", "manager", "boss"};
        for (String approver : approvers) {
            List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), null);
            assertFalse("期望有待办任务", doing.isEmpty());
            ProcessTask task = doing.get(0);

            repo.addTaskActor(task.getTaskId(), Arrays.asList(approver));
            task.getActorIds().add(approver);

            FlowData args = FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode());
            engine.executeProcessTask(task.getTaskId(), approver, args);
        }

        ProcessInstance updated = repo.findInstanceById(inst.getInstanceId());
        assertEquals(ProcessInstanceStateEnum.FINISHED.getCode(), updated.getState());
    }

    // ═══════════════════════════════════════════
    // 测试 3：表达式决策 amount>1000 走 task2，否则走 task3
    // ═══════════════════════════════════════════
    @Test
    public void test03DecisionExpr() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("03-decision-expr.json");

        // 先完成 task1，带 amount=5000
        FlowData startArgs = FlowData.create().set("amount", 5000);
        ProcessInstance inst = startFlow(def, startArgs);

        // 完成 task1
        ProcessTask task1 = repo.findDoingTasks(inst.getInstanceId(), null).get(0);
        repo.addTaskActor(task1.getTaskId(), Arrays.asList("leader"));
        task1.getActorIds().add("leader");
        engine.executeProcessTask(task1.getTaskId(), "leader",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));

        // 决策后应该走到 task2（经理审批，因为 amount>1000）
        ProcessInstance updated = repo.findInstanceById(inst.getInstanceId());
        List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), null);
        assertFalse(doing.isEmpty());
        assertEquals("task2", doing.get(0).getTaskName());

        // 完成 task2
        ProcessTask task2 = doing.get(0);
        repo.addTaskActor(task2.getTaskId(), Arrays.asList("manager"));
        task2.getActorIds().add("manager");
        engine.executeProcessTask(task2.getTaskId(), "manager",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));

        updated = repo.findInstanceById(inst.getInstanceId());
        assertEquals(ProcessInstanceStateEnum.FINISHED.getCode(), updated.getState());
    }

    // ═══════════════════════════════════════════
    // 测试 4：并行分支合并 Fork → TaskA+TaskB → Join → End
    // ═══════════════════════════════════════════
    @Test
    public void test04ForkJoin() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("04-fork-join.json");

        ProcessInstance inst = startFlow(def, FlowData.create());

        // fork 后应该产生两个任务
        List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), null);
        assertEquals(2, doing.size());

        // 完成 taskA
        ProcessTask taskA = doing.stream().filter(t -> "taskA".equals(t.getTaskName())).findFirst().orElse(null);
        assertNotNull(taskA);
        repo.addTaskActor(taskA.getTaskId(), Arrays.asList("userA"));
        taskA.getActorIds().add("userA");
        engine.executeProcessTask(taskA.getTaskId(), "userA",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));

        // 完成 taskB
        ProcessTask taskB = repo.findDoingTasks(inst.getInstanceId(), null).get(0);
        assertEquals("taskB", taskB.getTaskName());
        repo.addTaskActor(taskB.getTaskId(), Arrays.asList("userB"));
        taskB.getActorIds().add("userB");
        engine.executeProcessTask(taskB.getTaskId(), "userB",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));

        // 合并后流程结束
        ProcessInstance updated = repo.findInstanceById(inst.getInstanceId());
        assertEquals(ProcessInstanceStateEnum.FINISHED.getCode(), updated.getState());
    }

    // ═══════════════════════════════════════════
    // 测试 5：并行会签
    // ═══════════════════════════════════════════
    @Test
    public void test05CountersignParallel() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("05-countersign-parallel.json");

        ProcessInstance inst = startFlow(def, FlowData.create());

        // 会签创建 3 个任务
        List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), null);
        assertEquals(3, doing.size());

        // 依次完成所有
        String[] actors = {"userA", "userB", "userC"};
        for (String actor : actors) {
            ProcessTask task = repo.findDoingTasks(inst.getInstanceId(), null).get(0);
            repo.addTaskActor(task.getTaskId(), Arrays.asList(actor));
            task.getActorIds().add(actor);
            engine.executeProcessTask(task.getTaskId(), actor,
                    FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));
        }

        ProcessInstance updated = repo.findInstanceById(inst.getInstanceId());
        assertEquals(ProcessInstanceStateEnum.FINISHED.getCode(), updated.getState());
    }

    // ═══════════════════════════════════════════
    // 测试 6：串行会签
    // ═══════════════════════════════════════════
    @Test
    public void test06CountersignSequential() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("06-countersign-sequential.json");

        ProcessInstance inst = startFlow(def, FlowData.create());

        // 串行会签逐个创建（issues/93）：发起后仅第一位成员 userA 的 DOING 任务，
        // userB 尚未创建（不做全员预创建）
        List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), null);
        assertEquals("串行会签发起后应只有 1 个 DOING 任务（userA）", 1, doing.size());
        assertEquals("首个会签成员应为 userA", "userA", doing.get(0).getActorIds().get(0));

        // 完成第一个（userA）→ 推进创建第二位（userB），仍只有 1 个 DOING
        ProcessTask task1 = doing.get(0);
        repo.addTaskActor(task1.getTaskId(), Arrays.asList("userA"));
        task1.getActorIds().add("userA");
        engine.executeProcessTask(task1.getTaskId(), "userA",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));
        List<ProcessTask> doingAfter = repo.findDoingTasks(inst.getInstanceId(), null);
        assertEquals("userA 完成后应推进为 1 个 DOING 任务（userB）", 1, doingAfter.size());
        assertEquals("推进后的会签成员应为 userB", "userB", doingAfter.get(0).getActorIds().get(0));

        // 完成第二个（userB，最后一位）→ 流转结束
        ProcessTask task2 = doingAfter.get(0);
        repo.addTaskActor(task2.getTaskId(), Arrays.asList("userB"));
        task2.getActorIds().add("userB");
        engine.executeProcessTask(task2.getTaskId(), "userB",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));

        ProcessInstance updated = repo.findInstanceById(inst.getInstanceId());
        assertEquals(ProcessInstanceStateEnum.FINISHED.getCode(), updated.getState());
    }


    // ═══════════════════════════════════════════
    // 测试 6.5：串行会签流转守卫（issues/44 E16）——全部成员完成才流转后续节点
    // ═══════════════════════════════════════════
    @Test
    public void test065CountersignSequentialNoRepeatFlow() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("08-countersign-sequential-approve.json");
        ProcessInstance inst = startFlow(def, FlowData.create());

        // 发起后：串行会签逐个创建（issues/93）——仅第一位 userA 的 DOING 会签任务，
        // apply 已由 startFlow 自动完成，userB 尚未创建
        List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), null);
        assertEquals("串行会签发起后应只有 1 个 DOING 任务（userA）", 1, doing.size());
        assertEquals("task1", doing.get(0).getTaskName());
        // 尚未流转到 approve
        assertTrue("会签未完成前不应有审批任务",
                repo.findDoingTasks(inst.getInstanceId(), null).stream()
                        .noneMatch(t -> "approve".equals(t.getTaskName())));

        // 完成第一个会签成员（userA）→ 推进为 userB，仍不应流转 approve（非全部完成，E16 守卫）
        ProcessTask task1 = doing.stream().filter(t -> "task1".equals(t.getTaskName())).findFirst().get();
        repo.addTaskActor(task1.getTaskId(), Arrays.asList("userA"));
        task1.getActorIds().add("userA");
        engine.executeProcessTask(task1.getTaskId(), "userA",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));
        assertTrue("完成一个成员后不应创建审批任务（E16 守卫）",
                repo.findDoingTasks(inst.getInstanceId(), null).stream()
                        .noneMatch(t -> "approve".equals(t.getTaskName())));
        assertEquals("userA 完成后应推进为 1 个 DOING 任务（userB）", 1,
                repo.findDoingTasks(inst.getInstanceId(), null).size());

        // 完成第二个会签成员（userB，最后一位）→ 流转一次：approve 仅 1 个任务
        List<ProcessTask> doing2 = repo.findDoingTasks(inst.getInstanceId(), null);
        ProcessTask task2 = doing2.stream().filter(t -> "task1".equals(t.getTaskName())).findFirst().get();
        repo.addTaskActor(task2.getTaskId(), Arrays.asList("userB"));
        task2.getActorIds().add("userB");
        engine.executeProcessTask(task2.getTaskId(), "userB",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));
        List<ProcessTask> approveTasks = repo.findDoingTasks(inst.getInstanceId(), null).stream()
                .filter(t -> "approve".equals(t.getTaskName())).collect(java.util.stream.Collectors.toList());
        assertEquals("全部完成才流转：approve 应只有 1 个任务", 1, approveTasks.size());

        // 完成审批 → 流程结束
        ProcessTask approve = approveTasks.get(0);
        repo.addTaskActor(approve.getTaskId(), Arrays.asList("leader"));
        approve.getActorIds().add("leader");
        engine.executeProcessTask(approve.getTaskId(), "leader",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));
        ProcessInstance updated = repo.findInstanceById(inst.getInstanceId());
        assertEquals(ProcessInstanceStateEnum.FINISHED.getCode(), updated.getState());
    }


    // ═══════════════════════════════════════════
    // 测试 6.6：办理时抄送（issues/47 E19）——tf_ccActors 创建 cc 实例
    // ═══════════════════════════════════════════
    @Test
    public void test066ExecuteTaskCcActors() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("01-simple.json");
        ProcessInstance inst = startFlow(def, FlowData.create());

        // 办理 task1 时提交 tf_ccActors → 创建抄送
        List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), null);
        ProcessTask task1 = doing.stream().filter(t -> "task1".equals(t.getTaskName())).findFirst().get();
        repo.addTaskActor(task1.getTaskId(), java.util.Arrays.asList("leader"));
        task1.getActorIds().add("leader");
        engine.executeProcessTask(task1.getTaskId(), "leader",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode())
                        .set(FlowConst.CC_ACTORS, "wangqiang,zhaomin"));

        // 抄送已创建（内存仓储 ccInstances 查询）
        com.mldong.jeeflow.spi.PageResult<com.mldong.jeeflow.spi.IProcessRepository.InstanceRow> cc =
                repo.pageCcInstances(new com.mldong.jeeflow.spi.PageQuery());
        List<com.mldong.jeeflow.spi.IProcessRepository.InstanceRow> hit = cc.getRows().stream()
                .filter(r -> r.getId() != null && r.getId().equals(inst.getInstanceId())).collect(java.util.stream.Collectors.toList());
        assertFalse("办理时抄送应创建 cc 实例: " + cc.getRows(), hit.isEmpty());
    }

    // ═══════════════════════════════════════════
    // 测试 7：按比例会签（2人完成即通过）
    // ═══════════════════════════════════════════
    @Test
    public void test07CountersignRatio() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("07-countersign-ratio.json");

        ProcessInstance inst = startFlow(def, FlowData.create());

        // 4 个任务，只需完成 2 个
        List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), null);
        assertEquals(4, doing.size());

        // 完成 2 个即可
        for (int i = 0; i < 2; i++) {
            ProcessTask task = repo.findDoingTasks(inst.getInstanceId(), null).get(0);
            String actor = "user" + (char) ('A' + i);
            repo.addTaskActor(task.getTaskId(), Arrays.asList(actor));
            task.getActorIds().add(actor);
            engine.executeProcessTask(task.getTaskId(), actor,
                    FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));
        }

        ProcessInstance updated = repo.findInstanceById(inst.getInstanceId());
        assertEquals(ProcessInstanceStateEnum.FINISHED.getCode(), updated.getState());
    }

    // ═══════════════════════════════════════════
    // 测试 8：自定义节点
    // ═══════════════════════════════════════════
    @Test
    public void test08CustomNode() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("08-custom-node.json");

        ProcessInstance inst = startFlow(def, FlowData.create());

        // 自定义节点在当前实现中直接执行 runOutTransition（因为 invokeObject 为 null 时走 IHandler 检测）
        // 然后记录 history task，接着执行输出边 → end → finish
        ProcessInstance updated = repo.findInstanceById(inst.getInstanceId());
        // 流程可能已结束或产生了任务
        assertNotNull(updated);
    }

    // ═══════════════════════════════════════════
    // 测试 9：驳回场景（reject）
    // ═══════════════════════════════════════════
    @Test
    public void test09Reject() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("09-with-reject.json");

        ProcessInstance inst = startFlow(def, FlowData.create());

        // 完成 task1
        ProcessTask task1 = repo.findDoingTasks(inst.getInstanceId(), null).get(0);
        assertEquals("task1", task1.getTaskName());
        repo.addTaskActor(task1.getTaskId(), Arrays.asList("leader"));
        task1.getActorIds().add("leader");
        engine.executeProcessTask(task1.getTaskId(), "leader",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));

        // 在 task2 处驳回（boot2 契约：REJECT → executeAndJumpToEnd，实例 → 45）
        ProcessTask task2 = repo.findDoingTasks(inst.getInstanceId(), null).get(0);
        assertEquals("task2", task2.getTaskName());
        repo.addTaskActor(task2.getTaskId(), Arrays.asList("manager"));
        task2.getActorIds().add("manager");

        FlowData rejectArgs = FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.REJECT.getCode());
        engine.executeAndJumpToEnd(task2.getTaskId(), "manager", rejectArgs);

        ProcessInstance updated = repo.findInstanceById(inst.getInstanceId());
        assertEquals(ProcessInstanceStateEnum.REJECT.getCode(), updated.getState());
    }

    // ═══════════════════════════════════════════
    // 测试 10：跳转（jump to end）
    // ═══════════════════════════════════════════
    @Test
    public void test10Jump() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("01-simple.json");

        ProcessInstance inst = startFlow(def, FlowData.create());

        ProcessTask task1 = repo.findDoingTasks(inst.getInstanceId(), null).get(0);
        repo.addTaskActor(task1.getTaskId(), Arrays.asList("leader"));
        task1.getActorIds().add("leader");

        // 直接跳转到 end 节点
        engine.executeAndJumpToEnd(task1.getTaskId(), "leader", FlowData.create());

        ProcessInstance updated = repo.findInstanceById(inst.getInstanceId());
        assertEquals(ProcessInstanceStateEnum.FINISHED.getCode(), updated.getState());
    }

    // ═══════════════════════════════════════════
    // 测试 11：混合模式流程（综合场景）
    // ═══════════════════════════════════════════
    @Test
    public void test11MixedMode() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("10-mixed-mode.json");

        FlowData args = FlowData.create().set("finalAmount", 3000);
        ProcessInstance inst = startFlow(def, args);

        // start → apply → fork 出 task2 + task3
        List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), null);
        assertEquals(2, doing.size());

        // 完成 task2
        ProcessTask task2 = doing.stream().filter(t -> "task2".equals(t.getTaskName())).findFirst().orElse(null);
        assertNotNull(task2);
        repo.addTaskActor(task2.getTaskId(), Arrays.asList("checker"));
        task2.getActorIds().add("checker");
        engine.executeProcessTask(task2.getTaskId(), "checker",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));

        // 完成 task3
        ProcessTask task3 = repo.findDoingTasks(inst.getInstanceId(), null).get(0);
        assertEquals("task3", task3.getTaskName());
        repo.addTaskActor(task3.getTaskId(), Arrays.asList("reviewer"));
        task3.getActorIds().add("reviewer");
        engine.executeProcessTask(task3.getTaskId(), "reviewer",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));

        // 合并后决策——finalAmount=3000 <= 5000，走 end2
        ProcessInstance updated = repo.findInstanceById(inst.getInstanceId());
        assertEquals(ProcessInstanceStateEnum.FINISHED.getCode(), updated.getState());
    }

    // ═══════════════════════════════════════════
    // 测试 12：边界——未注册参与者不能执行
    // ═══════════════════════════════════════════
    @Test
    public void test12ActorNotAllowed() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("01-simple.json");

        ProcessInstance inst = startFlow(def, FlowData.create());
        ProcessTask task = repo.findDoingTasks(inst.getInstanceId(), null).get(0);

        // 没有添加参与者就执行，应抛异常
        try {
            engine.executeProcessTask(task.getTaskId(), "stranger",
                    FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));
            fail("应该抛出异常");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("参与者") || e.getMessage().contains("NOT_ALLOWED"));
        }
    }

    // ═══════════════════════════════════════════
    // 测试 13：FlowData 值对象
    // ═══════════════════════════════════════════
    @Test
    public void test13FlowData() {
        FlowData data = FlowData.create();
        data.set("name", "张三");
        data.set("age", 30);
        data.set("active", true);
        data.set("amount", 1234.56);

        assertEquals("张三", data.getStr("name"));
        assertEquals(Integer.valueOf(30), data.getInt("age"));
        assertEquals(Boolean.TRUE, data.getBool("active"));
        assertEquals(Long.valueOf(1234), data.getLong("amount"));

        // copy 不互相影响
        FlowData copy = data.copy();
        copy.set("name", "李四");
        assertEquals("张三", data.getStr("name"));
        assertEquals("李四", copy.getStr("name"));
    }

    // ═══════════════════════════════════════════
    // 测试 14：验证 CreateTaskHandler 是否自动继承 assignee 为 actor
    // ═══════════════════════════════════════════
    @Test
    public void test14ActorFromAssignee() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("01-simple.json");

        // 启动流程——不手动设置 actor
        ProcessInstance inst = startFlow(def, FlowData.create());

        // 获取创建的任务
        List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), null);
        assertEquals("应创建1个任务", 1, doing.size());
        ProcessTask task = doing.get(0);

        // 验证：任务应该自动有 assignee("leader") 作为 actor
        assertNotNull("任务参与者不应为空", task.getActorIds());
        assertFalse("任务参与者不应为空列表", task.getActorIds().isEmpty());
        assertTrue("任务参与者应包含 leader", task.getActorIds().contains("leader"));
    }

    // ═══════════════════════════════════════════
    // 测试 15：assignee 变量解析（v1.0.1，集成反馈③）
    // token 即变量 key：命中用值（集合展开）、未命中字面量；tf_nextNodeOperator 优先
    // ═══════════════════════════════════════════
    @Test
    public void test15AssigneeVariableResolution() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("11-assignee-vars.json");

        // ① deptLeader 变量命中 → 参与者 = 变量值
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "applicant",
                FlowData.create().set("deptLeader", "L001"));
        ProcessTask apply = repo.findDoingTasks(inst.getInstanceId(), null).get(0);
        assertEquals("applicant", apply.getActorIds().get(0));
        engine.executeProcessTask(apply.getTaskId(), "applicant",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.APPLY.getCode()));
        List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), null);
        assertEquals("task1", doing.get(0).getTaskName());
        assertEquals("变量命中应解析为变量值", Arrays.asList("L001"), doing.get(0).getActorIds());

        // ② 静态字面量 userA,userB（变量未命中）
        engine.executeProcessTask(doing.get(0).getTaskId(), "L001",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));
        doing = repo.findDoingTasks(inst.getInstanceId(), null);
        assertEquals("task2", doing.get(0).getTaskName());
        assertEquals("静态字面量参与者", Arrays.asList("userA", "userB"), doing.get(0).getActorIds());

        // ③ 变量未传入 → token 字面量回退（对齐 boot3 args.get(token, token)）
        def = registerFlow("11-assignee-vars.json");
        inst = engine.startProcessInstanceById(def.getId(), "applicant", FlowData.create());
        apply = repo.findDoingTasks(inst.getInstanceId(), null).get(0);
        engine.executeProcessTask(apply.getTaskId(), "applicant",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.APPLY.getCode()));
        doing = repo.findDoingTasks(inst.getInstanceId(), null);
        assertEquals("deptLeader 未传入应回退字面量", Arrays.asList("deptLeader"), doing.get(0).getActorIds());

        // ④ tf_nextNodeOperator 优先于 assignee
        def = registerFlow("11-assignee-vars.json");
        inst = engine.startProcessInstanceById(def.getId(), "applicant", FlowData.create());
        apply = repo.findDoingTasks(inst.getInstanceId(), null).get(0);
        engine.executeProcessTask(apply.getTaskId(), "applicant",
                FlowData.create()
                        .set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.APPLY.getCode())
                        .set(FlowConst.NEXT_NODE_OPERATOR, "BOSS1,BOSS2"));
        doing = repo.findDoingTasks(inst.getInstanceId(), null);
        assertEquals("tf_nextNodeOperator 应优先", Arrays.asList("BOSS1", "BOSS2"), doing.get(0).getActorIds());
    }

    // ═══════════════════════════════════════════
    // 测试 16：系统代执行 flow.auto / flow.admin（v1.0.1，集成反馈④）
    // ═══════════════════════════════════════════
    @Test
    public void test16SystemExecuteFlowAuto() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("11-assignee-vars.json");
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "applicant",
                FlowData.create().set("deptLeader", "L001"));
        ProcessTask apply = repo.findDoingTasks(inst.getInstanceId(), null).get(0);

        // ① flow.auto 非参与者身份放行（startAndExecute 契约）
        engine.executeProcessTask(apply.getTaskId(), FlowConst.AUTO_ID,
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.APPLY.getCode()));
        List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), null);
        assertEquals("flow.auto 应放行执行", "task1", doing.get(0).getTaskName());

        // ② 跳过 UserProvider 注入：u_userId 不会被替换成 flow.auto
        inst = repo.findInstanceById(inst.getInstanceId());
        assertEquals("flow.auto 执行应跳过用户注入", "applicant", inst.getVariables().getStr(FlowConst.USER_USER_ID));

        // ③ flow.admin 放行
        engine.executeProcessTask(doing.get(0).getTaskId(), FlowConst.ADMIN_ID,
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));
        doing = repo.findDoingTasks(inst.getInstanceId(), null);
        assertEquals("flow.admin 应放行执行", "task2", doing.get(0).getTaskName());
    }
}
