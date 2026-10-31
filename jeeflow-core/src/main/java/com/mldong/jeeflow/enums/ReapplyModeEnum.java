package com.mldong.jeeflow.enums;

import com.mldong.jeeflow.JeeflowException;

/**
 * 退回发起人后的重提方式（流程节点 properties.reapplyMode）
 *
 * @author mldong
 */
public enum ReapplyModeEnum {
    /** 退回发起人后按申请节点的正常出边重提（缺省，保持既有行为） */
    NORMAL,
    /** 退回发起人后，重提直接回到本来源节点建待办 */
    RETURN_TO_SOURCE;

    /**
     * 解析配置值。缺省/空串⇒NORMAL；非法值必须报配置错误，不得静默当缺省。
     */
    public static ReapplyModeEnum parse(Object value, String nodeName) {
        if (value == null) {
            return NORMAL;
        }
        String v = String.valueOf(value).trim();
        if (v.isEmpty()) {
            return NORMAL;
        }
        if ("NORMAL".equals(v)) {
            return NORMAL;
        }
        if ("RETURN_TO_SOURCE".equals(v)) {
            return RETURN_TO_SOURCE;
        }
        throw new JeeflowException("节点[" + nodeName + "]的 reapplyMode 配置非法: " + v);
    }
}
