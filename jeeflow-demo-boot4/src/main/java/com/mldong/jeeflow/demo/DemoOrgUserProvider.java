package com.mldong.jeeflow.demo;

import com.mldong.jeeflow.spi.IOrgUserProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 演示组织用户提供者——部门领导/分管领导/角色取人（声明 Bean 即被 autoconfigure 收集启用）
 *
 * <p>组织为扁平演示结构：D01 研发部，领导=李四（组长），分管领导=王五（经理）。</p>
 */
@Component
public class DemoOrgUserProvider implements IOrgUserProvider {

    @Override
    public List<String> findDeptLeaders(String deptId) {
        return List.of("leader");
    }

    @Override
    public List<String> findDeptMainLeaders(String deptId) {
        return List.of("manager");
    }

    @Override
    public List<String> findByRole(String roleCode) {
        return switch (roleCode == null ? "" : roleCode) {
            case "leader" -> List.of("leader");
            case "manager" -> List.of("manager");
            case "director" -> List.of("director");
            case "boss" -> List.of("boss");
            default -> List.of();
        };
    }
}
