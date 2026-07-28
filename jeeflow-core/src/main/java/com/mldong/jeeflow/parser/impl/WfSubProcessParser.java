package com.mldong.jeeflow.parser.impl;

import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.model.NodeModel;
import com.mldong.jeeflow.model.SubProcessModel;
import com.mldong.jeeflow.model.logicflow.LfNode;
import com.mldong.jeeflow.parser.AbstractNodeParser;

public class WfSubProcessParser extends AbstractNodeParser {

    @Override
    public void parseNode(LfNode lfNode) {
        SubProcessModel subProcessModel = (SubProcessModel) nodeModel;
        FlowData properties = lfNode.getProperties();
        if (properties != null) {
            subProcessModel.setForm(properties.getStr(FORM_KEY));
            subProcessModel.setVersion(properties.getInt(VERSION_KEY));
        }
    }

    @Override
    public NodeModel newModel() {
        return new SubProcessModel();
    }
}
