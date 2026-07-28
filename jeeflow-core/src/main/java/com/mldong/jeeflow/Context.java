package com.mldong.jeeflow;

import java.util.List;

/**
 * 服务上下文接口——引擎 DI 抽象
 *
 * <p>类比 Spring 的 ApplicationContext，但零框架依赖。
 * 引擎通过此接口查找 SPI 实现。集成方可用 {@link com.mldong.jeeflow.context.SimpleContext}
 * 或自己实现（如委托给 Spring 容器）。</p>
 *
 * @author mldong
 */
public interface Context {

    /** 注册服务实例 */
    void put(String name, Object object);

    /** 注册服务类型（通过反射实例化） */
    void put(String name, Class<?> clazz);

    /** 判断服务是否已注册 */
    boolean exist(String name);

    /** 按类型查找单个服务 */
    <T> T find(Class<T> clazz);

    /** 按类型查找所有服务 */
    <T> List<T> findList(Class<T> clazz);

    /** 按名称+类型查找服务 */
    <T> T findByName(String name, Class<T> clazz);
}
