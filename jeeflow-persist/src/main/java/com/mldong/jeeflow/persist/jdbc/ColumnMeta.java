package com.mldong.jeeflow.persist.jdbc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 表列元数据（缓存对象）——表结构列清单 + 主键列
 *
 * @author mldong
 */
public class ColumnMeta {

    private static final DateTimeFormatter DATETIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 列名（大写比较用） */
    private final String columnName;
    /** 是否主键 */
    private final boolean primaryKey;

    public ColumnMeta(String columnName, boolean primaryKey) {
        this.columnName = columnName;
        this.primaryKey = primaryKey;
    }

    public String getColumnName() { return columnName; }
    public boolean isPrimaryKey() { return primaryKey; }

    /** 列名不区分大小写比较 */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ColumnMeta)) return false;
        return columnName.equalsIgnoreCase(((ColumnMeta) o).columnName);
    }

    @Override
    public int hashCode() {
        return columnName.toLowerCase().hashCode();
    }

    /** 时间格式化（系统字段列约定） */
    public static String now() {
        return LocalDateTime.now().format(DATETIME_FMT);
    }
}
