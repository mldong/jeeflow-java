package com.mldong.jeeflow.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流程数据载体（替代 Hutool Dict），零外部依赖
 *
 * @author mldong
 */
public class FlowData extends LinkedHashMap<String, Object> {

    public FlowData() {
    }

    public FlowData(Map<String, Object> map) {
        super(map);
    }

    public static FlowData create() {
        return new FlowData();
    }

    /**
     * 从已有 Map 创建（浅拷贝）
     */
    public static FlowData of(Map<String, Object> map) {
        return new FlowData(map);
    }

    /**
     * 深拷贝
     */
    public FlowData copy() {
        FlowData data = new FlowData();
        data.putAll(this);
        return data;
    }

    /** 链式设置 */
    public FlowData set(String key, Object value) {
        put(key, value);
        return this;
    }

    /** 批量设置 */
    public FlowData setAll(Map<String, Object> map) {
        putAll(map);
        return this;
    }

    // ---- 类型安全取值 ----

    public String getStr(String key) {
        Object v = get(key);
        return v != null ? v.toString() : null;
    }

    public String getStr(String key, String defaultValue) {
        String v = getStr(key);
        return v != null ? v : defaultValue;
    }

    public Long getLong(String key) {
        Object v = get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    public Integer getInt(String key) {
        Object v = get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    public Integer getInt(String key, Integer defaultValue) {
        Integer v = getInt(key);
        return v != null ? v : defaultValue;
    }

    public Boolean getBool(String key) {
        Object v = get(key);
        if (v == null) return null;
        if (v instanceof Boolean) return (Boolean) v;
        String s = v.toString().toLowerCase();
        return "true".equals(s) || "1".equals(s) || "yes".equals(s);
    }

    public Object getObj(String key) {
        return get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getObj(String key, Class<T> type) {
        Object v = get(key);
        if (v != null && type.isInstance(v)) {
            return (T) v;
        }
        return null;
    }
}
