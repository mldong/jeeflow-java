package com.mldong.jeeflow.core;

import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.ProcessTaskStateEnum;
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

    public void addTask(ProcessTask task) {
        this.processTaskList.add(task);
    }

    public void addTasks(List<ProcessTask> tasks) {
        this.processTaskList.addAll(tasks);
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
