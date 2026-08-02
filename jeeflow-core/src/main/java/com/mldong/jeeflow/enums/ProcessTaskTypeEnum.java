package com.mldong.jeeflow.enums;

/**
 * 任务类型
 *
 * @author mldong
 */
public enum ProcessTaskTypeEnum implements IDictEnum {
    /** 主办 */
    MAJOR(0, "主办"),
    /** 协办 */
    SECONDARY(1, "协办"),
    /** 记录 */
    RECORD(2, "记录");

    private final Integer code;
    private final String message;

    ProcessTaskTypeEnum(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    /** 根据 code 或名称查找枚举 */
    public static ProcessTaskTypeEnum codeOf(Object code) {
        if (code == null) return MAJOR;
        String s = code.toString();
        for (ProcessTaskTypeEnum e : values()) {
            if (e.code.equals(toInt(code)) || e.name().equalsIgnoreCase(s) || e.message.equalsIgnoreCase(s)) {
                return e;
            }
        }
        return MAJOR;
    }

    private static Integer toInt(Object obj) {
        if (obj instanceof Number) return ((Number) obj).intValue();
        try { return Integer.parseInt(obj.toString()); } catch (NumberFormatException e) { return -1; }
    }
}
