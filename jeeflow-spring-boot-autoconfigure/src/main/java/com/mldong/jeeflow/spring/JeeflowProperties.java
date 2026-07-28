package com.mldong.jeeflow.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * jeeflow 配置属性
 *
 * @author mldong
 */
@ConfigurationProperties(prefix = "jeeflow")
public class JeeflowProperties {

    /** 是否启用 jeeflow 自动装配 */
    private boolean enabled = true;

    /** 雪花算法 workerId（0-31） */
    private long workerId = 1;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getWorkerId() { return workerId; }
    public void setWorkerId(long workerId) { this.workerId = workerId; }
}
