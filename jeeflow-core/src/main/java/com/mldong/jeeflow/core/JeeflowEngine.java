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

    /**
     * 已办撤回（issues/246，规范 04 · 已办撤回）：办理人撤回自己已办结的那一手，同实例内复活重办。
     *
     * @return 复活的新任务行（DOING，唯一活待办）
     */
    ProcessTask recallTask(Long taskId, String operator);

    /**
     * 动态协办（issues/253，规范 04 · 动态协办）：主办把"这活"交给别人干——按人派生
     * {@code task_type=1} 的协办子行（parent＝主办行、同节点同名、performType=NORMAL），
     * <b>主办行原样保留 DOING</b>；协办行办结不驱动流程，节点推进仍由主办行办结触发。
     *
     * <p>判据全排前（报错即零写入）：任务存在 → 禁递归（协办行不能再派生）→ 主办行 DOING →
     * 名册鉴权（{@code flow.auto}/{@code flow.admin} 例外）→ 逐人去重（该人已有未办结协办行）。
     * 建单走 {@code saveNewTask} 既有腿（委托并入＋落库＋fire 码 3），不新增事件码。</p>
     *
     * @param taskId         主办行任务 id
     * @param actorIds       协办人（已归一，每人一行）
     * @param reason         派生原因（选填，进账本＋盖每条协办行 tf_aidantReason）
     * @param waitCompletion true＝等待闸（主办办结前判无 DOING 协办行）；false（缺省）＝不等
     * @param operator       操作人（硬必填，门面已校验）
     * @return 本次派生的协办行（DOING）
     */
    List<ProcessTask> aidantTask(Long taskId, List<String> actorIds, String reason,
                                 boolean waitCompletion, String operator);

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
