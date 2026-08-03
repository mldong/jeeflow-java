package com.mldong.jeeflow.test;

import com.mldong.jeeflow.spi.IOrgUserProvider;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内存组织用户提供者（仅供测试）——部门领导 / 部门分管领导 / 角色成员
 *
 * @author mldong
 */
public class MemoryOrgUserProvider implements IOrgUserProvider {

    private final Map<String, List<String>> deptLeaders = new HashMap<>();
    private final Map<String, List<String>> deptMainLeaders = new HashMap<>();
    private final Map<String, List<String>> roleUsers = new HashMap<>();

    public MemoryOrgUserProvider() {
        deptLeaders.put("D01", Arrays.asList("leader1", "leader2"));
        deptMainLeaders.put("D01", Arrays.asList("boss1"));
        roleUsers.put("task4", Arrays.asList("roleA", "roleB"));
    }

    @Override
    public List<String> findDeptLeaders(String deptId) {
        return deptLeaders.get(deptId);
    }

    @Override
    public List<String> findDeptMainLeaders(String deptId) {
        return deptMainLeaders.get(deptId);
    }

    @Override
    public List<String> findByRole(String roleCode) {
        return roleUsers.get(roleCode);
    }
}
