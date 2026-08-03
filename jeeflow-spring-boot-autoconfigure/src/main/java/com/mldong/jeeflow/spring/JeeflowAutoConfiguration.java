package com.mldong.jeeflow.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.json.IJsonProvider;
import com.mldong.jeeflow.repository.JdbcProcessRepository;
import com.mldong.jeeflow.spi.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * jeeflow Spring Boot 自动装配
 *
 * <p>自动注册所有 SPI 实现，创建 JeeflowEngine Bean。
 * 业务方只需注入 JeeflowEngine 即可使用，也可覆盖任意 SPI。</p>
 *
 * @author mldong
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(JeeflowEngine.class)
@ConditionalOnProperty(prefix = "jeeflow", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(JeeflowProperties.class)
public class JeeflowAutoConfiguration {

    /**
     * JSON 提供者 —— 内置 Jackson，不依赖 Boot 自动配置的 ObjectMapper
     */
    @Bean
    @ConditionalOnMissingBean
    public IJsonProvider jeeflowJsonProvider() {
        return new JacksonJsonProvider(new com.fasterxml.jackson.databind.ObjectMapper());
    }

    /**
     * ID 生成器 —— 雪花算法（可禁用，走 JdbcProcessRepository fallback）
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "jeeflow.snowflake", name = "enabled", havingValue = "true", matchIfMissing = true)
    public IIdGenerator jeeflowIdGenerator(JeeflowProperties properties) {
        return new SnowflakeIdGenerator(properties.getWorkerId());
    }

    /**
     * 仓储实现 —— 纯 JDBC
     */
    @Bean
    @ConditionalOnMissingBean
    public IProcessRepository jeeflowProcessRepository(DataSource dataSource) {
        return new JdbcProcessRepository(dataSource);
    }

    /**
     * 事务模板 —— 基于 Spring PlatformTransactionManager
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(PlatformTransactionManager.class)
    public ITransactionTemplate jeeflowTransactionTemplate(PlatformTransactionManager tm) {
        return new SpringTransactionTemplate(tm);
    }

    /**
     * 表达式求值器 —— 基于 Spring EL
     */
    @Bean
    @ConditionalOnMissingBean
    public IExpressionEvaluator jeeflowExpressionEvaluator() {
        return new SpelExpressionEvaluator();
    }

    /**
     * 用户提供者 —— 默认空实现，业务方覆盖
     */
    @Bean
    @ConditionalOnMissingBean
    public IUserProvider jeeflowUserProvider() {
        return new DefaultUserProvider();
    }

    /**
     * 查询参数解析器 —— m_* 惯例 → PageQuery
     */
    @Bean
    @ConditionalOnMissingBean
    public JeeflowQueryParser jeeflowQueryParser() {
        return new JeeflowQueryParser();
    }

    /**
     * 工作流引擎 —— 自动装配所有 SPI 后创建
     */
    @Bean
    @ConditionalOnMissingBean
    public JeeflowEngine jeeflowEngine(
            ObjectProvider<IProcessRepository> repository,
            ObjectProvider<IJsonProvider> jsonProvider,
            ObjectProvider<IUserProvider> userProvider,
            ObjectProvider<IOrgUserProvider> orgProvider,
            ObjectProvider<ITransactionTemplate> txTemplate,
            ObjectProvider<IExpressionEvaluator> exprEvaluator,
            ObjectProvider<IIdGenerator> idGenerator) {

        // 初始化引擎上下文并注册解析器
        com.mldong.jeeflow.Configuration config = new com.mldong.jeeflow.Configuration();

        // 注册 SPI（允许业务方覆盖，也允许部分不注册）
        repository.ifAvailable(r -> ServiceContext.put("repository", r));
        jsonProvider.ifAvailable(j -> ServiceContext.put("json", j));
        userProvider.ifAvailable(u -> ServiceContext.put("user", u));
        // 组织用户提供者（v1.6.0，可选）：业务方定义 Bean 才启用——组织 handler 数据源
        orgProvider.ifAvailable(o -> ServiceContext.put("org", o));
        txTemplate.ifAvailable(t -> ServiceContext.put("tx", t));
        exprEvaluator.ifAvailable(e -> ServiceContext.put("expr", e));
        idGenerator.ifAvailable(i -> ServiceContext.put("idGen", i));

        JeeflowEngine engine = new JeeflowEngineImpl();
        engine.configure(config);
        return engine;
    }
}
