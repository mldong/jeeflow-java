package com.mldong.jeeflow.model;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.handler.impl.CreateTaskHandler;
import com.mldong.jeeflow.handler.impl.StartSubProcessHandler;
import com.mldong.jeeflow.interceptor.Action;

/**
 * 边/转移模型——连接两个节点的有向边
 *
 * @author mldong
 */
public class TransitionModel extends BaseModel implements Action {

    private NodeModel source;
    private NodeModel target;
    private String to;
    private String expr;
    private String g;
    private boolean enabled;

    @Override
    public void execute(Execution execution) {
        if (!enabled) return;
        if (target instanceof TaskModel) {
            fire(new CreateTaskHandler((TaskModel) target), execution);
        } else if (target instanceof SubProcessModel) {
            fire(new StartSubProcessHandler((SubProcessModel) target), execution);
        } else {
            target.execute(execution);
        }
    }

    // ---- getters/setters ----
    public NodeModel getSource() { return source; }
    public void setSource(NodeModel source) { this.source = source; }
    public NodeModel getTarget() { return target; }
    public void setTarget(NodeModel target) { this.target = target; }
    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }
    public String getExpr() { return expr; }
    public void setExpr(String expr) { this.expr = expr; }
    public String getG() { return g; }
    public void setG(String g) { this.g = g; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
