package com.mldong.jeeflow.model;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.handler.IHandler;

/**
 * 模型基类
 *
 * @author mldong
 */
public class BaseModel {

    private String name;
    private String displayName;

    /** 将执行对象交给具体的处理器处理 */
    protected void fire(IHandler handler, Execution execution) {
        handler.handle(execution);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}
