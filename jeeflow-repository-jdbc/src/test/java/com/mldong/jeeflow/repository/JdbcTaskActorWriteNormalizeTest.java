package com.mldong.jeeflow.repository;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.facade.JeeflowFacade;
import com.mldong.jeeflow.json.IJsonProvider;
import com.mldong.jeeflow.json.TypeReference;
import com.mldong.jeeflow.spi.IIdGenerator;
import com.mldong.jeeflow.spi.IUserProvider;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * 任务参与者写侧归属值归一的 <b>SQL 仓一路</b>（issues/142 B 批 · owner 2026-09-30 拍
 * 「八栈一起收：两形同判据＋写侧兜底＋trim＋哨兵」· spec 06-facade.md §2.11）。
 *
 * <p>与 jeeflow-core 的 {@code TaskActorWriteNormalizeTest}（内存仓一路）跑同一条判据：
 * <b>同一份数据，SQL 仓与内存仓必须给同一个答案</b>（issues/117 场景 27 那把尺子）。
 * 断言全部直接查 {@code wf_process_task_actor.actor_id} 与 {@code wf_process_cc_instance}
 * 的真实列值——内存对象绿不等于落库绿（142 §8.2 的两层冗余教训）。</p>
 *
 * <p>java 现状（普查 142 §2 B 表）：JDBC 仓 {@code addTaskActor} 只挡 {@code a != null}
 * ＋判重，<b>不挡 {@code ""}/空白、不 trim</b>；真正的插入腿 {@code insertTaskActors} 无任何值判据
 * ⇒ 空串/带空格的串直接进归属列，{@code null} 撞 {@code NOT NULL} 约束整批炸。</p>
 */
public class JdbcTaskActorWriteNormalizeTest {

    private JdbcDataSource ds;
    private JdbcProcessRepository repo;
    private JeeflowFacade facade;
    private final AtomicLong idSeq = new AtomicLong(7_000_000L);
    private long instanceSeq = 2_000_000L;

