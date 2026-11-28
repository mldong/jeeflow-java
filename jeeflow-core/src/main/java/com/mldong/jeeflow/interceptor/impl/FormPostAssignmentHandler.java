package com.mldong.jeeflow.interceptor.impl;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.interceptor.AssignmentHandler;

/** 表单指定 1–N 个岗位，按岗位成员分配参与者。 */
public class FormPostAssignmentHandler implements AssignmentHandler {
    @Override
    public String assign(Execution execution) {
        return FormPostAssignSupport.resolve(execution);
    }
}
