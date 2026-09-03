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
    /**
     * 抄送人 id（仅 {@link ProcessEventTypeEnum#CC_CREATE} 用）。
     *
     * <p>逐抄送人 fire，引擎把抄送人 id 直传事件体（对齐 PHP 参考实现 v1.3.8），
     * 集成层监听器直接取用、免反查 cc 表。非 CC_CREATE 事件恒为 null（向后兼容）。</p>
     */
    private String ccActorId;
    private FlowData data = FlowData.create();

    public static ProcessEventBuilder builder() {
        return new ProcessEventBuilder();
    }

    public ProcessEventTypeEnum getEventType() { return eventType; }
    public Long getSourceId() { return sourceId; }
    public String getCcActorId() { return ccActorId; }
    public FlowData getData() { return data; }

    public static class ProcessEventBuilder {
        private final ProcessEvent event = new ProcessEvent();
        public ProcessEventBuilder eventType(ProcessEventTypeEnum type) { event.eventType = type; return this; }
        public ProcessEventBuilder sourceId(Long id) { event.sourceId = id; return this; }
        public ProcessEventBuilder ccActorId(String ccActorId) { event.ccActorId = ccActorId; return this; }
        public ProcessEventBuilder data(FlowData data) { event.data = data; return this; }
        public ProcessEvent build() { return event; }
    }
}
