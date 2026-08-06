package com.mldong.jeeflow.interceptor.impl;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.interceptor.AssignmentHandler;
import com.mldong.jeeflow.model.NodeModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按表单字段值分配参与者（内置，issues/16）——纯引擎语义，无外部依赖。
 *
 * <p>匹配规则（对齐 boot4 FormFieldAssigneeHandler）：</p>
 * <ol>
 *   <li>精确匹配：节点名称与流程变量字段名完全一致（如 task1 → task1 字段）</li>
 *   <li>编号匹配：节点名称以 {@code _数字} 结尾时，去除后缀后匹配（如 task_01 → task 字段）</li>
 * </ol>
 * 字段值支持逗号分隔字符串 / Collection，取并集作为参与者。
 */
public class FormFieldAssigneeHandler implements AssignmentHandler {

    private static final Pattern NUMBER_SUFFIX_PATTERN = Pattern.compile("^(.+?)_(\\d+)$");

    @Override
    public String assign(Execution execution) {
        List<String> ids = new ArrayList<>();
        // 任务创建时 processTask 尚为 null，节点信息从 nodeModel（即当前 TaskModel）取
        NodeModel nodeModel = execution.getNodeModel();
        String currentTaskName = nodeModel == null ? null : nodeModel.getName();
        if (currentTaskName == null) return null;
        FlowData args = execution.getArgs();
        if (args == null || args.isEmpty()) return null;
        Object fieldValue = findFieldValue(args, currentTaskName);
        if (fieldValue == null) return null;
        collect(fieldValue, ids);
        return ids.isEmpty() ? null : String.join(",", ids);
    }

    private Object findFieldValue(FlowData args, String taskName) {
        // issues/48 E20：表单字段变量为 f_ 前缀（f_approver），先匹配前缀再回落裸名（兼容存量）
        if (args.containsKey("f_" + taskName)) return args.get("f_" + taskName);
        if (args.containsKey(taskName)) return args.get(taskName);
        Matcher matcher = NUMBER_SUFFIX_PATTERN.matcher(taskName);
        if (matcher.matches() && args.containsKey(matcher.group(1))) {
            return args.get(matcher.group(1));
        }
        return null;
    }

    private void collect(Object fieldValue, List<String> ids) {
        if (fieldValue instanceof Collection) {
            for (Object item : (Collection<?>) fieldValue) {
                add(ids, item);
            }
        } else {
            add(ids, fieldValue);
        }
    }

    private void add(List<String> ids, Object v) {
        if (v == null) return;
        for (String token : String.valueOf(v).split(",")) {
            String t = token.trim();
            if (!t.isEmpty() && !ids.contains(t)) ids.add(t);
        }
    }
}
