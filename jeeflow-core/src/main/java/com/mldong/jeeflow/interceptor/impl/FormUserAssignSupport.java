package com.mldong.jeeflow.interceptor.impl;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.model.NodeModel;
import com.mldong.jeeflow.spi.IOrgUserProvider;
import com.mldong.jeeflow.spi.IUserProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 「表单指定用户」两个内置处理器的共用取数档（issues/199，八语言契约 §3.2–§3.4）。
 *
 * <p>场景＝HR 代员工提交／代理申请：表单里给出被代理人的用户 id，取<b>该员工</b>所属部门的
 * 经理／分管领导，与当前操作人无关。链路＝字段 → user → dept → leader。</p>
 *
 * <p>本类只服务新增的两个 handler：现存 {@link FormFieldAssigneeHandler} 与
 * {@link OrgUserAssignmentHandlers} 里那套 {@code findFieldValue} 一字未动，
 * 四档并集只在本类的 {@link #findFieldValue} 里生效。</p>
 */
public final class FormUserAssignSupport {

    /** 节点唯一编码的编号后缀（task_01 → task），与现存 handler 同一条正则 */
    private static final Pattern NUMBER_SUFFIX_PATTERN = Pattern.compile("^(.+?)_(\\d+)$");

    private FormUserAssignSupport() {}

    /** 当前节点唯一编码：任务创建时 processTask 尚为 null，节点信息从 nodeModel 取（现存 handler 同位） */
    public static String nodeKey(Execution execution) {
        NodeModel nodeModel = execution.getNodeModel();
        return nodeModel == null ? null : nodeModel.getName();
    }

    /**
     * 四档并集查找（顺序即优先级，八语言契约 §3.2）：
     * <ol>
     *   <li>{@code f_<nodeKey>}——引擎变量里表单字段带 {@code f_} 前缀（issues/48 E20）</li>
     *   <li>{@code <nodeKey>} 裸名兜底（兼容存量）</li>
     *   <li>nodeKey 含 {@code #} ⇒ 取<b>最后一个</b> {@code #} 之后的 field：先裸名、再 {@code f_<field>}</li>
     *   <li>nodeKey 匹配 {@code ^(.+?)_(\d+)$} ⇒ base：先 {@code f_<base>}、再 {@code <base>}</li>
     * </ol>
     *
     * <p>第 3、4 两档各补 {@code f_} 变体，是相对参照实现（mldong-fastapi-wf fe9233d）的唯一增量。
     * 判据用 {@code containsKey} 而不是"值非空"：键存在即命中，值为 null 也算命中，
     * 由 {@link #firstUserId} 把 null 折成空串——与 rust/python 的 map-get 判据同形。
     * nodeKey 为空 ⇒ 不拿空前缀去撞变量表，直接返回 null。</p>
     */
    public static Object findFieldValue(FlowData args, String nodeKey) {
        if (args == null || nodeKey == null || nodeKey.isEmpty()) return null;
        if (args.containsKey("f_" + nodeKey)) return args.get("f_" + nodeKey);
        if (args.containsKey(nodeKey)) return args.get(nodeKey);
        int hash = nodeKey.lastIndexOf('#');
        if (hash >= 0) {
            String field = nodeKey.substring(hash + 1);
            if (!field.isEmpty()) {
                if (args.containsKey(field)) return args.get(field);
                if (args.containsKey("f_" + field)) return args.get("f_" + field);
            }
        }
        // matches() 要求前缀至少一字符 ⇒ "_01" 不落这一档（与 rust/python 同判据）
        Matcher matcher = NUMBER_SUFFIX_PATTERN.matcher(nodeKey);
        if (matcher.matches()) {
            String base = matcher.group(1);
            if (args.containsKey("f_" + base)) return args.get("f_" + base);
            if (args.containsKey(base)) return args.get(base);
        }
        return null;
    }

    /**
     * 取字段值里的<b>第一个</b>非空用户 id（逗号串与数组都只取一个，多值语义不在本轮承诺）。
     *
     * <p>数组元素不再二次拆逗号——整枚元素当一个 id（与 Go/Node 同形）。判空只比 trim 后的空串
     * ⇒ {@code "0"} 是合法 id，不被真值判据吃掉。</p>
     */
    public static String firstUserId(Object value) {
        if (value == null) return "";
        List<Object> tokens = new ArrayList<>();
        if (value instanceof Collection) {
            tokens.addAll((Collection<?>) value);
        } else if (value instanceof Object[]) {
            for (Object item : (Object[]) value) tokens.add(item);
        } else {
            for (String part : String.valueOf(value).split(",")) tokens.add(part);
        }
        for (Object token : tokens) {
            if (token == null) continue;
            String id = String.valueOf(token).trim();
            if (!id.isEmpty()) return id;
        }
        return "";
    }

    /**
     * 用户 id → 该用户部门 id。SPI 未注册／用户不存在／部门为空 ⇒ null（取人失败不许炸流程）。
     */
    public static String deptIdOf(String userId) {
        if (userId == null || userId.isEmpty()) return null;
        IUserProvider userProvider = ServiceContext.find(IUserProvider.class);
        if (userProvider == null) return null;
        IUserProvider.UserInfo u = userProvider.getUser(userId);
        return u == null ? null : u.getDeptId();
    }

    /**
     * 部门 id → 领导列表（main=true 取分管领导）。逗号串形状与现存 handler 的 {@code byDept} 同形；
     * SPI 未注册／部门无领导 ⇒ null。
     */
    public static String byDept(String deptId, boolean main) {
        if (deptId == null || deptId.isEmpty()) return null;
        IOrgUserProvider org = ServiceContext.find(IOrgUserProvider.class);
        if (org == null) return null;
        List<String> ids = main ? org.findDeptMainLeaders(deptId) : org.findDeptLeaders(deptId);
        return ids == null || ids.isEmpty() ? null : String.join(",", ids);
    }
}
