package com.mldong.jeeflow.repository;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ReapplySupport;
import com.mldong.jeeflow.context.SimpleContext;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;
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
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * 「退回修改、重提直返」在 JDBC 仓储上的落库往返 + 引擎重建 + 并发重提。
 *
 * <p>内存仓储用例证明语义；本类额外证明：分支归属/直返上下文经 SQL 列往返后仍可消费，
 * 且同一申请待办的并发重提在 JDBC 仓上至多一个成功。</p>
 */
public class ReturnToSourceJdbcTest {

    private JdbcDataSource ds;
    private JdbcProcessRepository repo;
    private JeeflowEngine engine;
    private ObjectMapper mapper;
    private Configuration config;
    private IJsonProvider jsonProvider;

    @Before
    public void setUp() throws Exception {
        ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:jeeflow_rts;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        String ddl = new String(Files.readAllBytes(
                Paths.get("src/test/resources/schema-h2.sql")), StandardCharsets.UTF_8);
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            for (String sql : ddl.split(";")) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) stmt.execute(trimmed);
            }
        }

        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        jsonProvider = new IJsonProvider() {
            @Override public String toJson(Object obj) {
                try { return mapper.writeValueAsString(obj); } catch (Exception e) { throw new RuntimeException(e); }
            }
            @Override public <T> T fromJson(String json, Class<T> type) {
                try { return mapper.readValue(json, type); } catch (Exception e) { throw new RuntimeException(e); }
            }
            @Override @SuppressWarnings("unchecked")
            public <T> T fromJson(String json, TypeReference<T> typeRef) {
                try { return (T) mapper.readValue(json, mapper.constructType(typeRef.getType())); }
                catch (Exception e) { throw new RuntimeException(e); }
            }
            @Override public boolean isJson(String str) {
                return str != null && (str.trim().startsWith("{") || str.trim().startsWith("["));
            }
        };

        repo = new JdbcProcessRepository(ds);
        config = new Configuration();
        bindSpis();
        ServiceContext.put("json", jsonProvider);
        engine = new JeeflowEngineImpl();
        engine.configure(config);
    }

    @After
    public void tearDown() throws Exception {
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
        }
    }

    @Test
    public void jdbcReturnReapplyPersistsThroughRepository() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("15-return-to-source-fork.json");
        Long iid = startFlow(def);
        ProcessTask taskA = doing(iid, "taskA");
        ProcessTask taskB = doing(iid, "taskB");
        assertNotNull(taskA);
        assertNotNull(taskB);

        engine.executeAndJumpToFirstTaskNode(taskA.getTaskId(), "userA", submit(RETURN));
        ProcessTask apply = doing(iid, "apply");
        assertNotNull("退回后应有申请待办", apply);
        // 从库中重新读取，确认保留键经 SQL 列往返仍在
        ProcessTask reloaded = repo.findTaskById(apply.getTaskId());
        assertNotNull(ReapplySupport.reapplyOf(reloaded));
        assertEquals(String.valueOf(taskA.getTaskId()),
                String.valueOf(ReapplySupport.reapplyOf(reloaded).get(ReapplySupport.KEY_SOURCE_TASK)));

        // 另一分支先办完到汇合点：申请人仍在修改，不得放行
        engine.executeProcessTask(taskB.getTaskId(), "userB", submit(AGREE));
        assertNull("来源分支仍在申请人手里，汇合不得放行", doing(iid, "taskC"));

        // 同一引擎上从库重读后重提（定义与任务每次请求都重新从仓储加载）
        engine.executeProcessTask(apply.getTaskId(), "applicant", submit(REAPPLY));

        ProcessTask newA = doing(iid, "taskA");
        assertNotNull("重提应生成新的来源待办", newA);
        assertNull("另一分支不得重建", doing(iid, "taskB"));
        assertEquals(1, rows(iid, "taskB"));
        assertEquals(2, rows(iid, "taskA"));
    }

    @Test
    public void jdbcConcurrentReapplyOnlyOnceWins() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("15-return-to-source-fork.json");
        Long iid = startFlow(def);
        ProcessTask taskA = doing(iid, "taskA");
        assertNotNull(taskA);
        engine.executeAndJumpToFirstTaskNode(taskA.getTaskId(), "userA", submit(RETURN));
        ProcessTask apply = doing(iid, "apply");
        assertNotNull(apply);

        final CountDownLatch start = new CountDownLatch(1);
        final AtomicInteger ok = new AtomicInteger();
        final AtomicInteger fail = new AtomicInteger();
        Runnable r = () -> {
            try {
                start.await();
                engine.executeProcessTask(apply.getTaskId(), "applicant", submit(REAPPLY));
                ok.incrementAndGet();
            } catch (Exception e) {
                fail.incrementAndGet();
            }
        };
        Thread t1 = new Thread(r);
        Thread t2 = new Thread(r);
        t1.start();
        t2.start();
        start.countDown();
        t1.join(30000);
        t2.join(30000);

        assertEquals("并发重提应只成功一次", 1, ok.get());
        assertEquals("另一个请求应失败", 1, fail.get());
        assertEquals("只生成一条新来源待办", 2, rows(iid, "taskA"));
        assertEquals("另一分支不重建", 1, rows(iid, "taskB"));
    }

    private void bindSpis() {
        ServiceContext.put("repository", repo);
        ServiceContext.put("json", jsonProvider);
        ServiceContext.put("expr", new IExpressionEvaluator() {
            @Override public Object eval(String expression, Map<String, Object> variables) {
                return Boolean.TRUE;
            }
        });
        ServiceContext.put("user", new IUserProvider() {
            @Override public UserInfo getUser(String userId) {
                UserInfo u = UserInfo.of(userId);
                u.setDeptId("D01");
                u.setDeptName("测试部门");
                u.setPostId("P01");
                u.setPostName("测试岗位");
                return u;
            }
        });
    }

    private static final int RETURN = ProcessSubmitTypeEnum.ROLLBACK_TO_OPERATOR.getCode();
    private static final int REAPPLY = ProcessSubmitTypeEnum.RE_APPLY.getCode();
    private static final int APPLY = ProcessSubmitTypeEnum.APPLY.getCode();
    private static final int AGREE = ProcessSubmitTypeEnum.AGREE.getCode();

    private FlowData submit(int type) {
        return FlowData.create().set(FlowConst.SUBMIT_TYPE, type);
    }

    private ProcessInstance.ProcessDefine registerFlow(String filename) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(
                "../jeeflow-core/src/test/resources/flows/" + filename));
        String jsonStr = new String(bytes, StandardCharsets.UTF_8);
        com.mldong.jeeflow.model.logicflow.LfModel lfModel =
                mapper.readValue(jsonStr, com.mldong.jeeflow.model.logicflow.LfModel.class);
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName(lfModel.getName());
        def.setDisplayName(lfModel.getDisplayName());
        def.setType(lfModel.getType());
        def.setState(1);
        def.setVersion(1);
        def.setContent(bytes);
        repo.saveDefine(def);
        return def;
    }

    private Long startFlow(ProcessInstance.ProcessDefine def) {
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "applicant", FlowData.create());
        for (ProcessTask task : repo.findDoingTasks(inst.getInstanceId(), null)) {
            engine.executeProcessTask(task.getTaskId(), "applicant", submit(APPLY));
        }
        return inst.getInstanceId();
    }

    private ProcessTask doing(Long iid, String name) {
        for (ProcessTask t : repo.findDoingTasks(iid, null)) {
            if (name.equals(t.getTaskName())) return t;
        }
        return null;
    }

    private long rows(Long iid, String name) {
        List<ProcessTask> all = repo.findHistoryTasks(iid);
        long n = 0;
        for (ProcessTask t : all) {
            if (name.equals(t.getTaskName())) n++;
        }
        return n;
    }
}
