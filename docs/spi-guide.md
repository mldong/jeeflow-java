# SPI 实现指南

jeeflow 通过 SPI（Service Provider Interface）解耦框架依赖。您只需实现以下接口并注册到 `ServiceContext`。

## 注册方式

```java
Configuration config = new Configuration();
ServiceContext.put("repository", new MyRepository());
ServiceContext.put("json", new MyJsonProvider());
// ...

JeeflowEngine engine = new JeeflowEngineImpl();
engine.configure(config);
```

Spring Boot 项目中，SPI 自动装配。如需覆盖，只需声明同名 Bean。

---

## IProcessRepository（必须）

聚合仓储——引擎所有持久化操作都通过此接口。

### 引擎运行时方法

```java
public interface IProcessRepository {
    // 流程定义
    ProcessDefine findDefineById(Long defineId);

    // 流程实例
    ProcessInstance findInstanceById(Long instanceId);
    void saveInstance(ProcessInstance instance);
    void updateInstance(ProcessInstance instance);

    // 流程任务
    ProcessTask findTaskById(Long taskId);
    void saveTask(ProcessTask task);
    void updateTask(ProcessTask task);
    List<ProcessTask> findDoingTasks(Long instanceId, String[] taskNames);
    List<ProcessTask> findDoneTasks(Long instanceId, String[] taskNames);
    List<ProcessTask> findHistoryTasks(Long instanceId);

    // 参与者
    List<String> findTaskActors(Long taskId);
    void addTaskActor(Long taskId, List<String> actors);
    void removeTaskActor(Long taskId, List<String> actors);

    // 抄送
    void createCcInstance(Long instanceId, String creator, String... actorIds);
    void updateCcStatus(Long instanceId, String actorId);
}
```

### 前端分页查询方法

```java
    // 待办/已办
    PageResult<TaskRow> pageTodoTasks(PageQuery query);
    PageResult<TaskRow> pageDoneTasks(PageQuery query);

    // 实例/抄送/定义
    PageResult<InstanceRow> pageInstances(PageQuery query);
    PageResult<InstanceRow> pageCcInstances(PageQuery query);
    PageResult<DefineRow> pageDefines(PageQuery query);

    int countTodoTasks(Long userId);
}
```

### PageQuery

```java
PageQuery query = new PageQuery(pageNum, pageSize);
query.add("t.task_name", "EQ", "task1");         // 等于
query.add("t.display_name", "LIKE", "审批");       // 模糊
query.add("t.create_time", "GT", startDate);       // 大于
query.add("pta.actor_id", "EQ", userId);           // 参与者
query.setOrderBy("t.id desc");
```

**支持的操作符：** `EQ`, `NE`, `LIKE`, `LLIKE`, `RLIKE`, `GT`, `GE`, `LT`, `LE`, `IN`, `NIN`, `BT`

### Spring Boot 中的 m_* 参数转换

前端传参使用 `m_{alias}_{type}_{column}` 约定。`JeeflowQueryParser` 自动转换：

```java
@Autowired
private JeeflowQueryParser queryParser;

@PostMapping("/wf/processTask/todoList")
public CommonResult<CommonPage<TaskRow>> todoList(@RequestBody Map<String, Object> params) {
    PageQuery query = queryParser.parse(params);
    query.add("pta.actor_id", "EQ", currentUserId);
    return CommonResult.data(repository.pageTodoTasks(query));
}
```

前端传参示例：
```json
{
  "pageNum": 1,
  "pageSize": 10,
  "m_t_EQ_taskName": "apply",
  "m_t_LIKE_displayName": "审批",
  "m_pi_GT_createTime": "2026-01-01"
}
```

### 表别名约定

> ⚠️ **各分页方法别名不统一（v1.0.0 现状）**，按方法区分：

| 方法 | FROM 主表别名 | 支持的关联别名 |
|------|--------------|---------------|
| `pageTodoTasks` / `pageDoneTasks` | `t` = wf_process_task | `pi`=wf_process_instance、`pd`=wf_process_define、`pta`=wf_process_task_actor |
| `pageInstances` / `pageCcInstances` | `t` = wf_process_instance | `pd`=wf_process_define、`cc`=wf_process_cc_instance |
| `pageDefines` | `t` = wf_process_define | （无关联表） |
| `pageDefines` 过滤列 | `t.name` / `t.state` / `t.displayName` 等（白名单见仓库实现） | — |

