package com.mldong.jeeflow.persist.meta;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * 字段存储类型（issues/23，对齐 mldong dev_schema_field 1-5 语义）
 *
 * <ul>
 *   <li>{@link #NORMAL}：直写列</li>
 *   <li>{@link #EXPAND}：对象展开为多列（expandFields 定义子字段列映射）</li>
 *   <li>{@link #JSON}：对象/数组序列化为 JSON 串写列</li>
 *   <li>{@link #ONE2ONE}：子表单条（外键=主表主键，同事务）</li>
 *   <li>{@link #ONE2MANY}：子表多条（外键=主表主键，同事务）</li>
 * </ul>
 *
 * @author mldong
 */
public enum StorageType {

    NORMAL(1),
    EXPAND(2),
    JSON(3),
    ONE2ONE(4),
    ONE2MANY(5);

    private final int code;

    StorageType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /** 按 code 解析（mldong dev_schema_field.storageType 1-5），未知返回 null */
    public static StorageType fromCode(Integer code) {
        if (code == null) return null;
        for (StorageType t : values()) {
            if (t.code == code) return t;
        }
        return null;
    }

    /** 按名称解析（大小写不敏感），未知返回 null */
    public static StorageType fromName(String name) {
        if (name == null || name.isEmpty()) return null;
        for (StorageType t : values()) {
            if (t.name().equalsIgnoreCase(name)) return t;
        }
        return null;
    }

    /** JSON 反序列化：支持名称（"EXPAND"）与数字（2，mldong dev_schema_field 语义） */
    @JsonCreator
    public static StorageType fromJson(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return fromCode(((Number) v).intValue());
        return fromName(String.valueOf(v));
    }
}
