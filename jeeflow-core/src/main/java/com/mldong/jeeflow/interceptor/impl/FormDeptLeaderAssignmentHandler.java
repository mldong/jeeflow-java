package com.mldong.jeeflow.interceptor.impl;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.interceptor.AssignmentHandler;

/** 表单指定 1–N 个部门，取这些部门的负责人。 */
public class FormDeptLeaderAssignmentHandler implements AssignmentHandler {
    @Override
    public String assign(Execution execution) {
        return FormDeptAssignSupport.resolve(execution, false);
    }
}
