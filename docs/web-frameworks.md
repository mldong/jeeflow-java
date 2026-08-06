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
│ ② 权限码动态校验            │  args   │  40 个 action 内置路由  │
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

## 5. 差异点对照表

| 要点 | Spring Boot | Spring MVC | JAX-RS（Jersey 等） |
|------|-------------|-----------|---------------------|
| 多段路径捕获 | `@PostMapping("/wf/**")` + URI 截取 | 同左 | `@Path("/wf/{action: .+}")` |
| 登录上下文 | LoginUserHolder / SecurityContext | 同左 | SecurityContext |
| 装配 | starter 自动 | 手动 @Bean | 手动 + 容器集成 |
| 参考实现 | mldong-boot4 集成仓 | — | — |

> 引擎初始化（仓储/SPI/用户体系映射）见 [SDK 集成](./getting-started.md) 与 [SPI 实现指南](./spi-guide.md)；
> 深度接入（persist 落表/字段权限）见 [Spring Boot 集成](./spring-boot.md)。
