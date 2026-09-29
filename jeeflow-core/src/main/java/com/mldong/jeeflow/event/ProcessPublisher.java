package com.mldong.jeeflow.event;

import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.enums.ProcessEventTypeEnum;

import java.util.List;
import java.util.logging.Logger;

/**
 * 流程事件发布者
 *
 * <p>兜底语义（issues/104 P2 统一口径）：单监听器异常只记 warn 不传播——
 * 不得影响引擎主流程，也不得中断后续监听器（对齐 PHP v1.3.8 per-listener
 * catch Throwable；Go/Node/Python/Rust 同批对齐）。</p>
 *
 * <p>载荷键名规范（规范 11 §11.3）：直传载荷键一律 camelCase，见本类 {@code KEY_*} 常量。
 * 键之外的字段允许监听器按 {@code sourceId} 反查仓储得到（任务名/流程名/发起人）。</p>
 *
 * @author mldong
 */
public final class ProcessPublisher {

    /** JUL 而非 slf4j：core 零外部运行时依赖（slf4j 为 provided，部分消费方无绑定）。 */
    private static final Logger log = Logger.getLogger(ProcessPublisher.class.getName());

    // ═══ 直传载荷键（spec §11.3「直传载荷键（必备）」列，一律 camelCase）═══

    /** 实例 id（码 1/2/3/5/6/7/8/9） */
    public static final String KEY_INSTANCE_ID = "instanceId";
    /** 任务 id（码 3/5/6/7） */
    public static final String KEY_TASK_ID = "taskId";
    /** 待办参与人列表（码 3） */
    public static final String KEY_ACTORS = "actors";
    /** 落库后的实例状态整数（码 2） */
    public static final String KEY_STATE = "state";
    /** 本次动作的操作人（码 5/6/7/8/9） */
    public static final String KEY_OPERATOR = "operator";
    /** 本次动作的提交类型，用于在"码粗载荷细"下区分拒绝/退回/退发起人/会签否决（码 5/6） */
    public static final String KEY_SUBMIT_TYPE = "submitType";
    /** 转办被摘走的参与人（码 7） */
    public static final String KEY_FROM_ACTOR = "fromActor";
    /** 转办接手的参与人（码 7） */
    public static final String KEY_TO_ACTOR = "toActor";
    /** 终止原因（码 9） */
    public static final String KEY_REASON = "reason";

    private ProcessPublisher() {}

    public static void notify(ProcessEvent event) {
        List<ProcessEventListener> listeners = ServiceContext.findList(ProcessEventListener.class);
        if (listeners != null) {
            for (ProcessEventListener listener : listeners) {
                try {
                    listener.onEvent(event);
                } catch (Exception e) {
                    log.warning(String.format(
                            "process event listener error: eventType=%s, sourceId=%s, listener=%s: %s",
                            event.getEventType(), event.getSourceId(),
                            listener.getClass().getName(), e));
                }
            }
        }
    }

    /**
     * 抄送知会（{@link ProcessEventTypeEnum#CC_CREATE} / 码 4）fire 收口：<b>逐抄送人</b> fire 一次，
     * {@code sourceId = instanceId}、{@code ccActorId} 直传事件体（监听器免反查 cc 表）。
     *
     * <p>三条路径共用本方法（spec §11.3 码 4 ＋ §11.7）：发起 {@code f_ccActors}、办理
     * {@code tf_ccActors}（{@code JeeflowEngineImpl#handleCcActors}）、门面手动
     * {@code processInstance/createCCInstance}。§11.2 原则 1「码值表达发生了什么事实，
     * 不表达谁触发的」⇒ 手动支同样必须 fire（"新增了一条抄送记录"这个事实成立）；
     * java 此前只在引擎侧 fire、手动支静默，是全联邦里"不 fire"那一派（§11.6 点名本案
     * 唯一一处基准要向 go/py/node 学）。</p>
     *
     * <p>调用前提（§11.2 原则 3）：cc 行已由 {@code IProcessRepository#createCcInstance} 落库。
     * 接收人过滤（trim / 非空 / 纯数字 / 去重）由集成层监听器负责，引擎只按 cc 行粒度 fire。</p>
     */
    public static void notifyCcCreate(Long instanceId, String[] ccActorIds) {
        if (instanceId == null || ccActorIds == null) {
            return;
        }
        for (String ccActorId : ccActorIds) {
            notify(ProcessEvent.builder()
                    .eventType(ProcessEventTypeEnum.CC_CREATE)
                    .sourceId(instanceId)
                    .ccActorId(ccActorId)
                    .data(FlowData.create().set(KEY_INSTANCE_ID, instanceId))
                    .build());
        }
    }

    /**
     * 实例终态（{@link ProcessEventTypeEnum#PROCESS_INSTANCE_END} / 码 2）fire 收口：
     * {@code sourceId}＝instanceId，直传载荷键 {@code instanceId} ＋ {@code state}。
     * 办结与拒绝共用这一支，规范名不拆，靠载荷 state 分（§11.3 码 2／§11.6 收口口径）。
     *
     * <p><b>调用前提</b>（§11.2 原则 3／08-compliance 场景 32）：实例那一行的 {@code state}
     * <b>已经落库</b>。java 的唯一调用点＝{@code JeeflowEngineImpl#flushInstanceEndEvents}，
     * 排在 {@code repository.updateInstance} 成功返回之后；子流程级联里被连带办结的
     * <b>父实例</b>不走那次 update，flush 先补写它自己的行再播。</p>
     *
     * @param state 落库后的实例状态整数（处理器落定时刻的快照，见 {@link PendingInstanceEnd}）
     */
    public static void notifyInstanceEnd(Long instanceId, Integer state) {
        if (instanceId == null) {
            return;
        }
        notify(ProcessEvent.builder()
                .eventType(ProcessEventTypeEnum.PROCESS_INSTANCE_END)
                .sourceId(instanceId)
                .data(FlowData.create()
                        .set(KEY_INSTANCE_ID, instanceId)
                        .set(KEY_STATE, state))
                .build());
    }
}
