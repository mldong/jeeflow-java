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


**流程定义配置字段语义**（顶层，与 `name`/`nodes` 同层）：

| 字段 | 取值 | 语义 |
|------|------|------|
| `relTableName` | 表名 | 业务表名——数据写入哪张表；**缺省回落流程 `name`**；表不存在 = 配置错误显性报错 |
| `persistMode` | `ARCHIVE` / `SYNC`（缺省 `ARCHIVE`） | 持久化模式——`ARCHIVE`：流程结束且同意落库一次；`SYNC`：发起即入库 → 节点推进 → 结束定稿，全程留痕。非 `SYNC` 值回落 `ARCHIVE` |


## 同步演进模式（SYNC，1.8.0）

流程定义顶层加 `"persistMode": "SYNC"`（缺省 `ARCHIVE`——保持"结束同意归档"不变），
改为**全程留痕**：提交申请即入库（start 节点 INSERT 全量）→ 任务节点推进 UPDATE →
结束节点定稿最终状态（FINISHED=20 / REJECT=45），不管成功失败都入库。

- **状态字段**：值 = 实例状态码，列名优先 `{节点ID}_{状态码}`（如 `task1_10`），
  无该列回落 `{节点ID}`（如 `task1`）；任务节点统一写 DOING(10)（任务推进状态），
  结束节点写实例最终状态
- **字段权限**（任务节点级）：节点 `properties.field` 声明——**键格式双兼容**：
  `PERMISSION_f_{表单字段全名}`（前端 vben5-wf 设计器约定，优先）与 `PERMISSION_{去前缀名}`
  （1.8.0 首版格式，兼容），两种都匹配（1.8.1 起）；值 `1` 只读 / `2` 可编辑 / `3` 隐藏
  （缺省可编辑）；**权限管控「办理时可提交的字段」**——只读/隐藏字段办理提交的值在引擎入口
  **过滤不入变量**（1.8.2，issues/26），下游无权限节点也无法写入（变量里没有被拒值），上游只读不可绕过；
  非任务节点不覆盖业务字段（只定稿状态）
- **`tf_` 冗余**：任务节点提交的 `tf_` 前缀变量（如 `tf_opinion` 审批意见）去前缀冗余到
  业务表对应列（列过滤由 writer 做，无列则丢弃）
- **幂等**：同链标记改节点级（`__persist_executed_{instanceId}_{节点ID}`）——任务推进与
  结束定稿是不同节点都要生效；`process_instance_id` 先查后插/更兜底
- **writer.update**：参数化 UPDATE（列过滤组装 SET、条件列排除防注入），
  `update(tableName, data, "process_instance_id", instanceId)`

示例：`{"persistMode": "SYNC", "relTableName": "biz_leave", ...}`，业务表建
`apply` / `task1` / `finish` 状态列（INT）+ `opinion` 列（tf_ 冗余，可选）。

## 测试

```bash
mvn -pl jeeflow-persist test   # 12 用例：writer 8 + 拦截器集成 4（H2 内存库全链路）
```
