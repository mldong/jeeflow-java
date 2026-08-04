# 业务数据入库（persist 组件）

> issues/18 · 1.6.2 起随 jeeflow-parent 发布

`jeeflow-persist` 子模块：**引擎无关的动态表写入组件 + 工作流入库适配拦截器**。
规范契约见文档站《09 · 业务数据通用入库》；本页是 Java 语言视角。

## 依赖

> 版本号不写死——由集成方在父 pom 的 `<jeeflow.version>` 属性统一管理
> （升级只改一处，参考 SDK 集成文档 getting-started）：

```xml
<dependency>
    <groupId>com.mldong.jeeflow</groupId>
    <artifactId>jeeflow-persist</artifactId>
    <version>${jeeflow.version}</version>
</dependency>
```

## 动态表写入（引擎无关）

```java
JdbcDynamicTableWriter writer = new JdbcDynamicTableWriter(dataSource);
// 可选：自定义系统字段列名（默认 create_time/create_user/update_time/update_user/is_deleted），null 禁用
writer.setCreateTimeColumn("create_time");

// ① 列过滤：目标表没有的列自动丢弃
List<String> kept = writer.filterColumns("biz_leave", Arrays.asList("title", "ghost_col"));
// ② 幂等检查：以 process_instance_id 为业务键
if (writer.exists("biz_leave", "process_instance_id", instanceId)) { /* 已入库 */ }
// ③ 系统字段填充 + 参数化插入
Map<String, Object> data = new HashMap<>();
data.put("title", "年假申请");
writer.fillSystemFields(data, true);
writer.insert("biz_leave", data);
```

安全：`sys_` 前缀表拒绝写入；非法字符表名拒绝；值走 PreparedStatement 参数化。

## 流程入库拦截器（流程结束同意自动落表）

1. 流程定义顶层声明（流程设计器配置即可）：

```json
{
  "name": "leave",
  "type": "approval",
  "relTableName": "biz_leave",
  "postInterceptors": "com.mldong.jeeflow.persist.interceptor.PersistPostInterceptor"
}
```

2. 启动时注册 writer 到引擎上下文（拦截器模型级反射实例化后按类型自取）：

```java
ServiceContext.put("dynamicTableWriter", new JdbcDynamicTableWriter(dataSource));
```

语义：结束节点 + 实例 FINISHED + submitType=AGREE 时，实例 `f_` 字段（去前缀）+
流程上下文（`process_instance_id`/`apply_user_id`/`apply_dept_id`）+ 系统字段写入业务表；
`process_instance_id` 幂等（先查后插）+ 同链内存标记（1.6.3：最后任务节点与结束节点都会触发后置拦截器，同链只插一次）；用户列 create_user/update_user 默认取 operator（1.6.3）；表不存在显性报错；不同意/退回不入库。

## 测试

```bash
mvn -pl jeeflow-persist test   # 12 用例：writer 8 + 拦截器集成 4（H2 内存库全链路）
```
