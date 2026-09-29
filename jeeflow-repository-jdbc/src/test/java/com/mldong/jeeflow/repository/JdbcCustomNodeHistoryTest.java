package com.mldong.jeeflow.repository;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessInstanceStateEnum;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
import com.mldong.jeeflow.enums.ProcessTaskStateEnum;
import com.mldong.jeeflow.handler.IHandler;
import com.mldong.jeeflow.json.IJsonProvider;
import com.mldong.jeeflow.json.TypeReference;
import com.mldong.jeeflow.spi.IExpressionEvaluator;
import com.mldong.jeeflow.spi.IUserProvider;
import com.mldong.jeeflow.spi.IUserProvider.UserInfo;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 记录类（custom）节点历史行的<b>真落库</b>回归 —— issues/142 · spec 02 §6.2 第 1 条。
 *
 * <p>为什么要在 jdbc 模块再钉一遍（core 那边 {@code JeeflowFullTest#test08CustomNode} ＋
 * {@code CustomNodeHistoryTest} 已经打在内存仓储上）：内存仓的 {@code ProcessTask} 是
 * <b>同一枚引用</b>，"读回来的行"与"聚合里的行"本质是同一个对象，只能证"引擎调了写侧"，
 * 证不了一条真的 {@code INSERT} 语句。本类用 H2 真表，判据是<b>裸 SQL 从
 * {@code wf_process_task} 里 SELECT 那一行</b>——正是缺陷原形（{@code CustomModel.exec}
 * 丢弃 {@code createHistoryTask} 返回值 → {@code persistTasks} 只遍历
 * {@code getProcessTaskList()} → {@code updateInstance} 级联又只 {@code UPDATE}
 * {@code taskId != null} 的行，而 {@code ProcessTask.create} 从不赋 taskId
 * ⇒ 那条 {@code task_state=20} 的行永远没有 INSERT）的照妖镜。</p>
 *
 * <p>两条腿各钉一格：<b>办理腿</b>（{@code persistTasks}，任务办完后流到 custom）与
 * <b>发起腿</b>（{@code startProcessInstanceById} 自带一套落库序列，start 直接进 custom）——
 * 只补一条腿的话另一半路径上的历史行照样进不了库。</p>
 */
public class JdbcCustomNodeHistoryTest {

    private static final String HANDLER =
            "com.mldong.jeeflow.repository.JdbcCustomNodeHistoryTest$RecordingHandler";

    private JdbcDataSource ds;
    private JdbcProcessRepository repo;
    private JeeflowEngine engine;
    private ObjectMapper mapper;

    @Before
    public void setUp() throws Exception {
        ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:jeeflow_custom_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        String ddl = new String(Files.readAllBytes(
                Paths.get("src/test/resources/schema-h2.sql")), StandardCharsets.UTF_8);
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            for (String sql : ddl.split(";")) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) stmt.execute(trimmed);
            }
        }

        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        IJsonProvider jsonProvider = new IJsonProvider() {
            @Override public String toJson(Object obj) {
                try { return mapper.writeValueAsString(obj); } catch (Exception e) { throw new RuntimeException(e); }
            }
            @Override public <T> T fromJson(String json, Class<T> type) {
                try { return mapper.readValue(json, type); } catch (Exception e) { throw new RuntimeException(e); }
            }
            @Override @SuppressWarnings("unchecked")
            public <T> T fromJson(String json, TypeReference<T> typeRef) {
                try { return (T) mapper.readValue(json, mapper.constructType(typeRef.getType())); } catch (Exception e) { throw new RuntimeException(e); }
            }
            @Override public boolean isJson(String str) {
                return str != null && (str.trim().startsWith("{") || str.trim().startsWith("["));
            }
        };

        repo = new JdbcProcessRepository(ds);

        Configuration config = new Configuration();
        ServiceContext.put("repository", repo);
        ServiceContext.put("json", jsonProvider);
        ServiceContext.put("expr", new IExpressionEvaluator() {
            @Override public Object eval(String expression, Map<String, Object> context) { return Boolean.FALSE; }
        });
        ServiceContext.put("user", new IUserProvider() {
            @Override public UserInfo getUser(String userId) {
                UserInfo u = UserInfo.of(userId);
                u.setDeptId("D01");
                u.setDeptName("XX部门");
                return u;
            }
        });

        engine = new JeeflowEngineImpl();
        engine.configure(config);
    }

    @After
    public void tearDown() throws Exception {
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
        }
    }

    /**
     * 办理腿：start → apply(task) → custom(c1) → end。
     *
     * <p>办完 apply 后令牌走进 custom：那条 {@code task_state=20} 的行必须能在
     * {@code wf_process_task} 里 SELECT 到，且四条形状都对——
     * ① 分到主键 id；② {@code task_parent_id}＝apply 那一行（建单不变量，issues/121 P1）；
     * ③ {@code variable} 里带行级首节点标记（c1 不是 start 直接后继 ⇒ false）；
     * ④ 参与者行＝当前操作人（留痕主体）。同时 {@code task_state=10} 的行必须为 0——
     * 这条历史行<b>没有</b>变成待办。</p>
     */
    @Test
    public void customHistoryRowIsReallyInsertedIntoTaskTable() throws Exception {
        long defineId = saveDefine("custom-apply", applyThenCustomFlow());

        ProcessInstance inst = engine.startProcessInstanceById(defineId, "applicant", FlowData.create());
        ProcessTask apply = repo.findDoingTasks(inst.getInstanceId(), null).get(0);
        assertEquals("apply", apply.getTaskName());
        repo.addTaskActor(apply.getTaskId(), Arrays.asList("applicant"));
        apply.getActorIds().add("applicant");

        // 办理 apply ⇒ 令牌进 custom ⇒ 历史行应当落库
        engine.executeProcessTask(apply.getTaskId(), "applicant",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));

        Map<String, Object> row = queryOne(
                "SELECT id, task_state, operator, finish_time, expire_time, task_parent_id, variable "
                        + "FROM wf_process_task WHERE process_instance_id = ? AND task_name = 'c1'",
                inst.getInstanceId());
        assertNotNull("custom 的历史行必须真的 INSERT 进 wf_process_task（§6.2 第 1 条：只在聚合里 append 不算做到）", row);
        assertEquals("记录类建行即已完成态", ProcessTaskStateEnum.FINISHED.getCode(), row.get("TASK_STATE"));
        assertEquals("历史行的 task_parent_id＝当前任务（建单不变量不许在改造时丢掉）",
                apply.getTaskId(), row.get("TASK_PARENT_ID"));
        assertTrue("行级首节点标记随建单一并进 variable：" + row.get("VARIABLE"),
                String.valueOf(row.get("VARIABLE")).contains(FlowConst.IS_FIRST_TASK_NODE));
        assertTrue("c1 不是 start 的直接后继 ⇒ 标记 false：" + row.get("VARIABLE"),
                String.valueOf(row.get("VARIABLE")).contains("false"));
        List<String> actors = queryStrings(
                "SELECT actor_id FROM wf_process_task_actor WHERE process_task_id = ? ORDER BY id",
                row.get("ID"));
        assertEquals("历史行参与者＝当前操作人（留痕主体，不是待办收单人）",
                Arrays.asList("applicant"), actors);
        // spec 02 §6.2 第 1bis 条：留痕行的 operator（＝actorId 列）与 finish_time 必须写——
        // doneList 走 `state<>10 AND operator=?`、审批记录也按这两列取数，
        // 只写 task_state=20 的留痕在用户面上等于没落过。
        assertEquals("留痕行的 operator 列必须＝触发这次流转的当前操作人",
                "applicant", String.valueOf(row.get("OPERATOR")));
        assertNotNull("留痕行必须写 finish_time（已完成却没有完成时间＝半条留痕）", row.get("FINISH_TIME"));

        assertEquals("记录类节点不产生待办：task_state=10 的行数必须为 0",
                Integer.valueOf(0), count(
                        "SELECT COUNT(*) FROM wf_process_task WHERE process_instance_id = ? AND task_state = 10",
                        inst.getInstanceId()));
        assertEquals("令牌必须继续流转到 end", ProcessInstanceStateEnum.FINISHED.getCode(),
                repo.findInstanceById(inst.getInstanceId()).getState());
    }

    /**
     * 发起腿：start → custom(c1) → end（一条待办都不产生的短流）。
     *
     * <p>{@code startProcessInstanceById} 自己有一套"任务落库 → updateInstance → 码 2 flush"，
     * 与 {@code persistTasks} 是两条腿；这一格钉的就是<b>只补办理腿</b>会漏掉的那一半。
     * 建单不变量在这一支是 {@code task_parent_id = 0}（发起 execution 没有当前任务）。</p>
     */
    @Test
    public void customHistoryRowIsInsertedOnStartLegToo() throws Exception {
        long defineId = saveDefine("custom-start", customOnlyFlow());

        ProcessInstance inst = engine.startProcessInstanceById(defineId, "zhangsan", FlowData.create());

        Map<String, Object> row = queryOne(
                "SELECT id, task_state, task_parent_id, variable, expire_time "
                        + "FROM wf_process_task WHERE process_instance_id = ? AND task_name = 'c1'",
                inst.getInstanceId());
        assertNotNull("发起腿也要有 INSERT 腿（只补 persistTasks 一半就是 §6.2 说的『半条形状』）", row);
        assertEquals(ProcessTaskStateEnum.FINISHED.getCode(), row.get("TASK_STATE"));
        assertEquals("发起腿无当前任务 ⇒ 建单不变量落 0", Long.valueOf(0L), row.get("TASK_PARENT_ID"));
        assertTrue("c1 是 start 直接后继 ⇒ 首节点标记 true：" + row.get("VARIABLE"),
                String.valueOf(row.get("VARIABLE")).contains("true"));
        assertEquals(Integer.valueOf(0), count(
                "SELECT COUNT(*) FROM wf_process_task WHERE process_instance_id = ? AND task_state = 10",
                inst.getInstanceId()));
        assertEquals(ProcessInstanceStateEnum.FINISHED.getCode(),
                repo.findInstanceById(inst.getInstanceId()).getState());
    }

    /**
     * 到期时间写点（issues/126 普查里被点名的那一支，本仓实读结论）：
     * {@code ProcessInstance.createHistoryTask} <b>不调</b> {@code applyExpireTime}，
     * 而 {@code CustomModel} 只有 {@code clazz}/{@code methodName}/{@code args}/{@code val}
     * 四个属性（{@code CustomParser}），{@code NodeModel} 基类也没有 {@code expireTime} 字段
     * ⇒ 记录类节点<b>没有任何到期表达式可取</b>，{@code expire_time} 这一列在两支都是 NULL。
     *
     * <p>该不该写：按 issues/126 已拍的 A 案（基准＝boot2，"节点没配就保持 NULL，不造默认值"，
     * owner 另否掉了"给个默认到期时间"），记录类行生来已完成、不可能逾期 ⇒
     * <b>NULL 是正确形状，不该补写</b>。本格把这个结论钉成断言，免得将来有人"顺手"给它
     * 补一个 {@code now()}（那正是 126 当初的红样）。</p>
     */
    @Test
    public void historyRowKeepsExpireTimeNull() throws Exception {
        long defineId = saveDefine("custom-expire", customOnlyFlow());

        ProcessInstance inst = engine.startProcessInstanceById(defineId, "wangwu", FlowData.create());

        Map<String, Object> row = queryOne(
                "SELECT expire_time FROM wf_process_task WHERE process_instance_id = ? AND task_name = 'c1'",
                inst.getInstanceId());
        assertNotNull(row);
        assertNull("记录类历史行的 expire_time 必须是 NULL：这一支没有节点级到期表达式可取，"
                + "且 126 已拍\"未配⇒留空、不造默认值\"", row.get("EXPIRE_TIME"));
        // 同一条判据的另一半：**该写的两列写了、不该写的一列不写**——
        // 补 expire_time 是造默认值（126 当初的红样），漏 operator/finish_time 是半条留痕，
        // 两个方向都要钉住，否则后人"顺手对齐"只会往一边倒。
    }

    // ═══ 处理器夹具（public static 嵌套类，Class.forName 按二元名可达）═══

    /** 只留痕的 IHandler 夹具 */
    public static class RecordingHandler implements IHandler {
        @Override
        public void handle(Execution execution) {
            execution.getArgs().put(FlowConst.CUSTOM_RETURN_VAL, "customExecuted");
        }
    }

    // ═══ 夹具流程 JSON ═══

    private static String customNodeJson() {
        return "{\"id\":\"c1\",\"type\":\"snaker:custom\",\"x\":400,\"y\":200,\"properties\":{"
                + "\"clazz\":\"" + HANDLER + "\",\"methodName\":\"handle\","
                + "\"args\":\"\",\"val\":\"customResult\"},\"text\":{\"value\":\"通知外部系统\"}}";
    }

    /** start → apply(task) → custom(c1) → end：办理腿 */
    private static String applyThenCustomFlow() {
        return ("{'name':'custom-apply','displayName':'记录类节点流程','type':'approval','nodes':["
                + "{'id':'start','type':'snaker:start','x':100,'y':200,'properties':{},'text':{'value':'开始'}},"
                + "{'id':'apply','type':'snaker:task','x':250,'y':200,'properties':{'form':'f1','assignee':'applicant',"
                + "'taskType':0,'performType':0},'text':{'value':'发起申请'}},"
                + "@CUSTOM@,"
                + "{'id':'end','type':'snaker:end','x':550,'y':200,'properties':{},'text':{'value':'结束'}}],"
                + "'edges':["
                + "{'id':'e1','sourceNodeId':'start','targetNodeId':'apply','properties':{}},"
                + "{'id':'e2','sourceNodeId':'apply','targetNodeId':'c1','properties':{}},"
                + "{'id':'e3','sourceNodeId':'c1','targetNodeId':'end','properties':{}}]}")
                .replace("@CUSTOM@", customNodeJson())
                .replace('\'', '"');
    }

    /** start → custom(c1) → end：发起腿短流 */
    private static String customOnlyFlow() {
        return ("{'name':'custom-start','displayName':'记录类短流','type':'approval','nodes':["
                + "{'id':'start','type':'snaker:start','x':100,'y':200,'properties':{},'text':{'value':'开始'}},"
                + "@CUSTOM@,"
                + "{'id':'end','type':'snaker:end','x':500,'y':200,'properties':{},'text':{'value':'结束'}}],"
                + "'edges':["
                + "{'id':'e1','sourceNodeId':'start','targetNodeId':'c1','properties':{}},"
                + "{'id':'e2','sourceNodeId':'c1','targetNodeId':'end','properties':{}}]}")
                .replace("@CUSTOM@", customNodeJson())
                .replace('\'', '"');
    }

    private long saveDefine(String name, String json) {
        long id = System.currentTimeMillis() + (long) (Math.random() * 1000);
        String sql = "INSERT INTO wf_process_define (id, name, display_name, type, state, content, version) "
                + "VALUES (?,?,?,?,?,?,?)";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setString(2, name);
            ps.setString(3, "记录类节点流程");
            ps.setString(4, "test");
            ps.setInt(5, 1);
            ps.setBytes(6, json.getBytes(StandardCharsets.UTF_8));
            ps.setInt(7, 1);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return id;
    }

    // ═══ 裸 SQL 读回（判据不打在内存对象上）═══

    private Map<String, Object> queryOne(String sql, Object... params) throws Exception {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Map<String, Object> row = new HashMap<>();
                int columns = rs.getMetaData().getColumnCount();
                for (int i = 1; i <= columns; i++) {
                    // H2 对未加引号的标识符按大写存 ⇒ 键统一大写，读法不受驱动/版本的大小写口径影响
                    row.put(rs.getMetaData().getColumnName(i).toUpperCase(java.util.Locale.ROOT),
                            rs.getObject(i));
                }
                return row;
            }
        }
    }

    private List<String> queryStrings(String sql, Object... params) throws Exception {
        List<String> out = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        }
        return out;
    }

    private Integer count(String sql, Object... params) throws Exception {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Integer.valueOf(rs.getInt(1)) : Integer.valueOf(-1);
            }
        }
    }

    private static void bind(PreparedStatement ps, Object... params) throws Exception {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }
}
