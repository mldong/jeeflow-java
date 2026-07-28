package com.mldong.jeeflow.enums;

/**
 * 流程定义状态
 *
 * @author mldong
 */
public enum ProcessDefineStateEnum {
    /** 禁用 */
    DISABLE(0, "禁用"),
    /** 启用 */
    ENABLE(1, "启用");

    private final Integer code;
    private final String message;

    ProcessDefineStateEnum(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
