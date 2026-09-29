package com.mldong.jeeflow.model;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.domain.FlowData;
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
        // PROCESS_INSTANCE_START（spec §11.3 码 1）：实例行 insert 之后才走到开始节点
        // （JeeflowEngineImpl 第 6 步 saveInstance → 第 8 步 start.execute），sourceId 可反查；
        // 直传载荷键 instanceId。
        ProcessPublisher.notify(ProcessEvent.builder()
                .eventType(ProcessEventTypeEnum.PROCESS_INSTANCE_START)
                .sourceId(execution.getProcessInstanceId())
                .data(FlowData.create().set(
                        ProcessPublisher.KEY_INSTANCE_ID, execution.getProcessInstanceId()))
                .build());
        runOutTransition(execution);
    }
}
