package com.mldong.jeeflow.enums;

/**
 * 任务状态枚举
 *
 * @author mldong
 */
public enum ProcessTaskStateEnum {
    /** 进行中 */
    DOING(10, "进行中"),
    /** 已完成 */
    FINISHED(20, "已完成"),
    /** 已撤回 */
    WITHDRAW(30, "已撤回"),
    /** 强行终止 */
    INTERRUPT(40, "强行终止"),
    /** 挂起 */
    PENDING(50, "挂起"),
    /** 已废弃 */
    ABANDON(99, "已废弃");

    private final Integer code;
    private final String message;

    ProcessTaskStateEnum(Integer code, String message) {
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
