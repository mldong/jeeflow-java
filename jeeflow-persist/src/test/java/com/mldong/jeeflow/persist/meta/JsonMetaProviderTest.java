package com.mldong.jeeflow.persist.meta;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * 元数据模型 + JSON 配置加载器测试（issues/23 阶段①）
 */
public class JsonMetaProviderTest {

    /** ① 模型：storageType 1-5 数字语义对齐 mldong dev_schema_field */
    @Test
    public void testStorageTypeCodes() {
        assertEquals(1, StorageType.NORMAL.getCode());
        assertEquals(2, StorageType.EXPAND.getCode());
        assertEquals(3, StorageType.JSON.getCode());
        assertEquals(4, StorageType.ONE2ONE.getCode());
        assertEquals(5, StorageType.ONE2MANY.getCode());
        assertEquals(StorageType.EXPAND, StorageType.fromCode(2));
        assertEquals(StorageType.JSON, StorageType.fromName("json"));
        assertNull(StorageType.fromCode(99));
    }

    /** ② JSON 配置加载：文件系统目录，storageType 名称/数字双解析，columnName 缺省转下划线 */
    @Test
    public void testLoadFromDir() throws Exception {
        Path dir = Files.createTempDirectory("persist-meta");
        Files.write(dir.resolve("biz_leave.json"),
                ("{\"tableName\":\"biz_leave\",\"primaryKey\":\"id\",\"fields\":["
                        + "{\"name\":\"companyName\",\"columnName\":\"company_name\",\"storageType\":\"NORMAL\"},"
                        + "{\"name\":\"address\",\"storageType\":2,\"expandFields\":{\"province\":\"province\",\"city\":\"city\"}},"
                        + "{\"name\":\"extra\",\"storageType\":\"JSON\"},"
                        + "{\"name\":\"items\",\"storageType\":5,\"targetTable\":\"biz_leave_item\",\"foreignKey\":\"leave_id\"}"
                        + "]}").getBytes());

        JsonMetaProvider provider = new JsonMetaProvider(dir.toString(), null);
        TableMeta meta = provider.loadTableMeta("biz_leave");
        assertNotNull(meta);
        assertEquals("biz_leave", meta.getTableName());
        assertEquals(4, meta.getFields().size());

        FieldMeta company = meta.findField("companyName");
        assertEquals("company_name", company.getColumnName());
        assertEquals(StorageType.NORMAL, company.getStorageType());

        FieldMeta address = meta.findField("address");
        assertEquals(StorageType.EXPAND, address.getStorageType());
        assertEquals("province", address.getExpandFields().get("province"));

        FieldMeta items = meta.findField("items");
        assertEquals(StorageType.ONE2MANY, items.getStorageType());
        assertEquals("biz_leave_item", items.getTargetTable());
        assertEquals("leave_id", items.getForeignKey());

        // 缓存
        assertSame(meta, provider.loadTableMeta("biz_leave"));
        // 未定义表 → null（回落表结构探测）
        assertNull(provider.loadTableMeta("no_such_table"));
    }

    /** ③ columnName 缺省：驼峰字段名转下划线 */
    @Test
    public void testDefaultColumnName() {
        FieldMeta f = new FieldMeta();
        f.setName("companyName");
        assertEquals("company_name", f.getColumnName());
        FieldMeta f2 = new FieldMeta();
        f2.setName("amount");
        assertEquals("amount", f2.getColumnName());
    }
}
