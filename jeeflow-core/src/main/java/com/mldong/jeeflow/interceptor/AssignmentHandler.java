package com.mldong.jeeflow.interceptor;

import com.mldong.jeeflow.core.Execution;

/**
 * 参与者处理接口——决定任务节点的参与者列表
 *
 * @author mldong
 */
public interface AssignmentHandler {

    String assign(Execution execution);
}
