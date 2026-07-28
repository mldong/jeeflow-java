package com.mldong.jeeflow.core;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;

import java.util.List;

/**
 * 工作流引擎接口
 *
 * @author mldong
 */
public interface JeeflowEngine {

    /** 配置引擎 */
    JeeflowEngine configure(Configuration config);

    /** 启动流程实例 */
    ProcessInstance startProcessInstanceById(Long defineId, String operator, FlowData args);

    /** 启动流程实例（子流程） */
    ProcessInstance startProcessInstanceById(Long defineId, String operator, FlowData args,
                                              Long parentId, String parentNodeName);

    /** 执行流程任务 */
    List<ProcessTask> executeProcessTask(Long taskId, String operator, FlowData args);

    /** 执行并跳转到指定节点 */
    List<ProcessTask> executeAndJumpTask(Long taskId, String operator, FlowData args, String nodeName);

    /** 执行并跳转到结束节点 */
    List<ProcessTask> executeAndJumpToEnd(Long taskId, String operator, FlowData args);

    /** 执行并跳转到第一个任务节点 */
    List<ProcessTask> executeAndJumpToFirstTaskNode(Long taskId, String operator, FlowData args);

    /** 获取仓储 */
    com.mldong.jeeflow.spi.IProcessRepository getRepository();
}
