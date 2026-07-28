package com.mldong.jeeflow.model.logicflow;

import com.mldong.jeeflow.domain.FlowData;

import java.io.Serializable;

/**
 * LogicFlow 节点
 *
 * @author mldong
 */
public class LfNode implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String type;
    private int x;
    private int y;
    private FlowData properties;
    private FlowData text;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public int getX() { return x; }
    public void setX(int x) { this.x = x; }
    public int getY() { return y; }
    public void setY(int y) { this.y = y; }
    public FlowData getProperties() { return properties; }
    public void setProperties(FlowData properties) { this.properties = properties; }
    public FlowData getText() { return text; }
    public void setText(FlowData text) { this.text = text; }
}
