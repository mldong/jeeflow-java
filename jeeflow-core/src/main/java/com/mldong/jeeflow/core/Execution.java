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
