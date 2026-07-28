# 引擎 API

## JeeflowEngine

工作流引擎的核心接口，所有流程操作都通过它完成。

### 配置

```java
JeeflowEngine engine = new JeeflowEngineImpl();
engine.configure(new Configuration());
```

### 方法

#### startProcessInstanceById

启动一个流程实例。

```java
ProcessInstance startProcessInstanceById(
    Long defineId,           // 流程定义 ID
    String operator,          // 发起人
    FlowData args             // 启动参数（业务编号、表单数据等）
);

// 子流程版本
ProcessInstance startProcessInstanceById(
    Long defineId,
    String operator,
    FlowData args,
    Long parentId,            // 父流程实例 ID
    String parentNodeName     // 父流程节点名称
);
```

**示例：**

```java
FlowData args = FlowData.create()
    .set(FlowConst.BUSINESS_NO, "BIZ-001")
    .set("amount", 5000)           // 表单字段
    .set(FlowConst.CC_ACTORS_START, "user5,user6");  // 启动时抄送

ProcessInstance pi = engine.startProcessInstanceById(1L, "user1", args);
System.out.println("实例 ID: " + pi.getInstanceId());
```

#### executeProcessTask

执行（审批）一个待办任务。

```java
List<ProcessTask> executeProcessTask(
    Long processTaskId,      // 任务 ID
    String operator,          // 操作人
    FlowData args             // 提交参数
);
```

**示例：**

```java
FlowData args = FlowData.create()
    .set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode())  // 1=同意
    .set(FlowConst.APPROVAL_COMMENT, "同意报销");

List<ProcessTask> next = engine.executeProcessTask(taskId, "leader", args);
```

#### executeAndJumpTask

执行任务并跳转到指定节点。

```java
List<ProcessTask> executeAndJumpTask(
    Long processTaskId,
    String operator,
    FlowData args,
    String nodeName           // 目标节点名称，null=退回上一步
);
```

#### executeAndJumpToEnd

执行任务并跳转到结束节点（拒绝）。

```java
List<ProcessTask> executeAndJumpToEnd(
    Long processTaskId,
    String operator,
    FlowData args
);
```

#### executeAndJumpToFirstTaskNode

执行任务并跳转到第一个任务节点（退回发起人）。

```java
List<ProcessTask> executeAndJumpToFirstTaskNode(
    Long processTaskId,
    String operator,
    FlowData args
);
```

## FlowData

流程数据载体，替代 Hutool Dict。

```java
FlowData args = FlowData.create()
    .set("name", "张三")
    .set("age", 30)
    .set("active", true);

String name = args.getStr("name");       // "张三"
Integer age = args.getInt("age");        // 30
Boolean active = args.getBool("active"); // true

FlowData copy = args.copy();  // 深拷贝
```

## FlowConst

常用常量。

| 常量 | 值 | 说明 |
|------|-----|------|
| `BUSINESS_NO` | `"BUSINESS_NO"` | 业务编号 |
| `SUBMIT_TYPE` | `"submitType"` | 提交类型 |
| `APPROVAL_COMMENT` | `"tf_approvalComment"` | 审批意见 |
| `NEXT_NODE_OPERATOR` | `"tf_nextNodeOperator"` | 下一节点执行人 |
| `FORM_DATA_PREFIX` | `"f_"` | 表单数据前缀 |
| `PROCESS_DEFINE_ID_KEY` | `"processDefineId"` | 流程定义 ID |
| `PROCESS_TASK_ID_KEY` | `"processTaskId"` | 流程任务 ID |

## ProcessSubmitTypeEnum

| 枚举 | 值 | 说明 |
|------|-----|------|
| `APPLY` | 0 | 发起申请 |
| `AGREE` | 1 | 同意 |
| `REJECT` | 2 | 拒绝 |
| `ROLLBACK` | 3 | 退回上一步 |
| `JUMP` | 4 | 跳转 |
| `ROLLBACK_TO_OPERATOR` | 6 | 退回发起人 |
| `COUNTERSIGN_DISAGREE` | 20 | 会签不同意 |
