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

    /** 委托代理（建任务时自动应用生效中的委托） */
    private final Surrogate surrogate = new Surrogate();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getWorkerId() { return workerId; }
    public void setWorkerId(long workerId) { this.workerId = workerId; }
    public Surrogate getSurrogate() { return surrogate; }

    /** {@code jeeflow.surrogate.*} */
    public static class Surrogate {

        /**
         * 引擎内置「委托代理自动生效」开关（默认开启）：建任务时把生效中的被委托人并入任务参与者。
         * 关掉即回到「processSurrogate/* 只是台账」的行为——{@code jeeflow.surrogate.auto-apply=false}。
         */
        private boolean autoApply = true;

        public boolean isAutoApply() { return autoApply; }
        public void setAutoApply(boolean autoApply) { this.autoApply = autoApply; }
    }
}
