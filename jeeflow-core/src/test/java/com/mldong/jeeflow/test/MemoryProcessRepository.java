package com.mldong.jeeflow.test;

import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.spi.IProcessRepository;
import com.mldong.jeeflow.spi.PageQuery;
import com.mldong.jeeflow.spi.PageResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
        // 追加语义（对齐 boot2/boot3）：去重后追加，不清空原参与者
        List<String> existing = taskActors.computeIfAbsent(taskId, k -> new ArrayList<>());
        for (String a : actors) {
            if (!existing.contains(a)) existing.add(a);
        }
    }

    @Override
    public void removeTaskActor(Long taskId, List<String> actors) {
        List<String> existing = taskActors.get(taskId);
        if (existing != null) existing.removeAll(actors);
    }

    @Override
    public PageResult<TaskRow> pageTodoTasks(PageQuery query) {
        // 通用条件过滤（含 pta.actor_id EQ，Facade todoList 依赖），仅进行中任务
        List<TaskRow> rows = new ArrayList<>();
        for (ProcessTask t : tasks.values()) {
            if (!Integer.valueOf(10).equals(t.getTaskState())) continue;
            TaskRow r = toTaskRow(t);
            Map<String, Object> fields = taskFields(r);
            fields.put("pta.actor_id", taskActors.getOrDefault(t.getTaskId(), new ArrayList<>()));
            if (matches(query.getConditions(), fields)) rows.add(r);
        }
        return PageResult.of(query.getPageNum(), query.getPageSize(), rows.size(), rows);
    }

    @Override
    public PageResult<TaskRow> pageDoneTasks(PageQuery query) {
        // 通用条件过滤（含 t.operator EQ，Facade doneList 依赖），仅已完成任务
        List<TaskRow> rows = new ArrayList<>();
        for (ProcessTask t : tasks.values()) {
            if (!Integer.valueOf(20).equals(t.getTaskState())) continue;
            TaskRow r = toTaskRow(t);
            if (matches(query.getConditions(), taskFields(r))) rows.add(r);
        }
        return PageResult.of(query.getPageNum(), query.getPageSize(), rows.size(), rows);
    }

    private TaskRow toTaskRow(ProcessTask t) {
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
        r.setTaskParentId(t.getParentTaskId());
        r.setCreateTime(t.getCreateTime());
        r.setUpdateTime(t.getUpdateTime());
        if (t.getVariables() != null) {
            com.mldong.jeeflow.json.IJsonProvider json = com.mldong.jeeflow.core.ServiceContext.find(com.mldong.jeeflow.json.IJsonProvider.class);
            if (json != null) r.setVariable(json.toJson(t.getVariables()));
        }
        ProcessInstance inst = instances.get(t.getProcessInstanceId());
        if (inst != null) {
            r.setInstanceCreateTime(inst.getCreateTime());
            ProcessInstance.ProcessDefine def = defines.get(inst.getDefineId());
            if (def != null) {
                r.setProcessDefineName(def.getName());
                r.setProcessDefineDisplayName(def.getDisplayName());
                r.setProcessDefineVersion(def.getVersion());
            }
        }
        return r;
    }

    @Override public PageResult<InstanceRow> pageInstances(PageQuery query) {
        // 通用条件过滤（含 t.operator EQ，Facade instancePage 依赖）
        List<InstanceRow> rows = new ArrayList<>();
        for (ProcessInstance inst : instances.values()) {
            InstanceRow r = toInstanceRow(inst);
            if (matches(query.getConditions(), instanceFields(r))) rows.add(r);
        }
        return PageResult.of(query.getPageNum(), query.getPageSize(), rows.size(), rows);
    }

    // ═══ 通用条件匹配（对齐 JdbcProcessRepository.buildWhere 语义，issues/05-5） ═══

    /** 行字段（列名 → 值）映射：白名单列均可匹配 */
    private static Map<String, Object> taskFields(TaskRow r) {
        Map<String, Object> m = new HashMap<>();
        m.put("t.id", r.getId());
        m.put("t.task_name", r.getTaskName());
        m.put("t.display_name", r.getDisplayName());
        m.put("t.task_type", r.getTaskType());
        m.put("t.perform_type", r.getPerformType());
        m.put("t.task_state", r.getTaskState());
        m.put("t.operator", r.getOperator());
        m.put("t.form_key", r.getFormKey());
        m.put("t.create_time", r.getCreateTime());
        m.put("t.finish_time", r.getFinishTime());
        m.put("t.expire_time", r.getExpireTime());
        m.put("t.process_instance_id", r.getProcessInstanceId());
        m.put("t.task_parent_id", r.getTaskParentId());
        m.put("t.variable", r.getVariable());
        m.put("pd.name", r.getProcessDefineName());
        m.put("pd.display_name", r.getProcessDefineDisplayName());
        m.put("pd.version", r.getProcessDefineVersion());
        return m;
    }

    private static Map<String, Object> instanceFields(InstanceRow r) {
        Map<String, Object> m = new HashMap<>();
        m.put("t.id", r.getId());
        m.put("t.parent_id", r.getParentId());
        m.put("t.process_define_id", r.getProcessDefineId());
        m.put("t.state", r.getState());
        m.put("t.parent_node_name", r.getParentNodeName());
        m.put("t.business_no", r.getBusinessNo());
        m.put("t.operator", r.getOperator());
        m.put("t.expire_time", r.getExpireTime());
        m.put("t.variable", r.getVariable());
        m.put("t.create_time", r.getCreateTime());
        m.put("pd.name", r.getProcessDefineName());
        m.put("pd.display_name", r.getProcessDefineDisplayName());
        m.put("pd.version", r.getProcessDefineVersion());
        return m;
    }

    private static Map<String, Object> defineFields(DefineRow r) {
        Map<String, Object> m = new HashMap<>();
        m.put("t.id", r.getId());
        m.put("t.name", r.getName());
        m.put("t.display_name", r.getDisplayName());
        m.put("t.type", r.getType());
        m.put("t.state", r.getState());
        m.put("t.version", r.getVersion());
        m.put("t.create_time", r.getCreateTime());
        m.put("t.update_time", r.getUpdateTime());
        return m;
    }

    /** 条件全匹配（列不在行字段中则跳过；操作符对齐 JDBC buildWhere） */
    private static boolean matches(List<PageQuery.Condition> conditions, Map<String, Object> fields) {
        for (PageQuery.Condition c : conditions) {
            Object v = fields.get(c.getColumn());
            Object expect = c.getValue();
            if (v == null || expect == null) continue;
            switch (c.getOperator().toUpperCase()) {
                case "EQ":
                    if (!eq(v, expect)) return false;
                    break;
                case "NE":
                    if (eq(v, expect)) return false;
                    break;
                case "LIKE":
                    if (!v.toString().contains(expect.toString())) return false;
                    break;
                case "LLIKE":
                    if (!v.toString().endsWith(expect.toString())) return false;
                    break;
                case "RLIKE":
                    if (!v.toString().startsWith(expect.toString())) return false;
                    break;
                case "GT":
                    if (compareValues(v, expect) <= 0) return false;
                    break;
                case "GE":
                    if (compareValues(v, expect) < 0) return false;
                    break;
                case "LT":
                    if (compareValues(v, expect) >= 0) return false;
                    break;
                case "LE":
                    if (compareValues(v, expect) > 0) return false;
                    break;
                case "IN":
                    if (expect instanceof Collection && !((Collection<?>) expect).contains(v)) return false;
                    break;
                case "NIN":
                    if (expect instanceof Collection && ((Collection<?>) expect).contains(v)) return false;
                    break;
            }
        }
        return true;
    }

    /** EQ：值或集合包含判断（pta.actor_id/cc.actor_id 为集合） */
    private static boolean eq(Object v, Object expect) {
        if (v instanceof Collection) return ((Collection<?>) v).contains(expect);
        return v.toString().equals(expect.toString());
    }

    /** 值比较：都可比则 compareTo，否则字符串比较 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int compareValues(Object a, Object b) {
        if (a instanceof Comparable && b instanceof Comparable) {
            return ((Comparable) a).compareTo(b);
        }
        return a.toString().compareTo(b.toString());
    }

    private InstanceRow toInstanceRow(ProcessInstance inst) {
        InstanceRow r = new InstanceRow();
        r.setId(inst.getInstanceId());
        r.setParentId(inst.getParentId());
        r.setProcessDefineId(inst.getDefineId());
        r.setState(inst.getState());
        r.setParentNodeName(inst.getParentNodeName());
        r.setBusinessNo(inst.getBusinessNo());
        r.setOperator(inst.getOperator());
        r.setExpireTime(inst.getExpireTime());
        r.setCreateTime(inst.getCreateTime());
        r.setCreateUser(inst.getCreateUser());
        r.setUpdateTime(inst.getUpdateTime());
        r.setUpdateUser(inst.getUpdateUser());
        if (inst.getVariables() != null) {
            com.mldong.jeeflow.json.IJsonProvider json = com.mldong.jeeflow.core.ServiceContext.find(com.mldong.jeeflow.json.IJsonProvider.class);
            if (json != null) r.setVariable(json.toJson(inst.getVariables()));
        }
        ProcessInstance.ProcessDefine def = defines.get(inst.getDefineId());
        if (def != null) {
            r.setProcessDefineName(def.getName());
            r.setProcessDefineDisplayName(def.getDisplayName());
            r.setProcessDefineVersion(def.getVersion());
        }
        return r;
    }

    @Override
    public PageResult<InstanceRow> pageCcInstances(PageQuery query) {
        // 通用条件过滤（含 cc.actor_id EQ，Facade ccList 依赖）
        List<InstanceRow> rows = new ArrayList<>();
        for (Map.Entry<Long, List<String>> e : ccInstances.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            ProcessInstance inst = instances.get(e.getKey());
            if (inst == null) continue;
            InstanceRow r = toInstanceRow(inst);
            Map<String, Object> fields = instanceFields(r);
            fields.put("cc.actor_id", new ArrayList<>(e.getValue()));
            if (matches(query.getConditions(), fields)) rows.add(r);
        }
        return PageResult.of(query.getPageNum(), query.getPageSize(), rows.size(), rows);
    }

    @Override
    public PageResult<DefineRow> pageDefines(PageQuery query) {
        // 通用条件过滤（含 t.name EQ / t.state GT，JeeflowFacade deploy 版本查询依赖），按 id 倒序
        List<DefineRow> rows = defines.values().stream()
                .filter(d -> {
                    DefineRow r = new DefineRow();
                    r.setId(d.getId());
                    r.setName(d.getName());
                    r.setDisplayName(d.getDisplayName());
                    r.setType(d.getType());
                    r.setState(d.getState());
                    r.setVersion(d.getVersion());
                    return matches(query.getConditions(), defineFields(r));
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
