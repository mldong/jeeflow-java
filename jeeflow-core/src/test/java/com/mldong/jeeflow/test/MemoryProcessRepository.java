package com.mldong.jeeflow.test;

import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.spi.IProcessRepository;
import com.mldong.jeeflow.spi.PageQuery;
import com.mldong.jeeflow.spi.PageResult;

import java.util.ArrayList;
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

    @Override public PageResult<TaskRow> pageTodoTasks(PageQuery query) { return PageResult.of(1, 10, 0, new ArrayList<>()); }
    @Override public PageResult<TaskRow> pageDoneTasks(PageQuery query) { return PageResult.of(1, 10, 0, new ArrayList<>()); }
    @Override public PageResult<InstanceRow> pageInstances(PageQuery query) { return PageResult.of(1, 10, 0, new ArrayList<>()); }
    @Override public PageResult<InstanceRow> pageCcInstances(PageQuery query) { return PageResult.of(1, 10, 0, new ArrayList<>()); }
    @Override public PageResult<DefineRow> pageDefines(PageQuery query) { return PageResult.of(1, 10, 0, new ArrayList<>()); }
    @Override public int countTodoTasks(Long userId) { return 0; }
}
