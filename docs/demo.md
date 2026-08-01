# Java 演示站（Demo）

> 演示站是运行在 :8080 的 Spring Boot 4 应用（H2 内存库 + 10 个示例流程），对接 jeeflow-ui 体验完整流程。SDK 集成方式见 [快速开始](./getting-started.md)。

## 环境要求

| 模块 | JDK |
|------|-----|
| `jeeflow-core`（引擎核心，零依赖） | JDK 8 |
| `jeeflow-demo-boot4`（演示站） | JDK 21（Boot 4 官方最低 17，本项目统一 21） |

Maven 3.6+。

## 启动演示站

```bash
cd jeeflow-demo-boot4
# ⚠️ 先安装依赖模块到 .m2，否则运行报 IUserProvider$UserInfo NoClassDefFoundError（.m2 是旧 jar）
mvn -q install -DskipTests -pl jeeflow-core,jeeflow-repository-jdbc,jeeflow-spring-boot-autoconfigure,jeeflow-spring-boot4-starter
# 启动（H2 内存库 + data.sql 种子流程，10 个示例流程）
mvn -q -pl jeeflow-demo-boot4 spring-boot:run
# → http://localhost:8080
```

> 演示站对接 jeeflow-ui（:5173）时右上角切到 `☕ Java :8080`；接口规范（code=0/msg、submitType 全枚举）见[文档站 REST API 指南](https://jeeflow-doc.mldong.com/guides/03-api)。

## 快速验证

```bash
B=http://localhost:8080
curl -s -X POST $B/wf/processDefine/page -H "Content-Type: application/json" -d '{}'   # → {"code":0,...}
curl -s -X POST $B/wf/processDefine/startAndExecute -H "Content-Type: application/json" -d '{"processDefineId":9,"operator":"user1","amount":500}'
```

完整验证矩阵（同意/拒绝/退回发起人/highLight/approvalRecord）见文档站通用指南。

## 运行测试

```bash
# 引擎核心 14 项 + JDBC 仓储 5 项
mvn test -pl jeeflow-core,jeeflow-repository-jdbc
```

> `JeeflowFullTest` 统一用 `startFlow` helper 模拟 startAndExecute 契约（10 个流程 JSON 的第一个节点都是 apply 申请节点，测试不能假设 start 后第一个任务就是业务任务）。

## 生产部署

```bash
mvn package
java -jar jeeflow-demo-boot4/target/*.jar --server.port=8080
```

生产接入：实现 `IProcessRepository` SPI（或直接用 `jeeflow-repository-jdbc` 的 JDBC 实现，注入 DataSource），映射 [SPEC §2](https://jeeflow-doc.mldong.com/spec/) 的 5 张表。

## 常见问题

| 症状 | 原因 | 处理 |
|------|------|------|
| 启动报 `Lookup method resolution failed` / `NoClassDefFoundError: IUserProvider$UserInfo` | `.m2` 里是旧 jar | 先 `mvn install` 依赖模块（见上） |
| define/page 的 createTime 为 null | data.sql 种子数据无时间列 | 演示数据问题，非 bug |
| 改了流程 JSON 不生效 | 演示站从 data.sql 加载（与共享 JSON 双份） | 同步 `src/main/resources/data.sql` |
