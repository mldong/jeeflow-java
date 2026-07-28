package com.mldong.jeeflow.event;

/**
 * 流程事件监听器接口
 *
 * @author mldong
 */
public interface ProcessEventListener {

    void onEvent(ProcessEvent event);
}
