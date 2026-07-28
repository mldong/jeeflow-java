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

| 别名 | 表 |
|------|-----|
| `t` | wf_process_task |
| `pi` | wf_process_instance |
| `pd` | wf_process_define |
| `pta` | wf_process_task_actor |

列名在仓库层过白名单校验，不在白名单的自动丢弃，防止 SQL 注入。

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
    String getRealName(String userId);
    String getDeptId(String userId);
    String getDeptName(String userId);
    String getPostId(String userId);
    String getPostName(String userId);
}
```

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
