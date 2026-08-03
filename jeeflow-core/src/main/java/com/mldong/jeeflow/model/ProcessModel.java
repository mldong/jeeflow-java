package com.mldong.jeeflow.model;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.interceptor.Action;
import com.mldong.jeeflow.interceptor.FlowInterceptor;
import com.mldong.jeeflow.interceptor.CandidateHandler;
import com.mldong.jeeflow.spi.IOrgUserProvider;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.Candidate;
import com.mldong.jeeflow.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 流程模型——整个流程定义的顶层对象
 *
 * @author mldong
 */
public class ProcessModel extends BaseModel {

    private String type;
    private String instanceUrl;
    private String expireTime;
    private String instanceNoClass;
    private String preInterceptors;
    private String postInterceptors;
    private String relTableName;
    private FlowData ext = FlowData.create();
    private List<NodeModel> nodes = new ArrayList<>();
    private List<TaskModel> tasks = new ArrayList<>();

    /** 获取开始节点 */
    public StartModel getStart() {
        for (NodeModel node : nodes) {
            if (node instanceof StartModel) return (StartModel) node;
        }
        return null;
    }

    /** 根据名称获取节点 */
    public NodeModel getNode(String nodeName) {
        for (NodeModel node : nodes) {
            if (node.getName().equals(nodeName)) return node;
        }
        return null;
    }

    /** 获取下一个任务节点模型集合 */
    public List<TaskModel> getNextTaskModels(String nodeName) {
        List<TaskModel> res = new ArrayList<>();
        NodeModel node = getNode(nodeName);
        if (node == null) return res;
        for (TransitionModel tm : node.getOutputs()) {
            NodeModel target = tm.getTarget();
            if (target instanceof TaskModel) {
                res.add((TaskModel) target);
            }
        }
        if (res.isEmpty()) {
            for (TransitionModel tm : node.getOutputs()) {
                res.addAll(getNextTaskModels(tm.getTarget().getName()));
            }
        }
        return res;
    }

    /** 获取下一个任务节点的候选人 */
    public List<Candidate> getNextTaskModelCandidates(String nodeName) {
        List<Candidate> res = new ArrayList<>();
        for (TaskModel tm : getNextTaskModels(nodeName)) {
            res.addAll(getCandidates(tm));
        }
        return res;
    }

    /** 根据任务模型获取候选人 */
    @SuppressWarnings("unchecked")
    public List<Candidate> getCandidates(TaskModel taskModel) {
        List<Candidate> res = new ArrayList<>();
        List<CandidateHandler> handlers = ServiceContext.findList(CandidateHandler.class);
        if (handlers != null) {
            for (CandidateHandler handler : handlers) {
                List<Candidate> candidates = handler.handle(taskModel);
                if (candidates != null) res.addAll(candidates);
            }
        }
        String handlerClass = taskModel.getCandidateHandler();
        if (StringUtils.isNotEmpty(handlerClass)) {
            try {
                CandidateHandler handler = (CandidateHandler)
                        Class.forName(handlerClass.trim()).getDeclaredConstructor().newInstance();
                List<Candidate> candidates = handler.handle(taskModel);
                if (candidates != null) res.addAll(candidates);
            } catch (Exception ignored) {}
        }
        // 内置解析（v1.6.0，对齐 boot4 GlobalCandidateHandler 双源语义）：
        // ① candidateUsers —— 逗号分隔 userId，直接作为候选人
        String candidateUsers = taskModel.getCandidateUsers();
        if (StringUtils.isNotEmpty(candidateUsers)) {
            for (String userId : candidateUsers.split(",")) {
                String uid = userId.trim();
                if (!uid.isEmpty()) res.add(new Candidate(uid, uid, "user"));
            }
        }
        // ② candidateGroups —— 逗号分隔角色标识，IOrgUserProvider.findByRole 取人
        String candidateGroups = taskModel.getCandidateGroups();
        if (StringUtils.isNotEmpty(candidateGroups)) {
            IOrgUserProvider orgProvider = ServiceContext.find(IOrgUserProvider.class);
            if (orgProvider != null) {
                for (String roleCode : candidateGroups.split(",")) {
                    String rc = roleCode.trim();
                    if (rc.isEmpty()) continue;
                    List<String> userIds = orgProvider.findByRole(rc);
                    if (userIds != null) {
                        for (String uid : userIds) {
                            if (uid != null && !uid.isEmpty()) res.add(new Candidate(uid, uid, "user"));
                        }
                    }
                }
            }
        }
        return res.stream().distinct().collect(Collectors.toList());
    }

    /** 获取指定类型的所有节点 */
    @SuppressWarnings("unchecked")
    public <T> List<T> getModels(Class<T> clazz) {
        List<T> models = new ArrayList<>();
        StartModel start = getStart();
        if (start != null) {
            buildModels(models, start.getNextModels(clazz), clazz);
        }
        return models;
    }

    @SuppressWarnings("unchecked")
    private <T> void buildModels(List<T> models, List<T> nextModels, Class<T> clazz) {
        for (T nextModel : nextModels) {
            if (!models.contains(nextModel)) {
                models.add(nextModel);
                buildModels(models, ((NodeModel) nextModel).getNextModels(clazz), clazz);
            }
        }
    }

    // ---- getters/setters ----
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getInstanceUrl() { return instanceUrl; }
    public void setInstanceUrl(String instanceUrl) { this.instanceUrl = instanceUrl; }
    public String getExpireTime() { return expireTime; }
    public void setExpireTime(String expireTime) { this.expireTime = expireTime; }
    public String getInstanceNoClass() { return instanceNoClass; }
    public void setInstanceNoClass(String instanceNoClass) { this.instanceNoClass = instanceNoClass; }
    public String getPreInterceptors() { return preInterceptors; }
    public void setPreInterceptors(String preInterceptors) { this.preInterceptors = preInterceptors; }
    public String getPostInterceptors() { return postInterceptors; }
    public void setPostInterceptors(String postInterceptors) { this.postInterceptors = postInterceptors; }
    public String getRelTableName() { return relTableName; }
    public void setRelTableName(String relTableName) { this.relTableName = relTableName; }
    public FlowData getExt() { return ext; }
    public void setExt(FlowData ext) { this.ext = ext; }
    public List<NodeModel> getNodes() { return nodes; }
    public void setNodes(List<NodeModel> nodes) { this.nodes = nodes; }
    public List<TaskModel> getTasks() { return tasks; }
    public void setTasks(List<TaskModel> tasks) { this.tasks = tasks; }
}
