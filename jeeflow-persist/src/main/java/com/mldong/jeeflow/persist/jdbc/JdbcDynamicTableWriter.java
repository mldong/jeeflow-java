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
import java.util.function.Function;

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
    /** 列探测（issues/22：限定当前 schema，防多库同名表列重复）——
     *  MySQL：DATABASE() + EXTRA(自增) + COLUMN_KEY(主键)；H2/PG：CURRENT_SCHEMA() + IS_IDENTITY + JOIN 主键约束 */
    private static final String SCHEMA_SQL_MYSQL =
            "SELECT column_name, extra, column_key FROM information_schema.columns "
                    + "WHERE UPPER(table_name) = UPPER(?) AND table_schema = DATABASE() "
                    + "ORDER BY ordinal_position";
    private static final String SCHEMA_SQL_STD =
            "SELECT c.column_name, c.is_identity, c.column_default, "
                    + "CASE WHEN kcu.column_name IS NOT NULL THEN 'PRI' ELSE '' END AS column_key "
                    + "FROM information_schema.columns c "
                    + "LEFT JOIN information_schema.table_constraints tc "
                    + "  ON tc.table_name = c.table_name AND tc.constraint_type = 'PRIMARY KEY' "
                    + "  AND tc.table_schema = c.table_schema "
                    + "LEFT JOIN information_schema.key_column_usage kcu "
                    + "  ON kcu.constraint_name = tc.constraint_name AND kcu.column_name = c.column_name "
                    + "  AND kcu.table_schema = c.table_schema "
                    + "WHERE UPPER(c.table_name) = UPPER(?) AND c.table_schema = CURRENT_SCHEMA() "
                    + "ORDER BY c.ordinal_position";

    private final DataSource dataSource;
    private final Map<String, List<ColumnMeta>> schemaCache = new ConcurrentHashMap<>();
    /** 方言（懒探测）：mysql 用 EXTRA/COLUMN_KEY，其余（H2/PG）用标准 SQL */
    private volatile Boolean mysql;    // null=未探测

    /** 系统字段列约定（可配置；null = 不填充该列） */
    private String createTimeColumn = "create_time";
    private String createUserColumn = "create_user";
    private String updateTimeColumn = "update_time";
    private String updateUserColumn = "update_user";
    private String isDeletedColumn = "is_deleted";
    /** 用户列默认值（issues/19：优先取 data 中已注入的 apply_user_id=流程 operator，
     *  否则用此配置值，缺省 "system"）——多数框架业务表 create_user/update_user 为 BIGINT 存 userId */
    private Object defaultUserValue = "system";
    /** 列匹配（issues/20）：默认宽松——驼峰↔下划线归一匹配（表单字段 companyName ↔ 表列 company_name）；
     *  需要精确控制列名的集成方显式开启严格模式（忽略大小写精确匹配） */
    private boolean strictColumnMatch = false;
    /** 主键生成器（issues/21）：非自增主键表（雪花/应用生成）插入时生成主键值，入参表名 */
    private Function<String, Object> primaryKeyGenerator;

    public JdbcDynamicTableWriter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // ── 系统字段配置 ───────────────────────────────────────────────────────────

    public void setCreateTimeColumn(String createTimeColumn) { this.createTimeColumn = createTimeColumn; }
    public void setCreateUserColumn(String createUserColumn) { this.createUserColumn = createUserColumn; }
    public void setUpdateTimeColumn(String updateTimeColumn) { this.updateTimeColumn = updateTimeColumn; }
    public void setUpdateUserColumn(String updateUserColumn) { this.updateUserColumn = updateUserColumn; }
    public void setIsDeletedColumn(String isDeletedColumn) { this.isDeletedColumn = isDeletedColumn; }
    public void setDefaultUserValue(Object defaultUserValue) { this.defaultUserValue = defaultUserValue; }
    public void setStrictColumnMatch(boolean strictColumnMatch) { this.strictColumnMatch = strictColumnMatch; }
    public void setPrimaryKeyGenerator(Function<String, Object> primaryKeyGenerator) { this.primaryKeyGenerator = primaryKeyGenerator; }

    // ── DynamicTableWriter ─────────────────────────────────────────────────────

    @Override
    public List<String> filterColumns(String tableName, List<String> columns) {
        List<ColumnMeta> meta = tableMeta(tableName);
        List<String> result = new ArrayList<>();
        for (String column : columns) {
            if (column == null || column.trim().isEmpty()) continue;
            if (findColumn(meta, column.trim()) != null && !result.contains(column.trim())) {
                result.add(column.trim());
            }
        }
        return result;
    }

    @Override
    public Object insert(String tableName, Map<String, Object> data) {
        validateTableName(tableName);
        List<ColumnMeta> meta = tableMeta(tableName);
        // 列过滤（保序）——写入用表列原名（宽松模式下驼峰 key 落库为下划线列名）
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (ColumnMeta m : meta) {
            String col = m.getColumnName();
            String key = findDataKey(data, col);
            if (key != null) {
                columns.add(col);
                values.add(data.get(key));
                continue;
            }
            // 主键生成（issues/21）：非自增主键表且 data 无主键值 → 调生成器；未配置 → 清晰报错
            if (m.isPrimaryKey() && !m.isAutoIncrement()) {
                if (primaryKeyGenerator == null) {
                    throw new IllegalArgumentException("表[" + tableName + "]主键[" + col
                            + "]非自增且未配置主键生成器（请调用 setPrimaryKeyGenerator，如雪花 IdWorker）");
                }
                columns.add(col);
                values.add(primaryKeyGenerator.apply(tableName));
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
            if (createUserColumn != null) data.putIfAbsent(createUserColumn, resolveDefaultUser(data));
            if (updateTimeColumn != null) data.putIfAbsent(updateTimeColumn, now);
            if (updateUserColumn != null) data.putIfAbsent(updateUserColumn, resolveDefaultUser(data));
            if (isDeletedColumn != null) data.putIfAbsent(isDeletedColumn, 0);
        } else {
            // 更新：只填 update*
            if (updateTimeColumn != null) data.put(updateTimeColumn, now);
            if (updateUserColumn != null) data.putIfAbsent(updateUserColumn, resolveDefaultUser(data));
        }
    }

    /**
     * 默认用户值（issues/19）：优先取 data 中已注入的 {@code apply_user_id}
     * （拦截器场景 = 流程 operator，BIGINT 用户列表开箱即用），否则回落配置默认值。
     */
    private Object resolveDefaultUser(Map<String, Object> data) {
        Object operator = data.get("apply_user_id");
        return operator != null ? operator : defaultUserValue;
    }

    // ── 内部 ───────────────────────────────────────────────────────────────────

    /** 表结构（缓存）；表不存在返回空列表 */
    private List<ColumnMeta> tableMeta(String tableName) {
        return schemaCache.computeIfAbsent(tableName.toLowerCase(), k -> loadTableMeta(tableName));
    }

    private List<ColumnMeta> loadTableMeta(String tableName) {
        List<ColumnMeta> meta = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            String sql = isMysql(conn) ? SCHEMA_SQL_MYSQL : SCHEMA_SQL_STD;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tableName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        // MySQL: (1)column_name (2)extra (3)column_key
                        // H2/PG:  (1)column_name (2)is_identity (3)column_default (4)column_key
                        boolean primaryKey = "PRI".equalsIgnoreCase(rs.getString(isMysql(conn) ? 3 : 4));
                        boolean autoIncrement;
                        if (isMysql(conn)) {
                            autoIncrement = rs.getString(2) != null
                                    && rs.getString(2).toLowerCase().contains("auto_increment");
                        } else {
                            // H2: is_identity=YES；PG: is_identity=YES（identity）或 column_default 含 nextval（serial）
                            autoIncrement = "YES".equalsIgnoreCase(rs.getString(2))
                                    || (rs.getString(3) != null && rs.getString(3).contains("nextval"));
                        }
                        meta.add(new ColumnMeta(rs.getString(1), primaryKey, autoIncrement));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("读取表结构失败: " + tableName + " -> " + e.getMessage(), e);
        }
        return meta;
    }

    /** 方言探测：MySQL 走 EXTRA/COLUMN_KEY；H2/PG 走标准 SQL（IS_IDENTITY + 主键约束 JOIN） */
    private boolean isMysql(Connection conn) {
        Boolean m = mysql;
        if (m == null) {
            synchronized (this) {
                m = mysql;
                if (m == null) {
                    try {
                        m = conn.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
                    } catch (SQLException e) {
                        m = false;
                    }
                    mysql = m;
                }
            }
        }
        return m;
    }

    /** 列匹配（issues/20）：严格=忽略大小写精确；宽松（默认）=驼峰↔下划线归一匹配 */
    private ColumnMeta findColumn(List<ColumnMeta> meta, String column) {
        for (ColumnMeta m : meta) {
            if (strictColumnMatch) {
                if (m.getColumnName().equalsIgnoreCase(column)) return m;
            } else {
                if (normalizeColumn(m.getColumnName()).equals(normalizeColumn(column))) return m;
            }
        }
        return null;
    }

    /** 列名归一：转小写 + 去下划线（companyName / company_name / COMPANY_NAME 等价） */
    private static String normalizeColumn(String name) {
        return name.toLowerCase().replace("_", "");
    }

    /** 在 data 中找匹配指定表列的 key（宽松模式驼峰 key 匹配下划线列） */
    private String findDataKey(Map<String, Object> data, String col) {
        for (String k : data.keySet()) {
            if (k == null) continue;
            if (strictColumnMatch) {
                if (col.equalsIgnoreCase(k)) return k;
            } else if (normalizeColumn(col).equals(normalizeColumn(k))) {
                return k;
            }
        }
        return null;
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
