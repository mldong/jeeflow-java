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

    @Test
    public void legacyBehaviorUnchanged() {
        // 回归：原放行集与 OR 规则不受影响
        Assert.assertNull(provider.permissionCodes("processInstance/approvalRecord"));
        Assert.assertArrayEquals(
                new String[]{"wf:processDefine:detail", "wf:processDesign:listByType"},
                provider.permissionCodes("processDefine/detail"));
    }
}
