# 快速开始

> ⚠️ **版本说明**：以下坐标是发布目标版本（`1.0.0`）。SDK 尚未发布到 Maven Central 前，请用本地构建：`mvn -q install -DskipTests -pl jeeflow-core`（当前版本 `1.0.0-SNAPSHOT`）。

## 1. Maven 依赖

### 最小运行（内存模式，不依赖数据库）

```xml
<dependency>
    <groupId>com.mldong.jeeflow</groupId>
    <artifactId>jeeflow-core</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-simple</artifactId>
    <version>2.0.13</version>
</dependency>
```

### Spring Boot 项目（自动装配 + JDBC）

```xml
<!-- Spring Boot 2.x -->
<dependency>
    <groupId>com.mldong.jeeflow</groupId>
    <artifactId>jeeflow-spring-boot2-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Spring Boot 3.x -->
<dependency>
    <groupId>com.mldong.jeeflow</groupId>
    <artifactId>jeeflow-spring-boot3-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Spring Boot 4.x -->
<dependency>
    <groupId>com.mldong.jeeflow</groupId>
    <artifactId>jeeflow-spring-boot4-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 2. 内存模式（5 行跑起来）

不依赖任何数据库，适合学习、测试。

```java
import com.mldong.jeeflow.*;
import com.mldong.jeeflow.core.*;
import com.mldong.jeeflow.domain.*;
import com.mldong.jeeflow.spi.*;

// 1. 初始化引擎
Configuration config = new Configuration();

// 2. 注册 SPI
ServiceContext.put("repository", new IProcessRepository() {
    // 实现 IProcessRepository 所有方法（可用内置 MemoryProcessRepository 示例）
});
ServiceContext.put("json", new IJsonProvider() {
    // 实现 IJsonProvider（Jackson / Gson / Fastjson）
});

// 3. 创建引擎
JeeflowEngine engine = new JeeflowEngineImpl();
engine.configure(config);

// 4. 准备流程定义并注册到 IProcessRepository
ProcessInstance.ProcessDefine define = ...;
// repository.addDefine(define); // 需要你实现的 repository 支持

// 5. 启动流程
ProcessInstance instance = engine.startProcessInstanceById(
    define.getId(), "张三", FlowData.create());
```

## 3. Spring Boot 模式（开箱即用）

无需任何实现代码，jeeflow 自动装配所有 SPI：

```java
@RestController
public class FlowController {

    @Autowired
    private JeeflowEngine engine;

    @Autowired
    private IProcessRepository repository;

    @PostMapping("/flow/start")
    public Map<String, Object> start(@RequestBody Map<String, Object> params) {
        Long defineId = Long.valueOf(params.get("defineId").toString());
        ProcessInstance inst = engine.startProcessInstanceById(
            defineId, "user1", FlowData.create());
        return Map.of("instanceId", inst.getInstanceId());
    }
}
```

### application.yml

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mldong
    username: root
    password: root
  sql:
    init:
      mode: always
      encoding: utf-8
      schema-locations: classpath:schema.sql  # wf_* 建表

jeeflow:
  worker-id: 1
```

## 4. 表结构

引擎使用 5 张核心表（与 mldong-boot2 完全兼容）：

- `wf_process_define` — 流程定义
- `wf_process_instance` — 流程实例
- `wf_process_task` — 流程任务
- `wf_process_task_actor` — 任务参与者
- `wf_process_cc_instance` — 抄送

DDL 见[引擎规范 §2](https://jeeflow-doc.mldong.com/spec/)（5 张表，H2/MySQL 均可）。

## 5. 下一步

- [流程定义 JSON 格式](flow-definition.md)
- [SPI 实现指南](spi-guide.md)
- [引擎 API](engine-api.md)
