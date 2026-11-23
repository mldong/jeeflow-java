package com.mldong.jeeflow.interceptor.impl;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.interceptor.AssignmentHandler;

/**
 * 表单指定用户所属部门分管领导（内置，issues/199，八语言契约 §3.3）。
 *
 * <p>与 {@link FormUserDeptLeaderAssignmentHandler} 同一条链路（字段 → user → dept → leader），
 * 只把最后一跳换成 {@code findDeptMainLeaders}；四档并集、只取第一个非空 id、
 * 取不到一律 null 且不抛异常，全部同形。</p>
 *
 * <p>注册名＝本类全限定名（顶级类，不带 {@code $}），八语言逐字一致，order 80。</p>
 *
 * @author mldong
 */
public class FormUserDeptMainLeaderAssignmentHandler implements AssignmentHandler {

    @Override
    public String assign(Execution execution) {
        String nodeKey = FormUserAssignSupport.nodeKey(execution);
        if (nodeKey == null || nodeKey.isEmpty()) return null;
        FlowData args = execution.getArgs();
        String userId = FormUserAssignSupport.firstUserId(
                FormUserAssignSupport.findFieldValue(args, nodeKey));
        if (userId.isEmpty()) return null;
        return FormUserAssignSupport.byDept(FormUserAssignSupport.deptIdOf(userId), true);
    }
}
