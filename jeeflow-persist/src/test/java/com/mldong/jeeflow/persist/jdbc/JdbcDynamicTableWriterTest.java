package com.mldong.jeeflow.persist.jdbc;

import com.mldong.jeeflow.persist.DynamicTableWriter;
import org.junit.Before;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * JdbcDynamicTableWriter 单元测试（H2）——issues/18
 */
public class JdbcDynamicTableWriterTest {

    private DataSource ds;
    private JdbcDynamicTableWriter writer;

    @Before
    public void setUp() throws Exception {
        org.h2.jdbcx.JdbcDataSource h2 = new org.h2.jdbcx.JdbcDataSource();
        h2.setURL("jdbc:h2:mem:persist_test;DB_CLOSE_DELAY=-1");
        h2.setUser("sa");
        ds = h2;
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS biz_leave");
            st.execute("CREATE TABLE biz_leave (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "title VARCHAR(100)," +
                    "amount DECIMAL(10,2)," +
                    "start_time VARCHAR(30)," +
                    "process_instance_id BIGINT," +
                    "apply_user_id VARCHAR(50)," +
                    "create_time VARCHAR(30)," +
                    "update_time VARCHAR(30)" +
                    ")");
        }
        writer = new JdbcDynamicTableWriter(ds);
    }

    /** ① 全字段插入：输入列 = 表列，全部写入，主键返回 */
    @Test
    public void testFullInsert() throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", "年假申请");
        data.put("amount", 1000.5);
        data.put("process_instance_id", 1L);
        Object id = writer.insert("biz_leave", data);
        assertNotNull("应返回主键", id);

        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT title, amount, process_instance_id FROM biz_leave")) {
            assertTrue(rs.next());
            assertEquals("年假申请", rs.getString("title"));
            assertEquals(1000.5, rs.getDouble("amount"), 0.001);
            assertEquals(1L, rs.getLong("process_instance_id"));
        }
    }

    /** ② 缺列过滤：输入字段 > 表列，多余剔除 */
    @Test
    public void testFilterColumns() throws Exception {
        List<String> filtered = writer.filterColumns("biz_leave",
                Arrays.asList("title", "not_exist_col", "amount", "also_missing"));
        assertEquals(Arrays.asList("title", "amount"), filtered);
    }

    /** ③ 类型转换：null 处理 + 字符串数字 */
    @Test
    public void testNullAndTypes() throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", null);
        data.put("amount", "999.99");   // 字符串数字
        writer.insert("biz_leave", data);
        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT title, amount FROM biz_leave")) {
            assertTrue(rs.next());
            assertNull(rs.getString("title"));
            assertEquals(999.99, rs.getDouble("amount"), 0.001);
        }
    }

    /** ④ 防注入：字段值含 SQL 片段安全（参数化验证） */
    @Test
    public void testSqlInjection() throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", "x' OR 1=1 --");
        writer.insert("biz_leave", data);
        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(1) FROM biz_leave")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));   // 只插入 1 条，未被注入放大
        }
    }

    /** ⑤ 表名安全：sys_ 前缀 / 非法字符拒绝 */
    @Test(expected = IllegalArgumentException.class)
    public void testRejectSysTable() {
        writer.insert("sys_user", new LinkedHashMap<>());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRejectIllegalChar() {
        writer.insert("biz; DROP TABLE", new LinkedHashMap<>());
    }

    /** ⑥ 幂等：同 bizKey 二次检查返回已存在 */
    @Test
    public void testIdempotent() throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", "t1");
        data.put("process_instance_id", 100L);
        writer.insert("biz_leave", data);
        assertTrue("同 processInstanceId 应已存在",
                writer.exists("biz_leave", "process_instance_id", 100L));
        assertFalse("不同 id 应不存在",
                writer.exists("biz_leave", "process_instance_id", 200L));
    }

    /** ⑦ 系统字段：配置列填充，未配置跳过 */
    @Test
    public void testSystemFields() throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", "sys-field-test");
        writer.fillSystemFields(data, true);
        assertNotNull("create_time 应填充", data.get("create_time"));
        assertNotNull("update_time 应填充", data.get("update_time"));
        assertTrue("已存在的值不覆盖", data.containsKey("title"));

        // 未配置 is_deleted（本表无该列）→ 不报错（filter 由 insert 内部做）
        data.put("is_deleted", 0);
        writer.insert("biz_leave", data);
        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT create_time FROM biz_leave")) {
            assertTrue(rs.next());
            assertNotNull(rs.getString("create_time"));
        }
    }

    /** ⑧ 宽松列匹配（issues/20）：驼峰表单字段 ↔ 下划线表列，写入保持表列原名 */
    @Test
    public void testLooseCamelMatch() throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("startTime", "09:00:00");   // 驼峰 key
        data.put("processInstanceId", 55L);  // 驼峰 key
        writer.insert("biz_leave", data);
        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT start_time, process_instance_id FROM biz_leave")) {
            assertTrue("驼峰 key 应落到下划线列", rs.next());
            assertEquals("09:00:00", rs.getString("start_time"));
            assertEquals(55L, rs.getLong("process_instance_id"));
        }
        // filterColumns 宽松匹配
        assertEquals(2, writer.filterColumns("biz_leave",
                Arrays.asList("startTime", "processInstanceId", "no_such")).size());
    }

    /** ⑨ 严格列匹配（issues/20）：显式开启后驼峰不再匹配 */
    @Test
    public void testStrictColumnMatch() throws Exception {
        writer.setStrictColumnMatch(true);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("startTime", "09:00:00");   // 严格模式：驼峰不匹配表列 start_time
        data.put("title", "strict");
        writer.insert("biz_leave", data);
        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT title, start_time FROM biz_leave")) {
            assertTrue(rs.next());
            assertEquals("strict", rs.getString("title"));
            assertNull("严格模式下驼峰 key 应被过滤", rs.getString("start_time"));
        }
    }
}
