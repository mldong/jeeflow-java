package com.mldong.jeeflow.handler.impl;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.ProcessEventTypeEnum;
import com.mldong.jeeflow.enums.ProcessTaskPerformTypeEnum;
import com.mldong.jeeflow.event.ProcessEvent;
import com.mldong.jeeflow.event.ProcessPublisher;
import com.mldong.jeeflow.handler.IHandler;
import com.mldong.jeeflow.interceptor.AssignmentHandler;
import com.mldong.jeeflow.interceptor.FlowInterceptor;
import com.mldong.jeeflow.model.ProcessModel;
import com.mldong.jeeflow.model.TaskModel;

import java.util.ArrayList;
import java.util.List;

/**
 * 创建任务处理器
 *
 * @author mldong
 */
public class CreateTaskHandler implements IHandler {

    private final TaskModel taskModel;

    public CreateTaskHandler(TaskModel taskModel) {
        this.taskModel = taskModel;
    }

    @Override
    public void handle(Execution execution) {
        ProcessInstance instance = execution.getProcessInstance();
        ProcessModel model = execution.getProcessModel();
        String operator = execution.getOperator();

        // 获取候选人
        List<String> actors = resolveActors(taskModel, model, execution);

        List<ProcessTask> tasks;
        if (ProcessTaskPerformTypeEnum.COUNTERSIGN.equals(taskModel.getPerformType())) {
            tasks = instance.createCountersignTasks(taskModel, actors, operator);
        } else {
            ProcessTask task = instance.createTask(taskModel, taskModel.getDisplayName(), actors, operator);
            tasks = new ArrayList<>();
            tasks.add(task);
        }

        execution.addTasks(tasks);

        // 执行拦截器
        List<FlowInterceptor> interceptors = ServiceContext.findList(FlowInterceptor.class);
        if (interceptors != null) {
            for (FlowInterceptor interceptor : interceptors) {
                interceptor.intercept(execution);
            }
        }

        // 发布事件
        for (ProcessTask task : tasks) {
            ProcessPublisher.notify(ProcessEvent.builder()
                    .eventType(ProcessEventTypeEnum.PROCESS_TASK_START)
                    .sourceId(task.getTaskId())
                    .build());
        }
    }

    private List<String> resolveActors(TaskModel taskModel, ProcessModel model, Execution execution) {
        List<String> actors = new ArrayList<>();
        // 1. 固定指派 assignee
        if (taskModel.getAssignee() != null && !taskModel.getAssignee().isEmpty()) {
            String assignee = taskModel.getAssignee();
            if (assignee.contains(",")) {
                for (String a : assignee.split(",")) {
                    String trimmed = a.trim();
                    if (!trimmed.isEmpty()) actors.add(trimmed);
                }
            } else {
                actors.add(assignee.trim());
            }
        }
        // 2. 动态指派处理器 assignmentHandler（assignee 为空时才生效）
        if (actors.isEmpty()) {
            String handlerClass = taskModel.getAssignmentHandler();
            if (handlerClass != null && !handlerClass.isEmpty()) {
                try {
                    AssignmentHandler handler = (AssignmentHandler)
                            Class.forName(handlerClass.trim()).getDeclaredConstructor().newInstance();
                    String result = handler.assign(execution);
                    if (result != null && !result.isEmpty()) {
                        if (result.contains(",")) {
                            for (String a : result.split(",")) {
                                if (!actors.contains(a.trim())) actors.add(a.trim());
                            }
                        } else if (!actors.contains(result.trim())) {
                            actors.add(result.trim());
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return actors;
    }
}