    @Before
    public void setUp() throws Exception {
        ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:jeeflow_actor142_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        String ddl = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get("src/test/resources/schema-h2.sql")), StandardCharsets.UTF_8);
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            for (String sql : ddl.split(";")) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) stmt.execute(trimmed);
            }
            // DB_CLOSE_DELAY=-1 会跨测试类存活，逐表清干净再跑
            for (String t : new String[]{"wf_process_task_actor", "wf_process_cc_instance",
                    "wf_process_task", "wf_process_instance", "wf_process_define"}) {
                stmt.execute("DELETE FROM " + t);
            }
        }
        repo = new JdbcProcessRepository(ds);

        ObjectMapper mapper = new ObjectMapper();
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
                try { return (T) mapper.readValue(json, mapper.constructType(typeRef.getType())); }
                catch (Exception e) { throw new RuntimeException(e); }
            }
            @Override public boolean isJson(String str) {
                return str != null && (str.trim().startsWith("{") || str.trim().startsWith("["));
            }
        };

        Configuration config = new Configuration();      // 重置 ServiceContext（全局静态）
        ServiceContext.put("idGen", (IIdGenerator) idSeq::incrementAndGet);
        ServiceContext.put("repository", repo);
        ServiceContext.put("json", jsonProvider);
        ServiceContext.put("expr", new JdbcRepositoryTest.TestExprEvaluator());
        ServiceContext.put("user", new IUserProvider() {
            @Override public IUserProvider.UserInfo getUser(String userId) {
                return IUserProvider.UserInfo.of(userId);
            }
        });
        JeeflowEngineImpl engine = new JeeflowEngineImpl();
        engine.configure(config);
        facade = new JeeflowFacade(engine, repo, null);
    }

    @After
    public void tearDown() throws Exception {
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
        }
    }

    // ═══ 夹具与取证辅助 ═══

    private long newInstance(String businessNo) throws Exception {
        long id = ++instanceSeq;
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO wf_process_instance (id, process_define_id, state, business_no, operator,"
                             + " create_time, create_user) VALUES (?,1,10,?,'zhangsan',?,?)")) {
            ps.setLong(1, id);
            ps.setString(2, businessNo);
            ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            ps.setString(4, "zhangsan");
            ps.executeUpdate();
        }
        return id;
    }

    /** 直插一条进行中（DOING=10）任务，参与者由 {@code baseActor} 单独落一行。 */
    private long newTask(long instanceId, String taskName, String baseActor) throws Exception {
        long id = idSeq.incrementAndGet();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO wf_process_task (id, process_instance_id, task_name, display_name,"
                             + " task_type, perform_type, task_state, create_time, create_user)"
                             + " VALUES (?, ?, ?, ?, 0, 0, 10, ?, 'zhangsan')")) {
            ps.setLong(1, id);
            ps.setLong(2, instanceId);
            ps.setString(3, taskName);
            ps.setString(4, taskName);
            ps.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        }
        if (baseActor != null) repo.addTaskActor(id, new ArrayList<>(Arrays.asList(baseActor)));
        return id;
    }

    /** 取证：{@code wf_process_task_actor.actor_id} 的真实列值（含 null 行，按插入序）。 */
    private List<String> rawActorRows(long taskId) throws Exception {
        List<String> rows = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT actor_id FROM wf_process_task_actor WHERE process_task_id = ? ORDER BY id")) {
            ps.setLong(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(rs.getString(1));
            }
        }
        return rows;
    }

    private int actorRowCount(long taskId) throws Exception {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM wf_process_task_actor WHERE process_task_id = ?")) {
            ps.setLong(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** cc 表取证：某 (实例, 人) 的 state 列值（没有该行返回 {@code null}）。 */
    private Integer ccState(long instanceId, String actorId) throws Exception {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT state FROM wf_process_cc_instance WHERE process_instance_id = ? AND actor_id = ?")) {
            ps.setLong(1, instanceId);
            ps.setString(2, actorId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Integer.valueOf(rs.getInt(1)) : null;
            }
        }
    }

    /** 历史脏行：直接 SQL 插一条 {@code actor_id=''} 的 cc 行（归一后的入口都建不出它，只有存量数据有）。 */
    private void insertDirtyCcRow(long instanceId) throws Exception {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO wf_process_cc_instance (id, process_instance_id, actor_id, state,"
                             + " create_time, create_user) VALUES (?,?,'',0,?,'zhangsan')")) {
            ps.setLong(1, idSeq.incrementAndGet());
            ps.setLong(2, instanceId);
            ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        }
    }

    private Map<String, Object> args(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i].toString(), kv[i + 1]);
        return m;
    }

    // ═══ §2.11 第 5 行：SQL 仓写侧自己再挡一次（追加腿 ＋ 真正插入腿） ═══

    /**
     * 直连仓储（绕过门面/引擎）给空串、纯空白、带空格的串、同批重复 ⇒ 库里只留 trim 后的有效人。
     * 现状 {@code addTaskActor} 只挡 {@code a != null}，空串/空白全放行。
     */
    @Test
    public void sqlRepoWriteSideTrimsAndDropsBlanks() throws Exception {
        long instanceId = newInstance("ACT142-BLANK");
        long taskId = newTask(instanceId, "approval", "leader");

        repo.addTaskActor(taskId, Arrays.asList("", "   ", " 9301 ", "9301", "9302"));

        assertEquals("SQL 仓写侧：空串/纯空白丢弃、落库值 trim、同批折叠、与既有参与者判重",
                Arrays.asList("leader", "9301", "9302"), rawActorRows(taskId));
        assertEquals("只该有这三行", 3, actorRowCount(taskId));
    }

    /**
     * {@code null} 元素：现状一路是 {@code setString(3, null)} 撞 {@code actor_id NOT NULL}
     * ⇒ 整批 {@code RuntimeException("添加任务参与者失败")}（一人生病、全批停药），
     * 另一路（ {@code addTaskActor} 的 {@code a != null} 分支）压根不落值。归一后必须是"丢弃该元素、
     * 其余照落"，与内存仓同一答案。
     */
    @Test
    public void sqlRepoWriteSideDropsNullActorElement() throws Exception {
        long instanceId = newInstance("ACT142-NULL");
        long taskId = newTask(instanceId, "approval", "leader");

        repo.addTaskActor(taskId, Arrays.asList(null, "9351"));

        assertEquals("null 元素丢弃、同批有效人照落（与内存仓同判据）",
                Arrays.asList("leader", "9351"), rawActorRows(taskId));
    }

    /** 反向哨兵（硬要求④，SQL 通道）：{@code "0"} 与 {@code "00"} 是两个不同的人，都得留下。 */
    @Test
    public void zeroLikeIdsAreDistinctPeopleInTheSqlTable() throws Exception {
        long instanceId = newInstance("ACT142-SENTINEL");
        long taskId = newTask(instanceId, "approval", "leader");

        repo.addTaskActor(taskId, Arrays.asList("0", "00", "  ", "a"));

        assertEquals("只有纯空白丢弃；'0'/'00'/'a' 各算一个人",
                Arrays.asList("leader", "0", "00", "a"), rawActorRows(taskId));
    }

    /** 门面腿落到 SQL 仓的同一判据（两形同判据的落库面）：数组带 null/空串/带空格/重复。 */
    @Test
    public void addCandidateThroughFacadeWritesOnlyTrimmedRowsIntoSqlTable() throws Exception {
        long instanceId = newInstance("ACT142-FUNNEL");
        long taskId = newTask(instanceId, "approval", "leader");

        Map<String, Object> resp = facade.flow("processTask/addCandidate", args(
                "processTaskId", taskId,
                "actorIds", Arrays.asList(" 9401 ", null, "", "   ", "9402", "9401")));

        assertEquals("应成功: " + resp, Integer.valueOf(0), resp.get("code"));
        assertEquals("数组腿与逗号串腿同判据落在同一张表上",
                Arrays.asList("leader", "9401", "9402"), rawActorRows(taskId));
    }

    /**
     * 全量覆写腿（{@code updateTask → saveTaskActors → insertTaskActors}，聚合副本/委托并入走这一支）
     * 也必须在插入前挡一次：只修 {@code addTaskActor} 就等于留一个后门。
     */
    @Test
    public void fullOverwriteLegDropsBlanksInTheSqlTable() throws Exception {
        long instanceId = newInstance("ACT142-OVERWRITE");
        long taskId = newTask(instanceId, "approval", "leader");

        ProcessTask task = repo.findTaskById(taskId);
        assertNotNull(task);
        task.setActorIds(new ArrayList<>(Arrays.asList("leader", "", " 9451 ", null, "9451", "   ")));
        repo.updateTask(task);

        assertEquals("全量覆写腿同样 trim＋丢空＋折叠", Arrays.asList("leader", "9451"), rawActorRows(taskId));
    }

    // ═══ §2.11 第 1＋2 行：transfer 的 fromActor/toActor 归一后再删再插（SQL 通道） ═══

    /**
     * 现状：transfer 腿用 {@code toStr} 不 trim ⇒ 未 trim 的 {@code " leader "} 与干净的
     * {@code operator="leader"} 判不成同一人 ⇒ 门面先撞"无权限转办该任务"，转办根本办不成
     * （下一档才是"原办理人不是该任务参与人"）。归一后删除与插入都取 trim 后的值。
     */
    @Test
    public void transferThroughFacadeTrimsBothActorsIntoSqlTable() throws Exception {
        long instanceId = newInstance("ACT142-TRANSFER");
        long taskId = newTask(instanceId, "approval", "leader");

        Map<String, Object> resp = facade.flow("processTask/transfer", args(
                "processTaskId", taskId, "operator", "leader",
                "fromActor", " leader ", "toActor", " boss "));

        assertEquals("带空格的 fromActor 与库里的人判为同一个（硬要求②）: " + resp,
                Integer.valueOf(0), resp.get("code"));
        assertEquals("摘原人＋加新人都在 trim 后的值上", Arrays.asList("boss"), rawActorRows(taskId));
    }

    // ═══ 主键档（§2.11 与"归属值为空 ⇒ 丢弃"是两件事） ═══

    /** {@code processTaskId} 缺失/空串 ⇒ 响亮报错，且一行都不许多落（不得拿 {@code ''}/{@code 0} 当 id）。 */
    @Test
    public void missingTaskIdIsALoudErrorAndWritesNoRows() throws Exception {
        long instanceId = newInstance("ACT142-PK");
        long taskId = newTask(instanceId, "approval", "leader");

        Map<String, Object> emptyTaskId = facade.flow("processTask/addCandidate",
                args("processTaskId", "", "actorIds", Arrays.asList("9701")));
        assertEquals("主键空串必须报错", "processTaskId/actorIds 缺失", emptyTaskId.get("msg"));

        Map<String, Object> noTaskId = facade.flow("processTask/addCandidate",
                args("actorIds", Arrays.asList("9701")));
        assertEquals("主键缺失同样报错", "processTaskId/actorIds 缺失", noTaskId.get("msg"));

        assertEquals("两个报错档都不许多落一行", Arrays.asList("leader"), rawActorRows(taskId));
    }

    // ═══ §2.11 第 4 行：updateCCStatus 的 operator 归一后再比（含历史脏行那一档） ═══

    /**
     * 两档一起钉：
     * ① {@code actor_id=''} 的<b>历史脏行</b>不得被空 operator 打成 {@code state=1}
     *    （spec §2.11 第 4 行原文点名的正是这个形状："空 operator 会把 state=1 打到历史
     *    {@code actor_id=''} 的脏行上"）；
     * ② 门面腿给带空格的 operator 必须打上 trim 后那一行（硬要求②的比较腿）。
     */
    @Test
    public void blankOperatorDoesNotMarkHistoricalDirtyRowAsRead() throws Exception {
        long instanceId = newInstance("ACT142-CCSTATE");
        insertDirtyCcRow(instanceId);
        repo.createCcInstance(instanceId, "zhangsan", " 9501 ");
        assertEquals("cc 落库行有两条：历史脏行 actor_id='' ＋ 本次的 trim 后值", 2, ccRowCount(instanceId));
        assertEquals("本次抄送落的是 trim 后的串", Integer.valueOf(0), ccState(instanceId, "9501"));
        assertNotNull("夹具：历史脏行在库里", ccState(instanceId, ""));

        repo.updateCcStatus(instanceId, "");
        repo.updateCcStatus(instanceId, "   ");
        repo.updateCcStatus(instanceId, null);
        assertEquals("空 operator 不得把历史 actor_id='' 的脏行打成已读",
                Integer.valueOf(0), ccState(instanceId, ""));
        assertEquals("空 operator 也不得动有效行", Integer.valueOf(0), ccState(instanceId, "9501"));

        Map<String, Object> resp = facade.flow("processInstance/updateCCStatus",
                args("processInstanceId", instanceId, "operator", " 9501 "));
        assertEquals("应成功: " + resp, Integer.valueOf(0), resp.get("code"));
        assertEquals("带空格的 operator 必须打上 trim 后那一行", Integer.valueOf(1), ccState(instanceId, "9501"));
        assertEquals("而脏行仍然不许被这一支顺手动到", Integer.valueOf(0), ccState(instanceId, ""));
    }

    /** cc 表全量行数（某实例的所有 cc 行，不带 actor 条件）。 */
    private int ccRowCount(long instanceId) throws Exception {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM wf_process_cc_instance WHERE process_instance_id = ?")) {
            ps.setLong(1, instanceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
