package com.mldong.jeeflow.model;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.interceptor.Action;
import com.mldong.jeeflow.interceptor.FlowInterceptor;
import com.mldong.jeeflow.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 节点模型抽象基类
 *
 * @author mldong
 */
public abstract class NodeModel extends BaseModel implements Action {

    private String layout;
    private List<TransitionModel> inputs = new ArrayList<>();
    private List<TransitionModel> outputs = new ArrayList<>();
    private String preInterceptors;
    private String postInterceptors;

    /** 子类实现具体执行逻辑 */
    abstract void exec(Execution execution);

    @Override
    public void execute(Execution execution) {
        execution.setNodeModel(this);
        execPreInterceptors(execution);
        exec(execution);
        // 流转链中 CreateTaskHandler 等会改写 nodeModel（标记"当前创建的任务节点"），
        // post 拦截器必须看到的是本节点——重新设置（1.8.0：字段权限/状态字段按节点判定）
        execution.setNodeModel(this);
        execPostInterceptors(execution);
    }

    /** 执行所有输出边 */
    protected void runOutTransition(Execution execution) {
        for (TransitionModel tr : outputs) {
            tr.setEnabled(true);
            tr.execute(execution);
        }
    }

    private void execPreInterceptors(Execution execution) {
        String interceptors = preInterceptors;
        if (StringUtils.isEmpty(interceptors)) {
            interceptors = execution.getProcessModel().getPreInterceptors();
        }
        runInterceptors(interceptors, execution);
    }

    private void execPostInterceptors(Execution execution) {
        String interceptors = postInterceptors;
        if (StringUtils.isEmpty(interceptors)) {
            interceptors = execution.getProcessModel().getPostInterceptors();
        }
        runInterceptors(interceptors, execution);
    }

    private void runInterceptors(String interceptors, Execution execution) {
        if (StringUtils.isEmpty(interceptors)) return;
        for (String className : interceptors.split(",")) {
            try {
                FlowInterceptor interceptor = (FlowInterceptor)
                        Class.forName(className.trim()).getDeclaredConstructor().newInstance();
                if (interceptor != null) {
                    interceptor.intercept(execution);
                }
            } catch (Exception e) {
                throw new RuntimeException("无法实例化拦截器: " + className, e);
            }
        }
    }

    /** 获取后续指定类型的所有节点模型 */
    @SuppressWarnings("unchecked")
    public <T> List<T> getNextModels(Class<T> clazz) {
        List<T> models = new ArrayList<>();
        Map<String, Object> visited = new LinkedHashMap<>();
        for (TransitionModel tm : this.outputs) {
            addNextModels(models, tm, clazz, visited);
        }
        return models;
    }

    @SuppressWarnings("unchecked")
    protected <T> void addNextModels(List<T> models, TransitionModel tm, Class<T> clazz, Map<String, Object> visited) {
        if (visited.containsKey(tm.getTo())) return;
        if (clazz.isInstance(tm.getTarget())) {
            models.add((T) tm.getTarget());
        } else {
            for (TransitionModel tm2 : tm.getTarget().getOutputs()) {
                visited.put(tm.getTo(), tm.getTarget());
                addNextModels(models, tm2, clazz, visited);
            }
        }
    }

    /**
     * 判断 current 是否可以退回到 parent
     */
    public static boolean canRejected(NodeModel current, NodeModel parent) {
        boolean result = false;
        for (TransitionModel tm : current.getInputs()) {
            NodeModel source = tm.getSource();
            if (source == parent) return true;
            if (source instanceof ForkModel
                    || source instanceof JoinModel
                    || source instanceof StartModel) {
                continue;
            }
            result = result || canRejected(source, parent);
        }
        return result;
    }

    @Override
    public String toString() {
        return "调用模型节点执行方法：model:" + this.getClass().getSimpleName()
                + ", name:" + getName() + ", displayName:" + getDisplayName();
    }

    // ---- getters/setters ----

    public String getLayout() { return layout; }
    public void setLayout(String layout) { this.layout = layout; }
    public List<TransitionModel> getInputs() { return inputs; }
    public void setInputs(List<TransitionModel> inputs) { this.inputs = inputs; }
    public List<TransitionModel> getOutputs() { return outputs; }
    public void setOutputs(List<TransitionModel> outputs) { this.outputs = outputs; }
    public String getPreInterceptors() { return preInterceptors; }
    public void setPreInterceptors(String preInterceptors) { this.preInterceptors = preInterceptors; }
    public String getPostInterceptors() { return postInterceptors; }
    public void setPostInterceptors(String postInterceptors) { this.postInterceptors = postInterceptors; }
}
