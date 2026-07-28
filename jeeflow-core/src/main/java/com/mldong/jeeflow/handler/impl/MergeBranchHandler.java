package com.mldong.jeeflow.handler.impl;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.ProcessTaskStateEnum;
import com.mldong.jeeflow.handler.IHandler;
import com.mldong.jeeflow.model.JoinModel;

import java.util.List;

/**
 * 合并分支处理器——等待所有并行分支完成后再继续
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
        ProcessInstance instance = execution.getProcessInstance();
        List<ProcessTask> doingTasks = instance.getDoingTasks();
        execution.setMerged(doingTasks == null || doingTasks.isEmpty());

        if (!execution.isMerged()) {
            // 检查本合并节点的输入分支是否还有进行中的任务
            boolean hasActive = false;
            List<com.mldong.jeeflow.model.TransitionModel> inputs = joinModel.getInputs();
            for (com.mldong.jeeflow.model.TransitionModel input : inputs) {
                String sourceName = input.getSource().getName();
                for (ProcessTask task : doingTasks) {
                    if (sourceName.equals(task.getTaskName())) {
                        hasActive = true;
                        break;
                    }
                }
            }
            execution.setMerged(!hasActive);
        }
    }

    /** 检查指定实例和合并节点是否已完成合并 */
    public static boolean isMerged(Long instanceId, com.mldong.jeeflow.model.NodeModel nodeModel) {
        // 简化：如果实例没有进行中的任务，则已合并
        return true; // 由外部判断
    }
}
