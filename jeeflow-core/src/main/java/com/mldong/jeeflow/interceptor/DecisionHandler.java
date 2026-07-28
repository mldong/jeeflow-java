package com.mldong.jeeflow.interceptor;

import com.mldong.jeeflow.core.Execution;

/**
 * 决策处理器接口——决策节点通过此接口决定下一个节点
 *
 * @author mldong
 */
public interface DecisionHandler {

    String decide(Execution execution);
}
