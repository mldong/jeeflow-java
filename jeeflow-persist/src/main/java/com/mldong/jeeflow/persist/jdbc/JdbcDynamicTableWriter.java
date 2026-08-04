package com.mldong.jeeflow.persist.jdbc;

import com.mldong.jeeflow.persist.DynamicTableWriter;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBC 动态表写入默认实现（issues/18）——零 ORM，仅依赖 {@link DataSource}。
 *
 * <ul>
 *   <li>表结构：information_schema.columns（MySQL/PG/H2 一致），首次查询后缓存</li>
 *   <li>参数化 INSERT：PreparedStatement 占位符，防注入</li>
 *   <li>表名安全：拒绝 {@code sys_} 前缀 / 非法字符（;、空格、引号）</li>
 *   <li>类型转换：String/Number/Boolean 直传，LocalDateTime 格式化为 yyyy-MM-dd HH:mm:ss</li>
 * </ul>
 *
 * @author mldong
 */
public class JdbcDynamicTableWriter implements DynamicTableWriter {

    private static final String SYS_PREFIX = "sys_";
    private static final String SCHEMA_SQL =
            "SELECT column_name FROM information_schema.columns "
                    + "WHERE UPPER(table_name) = UPPER(?) ORDER BY ordinal_position";

    private final DataSource dataSource;
    private final Map<String, List<ColumnMeta>> schemaCache = new ConcurrentHashMap<>();

    /** 系统字段列约定（可配置；null = 不填充该列） */
    private String createTimeColumn = "create_time";
    private String createUserColumn = "create_user";
    private String updateTimeColumn = "update_time";
    private String updateUserColumn = "update_user";
    private String isDeletedColumn = "is_deleted";

    public JdbcDynamicTableWriter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // ── 系统字段配置 ───────────────────────────────────────────────────────────

    public void setCreateTimeColumn(String createTimeColumn) { this.createTimeColumn = createTimeColumn; }
    public void setCreateUserColumn(String createUserColumn) { this.createUserColumn = createUserColumn; }
    public void setUpdateTimeColumn(String updateTimeColumn) { this.updateTimeColumn = updateTimeColumn; }
    public void setUpdateUserColumn(String updateUserColumn) { this.updateUserColumn = updateUserColumn; }
    public void setIsDeletedColumn(String isDeletedColumn) { this.isDeletedColumn = isDeletedColumn; }

    // ── DynamicTableWriter ─────────────────────────────────────────────────────

    @Override
    public List<String> filterColumns(String tableName, List<String> columns) {
        List<ColumnMeta> meta = tableMeta(tableName);
        List<String> result = new ArrayList<>();
        for (String column : columns) {
            if (column == null || column.trim().isEmpty()) continue;
            if (containsColumn(meta, column.trim()) && !result.contains(column.trim())) {
                result.add(column.trim());
            }
        }
        return result;
    }

    @Override
    public Object insert(String tableName, Map<String, Object> data) {
        validateTableName(tableName);
        List<ColumnMeta> meta = tableMeta(tableName);
        // 列过滤（保序）
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Map.Entry<String, Object> e : data.entrySet()) {
            String col = e.getKey();
            if (col == null) continue;
            if (containsColumn(meta, col.trim())) {
                columns.add(col.trim());
                values.add(e.getValue());
            }
        }
        if (columns.isEmpty()) return null;

        StringBuilder sql = new StringBuilder("INSERT INTO ").append(tableName)
                .append(" (").append(String.join(", ", columns)).append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("?");
        }
        sql.append(")");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString(), Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < values.size(); i++) {
                ps.setObject(i + 1, toJdbcValue(values.get(i)));
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getObject(1);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("动态表插入失败: " + tableName + " -> " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(String tableName, String bizKey, Object bizKeyValue) {
        validateTableName(tableName);
        String sql = "SELECT COUNT(1) FROM " + tableName + " WHERE " + bizKey + " = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, toJdbcValue(bizKeyValue));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("幂等检查失败: " + tableName + " -> " + e.getMessage(), e);
        }
    }

    @Override
    public void fillSystemFields(Map<String, Object> data, boolean insert) {
        String now = ColumnMeta.now();
        if (insert) {
            // 插入：create* + update* + isDeleted 全填（很多表插入时即填 update 列）
            if (createTimeColumn != null) data.putIfAbsent(createTimeColumn, now);
            if (createUserColumn != null) data.putIfAbsent(createUserColumn, "system");
            if (updateTimeColumn != null) data.putIfAbsent(updateTimeColumn, now);
            if (updateUserColumn != null) data.putIfAbsent(updateUserColumn, "system");
            if (isDeletedColumn != null) data.putIfAbsent(isDeletedColumn, 0);
        } else {
            // 更新：只填 update*
            if (updateTimeColumn != null) data.put(updateTimeColumn, now);
            if (updateUserColumn != null) data.putIfAbsent(updateUserColumn, "system");
        }
    }

    // ── 内部 ───────────────────────────────────────────────────────────────────

    /** 表结构（缓存）；表不存在返回空列表 */
    private List<ColumnMeta> tableMeta(String tableName) {
        return schemaCache.computeIfAbsent(tableName.toLowerCase(), k -> loadTableMeta(tableName));
    }

    private List<ColumnMeta> loadTableMeta(String tableName) {
        List<ColumnMeta> meta = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SCHEMA_SQL)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    meta.add(new ColumnMeta(rs.getString(1), false));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("读取表结构失败: " + tableName + " -> " + e.getMessage(), e);
        }
        return meta;
    }

    private boolean containsColumn(List<ColumnMeta> meta, String column) {
        for (ColumnMeta m : meta) {
            if (m.getColumnName().equalsIgnoreCase(column)) return true;
        }
        return false;
    }

    /** 表名安全：非空、非 sys_ 前缀、无非法字符 */
    private void validateTableName(String tableName) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("表名不能为空");
        }
        String t = tableName.trim();
        if (t.toLowerCase().startsWith(SYS_PREFIX)) {
            throw new IllegalArgumentException("拒绝写入系统表: " + t);
        }
        for (char c : t.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_') {
                throw new IllegalArgumentException("表名含非法字符: " + t);
            }
        }
    }

    /** 值转换：LocalDateTime → 字符串（驱动无关的时间格式） */
    private Object toJdbcValue(Object value) {
        if (value instanceof LocalDateTime) {
            return ColumnMeta.now();
        }
        return value;
    }
}
