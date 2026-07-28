package com.mldong.jeeflow.spi;

/**
 * 事务模板 SPI（可选）
 *
 * <p>如果注册，引擎的每个命令（启动/完成任务等）在此事务边界内执行。
 * 若不注册，引擎裸执行（适合内存测试）。</p>
 *
 * @author mldong
 */
public interface ITransactionTemplate {

    /** 在事务中执行 */
    <T> T execute(Supplier<T> action);

    /** 在事务中执行（无返回值） */
    default void execute(Runnable action) {
        execute(() -> {
            action.run();
            return null;
        });
    }

    /** 函数式接口 */
    @FunctionalInterface
    interface Supplier<T> {
        T get() throws Exception;
    }
}
