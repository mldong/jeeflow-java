package com.mldong.jeeflow.repository;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessDesign;
import com.mldong.jeeflow.domain.ProcessDesignHis;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessSurrogate;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessInstanceStateEnum;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
import com.mldong.jeeflow.json.IJsonProvider;
import com.mldong.jeeflow.json.TypeReference;
import com.mldong.jeeflow.spi.IIdGenerator;
import com.mldong.jeeflow.spi.IProcessExtRepository;
import com.mldong.jeeflow.spi.IUserProvider;
import com.mldong.jeeflow.spi.PageQuery;
import com.mldong.jeeflow.spi.PageResult;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/**
 * 委托代理自动生效（SQL 仓一路，issues/116 / 规范 06 §4.5 运行期语义 / 08-compliance 用例 26+27）
 *
 * <p>与 jeeflow-core 的 {@code SurrogateAutoApplyTest}（内存仓一路）跑同一份用例矩阵：
 * 同一条委托数据，两仓必须给出同一个结论（用例 27）。</p>
 *
 * <p>断言直接查 {@code wf_process_task_actor} 的真实行——「代理人真进了参与者表」才算生效；
 * 只断返回码、或只看内存对象上的参与者列表都不作数。</p>
 */
public class SurrogateAutoApplyJdbcTest {

    private static final String FLOW = "surrogate-jdbc";
    private static final String PRINCIPAL = "zhangsan";
    private static final String AGENT = "lisi";
    private static final AtomicLong DEFINE_SEQ = new AtomicLong();

    private JdbcDataSource ds;
    private JdbcProcessRepository repo;
    private JdbcProcessExtRepository extRepo;
    private JeeflowEngineImpl engine;

