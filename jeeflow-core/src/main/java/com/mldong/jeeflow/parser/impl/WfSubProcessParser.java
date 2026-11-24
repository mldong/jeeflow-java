package com.mldong.jeeflow.parser.impl;

import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.model.NodeModel;
import com.mldong.jeeflow.model.SubProcessModel;
import com.mldong.jeeflow.model.logicflow.LfNode;
import com.mldong.jeeflow.parser.AbstractNodeParser;
import com.mldong.jeeflow.parser.NodeParser;
import com.mldong.jeeflow.util.StringUtils;

public class WfSubProcessParser extends AbstractNodeParser {

    @Override
    public void parseNode(LfNode lfNode) {
        SubProcessModel subProcessModel = (SubProcessModel) nodeModel;
        FlowData properties = lfNode.getProperties();
        if (properties != null) {
            String processName = properties.getStr(PROCESS_NAME_KEY);
            if (StringUtils.isBlank(processName)) processName = properties.getStr(NAME_KEY);
            if (StringUtils.isBlank(processName)) processName = subProcessModel.getName();
            subProcessModel.setProcessName(processName);
            subProcessModel.setForm(properties.getStr(FORM_KEY));
            subProcessModel.setVersion(properties.getInt(VERSION_KEY));
        } else {
            subProcessModel.setProcessName(subProcessModel.getName());
        }
    }

    @Override
    public NodeModel newModel() {
        return new SubProcessModel();
    }
}
