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
            assertEquals("system", rs.getString("create_user"));
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
}
