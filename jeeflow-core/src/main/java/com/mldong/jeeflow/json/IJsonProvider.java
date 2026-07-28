package com.mldong.jeeflow.json;

/**
 * JSON 序列化/反序列化提供者 SPI
 * <p>
 * 引擎核心不依赖任何 JSON 库，集成方注入实现（Jackson/Gson/Fastjson 等）。
 * 如需开箱即用，可依赖 jeeflow-store-jdbc 等内置模块。
 *
 * @author mldong
 */
public interface IJsonProvider {

    /** 对象转 JSON 字符串 */
    String toJson(Object obj);

    /** JSON 字符串转指定类型对象 */
    <T> T fromJson(String json, Class<T> type);

    /** JSON 字符串转泛型对象（如 List&lt;User&gt;） */
    <T> T fromJson(String json, TypeReference<T> typeRef);

    /** 判断字符串是否为 JSON */
    boolean isJson(String str);
}
