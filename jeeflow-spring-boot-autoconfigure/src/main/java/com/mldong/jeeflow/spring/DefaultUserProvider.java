package com.mldong.jeeflow.spring;

import com.mldong.jeeflow.spi.IUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认用户提供者（返回空值，需业务方覆盖）
 *
 * @author mldong
 */
public class DefaultUserProvider implements IUserProvider {

    private static final Logger log = LoggerFactory.getLogger(DefaultUserProvider.class);

    @Override
    public String getRealName(String userId) {
        log.warn("IUserProvider 未实现，返回默认值。请注册自定义实现。");
        return userId;
    }

    @Override
    public String getDeptId(String userId) {
        return null;
    }

    @Override
    public String getDeptName(String userId) {
        return null;
    }

    @Override
    public String getPostId(String userId) {
        return null;
    }

    @Override
    public String getPostName(String userId) {
        return null;
    }
}
