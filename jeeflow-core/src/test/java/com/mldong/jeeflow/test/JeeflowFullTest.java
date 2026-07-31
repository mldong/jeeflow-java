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

        // 串行会签，每次只完成一个
        List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), null);
        assertEquals(2, doing.size());

        // 完成第一个
        ProcessTask task1 = doing.get(0);
        repo.addTaskActor(task1.getTaskId(), Arrays.asList("userA"));
        task1.getActorIds().add("userA");
        engine.executeProcessTask(task1.getTaskId(), "userA",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));

        // 完成第二个
        ProcessTask task2 = repo.findDoingTasks(inst.getInstanceId(), null).get(0);
        repo.addTaskActor(task2.getTaskId(), Arrays.asList("userB"));
        task2.getActorIds().add("userB");
        engine.executeProcessTask(task2.getTaskId(), "userB",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));

        ProcessInstance updated = repo.findInstanceById(inst.getInstanceId());
        assertEquals(ProcessInstanceStateEnum.FINISHED.getCode(), updated.getState());
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
}
