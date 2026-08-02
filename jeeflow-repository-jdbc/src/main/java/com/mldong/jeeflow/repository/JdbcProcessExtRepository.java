package com.mldong.jeeflow.repository;

import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.ProcessDesign;
import com.mldong.jeeflow.domain.ProcessDesignHis;
import com.mldong.jeeflow.domain.ProcessSurrogate;
import com.mldong.jeeflow.spi.IIdGenerator;
import com.mldong.jeeflow.spi.IProcessExtRepository;
import com.mldong.jeeflow.spi.PageQuery;
import com.mldong.jeeflow.spi.PageResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 扩展仓储 JDBC 参考实现（v1.1.0）——流程设计 / 设计历史 / 委托代理
 *
 * <p>与 {@link JdbcProcessRepository} 同一套分页/白名单约定（表别名 `t`，占位符 `?`）。</p>
 *
 * @author mldong
 */
public class JdbcProcessExtRepository implements IProcessExtRepository {

    private final DataSource dataSource;

    public JdbcProcessExtRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // ═══ 流程设计 ═══

    @Override
    public ProcessDesign findDesignById(Long designId) {
        String sql = "SELECT id, name, display_name, type, icon, is_deployed, remark, " +
                "create_time, create_user, update_time, update_user FROM wf_process_design WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, designId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapDesign(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询流程设计失败", e);
        }
        return null;
    }

    @Override
    public void saveDesign(ProcessDesign design) {
        if (design.getId() == null) design.setId(nextId());
        LocalDateTime now = LocalDateTime.now();
        if (design.getCreateTime() == null) design.setCreateTime(now);
        if (design.getUpdateTime() == null) design.setUpdateTime(now);
        String sql = "INSERT INTO wf_process_design (id, name, display_name, type, icon, is_deployed, remark, " +
                "create_time, create_user, update_time, update_user) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, design.getId());
            ps.setString(2, design.getName());
            ps.setString(3, design.getDisplayName());
            ps.setString(4, design.getType());
            ps.setString(5, design.getIcon());
            ps.setInt(6, design.getIsDeployed() != null ? design.getIsDeployed() : 0);
            ps.setString(7, design.getRemark());
            ps.setTimestamp(8, toTimestamp(design.getCreateTime()));
            ps.setString(9, design.getCreateUser());
            ps.setTimestamp(10, toTimestamp(design.getUpdateTime()));
            ps.setString(11, design.getUpdateUser());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("保存流程设计失败", e);
        }
    }

    @Override
    public void updateDesign(ProcessDesign design) {
        String sql = "UPDATE wf_process_design SET name=?, display_name=?, type=?, icon=?, is_deployed=?, " +
                "remark=?, update_time=?, update_user=? WHERE id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, design.getName());
            ps.setString(2, design.getDisplayName());
            ps.setString(3, design.getType());
            ps.setString(4, design.getIcon());
            ps.setInt(5, design.getIsDeployed() != null ? design.getIsDeployed() : 0);
            ps.setString(6, design.getRemark());
            ps.setTimestamp(7, toTimestamp(LocalDateTime.now()));
            ps.setString(8, design.getUpdateUser());
            ps.setLong(9, design.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("更新流程设计失败", e);
        }
    }

    @Override
    public void removeDesign(Long designId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM wf_process_design WHERE id=?")) {
            ps.setLong(1, designId);
            ps.executeUpdate();
            // 连带删除历史快照
            try (PreparedStatement ps2 = conn.prepareStatement("DELETE FROM wf_process_design_his WHERE process_design_id=?")) {
                ps2.setLong(1, designId);
                ps2.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("删除流程设计失败", e);
        }
    }

    @Override
    public PageResult<ProcessDesign> pageDesigns(PageQuery query) {
        StringBuilder baseSql = new StringBuilder("FROM wf_process_design t WHERE 1=1");
        List<Object> params = new ArrayList<>();
        buildWhere(baseSql, params, query, DESIGN_WHITELIST);

        int total = queryCount("SELECT COUNT(*) " + baseSql, params);
        buildOrder(baseSql, query, "t.", DESIGN_WHITELIST);
        baseSql.append(" LIMIT ? OFFSET ?");
        params.add(query.getPageSize());
        params.add((query.getPageNum() - 1) * query.getPageSize());

        List<ProcessDesign> rows = queryList("SELECT t.* " + baseSql, params, rs -> {
            ProcessDesign d = mapDesign(rs);
            return d;
        });
        return PageResult.of(query.getPageNum(), query.getPageSize(), total, rows);
    }

    // ═══ 设计历史 ═══

    @Override
    public void saveDesignHis(ProcessDesignHis his) {
        if (his.getId() == null) his.setId(nextId());
        if (his.getCreateTime() == null) his.setCreateTime(LocalDateTime.now());
        String sql = "INSERT INTO wf_process_design_his (id, process_design_id, content, create_time, create_user) " +
                "VALUES (?,?,?,?,?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, his.getId());
            ps.setLong(2, his.getProcessDesignId());
            ps.setBytes(3, his.getContent());
            ps.setTimestamp(4, toTimestamp(his.getCreateTime()));
            ps.setString(5, his.getCreateUser());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("保存设计历史失败", e);
        }
    }

    @Override
    public List<ProcessDesignHis> listDesignHis(Long designId) {
        String sql = "SELECT id, process_design_id, content, create_time, create_user " +
                "FROM wf_process_design_his WHERE process_design_id = ? ORDER BY id DESC";
        return queryList(sql, List.of(designId), rs -> {
            ProcessDesignHis his = new ProcessDesignHis();
            his.setId(rs.getLong("id"));
            his.setProcessDesignId(rs.getLong("process_design_id"));
            his.setContent(rs.getBytes("content"));
            his.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
            his.setCreateUser(rs.getString("create_user"));
            return his;
        });
    }

    // ═══ 委托代理 ═══

    @Override
    public ProcessSurrogate findSurrogateById(Long surrogateId) {
        String sql = "SELECT id, process_name, operator, surrogate, start_time, end_time, enabled, " +
                "create_time, create_user, update_time, update_user FROM wf_process_surrogate WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, surrogateId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapSurrogate(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询委托记录失败", e);
        }
        return null;
    }

    @Override
    public void saveSurrogate(ProcessSurrogate surrogate) {
        if (surrogate.getId() == null) surrogate.setId(nextId());
        LocalDateTime now = LocalDateTime.now();
        if (surrogate.getCreateTime() == null) surrogate.setCreateTime(now);
        if (surrogate.getUpdateTime() == null) surrogate.setUpdateTime(now);
        if (surrogate.getEnabled() == null) surrogate.setEnabled(1);
        String sql = "INSERT INTO wf_process_surrogate (id, process_name, operator, surrogate, start_time, " +
                "end_time, enabled, create_time, create_user, update_time, update_user) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, surrogate.getId());
            ps.setString(2, surrogate.getProcessName());
            ps.setString(3, surrogate.getOperator());
            ps.setString(4, surrogate.getSurrogate());
            ps.setTimestamp(5, toTimestamp(surrogate.getStartTime()));
            ps.setTimestamp(6, toTimestamp(surrogate.getEndTime()));
            ps.setInt(7, surrogate.getEnabled());
            ps.setTimestamp(8, toTimestamp(surrogate.getCreateTime()));
            ps.setString(9, surrogate.getCreateUser());
            ps.setTimestamp(10, toTimestamp(surrogate.getUpdateTime()));
            ps.setString(11, surrogate.getUpdateUser());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("保存委托记录失败", e);
        }
    }

    @Override
    public void updateSurrogate(ProcessSurrogate surrogate) {
        String sql = "UPDATE wf_process_surrogate SET process_name=?, operator=?, surrogate=?, start_time=?, " +
                "end_time=?, enabled=?, update_time=?, update_user=? WHERE id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, surrogate.getProcessName());
            ps.setString(2, surrogate.getOperator());
            ps.setString(3, surrogate.getSurrogate());
            ps.setTimestamp(4, toTimestamp(surrogate.getStartTime()));
            ps.setTimestamp(5, toTimestamp(surrogate.getEndTime()));
            ps.setInt(6, surrogate.getEnabled() != null ? surrogate.getEnabled() : 1);
            ps.setTimestamp(7, toTimestamp(LocalDateTime.now()));
            ps.setString(8, surrogate.getUpdateUser());
            ps.setLong(9, surrogate.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("更新委托记录失败", e);
        }
    }

    @Override
    public void removeSurrogate(Long surrogateId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM wf_process_surrogate WHERE id=?")) {
            ps.setLong(1, surrogateId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("删除委托记录失败", e);
        }
    }

    @Override
    public PageResult<ProcessSurrogate> pageSurrogates(PageQuery query) {
        StringBuilder baseSql = new StringBuilder("FROM wf_process_surrogate t WHERE 1=1");
        List<Object> params = new ArrayList<>();
        buildWhere(baseSql, params, query, SURROGATE_WHITELIST);

        int total = queryCount("SELECT COUNT(*) " + baseSql, params);
        buildOrder(baseSql, query, "t.", SURROGATE_WHITELIST);
        baseSql.append(" LIMIT ? OFFSET ?");
        params.add(query.getPageSize());
        params.add((query.getPageNum() - 1) * query.getPageSize());

        List<ProcessSurrogate> rows = queryList("SELECT t.* " + baseSql, params, rs -> mapSurrogate(rs));
        return PageResult.of(query.getPageNum(), query.getPageSize(), total, rows);
    }

    @Override
    public ProcessSurrogate getSurrogate(String operator, String processName, LocalDateTime time) {
        // 1. 精确匹配流程
        ProcessSurrogate hit = querySurrogate(operator, processName, time);
        if (hit != null) return hit;
        // 2. 全流程委托兜底（process_name 为空）
        return querySurrogate(operator, "", time);
    }

    private ProcessSurrogate querySurrogate(String operator, String processName, LocalDateTime time) {
        StringBuilder sql = new StringBuilder("SELECT id, process_name, operator, surrogate, start_time, end_time, enabled, " +
                "create_time, create_user, update_time, update_user FROM wf_process_surrogate " +
                "WHERE operator = ? AND enabled = 1 AND surrogate <> ?");
        List<Object> params = new ArrayList<>();
        params.add(operator);
        params.add(operator);
        if (processName == null || processName.isEmpty()) {
            sql.append(" AND (process_name IS NULL OR process_name = '')");
        } else {
            sql.append(" AND process_name = ?");
            params.add(processName);
        }
        if (time != null) {
            sql.append(" AND (start_time IS NULL OR start_time <= ?) AND (end_time IS NULL OR end_time >= ?)");
            params.add(Timestamp.valueOf(time));
            params.add(Timestamp.valueOf(time));
        }
        sql.append(" ORDER BY id DESC LIMIT 1");
        List<ProcessSurrogate> list = queryList(sql.toString(), params, rs -> mapSurrogate(rs));
        return list.isEmpty() ? null : list.get(0);
    }

    // ═══ 行映射 ═══

    private static ProcessDesign mapDesign(ResultSet rs) throws SQLException {
        ProcessDesign d = new ProcessDesign();
        d.setId(rs.getLong("id"));
        d.setName(rs.getString("name"));
        d.setDisplayName(rs.getString("display_name"));
        d.setType(rs.getString("type"));
        d.setIcon(rs.getString("icon"));
        d.setIsDeployed(getInt(rs, "is_deployed"));
        d.setRemark(rs.getString("remark"));
        d.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
        d.setCreateUser(rs.getString("create_user"));
        d.setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));
        d.setUpdateUser(rs.getString("update_user"));
        return d;
    }

    private static ProcessSurrogate mapSurrogate(ResultSet rs) throws SQLException {
        ProcessSurrogate s = new ProcessSurrogate();
        s.setId(rs.getLong("id"));
        s.setProcessName(rs.getString("process_name"));
        s.setOperator(rs.getString("operator"));
        s.setSurrogate(rs.getString("surrogate"));
        s.setStartTime(toLocalDateTime(rs.getTimestamp("start_time")));
        s.setEndTime(toLocalDateTime(rs.getTimestamp("end_time")));
        s.setEnabled(getInt(rs, "enabled"));
        s.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
        s.setCreateUser(rs.getString("create_user"));
        s.setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));
        s.setUpdateUser(rs.getString("update_user"));
        return s;
    }

    // ═══ 分页基建（与 JdbcProcessRepository 同一约定） ═══

    private static final Set<String> DESIGN_WHITELIST = new HashSet<>(Arrays.asList(
            "t.id", "t.name", "t.display_name", "t.type", "t.is_deployed", "t.remark",
            "t.create_time", "t.update_time"
    ));

    private static final Set<String> SURROGATE_WHITELIST = new HashSet<>(Arrays.asList(
            "t.id", "t.process_name", "t.operator", "t.surrogate", "t.enabled",
            "t.start_time", "t.end_time", "t.create_time", "t.update_time"
    ));

    private void buildWhere(StringBuilder sql, List<Object> params, PageQuery query, Set<String> whitelist) {
        for (PageQuery.Condition cond : query.getConditions()) {
            String col = cond.getColumn();
            if (!whitelist.contains(col)) continue; // 不在白名单，丢弃
            Object val = cond.getValue();
            if (val == null || (val instanceof String && ((String) val).isEmpty())) continue;

            switch (cond.getOperator().toUpperCase()) {
                case "EQ":
                    sql.append(" AND ").append(col).append(" = ?");
                    params.add(val);
                    break;
                case "NE":
                    sql.append(" AND ").append(col).append(" <> ?");
                    params.add(val);
                    break;
                case "LIKE":
                case "LLIKE":
                case "RLIKE":
                    sql.append(" AND ").append(col).append(" LIKE ?");
                    String v = val.toString();
                    if ("LIKE".equalsIgnoreCase(cond.getOperator())) v = "%" + v + "%";
                    else if ("LLIKE".equalsIgnoreCase(cond.getOperator())) v = "%" + v;
                    else v = v + "%";
                    params.add(v);
                    break;
                case "GT":
                    sql.append(" AND ").append(col).append(" > ?");
                    params.add(val);
                    break;
                case "GE":
                    sql.append(" AND ").append(col).append(" >= ?");
                    params.add(val);
                    break;
                case "LT":
                    sql.append(" AND ").append(col).append(" < ?");
                    params.add(val);
                    break;
                case "LE":
                    sql.append(" AND ").append(col).append(" <= ?");
                    params.add(val);
                    break;
                case "IN":
                case "NIN":
                    if (val instanceof List) {
                        List<?> list = (List<?>) val;
                        if (list.isEmpty()) continue;
                        sql.append(" AND ").append(col).append("IN".equalsIgnoreCase(cond.getOperator()) ? " IN (" : " NOT IN (");
                        for (int i = 0; i < list.size(); i++) {
                            sql.append(i == 0 ? "?" : ",?");
                            params.add(list.get(i));
                        }
                        sql.append(")");
                    }
                    break;
                case "BT":
                    if (val instanceof List && ((List<?>) val).size() == 2) {
                        List<?> list = (List<?>) val;
                        sql.append(" AND ").append(col).append(" BETWEEN ? AND ?");
                        params.add(list.get(0));
                        params.add(list.get(1));
                    }
                    break;
            }
        }
    }

    private void buildOrder(StringBuilder sql, PageQuery query, String defaultAlias, Set<String> whitelist) {
        String orderBy = query.getOrderBy();
        if (orderBy == null || orderBy.isEmpty()) {
            sql.append(" ORDER BY ").append(defaultAlias).append("id DESC");
            return;
        }
        StringBuilder order = new StringBuilder();
        for (String part : orderBy.split(",")) {
            String[] kv = part.trim().split("\\s+");
            String col = kv[0];
            String dir = kv.length > 1 ? kv[1].toUpperCase() : "ASC";
            if (!whitelist.contains(col) && !whitelist.contains(defaultAlias + col)) {
                col = defaultAlias + col;
                if (!whitelist.contains(col)) continue;
            }
            if (order.length() > 0) order.append(", ");
            order.append(col).append(" ").append(dir);
        }
        if (order.length() > 0) {
            sql.append(" ORDER BY ").append(order);
        } else {
            sql.append(" ORDER BY ").append(defaultAlias).append("id DESC");
        }
    }

    private int queryCount(String sql, List<Object> params) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("count 查询失败", e);
        }
    }

    @FunctionalInterface
    private interface RowMapper<T> { T map(ResultSet rs) throws SQLException; }

    private <T> List<T> queryList(String sql, List<Object> params, RowMapper<T> mapper) {
        List<T> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapper.map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询失败", e);
        }
        return result;
    }

    // ═══ 工具 ═══

    private static Timestamp toTimestamp(LocalDateTime ldt) {
        if (ldt == null) return null;
        return Timestamp.valueOf(ldt);
    }

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        if (ts == null) return null;
        return ts.toLocalDateTime();
    }

    private static Integer getInt(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }

    private static long lastMillis = 0;
    private static int seq = 0;

    private static synchronized long nextId() {
        IIdGenerator gen = ServiceContext.find(IIdGenerator.class);
        if (gen != null) return gen.nextId();
        long ts = System.currentTimeMillis();
        if (ts == lastMillis) {
            seq++;
        } else {
            seq = 0;
            lastMillis = ts;
        }
        return (ts << 10) | (seq & 0x3FF);
    }
}
