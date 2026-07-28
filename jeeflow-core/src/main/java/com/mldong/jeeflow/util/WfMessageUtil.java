package com.mldong.jeeflow.util;

import com.mldong.jeeflow.domain.FlowData;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作流消息工具类
 *
 * @author mldong
 */
public final class WfMessageUtil {

    private WfMessageUtil() {}

    /** 将逗号分割的字符串转为 List */
    public static List<String> splitActors(String actors) {
        if (StringUtils.isEmpty(actors)) return new ArrayList<>();
        List<String> result = new ArrayList<>();
        for (String s : actors.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
