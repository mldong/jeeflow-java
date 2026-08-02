package com.mldong.jeeflow.test;

import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.spi.IProcessRepository;
import com.mldong.jeeflow.spi.PageQuery;
import com.mldong.jeeflow.spi.PageResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 基于 HashMap 的内存存储实现（仅供测试和示例）
 *
 * @author mldong
 */
public class MemoryProcessRepository implements IProcessRepository {

    private final AtomicLong idSeq = new AtomicLong(1);
    private final Map<Long, ProcessInstance.ProcessDefine> defines = new ConcurrentHashMap<>();
    private final Map<Long, ProcessInstance> instances = new ConcurrentHashMap<>();
    private final Map<Long, ProcessTask> tasks = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> taskActors = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> ccInstances = new ConcurrentHashMap<>();

    // ---- 流程定义 ----
    @Override
    public ProcessInstance.ProcessDefine findDefineById(Long defineId) {
        return defines.get(defineId);
    }

    public void addDefine(ProcessInstance.ProcessDefine define) {
        if (define.getId() == null) define.setId(idSeq.getAndIncrement());
        defines.put(define.getId(), define);
    }

    // 定义写操作（v1.0.1，对齐 SPI）
    @Override
    public void saveDefine(ProcessInstance.ProcessDefine define) {
        if (define.getId() == null) define.setId(idSeq.getAndIncrement());
        defines.put(define.getId(), define);
    }

    @Override
    public void updateDefine(ProcessInstance.ProcessDefine define) {
        defines.put(define.getId(), define);
    }

    @Override
    public void updateDefineState(Long defineId, int state) {
        ProcessInstance.ProcessDefine d = defines.get(defineId);
        if (d != null) d.setState(state);
    }

    @Override
    public void removeDefine(Long defineId) {
        defines.remove(defineId);
    }

    // ---- 流程实例 ----
    @Override
    public ProcessInstance findInstanceById(Long instanceId) {
        ProcessInstance inst = instances.get(instanceId);
        if (inst != null) {
            // 加载关联的任务
            List<ProcessTask> allTasks = tasks.values().stream()
                    .filter(t -> instanceId.equals(t.getProcessInstanceId()))
                    .collect(Collectors.toList());
            inst.setTasks(allTasks);
        }
        return inst;
    }

    @Override
    public void saveInstance(ProcessInstance instance) {
        if (instance.getInstanceId() == null) instance.setInstanceId(idSeq.getAndIncrement());
        instances.put(instance.getInstanceId(), instance);
    }

    @Override
    public void updateInstance(ProcessInstance instance) {
        instances.put(instance.getInstanceId(), instance);
        // v1.0.1：级联保存聚合根内任务状态变更（对齐 JdbcProcessRepository）
        if (instance.getTasks() != null) {
            for (ProcessTask task : instance.getTasks()) {
                if (task.getTaskId() != null) {
                    tasks.put(task.getTaskId(), task);
                    if (task.getActorIds() != null && !task.getActorIds().isEmpty()) {
                        taskActors.put(task.getTaskId(), new ArrayList<>(task.getActorIds()));
                    }
                }
            }
        }
    }

    // ---- 流程任务 ----
    @Override
    public ProcessTask findTaskById(Long taskId) {
        return tasks.get(taskId);
    }

    @Override
    public void saveTask(ProcessTask task) {
        if (task.getTaskId() == null) task.setTaskId(idSeq.getAndIncrement());
        tasks.put(task.getTaskId(), task);
        if (task.getActorIds() != null && !task.getActorIds().isEmpty()) {
            taskActors.put(task.getTaskId(), new ArrayList<>(task.getActorIds()));
        }
    }

    @Override
    public void updateTask(ProcessTask task) {
        tasks.put(task.getTaskId(), task);
    }

    @Override
    public List<ProcessTask> findDoingTasks(Long instanceId, String[] taskNames) {
        List<String> names = taskNames != null ? java.util.Arrays.asList(taskNames) : new ArrayList<>();
        return tasks.values().stream()
                .filter(t -> instanceId.equals(t.getProcessInstanceId()) && t.isDoing())
                .filter(t -> names.isEmpty() || names.contains(t.getTaskName()))
                .collect(Collectors.toList());
    }

    @Override
    public List<ProcessTask> findDoneTasks(Long instanceId, String[] taskNames) {
        List<String> names = taskNames != null ? java.util.Arrays.asList(taskNames) : new ArrayList<>();
        return tasks.values().stream()
                .filter(t -> instanceId.equals(t.getProcessInstanceId()) && t.isFinished())
                .filter(t -> names.isEmpty() || names.contains(t.getTaskName()))
                .collect(Collectors.toList());
    }

    @Override
    public List<ProcessTask> findHistoryTasks(Long instanceId) {
        return tasks.values().stream()
                .filter(t -> instanceId.equals(t.getProcessInstanceId()))
                .collect(Collectors.toList());
    }

