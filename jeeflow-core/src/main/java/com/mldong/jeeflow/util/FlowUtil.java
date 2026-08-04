package com.mldong.jeeflow.util;

import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.model.NodeModel;
import com.mldong.jeeflow.model.ProcessModel;
import com.mldong.jeeflow.model.TaskModel;
import com.mldong.jeeflow.spi.IUserProvider;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流程工具类（去 Hutool 依赖）
 *
 * @author mldong
 */
public final class FlowUtil {

    private FlowUtil() {}

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /** 追加用户信息到流程参数 */
    public static void addUserInfoToArgs(String operator, FlowData args, IUserProvider userProvider) {
        if (userProvider == null) return;
        // v1.0.1：系统代执行（flow.auto）/超级管理员（flow.admin）非真实用户，跳过注入（对齐 boot2/boot3）
        if (FlowConst.AUTO_ID.equalsIgnoreCase(operator) || FlowConst.ADMIN_ID.equalsIgnoreCase(operator)) {
            return;
        }
        IUserProvider.UserInfo u = userProvider.getUser(operator);
        if (u == null) return;
        args.put(FlowConst.USER_USER_ID, u.getUserId() != null ? u.getUserId() : operator);
        args.put(FlowConst.USER_REAL_NAME, u.getRealName() != null ? u.getRealName() : operator);
        args.put(FlowConst.USER_DEPT_ID, u.getDeptId());
        args.put(FlowConst.USER_DEPT_NAME, u.getDeptName());
        args.put(FlowConst.USER_POST_ID, u.getPostId());
        args.put(FlowConst.USER_POST_NAME, u.getPostName());
    }

    /** 自动构造标题 */
    public static void addAutoGenTitle(String displayName, FlowData args) {
        String realName = args.getStr(FlowConst.USER_REAL_NAME, "");
        String title = realName + "的" + displayName + "-" + DATE_FORMAT.format(new Date());
        args.put(FlowConst.AUTO_GEN_TITLE, title);
    }

    /** 判断是否为第一个任务节点（开始节点的直接后继） */
    public static boolean isFirstTaskName(ProcessModel model, String taskName) {
        AtomicBoolean result = new AtomicBoolean(false);
        model.getStart().getOutputs().forEach(tm -> {
            if (tm.getTo().equalsIgnoreCase(taskName)) {
                result.set(true);
            }
        });
        return result.get();
    }

    /** 解析期待完成时间 */
    public static java.time.LocalDateTime processTime(String expireTime, FlowData args) {
        if (args.containsKey(expireTime)) {
            Object obj = args.get(expireTime);
            if (obj instanceof Date) {
                return toLocalDateTime((Date) obj);
            } else if (obj instanceof Long) {
                return toLocalDateTime(new Date((Long) obj));
            } else if (obj instanceof String) {
                try {
                    return toLocalDateTime(DATE_FORMAT.parse((String) obj));
                } catch (ParseException e) {
                    return null;
                }
            }
        }
        if (StringUtils.isNotBlank(expireTime)) {
            Date now = new Date();
            if (expireTime.endsWith("s")) {
                int seconds = Integer.parseInt(expireTime.substring(0, expireTime.length() - 1));
                return toLocalDateTime(new Date(now.getTime() + seconds * 1000L));
            } else if (expireTime.endsWith("m")) {
                int minutes = Integer.parseInt(expireTime.substring(0, expireTime.length() - 1));
                return toLocalDateTime(new Date(now.getTime() + minutes * 60000L));
            } else if (expireTime.endsWith("h")) {
                int hours = Integer.parseInt(expireTime.substring(0, expireTime.length() - 1));
                return toLocalDateTime(new Date(now.getTime() + hours * 3600000L));
            } else if (expireTime.endsWith("d")) {
                int days = Integer.parseInt(expireTime.substring(0, expireTime.length() - 1));
                Calendar cal = Calendar.getInstance();
                cal.setTime(now);
                cal.add(Calendar.DAY_OF_MONTH, days);
                return toLocalDateTime(cal.getTime());
            }
            try {
                return toLocalDateTime(DATE_FORMAT.parse(expireTime));
            } catch (ParseException e) {
                return null;
            }
        }
        return null;
    }

    private static java.time.LocalDateTime toLocalDateTime(Date date) {
        return java.time.LocalDateTime.ofInstant(date.toInstant(), java.time.ZoneId.systemDefault());
    }

    // ─── 字段权限（issues/26：办理入口过滤） ──────────────────────────────────

    /** 字段权限键前缀（任务节点 properties.field，vben5-wf 机制，与 persist 拦截器同契约） */
    public static final String FIELD_PERMISSION_PREFIX = "PERMISSION_";
    /** 表单字段前缀（f_） */
    public static final String FORM_FIELD_PREFIX = "f_";

    /**
     * 办理提交按任务节点字段权限过滤（issues/26）：任务节点 field 声明为只读(1)/隐藏(3)的
     * f_ 字段，办理提交的值**不并入流程变量**——被拒值无法经变量落到下游节点写入，
     * 上游只读声明不可被绕过。键格式双兼容（issues/25）：PERMISSION_f_{全名} 优先，
     * PERMISSION_{去前缀名} 兼容；2 可编辑/无声明放行。tf_ 等非表单键不受影响。
     *
     * @return 过滤后的新 args（未命中任务节点/无权限声明时返回原 args）
     */
    public static FlowData filterFieldByPerm(FlowData args, ProcessModel model, String taskName) {
        if (args == null || model == null || taskName == null) return args;
        NodeModel node = model.getNode(taskName);
        if (!(node instanceof TaskModel)) return args;
        FlowData fieldPerm = ((TaskModel) node).getExt();
        if (fieldPerm == null || fieldPerm.isEmpty()) return args;
        FlowData filtered = FlowData.create();
        for (Map.Entry<String, Object> e : args.entrySet()) {
            String key = e.getKey();
            if (key.startsWith(FORM_FIELD_PREFIX) && key.length() > FORM_FIELD_PREFIX.length()) {
                String fieldName = key.substring(FORM_FIELD_PREFIX.length());
                Object p = fieldPerm.get(FIELD_PERMISSION_PREFIX + FORM_FIELD_PREFIX + fieldName);
                if (p == null) p = fieldPerm.get(FIELD_PERMISSION_PREFIX + fieldName);
                if (p != null) {
                    int perm = toInt(p);
                    if (perm == 1 || perm == 3) continue;   // 只读/隐藏：剔除（不入变量）
                }
            }
            filtered.set(key, e.getValue());
        }
        return filtered;
    }

    private static int toInt(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

}
