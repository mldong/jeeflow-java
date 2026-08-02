package com.mldong.jeeflow.enums;

/**
 * 流程实例状态
 *
 * @author mldong
 */
public enum ProcessInstanceStateEnum implements IDictEnum {
    /** 进行中 */
    DOING(10, "进行中"),
    /** 已完成 */
    FINISHED(20, "已完成"),
    /** 已撤回 */
    WITHDRAW(30, "已撤回"),
    /** 强行终止 */
    INTERRUPT(40, "强行终止"),
    /** 已拒绝 */
    REJECT(45, "已拒绝"),
    /** 挂起 */
    PENDING(50, "挂起"),
    /** 已废弃 */
    ABANDON(99, "已废弃");

    private final Integer code;
    private final String message;

    ProcessInstanceStateEnum(Integer code, String message) {
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
