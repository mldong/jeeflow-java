package com.mldong.jeeflow.spring;

import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

import javax.sql.DataSource;

/**
 * JDBC 连接同源装配助手（规范 12 §12.2 覆盖面 / issues/145-1）。
 *
 * <p>jeeflow 的 JDBC 组件（{@code JdbcProcessRepository}/{@code JdbcProcessExtRepository}/
 * {@code JdbcDynamicTableWriter}/{@code JdbcTableReader}）都在构造时收一枚 {@link DataSource}，
 * 之后每条 SQL 自行取连接、用完关闭。若把原始 DataSource 直接交给它们：即便引擎命令入口
 * （{@code JeeflowEngineImpl.runInTx}）开着 Spring 事务，每条语句仍各自向池取新连接——
 * 事务空转，一次动作的多表写（实例/任务/参与者/业务表）不在同一事务里，
 * 中途失败会留下半完成态，回滚也撤不掉已写的行。</p>
 *
 * <p>包成 {@link TransactionAwareDataSourceProxy} 后：事务活跃时 {@code getConnection()}
 * 返回事务绑定连接、{@code close()} 被抑制（由事务管理器统一提交/回滚）；无事务时与原始
 * DataSource 行为一致。事务管理器（{@code DataSourceTransactionManager}）绑定的资源键仍是
 * 原始 DataSource，代理会以其为目标解析，因此两边必须收同一枚原始 DataSource。</p>
 *
 * <p><b>用法</b>：所有把 DataSource 递给上述 jeeflow 组件的装配点都先过
 * {@link #transactionAware(DataSource)}。幂等：收到已是代理的实例时原样返回。</p>
 */
public final class TransactionAwareDataSources {

    private TransactionAwareDataSources() {
    }

    public static DataSource transactionAware(DataSource dataSource) {
        if (dataSource == null || dataSource instanceof TransactionAwareDataSourceProxy) {
            return dataSource;
        }
        return new TransactionAwareDataSourceProxy(dataSource);
    }
}
