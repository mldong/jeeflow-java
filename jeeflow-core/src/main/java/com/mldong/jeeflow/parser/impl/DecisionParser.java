package com.mldong.jeeflow.parser.impl;

import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.model.DecisionModel;
import com.mldong.jeeflow.model.NodeModel;
import com.mldong.jeeflow.model.logicflow.LfNode;
import com.mldong.jeeflow.parser.AbstractNodeParser;

public class DecisionParser extends AbstractNodeParser {

    @Override
    public void parseNode(LfNode lfNode) {
        DecisionModel decisionModel = (DecisionModel) nodeModel;
        FlowData properties = lfNode.getProperties();
        if (properties != null) {
            decisionModel.setExpr(properties.getStr(EXPR_KEY));
            decisionModel.setHandleClass(properties.getStr(HANDLE_CLASS_KEY));
        }
    }

    @Override
    public NodeModel newModel() {
        return new DecisionModel();
    }
}
