package com.mldong.jeeflow.spi;

/**
 * action → 权限码映射提供者（issues/29，v1.8.3）
 *
 * <p>引擎不依赖任何鉴权框架（sa-token/Spring Security 是宿主框架的事）——本 SPI 只提供
 * 「action → 权限码」映射元数据，校验执行仍由集成方框架层完成（如 {@code StpUtil.checkPermissionOr}）。</p>
 *
 * <p>引擎内置默认实现 {@link DefaultActionPermissionProvider}（从 boot3 端点注解归纳），
 * 集成方零配置即得统一权限语义；特殊集成方（不同权限码体系）可覆盖注册。</p>
 *
 * @author mldong
 */
public interface IActionPermissionProvider {

    /**
     * 返回 action 的权限码集合（OR 语义——任一持有即可访问）。
     *
     * @param action facade action（如 {@code "processDefine/detail"}）
     * @return 权限码数组；{@code null} / 空数组 = 放行（登录即可）
     */
    String[] permissionCodes(String action);
}
