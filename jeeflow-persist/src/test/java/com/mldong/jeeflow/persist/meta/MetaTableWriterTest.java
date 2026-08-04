package com.mldong.jeeflow.persist.meta;

import com.mldong.jeeflow.persist.jdbc.JdbcDynamicTableWriter;
import com.mldong.jeeflow.persist.jdbc.JdbcTableReader;
import org.junit.Before;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * MetaTableWriter 测试（issues/23 阶段②：NORMAL/JSON 读写 + 回落）
 */
public class MetaTableWriterTest {

    private DataSource ds;
    private JdbcDynamicTableWriter base;

    @Before
    public void setUp() throws Exception {
        org.h2.jdbcx.JdbcDataSource h2 = new org.h2.jdbcx.JdbcDataSource();
        h2.setURL("jdbc:h2:mem:meta_test;DB_CLOSE_DELAY=-1");
        h2.setUser("sa");
        ds = h2;
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS biz_leave");
            st.execute("CREATE TABLE biz_leave (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "company_name VARCHAR(100)," +
                    "amount DECIMAL(10,2)," +
                    "extra VARCHAR(500)," +        // JSON 列用 varchar 存串（真实业务表约定）
                    "process_instance_id BIGINT," +
                    "apply_user_id VARCHAR(50)," +
                    "create_time VARCHAR(30)," +
                    "create_user VARCHAR(50)," +
                    "update_time VARCHAR(30)," +
                    "update_user VARCHAR(50)," +
                    "is_deleted INT" +
                    ")");
        }
        base = new JdbcDynamicTableWriter(ds);
    }

    private TableMeta leaveMeta() {
        TableMeta meta = new TableMeta();
        meta.setTableName("biz_leave");
        meta.setPrimaryKey("id");
        FieldMeta company = new FieldMeta();
        company.setName("companyName");
        company.setStorageType(StorageType.NORMAL);
        FieldMeta amount = new FieldMeta();
        amount.setName("amount");
        FieldMeta extra = new FieldMeta();
        extra.setName("extra");
        extra.setStorageType(StorageType.JSON);
        meta.setFields(Arrays.asList(company, amount, extra));
        return meta;
    }

    /** ① NORMAL/JSON 写入 + 流程上下文/系统字段（未消费字段直通） */
    @Test
    public void testInsertNormalJson() throws Exception {
        MetaTableWriter writer = new MetaTableWriter(base, t -> leaveMeta());
        Map<String, Object> data = new HashMap<>();
        data.put("companyName", "测试公司");
        data.put("amount", 800);
        data.put("extra", new HashMap<String, Object>() {{
            put("tag", "vip");
            put("level", 3);
        }});
        data.put("process_instance_id", 123L);
        data.put("apply_user_id", "user1");
        writer.insert("biz_leave", data);

        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT company_name, amount, extra, process_instance_id, create_user FROM biz_leave")) {
            assertTrue(rs.next());
            assertEquals("测试公司", rs.getString("company_name"));
            assertEquals(800.0, rs.getDouble("amount"), 0.001);
            String extraJson = rs.getString("extra");
            assertNotNull(extraJson);
            assertTrue(extraJson.contains("\"tag\""));
            assertTrue(extraJson.contains("vip"));
            assertEquals(123L, rs.getLong("process_instance_id"));
            assertEquals("user1", rs.getString("create_user"));   // issues/19: 用户列默认 operator
        }
    }

    /** ② 回显：NORMAL 直读 / JSON 反序列化 / 未消费列带出 */
    @Test
    public void testReadByProcessInstance() throws Exception {
        MetaTableWriter writer = new MetaTableWriter(base, t -> leaveMeta());
        MetaTableReader reader = new MetaTableReader(new JdbcTableReader(ds), t -> leaveMeta());
        Map<String, Object> data = new HashMap<>();
        data.put("companyName", "回显公司");
        data.put("amount", 1200);
        data.put("extra", new HashMap<String, Object>() {{
            put("tag", "gold");
        }});
        data.put("process_instance_id", 456L);
        data.put("apply_user_id", "user9");
        writer.insert("biz_leave", data);

        Map<String, Object> result = reader.readByProcessInstance("biz_leave", 456L);
        assertNotNull(result);
        assertEquals("回显公司", result.get("companyName"));
        assertEquals(1200, ((Number) result.get("amount")).intValue());
        Object extra = result.get("extra");
        assertTrue(extra instanceof Map);
        assertEquals("gold", ((Map<?, ?>) extra).get("tag"));
        assertEquals(456L, ((Number) result.get("process_instance_id")).longValue());
        assertEquals("user9", result.get("apply_user_id"));

        // 无记录
        assertNull(reader.readByProcessInstance("biz_leave", 999L));
    }

    /** ③ 无元数据回落：委托基础 writer（现状行为） */
    @Test
    public void testFallbackWithoutMeta() throws Exception {
        MetaTableWriter writer = new MetaTableWriter(base, t -> null);
        MetaTableReader reader = new MetaTableReader(new JdbcTableReader(ds), t -> null);
        Map<String, Object> data = new HashMap<>();
        data.put("company_name", "回落公司");
        data.put("process_instance_id", 789L);
        writer.insert("biz_leave", data);
        Map<String, Object> result = reader.readByProcessInstance("biz_leave", 789L);
        assertNotNull(result);
        assertEquals("回落公司", result.get("COMPANY_NAME"));   // 无元数据：原样列名（H2 label 大写）
    }

    /** ④ 阶段③全链路：EXPAND 展开 + ONE2ONE/ONE2MANY 子表递归写入与回显组装 */
    @Test
    public void testComplexStorageTypes() throws Exception {
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE biz_leave ADD COLUMN province VARCHAR(50)");
            st.execute("ALTER TABLE biz_leave ADD COLUMN city VARCHAR(50)");
            st.execute("ALTER TABLE biz_leave ADD COLUMN detail_addr VARCHAR(100)");
            st.execute("DROP TABLE IF EXISTS biz_leave_address");
            st.execute("CREATE TABLE biz_leave_address (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "leave_id BIGINT," +
                    "province VARCHAR(50)," +
                    "city VARCHAR(50)," +
                    "detail_addr VARCHAR(100)," +
                    "process_instance_id BIGINT" +
                    ")");
            st.execute("DROP TABLE IF EXISTS biz_leave_item");
            st.execute("CREATE TABLE biz_leave_item (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "leave_id BIGINT," +
                    "name VARCHAR(50)," +
                    "qty INT" +
                    ")");
        }
        // 元数据：主表（EXPAND address + ONE2ONE address + ONE2MANY items）
        IDynamicMetaProvider provider = tableName -> {
            if ("biz_leave".equals(tableName)) {
                TableMeta meta = new TableMeta();
                meta.setTableName("biz_leave");
                FieldMeta company = new FieldMeta();
                company.setName("companyName");
                FieldMeta address = new FieldMeta();
                address.setName("address");
                address.setStorageType(StorageType.EXPAND);
                address.setExpandFields(new LinkedHashMap<String, String>() {{
                    put("province", "province");
                    put("city", "city");
                    put("detail", "detail_addr");
                }});
                FieldMeta addressRel = new FieldMeta();
                addressRel.setName("addressRel");
                addressRel.setStorageType(StorageType.ONE2ONE);
                addressRel.setTargetTable("biz_leave_address");
                addressRel.setForeignKey("leave_id");
                FieldMeta items = new FieldMeta();
                items.setName("items");
                items.setStorageType(StorageType.ONE2MANY);
                items.setTargetTable("biz_leave_item");
                items.setForeignKey("leave_id");
                meta.setFields(Arrays.asList(company, address, addressRel, items));
                return meta;
            }
            if ("biz_leave_address".equals(tableName)) {
                TableMeta meta = new TableMeta();
                meta.setTableName("biz_leave_address");
                FieldMeta province = new FieldMeta();
                province.setName("province");
                FieldMeta city = new FieldMeta();
                city.setName("city");
                FieldMeta detail = new FieldMeta();
                detail.setName("detail");
                detail.setColumnName("detail_addr");
                meta.setFields(Arrays.asList(province, city, detail));
                return meta;
            }
            if ("biz_leave_item".equals(tableName)) {
                TableMeta meta = new TableMeta();
                meta.setTableName("biz_leave_item");
                FieldMeta name = new FieldMeta();
                name.setName("name");
                FieldMeta qty = new FieldMeta();
                qty.setName("qty");
                meta.setFields(Arrays.asList(name, qty));
                return meta;
            }
            return null;
        };
        MetaTableWriter writer = new MetaTableWriter(base, provider);
        MetaTableReader reader = new MetaTableReader(new JdbcTableReader(ds), provider);

        Map<String, Object> data = new HashMap<>();
        data.put("companyName", "复杂公司");
        data.put("address", new HashMap<String, Object>() {{
            put("province", "广东省");
            put("city", "深圳市");
            put("detail", "科技园路1号");
        }});
        data.put("addressRel", new HashMap<String, Object>() {{
            put("province", "广东省");
            put("city", "广州市");
            put("detail", "天河区");
        }});
        data.put("items", Arrays.asList(
                new HashMap<String, Object>() {{ put("name", "电脑"); put("qty", 2); }},
                new HashMap<String, Object>() {{ put("name", "键盘"); put("qty", 3); }}
        ));
        data.put("process_instance_id", 888L);
        Object pk = writer.insert("biz_leave", data);
        assertNotNull("主表应返回主键", pk);

        // 断言落库：EXPAND 展开到主表列
        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT province, city, detail_addr FROM biz_leave WHERE id = " + pk)) {
            assertTrue(rs.next());
            assertEquals("广东省", rs.getString("province"));
            assertEquals("深圳市", rs.getString("city"));
            assertEquals("科技园路1号", rs.getString("detail_addr"));
        }
        // ONE2ONE 子表
        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT province FROM biz_leave_address WHERE leave_id = " + pk)) {
            assertTrue(rs.next());
            assertEquals("广东省", rs.getString("province"));
        }
        // ONE2MANY 子表 2 条
        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(1) FROM biz_leave_item WHERE leave_id = " + pk)) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1));
        }

        // 回显组装：EXPAND 反展开对象 + ONE2ONE 对象 + ONE2MANY 数组
        Map<String, Object> result = reader.readByProcessInstance("biz_leave", 888L);
        assertNotNull(result);
        assertEquals("复杂公司", result.get("companyName"));
        Object addressObj = result.get("address");
        assertTrue(addressObj instanceof Map);
        assertEquals("深圳市", ((Map<?, ?>) addressObj).get("city"));
        Object relObj = result.get("addressRel");
        assertTrue(relObj instanceof Map);
        assertEquals("广州市", ((Map<?, ?>) relObj).get("city"));
        Object itemsObj = result.get("items");
        assertTrue(itemsObj instanceof List);
        assertEquals(2, ((List<?>) itemsObj).size());
        Map<?, ?> first = (Map<?, ?>) ((List<?>) itemsObj).get(0);
        assertEquals("电脑", first.get("name"));
        assertEquals(888L, ((Number) result.get("process_instance_id")).longValue());
    }
}
