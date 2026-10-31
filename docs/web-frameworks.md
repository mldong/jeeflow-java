# Java · Web 框架接入（统一门面转发层）

> 目标：**任意 Java Web 框架都能在 10 分钟内接入统一门面（JeeflowFacade）**——
> 门面接入 = **1 个路由 + 3 个注入点**，框架差异只在这 ~20 行转发层代码里。
> 引擎初始化、SPI、id 契约都是框架无关的（见 [SDK 集成](./getting-started.md) 与
> [规范 06 统一门面](../../spec/06-facade)）。

## 1. 门面接入模式（四步总则）

```
框架层                                jeeflow 引擎层
┌──────────────────────────┐         ┌──────────────────────┐
│ POST /wf/{action} 路由     │  body   │ JeeflowFacade         │
│ ① 登录校验（框架已有）      │ ──────→ │  flow(action, args)   │
│ ② 权限码动态校验            │  args   │  40+ 个 action 内置路由  │
│ ③ operator 注入            │         └──────────────────────┘
└──────────────────────────┘
```

| # | 步骤 | 说明 |
|---|------|------|
| 1 | 路由捕获 action | `POST /wf/{action}`，action 是多段路径（`processDefine/page`） |
| 2 | 登录校验 | 用框架已有的登录拦截器/过滤器（门面不感知登录态） |
| 3 | 权限码校验 | 引擎 SPI 提供映射（默认 `wf:{action.replace('/',':')}`），superAdmin 放行（见 [规范 06 §2.6](../../spec/06-facade)） |
| 4 | operator 注入 | `body.put("operator", 当前登录用户 id)`——"我的"语义 action 依赖它过滤 |

> **id 字符串契约**：Java 集成层全局配置 `ToStringSerializer`（Jackson），出口 id 一律字符串；
> 前端按字符串处理，提交时字符串/数字均可（引擎 toLong 容错）。
> **listByType 转换**：`processDesign/listByType` 引擎返回 `Map<type, items>`；若前端按
> boot3 惯例期望 `[{type, title, items}]`，转发层做一次转换（见 §3 示例）。

## 2. 引擎装配（Spring 系通用）

```java
@Configuration
public class WfJeeflowConfig {
    @Bean
    public JeeflowFacade jeeflowFacade(JeeflowEngine engine,
                                       IProcessRepository repository,
                                       IProcessExtRepository extRepository) {
        JeeflowFacade facade = new JeeflowFacade(engine, repository, extRepository);
        facade.setUserSearchProvider(userSearchProvider);   // 可选：用户搜索 SPI
        return facade;
    }
}
```

> 引擎/仓储 bean 由 `jeeflow-spring-bootX-starter` 自动装配（给 DataSource 即用）；
> 用户体系映射（UserProvider）、persist 等按需注册，见 [Spring Boot 集成](./spring-boot.md)。
>
> **非 Spring 宿主（Solon / JAX-RS / 裸 Servlet）拿不到 starter**：starter 里的
> `JacksonJsonProvider`、`SpelExpressionEvaluator`、`SnowflakeIdGenerator` 三枚都在
> `jeeflow-spring-boot-autoconfigure` 包内，要各自替代（JSON 一枚约 20 行、表达式一枚约 10 行、
> 雪花号一枚；不注册 idGen 时仓储有兜底号，见 §5.3）。装配形状见 §5 Solon 实测样本。

## 3. Spring Boot（参考实现）

```java
@RestController
@RequiredArgsConstructor
public class WfFlowController {
    private final JeeflowFacade jeeflowFacade;

    /** 统一入口：一个 /wf/** 转发全部 action */
    @PostMapping("/wf/**")
    public Map<String, Object> flow(HttpServletRequest request,
                                    @RequestBody(required = false) Map<String, Object> body) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String action = uri.substring(contextPath.length() + "/wf/".length());
        // ① 权限校验（权限码 SPI，引擎内置默认映射；superAdmin 万能放行）
        checkPermission(action);
        // ② 注入操作人
        if (body == null) body = new LinkedHashMap<>();
        body.put("operator", LoginUserHolder.getUserId().toString());
        // ③ listByType 返回结构转换（boot3 前端约定，按需）
        if ("processDesign/listByType".equals(action)) {
            return designListByType(body);
        }
        // ④ 门面转发（字段契约引擎已内置）
        return jeeflowFacade.flow(action, body);
    }

    private void checkPermission(String action) {
        LoginUser loginUser = LoginUserHolder.me();
        if (loginUser != null && loginUser.isSuperAdmin()) return;   // 超管万能
        IActionPermissionProvider provider = ServiceContext.find(IActionPermissionProvider.class);
        String[] codes = provider == null ? null : provider.permissionCodes(action);
        if (codes != null && codes.length > 0) {
            StpUtil.checkPermissionOr(codes);   // sa-token 校验（换成 Shiro/Spring Security 同理）
        }
    }

    /** Map<type, items> → [{type, title, items}]（boot3 前端 apply-list 约定） */
    private Map<String, Object> designListByType(Map<String, Object> body) {
        Map<String, Object> result = jeeflowFacade.flow("processDesign/listByType", body);
        List<Map<String, Object>> res = new ArrayList<>();
        if (result != null && (Integer) result.get("code") == 0 && result.get("data") instanceof Map) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) result.get("data")).entrySet()) {
                res.add(Map.of("type", e.getKey(), "title", "", "items", e.getValue()));
            }
        }
        result.put("data", res);
        return result;
    }
}
```

