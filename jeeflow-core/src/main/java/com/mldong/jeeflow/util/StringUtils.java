package com.mldong.jeeflow.util;

/**
 * 字符串工具（零依赖，替代 Hutool StrUtil 的少量方法）
 *
 * @author mldong
 */
public final class StringUtils {

    private StringUtils() {
    }

    public static boolean isEmpty(CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

    public static boolean isNotEmpty(CharSequence cs) {
        return !isEmpty(cs);
    }

    public static boolean isBlank(CharSequence cs) {
        if (cs == null) return true;
        for (int i = 0, len = cs.length(); i < len; i++) {
            if (!Character.isWhitespace(cs.charAt(i))) return false;
        }
        return true;
    }

    public static boolean isNotBlank(CharSequence cs) {
        return !isBlank(cs);
    }

    /**
     * 抄送人集合归一（issues/141 G10「空不创建行」，spec 06-facade.md §2.10）：
     * 三条入口（发起 {@code f_ccActors}／办理 {@code tf_ccActors}／门面手动
     * {@code createCCInstance}）解析出的<b>逗号串与数组两种形态</b>都先过这一支——
     * 逐元素 trim，<b>空串与纯空白丢弃</b>，同一次调用内的重复折叠（顺序保持）。
     *
     * <p>丢完为空 ⇒ 调用方不得建 cc 行、也不得 fire 码 4。</p>
     *
     * <p>java 尤其需要这一条：{@code "".split(",")} 在 Java 里得到<b>一个空元素</b>而不是零个
     * ⇒ 旧形状落出一条 {@code actor_id=''} 的 cc 行。空归属值正是 issues/129 那族
     * "空 operator 读全库"的病根，不能从抄送侧继续往里灌。落库/比较一律用 trim 后的值，
     * {@code " 123 "} 与 {@code "123"} 判为同一个人，与 G2 的写侧判重咬合。</p>
     */
    public static java.util.List<String> normalizeCcActors(String... raw) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        if (raw == null) return out;
        for (String actorId : raw) {
            if (actorId == null) continue;
            String trimmed = actorId.trim();
            if (trimmed.isEmpty()) continue;
            if (!out.contains(trimmed)) out.add(trimmed);
        }
        return out;
    }

    /**
     * 简单格式化：用 {} 占位符替换参数
     */
    public static String format(String template, Object... args) {
        if (template == null || args == null || args.length == 0) return template;
        StringBuilder sb = new StringBuilder();
        int argIdx = 0;
        for (int i = 0, len = template.length(); i < len; i++) {
            char c = template.charAt(i);
            if (c == '{' && i + 1 < len && template.charAt(i + 1) == '}') {
                sb.append(argIdx < args.length ? (args[argIdx] != null ? args[argIdx] : "null") : "{}");
                argIdx++;
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 首字母小写
     */
    public static String lowerFirst(CharSequence str) {
        if (str == null || str.length() == 0) return "";
        if (str.length() == 1) return str.toString().toLowerCase();
        char first = Character.toLowerCase(str.charAt(0));
        return first + str.subSequence(1, str.length()).toString();
    }
}
