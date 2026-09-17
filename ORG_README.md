# jeeflow · 轻量级多语言工作流引擎

一套契约，八种语言实现。jeeflow 是轻量级工作流引擎联邦：统一门面一行接入，零框架依赖、DDD 充血模型、SPI 全可替换，全量 action 契约跨语言一致。

- 📖 文档站：https://jeeflow-doc.mldong.com
- 🚀 演示站（八语言在线体验）：https://jeeflow-demo.mldong.com

<!-- 组织主页 README 源文件：GitCode/Gitee 组织设置 → README 配置指向 mldong/jeeflow-java 的 ORG_README.md，随 sync-cn 自动同步；与各仓自身 README 互不影响，改动后需手动触发 sync-cn 或等下次打 tag -->

## 仓库一览

| 仓库 | 语言 | 发布渠道 / 安装 |
|------|------|----------------|
| [jeeflow-java](https://gitcode.com/mldong/jeeflow-java) | Java 8+（参考实现） | Maven Central `com.mldong.jeeflow`，含 Spring Boot 2/3/4 Starter |
| [jeeflow-go](https://gitcode.com/mldong/jeeflow-go) | Go 1.25+ | `go get github.com/mldong/jeeflow-go` |
| [jeeflow-python](https://gitcode.com/mldong/jeeflow-python) | Python 3 | `pip install jeeflow[mysql]` |
| [jeeflow-node](https://gitcode.com/mldong/jeeflow-node) | Node.js | `npm i @mldong/jeeflow` |
| [jeeflow-php](https://gitcode.com/mldong/jeeflow-php) | PHP | `composer require mldong/jeeflow-php` |
| [jeeflow-rust](https://gitcode.com/mldong/jeeflow-rust) | Rust | crates.io 四 crate：`jeeflow-core` / `jeeflow-persist` / `jeeflow-repository-sqlx` / `jeeflow-facade` |
| [jeeflow-moon](https://gitcode.com/mldong/jeeflow-moon) | MoonBit | mooncakes.io 四模块：`mldong/jeeflow-core` / `jeeflow-persist` / `jeeflow-repository-mysql` / `jeeflow-facade` |
| [jeeflow-csharp](https://gitcode.com/mldong/jeeflow-csharp) | C#/.NET | NuGet 四包：`Mldong.Jeeflow.Core` / `Repository.MySql` / `Persist` / `Facade` |
| [jeeflow-ui](https://gitcode.com/mldong/jeeflow-ui) | Vue 3 / TypeScript | 统一前端（工作台 + 流程设计器，演示站同款） |
| [uni-jeeflow-app](https://gitcode.com/mldong/uni-jeeflow-app) | uni-app x（Android） | 移动审批端（待办 / 审批 / 发起 / 流程图，Gitee 同名镜像） |

## 特性

- **一套契约，八语言同构**：全量 action manifest 各语言与 Java 参考实现零差集，统计接口同数据集逐字段一致
- **统一门面一行接入**：`facade.flow(action, params)` 覆盖流程设计 / 部署 / 发起 / 审批 / 委托 / 抄送 / 统计全场景
- **零框架依赖**：引擎核心不绑定 Web / ORM 框架，接入层技术选型自由
- **DDD 充血模型**：`ProcessInstance` 聚合根封装所有状态变更行为
- **SPI 全可替换**：仓储 / JSON / 用户 / 事务 / 表达式 / ID 生成 / 权限等均可扩展
- **内置流程模式**：开始、结束、任务、决策、分支、合并、子流程、自定义节点、会签
- **事件机制**：流程开始 / 结束、任务创建 / 完成、抄送知会等事件，集成层可监听并落站内信

## 快速体验

打开演示站 https://jeeflow-demo.mldong.com ，切换 `?lang=java|go|python|node|php|rust|moon|csharp` 即可在同一套 UI 上体验八种语言的引擎实现：发起 → 审批 → 抄送 → 统计全流程。

## 同步说明

各仓开发主仓在 GitHub（`github.com/mldong/jeeflow-*`），发布打 tag 时 CI 自动将 master + 全部 tag 同步至本组织及 Gitee 同名仓库，内容与正式发布物一致。uni-jeeflow-app 无发版 tag，push master 即同步（不同步 tag）。

## 相关生态

- **mldong 快速开发框架**：13 语言栈（Java / Python / Go / Node / PHP / Rust / C# 等）已内置或集成 jeeflow 引擎
- **uni-jeeflow-app**：基于 uni-app x 的移动审批端，见 [github.com/mldong/uni-jeeflow-app](https://github.com/mldong/uni-jeeflow-app)

## License

Apache License 2.0
