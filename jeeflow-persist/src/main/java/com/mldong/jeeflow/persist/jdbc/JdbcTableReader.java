package com.mldong.jeeflow.persist.jdbc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务表查询器（issues/23 读取闭环底层）——按列等值查询原始行。
 *
 * <p>与写入器分离：{@link JdbcDynamicTableWriter} 保持纯写职责，
 * 读取（流程回显）由本类 + {@code MetaTableReader} 提供。</p>
 *
 * @author mldong
 */
public class JdbcTableReader {

    private final DataSource dataSource;

    public JdbcTableReader(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 查询首行——按指定列等值查询，返回列名→值（列名为数据库 label）。
     *
     * @param tableName   表名（过安全校验）
     * @param whereColumn 条件列（如 process_instance_id）
     * @param value       条件值
     */
    public Map<String, Object> queryFirst(String tableName, String whereColumn, Object value) {
        List<Map<String, Object>> rows = queryList(tableName, whereColumn, value, 1);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 查询列表——按指定列等值查询，limit 分页（0=不限制）。
     */
    public List<Map<String, Object>> queryList(String tableName, String whereColumn, Object value, int limit) {
        TableNames.validate(tableName);
        String sql = "SELECT * FROM " + tableName + " WHERE " + whereColumn + " = ?"
                + (limit > 0 ? " LIMIT " + limit : "");
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        row.put(md.getColumnLabel(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询失败: " + tableName + " -> " + e.getMessage(), e);
        }
    }
}
