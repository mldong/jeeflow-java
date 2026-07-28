package com.mldong.jeeflow.spring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.mldong.jeeflow.json.IJsonProvider;
import com.mldong.jeeflow.json.TypeReference;

/**
 * 基于 Jackson 的 JSON 提供者（复用 Spring Boot 自动配置的 ObjectMapper）
 *
 * @author mldong
 */
public class JacksonJsonProvider implements IJsonProvider {

    private final ObjectMapper mapper;

    public JacksonJsonProvider(ObjectMapper mapper) {
        this.mapper = mapper.copy();
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // Long 序列化为 String，避免 JavaScript 精度丢失
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(long.class, ToStringSerializer.instance);
        this.mapper.registerModule(module);
    }

    @Override
    public String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }

    @Override
    public <T> T fromJson(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 反序列化失败: " + type.getName(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T fromJson(String json, TypeReference<T> typeRef) {
        try {
            return (T) mapper.readValue(json, mapper.constructType(typeRef.getType()));
        } catch (JsonProcessingException e) {
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
}
