package com.mldong.jeeflow.spi;

import java.util.Collection;
import java.util.Map;

/**
 * m_* 查询参数解析器——桥接 mldong-boot2 前端查询惯例
 *
 * <p>解析前端传入的 {@code m_{alias}_{type}_{column}} 参数，
 * 安全转换为 {@link PageQuery} 对象。列名在仓库层过白名单校验。</p>
 *
 * <p>v1.1.0：自 core 提供（JeeflowFacade 门面内部使用），
 * spring-boot-autoconfigure 中的同名类继承本类保持兼容。</p>
 *
 * <pre>{@code
 * JeeflowQueryParser parser = new JeeflowQueryParser();
 * PageQuery query = parser.parse(params);
 * query.add("pta.actor_id", "EQ", userId);
 * PageResult<ProcessTask.TaskRow> page = repository.pageTodoTasks(query);
 * }</pre>
 *
 * @author mldong
 */
public class JeeflowQueryParser {

    private static final String PREFIX = "m_";

    /**
     * 解析 HTTP 请求参数 Map（通常是 @RequestBody Map 或 @RequestParam Map）
     */
    public PageQuery parse(Map<String, Object> params) {
        PageQuery query = new PageQuery();

        // 基本分页参数
        query.setPageNum(toInt(params.get("pageNum"), 1));
        query.setPageSize(toInt(params.get("pageSize"), 10));
        query.setOrderBy(toStr(params.get("orderBy")));

        // 解析所有 m_* 参数
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!key.startsWith(PREFIX) || isEmpty(value)) continue;

            String suffix = key.substring(PREFIX.length());
            String[] parts = suffix.split("_");
            if (parts.length < 2) continue;

            String column;
            String operator;

            if (parts.length == 2) {
                // m_EQ_taskName → column="t.task_name", operator="EQ"
                // （默认主表别名 t，与白名单约定一致；前端 m_LIKE_name/m_LIKE_displayName 落主表列，issues/05-5）
                operator = parts[0];
                column = "t." + toUnderscore(parts[1]);
            } else {
                // m_t_EQ_taskName → column="t.task_name", operator="EQ"
                String alias = parts[0];
                operator = parts[1];
                column = alias + "." + toUnderscore(parts[2]);
            }

            query.add(column, operator.toUpperCase(), value);
        }

        return query;
    }

    private static String toUnderscore(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isEmpty(Object val) {
        return val == null || (val instanceof String && ((String) val).isEmpty())
                || (val instanceof Collection && ((Collection<?>) val).isEmpty());
    }

    private static int toInt(Object val, int def) {
        if (val == null) return def;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); } catch (NumberFormatException e) { return def; }
    }

    private static String toStr(Object val) {
        return val != null ? val.toString() : null;
    }
}
