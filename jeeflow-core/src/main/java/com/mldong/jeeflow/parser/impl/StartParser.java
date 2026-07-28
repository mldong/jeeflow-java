package com.mldong.jeeflow.parser.impl;

import com.mldong.jeeflow.model.NodeModel;
import com.mldong.jeeflow.model.StartModel;
import com.mldong.jeeflow.model.logicflow.LfNode;
import com.mldong.jeeflow.parser.AbstractNodeParser;

public class StartParser extends AbstractNodeParser {
    @Override
    public void parseNode(LfNode lfNode) {}

    @Override
    public NodeModel newModel() {
        return new StartModel();
    }
}
