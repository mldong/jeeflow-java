package com.mldong.jeeflow.demo;

import com.mldong.jeeflow.spi.IUserProvider;
import org.springframework.stereotype.Component;

/**
 * 演示用用户提供者——硬编码几个虚拟用户
 */
@Component
public class DemoUserProvider implements IUserProvider {

    @Override public UserInfo getUser(String userId) {
        UserInfo u = UserInfo.of(userId);
        switch (userId) {
            case "user1": u.setRealName("张三"); break;
            case "leader": u.setRealName("李四(组长)"); break;
            case "manager": u.setRealName("王五(经理)"); break;
            case "director": u.setRealName("赵六(总监)"); break;
            case "boss": u.setRealName("钱七(总经理)"); break;
            default: u.setRealName("用户" + userId);
        }
        u.setDeptId("D01");
        u.setDeptName("研发部");
        u.setPostId("P01");
        u.setPostName("工程师");
        return u;
    }
}
