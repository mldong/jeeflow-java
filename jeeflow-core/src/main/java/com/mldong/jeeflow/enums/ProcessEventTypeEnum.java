package com.mldong.jeeflow.enums;

/**
 * 流程事件类型
 *
 * @author mldong
 */
public enum ProcessEventTypeEnum {
    /** 流程实例开始 */
    PROCESS_INSTANCE_START(1, "流程实例开始事件"),
    /** 流程实例结束 */
    PROCESS_INSTANCE_END(2, "流程实例结束事件"),
    /** 流程任务开始 */
    PROCESS_TASK_START(3, "流程任务开始事件"),
    /** 流程任务结束 */
    PROCESS_TASK_END(4, "流程任务结束事件");

    private final Integer code;
    private final String message;

    ProcessEventTypeEnum(Integer code, String message) {
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
