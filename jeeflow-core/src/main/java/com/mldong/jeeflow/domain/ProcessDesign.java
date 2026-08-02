package com.mldong.jeeflow.domain;

import java.time.LocalDateTime;

/**
 * 流程设计（wf_process_design）——设计器保存的设计稿
 *
 * <p>v1.1.0 管理扩展。设计稿内容（LogicFlow JSON）存于 {@link ProcessDesignHis} 快照，
 * 本表只存元信息；发布（deploy）后生成 wf_process_define 记录。</p>
 *
 * @author mldong
 */
public class ProcessDesign {

    private Long id;
    private String name;
    private String displayName;
    private String type;
    private String icon;
    private Integer isDeployed;
    private String remark;
    private LocalDateTime createTime;
    private String createUser;
    private LocalDateTime updateTime;
    private String updateUser;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public Integer getIsDeployed() { return isDeployed; }
    public void setIsDeployed(Integer isDeployed) { this.isDeployed = isDeployed; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public String getCreateUser() { return createUser; }
    public void setCreateUser(String createUser) { this.createUser = createUser; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public String getUpdateUser() { return updateUser; }
    public void setUpdateUser(String updateUser) { this.updateUser = updateUser; }
}
