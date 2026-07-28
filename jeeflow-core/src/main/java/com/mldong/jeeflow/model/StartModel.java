package com.mldong.jeeflow.model;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.event.ProcessEvent;
import com.mldong.jeeflow.event.ProcessPublisher;
import com.mldong.jeeflow.enums.ProcessEventTypeEnum;

/**
 * 开始节点模型
 *
 * @author mldong
 */
public class StartModel extends NodeModel {

    @Override
    public void exec(Execution execution) {
        ProcessPublisher.notify(ProcessEvent.builder()
                .eventType(ProcessEventTypeEnum.PROCESS_INSTANCE_START)
                .sourceId(execution.getProcessInstanceId())
                .build());
        runOutTransition(execution);
    }
}