- **鉴权框架替换**：`StpUtil.checkPermissionOr`（sa-token）可换成 Spring Security 的
  `hasAnyAuthority`、Shiro 的 `checkPermissions`——校验对象是同一组权限码
- **完整参考实现**：mldong-boot4 集成仓
  `mldong-admin/src/main/java/com/mldong/modules/wf/controller/WfFlowController.java`

## 4. Spring MVC（非 Boot 工程）

与 Spring Boot 完全相同的 controller 写法，差异只在装配方式：

| 差异点 | Spring Boot | Spring MVC（传统 war） |
|--------|-------------|----------------------|
| 引擎 bean | starter 自动装配 | `@Bean` 方法放 `@Configuration` 类，`@ComponentScan` 扫描 |
| 数据库 | DataSource 自动 | 手动配置 DataSource + 仓储实现 |
| 路由前缀 | `@PostMapping("/wf/**")` | 相同注解即可（`/wf/**` 通配 Spring 5.3+ 支持）；或 `@RequestMapping("/wf/{action}")` 手动解析 |
| 打包部署 | 内嵌容器 | war 部署到 Tomcat（Servlet 3+） |

> Java Web 生态实际以 Spring 系为主；若用 JAX-RS（Jersey/RESTEasy）等，转发层
> 同样只需「1 路由 + 3 注入点」，action 多段路径用 `@Path("/wf/{action: .+}")` 捕获。

## 5. Solon（非 Spring 宿主实测样本）

依赖三件：`org.noear:solon-web` + `jeeflow-core` + `jeeflow-repository-jdbc`
（版本各自现读 Maven Central，Solon 4.x 仍以 JDK 8 为基线）。转发层与 Spring 版**逐行同形**，
差异全在装配那一层与三处配置。

### 5.1 转发层

```java
@Controller
public class WfFlowController {
    @Inject
    JeeflowFacade facade;

    @Post
    @Transaction                                     // 命令类 action 的多表写要原子（见 §5.3）
    @Mapping("/wf/**")                               // Solon 原生支持多段通配
    public Map<String, Object> flow(Context ctx, @Body Map<String, Object> body) {
        String action = ctx.path().substring("/wf/".length());   // /wf/processDefine/page → processDefine/page
        checkPermission(action);                                 // ① 权限码：同一枚 IActionPermissionProvider
        if (body == null) body = new LinkedHashMap<>();
        body.put("operator", LoginUserHolder.id());              // ② 操作人：从 Solon 登录态取
        if ("processDesign/listByType".equals(action)) {
            return designListByType(body);                       // ③ 形状转换：与 §3 同款
        }
        return facade.flow(action, body);
    }
}
```

### 5.2 装配：`Configuration` 必须先于 `ServiceContext.put`

```java
@Configuration
public class WfJeeflowConfig {

    @Bean
    public DataSource dataSource() {
        return TranUtils.getDataSourceProxy(new HikariDataSource(...));   // 事务感知代理
    }

    @Bean
    public JeeflowFacade jeeflowFacade(DataSource ds) {
        IProcessRepository repo = new JdbcProcessRepository(ds);
        IProcessExtRepository ext = new JdbcProcessExtRepository(ds);

        Configuration config = new Configuration();     // ← 顺序有因：它内部才 setContext
        ServiceContext.put("json", new JacksonProvider());             // 自带（引擎核心不依赖 JSON 库）
        ServiceContext.put("repository", repo);
        ServiceContext.put("ext", ext);
        ServiceContext.put("expr", new SolonExpressionEvaluator());    // 见下
        // idGen 这行先省着：仓储有兜底号，但多实例会撞，上线前补一枚（见 §5.3）

        JeeflowEngine engine = new JeeflowEngineImpl();
        engine.configure(config);
        return new JeeflowFacade(engine, repo, ext);
    }
}
```

