package com.mldong.jeeflow.spi;

/**
 * 仓储可选的实例级事务排他保护（spec 04 · 并发保护）。
 *
 * <p>数据库仓储以实例行锁实现（MySQL/PostgreSQL {@code SELECT … FOR UPDATE}），
 * 锁随最外层事务提交/回滚释放；必须在事务内调用。未实现本接口的仓储退化为引擎进程内互斥，
 * 此时不得宣称跨进程自动/人工互斥。</p>
 */
public interface IInstanceLockRepository {

    void lockInstance(Long instanceId);
}
