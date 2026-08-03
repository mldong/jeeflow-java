package com.mldong.jeeflow.parser.impl;

import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.enums.CountersignTypeEnum;
import com.mldong.jeeflow.enums.ProcessTaskPerformTypeEnum;
import com.mldong.jeeflow.enums.ProcessTaskTypeEnum;
import com.mldong.jeeflow.model.NodeModel;
import com.mldong.jeeflow.model.TaskModel;
import com.mldong.jeeflow.model.logicflow.LfNode;
import com.mldong.jeeflow.parser.AbstractNodeParser;

import java.lang.reflect.Field;

public class TaskParser extends AbstractNodeParser {

    @Override
    public void parseNode(LfNode lfNode) {
        TaskModel taskModel = (TaskModel) nodeModel;
        FlowData properties = lfNode.getProperties();
        if (properties == null) {
            properties = FlowData.create();
        }

        taskModel.setForm(properties.getStr(FORM_KEY));
        taskModel.setAssignee(properties.getStr(ASSIGNEE_KEY));
        taskModel.setAssignmentHandler(properties.getStr(ASSIGNMENT_HANDLE_KEY));
        taskModel.setTaskType(ProcessTaskTypeEnum.codeOf(properties.get(TASK_TYPE_KEY)));
        taskModel.setPerformType(ProcessTaskPerformTypeEnum.codeOf(properties.get(PERFORM_TYPE_KEY)));
        taskModel.setReminderTime(properties.getStr(REMINDER_TIME_KEY));
        taskModel.setReminderRepeat(properties.getStr(REMINDER_REPEAT_KEY));
        taskModel.setExpireTime(properties.getStr(EXPIRE_TIME_KEY));
        taskModel.setAutoExecute(properties.getStr(AUTH_EXECUTE_KEY));
        taskModel.setCallback(properties.getStr(CALLBACK_KEY));

        // 解析候选人属性
        taskModel.setCandidateHandler(properties.getStr(EXT_FIELD_CANDIDATE_HANDLER_KEY));
        // 候选人（v1.6.0 对齐 Go/Python/Node：顶层 candidateUsers/candidateGroups）
        String candUsers = properties.getStr(EXT_FIELD_CANDIDATE_USERS_KET);
        String candGroups = properties.getStr(EXT_FIELD_CANDIDATE_GROUPS_KEY);
        if (candUsers != null || candGroups != null) {
            FlowData ext = taskModel.getExt();
            if (candUsers != null) ext.set(EXT_FIELD_CANDIDATE_USERS_KET, candUsers);
            if (candGroups != null) ext.set(EXT_FIELD_CANDIDATE_GROUPS_KEY, candGroups);
            taskModel.setExt(ext);
        }

        // 解析会签属性
        taskModel.setCountersignType(CountersignTypeEnum.codeOf(properties.getStr(EXT_FIELD_COUNTERSIGN_TYPE_KEY)));
        taskModel.setCountersignCompletionCondition(properties.getStr(EXT_FIELD_COUNTERSIGN_COMPLETION_CONDITION_KEY));

        // 自定义扩展属性
        Object field = properties.get(EXT_FIELD_KEY);
        if (field instanceof FlowData) {
            FlowData ext = (FlowData) field;
            taskModel.setExt(ext);
            // 候选人属性（优先从 ext 取）
            String candHandler = ext.getStr(EXT_FIELD_CANDIDATE_HANDLER_KEY);
            if (candHandler != null) {
                taskModel.setCandidateHandler(candHandler);
            }
            // 会签属性（优先从 ext 取）
            Object countersignType = ext.get(EXT_FIELD_COUNTERSIGN_TYPE_KEY);
            if (countersignType != null) {
                taskModel.setCountersignType(CountersignTypeEnum.codeOf(countersignType));
            }
            String countersignCond = ext.getStr(EXT_FIELD_COUNTERSIGN_COMPLETION_CONDITION_KEY);
            if (countersignCond != null) {
                taskModel.setCountersignCompletionCondition(countersignCond);
            }
        } else if (field instanceof java.util.Map) {
            // Jackson 反序列化为普通 Map，包装为 FlowData
            @SuppressWarnings("unchecked")
            FlowData ext = FlowData.of((java.util.Map<String, Object>) field);
            taskModel.setExt(ext);
            String candHandler = ext.getStr(EXT_FIELD_CANDIDATE_HANDLER_KEY);
            if (candHandler != null) {
                taskModel.setCandidateHandler(candHandler);
            }
            Object countersignType = ext.get(EXT_FIELD_COUNTERSIGN_TYPE_KEY);
            if (countersignType != null) {
                taskModel.setCountersignType(CountersignTypeEnum.codeOf(countersignType));
            }
            String countersignCond = ext.getStr(EXT_FIELD_COUNTERSIGN_COMPLETION_CONDITION_KEY);
            if (countersignCond != null) {
                taskModel.setCountersignCompletionCondition(countersignCond);
            }
        } else {
            taskModel.setExt(FlowData.create());
        }

        // 将其他 properties 添加到 ext 中（非 TaskModel 字段的）
        for (String key : properties.keySet()) {
            if (!hasField(TaskModel.class, key)) {
                taskModel.getExt().set(key, properties.get(key));
            }
        }
    }

    @Override
    public NodeModel newModel() {
        return new TaskModel();
    }

    private static boolean hasField(Class<?> clazz, String fieldName) {
        for (Field f : clazz.getDeclaredFields()) {
            if (f.getName().equals(fieldName)) return true;
        }
        return false;
    }
}
