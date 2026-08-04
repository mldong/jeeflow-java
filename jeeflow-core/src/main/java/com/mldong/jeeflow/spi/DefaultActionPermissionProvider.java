package com.mldong.jeeflow.spi;

import java.util.Map;
import java.util.Set;

/**
 * action → 权限码映射默认实现（issues/29）——从 boot3 端点注解语义归纳：
 *
 * <ul>
 *   <li>默认规则：{@code wf:{action.replace('/', ':')}}（如 {@code processDefine/page} → {@code wf:processDefine:page}）</li>
 *   <li>OR 语义 action：任一权限码持有即可（boot3 {@code @SaCheckPermission(value={...}, mode=OR)}）</li>
 *   <li>无权限注解 action：放行（登录即可，boot3 无注解 = 登录即可）</li>
 * </ul>
 *
 * @author mldong
 */
public class DefaultActionPermissionProvider implements IActionPermissionProvider {

    /** boot3 注解 OR 语义的 action（任选其一即有权） */
    private static final Map<String, String[]> OR_RULES = Map.ofEntries(
            Map.entry("processDefine/detail", new String[]{"wf:processDefine:detail", "wf:processDesign:listByType"}),
            Map.entry("processDefine/startAndExecute", new String[]{"wf:processDefine:startAndExecute", "wf:processDesign:listByType"}),
            Map.entry("processDefine/getLastByName", new String[]{"wf:processDefine:detail", "wf:processDesign:listByType", "wf:processDefine:getLastByName"}),
            Map.entry("processTask/candidatePage", new String[]{"wf:processTask:execute", "wf:processTask:candidatePage"}),
            Map.entry("processTask/jumpAbleTaskNameList", new String[]{"wf:processTask:execute"}));

    /** boot3 无权限注解的 action（登录即可） */
    private static final Set<String> NO_PERM_ACTIONS = Set.of(
            "processInstance/detail", "processInstance/highLight", "processInstance/approvalRecord",
            "processInstance/getAssigneeTextData", "processInstance/bizData",
            "processTask/detail", "processTask/addCandidate", "processTask/latest");

    @Override
    public String[] permissionCodes(String action) {
        if (action == null) return null;
        if (NO_PERM_ACTIONS.contains(action)) return null;   // 放行
        String[] or = OR_RULES.get(action);
        if (or != null) return or;                            // OR 语义
        return new String[]{"wf:" + action.replace("/", ":")}; // 默认
    }
}
