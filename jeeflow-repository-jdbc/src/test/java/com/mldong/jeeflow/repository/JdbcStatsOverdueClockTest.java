package com.mldong.jeeflow.repository;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * issues/125 · 逾期判据的"现在"必须由引擎侧供给，不能是 SQL 里的 {@code NOW()}。
 *
 * <p>{@code NOW()} 取的是<b>数据库会话时区</b>的墙钟，而 {@code expire_time} 是引擎进程钟写进去的裸墙钟。
 * 两者同基准时看不出错（H2 与宿主同区、MySQL 开发库 {@code time_zone=+08:00} 而应用本地钟也是东八），
 * 一旦宿主时区 ≠ 库时区（容器 UTC + RDS 东八是常见组合），逾期数就整批平移一个时区差。</p>
 *
 * <p>本用例的"牙"在第三段：<b>注入一把偏移钟</b>后判据必须跟着动。
 * 旧实现（SQL 里写 {@code NOW()}）不吃注入 ⇒ 该断言必红——已用临时改回旧写法验证过（变异对照），
 * 不是"看起来能过"的等值断言。</p>
 */
public class JdbcStatsOverdueClockTest {

    /** 固定"现在"：2026-08-01 12:00:00，判据只与它比，不随真实时钟漂移 */
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 8, 1, 12, 0, 0);

    private JdbcDataSource ds;

    @Before
    public void setUp() throws Exception {
        // 独立库名：别和 JdbcRepositoryTest 的 jeeflow_test 共用内存库（同 JVM 顺序跑会串数据）
        ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:jeeflow_overdue_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        String ddl = new String(Files.readAllBytes(
                Paths.get("src/test/resources/schema-h2.sql")), StandardCharsets.UTF_8);
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            for (String sql : ddl.split(";")) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) stmt.execute(trimmed);
            }
            // 建表之后再清场：同一 JVM 里若 jeeflow_overdue_test 已被上一轮用过，避免脏行数影响断言
            stmt.execute("DELETE FROM wf_process_task");
        }
    }

    /** 可注入"现在"的仓储子类：统计判据的时钟出口（statsNow）就是本用例的被测面 */
    private JdbcProcessRepository repoWithClock(LocalDateTime now) {
        return new JdbcProcessRepository(ds) {
            @Override
            protected LocalDateTime statsNow() {
                return now;
            }
        };
    }

    /** 造一行"进行中"任务，expire_time 可空；列与占位符一一对应（14 个） */
    private void insertDoingTask(long id, LocalDateTime expireTime) throws Exception {
        String sql = "INSERT INTO wf_process_task (id, process_instance_id, task_name, display_name, " +
                "task_type, perform_type, task_state, operator, expire_time, variable, " +
                "create_time, create_user, update_time, update_user) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = ds.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setLong(2, id);
            ps.setString(3, "t" + id);
            ps.setString(4, "逾期判据用例");
            ps.setInt(5, 0);
            ps.setInt(6, 0);
            ps.setInt(7, 10);                    // 10 = 进行中
            ps.setString(8, "u1");
            if (expireTime == null) {
                ps.setNull(9, java.sql.Types.TIMESTAMP);
            } else {
                ps.setTimestamp(9, Timestamp.valueOf(expireTime));
            }
            ps.setString(10, "{}");
            ps.setTimestamp(11, Timestamp.valueOf(BASE));
            ps.setString(12, "clocktest");
            ps.setTimestamp(13, Timestamp.valueOf(BASE));
            ps.setString(14, "clocktest");
            ps.executeUpdate();
        }
    }

    /**
     * 正向 + 负向 + "有牙"三件一次做完。
     * 数据集：①无到期时间 ②早于 BASE 1 小时（已过期）③晚于 BASE 1 小时（未过期）④晚于 BASE 8 小时
     */
    @Test
    public void overdueJudgedByEngineClockNotDbClock() throws Exception {
        insertDoingTask(900001L, null);
        insertDoingTask(900002L, BASE.minusHours(1));
        insertDoingTask(900003L, BASE.plusHours(1));
        insertDoingTask(900004L, BASE.plusHours(8));

        // 1 正向：注入 BASE ⇒ 只有 900002 过期（NULL 那行永不计入），pending 全算
        assertArrayEquals("注入 BASE 时的 (pending, overdue)",
                new int[]{4, 1}, repoWithClock(BASE).statsPendingAndOverdueCount());

        // 2 负向（边界）：NULL expire_time 不得被算成逾期——把钟拨到很久以后仍只有 3 条
        assertArrayEquals("钟拨到 +30 天时逾期应为 3（无到期时间的行不受影响）",
                new int[]{4, 3}, repoWithClock(BASE.plusDays(30)).statsPendingAndOverdueCount());

        // 3 有牙：注入钟前移 2 小时，900003 应转为逾期 ⇒ overdue 从 1 变 2。
        //    旧实现（SQL 里写 NOW()）取的是 H2 的真实当前时间，不随注入变 ⇒ 这条必红（变异对照已做）。
        int[] shifted = repoWithClock(BASE.plusHours(2)).statsPendingAndOverdueCount();
        assertEquals("注入钟前移 2 小时后，900003 必须转为逾期（判据跟着注入值走）", 2, shifted[1]);

        // 4 回归：同一注入值重复调用结果稳定（本方法只读不写）
        assertArrayEquals("重复调用应稳定", shifted, repoWithClock(BASE.plusHours(2)).statsPendingAndOverdueCount());
    }
}
