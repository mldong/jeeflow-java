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
            // issues/137 C（2026-09-29 owner 拍 · spec 04 §任务行 expire_time）：误配的相对档
            // （`xh` 前缀非整数 / `2.5h` 小数 / `3hh` 前缀还带字母 / 光秃秃一个后缀字母）一律**落穿**到
            // 下面的绝对时刻档，解析不出即 NULL——不抛 NumberFormatException 打断建单
            // （"节点属性配错了不该把流程炸掉"），也不退化成"取当前时间"（那等于建单即逾期，比不写更难发现）。
            if (expireTime.endsWith("s")) {
                Integer seconds = parseIntOrNull(expireTime.substring(0, expireTime.length() - 1));
                if (seconds != null) {
                    return toLocalDateTime(new Date(now.getTime() + seconds * 1000L));
                }
            } else if (expireTime.endsWith("m")) {
                Integer minutes = parseIntOrNull(expireTime.substring(0, expireTime.length() - 1));
                if (minutes != null) {
                    return toLocalDateTime(new Date(now.getTime() + minutes * 60000L));
                }
            } else if (expireTime.endsWith("h")) {
                Integer hours = parseIntOrNull(expireTime.substring(0, expireTime.length() - 1));
                if (hours != null) {
                    return toLocalDateTime(new Date(now.getTime() + hours * 3600000L));
                }
            } else if (expireTime.endsWith("d")) {
                Integer days = parseIntOrNull(expireTime.substring(0, expireTime.length() - 1));
                if (days != null) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(now);
                    cal.add(Calendar.DAY_OF_MONTH, days);
                    return toLocalDateTime(cal.getTime());
                }
            }
            try {
                return toLocalDateTime(DATE_FORMAT.parse(expireTime));
            } catch (ParseException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 相对档前缀取整数：非整数（{@code x}/{@code 2.5}/{@code 3h}）返回 null 交调用方**落穿**，不抛。
     *
     * <p>issues/137 C：这一档原本是 {@code Integer.parseInt} 直抛 NumberFormatException，
     * 于是节点把到期表达式写坏时 java 参考实现会打断建单，而其余七栈（无异常/已 try 住）落穿成 NULL——
     * 跨栈口径分叉。按 owner 裁定统一成"误配按未配置处理"：算不出就是 NULL。
     *
     * <p>issues/137 D（owner 2026-10-01 拍"判非负"）：**负数同样算不合法**。放行 {@code -5h} 会算出
     * 一个**过去**的时刻 ⇒ 新建的行当场就是逾期，比"没配到期时间"更难发现，也和上面那条
     * "不许退化成取当前时间"的精神冲突（那正是 126 的病灶形状）。返回 null ⇒ 落穿绝对档 ⇒ NULL。
     * 只裁负、不裁加号：各栈整数解析（python {@code [+-]?}、node {@code [-+]?\d+}、php
     * {@code [+-]?\d{1,18}}）都收 '+'，裁掉加号反而新造一处跨栈分叉。
     */
    private static Integer parseIntOrNull(String text) {
        if (text == null) return null;
        try {
            // issues/137 E（owner 拍"统一 trim" · spec 04 §任务行 expire_time）：**只裁前缀的两端空白**。
            // 到期表达式来自设计器手填/JSON 搬运，`" 2h"` 这种带一个空格的写法很常见，而各栈整数解析
            // 对空白的容忍度天然不同（go `strconv.Atoi` 前先 TrimSpace、rust `.trim()`、.NET `TryParse`
            // 默认就收前后空白、python `int()` 也收，java `Integer.parseInt` 偏偏不收）⇒ 不显式 trim 就是
            // "同一份流程定义在 java 没到期时间、在别家有"。裁的位置只在**前缀**：单位符与末尾空白
            // 不动（`"2h "` 末位是空格、认不出单位，仍按误配落穿），否则会把"整体去空白"这件没立过法的事
            // 顺手做进去。
            int parsed = Integer.parseInt(text.trim());
            return parsed < 0 ? null : parsed;   // issues/137 D：负数同样算不合法（见上）
        } catch (NumberFormatException e) { return null; }
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
