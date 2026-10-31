package com.mldong.jeeflow.core;

import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.spi.IProcessRepository;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 同实例串行化锁。
 *
 * <p>汇合一次性放行、同一任务重复办理、退回与另一支汇合交错，都必须在同一临界区里
 * 「重验状态 → 建后继任务」。JDBC 仓储当前每次调用各开一条连接（不在事务模板的连接上），
 * 行级 {@code FOR UPDATE} 无法跨调用持有，因此引擎侧用<b>按实例的进程内互斥</b>兜住
 * 单进程部署与全部本地/测试场景；多进程部署需要仓储侧提供真正的分布式锁
 * （spec 04-engine-ops「退回修改、重提直返」并发条款）。</p>
 *
 * @author mldong
 */
public final class InstanceLock {

    private static final ConcurrentHashMap<String, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

    private InstanceLock() {
    }

    /** 按任务所属实例取锁；任务不存在时退化为实例 0 的锁（无副作用） */
    public static ReentrantLock forTask(IProcessRepository repository, Long taskId) {
        Long instanceId = null;
        if (repository != null && taskId != null) {
            ProcessTask task = repository.findTaskById(taskId);
            if (task != null) {
                instanceId = task.getProcessInstanceId();
            }
        }
        return LOCKS.computeIfAbsent("i:" + (instanceId == null ? 0L : instanceId),
                k -> new ReentrantLock());
    }
}
