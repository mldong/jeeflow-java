package com.mldong.jeeflow.interceptor.impl;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.model.NodeModel;
import com.mldong.jeeflow.spi.IOrgUserProvider;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 表单指定部门（分管）领导处理器的共用取数档。 */
public final class FormDeptAssignSupport {
    private static final Pattern NUMBER_SUFFIX_PATTERN = Pattern.compile("^(.+?)_(\\d+)$");

    private FormDeptAssignSupport() {}

    public static String resolve(Execution execution, boolean main) {
        String nodeKey = nodeKey(execution);
        if (nodeKey == null || nodeKey.isEmpty()) return null;
        FlowData args = execution.getArgs();
        List<String> deptIds = deptIds(findFieldValue(args, nodeKey));
        if (deptIds.isEmpty()) return null;
        IOrgUserProvider org = ServiceContext.find(IOrgUserProvider.class);
        if (org == null) return null;
        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String deptId : deptIds) {
            List<String> leaders;
            try {
                leaders = main ? org.findDeptMainLeaders(deptId) : org.findDeptLeaders(deptId);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (leaders == null) continue;
            for (String leader : leaders) {
                if (leader == null) continue;
                String id = leader.trim();
                if (!id.isEmpty() && seen.add(id)) result.add(id);
            }
        }
        return result.isEmpty() ? null : String.join(",", result);
    }

    private static String nodeKey(Execution execution) {
        NodeModel nodeModel = execution.getNodeModel();
        return nodeModel == null ? null : nodeModel.getName();
    }

    private static Object findFieldValue(FlowData args, String nodeKey) {
        if (args == null || nodeKey == null || nodeKey.isEmpty()) return null;
        if (args.containsKey("f_" + nodeKey)) return args.get("f_" + nodeKey);
        if (args.containsKey(nodeKey)) return args.get(nodeKey);
        int hash = nodeKey.lastIndexOf('#');
        if (hash >= 0) {
            String field = nodeKey.substring(hash + 1);
            if (!field.isEmpty()) {
                if (args.containsKey(field)) return args.get(field);
                if (args.containsKey("f_" + field)) return args.get("f_" + field);
            }
        }
        Matcher matcher = NUMBER_SUFFIX_PATTERN.matcher(nodeKey);
        if (matcher.matches()) {
            String base = matcher.group(1);
            if (args.containsKey("f_" + base)) return args.get("f_" + base);
            if (args.containsKey(base)) return args.get(base);
        }
        return null;
    }

    private static List<String> deptIds(Object value) {
        List<String> result = new ArrayList<>();
        if (value == null) return result;
        List<Object> tokens = new ArrayList<>();
        if (value instanceof Collection) {
            tokens.addAll((Collection<?>) value);
        } else if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) tokens.add(Array.get(value, i));
        } else {
            for (String part : String.valueOf(value).split(",")) tokens.add(part);
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Object token : tokens) {
            if (token == null) continue;
            String id = String.valueOf(token).trim();
            if (!id.isEmpty() && seen.add(id)) result.add(id);
        }
        return result;
    }
}
