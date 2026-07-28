package com.mldong.jeeflow.model;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.handler.impl.MergeBranchHandler;

/**
 * 合并节点模型
 *
 * @author mldong
 */
public class JoinModel extends NodeModel {

    @Override
    public void exec(Execution execution) {
        fire(new MergeBranchHandler(this), execution);
        if (execution.isMerged()) {
            runOutTransition(execution);
        }
    }
}
