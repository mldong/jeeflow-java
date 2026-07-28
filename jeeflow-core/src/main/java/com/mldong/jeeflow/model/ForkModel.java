package com.mldong.jeeflow.model;

import com.mldong.jeeflow.core.Execution;

/**
 * 分支节点模型
 *
 * @author mldong
 */
public class ForkModel extends NodeModel {

    @Override
    public void exec(Execution execution) {
        runOutTransition(execution);
    }
}
