package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.ProcessTaskPerformTypeEnum;
import com.mldong.jeeflow.facade.JeeflowFacade;
import com.mldong.jeeflow.spi.IExpressionEvaluator;
import com.mldong.jeeflow.spi.IOrgUserProvider;
import com.mldong.jeeflow.spi.IUserProvider;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * stats 三 action 契约对齐测试（issues/103 验收修复 A–E）
 *
 * <p>覆盖：原矩阵（正向/负向/边界/会签）+ 验收修复自证：
 * A：trend/group 的 data 本体是裸数组（非 {series}/{rows} 包装）
 * B：stateIn 入参生效（缺省 [10,20,30,40,45,50]）
 * C：trend 缺 start/end/granularity → code!=0（对齐内置线 20010012 语义）
 * D：define 维度不过滤 state（count 全实例，avg 仅 state=20）
 * E：todayNew 不过滤 state（服务器当日全部实例）
 */
public class JeeflowStatsTest {

    private JeeflowFacade facade;
    private MemoryProcessRepository repo;

    @Before
    public void setUp() {
        Configuration config = new Configuration();
        repo = new MemoryProcessRepository();
        MemoryProcessExtRepository extRepo = new MemoryProcessExtRepository();
        TestJsonProvider json = new TestJsonProvider();
        ServiceContext.put("repository", repo);
        ServiceContext.put("json", json);
        ServiceContext.put("expr", new TestExpressionEvaluator());
        ServiceContext.put("user", new IUserProvider() {
            @Override public IUserProvider.UserInfo getUser(String userId) {
                IUserProvider.UserInfo u = new IUserProvider.UserInfo();
                u.setUserId(userId);
                u.setRealName("用户" + userId);
                return u;
            }
        });
        ServiceContext.put("org", new IOrgUserProvider() {
            @Override public List<String> findDeptLeaders(String deptId) { return null; }
            @Override public List<String> findDeptMainLeaders(String deptId) { return null; }
            @Override public List<String> findByRole(String roleCode) { return null; }
        });
        JeeflowEngine engine = new JeeflowEngineImpl();
        engine.configure(config);
        facade = new JeeflowFacade(engine, repo, extRepo);
    }

    // ───────── 测试数据 ─────────

    private ProcessInstance inst(long id, long defineId, int state, String op, LocalDateTime create) {
        ProcessInstance i = new ProcessInstance();
        i.setInstanceId(id);
        i.setDefineId(defineId);
        i.setState(state);
        i.setOperator(op);
        i.setCreateTime(create);
        return i;
    }

    private ProcessTask task(long id, long instId, int taskState, String display, String actor,
                             LocalDateTime create, LocalDateTime finish, LocalDateTime expire) {
        ProcessTask t = new ProcessTask();
        t.setTaskId(id);
        t.setProcessInstanceId(instId);
        t.setTaskState(taskState);
        t.setDisplayName(display);
        t.setActorId(actor);
        t.setCreateTime(create);
        t.setFinishTime(finish);
        t.setExpireTime(expire);
        return t;
    }

