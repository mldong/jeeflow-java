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
            task.withdraw();
        }
        repo.updateInstance(reloaded);

        // 重新加载验证任务状态已落库
        ProcessInstance after = repo.findInstanceById(inst.getInstanceId());
        for (ProcessTask task : after.getTasks()) {
            assertEquals("撤回后任务状态未级联落库", ProcessTaskStateEnum.WITHDRAW.getCode(), task.getTaskState());
        }
    }

    // ═══ 辅助方法 ═══

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
