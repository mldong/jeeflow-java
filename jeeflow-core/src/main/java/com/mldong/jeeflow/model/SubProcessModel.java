package com.mldong.jeeflow.model;

import com.mldong.jeeflow.core.Execution;

/**
 * 子流程节点模型
 *
 * @author mldong
 */
public class SubProcessModel extends NodeModel {

    private String form;
    private Integer version;

    @Override
    void exec(Execution execution) {
        runOutTransition(execution);
    }

    public String getForm() { return form; }
    public void setForm(String form) { this.form = form; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
