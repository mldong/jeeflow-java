package com.mldong.jeeflow.parser;

import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.model.NodeModel;
import com.mldong.jeeflow.model.TransitionModel;
import com.mldong.jeeflow.model.logicflow.LfEdge;
import com.mldong.jeeflow.model.logicflow.LfNode;
import com.mldong.jeeflow.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 通用属性解析抽象类（解析基本属性和边）
 *
 * @author mldong
 */
public abstract class AbstractNodeParser implements NodeParser {

    protected NodeModel nodeModel;

    @Override
    public void parse(LfNode lfNode, List<LfEdge> edges) {
        nodeModel = newModel();
        // 解析基本信息
        nodeModel.setName(lfNode.getId());
        if (lfNode.getText() != null) {
            nodeModel.setDisplayName(lfNode.getText().getStr(TEXT_VALUE_KEY));
        }
        FlowData properties = lfNode.getProperties();
        if (properties == null) {
            properties = FlowData.create();
        }
        // 解析布局属性
        int x = lfNode.getX();
        int y = lfNode.getY();
        int w = toInt(properties.get(WIDTH_KEY), 0);
        int h = toInt(properties.get(HEIGHT_KEY), 0);
        nodeModel.setLayout(x + "," + y + "," + w + "," + h);
        // 解析拦截器
        nodeModel.setPreInterceptors(properties.getStr(PRE_INTERCEPTORS_KEY));
        nodeModel.setPostInterceptors(properties.getStr(POST_INTERCEPTORS_KEY));
        // 解析输出边
        List<LfEdge> nodeEdges = getEdgeBySourceNodeId(lfNode.getId(), edges);
        for (LfEdge edge : nodeEdges) {
            TransitionModel tm = new TransitionModel();
            tm.setName(edge.getId());
            tm.setTo(edge.getTargetNodeId());
            tm.setSource(nodeModel);
            FlowData edgeProps = edge.getProperties();
            if (edgeProps != null) {
                tm.setExpr(edgeProps.getStr(EXPR_KEY));
            }
            if (edge.getPointsList() != null && !edge.getPointsList().isEmpty()) {
                tm.setG(edge.getPointsList().stream()
                        .map(p -> p.getX() + "," + p.getY())
                        .collect(Collectors.joining(";")));
            } else if (edge.getStartPoint() != null && edge.getEndPoint() != null) {
                tm.setG(edge.getStartPoint().getX() + "," + edge.getStartPoint().getY()
                        + ";" + edge.getEndPoint().getX() + "," + edge.getEndPoint().getY());
            }
            nodeModel.getOutputs().add(tm);
        }
        // 调用子类特定解析方法
        parseNode(lfNode);
    }

    /** 子类实现特定解析 */
    public abstract void parseNode(LfNode lfNode);

    /** 子类各自创建节点模型对象 */
    public abstract NodeModel newModel();

    @Override
    public NodeModel getModel() {
        return nodeModel;
    }

    private List<LfEdge> getEdgeBySourceNodeId(String sourceNodeId, List<LfEdge> edges) {
        return edges.stream()
                .filter(e -> e.getSourceNodeId().equals(sourceNodeId))
                .collect(Collectors.toList());
    }

    private static int toInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
