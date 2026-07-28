# 流程定义 JSON 格式

流程定义使用 LogicFlow 兼容的 JSON 格式。

## 基本结构

```json
{
  "name": "leave",
  "displayName": "请假审批",
  "type": "approval",
  "instanceUrl": "/form/leave",
  "nodes": [ ... ],
  "edges": [ ... ]
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `name` | ✅ | 唯一编码 |
| `displayName` | ✅ | 显示名称 |
| `type` | ❌ | 流程分类 |
| `instanceUrl` | ❌ | 发起表单地址 |
| `nodes` | ✅ | 节点列表 |
| `edges` | ✅ | 边列表 |

## 节点类型

### 开始节点 `snaker:start`

流程入口，每个流程有且只有一个。

```json
{
  "id": "start",
  "type": "snaker:start",
  "x": 100, "y": 200,
  "properties": {},
  "text": { "value": "开始" }
}
```

### 结束节点 `snaker:end`

流程出口。

```json
{
  "id": "end",
  "type": "snaker:end",
  "x": 500, "y": 200,
  "properties": {},
  "text": { "value": "结束" }
}
```

### 任务节点 `snaker:task`

审批节点，支持普通参与和会签。

```json
{
  "id": "task1",
  "type": "snaker:task",
  "x": 300, "y": 200,
  "properties": {
    "form": "leave-form",       // 表单标识
    "assignee": "leader",       // 静态参与者
    "taskType": 0,              // 0=主办 1=协办 2=记录
    "performType": 0,           // 0=普通参与 1=会签参与
    "field": {
      "candidateUsers": "user1,user2",     // 候选人列表
      "candidateGroups": "dept1",          // 候选组
      "countersignType": "PARALLEL",       // PARALLEL / SEQUENTIAL
      "countersignCompletionCondition": "#nrOfCompletedInstances==2"  // 会签完成条件
    }
  },
  "text": { "value": "部门经理审批" }
}
```

**属性说明：**

| 属性 | 说明 |
|------|------|
| `form` | 表单标识，前端根据此加载对应表单 |
| `assignee` | 静态参与者，支持逗号分隔多人和 `${变量名}` |
| `taskType` | 0=主办 1=协办 2=记录 |
| `performType` | 0=普通参与（任一人完成即推进） 1=会签参与 |
| `candidateUsers` | 候选人列表，逗号分隔 |
| `counterType` | `PARALLEL` 并行会签 / `SEQUENTIAL` 串行会签 |
| `countersignCompletionCondition` | 会签完成条件表达式 |

### 会签完成条件

| 条件 | 表达式 |
|------|--------|
| 全部完成 | 为空或 `#nrOfCompletedInstances==#nrOfInstances` |
| 按数量通过 | `#nrOfCompletedInstances==N` |
| 一票通过 | `#nrOfCompletedInstances==1` |
| 一票否决 | 设置 submitType=20 |

### 决策节点 `snaker:decision`

根据表达式或处理类分流。

```json
{
  "id": "decision1",
  "type": "snaker:decision",
  "x": 500, "y": 200,
  "properties": {
    "expr": "amount > 1000",         // 表达式（可选）
    "handleClass": "com.example.DecisionHandler"  // 处理类（可选）
  },
  "text": { "value": "金额>1000?" }
}
```

决策节点通过**边表达式**决定流向：

```json
{
  "id": "e3",
  "sourceNodeId": "decision1",
  "targetNodeId": "manager",
  "properties": { "expr": "amount > 1000" }
}
```

### 分支节点 `snaker:fork`

创建并行分支。

```json
{ "id": "fork1", "type": "snaker:fork", "x": 300, "y": 200, "properties": {}, "text": { "value": "分支" } }
```

### 合并节点 `snaker:join`

等待所有并行分支完成。

```json
{ "id": "join1", "type": "snaker:join", "x": 600, "y": 200, "properties": {}, "text": { "value": "合并" } }
```

### 子流程节点 `snaker:subProcess`

启动子流程实例。

```json
{
  "id": "sub1",
  "type": "snaker:subProcess",
  "properties": { "form": "sub-form", "version": 1 },
  "text": { "value": "子流程" }
}
```

### 自定义节点 `snaker:custom`

调用外部 Java 类。

```json
{
  "id": "custom1",
  "type": "snaker:custom",
  "properties": {
    "clazz": "com.example.MyHandler",  // 实现 IHandler 的类
    "methodName": "execute",           // 方法名
    "args": "param1,param2",           // 入参（从流程变量中取值）
    "val": "result"                    // 返回值变量名
  }
}
```

## 边

```json
{
  "id": "e1",
  "type": "snaker:transition",
  "sourceNodeId": "start",
  "targetNodeId": "task1",
  "properties": {
    "expr": "amount > 1000"   // 边表达式（可选，决策节点用）
  }
}
```
