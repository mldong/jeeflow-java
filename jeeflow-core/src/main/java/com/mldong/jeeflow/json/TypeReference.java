package com.mldong.jeeflow.json;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * 泛型类型引用（用于 JSON 反序列化时保持泛型信息）
 *
 * <pre>{@code
 * // 反序列化 List<User>
 * List<User> users = jsonProvider.fromJson(json, new TypeReference<List<User>>() {});
 * }</pre>
 *
 * @author mldong
 */
public abstract class TypeReference<T> {

    private final Type type;

    protected TypeReference() {
        Type superclass = getClass().getGenericSuperclass();
        if (superclass instanceof ParameterizedType) {
            this.type = ((ParameterizedType) superclass).getActualTypeArguments()[0];
        } else {
            throw new IllegalArgumentException("TypeReference must be parameterized, e.g. new TypeReference<List<String>>() {}");
        }
    }

    public Type getType() {
        return type;
    }
}
