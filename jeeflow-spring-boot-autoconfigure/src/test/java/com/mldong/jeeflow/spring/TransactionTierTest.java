package com.mldong.jeeflow.spring;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
import com.mldong.jeeflow.interceptor.FlowInterceptor;
import com.mldong.jeeflow.persist.jdbc.JdbcDynamicTableWriter;
import com.mldong.jeeflow.spi.IIdGenerator;
import com.mldong.jeeflow.spi.IProcessRepository;
import com.mldong.jeeflow.spi.ITransactionTemplate;
import com.mldong.jeeflow.spi.IUserProvider;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/**
 * 事务契约三档行为判据（spec/12 §12.4；issues/145-1 / C99 ⑥ java 腿）。
 *
 * <p>修前形状：jdbc 仓储与 persist 写入器每条语句向原始 DataSource 各取新连接，
 * 引擎 runInTx 开的 Spring 事务空转——一次动作的多表写不在同一事务里。
 * 修法：装配面把 DataSource 包成 {@link TransactionAwareDataSources} 的事务感知代理
 * （生产接线走 {@link JeeflowAutoConfiguration#jeeflowProcessRepository}，本测试直接调它，
 * 包装被拿掉这一组判据即红）。</p>
 *
 * <p>三档都带"修前形状"对照腿（raw = 原始 DataSource 直传），对照腿断言缺陷症状必须出现——
 * 判据在坏形状下不红 = 测的是空气（§12.4 阳性对照要求）。</p>
 *
 * <p>真库腿：H2 内存库（MODE=MySQL），建表脚本复用 jeeflow-repository-jdbc 的
 * schema-h2.sql（同一枚编辑源，不另抄一份防漂移）。</p>
 */
public class TransactionTierTest {

    private static final String BIZ_TABLE = "biz_tx_probe";
    private static final AtomicLong DEFINE_SEQ = new AtomicLong();

    /** 原始数据源：事务管理器与事务感知代理都必须以它为目标（资源键一致） */
    private JdbcDataSource ds;
    private PlatformTransactionManager tm;
    private SpringTransactionTemplate txTemplate;
    private IProcessRepository repo;
    private JeeflowEngineImpl engine;
    private final AtomicLong idSeq = new AtomicLong(100000);

