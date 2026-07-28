package com.mldong.jeeflow.enums;

/**
 * 流程提交类型（操作类型）
 *
 * @author mldong
 */
public enum ProcessSubmitTypeEnum {
    /** 发起申请 */
    APPLY(0, "发起申请"),
    /** 同意申请 */
    AGREE(1, "同意申请"),
    /** 拒绝申请 */
    REJECT(2, "拒绝申请"),
    /** 退回上一步 */
    ROLLBACK(3, "退回上一步"),
    /** 跳转 */
    JUMP(4, "跳转"),
    /** 重新提交 */
    RE_APPLY(5, "重新提交"),
    /** 退回发起人 */
    ROLLBACK_TO_OPERATOR(6, "退回发起人"),
    /** 拒绝申请（会签） */
    COUNTERSIGN_DISAGREE(20, "拒绝申请");

    private final Integer code;
    private final String message;

    ProcessSubmitTypeEnum(Integer code, String message) {
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
