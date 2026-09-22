package com.mldong.jeeflow.domain;

import java.time.LocalDateTime;

/**
 * 流程委托代理（wf_process_surrogate）——授权人把待办委托给代理人
 *
 * <p>v1.1.0 管理扩展。生效规则见 {@link #isEffective(String, LocalDateTime)}：enabled 严格等于 1、
 * 时间窗覆盖判定时刻（起止为空 = 该侧不限）、自委托（surrogate = operator）不新增；
 * processName 为空 = 全部流程。多条并存时由仓储按主键 id **取最新一条再交本方法裁决**
 * （规范 06 §4.5 条款 1.4；不得"先滤生效再取最新"，见 issues/123）。
 * 引擎侧由内置 {@code SurrogateInterceptor}（issues/116，
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

    /**
     * 四判据（规范 06 §4.5 条款 3/4 ＋ issues/123 §1）：本条委托此刻对该授权人是否生效。
     *
     * <p>调用方必须先按 id 选出「最新一条」再问本方法——本方法只裁决单条，不做多条择优。
     * SQL 仓与内存仓必须走同一份判据（08-compliance 用例 27 要求双仓同答案）。</p>
     *
     * @param operator 授权人（判自委托：被委托人等于授权人 ⇒ 不新增、不重复）
     * @param time     判定时刻；传 null 表示不做窗口比较（引擎建单路径恒有值）
     */
    public boolean isEffective(String operator, LocalDateTime time) {
        if (!Integer.valueOf(1).equals(enabled)) return false;          // 只认 1；0 / 2 / null 一律不生效
        if (surrogate == null || surrogate.trim().isEmpty()) return false;
        if (operator != null && surrogate.trim().equals(operator)) return false;
        if (time == null) return true;
        if (startTime != null && startTime.isAfter(time)) return false;
        return endTime == null || !endTime.isBefore(time);
    }
}
