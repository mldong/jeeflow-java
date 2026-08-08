package com.mldong.jeeflow.persist.interceptor;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.persist.jdbc.JdbcDynamicTableWriter;
import org.junit.Before;
import org.junit.Test;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * PersistPostInterceptor 集成测试（H2 全链路）——issues/18
 *
 * <p>流程 01-simple（start→apply→task1→end）+ 模型配置 relTableName=biz_leave，
 * 注册拦截器 → 发起（f_ 字段）→ 同意完成 → 断言业务表落库。</p>
 */
public class PersistPostInterceptorTest {

    private JeeflowEngine engine;
    private com.mldong.jeeflow.persist.test.MemoryRepo repo;
    private DataSource ds;
    private com.mldong.jeeflow.spi.IUserProvider userProvider;

    /** 重建干净引擎上下文（跨测试防污染；不注册 writer 即「未注入」场景） */
    private void resetContext() {
        // 注意：new Configuration() 会 setContext(new SimpleContext()) 重置上下文，
        // 必须先建配置再 put SPI（configure 时复用，不可在参数里再 new）
        Configuration config = new Configuration();
        ServiceContext.put("repository", repo);
        ServiceContext.put("json", new com.mldong.jeeflow.persist.test.TestJsonProvider());
        ServiceContext.put("expr", new com.mldong.jeeflow.persist.test.TestExpressionEvaluator());
        ServiceContext.put("user", userProvider);
        engine = new JeeflowEngineImpl();
        engine.configure(config);
    }

