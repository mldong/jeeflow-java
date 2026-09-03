package com.mldong.jeeflow.event;

import com.mldong.jeeflow.core.ServiceContext;

import java.util.List;
import java.util.logging.Logger;

/**
 * 流程事件发布者
 *
 * <p>兜底语义（issues/104 P2 统一口径）：单监听器异常只记 warn 不传播——
 * 不得影响引擎主流程，也不得中断后续监听器（对齐 PHP v1.3.8 per-listener
 * catch Throwable；Go/Node/Python/Rust 同批对齐）。</p>
 *
 * @author mldong
 */
public final class ProcessPublisher {

    /** JUL 而非 slf4j：core 零外部运行时依赖（slf4j 为 provided，部分消费方无绑定）。 */
    private static final Logger log = Logger.getLogger(ProcessPublisher.class.getName());

    private ProcessPublisher() {}

    public static void notify(ProcessEvent event) {
        List<ProcessEventListener> listeners = ServiceContext.findList(ProcessEventListener.class);
        if (listeners != null) {
            for (ProcessEventListener listener : listeners) {
                try {
                    listener.onEvent(event);
                } catch (Exception e) {
                    log.warning(String.format(
                            "process event listener error: eventType=%s, sourceId=%s, listener=%s: %s",
                            event.getEventType(), event.getSourceId(),
                            listener.getClass().getName(), e));
                }
            }
        }
    }
}
