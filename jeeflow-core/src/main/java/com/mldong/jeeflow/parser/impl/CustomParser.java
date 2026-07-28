package com.mldong.jeeflow.parser.impl;

import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.model.CustomModel;
import com.mldong.jeeflow.model.NodeModel;
import com.mldong.jeeflow.model.logicflow.LfNode;
import com.mldong.jeeflow.parser.AbstractNodeParser;

public class CustomParser extends AbstractNodeParser {

    @Override
    public void parseNode(LfNode lfNode) {
        CustomModel customModel = (CustomModel) nodeModel;
        FlowData properties = lfNode.getProperties();
        if (properties != null) {
            customModel.setClazz(properties.getStr(CLASS_KEY));
            customModel.setMethodName(properties.getStr(METHOD_NAME_KEY));
            customModel.setArgs(properties.getStr(ARGS_KEY));
            customModel.setVar(properties.getStr(RETURN_VAL_KEY) != null
                    ? properties.getStr(RETURN_VAL_KEY)
                    : FlowConst.CUSTOM_RETURN_VAL);
        }
    }

    @Override
    public NodeModel newModel() {
        return new CustomModel();
    }
}
