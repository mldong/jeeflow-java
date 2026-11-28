package com.mldong.jeeflow.interceptor.impl;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.spi.IPostUserProvider;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 表单指定岗位处理器的共用取数档。 */
public final class FormPostAssignSupport {
    private FormPostAssignSupport() {}

    public static String resolve(Execution execution) {
        String nodeKey = FormUserAssignSupport.nodeKey(execution);
        if (nodeKey == null || nodeKey.isEmpty()) return null;
        List<String> postCodes = postCodes(FormUserAssignSupport.findFieldValue(execution.getArgs(), nodeKey));
        if (postCodes.isEmpty()) return null;
        IPostUserProvider provider = ServiceContext.find(IPostUserProvider.class);
        if (provider == null) return null;
        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String postCode : postCodes) {
            List<String> userIds;
            try {
                userIds = provider.findByPost(postCode);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (userIds == null) continue;
            for (String userId : userIds) {
                if (userId == null) continue;
                String id = userId.trim();
                if (!id.isEmpty() && seen.add(id)) result.add(id);
            }
        }
        return result.isEmpty() ? null : String.join(",", result);
    }

    private static List<String> postCodes(Object value) {
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
            String code = String.valueOf(token).trim();
            if (!code.isEmpty() && seen.add(code)) result.add(code);
        }
        return result;
    }
}
