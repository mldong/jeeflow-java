package com.mldong.jeeflow.spi;

import java.util.List;

/**
 * 组织维度用户提供者 SPI（可选，issues/16）——按部门取领导 / 按角色取人
 *
 * <p>业务方实现本接口提供组织数据，jeeflow 内置的部门领导/角色类
 * {@code AssignmentHandler} 通过 {@code ServiceContext} 查找本 SPI 完成参与者解析，
 * 无需再手写 handler。</p>
 *
 * <p>未注册本 SPI 时，相关内置 handler 返回 null（参与者为空，任务无处理人）。</p>
 *
 * @author mldong
 */
public interface IOrgUserProvider {

    /** 部门领导（对应业务表部门 leaderIds 字段，如 boot4 DeptApi.findById(deptId).leaderIds） */
    List<String> findDeptLeaders(String deptId);

    /** 部门分管领导（部门 mainLeaderIds 字段） */
    List<String> findDeptMainLeaders(String deptId);

    /** 按角色编码取人（返回用户 ID 列表） */
    List<String> findByRole(String roleCode);
}
