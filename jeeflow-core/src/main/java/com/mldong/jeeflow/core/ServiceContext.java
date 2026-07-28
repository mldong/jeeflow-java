package com.mldong.jeeflow.core;

import com.mldong.jeeflow.Context;
import com.mldong.jeeflow.enums.WfErrEnum;
import com.mldong.jeeflow.JeeflowException;

import java.util.List;

/**
 * 服务上下文静态门面
 *
 * <p>引擎内部统一通过此类查找 SPI 实现。使用前需先调用 {@link #setContext(Context)} 注册上下文。</p>
 *
 * @author mldong
 */
public final class ServiceContext {

    private static volatile Context context;

    private ServiceContext() {
    }

    public static void setContext(Context ctx) {
        context = ctx;
    }

    public static Context getContext() {
        return context;
    }

    public static void put(String name, Object object) {
        ensureContext();
        context.put(name, object);
    }

    public static void put(String name, Class<?> clazz) {
        ensureContext();
        context.put(name, clazz);
    }

    public static boolean exist(String name) {
        ensureContext();
        return context.exist(name);
    }

    public static <T> T find(Class<T> clazz) {
        ensureContext();
        return context.find(clazz);
    }

    public static <T> List<T> findList(Class<T> clazz) {
        ensureContext();
        return context.findList(clazz);
    }

    public static <T> T findByName(String name, Class<T> clazz) {
        ensureContext();
        return context.findByName(name, clazz);
    }

    private static void ensureContext() {
        if (context == null) {
            throw new JeeflowException(WfErrEnum.SPI_NOT_REGISTERED);
        }
    }
}
