package com.mldong.jeeflow.handler;

import com.mldong.jeeflow.core.Execution;

/**
 * 任务处理器接口
 *
 * @author mldong
 */
public interface IHandler {

    void handle(Execution execution);
}
