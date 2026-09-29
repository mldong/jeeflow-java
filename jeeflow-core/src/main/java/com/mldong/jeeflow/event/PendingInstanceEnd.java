package com.mldong.jeeflow.event;

import com.mldong.jeeflow.domain.ProcessInstance;

/**
 * 待播的「实例终态」登记（spec §11.2 原则 3「只在落库之后 fire」／§11.3 码 2／08-compliance 场景 32）。
 *
 * <p><b>为什么要登记而不是就地 fire</b>：结束节点处理器（{@code EndProcessHandler}）跑到时，
 * 实例的 {@code state} 只在<b>内存聚合根</b>里落定，真正写库的那次
 * {@code repository.updateInstance} 发生在处理器<b>之后</b>（引擎 {@code persistTasks}／发起路径收口）。
 * 就地 fire 会让监听器（站内信、待办角标、persist 回写）反查实例读到旧 state——
 * 正是 issues/121、issues/126 两轮"回写序"教训的同一族。</p>
 *
 * <p>形状：处理器把「谁（instanceId）＋ 落库后该是什么（state）＋ 那一行的聚合根（instance）」
 * 挂到本次流转的 {@code Execution} 上，由引擎在 {@code updateInstance} 成功返回后统一 flush
 * （{@code JeeflowEngineImpl#flushInstanceEndEvents}）。{@code instance} 一起带上是必需的——
 * 子流程级联里被连带办结的是<b>父实例</b>，它不走子流程这次的 {@code updateInstance}，
 * flush 时要拿这个对象去补那次写。</p>
 *
 * @author mldong
 */
public final class PendingInstanceEnd {

    private final Long instanceId;
    private final Integer state;
    private final ProcessInstance instance;

    public PendingInstanceEnd(Long instanceId, Integer state, ProcessInstance instance) {
        this.instanceId = instanceId;
        this.state = state;
        this.instance = instance;
    }

    /** 事件 {@code sourceId} 与载荷 {@code instanceId} 用哪个实例的终态 */
    public Long getInstanceId() {
        return instanceId;
    }

    /** 处理器落定那一刻的实例状态整数（＝随后写库那一行的 state） */
    public Integer getState() {
        return state;
    }

    /** 终态所属的聚合根（引擎按它把父实例那一行补写落库） */
    public ProcessInstance getInstance() {
        return instance;
    }
}
