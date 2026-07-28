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