列名在仓库层过白名单校验，**不在白名单的自动丢弃**（v1.0.1 计划改为报错，调试更友好）。踩坑提醒：`pageDefines` 过滤请用 `t.xxx` 而非 `pd.xxx`（`pd` 仅 task 分页可用）。

### 参考实现：JdbcProcessRepository

`jeeflow-repository-jdbc` 模块提供纯 JDBC 实现（零 ORM 依赖），注入 `DataSource` 即可对接任意数据库（MySQL / H2 / PostgreSQL）：

```java
JdbcProcessRepository repository = new JdbcProcessRepository(dataSource);
```

- 自动映射 `wf_*` 5 张表（spec §2），兼容 mldong 框架（boot2 版）表结构
- 主键用 `IIdGenerator` SPI（不注册则内置时间戳+序号）
- **事务（spec §7.4）**：引擎方法不开启事务，由业务层用 `ITransactionTemplate` 包装——Spring Boot 场景使用 `@Transactional` 或 `TransactionTemplate`，仓储所有方法从 `DataSource.getConnection()` 取连接，天然加入当前 Spring 事务

---

## IJsonProvider（必须）

JSON 序列化/反序列化。引擎用此接口解析流程定义 JSON。

```java
public interface IJsonProvider {
    String toJson(Object obj);
    <T> T fromJson(String json, Class<T> type);
    <T> T fromJson(String json, TypeReference<T> typeRef);
    boolean isJson(String str);
}
```

**内置实现（Spring Boot）：** `JacksonJsonProvider`。复用 Boot 的 ObjectMapper。

---

## IUserProvider（可选）

用户信息查询。不注册则流程变量中的用户名不完整（不影响核心流转）。

```java
public interface IUserProvider {
    /** 一次返回用户全部信息（为空时返回 null）——避免多次查库 */
    UserInfo getUser(String userId);
}
```

> 注意：v1.0.0 起为单方法 `getUser`（一次返回完整 `UserInfo`：userId/realName/deptId/deptName/postId/postName），
> 早期 5 个 `getRealName/getDeptId/...` 方法的版本已废弃。

---

## ITransactionTemplate（可选）

事务模板。Spring Boot 项目自动使用 `TransactionTemplate`。

```java
public interface ITransactionTemplate {
    <T> T execute(Supplier<T> action);
}
```

不注册则引擎裸执行（适合内存测试）。

---

## IExpressionEvaluator（可选）

表达式求值器。决策节点和会签条件需要。不注册则使用决策/会签时报错。

```java
public interface IExpressionEvaluator {
    Object eval(String expression, Map<String, Object> context);
}
```

**内置实现（Spring Boot）：** `SpelExpressionEvaluator`，基于 Spring EL。

---

## IIdGenerator（可选）

ID 生成器。返回 `long` 类型主键。不注册则使用内置的时间戳+序列。

```java
public interface IIdGenerator {
    long nextId();
}
```

**内置实现（Spring Boot）：** `SnowflakeIdGenerator`。

---

## 管理扩展（v1.1.0，可选 SPI）

设计稿 / 历史 / 委托三张表由 `IProcessExtRepository` 提供统一读写（规范见文档站 spec §10）：

```java
// 参考实现：JDBC 版（给定 DataSource 即可）
IProcessExtRepository extRepo = new JdbcProcessExtRepository(dataSource);

// 委托生效查询（SurrogateInterceptor 内部使用）
ProcessSurrogate s = extRepo.getSurrogate("zhangsan", "leave", LocalDateTime.now());
```

**委托自动生效**：把 `SurrogateInterceptor`（core 提供，默认不注册）加入拦截器列表，
任务创建后自动把代理人加入参与者：

```java
ServiceContext.put("surrogateInterceptor", new SurrogateInterceptor(extRepo));
```

## 统一门面（v1.1.0）

"接口即 POST + JSON body"风格——集成方只写一个转发 controller：

```java
@PostMapping("/wf/**")
public Map<String, Object> flow(HttpServletRequest req, @RequestBody Map<String, Object> body) {
    String action = req.getRequestURI().substring(req.getContextPath().length() + "/wf/".length());
    return facade.flow(action, body);
}
```

`JeeflowFacade.flow(action, map)` 路由全部 27 个 action（spec §11.2 清单），
返回 `{code, msg, data}`；deploy 自动做版本管理，execute 按 submitType 全分发。
操作人约定：`args.operator` 显式传入（集成方可替换为登录上下文注入）。
