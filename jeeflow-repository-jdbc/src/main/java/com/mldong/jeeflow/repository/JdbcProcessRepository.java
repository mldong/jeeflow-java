package com.mldong.jeeflow.repository;

import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.enums.ProcessTaskTypeEnum;
import com.mldong.jeeflow.enums.ProcessTaskPerformTypeEnum;
import com.mldong.jeeflow.json.IJsonProvider;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.spi.IProcessRepository;
import com.mldong.jeeflow.spi.IIdGenerator;
import com.mldong.jeeflow.spi.PageQuery;
import com.mldong.jeeflow.spi.PageResult;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 纯 JDBC 的工作流仓储实现——零 ORM 依赖
 *
 * <p>使用原版 mldong-boot2 的表结构（wf_* 系列表）。
 * 注入 DataSource 即可对接任意数据库（MySQL / H2 / PostgreSQL 等）。</p>
 *
 * @author mldong
 */
public class JdbcProcessRepository implements IProcessRepository {

    private final DataSource dataSource;

    public JdbcProcessRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // ═══ 流程定义 ═══

    @Override
    public ProcessInstance.ProcessDefine findDefineById(Long defineId) {
        String sql = "SELECT id, name, display_name, type, state, content, version FROM wf_process_define WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, defineId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
                    def.setId(rs.getLong("id"));
                    def.setName(rs.getString("name"));
                    def.setDisplayName(rs.getString("display_name"));
                    def.setType(rs.getString("type"));
                    def.setState(rs.getInt("state"));
                    def.setContent(rs.getBytes("content"));
                    def.setVersion(rs.getInt("version"));
                    return def;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询流程定义失败", e);
        }
        return null;
    }

    // ═══ 流程实例 ═══

    @Override
    public ProcessInstance findInstanceById(Long instanceId) {
        String sql = "SELECT id, parent_id, process_define_id, state, parent_node_name, " +
                "business_no, operator, expire_time, variable, " +
                "create_time, create_user, update_time, update_user " +
                "FROM wf_process_instance WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, instanceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ProcessInstance inst = mapInstance(rs);
                    // 加载关联任务
                    inst.setTasks(findTasksByInstanceId(conn, instanceId));
                    return inst;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询流程实例失败", e);
        }
        return null;
    }

    @Override
    public void saveInstance(ProcessInstance instance) {
        if (instance.getInstanceId() == null) {
            instance.setInstanceId(nextId());
        }
        String sql = "INSERT INTO wf_process_instance " +
                "(id, parent_id, process_define_id, state, parent_node_name, business_no, operator, " +
                "expire_time, variable, create_time, create_user, update_time, update_user) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setInstanceParams(ps, instance);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("保存流程实例失败", e);
        }
    }

    @Override
    public void updateInstance(ProcessInstance instance) {
        String sql = "UPDATE wf_process_instance SET state=?, parent_node_name=?, expire_time=?, " +
                "variable=?, update_time=?, update_user=? WHERE id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, instance.getState() != null ? instance.getState() : 10);
            ps.setString(2, instance.getParentNodeName());
            ps.setTimestamp(3, toTimestamp(instance.getExpireTime()));
            ps.setString(4, toJson(instance.getVariables()));
            ps.setTimestamp(5, toTimestamp(LocalDateTime.now()));
            ps.setString(6, instance.getUpdateUser());
            ps.setLong(7, instance.getInstanceId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("更新流程实例失败", e);
        }
    }

    // ═══ 流程任务 ═══

    @Override
    public ProcessTask findTaskById(Long taskId) {
        String sql = "SELECT * FROM wf_process_task WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ProcessTask task = mapTask(rs);
                    task.setActorIds(findTaskActors(conn, taskId));
                    return task;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询流程任务失败", e);
        }
        return null;
    }

    @Override
    public void saveTask(ProcessTask task) {
        if (task.getTaskId() == null) {
            task.setTaskId(nextId());
        }
        String sql = "INSERT INTO wf_process_task " +
                "(id, process_instance_id, task_name, display_name, task_type, perform_type, task_state, " +
                "operator, finish_time, expire_time, form_key, task_parent_id, variable, " +
                "create_time, create_user, update_time, update_user) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setTaskParams(ps, task);
            ps.executeUpdate();
            // 同步参与人
            saveTaskActors(conn, task.getTaskId(), task.getActorIds(), task.getCreateUser());
        } catch (SQLException e) {
            throw new RuntimeException("保存流程任务失败", e);
        }
    }

    @Override
    public void updateTask(ProcessTask task) {
        String sql = "UPDATE wf_process_task SET task_state=?, operator=?, finish_time=?, expire_time=?, " +
                "variable=?, update_time=?, update_user=? WHERE id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, task.getTaskState() != null ? task.getTaskState() : 10);
            ps.setString(2, task.getActorId());
            ps.setTimestamp(3, toTimestamp(task.getFinishTime()));
            ps.setTimestamp(4, toTimestamp(task.getExpireTime()));
            ps.setString(5, toJson(task.getVariables()));
            ps.setTimestamp(6, toTimestamp(LocalDateTime.now()));
            ps.setString(7, task.getUpdateUser());
            ps.setLong(8, task.getTaskId());
            ps.executeUpdate();
            // 同步参与人
            saveTaskActors(conn, task.getTaskId(), task.getActorIds(), task.getUpdateUser());
        } catch (SQLException e) {
            throw new RuntimeException("更新流程任务失败", e);
        }
    }

    @Override
    public List<ProcessTask> findDoingTasks(Long instanceId, String[] taskNames) {
        StringBuilder sql = new StringBuilder("SELECT * FROM wf_process_task WHERE process_instance_id = ? AND task_state = 10");
        if (taskNames != null && taskNames.length > 0) {
            sql.append(" AND task_name IN (");
            for (int i = 0; i < taskNames.length; i++) sql.append(i == 0 ? "?" : ",?");
            sql.append(")");
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setLong(1, instanceId);
            if (taskNames != null) {
                for (int i = 0; i < taskNames.length; i++) ps.setString(i + 2, taskNames[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return mapTasks(conn, rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询进行中任务失败", e);
        }
    }

    @Override
    public List<ProcessTask> findDoneTasks(Long instanceId, String[] taskNames) {
        StringBuilder sql = new StringBuilder("SELECT * FROM wf_process_task WHERE process_instance_id = ? AND task_state = 20");
        if (taskNames != null && taskNames.length > 0) {
            sql.append(" AND task_name IN (");
            for (int i = 0; i < taskNames.length; i++) sql.append(i == 0 ? "?" : ",?");
            sql.append(")");
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setLong(1, instanceId);
            if (taskNames != null) {
                for (int i = 0; i < taskNames.length; i++) ps.setString(i + 2, taskNames[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return mapTasks(conn, rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询已完成任务失败", e);
        }
    }

    @Override
    public List<ProcessTask> findHistoryTasks(Long instanceId) {
        String sql = "SELECT * FROM wf_process_task WHERE process_instance_id = ? ORDER BY create_time ASC";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, instanceId);
            try (ResultSet rs = ps.executeQuery()) {
                return mapTasks(conn, rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询历史任务失败", e);
        }
    }

    // ═══ 抄送 ═══

    @Override
    public void createCcInstance(Long instanceId, String creator, String... actorIds) {
        String sql = "INSERT INTO wf_process_cc_instance " +
                "(id, process_instance_id, actor_id, state, create_time, create_user, update_time, update_user) " +
                "VALUES (?,?,?,0,?,?,?,?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            Timestamp now = new Timestamp(System.currentTimeMillis());
            for (String actorId : actorIds) {
                ps.setLong(1, nextId());
                ps.setLong(2, instanceId);
                ps.setString(3, actorId);
                ps.setTimestamp(4, now);
                ps.setString(5, creator);
                ps.setTimestamp(6, now);
                ps.setString(7, creator);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("创建抄送失败", e);
        }
    }

    @Override
    public void updateCcStatus(Long instanceId, String actorId) {
        String sql = "UPDATE wf_process_cc_instance SET state=1, update_time=? WHERE process_instance_id=? AND actor_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            ps.setLong(2, instanceId);
            ps.setString(3, actorId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("更新抄送状态失败", e);
        }
    }

    // ═══ 参与者 ═══

    @Override
    public List<String> findTaskActors(Long taskId) {
        try (Connection conn = dataSource.getConnection()) {
            return findTaskActors(conn, taskId);
        } catch (SQLException e) {
            throw new RuntimeException("查询任务参与者失败", e);
        }
    }

    @Override
    public void addTaskActor(Long taskId, List<String> actors) {
        try (Connection conn = dataSource.getConnection()) {
            saveTaskActors(conn, taskId, actors, null);
        } catch (SQLException e) {
            throw new RuntimeException("添加任务参与者失败", e);
        }
    }

    @Override
    public void removeTaskActor(Long taskId, List<String> actors) {
        if (actors == null || actors.isEmpty()) return;
        StringBuilder sql = new StringBuilder("DELETE FROM wf_process_task_actor WHERE process_task_id = ? AND actor_id IN (");
        for (int i = 0; i < actors.size(); i++) sql.append(i == 0 ? "?" : ",?");
        sql.append(")");
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setLong(1, taskId);
            for (int i = 0; i < actors.size(); i++) ps.setString(i + 2, actors.get(i));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("移除任务参与者失败", e);
        }
    }

    // ═══ 内部映射方法 ═══

    private ProcessInstance mapInstance(ResultSet rs) throws SQLException {
        ProcessInstance inst = new ProcessInstance();
        inst.setInstanceId(rs.getLong("id"));
        inst.setParentId(getLong(rs, "parent_id"));
        inst.setDefineId(rs.getLong("process_define_id"));
        inst.setState(rs.getInt("state"));
        inst.setParentNodeName(rs.getString("parent_node_name"));
        inst.setBusinessNo(rs.getString("business_no"));
        inst.setOperator(rs.getString("operator"));
        inst.setExpireTime(toLocalDateTime(rs.getTimestamp("expire_time")));
        inst.setVariables(fromJson(rs.getString("variable")));
        inst.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
        inst.setCreateUser(rs.getString("create_user"));
        inst.setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));
        inst.setUpdateUser(rs.getString("update_user"));
        return inst;
    }

    private void setInstanceParams(PreparedStatement ps, ProcessInstance inst) throws SQLException {
        ps.setLong(1, inst.getInstanceId());
        setLong(ps, 2, inst.getParentId());
        ps.setLong(3, inst.getDefineId());
        ps.setInt(4, inst.getState() != null ? inst.getState() : 10);
        ps.setString(5, inst.getParentNodeName());
        ps.setString(6, inst.getBusinessNo());
        ps.setString(7, inst.getOperator());
        ps.setTimestamp(8, toTimestamp(inst.getExpireTime()));
        ps.setString(9, toJson(inst.getVariables()));
        ps.setTimestamp(10, toTimestamp(inst.getCreateTime() != null ? inst.getCreateTime() : LocalDateTime.now()));
        ps.setString(11, inst.getCreateUser());
        ps.setTimestamp(12, toTimestamp(inst.getUpdateTime() != null ? inst.getUpdateTime() : LocalDateTime.now()));
        ps.setString(13, inst.getUpdateUser());
    }

    private ProcessTask mapTask(ResultSet rs) throws SQLException {
        ProcessTask task = new ProcessTask();
        task.setTaskId(rs.getLong("id"));
        task.setProcessInstanceId(rs.getLong("process_instance_id"));
        task.setTaskName(rs.getString("task_name"));
        task.setDisplayName(rs.getString("display_name"));
        int taskType = rs.getInt("task_type");
        if (!rs.wasNull()) {
            for (ProcessTaskTypeEnum e : ProcessTaskTypeEnum.values()) {
                if (e.getCode() == taskType) { task.setTaskType(e); break; }
            }
        }
        int performType = rs.getInt("perform_type");
        if (!rs.wasNull()) {
            for (ProcessTaskPerformTypeEnum e : ProcessTaskPerformTypeEnum.values()) {
                if (e.getCode() == performType) { task.setPerformType(e); break; }
            }
        }
        task.setTaskState(rs.getInt("task_state"));
        task.setActorId(rs.getString("operator"));
        task.setFinishTime(toLocalDateTime(rs.getTimestamp("finish_time")));
        task.setExpireTime(toLocalDateTime(rs.getTimestamp("expire_time")));
        task.setFormKey(rs.getString("form_key"));
        task.setParentTaskId(getLong(rs, "task_parent_id"));
        task.setVariables(fromJson(rs.getString("variable")));
        task.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
        task.setCreateUser(rs.getString("create_user"));
        task.setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));
        task.setUpdateUser(rs.getString("update_user"));
        return task;
    }

    private void setTaskParams(PreparedStatement ps, ProcessTask task) throws SQLException {
        ps.setLong(1, task.getTaskId());
        ps.setLong(2, task.getProcessInstanceId());
        ps.setString(3, task.getTaskName());
        ps.setString(4, task.getDisplayName());
        if (task.getTaskType() != null) ps.setInt(5, task.getTaskType().getCode()); else ps.setNull(5, Types.INTEGER);
        if (task.getPerformType() != null) ps.setInt(6, task.getPerformType().getCode()); else ps.setNull(6, Types.INTEGER);
        ps.setInt(7, task.getTaskState() != null ? task.getTaskState() : 10);
        ps.setString(8, task.getActorId());
        ps.setTimestamp(9, toTimestamp(task.getFinishTime()));
        ps.setTimestamp(10, toTimestamp(task.getExpireTime()));
        ps.setString(11, task.getFormKey());
        setLong(ps, 12, task.getParentTaskId());
        ps.setString(13, toJson(task.getVariables()));
        ps.setTimestamp(14, toTimestamp(task.getCreateTime() != null ? task.getCreateTime() : LocalDateTime.now()));
        ps.setString(15, task.getCreateUser());
        ps.setTimestamp(16, toTimestamp(task.getUpdateTime() != null ? task.getUpdateTime() : LocalDateTime.now()));
        ps.setString(17, task.getUpdateUser());
    }

    private List<ProcessTask> findTasksByInstanceId(Connection conn, Long instanceId) throws SQLException {
        String sql = "SELECT * FROM wf_process_task WHERE process_instance_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, instanceId);
            try (ResultSet rs = ps.executeQuery()) {
                return mapTasks(conn, rs);
            }
        }
    }

    private List<ProcessTask> mapTasks(Connection conn, ResultSet rs) throws SQLException {
        List<ProcessTask> tasks = new ArrayList<>();
        while (rs.next()) {
            ProcessTask task = mapTask(rs);
            task.setActorIds(findTaskActors(conn, task.getTaskId()));
            tasks.add(task);
        }
        return tasks;
    }

    private List<String> findTaskActors(Connection conn, Long taskId) throws SQLException {
        String sql = "SELECT actor_id FROM wf_process_task_actor WHERE process_task_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> actors = new ArrayList<>();
                while (rs.next()) actors.add(rs.getString("actor_id"));
                return actors;
            }
        }
    }

    private void saveTaskActors(Connection conn, Long taskId, List<String> actors, String createUser) throws SQLException {
        if (actors == null || actors.isEmpty()) return;
        try (PreparedStatement dps = conn.prepareStatement("DELETE FROM wf_process_task_actor WHERE process_task_id = ?")) {
            dps.setLong(1, taskId);
            dps.executeUpdate();
        }
        String insertSql = "INSERT INTO wf_process_task_actor (id, process_task_id, actor_id, create_time, create_user) VALUES (?,?,?,?,?)";
        try (PreparedStatement ips = conn.prepareStatement(insertSql)) {
            Timestamp now = new Timestamp(System.currentTimeMillis());
            for (String actorId : actors) {
                ips.setLong(1, nextId());
                ips.setLong(2, taskId);
                ips.setString(3, actorId);
                ips.setTimestamp(4, now);
                ips.setString(5, createUser);
                ips.executeUpdate();
            }
        }
    }

    // ═══ JSON 工具 ═══

    private String toJson(FlowData data) {
        if (data == null || data.isEmpty()) return null;
        IJsonProvider json = ServiceContext.find(IJsonProvider.class);
        if (json != null) return json.toJson(data);
        return data.toString();
    }

    private FlowData fromJson(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) return FlowData.create();
        IJsonProvider jsonProvider = ServiceContext.find(IJsonProvider.class);
        if (jsonProvider != null) {
            @SuppressWarnings("unchecked")
            FlowData data = jsonProvider.fromJson(jsonStr, FlowData.class);
            return data != null ? data : FlowData.create();
        }
        return FlowData.create();
    }

    // ═══ 时间转换 ═══

    private static Timestamp toTimestamp(LocalDateTime ldt) {
        if (ldt == null) return null;
        return Timestamp.valueOf(ldt);
    }

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        if (ts == null) return null;
        return ts.toLocalDateTime();
    }

    private static Long getLong(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }

    private static void setLong(PreparedStatement ps, int idx, Long val) throws SQLException {
        if (val == null) ps.setNull(idx, Types.BIGINT);
        else ps.setLong(idx, val);
    }

    // ═══════════════════════════════════════
    // 前端分页查询
    // ═══════════════════════════════════════

    @Override
    public PageResult<TaskRow> pageTodoTasks(PageQuery query) {
        return pageTasks(query, false, TASK_TODO_WHITELIST);
    }

    @Override
    public PageResult<TaskRow> pageDoneTasks(PageQuery query) {
        return pageTasks(query, true, TASK_DONE_WHITELIST);
    }

    @Override
    public PageResult<InstanceRow> pageInstances(PageQuery query) {
        return pageInstances(query, false, INSTANCE_WHITELIST);
    }

    @Override
    public PageResult<InstanceRow> pageCcInstances(PageQuery query) {
        return pageInstances(query, true, CC_INSTANCE_WHITELIST);
    }

    @Override
    public PageResult<DefineRow> pageDefines(PageQuery query) {
        StringBuilder baseSql = new StringBuilder("FROM wf_process_define t WHERE 1=1");
        List<Object> params = new ArrayList<>();
        buildWhere(baseSql, params, query, DEFINE_WHITELIST);

        String countSql = "SELECT COUNT(*) " + baseSql;
        int total = queryCount(countSql, params);

        buildOrder(baseSql, query, "t.", DEFINE_WHITELIST);
        baseSql.append(" LIMIT ? OFFSET ?");
        params.add(query.getPageSize());
        params.add((query.getPageNum() - 1) * query.getPageSize());

        String dataSql = "SELECT t.* " + baseSql;
        List<DefineRow> rows = queryList(dataSql, params, rs -> {
            DefineRow r = new DefineRow();
            r.setId(rs.getLong("id"));
            r.setName(rs.getString("name"));
            r.setDisplayName(rs.getString("display_name"));
            r.setType(rs.getString("type"));
            r.setState(rs.getInt("state"));
            r.setVersion(rs.getInt("version"));
            r.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
            r.setCreateUser(rs.getString("create_user"));
            r.setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));
            r.setUpdateUser(rs.getString("update_user"));
            return r;
        });
        return PageResult.of(query.getPageNum(), query.getPageSize(), total, rows);
    }

    @Override
    public int countTodoTasks(Long userId) {
        String sql = "SELECT COUNT(*) FROM wf_process_task t " +
                "LEFT JOIN wf_process_task_actor pta ON t.id = pta.process_task_id " +
                "WHERE t.task_state = 10 AND pta.actor_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, String.valueOf(userId));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    private PageResult<TaskRow> pageTasks(PageQuery query, boolean done, java.util.Set<String> whitelist) {
        StringBuilder baseSql = new StringBuilder(
                "FROM wf_process_task t " +
                "LEFT JOIN wf_process_instance pi ON t.process_instance_id = pi.id " +
                "LEFT JOIN wf_process_define pd ON pi.process_define_id = pd.id " +
                "LEFT JOIN wf_process_task_actor pta ON t.id = pta.process_task_id " +
                "WHERE 1=1");
        List<Object> params = new ArrayList<>();

        // 待办/已办过滤
        if (done) {
            baseSql.append(" AND t.task_state <> 10");
        } else {
            baseSql.append(" AND t.task_state = 10");
        }

        buildWhere(baseSql, params, query, whitelist);

        String countSql = "SELECT COUNT(DISTINCT t.id) " + baseSql;
        int total = queryCount(countSql, params);

        buildOrder(baseSql, query, "t.", whitelist);
        baseSql.append(" LIMIT ? OFFSET ?");
        params.add(query.getPageSize());
        params.add((query.getPageNum() - 1) * query.getPageSize());

        String dataSql = "SELECT DISTINCT t.*, pd.name AS process_define_name, pd.display_name AS process_define_display_name, " +
                "pi.variable AS instance_variable, pi.create_time AS instance_create_time " + baseSql;
        List<TaskRow> rows = queryList(dataSql, params, this::mapTaskRow);
        return PageResult.of(query.getPageNum(), query.getPageSize(), total, rows);
    }

    private PageResult<InstanceRow> pageInstances(PageQuery query, boolean cc, java.util.Set<String> whitelist) {
        StringBuilder baseSql = new StringBuilder(
                "FROM wf_process_instance t " +
                "LEFT JOIN wf_process_define pd ON t.process_define_id = pd.id ");
        if (cc) baseSql.append("LEFT JOIN wf_process_cc_instance cc ON t.id = cc.process_instance_id ");
        baseSql.append("WHERE 1=1");

        List<Object> params = new ArrayList<>();
        buildWhere(baseSql, params, query, whitelist);

        String countSql = "SELECT COUNT(*) " + baseSql;
        int total = queryCount(countSql, params);

        buildOrder(baseSql, query, "t.", whitelist);
        baseSql.append(" LIMIT ? OFFSET ?");
        params.add(query.getPageSize());
        params.add((query.getPageNum() - 1) * query.getPageSize());

        String dataSql = "SELECT pd.name AS pd_name, pd.display_name AS pd_display_name, pd.version AS pd_version, t.* " + baseSql;
        List<InstanceRow> rows = queryList(dataSql, params, rs -> {
            InstanceRow r = new InstanceRow();
            r.setId(rs.getLong("id"));
            r.setParentId(getLong(rs, "parent_id"));
            r.setProcessDefineId(rs.getLong("process_define_id"));
            r.setState(rs.getInt("state"));
            r.setParentNodeName(rs.getString("parent_node_name"));
            r.setBusinessNo(rs.getString("business_no"));
            r.setOperator(rs.getString("operator"));
            r.setExpireTime(toLocalDateTime(rs.getTimestamp("expire_time")));
            r.setVariable(rs.getString("variable"));
            r.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
            r.setCreateUser(rs.getString("create_user"));
            r.setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));
            r.setUpdateUser(rs.getString("update_user"));
            r.setProcessDefineName(rs.getString("pd_name"));
            r.setProcessDefineDisplayName(rs.getString("pd_display_name"));
            r.setProcessDefineVersion(rs.getInt("pd_version"));
            return r;
        });
        return PageResult.of(query.getPageNum(), query.getPageSize(), total, rows);
    }

    private TaskRow mapTaskRow(ResultSet rs) throws SQLException {
        TaskRow r = new TaskRow();
        r.setId(rs.getLong("id"));
        r.setProcessInstanceId(rs.getLong("process_instance_id"));
        r.setTaskName(rs.getString("task_name"));
        r.setDisplayName(rs.getString("display_name"));
        r.setTaskType(rs.getInt("task_type"));
        r.setPerformType(rs.getInt("perform_type"));
        r.setTaskState(rs.getInt("task_state"));
        r.setOperator(rs.getString("operator"));
        r.setFinishTime(toLocalDateTime(rs.getTimestamp("finish_time")));
        r.setExpireTime(toLocalDateTime(rs.getTimestamp("expire_time")));
        r.setFormKey(rs.getString("form_key"));
        r.setTaskParentId(getLong(rs, "task_parent_id"));
        r.setVariable(rs.getString("variable"));
        r.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
        r.setCreateUser(rs.getString("create_user"));
        r.setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));
        r.setUpdateUser(rs.getString("update_user"));
        r.setProcessDefineName(rs.getString("process_define_name"));
        r.setProcessDefineDisplayName(rs.getString("process_define_display_name"));
        r.setInstanceVariable(rs.getString("instance_variable"));
        r.setInstanceCreateTime(toLocalDateTime(rs.getTimestamp("instance_create_time")));
        return r;
    }

    // ═══ 通用 WHERE 构建（白名单 + 参数化） ═══

    private void buildWhere(StringBuilder sql, List<Object> params, PageQuery query, java.util.Set<String> whitelist) {
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

    private void buildOrder(StringBuilder sql, PageQuery query, String defaultAlias, java.util.Set<String> whitelist) {
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

    // ═══ 列白名单（与 mldong-boot2 别名一致） ═══

    private static final java.util.Set<String> TASK_TODO_WHITELIST = new java.util.HashSet<>(java.util.Arrays.asList(
            "t.id", "t.task_name", "t.display_name", "t.task_type", "t.perform_type", "t.task_state",
            "t.operator", "t.form_key", "t.create_time", "t.finish_time", "t.expire_time",
            "t.process_instance_id", "t.task_parent_id", "t.variable",
            "pi.id", "pi.business_no", "pi.operator", "pi.create_time", "pi.state",
            "pd.name", "pd.display_name", "pd.type",
            "pta.actor_id", "pta.process_task_id"
    ));

    private static final java.util.Set<String> TASK_DONE_WHITELIST = TASK_TODO_WHITELIST;

    private static final java.util.Set<String> INSTANCE_WHITELIST = new java.util.HashSet<>(java.util.Arrays.asList(
            "t.id", "t.parent_id", "t.process_define_id", "t.state", "t.business_no",
            "t.operator", "t.create_time", "t.expire_time", "t.variable",
            "pd.name", "pd.display_name", "pd.type", "pd.version"
    ));

    private static final java.util.Set<String> CC_INSTANCE_WHITELIST = new java.util.HashSet<>(java.util.Arrays.asList(
            "t.id", "t.process_define_id", "t.state", "t.business_no", "t.operator",
            "t.create_time", "t.variable",
            "pd.name", "pd.display_name", "pd.type", "pd.version",
            "cc.actor_id", "cc.state"
    ));

    private static final java.util.Set<String> DEFINE_WHITELIST = new java.util.HashSet<>(java.util.Arrays.asList(
            "t.id", "t.name", "t.display_name", "t.type", "t.state", "t.version",
            "t.create_time", "t.update_time"
    ));

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
