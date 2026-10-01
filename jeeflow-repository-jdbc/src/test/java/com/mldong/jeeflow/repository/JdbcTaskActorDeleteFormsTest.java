package com.mldong.jeeflow.repository;

import com.mldong.jeeflow.util.StringUtils;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;

/**
 * 参与者<b>删除腿</b>归属值判据的 <b>SQL 仓一路</b>（issues/137 §3-6 · owner 2026-10-02 拍
 * 「两形并集」· spec 06-facade.md §processTask/removeTaskActor 语义 6 ＋ §2.11 写点表末行）。
 *
 * <p>与 jeeflow-core 的 {@code TaskActorDeleteFormsTest}（内存仓一路）跑同一条判据：
 * <b>同一份数据，SQL 仓与内存仓必须给同一个答案</b>（issues/117 场景 27 那把尺子）。
 * 断言全部直接查 {@code wf_process_task_actor.actor_id} 的<b>真实列值</b>——内存对象绿不等于落库绿。</p>
 *
 * <p>本文件存在的理由是：语义 6 那两种假成功<b>只有在真库上才照得出来</b>。
 * 库里的历史脏行是"修复前落下的未 trim 原值"（{@code ' 9101 '}），写侧归一后的规范行是
 * {@code '9101'}；排序规则还会插手——MySQL 8.0 默认的 NO PAD 系（{@code utf8mb4_0900_*}）
 * 连<b>尾部</b>空格都算进比较，5.7 默认的 PAD SPACE 系只忽略尾部、<b>前导空格永远算</b>。
 * 所以这里的脏行夹具一律用<b>前导空格</b>（{@code " 9101 "}），在任何排序规则下都与规范行不等，
 * 判据不会因跑在哪台库上而漂。</p>
 *
 * <p>java 改前现状（1.8.36）：{@code JdbcProcessRepository.removeTaskActor} 是裸传——
 * {@code actors} 逐个 {@code setString} 进 {@code IN}，既不产出 trim 形（第三方传 {@code " 8601 "}
 * 删不掉规范行，issues/142 §9.2 那一路），也不丢空值（{@code ''} 入参会把历史
 * {@code actor_id=''} 脏行删掉，那是替脏数据做掉唯一痕迹）。改后走
 * {@link StringUtils#actorDeleteForms(String...)} 一枚单点。</p>
 */
public class JdbcTaskActorDeleteFormsTest {

    private JdbcDataSource ds;
    private JdbcProcessRepository repo;
    private final AtomicLong rowSeq = new AtomicLong(7_300_000L);
    private long taskSeq = 7_310_000L;

