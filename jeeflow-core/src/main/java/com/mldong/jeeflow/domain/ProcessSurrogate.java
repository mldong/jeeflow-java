package com.mldong.jeeflow.domain;

import java.time.LocalDateTime;

/**
 * 流程委托代理（wf_process_surrogate）——授权人把待办委托给代理人
 *
 * <p>v1.1.0 管理扩展。生效规则：enabled=1 且时间窗内（起止为空 = 该侧不限）；processName 为空 = 全部流程；
 * 自委托（surrogate = operator）不生效。引擎侧由内置 {@code SurrogateInterceptor}（issues/116，
 * 默认开启，{@code Configuration#surrogateAutoApply(false)} 可关）在建任务落库前把被委托人
 * 并入任务参与者集合，授权人保留、任一可办。</p>
 *
 * @author mldong
 */
public class ProcessSurrogate {

    private Long id;
    private String processName;
    private String operator;
    private String surrogate;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer enabled;
    private LocalDateTime createTime;
    private String createUser;
    private LocalDateTime updateTime;
    private String updateUser;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getSurrogate() { return surrogate; }
    public void setSurrogate(String surrogate) { this.surrogate = surrogate; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public String getCreateUser() { return createUser; }
    public void setCreateUser(String createUser) { this.createUser = createUser; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public String getUpdateUser() { return updateUser; }
    public void setUpdateUser(String updateUser) { this.updateUser = updateUser; }
}
