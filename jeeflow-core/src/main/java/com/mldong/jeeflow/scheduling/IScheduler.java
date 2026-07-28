package com.mldong.jeeflow.scheduling;

import com.mldong.jeeflow.domain.FlowData;

/**
 * 调度器接口——定时任务等场景使用
 *
 * @author mldong
 */
public interface IScheduler {

    void schedule(FlowData params);
}
