# 🧊 jeeflow

轻量 Java 工作流引擎——零框架依赖、DDD 充血模型、JDK 8 兼容。

> **v1.1.0**：新增管理扩展（流程设计/历史/委托三表 + `IProcessExtRepository` + `SurrogateInterceptor`）
> 与统一门面 `JeeflowFacade.flow(action, map)`（27 个 action，集成方 controller 一行接入）；
> assignee 变量解析与 `flow.auto`/`flow.admin` 系统代执行对齐 boot2/boot3（v1.0.1）。

## 特性

- **零框架依赖**：不依赖 Spring、MyBatis、JPA、Servlet，仅 `slf4j-api`（provided）
- **DDD 充血模型**：`ProcessInstance` 聚合根封装所有状态变更行为
- **SPI 可扩展**：5 个 SPI 接口，替换任意环节
- **JDK 8+**：在 8~25 上均可运行
- **Spring Boot Starter**：支持 Boot 2.x / 3.x / 4.x
- **内置流程模式**：开始、结束、任务、决策、分支、合并、子流程、自定义节点、会签

## 5 行跑起来

```java
Configuration config = new Configuration();
ServiceContext.put("repository", new MemoryProcessRepository());
ServiceContext.put("json", new BuiltinJsonProvider());
JeeflowEngine engine = new JeeflowEngineImpl();
engine.configure(config);
ProcessInstance pi = engine.startProcessInstanceById(defineId, "张三", FlowData.create());
```

## 模块

| 模块 | 说明 |
|------|------|
| `jeeflow-core` | 引擎核心，98 KB，仅 slf4j-api |
| `jeeflow-repository-jdbc` | 纯 JDBC 仓储实现（白名单防注入） |
| `jeeflow-spring-boot-autoconfigure` | 自动装配 + m_* 查询解析器 |
| `jeeflow-spring-boot2-starter` | Boot 2.x Starter |
| `jeeflow-spring-boot3-starter` | Boot 3.x Starter |
| `jeeflow-spring-boot4-starter` | Boot 4.x Starter |

## SPI 接口

| 接口 | 必须 | 说明 |
|------|------|------|
| `IProcessRepository` | ✅ | 聚合仓储 + 分页查询 |
| `IJsonProvider` | ✅ | JSON 解析 |
| `IUserProvider` | 可选 | 用户信息（人名/部门） |
| `ITransactionTemplate` | 可选 | 事务模板 |
| `IExpressionEvaluator` | 可选 | 表达式求值（决策节点用） |
| `IIdGenerator` | 可选 | ID 生成器 |

## 文档

- [快速开始](docs/getting-started.md)
- [流程定义 JSON 格式](docs/flow-definition.md)
- [SPI 实现指南](docs/spi-guide.md)
- [引擎 API](docs/engine-api.md)
- [Spring Boot 集成](docs/spring-boot.md)

## License

Apache-2.0