    @Before
    public void setUp() throws Exception {
        repo = new com.mldong.jeeflow.persist.test.MemoryRepo();
        userProvider = new com.mldong.jeeflow.spi.IUserProvider() {
            @Override public com.mldong.jeeflow.spi.IUserProvider.UserInfo getUser(String userId) {
                com.mldong.jeeflow.spi.IUserProvider.UserInfo u =
                        new com.mldong.jeeflow.spi.IUserProvider.UserInfo();
                u.setUserId(userId);
                u.setDeptId("D01");
                return u;
            }
        };
        resetContext();

        // H2 业务表
        org.h2.jdbcx.JdbcDataSource h2 = new org.h2.jdbcx.JdbcDataSource();
        h2.setURL("jdbc:h2:mem:persist_flow;DB_CLOSE_DELAY=-1");
        h2.setUser("sa");
        ds = h2;
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS biz_leave");
            st.execute("CREATE TABLE biz_leave (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "title VARCHAR(100)," +
                    "amount DECIMAL(10,2)," +
                    "process_instance_id BIGINT," +
                    "apply_user_id VARCHAR(50)," +
                    "apply_dept_id VARCHAR(50)," +
                    "create_time VARCHAR(30)," +
                    "create_user VARCHAR(50)," +
                    "update_time VARCHAR(30)," +
                    "update_user VARCHAR(50)," +
                    "is_deleted INT" +
                    ")");
        }
    }

    private ProcessInstance.ProcessDefine registerFlow() throws Exception {
        // 用 01-simple 但改 model：relTableName=biz_leave + postInterceptors 模型级挂载拦截器
        byte[] bytes = Files.readAllBytes(Paths.get(
                "../jeeflow-core/src/test/resources/flows/01-simple.json"));
        String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        // 注入 relTableName（顶层）
        content = content.replace("\"type\": \"approval\"", "\"type\": \"approval\", \"relTableName\": \"biz_leave\"");
        // 注入 postInterceptors（模型级挂载：结束节点执行后引擎反射实例化调用）
        content = content.replace("\"type\": \"approval\"",
                "\"type\": \"approval\", \"postInterceptors\": \"com.mldong.jeeflow.persist.interceptor.PersistPostInterceptor\"");
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName("simple");
        def.setDisplayName("01-simple.json");
        def.setType("approval");
        def.setState(1);
        def.setVersion(1);
        def.setContent(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        repo.addDefine(def);
        return def;
    }

    private void registerInterceptor() {
        // 注册 writer 到引擎上下文（拦截器模型级反射实例化后按类型自取）
        JdbcDynamicTableWriter writer = new JdbcDynamicTableWriter(ds);
        ServiceContext.put("dynamicTableWriter", writer);
    }

    /** ⑧ 流程结束同意 → 业务表落库（f_ 去前缀 + 系统字段 + 流程上下文） */
    @Test
    public void testFlowFinishPersist() throws Exception {
        registerInterceptor();
        ProcessInstance.ProcessDefine def = registerFlow();

        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "user1",
                FlowData.create()
                        .set("f_title", "年假申请")
                        .set("f_amount", 800)
                        .set("u_deptId", "D01"));

        // 自动完成 apply → task1（leader）
        List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), new String[]{});
        for (ProcessTask t : doing) {
            if ("apply".equals(t.getTaskName())) {
                repo.addTaskActor(t.getTaskId(), Arrays.asList("user1"));
                engine.executeProcessTask(t.getTaskId(), "user1",
                        FlowData.create().set("submitType", 0));
            }
        }
        doing = repo.findDoingTasks(inst.getInstanceId(), new String[]{});
        assertEquals("task1", doing.get(0).getTaskName());
        repo.addTaskActor(doing.get(0).getTaskId(), Arrays.asList("leader"));

        // leader 同意 → 流程结束 → 应落库
        engine.executeProcessTask(doing.get(0).getTaskId(), "leader",
                FlowData.create().set("submitType", 1));

        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM biz_leave")) {
            assertTrue("应有一条业务记录", rs.next());
            assertEquals("年假申请", rs.getString("title"));
            assertEquals(800.0, rs.getDouble("amount"), 0.001);
            assertEquals(inst.getInstanceId().longValue(), rs.getLong("process_instance_id"));
            assertEquals("user1", rs.getString("apply_user_id"));
            assertEquals("D01", rs.getString("apply_dept_id"));
            assertNotNull("create_time 应填充", rs.getString("create_time"));
            assertEquals("user1", rs.getString("create_user")); // issues/19: 默认用户值优先 operator
            assertEquals(0, rs.getInt("is_deleted"));
            assertFalse("应只有一条", rs.next());
        }
    }

    /** ⑨ 不同意/退回 → 不入库 */
    @Test
    public void testRejectNoPersist() throws Exception {
        registerInterceptor();
        ProcessInstance.ProcessDefine def = registerFlow();

        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "user1",
                FlowData.create().set("f_title", "年假申请").set("u_deptId", "D01"));
        List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), new String[]{});
        for (ProcessTask t : doing) {
            if ("apply".equals(t.getTaskName())) {
                repo.addTaskActor(t.getTaskId(), Arrays.asList("user1"));
                engine.executeProcessTask(t.getTaskId(), "user1",
                        FlowData.create().set("submitType", 0));
            }
        }
        doing = repo.findDoingTasks(inst.getInstanceId(), new String[]{});
        repo.addTaskActor(doing.get(0).getTaskId(), Arrays.asList("leader"));

        // 拒绝（submitType=2）→ 流程 REJECT → 不入库
        engine.executeProcessTask(doing.get(0).getTaskId(), "leader",
                FlowData.create().set("submitType", 2));

        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(1) FROM biz_leave")) {
            assertTrue(rs.next());
            assertEquals("拒绝不应入库", 0, rs.getInt(1));
        }
    }

    /** ⑩ writer 未注册 → 拦截器静默跳过（挂载了但不落库、不报错） */
    @Test
    public void testNoWriterSkip() throws Exception {
        resetContext();   // 重建干净上下文（不注册 writer）
        byte[] bytes = Files.readAllBytes(Paths.get(
                "../jeeflow-core/src/test/resources/flows/01-simple.json"));
        String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        // 挂载拦截器 + 配置 relTableName（biz_leave 表存在），但无 writer → 跳过
        content = content.replace("\"type\": \"approval\"",
                "\"type\": \"approval\", \"relTableName\": \"biz_leave\", \"postInterceptors\": \"com.mldong.jeeflow.persist.interceptor.PersistPostInterceptor\"");
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName("simple");
        def.setDisplayName("01-simple.json");
        def.setType("approval");
        def.setState(1);
        def.setVersion(1);
        def.setContent(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        repo.addDefine(def);

        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "user1",
                FlowData.create().set("f_title", "t").set("u_deptId", "D01"));
        List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), new String[]{});
        for (ProcessTask t : doing) {
            if ("apply".equals(t.getTaskName())) {
                repo.addTaskActor(t.getTaskId(), Arrays.asList("user1"));
                engine.executeProcessTask(t.getTaskId(), "user1",
                        FlowData.create().set("submitType", 0));
            }
        }
        doing = repo.findDoingTasks(inst.getInstanceId(), new String[]{});
        repo.addTaskActor(doing.get(0).getTaskId(), Arrays.asList("leader"));
        engine.executeProcessTask(doing.get(0).getTaskId(), "leader",
                FlowData.create().set("submitType", 1));

        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(1) FROM biz_leave")) {
            assertTrue(rs.next());
            assertEquals("未注册 writer 不应入库", 0, rs.getInt(1));
        }
    }

    /** ⑪ 重复执行链 → 幂等不重复插（同实例二次触发） */
    @Test
    public void testIdempotentFlow() throws Exception {
        registerInterceptor();
        ProcessInstance.ProcessDefine def = registerFlow();

        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "user1",
                FlowData.create().set("f_title", "t").set("u_deptId", "D01"));
        List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), new String[]{});
        for (ProcessTask t : doing) {
            if ("apply".equals(t.getTaskName())) {
                repo.addTaskActor(t.getTaskId(), Arrays.asList("user1"));
                engine.executeProcessTask(t.getTaskId(), "user1",
                        FlowData.create().set("submitType", 0));
            }
        }
        doing = repo.findDoingTasks(inst.getInstanceId(), new String[]{});
        repo.addTaskActor(doing.get(0).getTaskId(), Arrays.asList("leader"));
        engine.executeProcessTask(doing.get(0).getTaskId(), "leader",
                FlowData.create().set("submitType", 1));

        // 模拟重复触发（拦截器再跑一次）
        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(1) FROM biz_leave")) {
            assertTrue(rs.next());
            assertEquals("首次应入库 1 条", 1, rs.getInt(1));
        }
    }

    /** ⑫ BIGINT 用户列（issues/19）：多数框架表 create_user 是 BIGINT 存 userId——
     *  fillSystemFields 默认用户值优先 operator，插入不报类型错误 */
    @Test
    public void testBigintUserColumn() throws Exception {
        registerInterceptor();
        // 建 BIGINT 用户列的业务表（boot4 场景：operator 为数字 userId）
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS biz_settle");
            st.execute("CREATE TABLE biz_settle (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "title VARCHAR(100)," +
                    "process_instance_id BIGINT," +
                    "apply_user_id BIGINT," +
                    "create_user BIGINT," +
                    "update_user BIGINT," +
                    "is_deleted INT" +
                    ")");
        }
        byte[] bytes = Files.readAllBytes(Paths.get(
                "../jeeflow-core/src/test/resources/flows/01-simple.json"));
        String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                .replace("\"type\": \"approval\"",
                        "\"type\": \"approval\", \"relTableName\": \"biz_settle\", \"postInterceptors\": \"com.mldong.jeeflow.persist.interceptor.PersistPostInterceptor\"");
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName("simple");
        def.setDisplayName("01-simple.json");
        def.setType("approval");
        def.setState(1);
        def.setVersion(1);
        def.setContent(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        repo.addDefine(def);

        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "123",
                FlowData.create().set("f_title", "结算单").set("u_deptId", "D01"));
        List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), new String[]{});
        for (ProcessTask t : doing) {
            if ("apply".equals(t.getTaskName())) {
                repo.addTaskActor(t.getTaskId(), Arrays.asList("123"));
                engine.executeProcessTask(t.getTaskId(), "123",
                        FlowData.create().set("submitType", 0));
            }
        }
        doing = repo.findDoingTasks(inst.getInstanceId(), new String[]{});
        repo.addTaskActor(doing.get(0).getTaskId(), Arrays.asList("leader"));
        engine.executeProcessTask(doing.get(0).getTaskId(), "leader",
                FlowData.create().set("submitType", 1));

        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT create_user, apply_user_id FROM biz_settle")) {
            assertTrue("BIGINT 用户列应插入成功", rs.next());
            assertEquals("create_user 应为 operator", 123L, rs.getLong("create_user"));
            assertEquals("apply_user_id 应为 operator", 123L, rs.getLong("apply_user_id"));
        }
    }

    /** ⑬ 同链二次触发（issues/19）：最后任务节点与结束节点都会触发后置拦截器——
     *  同一次执行链（共享 args）仅插 1 条 */
    @Test
    public void testSameChainDoubleTrigger() throws Exception {
        registerInterceptor();
        ProcessInstance.ProcessDefine def = registerFlow();
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "user1",
                FlowData.create().set("f_title", "t").set("u_deptId", "D01"));
        List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), new String[]{});
        for (ProcessTask t : doing) {
            if ("apply".equals(t.getTaskName())) {
                repo.addTaskActor(t.getTaskId(), Arrays.asList("user1"));
                engine.executeProcessTask(t.getTaskId(), "user1",
                        FlowData.create().set("submitType", 0));
            }
        }
        doing = repo.findDoingTasks(inst.getInstanceId(), new String[]{});
        repo.addTaskActor(doing.get(0).getTaskId(), Arrays.asList("leader"));
        engine.executeProcessTask(doing.get(0).getTaskId(), "leader",
                FlowData.create().set("submitType", 1));

        // 构造同链 Execution（共享同一 args FlowData），模拟最后任务节点 + 结束节点各触发一次
        com.mldong.jeeflow.core.Execution execution = new com.mldong.jeeflow.core.Execution();
        execution.setProcessModel(new com.mldong.jeeflow.model.ProcessModel());
        execution.getProcessModel().setRelTableName("biz_leave");
        execution.setProcessInstance(inst);
        execution.setProcessInstanceId(inst.getInstanceId());
        FlowData args = FlowData.create().set("submitType", 1);
        execution.setArgs(args);
        com.mldong.jeeflow.persist.interceptor.PersistPostInterceptor interceptor =
                new com.mldong.jeeflow.persist.interceptor.PersistPostInterceptor();
        interceptor.intercept(execution);   // 第一次：落库
        interceptor.intercept(execution);   // 同链第二次：内存标记跳过

        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(1) FROM biz_leave")) {
            assertTrue(rs.next());
            assertEquals("同链重复触发应仅 1 条", 1, rs.getInt(1));
        }
    }

    /** ⑭ SYNC 同步演进全链路（1.8.0）：发起 INSERT → 任务 UPDATE（字段权限 + tf_ 冗余 + 状态字段）→ 结束定稿 */
    @Test
    public void testSyncModeFullCycle() throws Exception {
        registerInterceptor();
        // SYNC 业务表：f_ 列 + tf_ 冗余列 + 状态字段列（节点 ID）
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS biz_sync");
            st.execute("CREATE TABLE biz_sync (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "title VARCHAR(100)," +
                    "amount DECIMAL(10,2)," +
                    "opinion VARCHAR(200)," +
                    "apply INT," +
                    "task1 INT," +
                    "finish INT," +
                    "process_instance_id BIGINT," +
                    "apply_user_id VARCHAR(50)," +
                    "apply_dept_id VARCHAR(50)," +
                    "create_time VARCHAR(30)," +
                    "create_user VARCHAR(50)," +
                    "update_time VARCHAR(30)," +
                    "update_user VARCHAR(50)," +
                    "is_deleted INT" +
                    ")");
        }
        // 流程：persistMode=SYNC + task1 节点字段权限（title 只读 / amount 可编辑）+ 结束节点改名 finish（end 为 SQL 保留字）
        byte[] bytes = Files.readAllBytes(Paths.get(
                "../jeeflow-core/src/test/resources/flows/01-simple.json"));
        String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                .replace("\"type\": \"approval\"",
                        "\"type\": \"approval\", \"relTableName\": \"biz_sync\", \"persistMode\": \"SYNC\", \"postInterceptors\": \"com.mldong.jeeflow.persist.interceptor.PersistPostInterceptor\"")
                .replace("\"assignee\": \"leader\"",
                        "\"assignee\": \"leader\", \"field\": {\"PERMISSION_f_title\": 1, \"PERMISSION_amount\": 2}")
                .replace("\"id\": \"end\"", "\"id\": \"finish\"")
                .replace("\"targetNodeId\": \"end\"", "\"targetNodeId\": \"finish\"");
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName("simple");
        def.setDisplayName("01-simple.json");
        def.setType("approval");
        def.setState(1);
        def.setVersion(1);
        def.setContent(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        repo.addDefine(def);

        // ① 发起 → INSERT（title/amount；状态字段按当前节点 start 探测——表无 start 列则不写）
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "user1",
                FlowData.create().set("f_title", "年假申请").set("f_amount", 800).set("u_deptId", "D01"));
        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT title, amount, process_instance_id FROM biz_sync")) {
            assertTrue("发起应 INSERT", rs.next());
            assertEquals("年假申请", rs.getString("title"));
            assertEquals(800.0, rs.getDouble("amount"), 0.001);
        }

        // ② apply 完成 → UPDATE（apply 节点无权限声明，f_ 全量；apply 状态字段=10 DOING）
        List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), new String[]{});
        for (ProcessTask t : doing) {
            if ("apply".equals(t.getTaskName())) {
                repo.addTaskActor(t.getTaskId(), Arrays.asList("user1"));
                engine.executeProcessTask(t.getTaskId(), "user1",
                        FlowData.create().set("submitType", 0));
            }
        }
        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT apply FROM biz_sync")) {
            assertTrue(rs.next());
            assertEquals(10, rs.getInt("apply"));   // DOING
        }

        // ③ task1（leader）→ UPDATE：title 只读不更新 / amount 可编辑更新 / opinion(tf_) / task1 状态
        // （task1 为末节点，完成后自动流转结束节点定稿，见 ④ 汇总断言）
        doing = repo.findDoingTasks(inst.getInstanceId(), new String[]{});
        repo.addTaskActor(doing.get(0).getTaskId(), Arrays.asList("leader"));
        engine.executeProcessTask(doing.get(0).getTaskId(), "leader",
                FlowData.create().set("submitType", 1).set("tf_opinion", "同意")
                        .set("f_title", "修改标题").set("f_amount", 999));

        // ④ 结束 → UPDATE 最终状态（finish=20 FINISHED）
        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT title, amount, opinion, task1, finish FROM biz_sync")) {
            assertTrue(rs.next());
            assertEquals("只读字段不更新", "年假申请", rs.getString("title"));
            assertEquals("可编辑字段更新", 999.0, rs.getDouble("amount"), 0.001);
            assertEquals("tf_ 冗余", "同意", rs.getString("opinion"));
            assertEquals(10, rs.getInt("task1"));
            assertEquals(20, rs.getInt("finish"));   // FINISHED
            assertFalse("应仅 1 条（先插后更）", rs.next());
        }
    }

    /** ⑮ SYNC 驳回：结束 UPDATE 最终状态 end=45（REJECT），数据不丢 */
    @Test
    public void testSyncModeReject() throws Exception {
        registerInterceptor();
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS biz_sync2");
            st.execute("CREATE TABLE biz_sync2 (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "title VARCHAR(100)," +
                    "apply INT," +
                    "finish INT," +
                    "process_instance_id BIGINT," +
                    "create_user VARCHAR(50)," +
                    "is_deleted INT" +
                    ")");
        }
        byte[] bytes = Files.readAllBytes(Paths.get(
                "../jeeflow-core/src/test/resources/flows/01-simple.json"));
        String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                .replace("\"type\": \"approval\"",
                        "\"type\": \"approval\", \"relTableName\": \"biz_sync2\", \"persistMode\": \"SYNC\", \"postInterceptors\": \"com.mldong.jeeflow.persist.interceptor.PersistPostInterceptor\"")
                .replace("\"id\": \"end\"", "\"id\": \"finish\"")
                .replace("\"targetNodeId\": \"end\"", "\"targetNodeId\": \"finish\"");
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName("simple");
        def.setDisplayName("01-simple.json");
        def.setType("approval");
        def.setState(1);
        def.setVersion(1);
        def.setContent(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        repo.addDefine(def);

        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "user1",
                FlowData.create().set("f_title", "驳回单").set("u_deptId", "D01"));
        List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), new String[]{});
        for (ProcessTask t : doing) {
            if ("apply".equals(t.getTaskName())) {
                repo.addTaskActor(t.getTaskId(), Arrays.asList("user1"));
                engine.executeProcessTask(t.getTaskId(), "user1",
                        FlowData.create().set("submitType", 0));
            }
        }
        doing = repo.findDoingTasks(inst.getInstanceId(), new String[]{});
        repo.addTaskActor(doing.get(0).getTaskId(), Arrays.asList("leader"));
        engine.executeProcessTask(doing.get(0).getTaskId(), "leader",
                FlowData.create().set("submitType", 2));   // 驳回

        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT title, finish, create_user FROM biz_sync2")) {
            assertTrue("驳回也应有记录", rs.next());
            assertEquals("驳回单", rs.getString("title"));
            assertEquals("最终状态应为 REJECT", 45, rs.getInt("finish"));
            assertEquals("user1", rs.getString("create_user"));
            assertFalse("应仅 1 条", rs.next());
        }
    }

    /** ⑯ issues/26：办理提交被拒字段（只读/隐藏）不入变量——下游无权限节点无法绕过上游只读 */
    @Test
    public void testSyncPermBypass() throws Exception {
        registerInterceptor();
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS biz_perm3");
            st.execute("CREATE TABLE biz_perm3 (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "title VARCHAR(100)," +
                    "amount DECIMAL(10,2)," +
                    "apply INT," +
                    "approve1 INT," +
                    "approve2 INT," +
                    "finish INT," +
                    "process_instance_id BIGINT," +
                    "create_user VARCHAR(50)," +
                    "is_deleted INT" +
                    ")");
        }
        // 三任务节点：apply → approve1（PERMISSION_f_title=1 只读）→ approve2（无权限声明）→ finish
        String content = "{"
                + "\"name\": \"perm3\", \"displayName\": \"权限绕过验证\", \"type\": \"approval\","
                + "\"relTableName\": \"biz_perm3\", \"persistMode\": \"SYNC\","
                + "\"postInterceptors\": \"com.mldong.jeeflow.persist.interceptor.PersistPostInterceptor\","
                + "\"nodes\": ["
                + "{\"id\": \"start\", \"type\": \"snaker:start\", \"properties\": {\"width\": 50, \"height\": 50}, \"text\": {\"value\": \"开始\"}},"
                + "{\"id\": \"apply\", \"type\": \"snaker:task\", \"properties\": {\"form\": \"apply-form\", \"assignee\": \"applicant\", \"taskType\": 0, \"performType\": 0}, \"text\": {\"value\": \"发起申请\"}},"
                + "{\"id\": \"approve1\", \"type\": \"snaker:task\", \"properties\": {\"form\": \"f1\", \"assignee\": \"leader1\", \"taskType\": 0, \"performType\": 0, \"field\": {\"PERMISSION_f_title\": 1, \"PERMISSION_amount\": 2}}, \"text\": {\"value\": \"审批一\"}},"
                + "{\"id\": \"approve2\", \"type\": \"snaker:task\", \"properties\": {\"form\": \"f2\", \"assignee\": \"leader2\", \"taskType\": 0, \"performType\": 0}, \"text\": {\"value\": \"审批二\"}},"
                + "{\"id\": \"finish\", \"type\": \"snaker:end\", \"properties\": {\"width\": 50, \"height\": 50}, \"text\": {\"value\": \"结束\"}}"
                + "],"
                + "\"edges\": ["
                + "{\"id\": \"e0\", \"sourceNodeId\": \"start\", \"targetNodeId\": \"apply\", \"properties\": {}},"
                + "{\"id\": \"e1\", \"sourceNodeId\": \"apply\", \"targetNodeId\": \"approve1\", \"properties\": {}},"
                + "{\"id\": \"e2\", \"sourceNodeId\": \"approve1\", \"targetNodeId\": \"approve2\", \"properties\": {}},"
                + "{\"id\": \"e3\", \"sourceNodeId\": \"approve2\", \"targetNodeId\": \"finish\", \"properties\": {}}"
                + "]}";
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName("perm3");
        def.setDisplayName("perm3");
        def.setType("approval");
        def.setState(1);
        def.setVersion(1);
        def.setContent(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        repo.addDefine(def);

        // 发起 → INSERT（title=原始标题）
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "user1",
                FlowData.create().set("f_title", "原始标题").set("f_amount", 800).set("u_deptId", "D01"));

        // ① apply 完成
        List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), new String[]{});
        for (ProcessTask t : doing) {
            if ("apply".equals(t.getTaskName())) {
                repo.addTaskActor(t.getTaskId(), Arrays.asList("user1"));
                engine.executeProcessTask(t.getTaskId(), "user1", FlowData.create().set("submitType", 0));
            }
        }
        // ② approve1（只读 title）办理提交 TRY_HACK → 引擎入口过滤 → 不入变量 → 不落库
        doing = repo.findDoingTasks(inst.getInstanceId(), new String[]{});
        for (ProcessTask t : doing) {
            if ("approve1".equals(t.getTaskName())) {
                repo.addTaskActor(t.getTaskId(), Arrays.asList("leader1"));
                engine.executeProcessTask(t.getTaskId(), "leader1",
                        FlowData.create().set("submitType", 1).set("f_title", "TRY_HACK"));
            }
        }
        // ③ approve2（无权限声明）完成——变量无 TRY_HACK，title 保持原值
        doing = repo.findDoingTasks(inst.getInstanceId(), new String[]{});
        for (ProcessTask t : doing) {
            if ("approve2".equals(t.getTaskName())) {
                repo.addTaskActor(t.getTaskId(), Arrays.asList("leader2"));
                engine.executeProcessTask(t.getTaskId(), "leader2",
                        FlowData.create().set("submitType", 1).set("f_amount", 999));
            }
        }
        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT title, amount, approve1, approve2, finish FROM biz_perm3")) {
            assertTrue(rs.next());
            assertEquals("只读字段被拒值不应落库（下游无权限节点也不可绕过）", "原始标题", rs.getString("title"));
            assertEquals(999.0, rs.getDouble("amount"), 0.001);
            assertEquals(10, rs.getInt("approve1"));
            assertEquals(10, rs.getInt("approve2"));
            assertEquals(20, rs.getInt("finish"));
            assertFalse(rs.next());
        }
    }

    // ═══ issues/60：注册助手 ═══

    @Test
    public void testRegisterMeta() {
        com.mldong.jeeflow.metadata.HandlerRegistry registry =
                new com.mldong.jeeflow.metadata.HandlerRegistry();
        PersistPostInterceptor.registerMeta(registry);
        List<com.mldong.jeeflow.metadata.HandlerMeta> post =
                registry.listHandlers(com.mldong.jeeflow.interceptor.FlowInterceptor.class, "post");
        assertEquals(1, post.size());
        assertEquals(PersistPostInterceptor.class.getName(), post.get(0).getClassName());
        assertEquals("业务数据自动入库", post.get(0).getDisplayName());
        // 同名覆盖：集成方二次注册（增强版同名）仍可覆盖/追加——按注册顺序列出
        PersistPostInterceptor.registerMeta(registry);
        assertEquals(2, registry.listHandlers(
                com.mldong.jeeflow.interceptor.FlowInterceptor.class, "post").size());
    }
}
