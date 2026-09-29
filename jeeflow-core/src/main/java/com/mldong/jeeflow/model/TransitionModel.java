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

    /** 落穿记录的 logger（类名即 logger 名，用例按 {@code TransitionModel} 收流） */
    private static final java.util.logging.Logger log =
            java.util.logging.Logger.getLogger(TransitionModel.class.getName());

    @Override
    public void execute(Execution execution) {
        if (!enabled) return;
        // issues/143：出边的目标节点不在模型里 ⇒ 这条边**落穿**（记一条可诊断日志后不推进），
        // 严禁裸调 target.execute()。
        //
        // 什么时候会为 null：解析期类型表查不到解析器（G4 义务 2 的未知档）时记一条 WARNING 后
        // continue，节点不进模型（ModelParser.java:99-109），而**指向它的边**仍挂在上游节点的
        // outputs 上——setTarget 只在目标节点存在于模型里时才发生（ModelParser.java:100-108）。
        // 令牌走到上游节点并办结 ⇒ runOutTransition 逐条 execute ⇒ 原形状在这里 NPE：
        // 一次配置写错（大小写、拼错、设计器脏数据）把整次办理打崩，正面违反
        // spec 02 §6.2 第 2 条「严禁抛错打断建单」（owner 2026-09-30 拍）与 spec 04
        // 「节点属性配错不该把流程炸掉」。php 会话在 issues/142 A 批普查时先撞出来，
        // 本栈逐字同形（php c71d72e／c# 同批跟改）。
        //
        // 为什么选「停住」而不是「越过它继续」：未知档没被解析成任何模型，越过它等于用一条
        // 臆造的通路把跑不通的定义跑成功，用户面更难发现；停住＋一条带 from/to 的 WARNING 才可诊断，
        // 且与按 id 现查目标的那几栈可观测结果一致（go findNode!=nil / python / node / moon option /
        // rust 把未知节点留在模型里标 Unknown 再由执行腿跳过）——实例留 DOING、库里不产生越过它的行。
        if (target == null) {
            log.warning("转移出边的目标节点不在模型里（该节点类型未建档，解析期已跳过），"
                    + "本次流转停在这条边: from=" + (source != null ? source.getName() : "null")
                    + ", to=" + to);
            return;
        }
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
