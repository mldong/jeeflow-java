# 快速开始

> ✅ **版本说明**：已发布到 Maven Central，以下坐标可直接使用。
> 版本号**不在此写死**（每次发布递增，易忘更新）——推荐父 pom `<properties>` 统一管理：
> `<jeeflow.version>见下方「获取最新版本」</jeeflow.version>`，升级只改一处。
> 注意：`jeeflow-core` 不含 Spring 自动装配，仅引擎 + SPI（内存模式可跑）；Spring Boot 项目请用下文 Starter。

## 1. Maven 依赖

### 最小运行（内存模式，不依赖数据库）

```xml
<dependency>
    <groupId>com.mldong.jeeflow</groupId>
    <artifactId>jeeflow-core</artifactId>
    <version>${jeeflow.version}</version>
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
    <version>${jeeflow.version}</version>
</dependency>

<!-- Spring Boot 3.x -->
<dependency>
    <groupId>com.mldong.jeeflow</groupId>
    <artifactId>jeeflow-spring-boot3-starter</artifactId>
    <version>${jeeflow.version}</version>
</dependency>

<!-- Spring Boot 4.x -->
<dependency>
    <groupId>com.mldong.jeeflow</groupId>
    <artifactId>jeeflow-spring-boot4-starter</artifactId>
    <version>${jeeflow.version}</version>
</dependency>
```

## 获取最新版本

发布后版本号递增，用以下任一方式确认**当前最新发布版**（勿凭记忆写死）：

```bash
# ① 命令行（脚本友好）：maven-metadata.xml 的 <release> 即最新版
curl -s https://repo1.maven.org/maven2/com/mldong/jeeflow/jeeflow-spring-boot4-starter/maven-metadata.xml

# ② 或直接查中央仓库页面
#    https://central.sonatype.com/artifact/com.mldong.jeeflow/jeeflow-spring-boot4-starter
```

拿到版本号后，更新父 pom `<properties>`（推荐）或直接替换 `<version>`：

```xml
<properties>
    <jeeflow.version>此处填最新版</jeeflow.version>
</properties>
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

引擎使用 5 张核心表（与 mldong 框架 完全兼容）：

- `wf_process_define` — 流程定义
- `wf_process_instance` — 流程实例
- `wf_process_task` — 流程任务
- `wf_process_task_actor` — 任务参与者
- `wf_process_cc_instance` — 抄送

DDL 见[规范 01 · 数据模型](../../spec/01-data-model)（5 张表，H2/MySQL 均可）。

## 5. 下一步

- [流程定义 JSON 格式](flow-definition.md)
- [SPI 实现指南](spi-guide.md)
- [引擎 API](engine-api.md)
