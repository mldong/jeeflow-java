package com.mldong.jeeflow.persist.test;

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
 * 精简内存仓储（仅引擎执行链所需方法；分页/抄送等抛不支持）——persist 模块测试用
 */
public class MemoryRepo implements IProcessRepository {

    private final AtomicLong idSeq = new AtomicLong(1);
    private final Map<Long, ProcessInstance.ProcessDefine> defines = new ConcurrentHashMap<>();
    private final Map<Long, ProcessInstance> instances = new ConcurrentHashMap<>();
    private final Map<Long, ProcessTask> tasks = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> taskActors = new ConcurrentHashMap<>();

    public void addDefine(ProcessInstance.ProcessDefine define) {
        if (define.getId() == null) define.setId(idSeq.getAndIncrement());
        defines.put(define.getId(), define);
    }

    @Override public ProcessInstance.ProcessDefine findDefineById(Long defineId) { return defines.get(defineId); }
    @Override public void saveDefine(ProcessInstance.ProcessDefine define) { addDefine(define); }
    @Override public void updateDefine(ProcessInstance.ProcessDefine define) { defines.put(define.getId(), define); }
    @Override public void updateDefineState(Long defineId, int state) { defines.get(defineId).setState(state); }
    @Override public void removeDefine(Long defineId) { defines.remove(defineId); }

    @Override public ProcessInstance findInstanceById(Long instanceId) { return instances.get(instanceId); }
    @Override public void saveInstance(ProcessInstance instance) {
        if (instance.getInstanceId() == null) instance.setInstanceId(idSeq.getAndIncrement());
        instances.put(instance.getInstanceId(), instance);
    }
    @Override public void updateInstance(ProcessInstance instance) { instances.put(instance.getInstanceId(), instance); }

    @Override public ProcessTask findTaskById(Long taskId) { return tasks.get(taskId); }
    @Override public void saveTask(ProcessTask task) {
        if (task.getTaskId() == null) task.setTaskId(idSeq.getAndIncrement());
        tasks.put(task.getTaskId(), task);
        if (task.getActorIds() != null && !task.getActorIds().isEmpty()) {
            taskActors.put(task.getTaskId(), new ArrayList<>(task.getActorIds()));
        }
    }
    @Override public void updateTask(ProcessTask task) { tasks.put(task.getTaskId(), task); }

    @Override public List<ProcessTask> findDoingTasks(Long instanceId, String[] taskNames) {
        List<String> names = taskNames != null ? java.util.Arrays.asList(taskNames) : new ArrayList<>();
        return tasks.values().stream()
                .filter(t -> instanceId.equals(t.getProcessInstanceId()) && t.isDoing())
                .filter(t -> names.isEmpty() || names.contains(t.getTaskName()))
                .collect(Collectors.toList());
    }
    @Override public List<ProcessTask> findDoneTasks(Long instanceId, String[] taskNames) {
        List<String> names = taskNames != null ? java.util.Arrays.asList(taskNames) : new ArrayList<>();
        return tasks.values().stream()
                .filter(t -> instanceId.equals(t.getProcessInstanceId()) && t.isFinished())
                .filter(t -> names.isEmpty() || names.contains(t.getTaskName()))
                .collect(Collectors.toList());
    }
    @Override public List<ProcessTask> findHistoryTasks(Long instanceId) {
        return tasks.values().stream()
                .filter(t -> instanceId.equals(t.getProcessInstanceId()))
                .collect(Collectors.toList());
    }

    @Override public void createCcInstance(Long instanceId, String creator, String... actorIds) { }
    @Override public void updateCcStatus(Long instanceId, String actorId) { }
    @Override public List<String> findTaskActors(Long taskId) { return taskActors.getOrDefault(taskId, new ArrayList<>()); }
    @Override public void addTaskActor(Long taskId, List<String> actors) {
        List<String> existing = taskActors.computeIfAbsent(taskId, k -> new ArrayList<>());
        for (String a : actors) if (!existing.contains(a)) existing.add(a);
    }
    @Override public void removeTaskActor(Long taskId, List<String> actors) {
        List<String> existing = taskActors.get(taskId);
        if (existing != null) existing.removeAll(actors);
    }

    private <T> PageResult<T> unsupported() { throw new UnsupportedOperationException("persist 测试不涉及分页"); }
    @Override public PageResult<TaskRow> pageTodoTasks(PageQuery query) { return unsupported(); }
    @Override public PageResult<TaskRow> pageDoneTasks(PageQuery query) { return unsupported(); }
    @Override public PageResult<InstanceRow> pageInstances(PageQuery query) { return unsupported(); }
    @Override public PageResult<InstanceRow> pageCcInstances(PageQuery query) { return unsupported(); }
    @Override public PageResult<DefineRow> pageDefines(PageQuery query) { return unsupported(); }
    @Override public int countTodoTasks(Long userId) { return 0; }
}
