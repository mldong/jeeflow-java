package com.mldong.jeeflow.model.logicflow;

import com.mldong.jeeflow.domain.FlowData;

import java.io.Serializable;
import java.util.List;

/**
 * LogicFlow 边
 *
 * @author mldong
 */
public class LfEdge implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String type;
    private String sourceNodeId;
    private String targetNodeId;
    private FlowData properties;
    private FlowData text;
    private LfPoint startPoint;
    private LfPoint endPoint;
    private List<LfPoint> pointsList;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSourceNodeId() { return sourceNodeId; }
    public void setSourceNodeId(String sourceNodeId) { this.sourceNodeId = sourceNodeId; }
    public String getTargetNodeId() { return targetNodeId; }
    public void setTargetNodeId(String targetNodeId) { this.targetNodeId = targetNodeId; }
    public FlowData getProperties() { return properties; }
    public void setProperties(FlowData properties) { this.properties = properties; }
    public FlowData getText() { return text; }
    public void setText(FlowData text) { this.text = text; }
    public LfPoint getStartPoint() { return startPoint; }
    public void setStartPoint(LfPoint startPoint) { this.startPoint = startPoint; }
    public LfPoint getEndPoint() { return endPoint; }
    public void setEndPoint(LfPoint endPoint) { this.endPoint = endPoint; }
    public List<LfPoint> getPointsList() { return pointsList; }
    public void setPointsList(List<LfPoint> pointsList) { this.pointsList = pointsList; }
}