    @Before
    public void setUp() throws Exception {
        ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:jeeflow_surrogate_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        String ddl = new String(Files.readAllBytes(
                Paths.get("src/test/resources/schema-h2.sql")), StandardCharsets.UTF_8);
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            for (String sql : ddl.split(";")) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) stmt.execute(trimmed);
            }
            // 该库 DB_CLOSE_DELAY=-1 会跨测试类存活，逐表清干净再跑
            for (String t : new String[]{"wf_process_task_actor", "wf_process_task",
                    "wf_process_instance", "wf_process_define", "wf_process_surrogate"}) {
                stmt.execute("DELETE FROM " + t);
            }
        }
        repo = new JdbcProcessRepository(ds);
        extRepo = new JdbcProcessExtRepository(ds);
        wire(true, true);
    }

    /**
     * 重装配引擎上下文。
     *
     * @param withExtRepo 是否注册扩展仓储（false = 「未配置 IProcessExtRepository」部署形态）
     * @param autoApply   引擎内置委托自动生效开关
     */
    private void wire(boolean withExtRepo, boolean autoApply) {
        ObjectMapper mapper = new ObjectMapper();
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
                try { return (T) mapper.readValue(json, mapper.constructType(typeRef.getType())); }
                catch (Exception e) { throw new RuntimeException(e); }
            }
            @Override public boolean isJson(String str) {
                return str != null && (str.trim().startsWith("{") || str.trim().startsWith("["));
            }
        };
        Configuration config = new Configuration();          // 重置 ServiceContext（全局静态）
        AtomicLong seq = new AtomicLong(1000);
        ServiceContext.put("idGen", (IIdGenerator) seq::getAndIncrement);
        ServiceContext.put("repository", repo);
        ServiceContext.put("json", jsonProvider);
        ServiceContext.put("expr", new JdbcRepositoryTest.TestExprEvaluator());
        ServiceContext.put("user", new IUserProvider() {
            @Override public IUserProvider.UserInfo getUser(String userId) {
                IUserProvider.UserInfo u = IUserProvider.UserInfo.of(userId);
                u.setDeptId("D01");
                return u;
            }
        });
        // 引擎内置委托自动生效查的就是这个 SPI（未注册即静默跳过）
        if (withExtRepo) {
            ServiceContext.put("ext", (IProcessExtRepository) extRepo);
        }
        engine = new JeeflowEngineImpl();
        engine.configure(config.surrogateAutoApply(autoApply));
    }

    // ═══ 装配辅助 ═══

    /** start → apply(assignee=applicant 即发起人) → task1(assignee=leader) → end */
    private long registerFlow() {
        String json = ("{'name':'" + FLOW + "','displayName':'委托验证流程','type':'test','nodes':["
                + "{'id':'start','type':'snaker:start','x':100,'y':200,'properties':{},'text':{'value':'开始'}},"
                + "{'id':'apply','type':'snaker:task','x':250,'y':200,'properties':{'form':'f1','assignee':'applicant','taskType':0,'performType':0},'text':{'value':'发起申请'}},"
                + "{'id':'task1','type':'snaker:task','x':400,'y':200,'properties':{'form':'f1','assignee':'leader','taskType':0,'performType':0},'text':{'value':'上级审批'}},"
                + "{'id':'end','type':'snaker:end','x':550,'y':200,'properties':{},'text':{'value':'结束'}}],"
                + "'edges':["
                + "{'id':'e1','sourceNodeId':'start','targetNodeId':'apply','properties':{}},"
                + "{'id':'e2','sourceNodeId':'apply','targetNodeId':'task1','properties':{}},"
                + "{'id':'e3','sourceNodeId':'task1','targetNodeId':'end','properties':{}}]}")
                .replace('\'', '"');
        long id = 700000L + DEFINE_SEQ.incrementAndGet();
        String sql = "INSERT INTO wf_process_define (id, name, display_name, type, state, content, version) "
                + "VALUES (?,?,?,?,?,?,?)";
        try (Connection conn = ds.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setString(2, FLOW);
            ps.setString(3, "委托验证流程");
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

    private ProcessSurrogate ledger(String operator, String agent, String processName,
                                    LocalDateTime start, LocalDateTime end, Integer enabled) {
        ProcessSurrogate s = new ProcessSurrogate();
        s.setOperator(operator);
        s.setSurrogate(agent);
        s.setProcessName(processName);
        s.setStartTime(start);
        s.setEndTime(end);
        s.setEnabled(enabled);
        extRepo.saveSurrogate(s);
        return s;
    }

    /** 直接查参与者表——最硬的持久值证据 */
    private List<String> actorRows(long taskId) {
        String sql = "SELECT actor_id FROM wf_process_task_actor WHERE process_task_id = ? ORDER BY id";
        List<String> rows = new ArrayList<>();
        try (Connection conn = ds.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(rs.getString(1));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return rows;
    }

    private ProcessTask firstDoingTask(Long instanceId) {
        List<ProcessTask> doing = repo.findDoingTasks(instanceId, null);
        assertFalse("建单应产生待办任务", doing.isEmpty());
        return doing.get(0);
    }

    private ProcessInstance startAs(String operator) {
        return engine.startProcessInstanceById(registerFlow(), operator, FlowData.create());
    }

    private void agreeOn(ProcessTask task) {
        engine.executeProcessTask(task.getTaskId(), task.getActorIds().get(0),
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));
    }

    // ═══ 用例 26 ①：窗口内配「张三→李四」→ wf_process_task_actor 里李四真有一行，张三那行仍在 ═══

    @Test
    public void inWindowSurrogateAppendsAgentIntoActorTable() {
        ledger(PRINCIPAL, AGENT, FLOW, LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1), 1);
        ProcessInstance inst = startAs(PRINCIPAL);
        assertNotNull(inst);
        ProcessTask apply = firstDoingTask(inst.getInstanceId());

        List<String> rows = actorRows(apply.getTaskId());
        assertTrue("wf_process_task_actor 必须有代理人一行（实际=" + rows + "）", rows.contains(AGENT));
        assertTrue("授权人那行必须保留（实际=" + rows + "）", rows.contains(PRINCIPAL));
        assertEquals("只应多出代理人一行: " + rows, 2, rows.size());

        // 重新按主键读任务（参与者由仓储从表里回读）后代理人可办
        assertTrue("代理人应可办理该任务", repo.findTaskById(apply.getTaskId()).isAllowed(AGENT));
    }

    /** 办理推进产生的新单同样应用委托（挂点覆盖 execute 路径，不只是发起） */
    @Test
    public void surrogateAppliedOnTaskCreatedByAdvance() {
        ledger("leader", "lisi2", FLOW, null, null, 1);
        ProcessInstance inst = startAs(PRINCIPAL);
        agreeOn(firstDoingTask(inst.getInstanceId()));

        ProcessTask task1 = firstDoingTask(inst.getInstanceId());
        assertEquals("task1", task1.getTaskName());
        List<String> rows = actorRows(task1.getTaskId());
        assertTrue("推进出的新单也应并入代理人（实际=" + rows + "）", rows.contains("lisi2"));
        assertTrue(rows.contains("leader"));
    }

    // ═══ 用例 26 ②：窗外 / enabled=0 / 自己委托给自己 → 代理人无行 ═══

    @Test
    public void outOfWindowNotApplied() {
        String op = "op-out";
        ledger(op, AGENT, FLOW, LocalDateTime.now().minusDays(10),
                LocalDateTime.now().minusDays(5), 1);
        assertEquals("窗外委托不得追加代理人", Collections.singletonList(op),
                actorRows(firstDoingTask(startAs(op).getInstanceId()).getTaskId()));
    }

    @Test
    public void disabledSurrogateNotApplied() {
        String op = "op-off";
        ledger(op, AGENT, FLOW, LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1), 0);
        assertEquals("enabled=0 不得追加代理人", Collections.singletonList(op),
                actorRows(firstDoingTask(startAs(op).getInstanceId()).getTaskId()));
    }

    @Test
    public void selfDelegationNotApplied() {
        String op = "op-self";
        ledger(op, op, FLOW, null, null, 1);
        assertEquals("自委托不得生效（也不得重复插自己一行）", Collections.singletonList(op),
                actorRows(firstDoingTask(startAs(op).getInstanceId()).getTaskId()));
    }

    // ═══ 用例 26：未配置 IProcessExtRepository → 建单不被打断 ═══

    @Test
    public void missingExtRepositorySilentlySkipsAndDoesNotBreakStart() {
        wire(true, true);                 // 先跑一遍确认基线
        wire(false, true);                // 未配置扩展仓储的部署形态
        ProcessInstance inst = startAs(PRINCIPAL);
        assertNotNull("缺扩展仓储不得打断建单", inst);
        assertEquals(ProcessInstanceStateEnum.DOING.getCode(), inst.getState());
        ProcessTask apply = firstDoingTask(inst.getInstanceId());
        assertEquals(Collections.singletonList(PRINCIPAL), actorRows(apply.getTaskId()));
        agreeOn(apply);                   // 推进同样不受影响
        assertEquals(Collections.singletonList("leader"),
                actorRows(firstDoingTask(inst.getInstanceId()).getTaskId()));
    }

    /** 扩展仓储查询自身报错也不得打断建单（缺仓储/坏仓储同属"增强能力不可用"） */
    @Test
    public void throwingExtRepositoryDoesNotBreakStart() {
        ServiceContext.put("ext", new ThrowingExtRepository());
        ProcessInstance inst = startAs(PRINCIPAL);
        assertNotNull("仓储查询抛错不得打断建单", inst);
        assertEquals(Collections.singletonList(PRINCIPAL),
                actorRows(firstDoingTask(inst.getInstanceId()).getTaskId()));
    }

    // ═══ 用例 26：显式关闭 → 回到「仅台账」 ═══

    @Test
    public void explicitDisabledFallsBackToLedgerOnly() {
        ProcessSurrogate row = ledger(PRINCIPAL, AGENT, FLOW, null, null, 1);
        wire(true, false);                // = jeeflow.surrogate.auto-apply=false

        assertEquals("关闭后参与者只应有授权人", Collections.singletonList(PRINCIPAL),
                actorRows(firstDoingTask(startAs(PRINCIPAL).getInstanceId()).getTaskId()));
        assertNotNull("台账仍应查得到（关闭的是运行期应用，不是数据）",
                extRepo.findSurrogateById(row.getId()));
    }

    // ═══ 用例 27（SQL 仓一路）：四条判据 —— 与内存仓同答案 ═══

    @Test
    public void fourQueryCriteriaOnJdbcRepository() {
        LocalDateTime now = LocalDateTime.now();

        // a. 空 processName 全流程兜底 + 精确优先
        ledger("opA", "agentAll", null, null, null, 1);
        ledger("opA", "agentLeave", FLOW, null, null, 1);
        assertEquals("未配的流程应落到全流程兜底", "agentAll",
                extRepo.getSurrogate("opA", "other-flow", now).getSurrogate());
        assertEquals("精确命中优先于兜底", "agentLeave",
                extRepo.getSurrogate("opA", FLOW, now).getSurrogate());
        assertEquals("空 processName 查询也只应命中兜底行", "agentAll",
                extRepo.getSurrogate("opA", null, now).getSurrogate());
        ledger("opA2", "agentEmpty", "", null, null, 1);
        assertEquals("process_name='' 也算全流程委托", "agentEmpty",
                extRepo.getSurrogate("opA2", FLOW, now).getSurrogate());

        // b. 时间窗，任一侧 NULL = 该侧不限
        ledger("opB1", "sB1", FLOW, now.minusDays(1), now.plusDays(1), 1);
        ledger("opB2", "sB2", FLOW, now.plusDays(1), null, 1);
        ledger("opB3", "sB3", FLOW, null, now.minusDays(1), 1);
        ledger("opB4", "sB4", FLOW, now.minusDays(1), null, 1);
        ledger("opB5", "sB5", FLOW, null, now.plusDays(1), 1);
        assertEquals("在窗", "sB1", extRepo.getSurrogate("opB1", FLOW, now).getSurrogate());
        assertNull("未到窗不生效", extRepo.getSurrogate("opB2", FLOW, now));
        assertNull("已过窗不生效", extRepo.getSurrogate("opB3", FLOW, now));
        assertEquals("start=NULL 视为下界不限", "sB4", extRepo.getSurrogate("opB4", FLOW, now).getSurrogate());
        assertEquals("end=NULL 视为上界不限", "sB5", extRepo.getSurrogate("opB5", FLOW, now).getSurrogate());

        // c. 自委托过滤
        ledger("opC", "opC", FLOW, null, null, 1);
        assertNull("自己委托给自己不生效", extRepo.getSurrogate("opC", FLOW, now));

        // d. enabled 只认 1（脏值/非 1 均不得当启用）
        ledger("opD0", "sD0", FLOW, null, null, 0);
        ledger("opD2", "sD2", FLOW, null, null, 2);
        ledger("opD9", "sD9", FLOW, null, null, -1);
        assertNull("enabled=0 不生效", extRepo.getSurrogate("opD0", FLOW, now));
        assertNull("enabled=2 不生效", extRepo.getSurrogate("opD2", FLOW, now));
        assertNull("enabled=-1 不生效", extRepo.getSurrogate("opD9", FLOW, now));

        // 多条命中取最新（与内存仓同答案）
        ledger("opE", "sE-old", FLOW, null, null, 1);
        ledger("opE", "sE-new", FLOW, null, null, 1);
        assertEquals("双仓一致：多条命中取 id 最大", "sE-new",
                extRepo.getSurrogate("opE", FLOW, now).getSurrogate());
    }

    // ═══ 脏值经门面写入也不得当启用（06-facade §4.5 条款 5「enabled 只认 1」） ═══

    @Test
    public void dirtyEnabledViaFacadeIsNotTreatedAsEnabled() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("operator", "opDirty");
        body.put("surrogate", "sDirty");
        body.put("processName", FLOW);
        body.put("enabled", "abc");                          // 不可解析为整数的脏值
        com.mldong.jeeflow.facade.JeeflowFacade facade =
                new com.mldong.jeeflow.facade.JeeflowFacade(engine, repo, extRepo);
        @SuppressWarnings("unchecked")
        Map<String, Object> r = (Map<String, Object>) facade.flow("processSurrogate/save", body);
        assertEquals("save 应成功: " + r, Integer.valueOf(0), r.get("code"));
        Object id = ((Map<?, ?>) r.get("data")).get("id");
        assertNotNull(id);
        assertEquals("脏值不得折叠成启用", Integer.valueOf(0),
                extRepo.findSurrogateById(Long.valueOf(String.valueOf(id))).getEnabled());
        assertNull("脏值委托不得生效", extRepo.getSurrogate("opDirty", FLOW, LocalDateTime.now()));
    }

    /** getSurrogate 恒抛错的扩展仓储（其余方法用不到） */
    private static class ThrowingExtRepository implements IProcessExtRepository {
        @Override public ProcessSurrogate getSurrogate(String operator, String processName, LocalDateTime time) {
            throw new RuntimeException("simulated ext repo failure");
        }
        @Override public ProcessDesign findDesignById(Long designId) { return null; }
        @Override public void saveDesign(ProcessDesign design) { }
        @Override public void updateDesign(ProcessDesign design) { }
        @Override public void removeDesign(Long designId) { }
        @Override public PageResult<ProcessDesign> pageDesigns(PageQuery query) { return null; }
        @Override public void saveDesignHis(ProcessDesignHis his) { }
        @Override public List<ProcessDesignHis> listDesignHis(Long designId) { return new ArrayList<>(); }
        @Override public ProcessSurrogate findSurrogateById(Long surrogateId) { return null; }
        @Override public void saveSurrogate(ProcessSurrogate surrogate) { }
        @Override public void updateSurrogate(ProcessSurrogate surrogate) { }
        @Override public void removeSurrogate(Long surrogateId) { }
        @Override public PageResult<ProcessSurrogate> pageSurrogates(PageQuery query) { return null; }
    }
}
