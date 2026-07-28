package com.mldong.jeeflow.model.logicflow;

import com.mldong.jeeflow.model.BaseModel;

import java.util.List;

/**
 * LogicFlow 模型——前端流程设计器的 JSON 数据模型
 *
 * @author mldong
 */
public class LfModel extends BaseModel {

    private String type;
    private String expireTime;
    private String instanceUrl;
    private String instanceNoClass;
    private String preInterceptors;
    private String postInterceptors;
    private String relTableName;
    private List<LfNode> nodes;
    private List<LfEdge> edges;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getExpireTime() { return expireTime; }
    public void setExpireTime(String expireTime) { this.expireTime = expireTime; }
    public String getInstanceUrl() { return instanceUrl; }
    public void setInstanceUrl(String instanceUrl) { this.instanceUrl = instanceUrl; }
    public String getInstanceNoClass() { return instanceNoClass; }
    public void setInstanceNoClass(String instanceNoClass) { this.instanceNoClass = instanceNoClass; }
    public String getPreInterceptors() { return preInterceptors; }
    public void setPreInterceptors(String preInterceptors) { this.preInterceptors = preInterceptors; }
    public String getPostInterceptors() { return postInterceptors; }
    public void setPostInterceptors(String postInterceptors) { this.postInterceptors = postInterceptors; }
    public String getRelTableName() { return relTableName; }
    public void setRelTableName(String relTableName) { this.relTableName = relTableName; }
    public List<LfNode> getNodes() { return nodes; }
    public void setNodes(List<LfNode> nodes) { this.nodes = nodes; }
    public List<LfEdge> getEdges() { return edges; }
    public void setEdges(List<LfEdge> edges) { this.edges = edges; }
}
