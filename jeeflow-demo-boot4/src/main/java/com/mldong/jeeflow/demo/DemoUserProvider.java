package com.mldong.jeeflow.demo;

import com.mldong.jeeflow.spi.IUserProvider;
import org.springframework.stereotype.Component;

/**
 * 演示用用户提供者——硬编码几个虚拟用户
 */
@Component
public class DemoUserProvider implements IUserProvider {

    @Override public String getRealName(String userId) {
        switch (userId) {
            case "user1": return "张三";
            case "leader": return "李四(组长)";
            case "manager": return "王五(经理)";
            case "director": return "赵六(总监)";
            case "boss": return "钱七(总经理)";
            default: return "用户" + userId;
        }
    }

    @Override public String getDeptId(String userId) { return "D01"; }
    @Override public String getDeptName(String userId) { return "研发部"; }
    @Override public String getPostId(String userId) { return "P01"; }
    @Override public String getPostName(String userId) { return "工程师"; }
}
