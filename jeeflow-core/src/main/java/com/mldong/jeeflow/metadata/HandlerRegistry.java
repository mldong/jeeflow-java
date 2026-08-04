package com.mldong.jeeflow.metadata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 处理器注册中心（v1.4.0，引擎元数据能力）
 *
 * <p>引擎按类名/handlerName 懒加载处理器（Java Class.forName，其余语言扩展点函数分发），
 * 本身不感知"有哪些可用实现"。本注册表让集成方显式登记可用实现及元数据
 * （显示名/排序/分组），作为「SPI 实现清单」字典源——字典与运行时配置的
 * className/handlerName 天然一致，杜绝值漂移。</p>
 *
 * <p>可选能力：不注册不影响引擎现有加载行为。</p>
 *
 * @author mldong
 */
public class HandlerRegistry {

    private final Map<Class<?>, List<HandlerMeta>> handlers = new LinkedHashMap<>();

    /** 构造即注册内置通用 handler 元数据（v1.6.0，issues/16）——集成方零注册即得字典 */
    public HandlerRegistry() {
        registerBuiltins();
    }

    /** 内置通用参与者 handler 元数据（注册名 = 内置类全限定名，四语言一致） */
    private void registerBuiltins() {
        Class<?> type = com.mldong.jeeflow.interceptor.AssignmentHandler.class;
        register(type, "com.mldong.jeeflow.interceptor.impl.OperatorAssignmentHandler", "流程发起人", -9999, null);
        register(type, "com.mldong.jeeflow.interceptor.impl.OrgUserAssignmentHandlers$ApplicantDeptLeaderAssignmentHandler", "发起人所属部门经理", 10, null);
        register(type, "com.mldong.jeeflow.interceptor.impl.OrgUserAssignmentHandlers$ApplicantDeptMainLeaderAssignmentHandler", "发起人所属部门分管领导", 20, null);
        register(type, "com.mldong.jeeflow.interceptor.impl.OrgUserAssignmentHandlers$DeptLeaderAssignmentHandler", "当前用户所属部门经理", 30, null);
        register(type, "com.mldong.jeeflow.interceptor.impl.OrgUserAssignmentHandlers$DeptMainLeaderAssignmentHandler", "当前用户所属部门分管领导", 40, null);
        register(type, "com.mldong.jeeflow.interceptor.impl.FormFieldAssigneeHandler", "根据表单字段值分配参与者", 50, null);
        register(type, "com.mldong.jeeflow.interceptor.impl.OrgUserAssignmentHandlers$TaskRoleAssigneeHandler", "根据任务节点唯一编码关联角色分配参与者", 60, null);
    }

    /** 注册单个处理器元数据 */
    public void register(HandlerMeta meta) {
        handlers.computeIfAbsent(meta.getType(), k -> new ArrayList<>()).add(meta);
    }

    /** 注册处理器元数据（便捷方法） */
    public void register(Class<?> type, String className, String displayName, int order, String group) {
        register(new HandlerMeta(type, className, displayName, order, group));
    }

    /** 批量注册 */
    public void registerAll(List<HandlerMeta> metas) {
        if (metas == null) return;
        for (HandlerMeta meta : metas) {
            register(meta);
        }
    }

    /** 按处理器类型列出可用实现（按 order 升序） */
    public List<HandlerMeta> listHandlers(Class<?> type) {
        List<HandlerMeta> list = handlers.getOrDefault(type, new ArrayList<>());
        list.sort(Comparator.comparingInt(HandlerMeta::getOrder));
        return list;
    }

    /** 按处理器类型 + 分组列出（拦截器 pre/post 分组） */
    public List<HandlerMeta> listHandlers(Class<?> type, String group) {
        List<HandlerMeta> result = new ArrayList<>();
        for (HandlerMeta meta : handlers.getOrDefault(type, new ArrayList<>())) {
            if (group == null || group.equals(meta.getGroup())) {
                result.add(meta);
            }
        }
        result.sort(Comparator.comparingInt(HandlerMeta::getOrder));
        return result;
    }

    /** 已注册的处理器接口类型清单 */
    public List<Class<?>> listHandlerTypes() {
        return new ArrayList<>(handlers.keySet());
    }
}
