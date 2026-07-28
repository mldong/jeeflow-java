package com.mldong.jeeflow.event;

import com.mldong.jeeflow.core.ServiceContext;

import java.util.List;

/**
 * 流程事件发布者
 *
 * @author mldong
 */
public final class ProcessPublisher {

    private ProcessPublisher() {}

    public static void notify(ProcessEvent event) {
        List<ProcessEventListener> listeners = ServiceContext.findList(ProcessEventListener.class);
        if (listeners != null) {
            for (ProcessEventListener listener : listeners) {
                listener.onEvent(event);
            }
        }
    }
}
