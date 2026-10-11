package com.mldong.jeeflow.core;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.spi.ITransactionTemplate;

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

    /**
     * 在事务中执行一段动作（spec/12 §12.2：事务边界＝一次动作；§12.1 原则 3「有则用、无则裸跑」）。
     *
     * <p>门面 {@code flow()} 用它把一次 action 的**全部**写入收进同一事务——含门面自组的复合腿
     * （startAndExecute 的 start＋参与者写＋execute、redeploy 的 define＋design 两表等）。
     * 引擎命令入口内部的包裹会嵌套并入本层（Spring REQUIRED）。未注册
     * {@link ITransactionTemplate} 时直接执行＝显式降级（§12.1 原则 4），行为与旧版一致。</p>
     */
    default <T> T inTx(ITransactionTemplate.Supplier<T> action) throws Exception {
        return action.get();
    }
}
