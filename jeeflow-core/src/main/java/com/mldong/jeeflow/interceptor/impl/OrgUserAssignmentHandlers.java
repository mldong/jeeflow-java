package com.mldong.jeeflow.interceptor.impl;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.interceptor.AssignmentHandler;
import com.mldong.jeeflow.model.NodeModel;
import com.mldong.jeeflow.spi.IOrgUserProvider;
import com.mldong.jeeflow.spi.IUserProvider;

import java.util.List;

/**
 * 组织维度参与者处理器（内置，issues/16）——按部门领导/分管领导/角色取人。
 *
 * <p>语义对齐 boot4（DeptLeader/DeptMainLeader/ApplicantDeptLeader/TaskRole 四个 handler），
 * 数据来源改为 {@link IOrgUserProvider} SPI：业务方只实现数据接口，不写 handler。</p>
 *
 * <p>节点 assignmentHandler 配置（全限定名）：</p>
 * <ul>
 *   <li>当前用户部门领导：…impl.DeptLeaderAssignmentHandler</li>
 *   <li>当前用户部门分管领导：…impl.DeptMainLeaderAssignmentHandler</li>
 *   <li>发起人部门领导：…impl.ApplicantDeptLeaderAssignmentHandler</li>
 *   <li>发起人部门分管领导：…impl.ApplicantDeptMainLeaderAssignmentHandler</li>
 *   <li>任务节点唯一编码关联角色：…impl.TaskRoleAssigneeHandler</li>
 * </ul>
 */
public final class OrgUserAssignmentHandlers {

    private OrgUserAssignmentHandlers() {}

    /** 当前用户（任务操作人）部门领导 */
    public static class DeptLeaderAssignmentHandler implements AssignmentHandler {
        @Override
        public String assign(Execution execution) {
            return byDept(deptIdOf(execution.getOperator(), execution), false);
        }
    }

    /** 当前用户（任务操作人）部门分管领导 */
    public static class DeptMainLeaderAssignmentHandler implements AssignmentHandler {
        @Override
        public String assign(Execution execution) {
            return byDept(deptIdOf(execution.getOperator(), execution), true);
        }
    }

    /** 发起人部门领导 */
    public static class ApplicantDeptLeaderAssignmentHandler implements AssignmentHandler {
        @Override
        public String assign(Execution execution) {
            String applicant = execution.getProcessInstance() != null
                    ? execution.getProcessInstance().getOperator() : null;
            return byDept(deptIdOf(applicant, execution), false);
        }
    }

    /** 发起人部门分管领导 */
    public static class ApplicantDeptMainLeaderAssignmentHandler implements AssignmentHandler {
        @Override
        public String assign(Execution execution) {
            String applicant = execution.getProcessInstance() != null
                    ? execution.getProcessInstance().getOperator() : null;
            return byDept(deptIdOf(applicant, execution), true);
        }
    }

    /** 任务节点唯一编码关联角色（roleCode = 节点 name） */
    public static class TaskRoleAssigneeHandler implements AssignmentHandler {
        @Override
        public String assign(Execution execution) {
            // 任务创建时 processTask 尚为 null，节点信息从 nodeModel（即当前 TaskModel）取
            NodeModel nodeModel = execution.getNodeModel();
            String roleCode = nodeModel == null ? null : nodeModel.getName();
            if (roleCode == null) return null;
            IOrgUserProvider org = ServiceContext.find(IOrgUserProvider.class);
            if (org == null) return null;
            List<String> ids = org.findByRole(roleCode);
            return ids == null || ids.isEmpty() ? null : String.join(",", ids);
        }
    }

    /** 用户 deptId：operator → IUserProvider.getUser */
    private static String deptIdOf(String userId, Execution execution) {
        if (userId == null || userId.isEmpty()) return null;
        IUserProvider userProvider = ServiceContext.find(IUserProvider.class);
        if (userProvider == null) return null;
        IUserProvider.UserInfo u = userProvider.getUser(userId);
        return u != null ? u.getDeptId() : null;
    }

    private static String byDept(String deptId, boolean main) {
        if (deptId == null || deptId.isEmpty()) return null;
        IOrgUserProvider org = ServiceContext.find(IOrgUserProvider.class);
        if (org == null) return null;
        List<String> ids = main ? org.findDeptMainLeaders(deptId) : org.findDeptLeaders(deptId);
        return ids == null || ids.isEmpty() ? null : String.join(",", ids);
    }
}
