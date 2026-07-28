package com.mldong.jeeflow.enums;

/**
 * 工作流错误码枚举
 *
 * @author mldong
 */
public enum WfErrEnum {
    /** decision 节点无法确定下一步执行路线 */
    NOT_FOUND_NEXT_NODE(20010001, "decision节点无法确定下一步执行路线"),
    /** 没有流程定义 */
    NOT_FOUND_PROCESS_DEFINE(20010002, "没有流程定义"),
    /** 没有进行中的流程任务 */
    NOT_FOUND_DOING_PROCESS_TASK(20010003, "没有进行中的流程任务"),
    /** 当前参与者不能执行该流程任务 */
    NOT_ALLOWED_EXECUTE(20010004, "当前参与者不能执行该流程任务"),
    /** 存在正在未完成的流程实例，不允许删除 */
    EXIST_UN_FINISH_INSTANCE(20010005, "存在正在未完成的流程实例，不允许删除！"),
    /** SPI 未注册 */
    SPI_NOT_REGISTERED(20010006, "必需的 SPI 未注册，请调用 ServiceContext.put() 注册实现");

    private final Integer code;
    private final String message;

    WfErrEnum(Integer code, String message) {
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
