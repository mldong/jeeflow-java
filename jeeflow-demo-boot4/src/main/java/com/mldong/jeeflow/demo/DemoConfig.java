package com.mldong.jeeflow.demo;

import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.facade.JeeflowFacade;
import com.mldong.jeeflow.repository.JdbcProcessExtRepository;
import com.mldong.jeeflow.spi.IProcessRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 演示站门面装配——接入扩展仓储与用户搜索钩子
 *
 * <p>扩展仓储用 JDBC 实现（复用 H2 内存数据源），design/surrogate 类 action 可用；
 * 用户搜索钩子让 candidatePage 的"无模型候选→用户分页搜索"在 demo 内闭环。</p>
 */
@Configuration(proxyBeanMethods = false)
public class DemoConfig {

    @Bean
    public JeeflowFacade jeeflowFacade(JeeflowEngine engine,
                                       IProcessRepository repository,
                                       DataSource dataSource,
                                       DemoUserSearchProvider userSearchProvider) {
        return new JeeflowFacade(engine, repository, new JdbcProcessExtRepository(dataSource))
                .setUserSearchProvider(userSearchProvider);
    }
}
