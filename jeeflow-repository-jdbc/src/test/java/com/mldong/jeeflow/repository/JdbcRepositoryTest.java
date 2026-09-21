package com.mldong.jeeflow.repository;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessInstanceStateEnum;
import com.mldong.jeeflow.enums.ProcessTaskStateEnum;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
import com.mldong.jeeflow.facade.JeeflowFacade;
import com.mldong.jeeflow.json.IJsonProvider;
import com.mldong.jeeflow.json.TypeReference;
import com.mldong.jeeflow.spi.IExpressionEvaluator;
import com.mldong.jeeflow.spi.IUserProvider;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * JDBC 仓储集成测试——使用 H2 内存数据库验证全链路
 */
public class JdbcRepositoryTest {

    private JdbcDataSource ds;
    private JdbcProcessRepository repo;
    private JeeflowEngine engine;
    private ObjectMapper mapper;

    @Before
    public void setUp() throws Exception {
        // H2 内存数据库
        ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:jeeflow_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        // 执行 DDL
        String ddl = new String(Files.readAllBytes(
                Paths.get("src/test/resources/schema-h2.sql")), StandardCharsets.UTF_8);
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            for (String sql : ddl.split(";")) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) stmt.execute(trimmed);
            }
        }

        // Jackson JSON provider
        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        IJsonProvider jsonProvider = new IJsonProvider() {
            @Override public String toJson(Object obj) {
                try { return mapper.writeValueAsString(obj); } catch (Exception e) { throw new RuntimeException(e); }
            }
            @Override public <T> T fromJson(String json, Class<T> type) {
                try { return mapper.readValue(json, type); } catch (Exception e) { throw new RuntimeException(e); }
            }
            @Override @SuppressWarnings("unchecked")
            public <T> T fromJson(String json, TypeReference<T> typeRef) {
                try { return (T) mapper.readValue(json, mapper.constructType(typeRef.getType())); } catch (Exception e) { throw new RuntimeException(e); }
            }
            @Override public boolean isJson(String str) {
                return str != null && (str.trim().startsWith("{") || str.trim().startsWith("["));
            }
        };

        repo = new JdbcProcessRepository(ds);

        Configuration config = new Configuration();
        ServiceContext.put("repository", repo);
        ServiceContext.put("json", jsonProvider);
        ServiceContext.put("expr", new TestExprEvaluator());
        ServiceContext.put("user", new IUserProvider() {
            @Override public UserInfo getUser(String userId) {
                UserInfo u = UserInfo.of(userId);
                u.setDeptId("D01");
                u.setDeptName("XX部门");
                u.setPostId("P01");
                u.setPostName("XX岗位");
                return u;
            }
        });

        engine = new JeeflowEngineImpl();
        engine.configure(config);
    }

    @After
    public void tearDown() throws Exception {
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
        }
    }

    // ═══ 测试 1：简单流程 Start→Task→End（JDBC 持久化） ═══

    @Test
    public void testSimpleFlowJdbc() throws Exception {
        // 注册流程定义
        ProcessInstance.ProcessDefine def = registerSimpleFlow();

        // 启动
        FlowData args = FlowData.create().set(FlowConst.BUSINESS_NO, "JDBC-001");
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "user1", args);
        assertNotNull(inst);
        assertEquals(ProcessInstanceStateEnum.DOING.getCode(), inst.getState());

        // 重新从数据库加载验证
        ProcessInstance reloaded = repo.findInstanceById(inst.getInstanceId());
        assertNotNull(reloaded);
        assertEquals(inst.getInstanceId(), reloaded.getInstanceId());

        // 查待办
        List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), null);
        assertEquals(1, doing.size());
        ProcessTask task = doing.get(0);

        // 完成
        repo.addTaskActor(task.getTaskId(), Arrays.asList("leader"));
        task.getActorIds().add("leader");
        engine.executeProcessTask(task.getTaskId(), "leader",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));

        // 验证结束
        ProcessInstance updated = repo.findInstanceById(inst.getInstanceId());
        assertEquals(ProcessInstanceStateEnum.FINISHED.getCode(), updated.getState());
    }

    // ═══ 测试：addTaskActor 追加语义（对齐 boot2/boot3，issues/03） ═══

    @Test
    public void testAddTaskActorAppendJdbc() throws Exception {
        ProcessInstance.ProcessDefine def = registerSimpleFlow();
        FlowData args = FlowData.create().set(FlowConst.BUSINESS_NO, "JDBC-APPEND");
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "user1", args);
        ProcessTask task = repo.findDoingTasks(inst.getInstanceId(), null).get(0);

        // 首次添加
        repo.addTaskActor(task.getTaskId(), Arrays.asList("admin"));
        // 追加：新参与者 + 已存在的重复项
        repo.addTaskActor(task.getTaskId(), Arrays.asList("userB", "admin"));
        List<String> actors = repo.findTaskActors(task.getTaskId());

        // 初始参与者 leader 保留 + 追加的 admin/userB 不丢且不重复
        // （覆盖语义下 admin 会被清空，此处验证已修复）
        assertEquals(Arrays.asList("leader", "admin", "userB"), actors);
    }

    // ═══ 测试 2：多级审批 ═══

    @Test
    public void testMultiTaskJdbc() throws Exception {
        ProcessInstance.ProcessDefine def = registerMultiTaskFlow();

        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "applicant", FlowData.create());

        String[] approvers = {"leader", "manager", "boss"};
        for (String approver : approvers) {
            List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), null);
            assertFalse(doing.isEmpty());
            ProcessTask task = doing.get(0);
            repo.addTaskActor(task.getTaskId(), Arrays.asList(approver));
            task.getActorIds().add(approver);
            engine.executeProcessTask(task.getTaskId(), approver,
                    FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));
        }

        ProcessInstance updated = repo.findInstanceById(inst.getInstanceId());
        assertEquals(ProcessInstanceStateEnum.FINISHED.getCode(), updated.getState());
    }

    // ═══ 测试 3：决策表达式 ═══

    @Test
    public void testDecisionExprJdbc() throws Exception {
        ProcessInstance.ProcessDefine def = registerDecisionFlow();

        FlowData args = FlowData.create().set("amount", 5000);
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "applicant", args);

        // 完成第一个任务
        ProcessTask task1 = repo.findDoingTasks(inst.getInstanceId(), null).get(0);
        repo.addTaskActor(task1.getTaskId(), Arrays.asList("leader"));
        task1.getActorIds().add("leader");
        engine.executeProcessTask(task1.getTaskId(), "leader",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));

        // amount=5000 → 走 manager
        List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), null);
        assertEquals("manager", doing.get(0).getTaskName());

        // 完成
        ProcessTask task2 = doing.get(0);
        repo.addTaskActor(task2.getTaskId(), Arrays.asList("manager"));
        task2.getActorIds().add("manager");
        engine.executeProcessTask(task2.getTaskId(), "manager",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));

        ProcessInstance updated = repo.findInstanceById(inst.getInstanceId());
        assertEquals(ProcessInstanceStateEnum.FINISHED.getCode(), updated.getState());
    }

    // ═══ 测试 4：驳回场景（单任务流程，拒绝后直接结束） ═══

    @Test
    public void testRejectJdbc() throws Exception {
        ProcessInstance.ProcessDefine def = registerSimpleFlow();

        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "applicant", FlowData.create());

        // 完成并拒绝
        ProcessTask task1 = repo.findDoingTasks(inst.getInstanceId(), null).get(0);
        repo.addTaskActor(task1.getTaskId(), Arrays.asList("leader"));
        task1.getActorIds().add("leader");

        FlowData rejectArgs = FlowData.create()
                .set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.REJECT.getCode());
        engine.executeProcessTask(task1.getTaskId(), "leader", rejectArgs);

        ProcessInstance updated = repo.findInstanceById(inst.getInstanceId());
        assertEquals(ProcessInstanceStateEnum.REJECT.getCode(), updated.getState());
    }

    // ═══ 测试 5：抄送功能 ═══

    @Test
    public void testCcInstanceJdbc() throws Exception {
        ProcessInstance.ProcessDefine def = registerSimpleFlow();

        FlowData args = FlowData.create()
                .set(FlowConst.CC_ACTORS_START, "ccUser1,ccUser2");
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "user1", args);

        // 验证抄送记录（不能直接查询，但流程正常运行即可）
        assertNotNull(inst);
    }

    // ═══ 测试：流程定义写操作 CRUD（v1.0.1） ═══

    @Test
    public void testDefineCrud() throws Exception {
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName("crud-flow");
        def.setDisplayName("CRUD 流程");
        def.setType("test");
        def.setState(1);
        def.setVersion(1);
        def.setContent("{}".getBytes(StandardCharsets.UTF_8));
        def.setUpdateUser("tester");

        // save
        repo.saveDefine(def);
        assertNotNull(def.getId());
        ProcessInstance.ProcessDefine loaded = repo.findDefineById(def.getId());
        assertNotNull(loaded);
        assertEquals("crud-flow", loaded.getName());

        // update
        loaded.setDisplayName("CRUD 流程 v2");
        loaded.setContent("{\"v\":2}".getBytes(StandardCharsets.UTF_8));
        repo.updateDefine(loaded);
        ProcessInstance.ProcessDefine updated = repo.findDefineById(def.getId());
        assertEquals("CRUD 流程 v2", updated.getDisplayName());

        // state
        repo.updateDefineState(def.getId(), 0);
        assertEquals(0, repo.findDefineById(def.getId()).getState().intValue());

        // remove
        repo.removeDefine(def.getId());
        assertNull(repo.findDefineById(def.getId()));
    }

    // ═══ 测试：updateInstance 级联持久化任务状态（v1.0.1） ═══

    @Test
    public void testUpdateInstanceCascadesTasks() throws Exception {
        ProcessInstance.ProcessDefine def = registerSimpleFlow();
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "user1", FlowData.create());

        // 加载实例（含任务），修改任务状态后 updateInstance
        ProcessInstance reloaded = repo.findInstanceById(inst.getInstanceId());
        assertNotNull(reloaded.getTasks());
        assertTrue(reloaded.getTasks().size() > 0);
        for (ProcessTask task : reloaded.getTasks()) {
            task.withdraw("cascadeOp");
        }
        repo.updateInstance(reloaded);

        // 重新加载验证任务状态已落库
        ProcessInstance after = repo.findInstanceById(inst.getInstanceId());
        for (ProcessTask task : after.getTasks()) {
            assertEquals("撤回后任务状态未级联落库", ProcessTaskStateEnum.WITHDRAW.getCode(), task.getTaskState());
            assertEquals("update_user 须随级联落库（不是只改内存副本）", "cascadeOp", task.getUpdateUser());
        }
    }

    /**
     * issues/113：门面 processInstance/withdraw 走真实派发路径时，任务须落库 30（WITHDRAW）。
     * 上面那条手调聚合命令 + updateInstance，绕过了门面分支——而 go/python/node/rust 四栈恰恰
     * 是在门面 withdraw 分支里写成 99 / 干脆不落库，故门面级 + SQL 回读的断言不能省。
     */
    @Test
    public void testFacadeWithdrawPersistsTaskStateWithdraw() {
        ProcessInstance.ProcessDefine def = registerSimpleFlow();
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "user1", FlowData.create());
        assertFalse("撤回前应有进行中任务", repo.findDoingTasks(inst.getInstanceId(), null).isEmpty());

        JeeflowFacade facade = new JeeflowFacade(engine, repo, null);
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("id", inst.getInstanceId());
        args.put("operator", "user1");
        Map<String, Object> resp = facade.flow("processInstance/withdraw", args);
        assertEquals("门面撤回应成功: " + resp, 0, ((Number) resp.get("code")).intValue());

        List<ProcessTask> tasks = repo.findHistoryTasks(inst.getInstanceId());
        assertFalse("撤回后库里应有任务行", tasks.isEmpty());
        for (ProcessTask task : tasks) {
            assertEquals("撤回后库内任务态应=30(WITHDRAW)，不能是 99(废弃码)",
                    ProcessTaskStateEnum.WITHDRAW.getCode(), task.getTaskState());
        }
        assertEquals("撤回后不应再有进行中任务", 0, repo.findDoingTasks(inst.getInstanceId(), null).size());
        assertEquals("实例态应=30(WITHDRAW)", ProcessInstanceStateEnum.WITHDRAW.getCode(),
                repo.findInstanceById(inst.getInstanceId()).getState());
    }

    /**
     * 合规用例 24（issues/114）在 SQL 仓的落地：撤回 operator 硬必填 + 三条归属判据 +
     * 实例与进行中任务的 {@code update_user} 回写；已完成(20)/已终止(40) 行不得被改写。
     *
     * <p>断言一律走 SQL 回读（H2）：30/99 与"有没有回落 user1"只有查库才看得出来。</p>
     */
    @Test
    public void testFacadeWithdrawAuthzAndAuditOnSqlStore() throws Exception {
        ProcessInstance.ProcessDefine def = registerSimpleFlow();
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "boss", FlowData.create());
        Long instanceId = inst.getInstanceId();
        JeeflowFacade facade = new JeeflowFacade(engine, repo, null);
        Long doingTaskId = repo.findDoingTasks(instanceId, new String[]{}).get(0).getTaskId();
        assertTrue("进行中任务的参与者应能从 wf_process_task_actor 读到",
                repo.findTaskActors(doingTaskId).contains("leader"));
        // 造两条历史行：已完成(20) + 已终止(40)，撤回不得改写
        insertTaskRow(instanceId, "doneRow", ProcessTaskStateEnum.FINISHED.getCode(), "prevDoneUser");
        insertTaskRow(instanceId, "interruptRow", ProcessTaskStateEnum.INTERRUPT.getCode(), "prevInterruptUser");

        // 负向①：缺 operator → 99999999 + msg，库里一行未动（update_user 绝不能被写成 user1）
        Map<String, Object> resp = facade.flow("processInstance/withdraw", args("id", instanceId));
        assertEquals("缺 operator 撤回必须报错: " + resp, 99999999, ((Number) resp.get("code")).intValue());
        assertTrue("msg 应含「operator 必填」: " + resp, String.valueOf(resp.get("msg")).contains("operator 必填"));
        ProcessInstance same = repo.findInstanceById(instanceId);
        assertEquals("缺 operator 不得改实例状态", ProcessInstanceStateEnum.DOING.getCode(), same.getState());
        assertNotEquals("严禁缺省回落固定账号 user1", "user1", same.getUpdateUser());
        assertEquals(ProcessTaskStateEnum.DOING.getCode(), repo.findTaskById(doingTaskId).getTaskState());

        // 负向②：无关第三人（非发起人、非任何进行中任务参与者）→ 拒绝且不改状态
        Map<String, Object> denied = facade.flow("processInstance/withdraw",
                args("id", instanceId, "operator", "nobody"));
        assertEquals("越权撤回必须报错: " + denied, 99999999, ((Number) denied.get("code")).intValue());
        assertTrue("msg 应含「无权限撤回该流程实例」: " + denied,
                String.valueOf(denied.get("msg")).contains("无权限撤回该流程实例"));
        assertEquals(ProcessInstanceStateEnum.DOING.getCode(), repo.findInstanceById(instanceId).getState());
        assertEquals(ProcessTaskStateEnum.DOING.getCode(), repo.findTaskById(doingTaskId).getTaskState());

        // 正向（判据②）：进行中任务的参与者撤回整单 → 实例/任务 30 + update_user 回写真实撤回人
        Map<String, Object> granted = facade.flow("processInstance/withdraw",
                args("id", instanceId, "operator", "leader"));
        assertEquals("参与者应可撤回整单: " + granted, 0, ((Number) granted.get("code")).intValue());
        ProcessInstance after = repo.findInstanceById(instanceId);
        assertEquals(ProcessInstanceStateEnum.WITHDRAW.getCode(), after.getState());
        assertEquals("实例 update_user 回写撤回人", "leader", after.getUpdateUser());
        Map<String, ProcessTask> byName = new HashMap<String, ProcessTask>();
        for (ProcessTask t : repo.findHistoryTasks(instanceId)) byName.put(t.getTaskName(), t);
        ProcessTask withdrawn = byName.get("task1");
        assertEquals("进行中任务须落 30(WITHDRAW)，不能是 99(废弃码)",
                ProcessTaskStateEnum.WITHDRAW.getCode(), withdrawn.getTaskState());
        assertEquals("被撤任务 update_user 回写撤回人", "leader", withdrawn.getUpdateUser());
        assertEquals("已完成(20) 行不得被撤回改写", ProcessTaskStateEnum.FINISHED.getCode(),
                byName.get("doneRow").getTaskState());
        assertEquals("prevDoneUser", byName.get("doneRow").getUpdateUser());
        assertEquals("已终止(40) 行不得被撤回改写", ProcessTaskStateEnum.INTERRUPT.getCode(),
                byName.get("interruptRow").getTaskState());
        assertEquals("prevInterruptUser", byName.get("interruptRow").getUpdateUser());

        // 正向（判据③）：flow.admin 放行沿用既有约定，update_user 记真实操作人
        ProcessInstance inst2 = engine.startProcessInstanceById(def.getId(), "boss", FlowData.create());
        Map<String, Object> byAdmin = facade.flow("processInstance/withdraw",
                args("id", inst2.getInstanceId(), "operator", FlowConst.ADMIN_ID));
        assertEquals("flow.admin 应放行: " + byAdmin, 0, ((Number) byAdmin.get("code")).intValue());
        assertEquals(FlowConst.ADMIN_ID, repo.findInstanceById(inst2.getInstanceId()).getUpdateUser());
    }

    /**
     * 合规用例 25（issues/115）在 SQL 仓的落地：转办摘/加 {@code wf_process_task_actor} 行 +
     * {@code submitType=7} 留痕落任务变量，全部 SQL 回读；四类明确报错。
     */
    @Test
    public void testFacadeTransferOnSqlStore() throws Exception {
        ProcessInstance.ProcessDefine def = registerSimpleFlow();
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "boss", FlowData.create());
        Long instanceId = inst.getInstanceId();
        JeeflowFacade facade = new JeeflowFacade(engine, repo, null);
        Long taskId = repo.findDoingTasks(instanceId, new String[]{}).get(0).getTaskId();

        // 负向：第三人转别人的待办 → 拒绝，且 actor 行原样（不静默成功）
        Map<String, Object> denied = facade.flow("processTask/transfer",
                args("processTaskId", taskId, "operator", "nobody", "fromActor", "leader", "toActor", "lisi"));
        assertEquals("越权转办必须报错: " + denied, 99999999, ((Number) denied.get("code")).intValue());
        assertTrue("msg 应含「无权限转办该任务」: " + denied,
                String.valueOf(denied.get("msg")).contains("无权限转办该任务"));
        assertEquals(Arrays.asList("leader"), repo.findTaskActors(taskId));
        // 负向：缺 operator
        Map<String, Object> noOp = facade.flow("processTask/transfer",
                args("processTaskId", taskId, "fromActor", "leader", "toActor", "lisi"));
        assertEquals(99999999, ((Number) noOp.get("code")).intValue());
        assertTrue("msg 应含「operator 必填」: " + noOp, String.valueOf(noOp.get("msg")).contains("operator 必填"));

        // 正向：leader 把自己那条转给 lisi（带原因）
        Map<String, Object> ok = facade.flow("processTask/transfer",
                args("processTaskId", taskId, "operator", "leader", "fromActor", "leader",
                        "toActor", "lisi", "reason", "临时出差"));
        assertEquals("转办应成功: " + ok, 0, ((Number) ok.get("code")).intValue());
        assertEquals("原办理人那一行 actor 必须真被删掉", Arrays.asList("lisi"), repo.findTaskActors(taskId));
        ProcessTask after = repo.findTaskById(taskId);
        assertEquals("任务不新建：同一条任务仍进行中(10)",
                ProcessTaskStateEnum.DOING.getCode(), after.getTaskState());
        assertNull("转办严禁覆写 operator 列（契约条款 4 的 ⚠️，SQL 回读）：进行中任务该列恒无值是家族"
                + "不变量，写入被摘走的人会在撤回/终止后凭空出现在其「我已办」（state<>10 AND operator=?）",
                after.getActorId());
        assertEquals("任务 update_user 回写转办操作人（办理人由它 + tf_transferHistory[].operator 承载）",
                "leader", after.getUpdateUser());
        assertEquals("SQL 回读变量 submitType 应为 7(TRANSFER)", "7",
                String.valueOf(after.getVariables().get(FlowConst.SUBMIT_TYPE)));
        assertEquals("SQL 回读 tf_transferTo", "lisi", after.getVariables().get(FlowConst.TRANSFER_TO));
        assertEquals("SQL 回读 tf_transferReason", "临时出差", after.getVariables().get(FlowConst.TRANSFER_REASON));
        assertEquals("SQL 回读审批文案", "leader 转办给 lisi（临时出差）",
                after.getVariables().get(FlowConst.APPROVAL_COMMENT));
        // 待办挪位（走门面真实查询路径）
        assertEquals(0, countTodoOf(facade, "leader", taskId));
        assertEquals(1, countTodoOf(facade, "lisi", taskId));
        // approvalRecord 出口读得到留痕
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records =
                (List<Map<String, Object>>) facade.flow("processInstance/approvalRecord",
                        args("id", instanceId)).get("data");
        Map<String, Object> row = null;
        for (Map<String, Object> rec : records) if ("task1".equals(rec.get("taskName"))) row = rec;
        assertNotNull(row);
        @SuppressWarnings("unchecked")
        Map<String, Object> vars = (Map<String, Object>) row.get("variable");
        assertEquals("7", String.valueOf(vars.get(FlowConst.SUBMIT_TYPE)));
        assertEquals("lisi", vars.get(FlowConst.TRANSFER_TO));
        assertTrue("审批记录文案要读得出「A 转办给 B（原因）」: " + vars,
                String.valueOf(vars.get(FlowConst.APPROVAL_COMMENT)).contains("leader 转办给 lisi（临时出差）"));

        // 负向：原办理人已不在参与者里（刚被摘走）→ 明确报错，不静默成功
        Map<String, Object> notActor = facade.flow("processTask/transfer",
                args("processTaskId", taskId, "operator", "leader", "fromActor", "leader", "toActor", "wangwu"));
        assertEquals("原办理人非参与者应报错: " + notActor, 99999999, ((Number) notActor.get("code")).intValue());
        assertTrue("msg 应含「原办理人不是该任务参与人」: " + notActor,
                String.valueOf(notActor.get("msg")).contains("原办理人不是该任务参与人"));

        // 回归：加签（surrogate）仍是"只追加"，原人保留可办——它不是转办
        assertEquals(0, ((Number) facade.flow("processTask/surrogate",
                args("processTaskId", taskId, "actorIds", Arrays.asList("carol"))).get("code")).intValue());
        assertEquals("加签后原人保留、新人追加", Arrays.asList("lisi", "carol"), repo.findTaskActors(taskId));
        // 负向：目标人已是该任务参与者 → 明确报错，且 actor 行原样
        Map<String, Object> dupTo = facade.flow("processTask/transfer",
                args("processTaskId", taskId, "operator", "lisi", "fromActor", "lisi", "toActor", "carol"));
        assertEquals("目标人已是参与者应报错: " + dupTo, 99999999, ((Number) dupTo.get("code")).intValue());
        assertTrue("msg 应含「目标人已是该任务参与人」: " + dupTo,
                String.valueOf(dupTo.get("msg")).contains("目标人已是该任务参与人"));
        assertEquals("报错不得改动 actor 行", Arrays.asList("lisi", "carol"), repo.findTaskActors(taskId));

        // 负向：任务非进行中（lisi 办完后再转）
        assertEquals(0, ((Number) facade.flow("processTask/execute",
                args("processTaskId", taskId, "operator", "lisi", "submitType", 1)).get("code")).intValue());
        assertEquals("回归前提：任务已置 20", ProcessTaskStateEnum.FINISHED.getCode(),
                repo.findTaskById(taskId).getTaskState());
        Map<String, Object> notDoing = facade.flow("processTask/transfer",
                args("processTaskId", taskId, "operator", "lisi", "fromActor", "lisi", "toActor", "leader"));
        assertEquals("非进行中任务不可转办: " + notDoing, 99999999, ((Number) notDoing.get("code")).intValue());
        assertTrue("msg 应含「任务非进行中，不可转办」: " + notDoing,
                String.valueOf(notDoing.get("msg")).contains("任务非进行中，不可转办"));
    }

    /**
     * 合规用例 25 · 追加式账本 {@code tf_transferHistory} 在 SQL 仓的落地（契约 fc0883a 留痕三件之第三件）：
     * A→B、B→C 两跳后 C 办结，账本两条须跨过 JSON 列往返与"办结覆盖当前槽位"仍然存活。
     *
     * <p>与内存仓同一把尺子：断条数、append 顺序、逐字段值、键名键序，而不是只断"读得到"。</p>
     */
    @Test
    public void testFacadeTransferHistoryLedgerOnSqlStore() throws Exception {
        ProcessInstance.ProcessDefine def = registerSimpleFlow();
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "boss", FlowData.create());
        Long instanceId = inst.getInstanceId();
        JeeflowFacade facade = new JeeflowFacade(engine, repo, null);
        Long taskId = repo.findDoingTasks(instanceId, new String[]{}).get(0).getTaskId();

        // 跳 1：A(leader) → B(lisi)，跳 2：B(lisi) → C(carol)
        assertEquals(0, ((Number) facade.flow("processTask/transfer",
                args("processTaskId", taskId, "operator", "leader", "fromActor", "leader",
                        "toActor", "lisi", "reason", "临时出差")).get("code")).intValue());
        assertEquals(0, ((Number) facade.flow("processTask/transfer",
                args("processTaskId", taskId, "operator", "lisi", "fromActor", "lisi",
                        "toActor", "carol", "reason", "由其代批")).get("code")).intValue());

        // 两跳后（SQL 回读 JSON 列）：账本两条 + 逐字段 + 当前槽位仍为 7
        FlowData afterTwoHops = repo.findTaskById(taskId).getVariables();
        assertEquals("C 办结前当前槽位 submitType 读作转办", "7",
                String.valueOf(afterTwoHops.get(FlowConst.SUBMIT_TYPE)));
        List<Map<String, Object>> hops = readLedger(afterTwoHops, "两跳后 SQL 回读");
        assertHop(hops.get(0), 1, "leader", "lisi", "临时出差", "leader");
        assertHop(hops.get(1), 2, "lisi", "carol", "由其代批", "lisi");
        assertEquals("单跳便捷键只留末跳", "carol", afterTwoHops.get(FlowConst.TRANSFER_TO));
        assertEquals("末跳文案只留末跳", "lisi 转办给 carol（由其代批）",
                afterTwoHops.get(FlowConst.APPROVAL_COMMENT));

        // C 办结（execute submitType=1）：合并语义下槽位被覆盖，账本两条必须仍在
        assertEquals(0, ((Number) facade.flow("processTask/execute",
                args("processTaskId", taskId, "operator", "carol", "submitType", 1)).get("code")).intValue());
        FlowData afterFinish = repo.findTaskById(taskId).getVariables();
        assertEquals("办结后当前槽位由办理参数覆盖为 1（契约明确的预期行为）", "1",
                String.valueOf(afterFinish.get(FlowConst.SUBMIT_TYPE)));
        List<Map<String, Object>> kept = readLedger(afterFinish, "C 办结后 SQL 回读");
        assertHop(kept.get(0), 1, "leader", "lisi", "临时出差", "leader");
        assertHop(kept.get(1), 2, "lisi", "carol", "由其代批", "lisi");

        // approvalRecord 出口（前端读取位）同样读得出全量账本
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records =
                (List<Map<String, Object>>) facade.flow("processInstance/approvalRecord",
                        args("id", instanceId)).get("data");
        Map<String, Object> row = null;
        for (Map<String, Object> rec : records) if ("task1".equals(rec.get("taskName"))) row = rec;
        assertNotNull(row);
        @SuppressWarnings("unchecked")
        Map<String, Object> vars = (Map<String, Object>) row.get("variable");
        readLedger(vars, "approvalRecord.variable 出口");
    }

    /**
     * 契约条款 4 的 ⚠️（jeeflow-doc 778340a，Node 实测纠偏）SQL 仓路径：转办严禁覆写
     * {@code wf_process_task.operator}（actor_id）列，转办→撤回后被摘走的人「我已办」不得凭空多出这单。
     *
     * <p>机理：{@code pageDoneTasks} 按 {@code state <> 10 AND operator = ?} 过滤——转办时把被摘走的
     * 人写进该列，单被撤回（任务落 30，离开 DOING 但列值留着）即命中过滤（Node 实测：A 的 doneList
     * 冒出 task#30，未转办对照为空）。办理人由 update_user + tf_transferHistory[].operator 承载。
     * 污染只落在库里的列上，必须走门面 transfer→withdraw 再 SQL 查 doneList 才是铁证。</p>
     */
    @Test
    public void testFacadeTransferThenWithdrawKeepsFromActorDoneListCleanOnSqlStore() throws Exception {
        ProcessInstance.ProcessDefine def = registerSimpleFlow();
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "boss", FlowData.create());
        Long instanceId = inst.getInstanceId();
        JeeflowFacade facade = new JeeflowFacade(engine, repo, null);
        Long taskId = repo.findDoingTasks(instanceId, new String[]{}).get(0).getTaskId();

        assertEquals(0, ((Number) facade.flow("processTask/transfer",
                args("processTaskId", taskId, "operator", "leader", "fromActor", "leader",
                        "toActor", "lisi", "reason", "临时出差")).get("code")).intValue());

        // SQL 回读：operator 列（actor_id）仍无值——进行中任务该列恒无值是家族不变量
        assertNull("转办后任务 operator 列应仍无值", repo.findTaskById(taskId).getActorId());

        // 发起人撤回整单：任务落 30(WITHDRAW) 离开 DOING——若转办曾污染 operator 列，污染窗口就此打开
        Map<String, Object> wd = facade.flow("processInstance/withdraw",
                args("id", instanceId, "operator", "boss"));
        assertEquals("撤回应成功: " + wd, 0, ((Number) wd.get("code")).intValue());
        assertEquals(ProcessTaskStateEnum.WITHDRAW.getCode(), repo.findTaskById(taskId).getTaskState());

        // 被摘走的 fromActor 从没办过这单，doneList 不得含它；接手人未办结，同样不得含
        assertEquals("转办→撤回后 fromActor 的已办列表不得含该单", 0, countDoneOf(facade, "leader", taskId));
        assertEquals("接手人（未办结）的已办列表同样不得含该单", 0, countDoneOf(facade, "lisi", taskId));
    }

    /** 读回并校验 {@code tf_transferHistory} 的形状/条数（SQL 侧 JSON 往返后仍是 list of map）。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readLedger(Map<String, Object> vars, String when) {
        Object raw = vars.get(FlowConst.TRANSFER_HISTORY);
        assertTrue(when + "：tf_transferHistory 应为列表，实际: " + raw, raw instanceof List);
        List<Object> list = (List<Object>) raw;
        assertEquals(when + "：账本应为两条（每跳 append，不覆盖不裁剪）", 2, list.size());
        List<Map<String, Object>> hops = new java.util.ArrayList<Map<String, Object>>();
        for (Object o : list) {
            assertTrue(when + "：账本每条应为 map，实际: " + o, o instanceof Map);
            Map<String, Object> hop = (Map<String, Object>) o;
            assertEquals(when + "：账本键序须与契约一致（跨栈对齐形态）: " + hop,
                    Arrays.asList("submitType", "fromActor", "toActor", "reason", "time", "operator"),
                    new java.util.ArrayList<String>(hop.keySet()));
            hops.add(hop);
        }
        return hops;
    }

    /** 逐字段断一条账本记录（含 append 位置）。 */
    private void assertHop(Map<String, Object> hop, int position,
                           String from, String to, String reason, String operator) {
        String at = "第 " + position + " 跳 " + from + "→" + to + ": " + hop;
        assertEquals(at + " 的 submitType", 7, ((Number) hop.get("submitType")).intValue());
        assertEquals(at + " 的 fromActor", from, hop.get("fromActor"));
        assertEquals(at + " 的 toActor", to, hop.get("toActor"));
        assertEquals(at + " 的 reason", reason, hop.get("reason"));
        assertEquals(at + " 的 operator", operator, hop.get("operator"));
        Object time = hop.get("time");
        // 跨栈同形锁：账本 time 一律 yyyy-MM-dd HH:mm:ss（spec §2.4，Go/Python/Node/PHP/Rust/C# 同款），
        // JSON 往返后仍须匹配该形状，不得是 ISO 方言（带 T/小数秒）
        assertTrue(at + " 的 time 应为 yyyy-MM-dd HH:mm:ss 串（跨栈同形），实际: " + time,
                time instanceof String && ((String) time).matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$"));
        assertNotNull(at + " 的 time 应按该格式可解析", java.time.LocalDateTime.parse((String) time,
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }

    // ═══ 辅助方法 ═══

    /** 门面入参 map（与 core 门面测试同款写法，值为 null 时不塞键） */
    private Map<String, Object> args(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) {
            if (kv[i + 1] != null) m.put(kv[i].toString(), kv[i + 1]);
        }
        return m;
    }

    /** 某人待办里指定任务的数量（走门面 todoList，断"待办挪位"用） */
    private int countTodoOf(JeeflowFacade facade, String operator, Long taskId) {
        Map<String, Object> resp = facade.flow("processTask/todoList", args("operator", operator));
        assertEquals(0, ((Number) resp.get("code")).intValue());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows =
                (List<Map<String, Object>>) ((Map<String, Object>) resp.get("data")).get("rows");
        int n = 0;
        for (Map<String, Object> row : rows) if (String.valueOf(taskId).equals(String.valueOf(row.get("id")))) n++;
        return n;
    }

    /** 某人已办里指定任务的数量（走门面 doneList，断"已办列表不被转办污染"用） */
    private int countDoneOf(JeeflowFacade facade, String operator, Long taskId) {
        Map<String, Object> resp = facade.flow("processTask/doneList", args("operator", operator));
        assertEquals(0, ((Number) resp.get("code")).intValue());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows =
                (List<Map<String, Object>>) ((Map<String, Object>) resp.get("data")).get("rows");
        int n = 0;
        for (Map<String, Object> row : rows) if (String.valueOf(taskId).equals(String.valueOf(row.get("id")))) n++;
        return n;
    }

    /** 直接插一行任务（造已完成/已终止历史行用，绕开门面以便精确控制落库值） */
    private void insertTaskRow(Long instanceId, String taskName, int taskState, String updateUser) {
        String sql = "INSERT INTO wf_process_task (id, process_instance_id, task_name, display_name, " +
                "task_type, perform_type, task_state, operator, finish_time, expire_time, form_key, " +
                "task_parent_id, variable, create_time, create_user, update_time, update_user) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = ds.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            long id = System.currentTimeMillis() + (long) (Math.random() * 10000);
            ps.setLong(1, id);
            ps.setLong(2, instanceId);
            ps.setString(3, taskName);
            ps.setString(4, taskName);
            ps.setInt(5, 0);
            ps.setInt(6, 0);
            ps.setInt(7, taskState);
            ps.setString(8, "prevActor");
            ps.setTimestamp(9, new java.sql.Timestamp(System.currentTimeMillis()));
            ps.setTimestamp(10, null);
            ps.setString(11, "f1");
            ps.setLong(12, 0L);
            ps.setString(13, null);
            ps.setTimestamp(14, new java.sql.Timestamp(System.currentTimeMillis()));
            ps.setString(15, updateUser);
            ps.setTimestamp(16, new java.sql.Timestamp(System.currentTimeMillis()));
            ps.setString(17, updateUser);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ProcessInstance.ProcessDefine registerSimpleFlow() {
        String json = ("{'name':'simple','displayName':'简单流程','type':'test','nodes':[" +
                "{'id':'start','type':'snaker:start','x':100,'y':200,'properties':{},'text':{'value':'开始'}}," +
                "{'id':'task1','type':'snaker:task','x':300,'y':200,'properties':{'form':'f1','assignee':'leader','taskType':0,'performType':0},'text':{'value':'审批'}}," +
                "{'id':'end','type':'snaker:end','x':500,'y':200,'properties':{},'text':{'value':'结束'}}]," +
                "'edges':[" +
                "{'id':'e1','sourceNodeId':'start','targetNodeId':'task1','properties':{}}," +
                "{'id':'e2','sourceNodeId':'task1','targetNodeId':'end','properties':{}}]}").replace('\'', '"');
        return saveDefine(json, "simple", "简单流程");
    }

    private ProcessInstance.ProcessDefine registerMultiTaskFlow() {
        String json = ("{'name':'multi','displayName':'多级审批','type':'test','nodes':[" +
                "{'id':'start','type':'snaker:start','x':100,'y':200,'properties':{},'text':{'value':'开始'}}," +
                "{'id':'t1','type':'snaker:task','x':250,'y':200,'properties':{'form':'f1','assignee':'leader','taskType':0,'performType':0},'text':{'value':'上级'}}," +
                "{'id':'t2','type':'snaker:task','x':400,'y':200,'properties':{'form':'f1','assignee':'manager','taskType':0,'performType':0},'text':{'value':'经理'}}," +
                "{'id':'t3','type':'snaker:task','x':550,'y':200,'properties':{'form':'f1','assignee':'boss','taskType':0,'performType':0},'text':{'value':'总监'}}," +
                "{'id':'end','type':'snaker:end','x':700,'y':200,'properties':{},'text':{'value':'结束'}}]," +
                "'edges':[" +
                "{'id':'e1','sourceNodeId':'start','targetNodeId':'t1','properties':{}}," +
                "{'id':'e2','sourceNodeId':'t1','targetNodeId':'t2','properties':{}}," +
                "{'id':'e3','sourceNodeId':'t2','targetNodeId':'t3','properties':{}}," +
                "{'id':'e4','sourceNodeId':'t3','targetNodeId':'end','properties':{}}]}").replace('\'', '"');
        return saveDefine(json, "multi", "多级审批");
    }

    private ProcessInstance.ProcessDefine registerDecisionFlow() {
        String json = ("{'name':'decision','displayName':'决策流程','type':'test','nodes':[" +
                "{'id':'start','type':'snaker:start','x':100,'y':200,'properties':{},'text':{'value':'开始'}}," +
                "{'id':'apply','type':'snaker:task','x':300,'y':200,'properties':{'form':'f1','assignee':'leader','taskType':0,'performType':0},'text':{'value':'申请'}}," +
                "{'id':'d1','type':'snaker:decision','x':500,'y':200,'properties':{'expr':'amount > 1000'},'text':{'value':'>1000?'}}," +
                "{'id':'manager','type':'snaker:task','x':650,'y':100,'properties':{'form':'f1','assignee':'manager','taskType':0,'performType':0},'text':{'value':'经理审批'}}," +
                "{'id':'end','type':'snaker:end','x':850,'y':100,'properties':{},'text':{'value':'结束'}}]," +
                "'edges':[" +
                "{'id':'e1','sourceNodeId':'start','targetNodeId':'apply','properties':{}}," +
                "{'id':'e2','sourceNodeId':'apply','targetNodeId':'d1','properties':{}}," +
                "{'id':'e3','sourceNodeId':'d1','targetNodeId':'manager','properties':{'expr':'amount > 1000'}}," +
                "{'id':'e4','sourceNodeId':'d1','targetNodeId':'end','properties':{'expr':'amount <= 1000'}}," +
                "{'id':'e5','sourceNodeId':'manager','targetNodeId':'end','properties':{}}]}").replace('\'', '"');
        return saveDefine(json, "decision", "决策流程");
    }

    private ProcessInstance.ProcessDefine saveDefine(String json, String name, String displayName) {
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName(name);
        def.setDisplayName(displayName);
        def.setType("test");
        def.setState(1);
        def.setVersion(1);
        def.setContent(json.getBytes(StandardCharsets.UTF_8));

        String sql = "INSERT INTO wf_process_define (id, name, display_name, type, state, content, version) VALUES (?,?,?,?,?,?,?)";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            long id = System.currentTimeMillis();
            def.setId(id);
            ps.setLong(1, id);
            ps.setString(2, name);
            ps.setString(3, displayName);
            ps.setString(4, "test");
            ps.setInt(5, 1);
            ps.setBytes(6, json.getBytes(StandardCharsets.UTF_8));
            ps.setInt(7, 1);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return def;
    }

    // 测试用表达式求值器
    public static class TestExprEvaluator implements IExpressionEvaluator {
        @Override
        public Object eval(String expression, Map<String, Object> context) {
            if ("amount > 1000".equals(expression)) {
                Object val = context.get("amount");
                if (val != null) return Double.parseDouble(val.toString()) > 1000;
            }
            if ("amount <= 1000".equals(expression)) {
                Object val = context.get("amount");
                if (val != null) return Double.parseDouble(val.toString()) <= 1000;
            }
            return false;
        }
    }
}
