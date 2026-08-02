package com.mldong.jeeflow.metadata;

/**
 * 处理器元数据（v1.4.0，引擎元数据能力）
 *
 * <p>用于「SPI 实现清单」字典：前端流程设计器的 assignmentHandler / candidateHandler /
 * 拦截器配置需要"可用实现清单"（value = 类全名 / handlerName，运行时引擎加载），
 * 集成方把自身可用的实现注册进 {@link HandlerRegistry}，显示名/排序与运行时配置天然一致。</p>
 *
 * @author mldong
 */
public class HandlerMeta {

    /** 处理器接口类型（AssignmentHandler / CandidateHandler / FlowInterceptor） */
    private final Class<?> type;
    /** 处理器标识：Java 为类全名（Class.forName 加载），其余语言为节点配置的 handlerName 字符串（与字典 value 一致） */
    private final String className;
    /** 显示名（字典 label） */
    private final String displayName;
    /** 排序（小在前） */
    private final int order;
    /** 分组（拦截器 pre/post 显式声明；其余可为空） */
    private final String group;

    public HandlerMeta(Class<?> type, String className, String displayName, int order, String group) {
        if (type == null) throw new IllegalArgumentException("type 不能为空");
        if (className == null || className.isEmpty()) throw new IllegalArgumentException("className 不能为空");
        this.type = type;
        this.className = className;
        this.displayName = displayName;
        this.order = order;
        this.group = group;
    }

    public Class<?> getType() { return type; }
    public String getClassName() { return className; }
    public String getDisplayName() { return displayName; }
    public int getOrder() { return order; }
    public String getGroup() { return group; }

    public String getTypeName() {
        return type.getSimpleName();
    }
}
