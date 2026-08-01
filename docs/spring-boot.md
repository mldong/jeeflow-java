# Spring Boot 集成

## 版本选择

| Starter | Boot 版本 | JDK 最低 |
|---------|----------|----------|
| `jeeflow-spring-boot2-starter` | 2.x | 8 |
| `jeeflow-spring-boot3-starter` | 3.x | 17 |
| `jeeflow-spring-boot4-starter` | 4.x | 17（官方基线；本项目统一 21） |

## 添加依赖

```xml
<dependency>
    <groupId>com.mldong.jeeflow</groupId>
    <artifactId>jeeflow-spring-boot4-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 自动装配的内容

引入 Starter 后，以下 Bean 自动注册：

| Bean | 类型 | 说明 |
|------|------|------|
| `jeeflowProcessRepository` | `JdbcProcessRepository` | 基于 DataSource 的 JDBC 仓储 |
| `jeeflowJsonProvider` | `JacksonJsonProvider` | 基于 Jackson 的 JSON 提供者 |
| `jeeflowTransactionTemplate` | `SpringTransactionTemplate` | Spring 事务模板 |
| `jeeflowExpressionEvaluator` | `SpelExpressionEvaluator` | 基于 SpEL 的表达式求值器 |
| `jeeflowUserProvider` | `DefaultUserProvider` | 默认用户提供者（需覆盖） |
| `jeeflowIdGenerator` | `SnowflakeIdGenerator` | 雪花算法 ID 生成器 |
| `jeeflowQueryParser` | `JeeflowQueryParser` | m_* 参数解析器 |
| `jeeflowEngine` | `JeeflowEngineImpl` | 工作流引擎 |

## 使用方式

直接注入 `JeeflowEngine` 和 `IProcessRepository`：

```java
@RestController
public class FlowController {

    @Autowired
    private JeeflowEngine engine;

    @Autowired
    private IProcessRepository repository;

    @Autowired
    private JeeflowQueryParser queryParser;
}
```

## 覆盖 Bean

如果内置实现不满足需求，声明同名 Bean 即可覆盖：

```java
@Bean
public IUserProvider myUserProvider() {
    return new MyUserProvider();  // 你的实现
}
```

## 配置属性

```yaml
jeeflow:
  enabled: true        # 是否启用 auto-configuration（默认 true）
  worker-id: 1         # 雪花算法 workerId（0-31，默认 1）
```

## 表结构

Starter 依赖 `spring-boot-starter-jdbc`（由你已有配置提供 DataSource），引擎使用 5 张表：

```sql
-- MySQL DDL（与 mldong-boot2 完全兼容）
CREATE TABLE wf_process_define (
    id BIGINT NOT NULL COMMENT '主键',
    name VARCHAR(64) NOT NULL COMMENT '唯一编码',
    display_name VARCHAR(100) NOT NULL COMMENT '显示名称',
    type VARCHAR(32) NULL COMMENT '流程类型',
    state INT NULL DEFAULT 1 COMMENT '是否可用',
    content BLOB NULL COMMENT '流程模型定义(JSON)',
    version INT NULL DEFAULT 1 COMMENT '版本',
    create_time DATETIME(3) NULL,
    create_user VARCHAR(64) NULL,
    update_time DATETIME(3) NULL,
    update_user VARCHAR(64) NULL,
    PRIMARY KEY (id)
);

-- wf_process_instance, wf_process_task,
-- wf_process_task_actor, wf_process_cc_instance
-- 等其他 4 张表
```

完整 DDL 参考：[schema-h2.sql](https://github.com/mldong/jeeflow/blob/main/jeeflow-repository-jdbc/src/test/resources/schema-h2.sql)

## CORS 配置（演示站需要）

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**").allowedOrigins("*");
    }
}
```
