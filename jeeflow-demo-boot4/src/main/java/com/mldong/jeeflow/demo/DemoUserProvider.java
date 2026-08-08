package com.mldong.jeeflow.demo;

import com.mldong.jeeflow.spi.IUserProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 演示用用户提供者——画像来自 {@link DemoUsers}（四端统一 8 个具名用户）
 */
@Component
public class DemoUserProvider implements IUserProvider {

    @Override public UserInfo getUser(String userId) {
        Map<String, Object> m = DemoUsers.toMap(userId);
        UserInfo u = UserInfo.of(userId);
        u.setRealName((String) m.get("realName"));
        u.setDeptId((String) m.get("deptId"));
        u.setDeptName((String) m.get("deptName"));
        u.setPostId((String) m.get("postId"));
        u.setPostName((String) m.get("postName"));
        return u;
    }
}
