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
        runInterceptors(effectiveInterceptors(preInterceptors,
                execution.getProcessModel().getPreInterceptors()), execution);
    }

    /**
     * pre/post 拦截器串的<b>唯一</b>回落判据：节点级为空则取流程级。
     *
     * <p>执行期（{@link #execPreInterceptors}／{@link #execPostInterceptors}）与写前只读校验
     * （{@link #validateDeclaredInterceptors}）共用这一处，避免"校验看节点级、执行看流程级"
     * 这类两套判据各自演进的病。</p>
     */
    public static String effectiveInterceptors(String nodeLevel, String processLevel) {
        return StringUtils.isEmpty(nodeLevel) ? processLevel : nodeLevel;
    }

    /**
     * 本节点声明的 pre/post 拦截器类名<b>只读校验</b>（issues/244 ①，参考实现）。
     *
     * <p>判据在 {@link #validateInterceptors}（一处实现、只解析类名、不实例化）。引擎必须在
     * {@code ProcessInstance#completeTask} 之前调用本方法：内存仓储没有事务回滚，等到
     * {@code NodeModel#execute} 里 {@code execPostInterceptors} 才炸时，当前任务行已经被办结、
     * 后继任务又还没落库（{@code persistTasks} 排在 {@code node.execute} 之后）⇒ 实例保持
     * DOING(10) 却零待办，流程原地失联。真库靠入口外层事务整笔回滚，观察不到差别。</p>
     *
     * <p><b>为什么不是"再跑一次拦截器"</b>：{@link #runInterceptors} 会 {@code newInstance} 并
     * 调用 {@code intercept(execution)}——构造器与业务逻辑都有执行期副作用，预跑一遍等于让每个
     * 拦截器执行两次（还会污染 execution.args / 任务变量）。所以这里只做"能不能解析出可实例化的类"。</p>
     *
     * <p><b>能力边界（如实记）</b>：只覆盖三类——①类名解析不出来；②类没实现
     * {@link FlowInterceptor}；③没有无参构造器。<b>构造器自己抛异常</b>仍会在写之后炸，本轮不管；
     * 抽象类/非 public 构造器也不在本档判据内（那属于第四、第五类，要扩须另立判据）。</p>
     */
    public void validateDeclaredInterceptors(ProcessModel processModel) {
        validateInterceptors(effectiveInterceptors(preInterceptors, processModel.getPreInterceptors()));
        validateInterceptors(effectiveInterceptors(postInterceptors, processModel.getPostInterceptors()));
    }

    /**
     * 拦截器串的只读可实例化校验：逐类名 {@code Class.forName} ＋ 断言实现
     * {@link FlowInterceptor} ＋ 存在无参构造器，<b>不调用构造器</b>。
     *
     * @throws RuntimeException 文案与执行期逐字相同（{@code "无法实例化拦截器: " + 原始串元素}），
     *                          cause 保留底层反射异常——外部出口与日志都不变
     */
    public static void validateInterceptors(String interceptors) {
        if (StringUtils.isEmpty(interceptors)) return;
        for (String className : interceptors.split(",")) {
            try {
                Class<?> clazz = Class.forName(className.trim());
                if (!FlowInterceptor.class.isAssignableFrom(clazz)) {
                    throw new IllegalArgumentException(
                            "类[" + className.trim() + "]未实现 FlowInterceptor 接口");
                }
                // 只查声明、不调用：getDeclaredConstructor() 找不到时抛 NoSuchMethodException
                clazz.getDeclaredConstructor();
            } catch (Exception e) {
                throw new RuntimeException("无法实例化拦截器: " + className, e);
            }
        }
    }

    /**
     * 仅供引擎在"不执行节点出边、但已办结该节点任务"的路径（退回直返/重提）上调用，
     * 保持 post 拦截器语义；不得在正常 execute 流程里额外调用（会重复执行一次）。
     */
    public void executePostInterceptors(Execution execution) {
        execution.setNodeModel(this);
        execPostInterceptors(execution);
    }

    private void execPostInterceptors(Execution execution) {
        runInterceptors(effectiveInterceptors(postInterceptors,
                execution.getProcessModel().getPostInterceptors()), execution);
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