    /**
     * 固定数据集：历史数据全部落在 2026-08-01/02（明显在真实 today 之前，固定范围查询与
     * "真实今日"查询互不干扰）；I106 为服务器当日（LocalDateTime.now()，E 自证：todayNew 计它）。
     * 用 8 月而非真实当前月，是为了让固定范围（08-01..08-02）的趋势/分组断言不依赖时钟。
     */
    private void seed() {
        ProcessInstance.ProcessDefine d1 = new ProcessInstance.ProcessDefine();
        d1.setName("leave"); d1.setDisplayName("请假流程"); d1.setType("approval"); d1.setState(1);
        d1.setId(1L);
        ProcessInstance.ProcessDefine d2 = new ProcessInstance.ProcessDefine();
        d2.setName("expense"); d2.setDisplayName("报销流程"); d2.setType("finance"); d2.setState(1);
        d2.setId(2L);
        repo.addDefine(d1);
        repo.addDefine(d2);

        LocalDateTime d1a = LocalDateTime.of(2026, 8, 1, 10, 0);
        LocalDateTime d1b = LocalDateTime.of(2026, 8, 1, 12, 0);
        LocalDateTime d2a = LocalDateTime.of(2026, 8, 2, 9, 0);
        LocalDateTime d2b = LocalDateTime.of(2026, 8, 2, 11, 0);
        LocalDateTime d2c = LocalDateTime.of(2026, 8, 2, 15, 0);
        LocalDateTime d2d = LocalDateTime.of(2026, 8, 2, 16, 0);
        repo.saveInstance(inst(100, 1, 10, "u1", d1a));          // 进行中
        repo.saveInstance(inst(101, 1, 20, "u1", d1b));          // 已完成
        repo.saveInstance(inst(102, 2, 20, "u2", d2a));          // 已完成
        repo.saveInstance(inst(103, 2, 30, "u2", d2b));          // 已撤销
        repo.saveInstance(inst(104, 1, 45, "u3", d2c));          // 已驳回
        repo.saveInstance(inst(105, 1, 99, "u3", d2d));          // 废弃（缺省 stateIn 剔除）
        repo.saveInstance(inst(106, 1, 99, "u1", LocalDateTime.now())); // 当日·废弃（E 自证：todayNew 计它）

        repo.saveTask(task(200, 100, 10, "部门经理审批", "u9",
                d1a.plusMinutes(5), null, null));                    // 在办
        repo.saveTask(task(201, 100, 10, "人事确认", "u9",
                d1a.plusMinutes(6), null, null));                    // 在办·会签
        repo.addTaskActor(201L, Arrays.asList("u9", "u10"));
        repo.saveTask(task(202, 101, 20, "部门经理审批", "u5",
                d1b.plusMinutes(10), d1b.plusHours(6), d1b.plusDays(1))); // 办结·及时
        repo.saveTask(task(203, 102, 20, "财务审批", "u6",
                d2a.plusMinutes(10), d2a.plusHours(9), d2a.plusHours(3))); // 办结·超时
        repo.saveTask(task(204, 105, 20, "部门经理审批", "u5",
                d2d.plusMinutes(10), d2d.plusMinutes(60), d2d.plusMinutes(90))); // 办结·及时（废弃实例的任务）
        ProcessTask t6 = task(205, 106, 20, "财务审批", "u6",
                LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(1), null); // 会签·当日
        t6.setPerformType(ProcessTaskPerformTypeEnum.COUNTERSIGN);
        repo.saveTask(t6);
        repo.addTaskActor(205L, Arrays.asList("u6", "u7"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String action, Object... kv) {
        Map<String, Object> a = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) a.put((String) kv[i], kv[i + 1]);
        return (Map<String, Object>) facade.flow(action, a);
    }

    private List<Map<String, Object>> list(Object data) {
        assertTrue("data 本体应为裸数组，实际：" + data, data instanceof List);
        return (List<Map<String, Object>>) data;
    }

    // ───────── overview ─────────

    @Test
    public void overview_full() {
        seed();
        Map<String, Object> r = call("processInstance/stats/overview");
        assertEquals(0, r.get("code"));
        Map<String, Object> d = (Map<String, Object>) r.get("data");
        assertEquals(5L, d.get("total"));            // 缺省 stateIn 剔除 99（I6）
        assertEquals(1L, d.get("inProgress"));
        assertEquals(2L, d.get("completed"));
        assertEquals(1L, d.get("rejected"));
        assertEquals(1L, d.get("withdrawn"));
        assertEquals(0L, d.get("suspended"));
        assertEquals(1L, d.get("todayNew"));          // E：只计当日 I6（state=99 也计）
        assertEquals(27000, d.get("avgDurationSeconds")); // (21600+32400)/2
        assertEquals(0.3333, (double) d.get("rejectRate"), 1e-9);
        assertEquals(2L, d.get("pendingTaskCount"));
        assertEquals(0L, d.get("overdueTaskCount"));
        assertEquals(0.25, (double) d.get("countersignRate"), 1e-9); // 1/4 会签完成
        assertEquals(0.6667, (double) d.get("onTimeRate"), 1e-9);    // 2/3（T6 无 expire 不入分母）
    }

    @Test
    public void overview_stateIn_respected() {
        // B 自证：非缺省 stateIn 六个计数随动，todayNew 不受影响
        seed();
        Map<String, Object> r = call("processInstance/stats/overview",
                "stateIn", Arrays.asList(10));
        Map<String, Object> d = (Map<String, Object>) r.get("data");
        assertEquals(1L, d.get("total"));
        assertEquals(1L, d.get("inProgress"));
        assertEquals(0L, d.get("completed"));
        assertEquals(1L, d.get("todayNew"));          // 仍计当日 I6
    }

    @Test
    public void overview_empty_repo() {
        Map<String, Object> r = call("processInstance/stats/overview");
        assertEquals(0, r.get("code"));
        Map<String, Object> d = (Map<String, Object>) r.get("data");
        for (String k : Arrays.asList("total", "inProgress", "completed", "rejected",
                "withdrawn", "suspended", "todayNew", "avgDurationSeconds",
                "pendingTaskCount", "overdueTaskCount")) {
            assertEquals(0, ((Number) d.get(k)).intValue());
        }
        assertEquals(0.0, (double) d.get("rejectRate"), 1e-9);
        assertEquals(0.0, (double) d.get("onTimeRate"), 1e-9);
    }

    // ───────── trend ─────────

    @Test
    public void trend_day_bareArray() {
        // A 自证：data 本体是裸数组（非 {granularity, series}）
        seed();
        Map<String, Object> r = call("processInstance/stats/trend",
                "start", "2026-08-01 00:00:00", "end", "2026-08-02 23:59:59", "granularity", "day");
        assertEquals(0, r.get("code"));
        List<Map<String, Object>> arr = list(r.get("data"));
        assertEquals(2, arr.size());
        assertEquals("2026-08-01", arr.get(0).get("bucket"));
        assertEquals(2L, arr.get(0).get("started"));   // I100,I101（实例侧无 state 过滤）
        assertEquals(1L, arr.get(0).get("finished"));  // T202
        assertEquals("2026-08-02", arr.get(1).get("bucket"));
        assertEquals(4L, arr.get(1).get("started"));   // I102,I103,I104,I105（无 state 过滤，含 99 的 I105）
        assertEquals(2L, arr.get(1).get("finished"));  // T203,T204
    }

    @Test
    public void trend_hour_week_month() {
        seed();
        Map<String, Object> h = call("processInstance/stats/trend",
                "start", "2026-08-01 10:00:00", "end", "2026-08-01 12:00:00", "granularity", "hour");
        List<Map<String, Object>> hb = list(h.get("data"));
        assertEquals(3, hb.size());
        assertEquals("2026-08-01 10:00", hb.get(0).get("bucket"));
        assertEquals("2026-08-01 11:00", hb.get(1).get("bucket"));
        assertEquals("2026-08-01 12:00", hb.get(2).get("bucket"));
        assertEquals(1L, hb.get(0).get("started"));    // I100
        assertEquals(1L, hb.get(2).get("started"));    // I101

        Map<String, Object> w = call("processInstance/stats/trend",
                "start", "2026-08-01 00:00:00", "end", "2026-08-02 23:59:59", "granularity", "week");
        List<Map<String, Object>> wb = list(w.get("data"));
        assertEquals(1, wb.size());
        assertEquals("2026-W31", wb.get(0).get("bucket")); // ISO 周（08-01/08-02 同属 W31）
        assertEquals(6L, wb.get(0).get("started"));        // I100–I105 全部落在 W31（含 99 的 I105；I106 在真实当月不入范围）

        Map<String, Object> m = call("processInstance/stats/trend",
                "start", "2026-07-31 00:00:00", "end", "2026-08-31 23:59:59", "granularity", "month");
        List<Map<String, Object>> mb = list(m.get("data"));
        assertEquals(2, mb.size());
        assertEquals("2026-07", mb.get(0).get("bucket"));
        assertEquals(0L, mb.get(0).get("started"));    // 补 0 桶
        assertEquals("2026-08", mb.get(1).get("bucket"));
        assertEquals(6L, mb.get(1).get("started"));    // I100–I105（含 99 的 I105）
    }

    @Test
    public void trend_missing_params_error() {
        // C 自证：缺 start / 缺 end / 缺 granularity / granularity 非法 → 均 code!=0
        for (Object[] kv : new Object[][]{
                {"end", "2026-09-02 00:00:00", "granularity", "day"},
                {"start", "2026-09-01 00:00:00", "granularity", "day"},
                {"start", "2026-09-01 00:00:00", "end", "2026-09-02 00:00:00"},
                {"start", "2026-09-01 00:00:00", "end", "2026-09-02 00:00:00", "granularity", "abc"},
        }) {
            Map<String, Object> r = call("processInstance/stats/trend", kv);
            assertNotEquals("缺参应报错：" + java.util.Arrays.toString(kv), 0, r.get("code"));
        }
    }

    @Test
    public void trend_empty_repo_allZeroBuckets() {
        Map<String, Object> r = call("processInstance/stats/trend",
                "start", "2026-09-01 00:00:00", "end", "2026-09-01 23:59:59", "granularity", "day");
        assertEquals(0, r.get("code"));
        List<Map<String, Object>> arr = list(r.get("data"));
        assertEquals(1, arr.size());
        assertEquals(0L, arr.get(0).get("started"));
        assertEquals(0L, arr.get(0).get("finished"));
    }

    // ───────── group ─────────

    @Test
    public void group_allDimensions_bareArray() {
        seed();
        // A 自证 + 正向：9 维度 data 本体均为裸数组
        for (String dim : Arrays.asList("state", "define", "category", "approver",
                "applicant", "node", "stuckNode", "stuckApprover", "durationBucket")) {
            Map<String, Object> r = call("processInstance/stats/group",
                    "dimension", dim, "start", "2026-08-01 00:00:00", "end", "2026-08-02 23:59:59");
            assertEquals(0, r.get("code"));
            list(r.get("data"));
        }
    }

    @Test
    public void group_define_noStateFilter() {
        // D 自证：count 全实例（含 99/45/30），avg 仅 state=20
        seed();
        Map<String, Object> r = call("processInstance/stats/group", "dimension", "define");
        List<Map<String, Object>> rows = list(r.get("data"));
        assertEquals(2, rows.size());
        Map<String, Object> leave = rows.get(0);
        assertEquals("leave", leave.get("key"));
        assertEquals("请假流程", leave.get("label"));
        assertEquals(5L, leave.get("count"));          // I100,I101,I104,I105,I106（无 state 过滤，含 45/99）
        assertEquals(21600, ((Number) leave.get("avgDurationSeconds")).intValue()); // avg 仅对 state=20（I101=6h）
        Map<String, Object> expense = rows.get(1);
        assertEquals(2L, expense.get("count"));        // I3,I4
        assertEquals(32400, ((Number) expense.get("avgDurationSeconds")).intValue());
    }

    @Test
    public void group_state_category_applicant() {
        seed();
        List<Map<String, Object>> st = list(call("processInstance/stats/group",
                "dimension", "state").get("data"));
        // 无 state 过滤：99 也出现（I105,I106 两条 99）；5 个不同 state（10/20/30/45/99）
        Map<String, Long> byKey = new HashMap<>();
        for (Map<String, Object> m : st) byKey.put(String.valueOf(m.get("key")), (Long) m.get("count"));
        assertEquals(Long.valueOf(2), byKey.get("20"));   // I101,I102
        assertEquals(Long.valueOf(2), byKey.get("99"));   // I105,I106
        assertEquals(Long.valueOf(1), byKey.get("10"));   // I100
        assertEquals(Long.valueOf(1), byKey.get("30"));   // I103
        assertEquals(Long.valueOf(1), byKey.get("45"));   // I104
        assertEquals(5, st.size());

        List<Map<String, Object>> cat = list(call("processInstance/stats/group",
                "dimension", "category").get("data"));
        assertEquals(5L, cat.get(0).get("count"));     // approval（无 state 过滤，含 99）
        assertEquals(2L, cat.get(1).get("count"));     // finance

        List<Map<String, Object>> ap = list(call("processInstance/stats/group",
                "dimension", "applicant").get("data"));
        assertEquals("u1", ap.get(0).get("key"));
        assertEquals(3L, ap.get(0).get("count"));      // I100,I101,I106（无 state 过滤）
    }

    @Test
    public void group_node_approver_avg() {
        seed();
        Map<String, Object> r = call("processInstance/stats/group", "dimension", "node",
                "start", "2026-08-01 00:00:00", "end", "2026-08-02 23:59:59");
        List<Map<String, Object>> rows = list(r.get("data"));
        // 8 月范围只含 T202/T203/T204（T205 的 finish 是真实当日，不入范围）
        // 部门经理审批：T202=21000s、T204=3000s → avg=(21000+3000)/2=12000
        assertEquals("部门经理审批", rows.get(0).get("key"));
        assertEquals(2L, rows.get(0).get("count"));
        assertEquals(12000, ((Number) rows.get(0).get("avgDurationSeconds")).intValue());
        assertEquals("财务审批", rows.get(1).get("key"));
        assertEquals(31800, ((Number) rows.get(1).get("avgDurationSeconds")).intValue()); // T203=8h50m

        List<Map<String, Object>> ap = list(call("processInstance/stats/group", "dimension", "approver",
                "start", "2026-08-01 00:00:00", "end", "2026-08-02 23:59:59").get("data"));
        assertEquals("u5", ap.get(0).get("key"));      // T202+T204 历史办结
        assertEquals(2L, ap.get(0).get("count"));
    }

    @Test
    public void group_stuckRealtime_ignoresRange() {
        // stuckNode/stuckApprover 实时快照，忽略 start/end
        seed();
        Map<String, Object> r = call("processInstance/stats/group", "dimension", "stuckNode",
                "start", "2020-01-01 00:00:00", "end", "2020-01-02 00:00:00");
        List<Map<String, Object>> rows = list(r.get("data"));
        // T200（部门经理审批）/T201（人事确认）两条在办任务分属不同节点 → 2 行，各 count=1
        assertEquals(2, rows.size());
        Set<String> nodeKeys = new HashSet<>();
        for (Map<String, Object> m : rows) {
            nodeKeys.add((String) m.get("key"));
            assertEquals(1L, m.get("count"));
            assertNull(m.get("avgDurationSeconds"));
        }
        assertTrue(nodeKeys.contains("部门经理审批"));
        assertTrue(nodeKeys.contains("人事确认"));

        // 会签自证：stuckApprover 每 actor 一行不重复计（仅 T201 注册了 actors u9/u10）
        Map<String, Object> r2 = call("processInstance/stats/group", "dimension", "stuckApprover",
                "start", "2020-01-01 00:00:00", "end", "2020-01-02 00:00:00");
        List<Map<String, Object>> actors = list(r2.get("data"));
        assertEquals(2, actors.size());
        for (Map<String, Object> m : actors) {
            assertEquals(1L, m.get("count"));          // u9、u10 各一行
            assertNull(m.get("avgDurationSeconds"));
        }
    }

    @Test
    public void group_durationBucket_fixedOrder() {
        seed();
        Map<String, Object> r = call("processInstance/stats/group", "dimension", "durationBucket");
        List<Map<String, Object>> rows = list(r.get("data"));
        assertEquals(4, rows.size());
        assertEquals("sameDay", rows.get(0).get("key"));
        assertEquals("1to3d", rows.get(1).get("key"));
        assertEquals("3to7d", rows.get(2).get("key"));
        assertEquals("over7d", rows.get(3).get("key"));
        // durationBucket 仅对 state=20 实例：I101=6h、I102=9h（均 <24h）→ sameDay=2，其余 0
        assertEquals(2L, rows.get(0).get("count"));
        assertEquals(0L, rows.get(1).get("count"));
        assertEquals(0L, rows.get(2).get("count"));
        assertEquals(0L, rows.get(3).get("count"));
    }

    @Test
    public void group_invalid_dimension_error() {
        Map<String, Object> r = call("processInstance/stats/group", "dimension", "bogus");
        assertNotEquals(0, r.get("code"));
    }

    @Test
    public void group_limit_topN() {
        seed();
        Map<String, Object> r = call("processInstance/stats/group", "dimension", "state", "limit", 2);
        List<Map<String, Object>> rows = list(r.get("data"));
        assertEquals(2, rows.size());
        assertTrue((Long) rows.get(0).get("count") >= (Long) rows.get(1).get("count"));
    }

    @Test
    public void overview_expire_allNull() {
        // 边界：expire 全 NULL → overdueTaskCount=0、onTimeRate=0（非错误）
        ProcessInstance.ProcessDefine d1 = new ProcessInstance.ProcessDefine();
        d1.setName("leave"); d1.setDisplayName("请假流程"); d1.setType("approval"); d1.setState(1);
        d1.setId(1L);
        repo.addDefine(d1);
        repo.saveInstance(inst(100, 1, 10, "u1", LocalDateTime.now().minusHours(2)));
        repo.saveTask(task(200, 100, 10, "审批", "u9",
                LocalDateTime.now().minusHours(1), null, null));
        Map<String, Object> r = call("processInstance/stats/overview");
        Map<String, Object> d = (Map<String, Object>) r.get("data");
        assertEquals(0L, d.get("overdueTaskCount"));
        assertEquals(0.0, (double) d.get("onTimeRate"), 1e-9);
    }
}