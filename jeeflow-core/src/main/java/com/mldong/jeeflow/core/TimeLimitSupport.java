package com.mldong.jeeflow.core;

import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.model.TaskModel;
import com.mldong.jeeflow.util.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 任务时限四键的求值器（spec 02 §4.5 / spec 04 · 任务时限动作）。
 *
 * <p>只做纯函数：提醒首次时刻、重复间隔、开关判读；动作行落库/取消由引擎经
 * {@link com.mldong.jeeflow.spi.ITimeActionSink} 交给集成层。</p>
 */
public final class TimeLimitSupport {

    private TimeLimitSupport() {}

    private static final SimpleDateFormat ABSOLUTE = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /** autoExecute 仅精确 "Y" 开启；N/未填/未知值一律关闭。 */
    public static boolean autoExecuteEnabled(TaskModel node) {
        return node != null && "Y".equals(node.getAutoExecute());
    }

    /** callback 完成处理器注册名（空＝不调用；两端空白裁掉）。 */
    public static String callbackName(TaskModel node) {
        if (node == null || node.getCallback() == null) return "";
        return node.getCallback().trim();
    }

    /** reminderTime 原串（不 trim：与 expireTime 的"表达式原串即键名/时间串"同尺）。 */
    public static String reminderTimeExpr(TaskModel node) {
        return node == null || node.getReminderTime() == null ? "" : node.getReminderTime();
    }

    /**
     * 首次提醒时刻。档位顺序（规范 02 §4.5）：
     * ① 实例变量同名键；② 相对量 Ns/Nm/Nh/Nd（非负整数，锚点＝任务创建时刻）；
     * ③ 严格 HH:mm（任务创建日当天，早于创建时刻取次日）；④ 完整日期字符串。
     * 任何一档都不得回落成"当前时刻"或 expireTime。
     */
    public static LocalDateTime firstReminder(String expr, FlowData args, LocalDateTime createTime) {
        if (StringUtils.isBlank(expr)) return null;
        if (args != null && args.containsKey(expr)) {
            Object v = args.get(expr);
            if (v instanceof Date) return toLocalDateTime((Date) v);
            if (v instanceof Long) return toLocalDateTime(new Date((Long) v));
            if (v instanceof String) {
                try {
                    return toLocalDateTime(ABSOLUTE.parse((String) v));
                } catch (ParseException e) {
                    return null; // 变量档字符串解析失败即终局（同 expireTime 变量档）
                }
            }
            // 其它类型落穿
        }
        LocalDateTime rel = relative(expr, createTime);
        if (rel != null) return rel;
        LocalDateTime daily = dailyHHmm(expr, createTime);
        if (daily != null) return daily;
        try {
            return toLocalDateTime(ABSOLUTE.parse(expr));
        } catch (ParseException e) {
            return null;
        }
    }

    /** 相对量 Ns/Nm/Nh/Nd：前缀必须是非负整数（负数/小数/字母前缀一律不合法）；d 走日历加天。 */
    private static LocalDateTime relative(String expr, LocalDateTime base) {
        if (expr == null || expr.length() < 2) return null;
        char unit = expr.charAt(expr.length() - 1);
        if (unit != 's' && unit != 'm' && unit != 'h' && unit != 'd') return null;
        Integer n = nonNegativeInt(expr.substring(0, expr.length() - 1));
        if (n == null) return null;
        switch (unit) {
            case 's': return base.plusSeconds(n);
            case 'm': return base.plusMinutes(n);
            case 'h': return base.plusHours(n);
            default: return base.plusDays(n);
        }
    }

    /** 严格 HH:mm（两位时、两位分、0≤时≤23、0≤分≤59）。 */
    private static LocalDateTime dailyHHmm(String expr, LocalDateTime base) {
        if (expr == null || expr.length() != 5 || expr.charAt(2) != ':') return null;
        for (int i = 0; i < 5; i++) {
            if (i == 2) continue;
            char c = expr.charAt(i);
            if (c < '0' || c > '9') return null;
        }
        int h = Integer.parseInt(expr.substring(0, 2));
        int m = Integer.parseInt(expr.substring(3, 5));
        if (h > 23 || m > 59) return null;
        LocalDateTime t = base.toLocalDate().atTime(h, m);
        return t.isBefore(base) ? t.plusDays(1) : t;
    }

    /** 重复间隔（正整数 + s/m/h/d）；空/非法返回 null。 */
    public static Repeat parseRepeat(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.length() < 2) return null;
        char unit = s.charAt(s.length() - 1);
        if (unit != 's' && unit != 'm' && unit != 'h' && unit != 'd') return null;
        Integer n = nonNegativeInt(s.substring(0, s.length() - 1));
        if (n == null || n <= 0) return null;
        return new Repeat(n, unit);
    }

    public static final class Repeat {
        private final int n;
        private final char unit;

        Repeat(int n, char unit) {
            this.n = n;
            this.unit = unit;
        }

        public LocalDateTime next(LocalDateTime from) {
            switch (unit) {
                case 's': return from.plusSeconds(n);
                case 'm': return from.plusMinutes(n);
                case 'h': return from.plusHours(n);
                default: return from.plusDays(n);
            }
        }
    }

    /** 非负整数解析（允许两端 ASCII 空白；拒绝小数/符号以外的杂字符）。 */
    private static Integer nonNegativeInt(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        int start = s.charAt(0) == '+' ? 1 : 0;
        if (start >= s.length()) return null;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return null;
        }
        try {
            int v = Integer.parseInt(s.substring(start));
            return v < 0 ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static long toEpochMilli(LocalDateTime t) {
        return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static LocalDateTime toLocalDateTime(Date d) {
        return LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
    }
}
