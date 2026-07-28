package com.mldong.jeeflow.enums;

/**
 * 会签类型
 *
 * @author mldong
 */
public enum CountersignTypeEnum {
    /** 并行会签 */
    PARALLEL(0, "并行会签"),
    /** 串行会签 */
    SEQUENTIAL(1, "串行会签");

    private final Integer code;
    private final String message;

    CountersignTypeEnum(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public static CountersignTypeEnum codeOf(Object code) {
        if (code == null) return PARALLEL;
        String s = code.toString();
        for (CountersignTypeEnum e : values()) {
            if (e.code.equals(toInt(code)) || e.name().equalsIgnoreCase(s) || e.message.equalsIgnoreCase(s)) {
                return e;
            }
        }
        return PARALLEL;
    }

    private static Integer toInt(Object obj) {
        if (obj instanceof Number) return ((Number) obj).intValue();
        try { return Integer.parseInt(obj.toString()); } catch (NumberFormatException e) { return -1; }
    }
}
