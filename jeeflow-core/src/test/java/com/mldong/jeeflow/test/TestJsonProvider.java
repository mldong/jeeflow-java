package com.mldong.jeeflow.test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mldong.jeeflow.json.IJsonProvider;
import com.mldong.jeeflow.json.TypeReference;

/**
 * 基于 Jackson 的 JSON 提供者（测试用，不进 JAR）
 *
 * @author mldong
 */
public class TestJsonProvider implements IJsonProvider {

    private final ObjectMapper mapper;

    public TestJsonProvider() {
        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }

    @Override
    public <T> T fromJson(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("JSON 反序列化失败: " + type.getName(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T fromJson(String json, TypeReference<T> typeRef) {
        try {
            com.fasterxml.jackson.core.type.TypeReference<Object> jacksonRef =
                    new com.fasterxml.jackson.core.type.TypeReference<Object>() {
                        @Override
                        public java.lang.reflect.Type getType() {
                            return typeRef.getType();
                        }
                    };
            return (T) mapper.readValue(json, jacksonRef);
        } catch (Exception e) {
            throw new RuntimeException("JSON 泛型反序列化失败", e);
        }
    }

    @Override
    public boolean isJson(String str) {
        if (str == null || str.isEmpty()) return false;
        str = str.trim();
        return (str.startsWith("{") && str.endsWith("}"))
                || (str.startsWith("[") && str.endsWith("]"));
    }

    public ObjectMapper getMapper() {
        return mapper;
    }
}
