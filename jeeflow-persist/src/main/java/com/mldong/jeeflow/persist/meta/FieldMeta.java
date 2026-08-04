package com.mldong.jeeflow.persist.meta;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 字段元数据（issues/23）——表单字段 → 存储语义映射
 *
 * @author mldong
 */
public class FieldMeta {

    /** 表单字段名（f_ 去前缀后的名，如 companyName） */
    private String name;
    /** 主表列名（缺省 = name 转下划线） */
    private String columnName;
    /** 存储类型（默认 NORMAL） */
    private StorageType storageType = StorageType.NORMAL;
    /** EXPAND：展开的子字段（表单字段名 → 表列名） */
    private Map<String, String> expandFields = new LinkedHashMap<>();
    /** ONE2ONE / ONE2MANY：子表表名 */
    private String targetTable;
    /** ONE2ONE / ONE2MANY：子表外键列（缺省 = 主表主键列名） */
    private String foreignKey;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColumnName() {
        if (columnName == null || columnName.isEmpty()) {
            return name != null ? toUnderline(name) : null;
        }
        return columnName;
    }
    public void setColumnName(String columnName) { this.columnName = columnName; }
    public StorageType getStorageType() { return storageType; }
    public void setStorageType(StorageType storageType) {
        this.storageType = storageType != null ? storageType : StorageType.NORMAL;
    }
    public Map<String, String> getExpandFields() { return expandFields; }
    public void setExpandFields(Map<String, String> expandFields) {
        this.expandFields = expandFields != null ? expandFields : new LinkedHashMap<>();
    }
    public String getTargetTable() { return targetTable; }
    public void setTargetTable(String targetTable) { this.targetTable = targetTable; }
    public String getForeignKey() { return foreignKey; }
    public void setForeignKey(String foreignKey) { this.foreignKey = foreignKey; }

    /** 驼峰转下划线（companyName → company_name） */
    public static String toUnderline(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public Map<String, String> safeExpandFields() {
        return expandFields != null ? expandFields : Collections.emptyMap();
    }
}
