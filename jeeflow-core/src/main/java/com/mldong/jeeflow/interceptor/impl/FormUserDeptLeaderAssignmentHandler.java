package com.mldong.jeeflow.interceptor.impl;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.interceptor.AssignmentHandler;

/**
 * 表单指定用户所属部门经理（内置，issues/199，八语言契约 §3.3）。
 *
 * <p>HR 代员工提交／代理申请场景：表单字段里给出被代理人的用户 id，取<b>该员工</b>部门的经理，
 * 与当前操作人无关。字段查找走 {@link FormUserAssignSupport#findFieldValue} 四档并集，
 * 取人链路＝字段 → user → dept → leader。</p>
 *
 * <p>取不到（字段缺失／值为空／用户不存在／无部门／部门无领导／SPI 未注册）⇒ 返回 null，
 * 参与者为空且<b>不抛异常</b>——与现存部门类 handler 的吞错形状同族（指派解析不许炸掉流程）。</p>
 *
 * <p>注册名＝本类全限定名（顶级类，不带 {@code $}），八语言逐字一致，order 70。</p>
 *
 * @author mldong
 */
public class FormUserDeptLeaderAssignmentHandler implements AssignmentHandler {

    @Override
    public String assign(Execution execution) {
        String nodeKey = FormUserAssignSupport.nodeKey(execution);
        if (nodeKey == null || nodeKey.isEmpty()) return null;
        FlowData args = execution.getArgs();
        String userId = FormUserAssignSupport.firstUserId(
                FormUserAssignSupport.findFieldValue(args, nodeKey));
        if (userId.isEmpty()) return null;
        return FormUserAssignSupport.byDept(FormUserAssignSupport.deptIdOf(userId), false);
    }
}