    @Before
    public void setUp() throws Exception {
        ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:jeeflow_tx_tier_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            for (String sql : loadSchema().split(";")) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
            // DB_CLOSE_DELAY=-1 会跨测试类存活（本类内逐用例亦然），先清干净
            for (String t : new String[]{"wf_process_task_actor", "wf_process_task",
                    "wf_process_cc_instance", "wf_process_instance", "wf_process_define"}) {
                stmt.execute("DELETE FROM " + t);
            }
            stmt.execute("CREATE TABLE IF NOT EXISTS " + BIZ_TABLE + " ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "process_instance_id BIGINT,"
                    + "amount INT)");
            stmt.execute("DELETE FROM " + BIZ_TABLE);
        }
        tm = new DataSourceTransactionManager(ds);
        txTemplate = new SpringTransactionTemplate(tm);
    }

    /** 建表脚本复用 repository-jdbc 的测试资源（相对模块目录；maven 从模块目录跑 surefire） */
    private static String loadSchema() throws Exception {
        Path p = Paths.get("..", "jeeflow-repository-jdbc", "src", "test", "resources", "schema-h2.sql");
        if (!Files.exists(p)) {
            p = Paths.get("jeeflow-repository-jdbc", "src", "test", "resources", "schema-h2.sql");
        }
        assertTrue("schema-h2.sql 找不到（cwd=" + Paths.get(".").toAbsolutePath() + "），判据无法执行", Files.exists(p));
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    // ═══ 装配 ═══

    /** 修后形状：仓储经生产接线（autoconfigure bean 方法）构造，内部收事务感知代理 */
    private void wireFixed() {
        repo = new JeeflowAutoConfiguration().jeeflowProcessRepository(ds);
        wire(repo);
    }

    /** 修前形状对照：原始 DataSource 直传（每条语句自取新连接） */
    private void wireRaw() {
        repo = new com.mldong.jeeflow.repository.JdbcProcessRepository(ds);
        wire(repo);
    }

    private void wire(IProcessRepository repository) {
        Configuration config = new Configuration();          // 重置 ServiceContext（全局静态）
        ServiceContext.put("idGen", (IIdGenerator) idSeq::getAndIncrement);
        ServiceContext.put("repository", repository);
        ServiceContext.put("tx", txTemplate);
        ServiceContext.put("json", new JacksonJsonProvider(mapper()));
        ServiceContext.put("expr", new SpelExpressionEvaluator());
        ServiceContext.put("user", new IUserProvider() {
            @Override
            public UserInfo getUser(String userId) {
                UserInfo u = UserInfo.of(userId);
                u.setDeptId("D01");
                return u;
            }
        });
        engine = new JeeflowEngineImpl();
        engine.configure(config);
    }

    private static ObjectMapper mapper() {
        ObjectMapper m = new ObjectMapper();
        m.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return m;
    }

    /** start → apply(assignee=applicant) → task1(assignee=leader) → end；可选在 apply 挂抛错后置拦截器 */
    private long registerDefine(String name, boolean throwingPost) {
        // 抛错点挂 apply 节点的**后置**拦截器：办理 apply 时 node.execute → 建出 task1 → post 抛错
        // （到达任务节点走 CreateTaskHandler、不跑目标节点 post，所以挂 task1 不会在办理 apply 时触发）
        String applyProps = throwingPost
                ? "{'form':'f1','assignee':'applicant','taskType':0,'performType':0,'postInterceptors':'"
                        + ThrowingPostInterceptor.class.getName() + "'}"
                : "{'form':'f1','assignee':'applicant','taskType':0,'performType':0}";
        String json = ("{'name':'" + name + "','displayName':'事务档验证流程','type':'test','nodes':["
                + "{'id':'start','type':'snaker:start','x':100,'y':200,'properties':{},'text':{'value':'开始'}},"
                + "{'id':'apply','type':'snaker:task','x':250,'y':200,'properties':" + applyProps + ",'text':{'value':'发起申请'}},"
                + "{'id':'task1','type':'snaker:task','x':400,'y':200,'properties':{'form':'f1','assignee':'leader','taskType':0,'performType':0},'text':{'value':'上级审批'}},"
                + "{'id':'end','type':'snaker:end','x':550,'y':200,'properties':{},'text':{'value':'结束'}}],"
                + "'edges':["
                + "{'id':'e1','sourceNodeId':'start','targetNodeId':'apply','properties':{}},"
                + "{'id':'e2','sourceNodeId':'apply','targetNodeId':'task1','properties':{}},"
                + "{'id':'e3','sourceNodeId':'task1','targetNodeId':'end','properties':{}}]}")
                .replace('\'', '"');
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        long id = 910000L + DEFINE_SEQ.incrementAndGet();
        def.setId(id);
        def.setName(name);
        def.setDisplayName("事务档验证流程");
        def.setType("test");
        def.setState(1);
        def.setContent(json.getBytes(StandardCharsets.UTF_8));
        def.setVersion(1);
        repo.saveDefine(def);
        return id;
    }

    private ProcessInstance startInstance(long defineId) {
        return engine.startProcessInstanceById(defineId, "u1", FlowData.create());
    }

    private void agree(ProcessTask task) {
        engine.executeProcessTask(task.getTaskId(), task.getActorIds().get(0),
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));
    }

    // ═══ 空转档（§12.4-3）═══

    /**
     * 修后形状：事务内写一行，提交前同事务连接读得到、独立连接读不到；回滚后不存在。
     * 若仓储其实每句自取新连接，第 1 步"同连接读得到"或第 3 步"回滚后不存在"会失败。
     */
    @Test
    public void emptyRunTier_wrappedRepoSeesUncommittedWriteOnlyOnTxConnection() {
        wireFixed();
        final long id = 930001L;
        try {
            txTemplate.execute((ITransactionTemplate.Supplier<Object>) () -> {
                repo.saveDefine(simpleDefine(id, "tx-tier-emptyrun"));
                assertNotNull("同事务连接必须读得到未提交写（空转档第 1 步）", repo.findDefineById(id));
                assertEquals("独立连接在提交前必须读不到", 0, rawCount("wf_process_define", "id", id));
                throw new IllegalStateException("rollback-me");
            });
        } catch (RuntimeException expected) {
            // 事务回滚
        }
        assertEquals("回滚后该行必须不存在（空转档第 3 步）", 0, rawCount("wf_process_define", "id", id));
    }

    /** 修前形状对照（红样）：原始 DataSource 直传 ⇒ 写入自提交逃逸，回滚撤不掉 */
    @Test
    public void emptyRunTier_rawRepoWriteEscapesRollback() {
        wireRaw();
        final long id = 930002L;
        try {
            txTemplate.execute((ITransactionTemplate.Supplier<Object>) () -> {
                repo.saveDefine(simpleDefine(id, "tx-tier-emptyrun-raw"));
                throw new IllegalStateException("rollback-me");
            });
        } catch (RuntimeException expected) {
            // 事务回滚
        }
        assertEquals("修前形状：回滚后行仍在（写不在事务内，红样）", 1, rawCount("wf_process_define", "id", id));
    }

    // ═══ 原子档（§12.4-1）═══

    /**
     * 修后形状：办理动作中途抛错（task1 节点的后置拦截器注入），
     * 断言该动作的全部写入都不存在——apply 仍在 DOING、task1 不留行。
     */
    @Test
    public void atomicTier_engineActionFailureRollsBackAll() {
        wireFixed();
        long defineId = registerDefine("tx-tier-atomic", true);
        ProcessInstance inst = startInstance(defineId);
        long instId = inst.getInstanceId();

        List<ProcessTask> doing = repo.findDoingTasks(instId, null);
        assertEquals("建单后应只有 apply 一个待办", 1, doing.size());

        boolean threw = false;
        try {
            agree(doing.get(0));
        } catch (RuntimeException e) {
            assertTrue("抛错必须来自注入点（中途失败，而不是别处在写之前就崩）: " + e, causedByInjection(e));
            threw = true;
        }
        assertTrue("注入的拦截器必须让动作抛错（否则这档没测到东西）", threw);

        List<ProcessTask> doingAfter = repo.findDoingTasks(instId, null);
        assertEquals("原子：apply 必须仍在 DOING", 1, doingAfter.size());
        assertEquals("apply", doingAfter.get(0).getTaskName());
        boolean task1Anywhere = doingAfter.stream().anyMatch(t -> "task1".equals(t.getTaskName()))
                || repo.findHistoryTasks(instId).stream().anyMatch(t -> "task1".equals(t.getTaskName()));
        assertFalse("原子：task1 不得留下任何行", task1Anywhere);
    }

    /** 修前形状对照（红样）：同样的中途抛错留下半完成态（apply 已办掉或 task1 已建行） */
    @Test
    public void atomicTier_rawRepoLeavesPartialState() {
        wireRaw();
        long defineId = registerDefine("tx-tier-atomic-raw", true);
        ProcessInstance inst = startInstance(defineId);
        long instId = inst.getInstanceId();
        List<ProcessTask> doing = repo.findDoingTasks(instId, null);
        assertEquals(1, doing.size());

        try {
            agree(doing.get(0));
            fail("注入的拦截器必须让动作抛错");
        } catch (RuntimeException expected) {
            assertTrue("抛错必须来自注入点（中途失败）: " + expected, causedByInjection(expected));
        }

        List<ProcessTask> doingAfter = repo.findDoingTasks(instId, null);
        boolean applyNotDoing = doingAfter.stream().noneMatch(t -> "apply".equals(t.getTaskName()));
        boolean task1Exists = doingAfter.stream().anyMatch(t -> "task1".equals(t.getTaskName()))
                || repo.findHistoryTasks(instId).stream().anyMatch(t -> "task1".equals(t.getTaskName()));
        assertTrue("修前形状：中途抛错必须留下半完成态（红样）", applyNotDoing || task1Exists);
    }

    // ═══ 归属档（§12.4-2：嵌套复用 + 并发隔离）═══

    /** 嵌套：外层事务里发起流程（引擎内层 runInTx 必须并入）——外层回滚，内层写入一起消失；外层提交，写入全在 */
    @Test
    public void ownershipTier_nestedEngineActionJoinsOuter() {
        wireFixed();
        long defineId = registerDefine("tx-tier-nested", false);

        final long[] rolledBack = new long[1];
        try {
            txTemplate.execute((ITransactionTemplate.Supplier<Object>) () -> {
                rolledBack[0] = startInstance(defineId).getInstanceId();
                throw new IllegalStateException("outer-rollback");
            });
        } catch (RuntimeException expected) {
            // 外层回滚
        }
        assertEquals("外层回滚 ⇒ 实例行必须不在（内层没有另开连接/提前提交）",
                0, rawCount("wf_process_instance", "id", rolledBack[0]));
        assertEquals("任务行同理", 0, rawCount("wf_process_task", "process_instance_id", rolledBack[0]));

        // 正半：外层提交 ⇒ 实例与任务全在（并入不等于把写吞掉）
        ProcessInstance kept = txTemplate.execute(() -> startInstance(defineId));
        assertNotNull(kept);
        assertEquals(1, rawCount("wf_process_instance", "id", kept.getInstanceId()));
        assertEquals(1, rawCount("wf_process_task", "process_instance_id", kept.getInstanceId()));
    }

    /** 并发：两个调用作用域各走一次事务，A 让出窗口抛错回滚，B 写完提交 ⇒ A 的行不在且 B 的行在 */
    @Test
    public void ownershipTier_concurrentScopesIsolated() throws Exception {
        wireFixed();
        final long idA = 930021L;
        final long idB = 930022L;
        final CountDownLatch aWrote = new CountDownLatch(1);
        final CountDownLatch bCommitted = new CountDownLatch(1);
        final Throwable[] failures = new Throwable[2];

        Thread ta = new Thread(() -> {
            try {
                txTemplate.execute((ITransactionTemplate.Supplier<Object>) () -> {
                    repo.saveDefine(simpleDefine(idA, "tx-tier-conc-A"));
                    aWrote.countDown();
                    try {
                        assertTrue("B 必须在 10s 内完成", bCommitted.await(10, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                    throw new IllegalStateException("A-rollback");
                });
            } catch (RuntimeException expected) {
                // A 的设计就是回滚
            } catch (Throwable t) {
                failures[0] = t;
            }
        });
        Thread tb = new Thread(() -> {
            try {
                assertTrue("A 必须在 10s 内先写", aWrote.await(10, TimeUnit.SECONDS));
                txTemplate.execute(() -> {
                    repo.saveDefine(simpleDefine(idB, "tx-tier-conc-B"));
                    return null;
                });
                bCommitted.countDown();
            } catch (Throwable t) {
                failures[1] = t;
                bCommitted.countDown();
            }
        });
        ta.start();
        tb.start();
        ta.join(15000);
        tb.join(15000);
        assertNull("并发线程异常: " + failures[0], failures[0]);
        assertNull("并发线程异常: " + failures[1], failures[1]);
        assertEquals("A 回滚后其行必须不在", 0, rawCount("wf_process_define", "id", idA));
        assertEquals("B 的行必须在", 1, rawCount("wf_process_define", "id", idB));
    }

    // ═══ 动作档（spec/12 §12.2 原子面：门面 action ＝ 事务边界）═══

    /**
     * 复合动作 processInstance/startAndExecute（门面自组：engine.start ＋ repo.addTaskActor ＋ engine.execute）
     * 在 execute 段中途抛错 ⇒ 整个动作一行不留（实例/任务/参与者）。
     * 修前形状对照＝下一枚：不包动作边界时 start 的事务已单独提交，实例行留存＝半完成。
     */
    @Test
    public void actionTier_facadeStartAndExecuteRollsBackWholeComposite() {
        wireFixed();
        long defineId = registerDefine("tx-tier-action", true);
        com.mldong.jeeflow.facade.JeeflowFacade facade =
                new com.mldong.jeeflow.facade.JeeflowFacade(engine, repo, null);

        Map<String, Object> args = new HashMap<>();
        args.put("processDefineId", defineId);
        args.put("operator", "u1");
        Map<String, Object> result = facade.flow("processInstance/startAndExecute", args);

        assertNotNull(result);
        assertEquals("复合动作必须以失败信封收场", 99999999, result.get("code"));
        assertEquals("动作档：实例行必须整体回滚", 0,
                rawCount("wf_process_instance", "process_define_id", defineId));
        assertEquals("动作档：任务行必须整体回滚（逐语句提交会留行）", 0, rawCountAll("wf_process_task"));
        assertEquals("动作档：参与者行必须整体回滚", 0, rawCountAll("wf_process_task_actor"));
    }

    /** 修前形状对照（红样）：同样三段调用不经门面动作边界裸拼 ⇒ 中途抛错后实例行留存（半完成） */
    @Test
    public void actionTier_rawCompositionLeavesPartialState() {
        wireFixed();
        long defineId = registerDefine("tx-tier-action-raw", true);
        ProcessInstance inst = startInstance(defineId);                        // 引擎入口事务：单独提交
        List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), null);
        assertEquals(1, doing.size());
        repo.addTaskActor(doing.get(0).getTaskId(), java.util.Collections.singletonList("u1"));
        try {
            agree(doing.get(0));                                               // 入口事务内抛错：只回滚本段
            fail("注入的拦截器必须让动作抛错");
        } catch (RuntimeException expected) {
            assertTrue("抛错必须来自注入点: " + expected, causedByInjection(expected));
        }
        assertEquals("对照（红样）：实例行留存＝半完成", 1,
                rawCount("wf_process_instance", "process_define_id", defineId));
    }

    // ═══ persist 业务表写腿同源（spec/12 §12.2 覆盖面 / C99 ⑥）═══

    /** 修后形状：写入器也收事务感知代理 ⇒ 业务表写与流程写同一条事务，回滚一起撤 */
    @Test
    public void persistLeg_writerJoinsTxAndRollsBack() {
        wireFixed();
        DataSource wrappedWriterDs = TransactionAwareDataSources.transactionAware(ds);
        JdbcDynamicTableWriter writer = new JdbcDynamicTableWriter(wrappedWriterDs);
        try {
            txTemplate.execute((ITransactionTemplate.Supplier<Object>) () -> {
                writer.insert(BIZ_TABLE, bizRow(940001L));
                assertEquals("同事务连接必须读得到业务行", 1, countOn(wrappedWriterDs, BIZ_TABLE, 940001L));
                assertEquals("独立连接在提交前必须读不到", 0, countOn(ds, BIZ_TABLE, 940001L));
                throw new IllegalStateException("rollback-me");
            });
        } catch (RuntimeException expected) {
            // 事务回滚
        }
        assertEquals("回滚后业务行必须不存在（persist 写腿与流程同源 ⇒ 同回滚）",
                0, countOn(ds, BIZ_TABLE, 940001L));
    }

    /** 修前形状对照（红样）：写入器收原始 DataSource ⇒ 每句自提交逃逸，回滚撤不掉 */
    @Test
    public void persistLeg_rawWriterEscapesRollback() {
        wireFixed();
        JdbcDynamicTableWriter writer = new JdbcDynamicTableWriter((DataSource) ds);
        try {
            txTemplate.execute((ITransactionTemplate.Supplier<Object>) () -> {
                writer.insert(BIZ_TABLE, bizRow(940002L));
                throw new IllegalStateException("rollback-me");
            });
        } catch (RuntimeException expected) {
            // 事务回滚
        }
        assertEquals("修前形状：回滚后业务行仍在（写不在事务内，红样）",
                1, countOn(ds, BIZ_TABLE, 940002L));
    }

    // ═══ 工具 ═══

    private static Map<String, Object> bizRow(long instanceId) {
        Map<String, Object> row = new HashMap<>();
        row.put("process_instance_id", instanceId);
        row.put("amount", 100);
        return row;
    }

    private static ProcessInstance.ProcessDefine simpleDefine(long id, String name) {
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setId(id);
        def.setName(name);
        def.setDisplayName("事务档验证流程");
        def.setType("test");
        def.setState(1);
        def.setContent("{}".getBytes(StandardCharsets.UTF_8));
        def.setVersion(1);
        return def;
    }

    private int rawCount(String table, String column, long value) {
        return countOn(ds, table, column, value);
    }

    private int rawCountAll(String table) {
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(1) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static int countOn(DataSource source, String table, long value) {
        return countOn(source, table, "process_instance_id", value);
    }

    private static int countOn(DataSource source, String table, String column, long value) {
        String sql = "SELECT COUNT(1) FROM " + table + " WHERE " + column + " = ?";
        try (Connection conn = source.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 动作中途失败的注入点（apply 节点后置拦截器） */
    public static class ThrowingPostInterceptor implements FlowInterceptor {
        @Override
        public void intercept(Execution execution) {
            throw new IllegalStateException("事务档注入错误：post 拦截器抛错（模拟动作中途失败）");
        }
    }

    /** 异常链里必须能找到注入点抛的那句——防"在写任何东西之前就崩了"的假绿 */
    private static boolean causedByInjection(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof IllegalStateException
                    && c.getMessage() != null && c.getMessage().contains("事务档注入错误")) {
                return true;
            }
        }
        return false;
    }
}
