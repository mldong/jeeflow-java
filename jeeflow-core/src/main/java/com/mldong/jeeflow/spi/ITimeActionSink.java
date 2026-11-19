package com.mldong.jeeflow.spi;

/**
 * 时限动作凭证的仓储侧实现（spec 04 · 动作行契约）：由集成层按框架执行队列实现，
 * 引擎在业务事务内回调，实现方必须复用同一事务连接。
 *
 * <p>未注册实现时引擎侧全部时限动作静默跳过，存量使用方行为零变化。</p>
 */
public interface ITimeActionSink {

    /** 插入 pending 动作行；同一 taskId+type+occurrenceNo 已存在时跳过（单写者＋插前查）。 */
    void insertTimeAction(TimeAction action);

    /** 删除该任务 pending 的 REMIND/AUTO_COMPLETE 行（CALLBACK 表达既成事实，不随失效取消）。 */
    void cancelTaskTimeActions(Long taskId);

    /** 删除该实例 pending 的 REMIND/AUTO_COMPLETE 行（实例终态）。 */
    void cancelInstanceTimeActions(Long instanceId);
}
