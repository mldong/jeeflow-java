package com.mldong.jeeflow.scheduling;

import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.enums.ProcessEventTypeEnum;
import com.mldong.jeeflow.event.ProcessEvent;
import com.mldong.jeeflow.event.ProcessEventListener;

import java.util.List;

/**
 * 调度事件监听器
 *
 * @author mldong
 */
public class SchedulerProcessEventListener implements ProcessEventListener {

    @Override
    public void onEvent(ProcessEvent event) {
        List<IScheduler> schedulers = ServiceContext.findList(IScheduler.class);
        if (schedulers != null) {
            FlowData params = FlowData.create();
            params.put("eventType", event.getEventType());
            params.put("sourceId", event.getSourceId());
            for (IScheduler scheduler : schedulers) {
                scheduler.schedule(params);
            }
        }
    }
}
