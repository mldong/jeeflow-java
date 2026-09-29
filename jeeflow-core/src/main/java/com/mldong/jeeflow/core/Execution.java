package com.mldong.jeeflow.core;

import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.ProcessTaskStateEnum;
import com.mldong.jeeflow.event.PendingInstanceEnd;
import com.mldong.jeeflow.model.NodeModel;
import com.mldong.jeeflow.model.ProcessModel;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 执行上下文——流转过程中携带的状态
 *
 * @author mldong
 */
public class Execution {

    private Long processInstanceId;
    private Long processTaskId;
    private FlowData args = FlowData.create();
    private ProcessModel processModel;
    private ProcessTask processTask;
    private ProcessInstance processInstance;
    private List<ProcessTask> processTaskList = new ArrayList<>();

    /**
     * 记录类（custom）节点产生的<b>历史行</b>（{@code task_state=20}）——
     * issues/142 · spec 02 §6.2 第 1 条（owner 2026-09-30 拍）。
     *
     * <p><b>为什么单开一条通道，而不是塞进 {@link #processTaskList}</b>（这是"落库"与"fire 码 3"
     * 的解耦点，不是风格问题）：{@code processTaskList} 在引擎侧走的是
     * {@code JeeflowEngineImpl#saveNewTask} 那条腿——{@code saveTask} 之后紧跟
     * {@code notifyTaskStart}，即 spec §11.3 <b>码 3 {@code PROCESS_TASK_START}</b>。
     * 码 3 表达的事实是"<b>新待办产生</b>"，而 spec §6.1 把记录类节点定性为
     * "本来就不该有参与者，也不该有待办"——它的行生来就是已完成态，谁也办不动。
     * 历史行混进那条腿 ⇒ 待办列表凭空多一条办不了的单、站内信多发一条"待您处理"
     * （§6.1 禁止形状 ② "兜底把行挂给当前操作人，伪造一条他不该收到的待办"的另一种实现形态）。
     * 所以这里单独一条通道，引擎只对它 {@code saveTask}、<b>不 fire 码 3</b>。</p>
     *
     * <p>形状对照：python 本轮那条腿（{@code engine.py#_exec_custom_node}）也是
     * "建行 → {@code repo.save_task} → 不发码 3 → 令牌继续"。</p>
     *
     * <p>随 execution 生死，与 {@link #pendingEnds} 同一条腿——子流程级联用
     * {@link #addHistoryTasks(java.util.List)} 上收，漏上收就是"历史行整支丢掉"。</p>
     */
    private final List<ProcessTask> historyTasks = new ArrayList<>();

    private boolean isMerged;
    private JeeflowEngine engine;
    private String operator;
    private NodeModel nodeModel;

    /**
     * 实例终态待播队列（spec §11.2 原则 3／码 2 触发时机，见
     * {@link PendingInstanceEnd}）：结束节点处理器只登记，真正的 fire 由引擎在
     * {@code repository.updateInstance} 成功返回后统一 flush。
     *
     * <p>随 execution 生死，不留静态登记表——并发流转互不串味。</p>
     */
    private final List<PendingInstanceEnd> pendingEnds = new ArrayList<>();

    public void addTask(ProcessTask task) {
        this.processTaskList.add(task);
    }

    public void addTasks(List<ProcessTask> tasks) {
        this.processTaskList.addAll(tasks);
    }

    /**
     * 登记一条记录类节点的历史行（只落库、不 fire 码 3，判据见 {@link #historyTasks} 字段注释）。
     */
    public void addHistoryTask(ProcessTask task) {
        if (task != null) {
            this.historyTasks.add(task);
        }
    }

    /**
     * 并入另一条 execution 的历史行——子流程级联的上收腿，与 {@link #addTasks(List)} 同形
     * （{@code EndProcessHandler} 在父实例的临时 execution 上流转，临时对象随即丢弃，
     * 漏上收就是"父实例这一支的历史行整支丢掉"）。
     */
    public void addHistoryTasks(List<ProcessTask> tasks) {
        if (tasks != null && !tasks.isEmpty()) {
            this.historyTasks.addAll(tasks);
        }
    }

    /** 登记一条待播的实例终态事件（不 fire，见 {@link #drainPendingEnds()}） */
    public void addPendingEnd(PendingInstanceEnd pendingEnd) {
        if (pendingEnd != null) {
            this.pendingEnds.add(pendingEnd);
        }
    }

    /**
     * 并入另一条 execution 的待播终态事件——子流程级联的收口姿势：处理器在**父实例**的
     * 临时 execution 上办结父实例，登记要随任务一起上收到外层 execution，
     * 与 {@link #addTasks(List)} 同一条腿（漏了就是"父实例终态事件整支丢掉"）。
     */
    public void addPendingEnds(List<PendingInstanceEnd> pendingEnds) {
        if (pendingEnds != null && !pendingEnds.isEmpty()) {
            this.pendingEnds.addAll(pendingEnds);
        }
    }

    public List<PendingInstanceEnd> getPendingEnds() {
        return pendingEnds;
    }

    /** 历史记录行的只读视图（引擎落库用 {@link #drainHistoryTasks()}，级联上收用本方法） */
    public List<ProcessTask> getHistoryTasks() {
        return historyTasks;
    }

    /**
     * 取走并清空历史行——与 {@link #drainPendingEnds()} 同一条理由：
     * 落库这一腿必须"一次登记只写一次"，重复 {@code saveTask} 在 JDBC 侧会打在已分配的
     * 主键上（{@code saveTask} 见 taskId 非空就不再取号，但仍是 INSERT）。
     */
    public List<ProcessTask> drainHistoryTasks() {
        List<ProcessTask> taken = new ArrayList<>(historyTasks);
        historyTasks.clear();
        return taken;
    }

    /** 取走并清空（引擎 flush 用；清空保证同一条登记不会被播两次） */
    public List<PendingInstanceEnd> drainPendingEnds() {
        List<PendingInstanceEnd> taken = new ArrayList<>(pendingEnds);
        pendingEnds.clear();
        return taken;
    }

    public List<ProcessTask> getDoingTaskList() {
        return processTaskList.stream()
                .filter(t -> ProcessTaskStateEnum.DOING.getCode().equals(t.getTaskState()))
                .collect(Collectors.toList());
    }

    // ---- getters/setters ----
    public Long getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(Long processInstanceId) { this.processInstanceId = processInstanceId; }
    public Long getProcessTaskId() { return processTaskId; }
    public void setProcessTaskId(Long processTaskId) { this.processTaskId = processTaskId; }
    public FlowData getArgs() { return args; }
    public void setArgs(FlowData args) { this.args = args; }
    public ProcessModel getProcessModel() { return processModel; }
    public void setProcessModel(ProcessModel processModel) { this.processModel = processModel; }
    public ProcessTask getProcessTask() { return processTask; }
    public void setProcessTask(ProcessTask processTask) { this.processTask = processTask; }
    public ProcessInstance getProcessInstance() { return processInstance; }
    public void setProcessInstance(ProcessInstance processInstance) { this.processInstance = processInstance; }
    public List<ProcessTask> getProcessTaskList() { return processTaskList; }
    public void setProcessTaskList(List<ProcessTask> processTaskList) { this.processTaskList = processTaskList; }
    public boolean isMerged() { return isMerged; }
    public void setMerged(boolean merged) { isMerged = merged; }
    public JeeflowEngine getEngine() { return engine; }
    public void setEngine(JeeflowEngine engine) { this.engine = engine; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public NodeModel getNodeModel() { return nodeModel; }
    public void setNodeModel(NodeModel nodeModel) { this.nodeModel = nodeModel; }
}
