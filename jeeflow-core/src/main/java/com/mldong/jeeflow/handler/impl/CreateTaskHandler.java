package com.mldong.jeeflow.handler.impl;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;
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
import java.util.Collection;
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
        FlowData args = execution.getArgs();
        // 1. 动态指定下一节点处理人优先（v1.0.1：对齐 boot2/boot3 tf_nextNodeOperator）
        Object nextNodeOperator = args.get(FlowConst.NEXT_NODE_OPERATOR);
        if (nextNodeOperator != null && !nextNodeOperator.toString().isEmpty()) {
            if (nextNodeOperator instanceof Collection) {
                for (Object o : (Collection<?>) nextNodeOperator) {
                    String t = String.valueOf(o).trim();
                    if (!t.isEmpty() && !actors.contains(t)) actors.add(t);
                }
            } else {
                for (String a : nextNodeOperator.toString().split(",")) {
                    String t = a.trim();
                    if (!t.isEmpty() && !actors.contains(t)) actors.add(t);
                }
            }
            return actors;
        }
        // 2. 固定指派 assignee——token 即变量 key，能替换就换，换不了就是字面量（v1.0.1 对齐 boot3 args.get(token, token)）
        if (taskModel.getAssignee() != null && !taskModel.getAssignee().isEmpty()) {
            String assignee = taskModel.getAssignee();
            for (String raw : assignee.split(",")) {
                String token = raw.trim();
                if (token.isEmpty()) continue;
                // mldong 契约特殊值：applicant → 流程发起人
                if (token.contains("applicant")) {
                    token = token.replace("applicant", execution.getProcessInstance().getOperator());
                }
                Object v = args.get(token);
                if (v != null) {
                    if (v instanceof Collection) {
                        for (Object o : (Collection<?>) v) {
                            String t = String.valueOf(o).trim();
                            if (!t.isEmpty() && !actors.contains(t)) actors.add(t);
                        }
                    } else {
                        String t = String.valueOf(v).trim();
                        if (!t.isEmpty() && !actors.contains(t)) actors.add(t);
                    }
                } else if (!actors.contains(token)) {
                    actors.add(token);
                }
            }
        }
        // 3. 动态指派处理器 assignmentHandler（assignee 为空时才生效）
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