    @Before
    public void setUp() throws Exception {
        ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:jeeflow_actor137_delete_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
            for (String t : new String[]{"wf_process_task_actor", "wf_process_task",
                    "wf_process_instance", "wf_process_define"}) {
                stmt.execute("DELETE FROM " + t);
            }
        }
        repo = new JdbcProcessRepository(ds);
    }

    @After
    public void tearDown() throws Exception {
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
        }
    }

    // ═══ 夹具与取证辅助 ═══

    private long newTask() throws Exception {
        long instanceId = rowSeq.incrementAndGet();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO wf_process_instance (id, process_define_id, state, business_no, operator,"
                             + " create_time, create_user) VALUES (?,1,10,?,'zhangsan',?,'zhangsan')")) {
            ps.setLong(1, instanceId);
            ps.setString(2, "b137-" + instanceId);
            ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        }
        long taskId = ++taskSeq;
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO wf_process_task (id, process_instance_id, task_name, display_name,"
                             + " task_type, perform_type, task_state, create_time, create_user)"
                             + " VALUES (?, ?, '审批', '审批', 0, 0, 10, ?, 'zhangsan')")) {
            ps.setLong(1, taskId);
            ps.setLong(2, instanceId);
            ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        }
        return taskId;
    }

    /**
     * <b>绕开写侧归一</b>直插参与者行——模拟"修复前落库的历史数据"。
     * {@link JdbcProcessRepository#addTaskActor} 会 trim＋丢空，正常路径建不出未 trim 脏行
     * 也建不出 {@code actor_id=''} 行，而这两档正是删除腿判据要照的。
     */
    private void seedRawActorRows(long taskId, String... actorIds) throws Exception {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO wf_process_task_actor (id, process_task_id, actor_id, create_time, create_user)"
                             + " VALUES (?,?,?,?,'zhangsan')")) {
            for (String actorId : actorIds) {
                ps.setLong(1, rowSeq.incrementAndGet());
                ps.setLong(2, taskId);
                ps.setString(3, actorId);
                ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
                ps.executeUpdate();
            }
        }
    }

    /** 取证：{@code wf_process_task_actor.actor_id} 的真实列值（按插入序）。 */
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

    // ═══ N 档：两种形态都必须真删得掉 ═══

    /**
     * <b>假成功修复（语义 6 的主场景）</b>：库里躺着未 trim 的历史脏行 {@code ' 9101 '}，
     * 门面按语义 6 交出<b>行上的原值</b>去删 ⇒ 那一行必须真消失。
     *
     * <p>只产出 trim 形的实现（1.8.36 之前的 php/csharp/rust/moon 四栈八处）在这一格会把
     * {@code " 9101 "} 削成 {@code 9101}，{@code DELETE} 命中零行、门面却报成功——
     * <b>被摘的人待办还在</b>。</p>
     */
    @Test
    public void untrimmedLegacyRowIsDeletedByItsRawForm() throws Exception {
        long taskId = newTask();
        seedRawActorRows(taskId, " 9101 ", "leader");

        repo.removeTaskActor(taskId, Arrays.asList(" 9101 "));

        assertEquals("未 trim 的历史脏行必须被原值形真删掉（否则是门面报成功的假成功）",
                Arrays.asList("leader"), rawActorRows(taskId));
    }

    /**
     * <b>issues/142 §9.2 那一路不破</b>：库里是写侧归一后的规范行 {@code '8601'}，
     * 第三方绕过门面直连仓储传 {@code " 8601 "} ⇒ 靠 trim 形命中，也必须删得掉。
     *
     * <p>这一格是 142 B 批"删除位 trim"的既有判据。<b>并集方案下照绿</b>——
     * 所以本轮不需要反向改任何 142 的既有测试（改既有测试期望值是红线，能不动就不动）。</p>
     */
    @Test
    public void normalizedRowIsStillDeletedByTrimmedForm() throws Exception {
        long taskId = newTask();
        seedRawActorRows(taskId, "8601", "leader");

        repo.removeTaskActor(taskId, Arrays.asList(" 8601 "));

        assertEquals("规范行由 trim 形命中（142 §9.2 既有判据不破）",
                Arrays.asList("leader"), rawActorRows(taskId));
    }

    /**
     * 同一个人的两种写法在库里并存（脏行＋规范行）⇒ 两行都要摘掉，其余参与人一行不动
     * （语义 1「只摘不加、不动其他参与人」）。按 §2.11 归一口径它们本就是同一个人，
     * 删两行才是"摘掉这个人"的正确结果，<b>不构成误删</b>。
     */
    @Test
    public void bothFormsOfTheSamePersonAreRemovedTogether() throws Exception {
        long taskId = newTask();
        seedRawActorRows(taskId, " 9101 ", "9101", "leader", "boss");

        repo.removeTaskActor(taskId, Arrays.asList(" 9101 "));

        assertEquals("两形并集 ⇒ 脏行与规范行一起摘，其余参与人原样保留",
                Arrays.asList("leader", "boss"), rawActorRows(taskId));
    }

    // ═══ P 档：空值一律不参与匹配，且不得退化成"清空全部参与者" ═══

    /**
     * <b>脏行保护</b>：空串／纯空白／{@code null} 入参一律不喂 {@code DELETE}——
     * 历史 {@code actor_id=''} 脏行是待另案清洗的取证痕迹，不得被一次空值入参做掉
     * （issues/129 那族"空归属值读全库"的删除位对偶）。
     */
    @Test
    public void blankInputNeverDeletesEmptyActorIdDirtyRow() throws Exception {
        long taskId = newTask();
        seedRawActorRows(taskId, "", "   ", "leader");

        repo.removeTaskActor(taskId, Arrays.asList("", "   ", null));

        assertEquals("空值入参一行都不许删（含历史 actor_id=''/纯空白脏行）",
                Arrays.asList("", "   ", "leader"), rawActorRows(taskId));
        assertEquals(3, actorRowCount(taskId));
    }

    /**
     * <b>不得退化成清空</b>：展开后为空 ⇒ 早退，<b>一条 {@code DELETE} 都不发</b>。
     * 少了这一条，一次误传空串就会把该任务全部参与者清空，留下永远无人可办、
     * 也无法撤回重派的死任务（语义 5「至少需保留一名参与人」的仓储侧对偶）。
     */
    @Test
    public void allBlankInputIsNoOpAndNeverClearsAllActors() throws Exception {
        long taskId = newTask();
        seedRawActorRows(taskId, "zhangsan", "leader");

        repo.removeTaskActor(taskId, Arrays.asList("", "  "));
        repo.removeTaskActor(taskId, Collections.<String>emptyList());
        repo.removeTaskActor(taskId, null);

        assertEquals("空入参三形（纯空白/空列表/null）都是零删除",
                Arrays.asList("zhangsan", "leader"), rawActorRows(taskId));
    }

    /**
     * {@code null} 元素<b>不得</b>被串化成 {@code "null"} 再去删——那会删掉一个真名叫
     * {@code "null"} 的人（§2.11 第 1 行的写侧义务，删除腿同样适用；java 的反面形状是
     * {@code String.valueOf(null)}→{@code "null"}）。
     */
    @Test
    public void nullElementIsNeverStringifiedIntoAMatch() throws Exception {
        long taskId = newTask();
        seedRawActorRows(taskId, "null", "leader");

        repo.removeTaskActor(taskId, Arrays.asList(null, "leader"));

        assertEquals("null 元素丢弃、不得串化成 \"null\" 参与匹配",
                Arrays.asList("null"), rawActorRows(taskId));
    }

    /**
     * 反向哨兵（§2.11 硬要求④）：{@code "0"} 是合法 id，摘 {@code "0"} <b>不得</b>连带摘掉
     * {@code "00"}——它们是两个人。判空一律 {@code trim().isEmpty()}，严禁借语言自带假值判据。
     */
    @Test
    public void zeroLikeIdsAreNotCollapsedTogether() throws Exception {
        long taskId = newTask();
        seedRawActorRows(taskId, "0", "00", "leader");

        repo.removeTaskActor(taskId, Arrays.asList("0"));

        assertEquals("'0' 与 '00' 是两个人，摘一个不得连带另一个",
                Arrays.asList("00", "leader"), rawActorRows(taskId));
    }

    /** 非参与者静默忽略（语义 7 幂等）：一个都没命中 ⇒ 零删除、不抛异常。 */
    @Test
    public void unknownActorIsSilentlyIgnored() throws Exception {
        long taskId = newTask();
        seedRawActorRows(taskId, "zhangsan", "leader");

        repo.removeTaskActor(taskId, Arrays.asList("stranger", " 9999 "));

        assertEquals("非参与者静默忽略，既有参与者一行不动",
                Arrays.asList("zhangsan", "leader"), rawActorRows(taskId));
    }

    /** 任务不存在 ⇒ 零操作、不抛异常（删除腿对不存在的 taskId 不报错）。 */
    @Test
    public void unknownTaskIsNoOpWithoutThrowing() throws Exception {
        repo.removeTaskActor(404404L, Arrays.asList(" 9101 "));

        assertEquals(0, actorRowCount(404404L));
    }
}
