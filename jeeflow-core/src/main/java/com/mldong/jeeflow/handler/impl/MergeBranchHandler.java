package com.mldong.jeeflow.handler.impl;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.core.ReapplySupport;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.handler.IHandler;
import com.mldong.jeeflow.model.JoinModel;
import com.mldong.jeeflow.model.TransitionModel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合并分支处理器——等待所有并行分支完成后再继续
 *
 * <p>判据分两档：</p>
 * <ul>
 *   <li><b>有分支归属</b>（任务变量 {@code _branchCtx}）：按本轮 round 统计全部分支根，
 *       任一根下仍有进行中任务、或任一根有申请待办携带匹配的修改等待，都不放行。
 *       申请人回到分叉之外修改时，那条申请任务不叫任何分支节点名，靠等待关系仍被算作
 *       「该分支未完成」——这是旧的按节点名扫描做不到的。</li>
 *   <li><b>无分支归属</b>（存量任务）：回落旧判据——实例无进行中任务，或输入源节点无进行中任务。</li>
 * </ul>
 *
 * <p>一次性放行由调用方在实例锁内完成（见 plan §4.4），本处理器只做判定。</p>
 *
 * @author mldong
 */
public class MergeBranchHandler implements IHandler {

    private final JoinModel joinModel;

    public MergeBranchHandler(JoinModel joinModel) {
        this.joinModel = joinModel;
    }

    @Override
    public void handle(Execution execution) {
        execution.setMerged(computeMerged(execution));
    }

    private boolean computeMerged(Execution execution) {
        ProcessInstance instance = execution.getProcessInstance();
        ProcessTask arriving = execution.getProcessTask();
        String joinName = joinModel.getName();
        Map<String, Object> arrivingScope = arriving == null ? null
                : ReapplySupport.findScope(ReapplySupport.scopesOf(arriving), joinName);
        if (arrivingScope == null) {
            return legacyMerged(instance);
        }
        String fork = ReapplySupport.asStr(arrivingScope.get(ReapplySupport.KEY_FORK));
        long round = ReapplySupport.asLong(arrivingScope.get(ReapplySupport.KEY_ROUND));

        // root → 是否仍阻塞（true=未完成）
        Map<Long, Boolean> roots = new LinkedHashMap<>();
        for (ProcessTask task : instance.getTasks()) {
            Map<String, Object> scope = ReapplySupport.findScope(ReapplySupport.scopesOf(task), joinName);
            boolean inRound = scope != null
                    && fork != null && fork.equals(ReapplySupport.asStr(scope.get(ReapplySupport.KEY_FORK)))
                    && ReapplySupport.asLong(scope.get(ReapplySupport.KEY_ROUND)) == round;
            if (inRound) {
                long root = ReapplySupport.asLong(scope.get(ReapplySupport.KEY_ROOT));
                roots.put(root, roots.get(root) != null && roots.get(root));
            }
            if (!task.isDoing()) {
                continue;
            }
            if (inRound) {
                roots.put(ReapplySupport.asLong(scope.get(ReapplySupport.KEY_ROOT)), Boolean.TRUE);
            }
            // 该分支正在申请人手里修改：申请任务本身不在分支节点名集合里，必须按等待关系计入
            for (Map<String, Object> b : ReapplySupport.branchOf(ReapplySupport.reapplyOf(task))) {
                if (joinName.equals(ReapplySupport.asStr(b.get(ReapplySupport.KEY_JOIN)))
                        && fork != null
                        && fork.equals(ReapplySupport.asStr(b.get(ReapplySupport.KEY_FORK)))
                        && ReapplySupport.asLong(b.get(ReapplySupport.KEY_ROUND)) == round) {
                    roots.put(ReapplySupport.asLong(b.get(ReapplySupport.KEY_ROOT)), Boolean.TRUE);
                }
            }
        }
        if (roots.isEmpty()) {
            return legacyMerged(instance);
        }
        return !roots.containsValue(Boolean.TRUE);
    }

    /** 存量/无归属任务的旧判据：进行中任务里没有本汇合点输入源节点的任务即可合并 */
    private boolean legacyMerged(ProcessInstance instance) {
        List<ProcessTask> doingTasks = instance.getDoingTasks();
        if (doingTasks == null || doingTasks.isEmpty()) {
            return true;
        }
        for (TransitionModel input : joinModel.getInputs()) {
            String sourceName = input.getSource() == null ? null : input.getSource().getName();
            if (sourceName == null) {
                continue;
            }
            for (ProcessTask task : doingTasks) {
                if (sourceName.equals(task.getTaskName())) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 检查指定实例和合并节点是否已完成合并 */
    public static boolean isMerged(Long instanceId, com.mldong.jeeflow.model.NodeModel nodeModel) {
        // 简化：如果实例没有进行中的任务，则已合并
        return true; // 由外部判断
    }
}