先 `put` 后 `new Configuration()` ⇒ 启动即 `必需的 SPI 未注册，请调用 ServiceContext.put() 注册实现`。
Spring 里这个顺序由 starter 代劳，手写装配才会踩。

表达式 SPI 用 Solon 自带的 SnEL 十行即可，决策边的 `amount > 1000` 原样可求值
（SpEL 适配件要先把变量名改写成 `#amount`，这步在 Solon 侧省掉）：

```java
public class SolonExpressionEvaluator implements IExpressionEvaluator {
    @Override
    public Object eval(String expression, Map<String, Object> context) {
        try {
            return SnelParser.getInstance().forEval().parse(expression, false).eval(context);
        } catch (Exception e) {
            return null;      // 求值失败按"不命中"处理
        }
    }
}
```

### 5.3 三处必须显式配置的点

| 点 | 怎么配 | 不配会怎样 |
|----|--------|-----------|
| id 字符串契约 | `app.yml` 一行 `solon.serialization.json.longAsString: true` | 出口 id 是裸数字，前端按字符串比对的通道全断（Spring 侧对应 `ToStringSerializer` 那枚模块） |
| 写事务 | 转发方法标 `@Transaction`，且数据源过 `TranUtils.getDataSourceProxy` | 未注册 `ITransactionTemplate` 时引擎走裸执行兜底：一次审批的建任务／写参与者／更新实例三张表不在同一事务，中途失败留脏数据 |
| 主键生成器 | 注册一枚 `IIdGenerator`（`ServiceContext.put("idGen", ...)`） | 仓储兜底号是「毫秒时间戳左移 10 位 ＋ 同毫秒序号」，只在单进程内唯一；多实例并发起单会撞主键 |

前两件缺一件就是半截接入——只标 `@Transaction` 而数据源没过代理，仓储拿的还是新连接，事务盖不住。

### 5.4 实测读数（Solon 4.1.1 × jeeflow-java，H2 内存库）

- 线性流全链：`processDesign/save` → `updateDefine` → `deploy` → `processInstance/startAndExecute`
  → leader 待办 1 条 → `processTask/execute(submitType=0)` → 实例 `state=20`（办结）
- 决策分支：同一份 `03-decision-expr` 流程，`amount=2000` 落 manager 节点、`amount=100` 落 director 节点
  ——分支由 SnEL 真求值决定，不是写死
- 事务腿：`@Transaction` 内先落一条设计再抛异常 ⇒ 该条不在库里；同一路径的正常腿照常提交
- 错误码：缺参 action 返回 `code=99999999` + 中文 msg，与 mldong 框架契约同形
- 中文：请求体按 `application/json;charset=utf-8` 送出，中文流程名存取一致。在 Windows 上排查乱码时
  先换成文件传体复测——命令行直传中文会被本机码页改写，那一层乱码与框架无关

## 6. 差异点对照表

| 要点 | Spring Boot | Spring MVC | Solon | JAX-RS（Jersey 等） |
|------|-------------|-----------|-------|---------------------|
| 多段路径捕获 | `@PostMapping("/wf/**")` + URI 截取 | 同左 | `@Mapping("/wf/**")` + `ctx.path()` | `@Path("/wf/{action: .+}")` |
| 登录上下文 | LoginUserHolder / SecurityContext | 同左 | Solon 登录拦截器 / `Context` | SecurityContext |
| 装配 | starter 自动 | 手动 @Bean | 手动 @Bean（注意 §5.2 顺序） | 手动 + 容器集成 |
| JSON / 表达式 SPI | starter 内置 | starter 内置 | 各自带一枚（starter 那三枚拿不到） | 各自带一枚 |
| id 出口字符串 | `ToStringSerializer` 模块 | 同左 | `longAsString: true` 一行 | 宿主序列化器自配 |
| 参考实现 | mldong-boot4 集成仓 | — | 本文 §5 三段代码 | — |

> 引擎初始化（仓储/SPI/用户体系映射）见 [SDK 集成](./getting-started.md) 与 [SPI 实现指南](./spi-guide.md)；
> 深度接入（persist 落表/字段权限）见 [Spring Boot 集成](./spring-boot.md)。
