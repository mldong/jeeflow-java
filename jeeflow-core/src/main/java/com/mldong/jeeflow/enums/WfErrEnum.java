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
    SPI_NOT_REGISTERED(20010006, "必需的 SPI 未注册，请调用 ServiceContext.put() 注册实现"),
    /** 退回上一步：当前行没有血缘（task_parent_id 为空或 0） */
    ROLLBACK_PARENT_TASK_ID_EMPTY(20010007, "上一步任务ID为空，无法驳回至上一步处理"),
    /** 退回上一步：血缘守卫不通过（上一步是 fork/join/subprocess/会签） */
    ROLLBACK_PARENT_NOT_REJECTABLE(20010008, "无法驳回至上一步处理，请确认上一步骤并非fork、join、suprocess以及会签任务");

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
