package com.mldong.jeeflow.persist.meta;

import java.util.ArrayList;
import java.util.List;

/**
 * 表元数据（issues/23）——一张业务表的字段存储规范
 *
 * @author mldong
 */
public class TableMeta {

    /** 表名 */
    private String tableName;
    /** 主键列（默认 id） */
    private String primaryKey = "id";
    /** 字段清单 */
    private List<FieldMeta> fields = new ArrayList<>();

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getPrimaryKey() { return primaryKey; }
    public void setPrimaryKey(String primaryKey) {
        this.primaryKey = primaryKey != null && !primaryKey.isEmpty() ? primaryKey : "id";
    }
    public List<FieldMeta> getFields() { return fields; }
    public void setFields(List<FieldMeta> fields) {
        this.fields = fields != null ? fields : new ArrayList<>();
    }

    /** 按字段名查 FieldMeta（大小写不敏感） */
    public FieldMeta findField(String name) {
        if (name == null) return null;
        for (FieldMeta f : fields) {
            if (name.equalsIgnoreCase(f.getName())) return f;
        }
        return null;
    }

    /** 按列名查 FieldMeta（大小写不敏感，用于未消费列判定） */
    public FieldMeta findFieldByColumn(String columnName) {
        if (columnName == null) return null;
        for (FieldMeta f : fields) {
            if (columnName.equalsIgnoreCase(f.getColumnName())) return f;
        }
        return null;
    }
}
