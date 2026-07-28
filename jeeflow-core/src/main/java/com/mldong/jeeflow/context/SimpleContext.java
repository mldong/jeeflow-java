package com.mldong.jeeflow.context;

import com.mldong.jeeflow.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单的 Map-based 上下文实现，零外部依赖
 *
 * @author mldong
 */
public class SimpleContext implements Context {

    private final Map<String, Object> map = new ConcurrentHashMap<>();

    @Override
    public void put(String name, Object object) {
        map.put(name, object);
    }

    @Override
    public void put(String name, Class<?> clazz) {
        try {
            map.put(name, clazz.getDeclaredConstructor().newInstance());
        } catch (Exception e) {
            throw new RuntimeException("无法实例化 " + clazz.getName(), e);
        }
    }

    @Override
    public boolean exist(String name) {
        return map.get(name) != null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T find(Class<T> clazz) {
        for (Object value : map.values()) {
            if (clazz.isInstance(value)) {
                return (T) value;
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> findList(Class<T> clazz) {
        List<T> result = new ArrayList<>();
        for (Object value : map.values()) {
            if (clazz.isInstance(value)) {
                result.add((T) value);
            }
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T findByName(String name, Class<T> clazz) {
        Object value = map.get(name);
        if (value != null && clazz.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
}
