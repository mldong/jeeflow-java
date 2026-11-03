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
        // 退回发起人后的重提方式：缺省 NORMAL，非法值报配置错误
        taskModel.setReapplyMode(com.mldong.jeeflow.enums.ReapplyModeEnum.parse(
                properties.get(com.mldong.jeeflow.core.ReapplySupport.PROP_REAPPLY_MODE), taskModel.getName()));
        // 后加签两开关（规范 02 §4.3）：缺省关／0＝不限，非法值解析期报配置错误，不得静默当缺省。
        // 声明成 TaskModel 字段后，下方"非模型字段的键镜像进 ext"那条腿不再收它们（双源就此断掉）。
        taskModel.setAllowAddSign(com.mldong.jeeflow.core.AddSignSupport.parseAllow(
                properties.get(com.mldong.jeeflow.core.AddSignSupport.PROP_ALLOW_ADD_SIGN),
                taskModel.getName(), com.mldong.jeeflow.core.AddSignSupport.PROP_ALLOW_ADD_SIGN));
        // 已办撤回开关（规范 02 §4.4，issues/246）：同尺解析，非法值解析期报配置错误
        taskModel.setAllowRecall(com.mldong.jeeflow.core.AddSignSupport.parseAllow(
                properties.get(com.mldong.jeeflow.core.RecallSupport.PROP_ALLOW_RECALL),
                taskModel.getName(), com.mldong.jeeflow.core.RecallSupport.PROP_ALLOW_RECALL));
        // 动态会签（征询模式）开关（规范 02 §4.4，issues/247）：同尺解析
        taskModel.setDynamicCountersign(com.mldong.jeeflow.core.AddSignSupport.parseAllow(
                properties.get(com.mldong.jeeflow.core.CollectSupport.PROP_DYNAMIC_COUNTERSIGN),
                taskModel.getName(), com.mldong.jeeflow.core.CollectSupport.PROP_DYNAMIC_COUNTERSIGN));
        taskModel.setAddSignMaxRounds(com.mldong.jeeflow.core.AddSignSupport.parseMaxRounds(
                properties.get(com.mldong.jeeflow.core.AddSignSupport.PROP_ADD_SIGN_MAX_ROUNDS), taskModel.getName()));

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
        // issues/231：判据含父类链（对齐 boot2 ReflectUtil.hasField 语义）——
        // name/displayName/layout/pre/postInterceptors 等父类声明键不得再镜像进 ext
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equals(fieldName)) return true;
            }
        }
        return false;
    }
}
