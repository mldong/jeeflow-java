package com.mldong.jeeflow.enums;

/**
 * 任务参与类型
 *
 * @author mldong
 */
public enum ProcessTaskPerformTypeEnum implements IDictEnum {
    /**
     * 普通参与：多人参与同一任务，任一人完成即可驱动下一步
     */
    NORMAL(0, "普通参与"),
    /**
     * 会签参与：为每人创建独立任务，满足条件后才驱动下一步
     */
    COUNTERSIGN(1, "会签参与");

    private final Integer code;
    private final String message;

    ProcessTaskPerformTypeEnum(Integer code, String message) {
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
    public static ProcessTaskPerformTypeEnum codeOf(Object code) {
        if (code == null) return NORMAL;
        String s = code.toString();
        if ("ALL".equalsIgnoreCase(s)
                || COUNTERSIGN.name().equalsIgnoreCase(s)
                || COUNTERSIGN.code.equals(toInt(code))) {
            return COUNTERSIGN;
        }
        return NORMAL;
    }

    private static Integer toInt(Object obj) {
        if (obj instanceof Number) return ((Number) obj).intValue();
        try { return Integer.parseInt(obj.toString()); } catch (NumberFormatException e) { return -1; }
    }
}
