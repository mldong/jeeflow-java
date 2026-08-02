package com.mldong.jeeflow.domain;

import java.time.LocalDateTime;

/**
 * 流程设计历史（wf_process_design_his）——每次保存设计的 content 快照
 *
 * <p>v1.1.0 管理扩展。支持设计器"历史版本"回看；设计稿当前内容 = 最新一条快照。</p>
 *
 * @author mldong
 */
public class ProcessDesignHis {

    private Long id;
    private Long processDesignId;
    private byte[] content;
    private LocalDateTime createTime;
    private String createUser;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProcessDesignId() { return processDesignId; }
    public void setProcessDesignId(Long processDesignId) { this.processDesignId = processDesignId; }
    public byte[] getContent() { return content; }
    public void setContent(byte[] content) { this.content = content; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public String getCreateUser() { return createUser; }
    public void setCreateUser(String createUser) { this.createUser = createUser; }
}
