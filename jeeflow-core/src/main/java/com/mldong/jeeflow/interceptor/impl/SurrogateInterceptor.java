package com.mldong.jeeflow.interceptor.impl;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.domain.ProcessSurrogate;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.interceptor.FlowInterceptor;
import com.mldong.jeeflow.spi.IProcessExtRepository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 委托代理拦截器（v1.1.0 参考实现，默认不注册）
 *
 * <p>任务创建后，对每个参与者查询生效中的委托（{@link IProcessExtRepository#getSurrogate}），
 * 命中则把代理人加入任务参与者（原授权人保留，任一可办）。对齐 boot3 SurrogateInterceptor。</p>
 *
 * <p>使用：集成方将本实例加入引擎拦截器列表（构造注入扩展仓储实现）。</p>
 *
 * @author mldong
 */
public class SurrogateInterceptor implements FlowInterceptor {

    private final IProcessExtRepository extRepository;

    public SurrogateInterceptor(IProcessExtRepository extRepository) {
        this.extRepository = extRepository;
    }

    @Override
    public void intercept(Execution execution) {
        if (extRepository == null) return;
        String processName = execution.getProcessModel() != null
                ? execution.getProcessModel().getName() : null;
        LocalDateTime now = LocalDateTime.now();
        List<ProcessTask> taskList = execution.getProcessTaskList();
        if (taskList == null) return;
        for (ProcessTask task : taskList) {
            List<String> actors = task.getActorIds();
            if (actors == null) continue;
            for (String actor : actors) {
                ProcessSurrogate surrogate = extRepository.getSurrogate(actor, processName, now);
                if (surrogate != null) {
                    String agent = surrogate.getSurrogate();
                    if (agent != null && !agent.isEmpty() && !task.getActorIds().contains(agent)) {
                        task.getActorIds().add(agent);
                        JeeflowEngine engine = execution.getEngine();
                        if (engine != null) {
                            engine.getRepository().addTaskActor(task.getTaskId(), Collections.singletonList(agent));
                        }
                    }
                }
            }
        }
    }
}
