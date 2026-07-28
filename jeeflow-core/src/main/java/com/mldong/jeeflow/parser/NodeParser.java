package com.mldong.jeeflow.parser;

import com.mldong.jeeflow.model.NodeModel;
import com.mldong.jeeflow.model.logicflow.LfEdge;
import com.mldong.jeeflow.model.logicflow.LfNode;

import java.util.List;

/**
 * 节点解析接口
 *
 * @author mldong
 */
public interface NodeParser {

    String NODE_NAME_PREFIX = "snaker:";
    String TEXT_VALUE_KEY = "value";
    String WIDTH_KEY = "width";
    String HEIGHT_KEY = "height";
    String PRE_INTERCEPTORS_KEY = "preInterceptors";
    String POST_INTERCEPTORS_KEY = "postInterceptors";
    String EXPR_KEY = "expr";
    String HANDLE_CLASS_KEY = "handleClass";
    String FORM_KEY = "form";
    String ASSIGNEE_KEY = "assignee";
    String ASSIGNMENT_HANDLE_KEY = "assignmentHandler";
    String TASK_TYPE_KEY = "taskType";
    String PERFORM_TYPE_KEY = "performType";
    String REMINDER_TIME_KEY = "reminderTime";
    String REMINDER_REPEAT_KEY = "reminderRepeat";
    String EXPIRE_TIME_KEY = "expireTime";
    String AUTH_EXECUTE_KEY = "autoExecute";
    String CALLBACK_KEY = "callback";
    String EXT_FIELD_KEY = "field";
    String EXT_FIELD_CANDIDATE_USERS_KET = "candidateUsers";
    String EXT_FIELD_CANDIDATE_GROUPS_KEY = "candidateGroups";
    String EXT_FIELD_CANDIDATE_HANDLER_KEY = "candidateHandler";
    String EXT_FIELD_COUNTERSIGN_TYPE_KEY = "countersignType";
    String EXT_FIELD_COUNTERSIGN_COMPLETION_CONDITION_KEY = "countersignCompletionCondition";
    String CLASS_KEY = "clazz";
    String METHOD_NAME_KEY = "methodName";
    String ARGS_KEY = "args";
    String RETURN_VAL_KEY = "val";
    String VERSION_KEY = "version";

    void parse(LfNode lfNode, List<LfEdge> edges);

    NodeModel getModel();
}
