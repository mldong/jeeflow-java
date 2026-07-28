package com.mldong.jeeflow.interceptor;

import com.mldong.jeeflow.core.Execution;

/**
 * 节点执行接口（所有节点模型都实现此接口）
 *
 * @author mldong
 */
public interface Action {

    void execute(Execution execution);
}
