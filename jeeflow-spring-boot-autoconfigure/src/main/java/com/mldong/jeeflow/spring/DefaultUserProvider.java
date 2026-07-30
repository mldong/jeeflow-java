package com.mldong.jeeflow.spring;

import com.mldong.jeeflow.spi.IUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认用户提供者（返回 userId 作为 realName，需业务方覆盖）
 *
 * @author mldong
 */
public class DefaultUserProvider implements IUserProvider {

    private static final Logger log = LoggerFactory.getLogger(DefaultUserProvider.class);

    @Override
    public UserInfo getUser(String userId) {
        log.warn("IUserProvider 未实现，返回默认值。请注册自定义实现。");
        return UserInfo.of(userId);
    }
}