    @Override
    public void createCcInstance(Long instanceId, String creator, String... actorIds) {
        for (String actorId : actorIds) {
            ccInstances.computeIfAbsent(instanceId, k -> new ArrayList<>()).add(actorId);
        }
    }

    @Override
    public void updateCcStatus(Long instanceId, String actorId) {
        // no-op for memory
    }

    @Override
    public List<String> findTaskActors(Long taskId) {
        return taskActors.getOrDefault(taskId, new ArrayList<>());
    }

    @Override
    public void addTaskActor(Long taskId, List<String> actors) {
        taskActors.computeIfAbsent(taskId, k -> new ArrayList<>()).addAll(actors);
    }

    @Override
    public void removeTaskActor(Long taskId, List<String> actors) {
        List<String> existing = taskActors.get(taskId);
        if (existing != null) existing.removeAll(actors);
    }

    @Override
    public PageResult<TaskRow> pageTodoTasks(PageQuery query) {
        // 支持 pta.actor_id EQ 过滤（Facade todoList 依赖），仅进行中任务
        String actorId = null;
        for (PageQuery.Condition c : query.getConditions()) {
            if ("pta.actor_id".equals(c.getColumn()) && "EQ".equalsIgnoreCase(c.getOperator())) {
                actorId = c.getValue().toString();
            }
        }
        List<TaskRow> rows = new ArrayList<>();
        for (ProcessTask t : tasks.values()) {
            if (!Integer.valueOf(10).equals(t.getTaskState())) continue;
            List<String> actors = taskActors.get(t.getTaskId());
            if (actorId != null && (actors == null || !actors.contains(actorId))) continue;
            rows.add(toTaskRow(t));
        }
        return PageResult.of(query.getPageNum(), query.getPageSize(), rows.size(), rows);
    }

    @Override
    public PageResult<TaskRow> pageDoneTasks(PageQuery query) {
        // 支持 t.operator EQ 过滤（Facade doneList 依赖），仅已完成任务
        String operator = null;
        for (PageQuery.Condition c : query.getConditions()) {
            if ("t.operator".equals(c.getColumn()) && "EQ".equalsIgnoreCase(c.getOperator())) {
                operator = c.getValue().toString();
            }
        }
        List<TaskRow> rows = new ArrayList<>();
        for (ProcessTask t : tasks.values()) {
            if (!Integer.valueOf(20).equals(t.getTaskState())) continue;
            if (operator != null && !operator.equals(t.getActorId())) continue;
            rows.add(toTaskRow(t));
        }
        return PageResult.of(query.getPageNum(), query.getPageSize(), rows.size(), rows);
    }

    private static TaskRow toTaskRow(ProcessTask t) {
        TaskRow r = new TaskRow();
        r.setId(t.getTaskId());
        r.setProcessInstanceId(t.getProcessInstanceId());
        r.setTaskName(t.getTaskName());
        r.setDisplayName(t.getDisplayName());
        r.setTaskType(t.getTaskType() != null ? t.getTaskType().getCode() : null);
        r.setPerformType(t.getPerformType() != null ? t.getPerformType().getCode() : null);
        r.setTaskState(t.getTaskState());
        r.setOperator(t.getActorId());
        r.setFormKey(t.getFormKey());
        r.setCreateTime(t.getCreateTime());
        return r;
    }

    @Override public PageResult<InstanceRow> pageInstances(PageQuery query) { return PageResult.of(1, 10, 0, new ArrayList<>()); }
    @Override public PageResult<InstanceRow> pageCcInstances(PageQuery query) { return PageResult.of(1, 10, 0, new ArrayList<>()); }

    @Override
    public PageResult<DefineRow> pageDefines(PageQuery query) {
        // 支持 t.name EQ / t.state 过滤（JeeflowFacade deploy 版本查询依赖），按 id 倒序
        List<DefineRow> rows = defines.values().stream()
                .filter(d -> {
                    for (PageQuery.Condition c : query.getConditions()) {
                        if ("t.name".equals(c.getColumn()) && "EQ".equalsIgnoreCase(c.getOperator())) {
                            if (!c.getValue().toString().equals(d.getName())) return false;
                        }
                        if ("t.state".equals(c.getColumn()) && "GT".equalsIgnoreCase(c.getOperator())) {
                            if (d.getState() == null || d.getState() <= ((Number) c.getValue()).intValue()) return false;
                        }
                    }
                    return true;
                })
                .sorted(Comparator.comparing(ProcessInstance.ProcessDefine::getId).reversed())
                .map(d -> {
                    DefineRow r = new DefineRow();
                    r.setId(d.getId());
                    r.setName(d.getName());
                    r.setDisplayName(d.getDisplayName());
                    r.setType(d.getType());
                    r.setState(d.getState());
                    r.setVersion(d.getVersion());
                    return r;
                })
                .collect(Collectors.toList());
        return PageResult.of(query.getPageNum(), query.getPageSize(), rows.size(), rows);
    }

    @Override public int countTodoTasks(Long userId) { return 0; }
}
