package com.mldong.jeeflow.handler.impl;

import com.mldong.jeeflow.core.AddSignSupport;
import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.core.ReapplySupport;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.handler.IHandler;
import com.mldong.jeeflow.model.TaskModel;
import com.mldong.jeeflow.util.FlowUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 后加签（可重复）处理器——决定本节点的推进闸（merged），并在点了人时同节点续建任务。
 *
 * <p>判据顺序不可反（规范 04 · 后加签）：</p>
 * <ol>
 *   <li>本次给了加签人 → 同节点建单，{@code merged=false}；</li>
 *   <li>没给但本节点还有未完成任务（多选时同圈的兄弟待办）→ {@code merged=false}；</li>
 *   <li>两者皆否 → {@code merged=true}，由 {@link TaskModel#exec} 放行出边。</li>
 * </ol>
 *
 * <p>形状基准＝串行会签的续建腿（{@code CountersignHandler#createNextCountersignTask}），
 * 但走 {@link ProcessInstance#createTask} 而非裸 {@code ProcessTask.create}：那条腿内部已按节点
 * {@code expireTime} 表达式同尺写 {@code expire_time}，加签腿因此<b>不新增</b>建单写点。</p>
 *
 * <p>报错类判据不在此（互斥／开关未开／加给自己／超上限由 {@link AddSignSupport#validateSubmission}
 * 在改动状态之前拦下）——本处理器只负责建单与裁决，进到这里的提交都已判定合法。</p>
 *
 * @author mldong
 */
public class AddSignHandler implements IHandler {

    private final TaskModel taskModel;

    public AddSignHandler(TaskModel taskModel) {
        this.taskModel = taskModel;
    }

    @Override
    public void handle(Execution execution) {
        ProcessInstance instance = execution.getProcessInstance();
        ProcessTask completed = execution.getProcessTask();
        String node = taskModel.getName();
        // 只认本次请求点名的办理人（Execution#addSignOperators 由引擎从请求参数带入，
        // 不读 execution.getArgs()——那里混着实例变量，会把上一轮的 tf_addSignOperators 复活）
        List<String> operators = execution.getAddSignOperators();

        if (operators.isEmpty()) {
            // 停止条件即本判据本身：没点人且本节点再无未完成待办 ⇒ 放行出边
            execution.setMerged(!AddSignSupport.hasDoingOnNode(instance, node));
            return;
        }

        int round = AddSignSupport.roundOf(completed, node) + 1;
        boolean firstTaskNode = FlowUtil.isFirstTaskName(execution.getProcessModel(), node);
        for (String actor : operators) {
            ProcessTask next = instance.createTask(
                    taskModel,
                    taskModel.getDisplayName(),
                    new ArrayList<>(Collections.singletonList(actor)),
                    execution.getOperator(),
                    execution.getProcessTaskId(),
                    firstTaskNode
            );
            next.getVariables().put(FlowConst.ADD_SIGN_ROUND + "_" + node, round);
            // 分支归属与每一次普通建单同一条腿：并行范围内的加签任务必须继承父任务归属，
            // 否则汇合判据读不到这一支（缺省按父任务血缘＋模型静态范围推导）。
            ReapplySupport.putScopes(next, ReapplySupport.buildScopes(
                    completed, execution.getProcessModel(), node, next.getTaskId()));
            execution.addTask(next);
        }
        AddSignSupport.appendHistory(completed, execution.getOperator(), operators, round);
        execution.setMerged(false);
    }
}
