package com.mldong.jeeflow.test;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.interceptor.AssignmentHandler;

/**
 * issues/244 ② 夹具：一个解析不到任何人的参与者处理器（返回空串）。
 *
 * <p>用途：串行会签节点只挂 {@code assignmentHandler}、不写 {@code assignee}，
 * 让 {@code CreateTaskHandler#resolveActors} 交出零人名单，从而走到
 * {@code ProcessInstance#createCountersignTasks} 的 SEQUENTIAL 分支。</p>
 */
public class EmptyAssigneeHandler244 implements AssignmentHandler {

    @Override
    public String assign(Execution execution) {
        return "";
    }
}
