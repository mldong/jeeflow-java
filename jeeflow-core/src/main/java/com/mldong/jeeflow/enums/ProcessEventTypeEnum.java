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
    /**
     * 抄送知会（issues/102 新增，六语言统一）。
     *
     * <p>4 号位原为 {@code PROCESS_TASK_END}（流程任务结束）——引擎从不 fire 独立任务结束事件
     * （spec 04-extensions §7「Java 从不 fire PROCESS_TASK_END」），该枚举值此前从未被任何 fire 点/
     * 监听器消费（全联邦 grep 证实零引用；集成层监听器亦注明「TASK_END 引擎不 fire」）。故 4 号位
     * 复用于 {@code CC_CREATE}，与 PHP 参考实现（v1.3.8 {@code CC_CREATE=4}，已发版）码值对齐。
     * 码值不重排：4 仍为 4，仅语义由「任务结束」改「抄送知会」，1/2/3 不变。</p>
     */
    CC_CREATE(4, "抄送知会事件");

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
