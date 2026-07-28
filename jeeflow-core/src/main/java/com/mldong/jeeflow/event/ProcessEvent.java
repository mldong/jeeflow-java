package com.mldong.jeeflow.event;

import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.enums.ProcessEventTypeEnum;

/**
 * 流程事件
 *
 * @author mldong
 */
public class ProcessEvent {

    private ProcessEventTypeEnum eventType;
    private Long sourceId;
    private FlowData data = FlowData.create();

    public static ProcessEventBuilder builder() {
        return new ProcessEventBuilder();
    }

    public ProcessEventTypeEnum getEventType() { return eventType; }
    public Long getSourceId() { return sourceId; }
    public FlowData getData() { return data; }

    public static class ProcessEventBuilder {
        private final ProcessEvent event = new ProcessEvent();
        public ProcessEventBuilder eventType(ProcessEventTypeEnum type) { event.eventType = type; return this; }
        public ProcessEventBuilder sourceId(Long id) { event.sourceId = id; return this; }
        public ProcessEventBuilder data(FlowData data) { event.data = data; return this; }
        public ProcessEvent build() { return event; }
    }
}
