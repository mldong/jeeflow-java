package com.mldong.jeeflow.spi;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link DefaultActionPermissionProvider} 三场景（issues/103 §10 stats 放行修复）。
 *
 * @author mldong
 */
public class DefaultActionPermissionProviderTest {

    private final DefaultActionPermissionProvider provider = new DefaultActionPermissionProvider();

    @Test
    public void statsActionsLoginOnly() {
        // 正向：stats 三 action 登录即可、不配权限码（spec 06 §2.6 放行清单）
        Assert.assertNull(provider.permissionCodes("processInstance/stats/overview"));
        Assert.assertNull(provider.permissionCodes("processInstance/stats/trend"));
        Assert.assertNull(provider.permissionCodes("processInstance/stats/group"));
    }

    @Test
    public void defaultRuleStillApplies() {
        // 负向：无码 action 仍走默认规则（不得误放行）
        Assert.assertArrayEquals(new String[]{"wf:processDefine:deploy"},
                provider.permissionCodes("processDefine/deploy"));
        Assert.assertArrayEquals(new String[]{"wf:processInstance:updateCCStatus"},
                provider.permissionCodes("processInstance/updateCCStatus"));
    }

    /**
     * issues/246／247 两枚新 action 的权限码粒度（owner 2026-11-04 拍 B）：复用 {@code wf:processTask:execute}，
     * 不另建 {@code sys_menu} 按钮种子。反向哨兵同钉在这里——两码必须**只有** execute 一枚，
     * 写成默认派生（{@code wf:processTask:recall}）就是回到 A 案，普通角色会因为没种子而 403。
     */
    @Test
    public void recallAndRevokeCountersignRideExecuteCode() {
        Assert.assertArrayEquals(new String[]{"wf:processTask:execute"},
                provider.permissionCodes("processTask/recall"));
        Assert.assertArrayEquals(new String[]{"wf:processTask:execute"},
                provider.permissionCodes("processTask/revokeDynamicCountersign"));
        // 哨兵：没进表的 action 仍走默认派生（证明上面两枚是真的命中规则，不是规则整体失效）
        Assert.assertArrayEquals(new String[]{"wf:processTask:transfer"},
                provider.permissionCodes("processTask/transfer"));
    }

    @Test
    public void legacyBehaviorUnchanged() {        // 回归：原放行集与 OR 规则不受影响
        Assert.assertNull(provider.permissionCodes("processInstance/approvalRecord"));
        Assert.assertArrayEquals(
                new String[]{"wf:processDefine:detail", "wf:processDesign:listByType"},
                provider.permissionCodes("processDefine/detail"));
    }
}
