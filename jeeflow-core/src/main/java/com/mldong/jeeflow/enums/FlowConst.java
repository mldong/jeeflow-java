package com.mldong.jeeflow.enums;

/**
 * 流程常量定义
 *
 * @author mldong
 */
public final class FlowConst {

    private FlowConst() {
    }

    /** 业务流程号 */
    public static final String BUSINESS_NO = "BUSINESS_NO";
    /** 超级管理员 ID */
    public static final String ADMIN_ID = "flow.admin";
    /** 自动执行 ID */
    public static final String AUTO_ID = "flow.auto";

    public static final String PROCESS_NAME_KEY = "name";
    public static final String PROCESS_DISPLAY_NAME_KEY = "displayName";
    public static final String PROCESS_TYPE = "type";

    /** 流程定义 ID */
    public static final String PROCESS_DEFINE_ID_KEY = "processDefineId";
    /** 流程设计 ID */
    public static final String PROCESS_DESIGN_ID_KEY = "processDesignId";
    /** 流程任务 ID */
    public static final String PROCESS_TASK_ID_KEY = "processTaskId";
    /** 流程实例 ID */
    public static final String PROCESS_INSTANCE_ID_KEY = "processInstanceId";

    /** 表单数据前缀 */
    public static final String FORM_DATA_PREFIX = "f_";
    /** 任务表单数据前缀 */
    public static final String TASK_FORM_DATA_PREFIX = "tf_";

    /** 审批意见 */
    public static final String APPROVAL_COMMENT = "tf_approvalComment";
    /** 审批附件 */
    public static final String APPROVAL_ATTACHMENT = "tf_approvalAttachment";
    /** 下一节点执行人 */
    public static final String NEXT_NODE_OPERATOR = "tf_nextNodeOperator";
    /** 流程启动时下一步节点执行人 */
    public static final String PROCESS_START_NEXT_NODE_OPERATOR = "f_nextNodeOperator";
    /** 抄送人 */
    public static final String CC_ACTORS = "tf_ccActors";
    /** 启动参数中的抄送人 */
    public static final String CC_ACTORS_START = "f_ccActors";

    /** 用户 ID */
    public static final String USER_USER_ID = "u_userId";
    /** 用户姓名 */
    public static final String USER_REAL_NAME = "u_realName";
    /** 用户部门 ID */
    public static final String USER_DEPT_ID = "u_deptId";
    /** 用户部门名称 */
    public static final String USER_DEPT_NAME = "u_deptName";
    /** 用户岗位 ID */
    public static final String USER_POST_ID = "u_postId";
    /** 用户岗位名称 */
    public static final String USER_POST_NAME = "u_postName";

    /** 提交类型 */
    public static final String SUBMIT_TYPE = "submitType";
    /** 自动生成标题 */
    public static final String AUTO_GEN_TITLE = "autoGenTitle";
    /** 节点名称 */
    public static final String TASK_NAME = "taskName";
    /** 是否第一个任务节点 */
    public static final String IS_FIRST_TASK_NODE = "isFirstTaskNode";

    /** 会签变量前缀 */
    public static final String COUNTERSIGN_VARIABLE_PREFIX = "csv_";
    /** 活跃会签实例数 */
    public static final String NR_OF_ACTIVATE_INSTANCES = "nrOfActivateInstances";
    /** 循环计数器 */
    public static final String LOOP_COUNTER = "loopCounter";
    /** 会签总实例数 */
    public static final String NR_OF_INSTANCES = "nrOfInstances";
    /** 已完成会签实例数 */
    public static final String NR_OF_COMPLETED_INSTANCES = "nrOfCompletedInstances";
    /** 会签操作人列表 */
    public static final String COUNTERSIGN_OPERATOR_LIST = "operatorList";
    /** 会签类型 */
    public static final String COUNTERSIGN_TYPE = "countersignType";
    /** 会签不同意标识 */
    public static final String COUNTERSIGN_DISAGREE_FLAG = "countersignDisagreeFlag";

    /** 参与者 ID 列表 key */
    public static final String ACTOR_IDS_KEY = "actorIds";
    /** 自定义节点默认返回值 */
    public static final String CUSTOM_RETURN_VAL = "custom_return_val";
}
