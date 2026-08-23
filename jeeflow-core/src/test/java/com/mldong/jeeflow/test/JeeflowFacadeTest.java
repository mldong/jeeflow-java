package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessDesign;
import com.mldong.jeeflow.domain.ProcessDesignHis;
import com.mldong.jeeflow.facade.JeeflowFacade;
import com.mldong.jeeflow.spi.IExpressionEvaluator;
import com.mldong.jeeflow.spi.IProcessExtRepository;
import com.mldong.jeeflow.spi.IProcessRepository;
import com.mldong.jeeflow.spi.IOrgUserProvider;
import com.mldong.jeeflow.spi.IUserProvider;
import com.mldong.jeeflow.spi.IUserProvider.UserInfo;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * JeeflowFacade 统一门面测试（v1.1.0）——flow(action, map) 全 action 路由
 */
public class JeeflowFacadeTest {

    private JeeflowFacade facade;
    private JeeflowEngine engine;
    private MemoryProcessRepository repo;
    private MemoryProcessExtRepository extRepo;
    private IProcessRepository rawRepo;

    @Before
    public void setUp() {
        Configuration config = new Configuration();
        repo = new MemoryProcessRepository();
        extRepo = new MemoryProcessExtRepository();
        TestJsonProvider json = new TestJsonProvider();

        ServiceContext.put("repository", repo);
        ServiceContext.put("json", json);
        ServiceContext.put("expr", new TestExpressionEvaluator());
        ServiceContext.put("user", new IUserProvider() {
            @Override public UserInfo getUser(String userId) {
                UserInfo u = new UserInfo();
                u.setUserId(userId);
                u.setRealName("用户" + userId);
                return u;
            }
        });

        ServiceContext.put("org", new IOrgUserProvider() {
            @Override public List<String> findDeptLeaders(String deptId) { return null; }
            @Override public List<String> findDeptMainLeaders(String deptId) { return null; }
            @Override public List<String> findByRole(String roleCode) {
                if ("finance".equals(roleCode)) return Arrays.asList("finA", "finB");
                return null;
            }
        });

        engine = new JeeflowEngineImpl();
        engine.configure(config);
        rawRepo = repo;
        facade = new JeeflowFacade(engine, repo, extRepo);
    }

    private ProcessInstance.ProcessDefine registerFlow(String filename) throws Exception {
        byte[] bytes = Files.readAllBytes(
                Paths.get("src/test/resources/flows/" + filename));
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName(filename.replace(".json", ""));
        def.setDisplayName(filename);
        def.setType("approval");
        def.setState(1);
        def.setVersion(1);
        def.setContent(bytes);
        repo.addDefine(def);
        return def;
    }

    private Map<String, Object> args(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i].toString(), kv[i + 1]);
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String action, Map<String, Object> a) {
        return (Map<String, Object>) facade.flow(action, a);
    }

    private void assertOk(Map<String, Object> r) {
        assertEquals("code 应为 0: " + r, Integer.valueOf(0), r.get("code"));
    }

    // ═══ 流程定义 ═══

    @Test
    public void testDefinePageAndDeployVersion() throws Exception {
        // deploy：新定义 version=0
        String content = new String(Files.readAllBytes(
                Paths.get("src/test/resources/flows/01-simple.json")), StandardCharsets.UTF_8);
        Map<String, Object> r = call("processDefine/deploy", args("content", content));
        assertOk(r);
        Long defineId = toLong(((Map<String, Object>) r.get("data")).get("processDefineId"));
        assertNotNull(defineId);
        ProcessInstance.ProcessDefine def = repo.findDefineById(defineId);
        assertEquals(Integer.valueOf(0), def.getVersion());

        // 再次 deploy：version+1
        r = call("processDefine/deploy", args("content", content));
        assertOk(r);
        Long defineId2 = toLong(((Map<String, Object>) r.get("data")).get("processDefineId"));
        assertNotEquals(defineId, defineId2);
        assertEquals(Integer.valueOf(1), repo.findDefineById(defineId2).getVersion());

        // 分页
        r = call("processDefine/page", args("pageNum", 1, "pageSize", 10));
        assertOk(r);
        Map<String, Object> data = (Map<String, Object>) r.get("data");
        assertEquals(Integer.valueOf(2), data.get("recordCount"));

        // 启停
        r = call("processDefine/upAndDown", args("id", defineId, "state", 0));
        assertOk(r);
        assertEquals(Integer.valueOf(0), repo.findDefineById(defineId).getState());

        // 删除
        r = call("processDefine/remove", args("id", defineId2));
        assertOk(r);
        assertNull(repo.findDefineById(defineId2));
    }

    // ═══ 流程实例 + 任务 ═══

    @Test
    public void testInstanceTaskAndWithdraw() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("01-simple.json");
        Long defineId = def.getId();

        // startAndExecute：发起并自动完成 apply → task1(leader)
        Map<String, Object> r = call("processInstance/startAndExecute",
                args("processDefineId", defineId, "operator", "zhangsan", "amount", "1000"));
        assertOk(r);
        Long instanceId = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));
        ProcessInstance inst = repo.findInstanceById(instanceId);
        assertNotNull(inst);

        // todoList：leader 有待办
        r = call("processTask/todoList", args("operator", "leader"));
        assertOk(r);
        Map<String, Object> data = (Map<String, Object>) r.get("data");
        assertEquals(Integer.valueOf(1), data.get("recordCount"));

        // execute（AGREE=1）：leader 完成任务 → 实例完成
        List<com.mldong.jeeflow.domain.ProcessTask> doing = rawRepo.findDoingTasks(inst.getInstanceId(), null);
        r = call("processTask/execute", args("processTaskId", doing.get(0).getTaskId(),
                "operator", "leader", "submitType", 1));
        assertOk(r);
        assertEquals(com.mldong.jeeflow.enums.ProcessInstanceStateEnum.FINISHED.getCode(),
                repo.findInstanceById(inst.getInstanceId()).getState());

        // withdraw：完成前撤回
        ProcessInstance.ProcessDefine def2 = registerFlow("01-simple.json");
        r = call("processInstance/startAndExecute", args("processDefineId", def2.getId(), "operator", "zhangsan"));
        Long instanceId2 = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));
        r = call("processInstance/withdraw", args("id", instanceId2, "operator", "zhangsan"));
        assertOk(r);
        assertEquals(com.mldong.jeeflow.enums.ProcessInstanceStateEnum.WITHDRAW.getCode(),
                repo.findInstanceById(instanceId2).getState());
        // 级联：doing 任务全部废弃（v1.0.1）
        List<com.mldong.jeeflow.domain.ProcessTask> after = rawRepo.findDoingTasks(instanceId2, null);
        assertEquals(0, after.size());
    }

    // ═══ 流程设计 + 委托（扩展仓储） ═══

    @Test
    public void testDesignAndSurrogate() throws Exception {
        String content = new String(Files.readAllBytes(
                Paths.get("src/test/resources/flows/01-simple.json")), StandardCharsets.UTF_8);

        // 保存设计（含内容快照 + remark/icon，82-9 回显断言用）
        Map<String, Object> r = call("processDesign/save", args(
                "name", "leave", "displayName", "请假流程", "content", content, "operator", "zhangsan",
                "icon", "icon-leave", "remark", "请假流程 v1（含附件上传）"));
        assertOk(r);
        Long designId = toLong(((Map<String, Object>) r.get("data")).get("id"));
        assertNotNull(designId);

        // designPage：时间格式化（processDesign/page 应与 processDefine/page 一致 yyyy-MM-dd HH:mm:ss）
        r = call("processDesign/page", args());
        assertOk(r);
        List<?> dRows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertFalse("designPage 应有行: " + r, dRows.isEmpty());
        Object ct = ((Map<String, Object>) dRows.get(0)).get("createTime");
        assertTrue("designPage 时间应格式化为 yyyy-MM-dd HH:mm:ss: " + ct,
                ct != null && ct.toString().matches("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}"));
        // 82-9：designPage 行回显 remark/icon（设计页回显字段）
        Map<String, Object> dRow = null;
        for (Object o : dRows) {
            Map<String, Object> m = (Map<String, Object>) o;
            if ("leave".equals(m.get("name"))) { dRow = m; break; }
        }
        assertNotNull("designPage 应含 leave 设计行: " + dRows, dRow);
        assertEquals("designPage remark 应回显保存值", "请假流程 v1（含附件上传）", dRow.get("remark"));
        assertEquals("designPage icon 应回显保存值", "icon-leave", dRow.get("icon"));

        // detail：含历史
        r = call("processDesign/detail", args("id", designId));
        assertOk(r);
        Map<String, Object> data = (Map<String, Object>) r.get("data");
        assertNotNull(data.get("jsonObject"));
        assertEquals(1, ((List<?>) data.get("his")).size());

        // 发布设计 → 生成 define + isDeployed=1
        r = call("processDesign/deploy", args("id", designId, "operator", "zhangsan"));
        assertOk(r);
        assertNotNull(((Map<String, Object>) r.get("data")).get("processDefineId"));
        assertEquals(Integer.valueOf(1), extRepo.findDesignById(designId).getIsDeployed());

        // 委托：新增 + 生效查询
        r = call("processSurrogate/save", args(
                "operator", "zhangsan", "surrogate", "lisi", "processName", "leave", "operator", "zhangsan"));
        assertOk(r);
        Long surrogateId = toLong(((Map<String, Object>) r.get("data")).get("id"));
        assertNotNull(surrogateId);
        // getSurrogate 生效（无时间窗）
        assertEquals("lisi", extRepo.getSurrogate("zhangsan", "leave", java.time.LocalDateTime.now()).getSurrogate());
        // 委托分页
        r = call("processSurrogate/page", args("operator", "zhangsan"));
        assertOk(r);
        assertEquals(Integer.valueOf(1), ((Map<String, Object>) r.get("data")).get("recordCount"));

        // 删除委托
        r = call("processSurrogate/remove", args("id", surrogateId));
        assertOk(r);
        assertNull(extRepo.findSurrogateById(surrogateId));
    }

    /** issues/77：委托编辑链路 save → detail 回显 → update 改字段 → detail 再回显断言变更 */
    @Test
    public void testSurrogateDetailAndUpdate() {
        // 新增（带时间窗，用前端 RangePicker 实际提交的 yyyy-MM-dd HH:mm:ss 空格格式）
        Map<String, Object> r = call("processSurrogate/save", args(
                "operator", "zhangsan", "surrogate", "lisi", "processName", "leave",
                "startTime", "2026-08-01 00:00:00", "endTime", "2026-08-31 23:59:59", "enabled", 1));
        assertOk(r);
        Long surrogateId = toLong(((Map<String, Object>) r.get("data")).get("id"));
        assertNotNull(surrogateId);

        // detail 回显：行结构齐全 + 时间格式化（前端 form.vue 用 startTime/endTime 组 RangePicker）
        r = call("processSurrogate/detail", args("id", surrogateId));
        assertOk(r);
        Map<String, Object> d = (Map<String, Object>) r.get("data");
        assertEquals("leave", d.get("processName"));
        assertEquals("zhangsan", d.get("operator"));
        assertEquals("lisi", d.get("surrogate"));
        assertEquals(Integer.valueOf(1), d.get("enabled"));
        assertEquals("2026-08-01 00:00:00", d.get("startTime"));
        assertEquals("2026-08-31 23:59:59", d.get("endTime"));

        // update：改代理人/时间窗/启用状态（前端编辑表单不带 operator，授权人应保留）
        r = call("processSurrogate/update", args(
                "id", surrogateId, "surrogate", "wangwu", "processName", "leave",
                "startTime", "2026-09-01 00:00:00", "endTime", "2026-09-30 23:59:59", "enabled", 0));
        assertOk(r);
        assertEquals(surrogateId, toLong(((Map<String, Object>) r.get("data")).get("id")));

        // detail 再回显：变更生效 + 授权人未被清空
        r = call("processSurrogate/detail", args("id", surrogateId));
        assertOk(r);
        d = (Map<String, Object>) r.get("data");
        assertEquals("wangwu", d.get("surrogate"));
        assertEquals("zhangsan", d.get("operator"));
        assertEquals(Integer.valueOf(0), d.get("enabled"));
        assertEquals("2026-09-01 00:00:00", d.get("startTime"));
        assertEquals("2026-09-30 23:59:59", d.get("endTime"));

        // 负向：id 不存在
        r = call("processSurrogate/detail", args("id", 99999L));
        assertEquals(Integer.valueOf(99999999), r.get("code"));
        r = call("processSurrogate/update", args("id", 99999L, "surrogate", "wangwu"));
        assertEquals(Integer.valueOf(99999999), r.get("code"));
    }

    /** issues/82-12：委托生效判断——时间窗 startTime/endTime + enabled 过滤（五语言基准）。
     *  5 条委托各对应一个时间态：在窗 / 未到 / 已过 / 无窗(enabled=0) / 无窗(enabled=1)。
     *  每条查询只命中其中一条（processName 精确区分），断言结果与命中集唯一 → 不依赖仓储返回顺序。 */
    @Test
    public void testSurrogateEffectiveWindowAndEnabled() throws Exception {
        String op = "winop";
        // A 在窗（2026-08-01 ~ 08-31）
        assertOk(call("processSurrogate/save", args("operator", op, "surrogate", "sA", "processName", "winA",
                "startTime", "2026-08-01 00:00:00", "endTime", "2026-08-31 23:59:59", "enabled", 1)));
        // B 未到（2026-09-01 起）
        assertOk(call("processSurrogate/save", args("operator", op, "surrogate", "sB", "processName", "winB",
                "startTime", "2026-09-01 00:00:00", "enabled", 1)));
        // C 已过（07-31 止）
        assertOk(call("processSurrogate/save", args("operator", op, "surrogate", "sC", "processName", "winC",
                "endTime", "2026-07-31 23:59:59", "enabled", 1)));
        // D 无窗但停用（enabled=0）
        assertOk(call("processSurrogate/save", args("operator", op, "surrogate", "sD", "processName", "winD", "enabled", 0)));
        // E 无窗且启用（enabled=1）
        assertOk(call("processSurrogate/save", args("operator", op, "surrogate", "sE", "processName", "winE", "enabled", 1)));

        java.time.LocalDateTime at = java.time.LocalDateTime.of(2026, 8, 15, 12, 0, 0);
        assertEquals("在窗委托应生效", "sA", extRepo.getSurrogate(op, "winA", at).getSurrogate());
        assertEquals("未到窗委托不应生效", null, extRepo.getSurrogate(op, "winB", at));
        assertEquals("已过窗委托不应生效", null, extRepo.getSurrogate(op, "winC", at));
        assertEquals("enabled=0 不应生效", null, extRepo.getSurrogate(op, "winD", at));
        assertEquals("无窗启用委托应生效（NULL=不限）", "sE", extRepo.getSurrogate(op, "winE", at).getSurrogate());
        // 负向：查不存在的流程 → null
        assertEquals("无匹配流程应返回 null", null, extRepo.getSurrogate(op, "winZ", at));
        // 换时间验证窗口边界随时间变化：B 在 9 月生效、A 在 9 月失效
        java.time.LocalDateTime atSep = java.time.LocalDateTime.of(2026, 9, 15, 12, 0, 0);
        assertEquals("9 月：B 进入窗口应生效", "sB", extRepo.getSurrogate(op, "winB", atSep).getSurrogate());
        assertEquals("9 月：A 已出窗口不应生效", null, extRepo.getSurrogate(op, "winA", atSep));
    }

    /** issues/82-7：委托分页 m_IN_processName / m_EQ_enabled 查询形态（对齐 mldong 前端委托页搜索） */
    @Test
    public void testSurrogatePageInAndEqConditions() {
        // 3 条委托：leave(启用) / overtime(启用) / sick(停用)
        Map<String, Object> r = call("processSurrogate/save", args(
                "operator", "zhangsan", "surrogate", "lisi", "processName", "leave", "enabled", 1));
        assertOk(r);
        r = call("processSurrogate/save", args(
                "operator", "zhangsan", "surrogate", "wangwu", "processName", "overtime", "enabled", 1));
        assertOk(r);
        r = call("processSurrogate/save", args(
                "operator", "zhangsan", "surrogate", "zhaoliu", "processName", "sick", "enabled", 0));
        assertOk(r);

        // 无过滤：3 条
        r = call("processSurrogate/page", args("operator", "zhangsan"));
        assertOk(r);
        assertEquals(Integer.valueOf(3), ((Map<String, Object>) r.get("data")).get("recordCount"));

        // m_IN_processName：IN 列表命中 2 条
        r = call("processSurrogate/page", args("operator", "zhangsan",
                "m_IN_processName", Arrays.asList("leave", "overtime")));
        assertOk(r);
        Map<String, Object> inData = (Map<String, Object>) r.get("data");
        assertEquals(Integer.valueOf(2), inData.get("recordCount"));
        List<String> names = new ArrayList<>();
        for (Object row : (List<?>) inData.get("rows")) {
            names.add((String) ((Map<String, Object>) row).get("processName"));
        }
        assertTrue("IN 应命中 leave+overtime: " + names, names.contains("leave") && names.contains("overtime"));

        // m_EQ_enabled：启用过滤命中 2 条
        r = call("processSurrogate/page", args("operator", "zhangsan", "m_EQ_enabled", 1));
        assertOk(r);
        assertEquals(Integer.valueOf(2), ((Map<String, Object>) r.get("data")).get("recordCount"));

        // m_IN + m_EQ 组合：leave/overtime 中仅启用 → 仍 2 条；换成 sick/overtime → 1 条
        r = call("processSurrogate/page", args("operator", "zhangsan",
                "m_IN_processName", Arrays.asList("sick", "overtime"), "m_EQ_enabled", 1));
        assertOk(r);
        Map<String, Object> comboData = (Map<String, Object>) r.get("data");
        assertEquals(Integer.valueOf(1), comboData.get("recordCount"));
        assertEquals("overtime", ((Map<String, Object>) ((List<?>) comboData.get("rows")).get(0)).get("processName"));

        // 负向：IN 全不命中 / EQ 无匹配 → 0 条
        r = call("processSurrogate/page", args("operator", "zhangsan",
                "m_IN_processName", Arrays.asList("none1", "none2")));
        assertOk(r);
        assertEquals(Integer.valueOf(0), ((Map<String, Object>) r.get("data")).get("recordCount"));
        r = call("processSurrogate/page", args("operator", "zhangsan", "m_EQ_enabled", 2));
        assertOk(r);
        assertEquals(Integer.valueOf(0), ((Map<String, Object>) r.get("data")).get("recordCount"));
    }

    // ═══ 视图端点（v1.2.0） ═══

    @Test
    public void testViewEndpoints() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("01-simple.json");
        // getLastByName
        Map<String, Object> r = call("processDefine/getLastByName", args("processDefineName", "01-simple"));
        assertOk(r);
        Map<String, Object> data = (Map<String, Object>) r.get("data");
        assertEquals("01-simple", data.get("name"));

        // startAndExecute 后：approvalRecord / highLight / getAssigneeTextData / latest / jumpAble / detail
        r = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "zhangsan"));
        Long instanceId = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));

        r = call("processInstance/approvalRecord", args("id", instanceId));
        assertOk(r);
        List<?> records = (List<?>) r.get("data");
        assertEquals(2, records.size()); // apply 已完成 + task1 进行中（全部任务记录，对齐 boot2）

        r = call("processInstance/highLight", args("id", instanceId));
        assertOk(r);
        Map<String, Object> hl = (Map<String, Object>) r.get("data");
        assertTrue(((List<?>) hl.get("activeNodeNames")).contains("task1"));
        assertTrue(((List<?>) hl.get("historyNodeNames")).contains("apply"));

        r = call("processInstance/getAssigneeTextData", args("id", instanceId));
        assertOk(r);
        List<?> texts = (List<?>) r.get("data");
        assertEquals(1, texts.size()); // task1 参与者 leader

        r = call("processTask/latest", args("processInstanceId", instanceId));
        assertOk(r);
        assertEquals("task1", ((Map<String, Object>) r.get("data")).get("taskName"));

        List<com.mldong.jeeflow.domain.ProcessTask> doing = rawRepo.findDoingTasks(instanceId, null);
        r = call("processTask/detail", args("id", doing.get(0).getTaskId(), "operator", "leader"));
        assertOk(r);
        data = (Map<String, Object>) r.get("data");
        assertEquals("task1", data.get("taskName"));
        assertEquals(Boolean.TRUE, data.get("executable"));
        assertNotNull(data.get("taskModel"));
        // issues/62：taskModel 补 form/ext（字段权限）
        @SuppressWarnings("unchecked")
        Map<String, Object> tm = (Map<String, Object>) data.get("taskModel");
        assertEquals("leave-form", tm.get("form"));
        Map<String, Object> tmExt = (Map<String, Object>) tm.get("ext");
        assertEquals(Integer.valueOf(1), tmExt.get("PERMISSION_f_leaveType"));
        assertEquals(Integer.valueOf(2), tmExt.get("PERMISSION_days"));

        // 抄送：创建 + 我的抄送 + 已读
        r = call("processInstance/createCCInstance",
                args("processInstanceId", instanceId, "operator", "zhangsan", "actorIds", Arrays.asList("lisi", "wangwu")));
        assertOk(r);
        r = call("processInstance/ccList", args("operator", "lisi"));
        assertOk(r);
        assertEquals(Integer.valueOf(1), ((Map<String, Object>) r.get("data")).get("recordCount"));
        r = call("processInstance/updateCCStatus", args("processInstanceId", instanceId, "operator", "lisi"));
        assertOk(r);

        // jumpAbleTaskNameList（apply 完成后 task1 未办——done 只有 apply，非会签）
        r = call("processTask/jumpAbleTaskNameList", args("processInstanceId", instanceId));
        assertOk(r);
        assertEquals(1, ((List<?>) r.get("data")).size());

        // 加签/转交
        r = call("processTask/addCandidate", args("processTaskId", doing.get(0).getTaskId(), "actorIds", Arrays.asList("zhaoliu")));
        assertOk(r);
        assertTrue(rawRepo.findTaskActors(doing.get(0).getTaskId()).contains("zhaoliu"));
        r = call("processTask/surrogate", args("processTaskId", doing.get(0).getTaskId(), "actorIds", Arrays.asList("sunqi")));
        assertOk(r);
        assertTrue(rawRepo.findTaskActors(doing.get(0).getTaskId()).contains("sunqi"));

        // candidatePage：无模型候选（01-simple 无 candidateUsers 配置）→ 未配置用户搜索钩子报错
        r = call("processTask/candidatePage", args("processTaskId", doing.get(0).getTaskId()));
        assertEquals(Integer.valueOf(99999999), r.get("code"));
    }

    // ═══ 错误路径 ═══

    @Test
    public void testUnknownActionAndMissingExt() {
        Map<String, Object> r = call("foo/bar", args());
        assertEquals(Integer.valueOf(99999999), r.get("code"));

        // 未配置扩展仓储时设计 action 报错
        JeeflowFacade facadeNoExt = new JeeflowFacade(
                new JeeflowEngineImpl(), repo, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> r2 = (Map<String, Object>) facadeNoExt.flow("processDesign/page", args());
        assertEquals(Integer.valueOf(99999999), r2.get("code"));
    }

    /** bizData mock 读取器（同 MetaTableReader 契约，public 供 facade 反射调用） */
    public static class MockMetaTableReader {
        public Map<String, Object> readByProcessInstance(String tableName, Object processInstanceId) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tableName", tableName);
            m.put("processInstanceId", processInstanceId);
            m.put("title", "业务数据");
            return m;
        }
    }

    private static Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).longValue();
        return Long.parseLong(val.toString());
    }

    // ═══ highLight 决策分支表达式过滤（issues/06）═══

    @Test
    public void testHighLightFiltersDecisionBranch() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("03-decision-expr.json");
        // amount=500 → 走「amount <= 1000」分支（task3），task2 分支未执行
        Map<String, Object> r = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "zhangsan", "amount", 500));
        assertOk(r);
        Long instanceId = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));

        // 推进：task1(leader) → decision → task3(director) → end
        List<com.mldong.jeeflow.domain.ProcessTask> doing = rawRepo.findDoingTasks(instanceId, null);
        for (com.mldong.jeeflow.domain.ProcessTask t : doing) {
            if ("task1".equals(t.getTaskName())) {
                rawRepo.addTaskActor(t.getTaskId(), java.util.Arrays.asList("leader"));
                call("processTask/execute", args("processTaskId", t.getTaskId(), "operator", "leader", "submitType", 1));
            }
        }
        doing = rawRepo.findDoingTasks(instanceId, null);
        for (com.mldong.jeeflow.domain.ProcessTask t : doing) {
            if ("task3".equals(t.getTaskName())) {
                rawRepo.addTaskActor(t.getTaskId(), java.util.Arrays.asList("director"));
                call("processTask/execute", args("processTaskId", t.getTaskId(), "operator", "director", "submitType", 1));
            }
        }

        r = call("processInstance/highLight", args("id", instanceId));
        assertOk(r);
        Map<String, Object> hl = (Map<String, Object>) r.get("data");
        @SuppressWarnings("unchecked")
        List<String> historyEdges = (List<String>) hl.get("historyEdgeNames");
        @SuppressWarnings("unchecked")
        List<String> historyNodes = (List<String>) hl.get("historyNodeNames");
        // 走过的分支：e4（amount<=1000 → task3）+ e6（task3→end）
        assertTrue("应包含走过的边 e4/e6: " + historyEdges, historyEdges.contains("e4") && historyEdges.contains("e6"));
        // 未走分支：e3（amount>1000 → task2）与 e5（task2→end）不得出现
        assertFalse("未走分支 e3 不应高亮: " + historyEdges, historyEdges.contains("e3"));
        assertFalse("未走分支 e5 不应高亮: " + historyEdges, historyEdges.contains("e5"));
        assertFalse("未走节点 task2 不应高亮: " + historyNodes, historyNodes.contains("task2"));
        assertTrue("应包含走过节点 task3: " + historyNodes, historyNodes.contains("task3"));
    }


    // ═══ highLight nodeProgress 成员进度回显（issues/41，四语言对齐）═══

    @Test
    public void testHighLightNodeProgress() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("06-countersign-sequential.json");
        Map<String, Object> r = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "user1"));
        assertOk(r);
        Long instanceId = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));

        r = call("processInstance/highLight", args("id", instanceId));
        assertOk(r);
        @SuppressWarnings("unchecked")
        Map<String, Object> np = (Map<String, Object>) ((Map<String, Object>) r.get("data")).get("nodeProgress");
        // 历史节点 apply：发起人 done + name 经 IUserProvider 解析
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> applyMembers = (List<Map<String, Object>>) ((Map<String, Object>) np.get("apply")).get("members");
        assertEquals("user1", applyMembers.get(0).get("id"));
        assertEquals(Boolean.TRUE, applyMembers.get(0).get("done"));
        assertEquals("用户user1", applyMembers.get(0).get("name"));
        // 顺序会签进行中：type=SEQUENTIAL、第一位 active、第二位无标记
        @SuppressWarnings("unchecked")
        Map<String, Object> task1 = (Map<String, Object>) np.get("task1");
        assertEquals("SEQUENTIAL", task1.get("type"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> m1 = (List<Map<String, Object>>) task1.get("members");
        assertEquals("userA", m1.get(0).get("id"));
        assertEquals(Boolean.TRUE, m1.get(0).get("active"));
        assertEquals("用户userA", m1.get(0).get("name"));
        assertEquals("userB", m1.get(1).get("id"));
        assertNull("userB 不应有 done 标记", m1.get(1).get("done"));
        assertNull("userB 不应有 active 标记", m1.get(1).get("active"));
        // 推进会签：userA done → userB active
        List<com.mldong.jeeflow.domain.ProcessTask> doing = rawRepo.findDoingTasks(instanceId, null);
        for (com.mldong.jeeflow.domain.ProcessTask t : doing) {
            if ("task1".equals(t.getTaskName())) {
                rawRepo.addTaskActor(t.getTaskId(), java.util.Arrays.asList("userA"));
                call("processTask/execute", args("processTaskId", t.getTaskId(), "operator", "userA", "submitType", 1));
            }
        }
        r = call("processInstance/highLight", args("id", instanceId));
        assertOk(r);
        @SuppressWarnings("unchecked")
        Map<String, Object> np2 = (Map<String, Object>) ((Map<String, Object>) r.get("data")).get("nodeProgress");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> m2 = (List<Map<String, Object>>) ((Map<String, Object>) np2.get("task1")).get("members");
        assertEquals(Boolean.TRUE, m2.get(0).get("done"));
        assertEquals(Boolean.TRUE, m2.get(1).get("active"));
        // 全部完成 → 全部 done
        doing = rawRepo.findDoingTasks(instanceId, null);
        for (com.mldong.jeeflow.domain.ProcessTask t : doing) {
            if ("task1".equals(t.getTaskName())) {
                rawRepo.addTaskActor(t.getTaskId(), java.util.Arrays.asList("userB"));
                call("processTask/execute", args("processTaskId", t.getTaskId(), "operator", "userB", "submitType", 1));
            }
        }
        r = call("processInstance/highLight", args("id", instanceId));
        assertOk(r);
        @SuppressWarnings("unchecked")
        Map<String, Object> np3 = (Map<String, Object>) ((Map<String, Object>) r.get("data")).get("nodeProgress");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> m3 = (List<Map<String, Object>>) ((Map<String, Object>) np3.get("task1")).get("members");
        assertEquals(Boolean.TRUE, m3.get(0).get("done"));
        assertEquals(Boolean.TRUE, m3.get(1).get("done"));
        assertNull("完成后不应有 active", m3.get(1).get("active"));
    }

    // ═══ 三个 detail 返回 jsonObject（issues/05-1）═══

    @Test
    public void testDetailJsonObject() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("01-simple.json");
        Map<String, Object> r = call("processDefine/detail", args("id", def.getId()));
        assertOk(r);
        assertNotNull(((Map<String, Object>) r.get("data")).get("jsonObject"));

        r = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "zhangsan"));
        assertOk(r);
        Long instanceId = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));
        r = call("processInstance/detail", args("id", instanceId));
        assertOk(r);
        assertNotNull(((Map<String, Object>) r.get("data")).get("jsonObject"));
        // issues/05-4：activeTaskList 仅 DOING 任务 + 任务行 ext.isFirstTaskNode
        Map<String, Object> instData = (Map<String, Object>) r.get("data");
        List<?> activeList = (List<?>) instData.get("activeTaskList");
        assertEquals(1, activeList.size()); // apply 已自动完成，剩余 task1（DOING）
        Map<String, Object> task1Row = (Map<String, Object>) activeList.get(0);
        assertEquals("task1", task1Row.get("taskName"));
        @SuppressWarnings("unchecked")
        Map<String, Object> ext = (Map<String, Object>) task1Row.get("ext");
        assertNotNull(ext);
        // task1 不是首个任务节点（apply 才是），isFirstTaskNode 应为 false
        assertEquals(Boolean.FALSE, ext.get("isFirstTaskNode"));
        List<?> allTasks = (List<?>) instData.get("tasks");
        assertEquals(2, allTasks.size()); // apply + task1 全量

        List<com.mldong.jeeflow.domain.ProcessTask> doing = rawRepo.findDoingTasks(instanceId, null);
        r = call("processTask/detail", args("id", doing.get(0).getTaskId(), "operator", "zhangsan"));
        assertOk(r);
        assertNotNull(((Map<String, Object>) r.get("data")).get("jsonObject"));
    }

    // ═══ taskDetail performType/taskType 出口数字契约（issues/78）═══

    @Test
    public void testTaskDetailPerformTypeNumeric() throws Exception {
        // 普通流程：task1 performType=0、taskType=0（出口应为数字 0，非 null/非枚举 name）
        ProcessInstance.ProcessDefine simple = registerFlow("01-simple.json");
        Map<String, Object> s1 = call("processInstance/startAndExecute",
                args("processDefineId", simple.getId(), "operator", "zhangsan"));
        assertOk(s1);
        Long simpleInstId = toLong(((Map<String, Object>) s1.get("data")).get("processInstanceId"));
        Map<String, Object> d1 = call("processTask/detail",
                args("id", doingTaskId(simpleInstId, "task1"), "operator", "leader"));
        assertOk(d1);
        Map<String, Object> vo1 = (Map<String, Object>) d1.get("data");
        assertEquals("普通任务 performType 应为数字 0", Integer.valueOf(0), vo1.get("performType"));
        assertEquals("普通任务 taskType 应为数字 0", Integer.valueOf(0), vo1.get("taskType"));

        // 会签流程：task1 performType=1（出口应为数字 1，非字符串 "COUNTERSIGN"）
        ProcessInstance.ProcessDefine seq = registerFlow("06-countersign-sequential.json");
        Map<String, Object> s2 = call("processInstance/startAndExecute",
                args("processDefineId", seq.getId(), "operator", "user1"));
        assertOk(s2);
        Long seqInstId = toLong(((Map<String, Object>) s2.get("data")).get("processInstanceId"));
        Map<String, Object> d2 = call("processTask/detail",
                args("id", doingTaskId(seqInstId, "task1"), "operator", "userA"));
        assertOk(d2);
        Map<String, Object> vo2 = (Map<String, Object>) d2.get("data");
        assertEquals("会签任务 performType 应为数字 1", Integer.valueOf(1), vo2.get("performType"));
        assertEquals("会签任务 taskType 应为数字 0", Integer.valueOf(0), vo2.get("taskType"));
    }

    /** 找指定实例下进行中、名为 name 的任务 id */
    private Long doingTaskId(Long instanceId, String name) {
        for (com.mldong.jeeflow.domain.ProcessTask t : rawRepo.findDoingTasks(instanceId, null)) {
            if (name.equals(t.getTaskName())) return t.getTaskId();
        }
        return null;
    }

    // ═══ issues/82-5：task detail 任务级 ext.isFirstTaskNode（前端 detail.vue 双兜底）═══

    @Test
    public void testTaskDetailExtIsFirstTaskNode() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("01-simple.json");
        // startAndExecute 自动完成 apply → 剩 task1（DOING，非首节点）
        Map<String, Object> s1 = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "zhangsan"));
        assertOk(s1);
        Long instanceId = toLong(((Map<String, Object>) s1.get("data")).get("processInstanceId"));
        Long task1Id = doingTaskId(instanceId, "task1");
        Map<String, Object> d = call("processTask/detail", args("id", task1Id, "operator", "leader"));
        assertOk(d);
        Map<String, Object> vo = (Map<String, Object>) d.get("data");
        Map<String, Object> ext = (Map<String, Object>) vo.get("ext");
        assertNotNull("task detail 应含 ext 容器: " + vo.keySet(), ext);
        assertEquals("task1 非首任务节点，ext.isFirstTaskNode 应为 false",
                Boolean.FALSE, ext.get("isFirstTaskNode"));
    }

    @Test
    public void testTaskDetailExtIsFirstTaskNodeTrue() throws Exception {
        // 直接启动（不自动完成 apply）→ apply 为首任务节点且 DOING → ext.isFirstTaskNode=true
        ProcessInstance.ProcessDefine def = registerFlow("01-simple.json");
        com.mldong.jeeflow.domain.ProcessInstance inst =
                engine.startProcessInstanceById(def.getId(), "zhangsan", com.mldong.jeeflow.domain.FlowData.create());
        Long applyId = doingTaskId(inst.getInstanceId(), "apply");
        assertNotNull("apply 应为进行中任务", applyId);
        Map<String, Object> d = call("processTask/detail", args("id", applyId, "operator", "zhangsan"));
        assertOk(d);
        Map<String, Object> ext = (Map<String, Object>) ((Map<String, Object>) d.get("data")).get("ext");
        assertNotNull("task detail 应含 ext 容器", ext);
        assertEquals("apply 为首任务节点且 DOING，ext.isFirstTaskNode 应为 true",
                Boolean.TRUE, ext.get("isFirstTaskNode"));
    }

    // ═══ 按 id 查"记录不存在"负向（对齐 PHP 模板，detail 页最直接报错路径）═══

    @Test
    public void testDetailByIdNotFound() throws Exception {
        // processDefine/detail：流程定义不存在
        Map<String, Object> r = call("processDefine/detail", args("id", 99999L));
        assertEquals(Integer.valueOf(99999999), r.get("code"));
        assertTrue(String.valueOf(r.get("msg")).contains("流程定义不存在"));

        // processInstance/detail：流程实例不存在
        r = call("processInstance/detail", args("id", 99999L));
        assertEquals(Integer.valueOf(99999999), r.get("code"));
        assertTrue(String.valueOf(r.get("msg")).contains("流程实例不存在"));

        // processDesign/detail：流程设计不存在
        r = call("processDesign/detail", args("id", 99999L));
        assertEquals(Integer.valueOf(99999999), r.get("code"));
        assertTrue(String.valueOf(r.get("msg")).contains("流程设计不存在"));

        // processTask/detail：任务不存在
        r = call("processTask/detail", args("id", 99999L));
        assertEquals(Integer.valueOf(99999999), r.get("code"));
        assertTrue(String.valueOf(r.get("msg")).contains("任务不存在"));
    }

    /** issues/82 负向：抄送空 actors 报错（对齐 PHP 基准 testCreateCCInstanceEmptyActors）。
     *  createCCInstance 空/缺失 actorIds → 99999999 + "actorIds 缺失"。 */
    @Test
    public void testCreateCCInstanceEmptyActors() {
        Map<String, Object> r = call("processInstance/createCCInstance",
                args("processInstanceId", 123L, "operator", "user1", "actorIds", Arrays.asList()));
        assertEquals(Integer.valueOf(99999999), r.get("code"));
        assertTrue(String.valueOf(r.get("msg")).contains("actorIds 缺失"));

        // 负向边界：actorIds 键完全缺失（非空 list）同样报错
        r = call("processInstance/createCCInstance",
                args("processInstanceId", 123L, "operator", "user1"));
        assertEquals(Integer.valueOf(99999999), r.get("code"));
        assertTrue(String.valueOf(r.get("msg")).contains("actorIds 缺失"));
    }

    // ═══ execute submitType 3/4/5/6/20 门面行为（issues/79，前端按钮全量暴露路径）═══

    /** 02-multi-task：发起（apply 自动完成）→ 推进到名为 name 的任务节点 */
    private Long startMultiTaskAt(String name) throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("02-multi-task.json");
        Map<String, Object> r = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "zhangsan"));
        assertOk(r);
        Long instanceId = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));
        String[] order = {"task1", "task2", "task3"};
        String[] actor = {"leader", "manager", "boss"};
        int target = 0;
        for (int i = 0; i < order.length; i++) if (order[i].equals(name)) target = i;
        for (int i = 0; i < target; i++) {
            Long tid = doingTaskId(instanceId, order[i]);
            assertNotNull("应推进到 " + order[i], tid);
            rawRepo.addTaskActor(tid, Arrays.asList(actor[i]));
            assertOk(call("processTask/execute",
                    args("processTaskId", tid, "operator", actor[i], "submitType", 1)));
        }
        return instanceId;
    }

    @Test
    public void testExecuteSubmitTypeBehavior() throws Exception {
        // ── submitType=3 ROLLBACK：task2 退回上一步 → task1 新待办（actor=退回操作人），实例保持 DOING(10)
        Long rb = startMultiTaskAt("task2");
        Long t2 = doingTaskId(rb, "task2");
        rawRepo.addTaskActor(t2, Arrays.asList("manager"));
        assertOk(call("processTask/execute", args("processTaskId", t2, "operator", "manager", "submitType", 3)));
        Long rbTask1 = doingTaskId(rb, "task1");
        assertNotNull("ROLLBACK 应在 task1 产生新待办", rbTask1);
        assertTrue("退回任务 actor 应为退回操作人 manager",
                rawRepo.findTaskActors(rbTask1).contains("manager"));
        assertEquals("ROLLBACK 后实例应保持 DOING(10)", Integer.valueOf(10),
                rawRepo.findInstanceById(rb).getState());

        // ── submitType=4 JUMP：task3 跳转 apply（第一个任务节点 = start 直接后继，assignee=发起人）
        //   taskName 取自 jumpAbleTaskNameList（已完成任务节点清单）
        Long jp = startMultiTaskAt("task3");
        Long t3 = doingTaskId(jp, "task3");
        rawRepo.addTaskActor(t3, Arrays.asList("boss"));
        Map<String, Object> jl = call("processTask/jumpAbleTaskNameList", args("processInstanceId", jp));
        assertOk(jl);
        List<?> items = (List<?>) jl.get("data");
        assertTrue("jumpAble 应包含已完成的 task1/apply",
                items.stream().anyMatch(m -> "task1".equals(((Map<?, ?>) m).get("value")))
                        && items.stream().anyMatch(m -> "apply".equals(((Map<?, ?>) m).get("value"))));
        assertOk(call("processTask/execute", args("processTaskId", t3, "operator", "boss",
                "submitType", 4, "taskName", "apply")));
        Long jpApply = doingTaskId(jp, "apply");
        assertNotNull("JUMP 应在 apply（首任务节点）产生新待办", jpApply);
        assertEquals("跳首任务节点 assignee 强制为发起人 zhangsan",
                Arrays.asList("zhangsan"), rawRepo.findTaskActors(jpApply));
        assertEquals("JUMP 后实例应保持 DOING(10)", Integer.valueOf(10),
                rawRepo.findInstanceById(jp).getState());

        // ── 负向：JUMP taskName 不存在 → 99999999 + 「无法找到节点模型」
        Long jn = startMultiTaskAt("task2");
        Long t2n = doingTaskId(jn, "task2");
        rawRepo.addTaskActor(t2n, Arrays.asList("manager"));
        Map<String, Object> jr = call("processTask/execute", args("processTaskId", t2n, "operator", "manager",
                "submitType", 4, "taskName", "no-such-node"));
        assertEquals(Integer.valueOf(99999999), jr.get("code"));
        assertTrue("JUMP 无效节点应报「无法找到节点模型」: " + jr.get("msg"),
                String.valueOf(jr.get("msg")).contains("无法找到节点模型"));

        // ── submitType=5 RE_APPLY：task1 重新提交（前端 detail 抽屉场景，含 f_ 表单 + tf_nextNodeOperator）
        Long ra = startMultiTaskAt("task1");
        Long t1r = doingTaskId(ra, "task1");
        rawRepo.addTaskActor(t1r, Arrays.asList("leader"));
        assertOk(call("processTask/execute", args("processTaskId", t1r, "operator", "leader",
                "submitType", 5, "tf_nextNodeOperator", "manager", "f_leaveType", "annual")));
        assertEquals("RE_APPLY 后应推进到 task2", "task2",
                rawRepo.findDoingTasks(ra, null).get(0).getTaskName());
        assertEquals("tf_nextNodeOperator 应覆盖 task2 处理人",
                Arrays.asList("manager"), rawRepo.findTaskActors(doingTaskId(ra, "task2")));
        assertEquals("f_ 表单字段应落实例变量", "annual",
                rawRepo.findInstanceById(ra).getVariables().get("f_leaveType"));
        assertEquals("RE_APPLY 后实例应保持 DOING(10)", Integer.valueOf(10),
                rawRepo.findInstanceById(ra).getState());

        // ── submitType=6 ROLLBACK_TO_OPERATOR：task3 退回发起人 → apply 重执行、actor=发起人 zhangsan
        Long ro = startMultiTaskAt("task3");
        Long t3o = doingTaskId(ro, "task3");
        rawRepo.addTaskActor(t3o, Arrays.asList("boss"));
        assertOk(call("processTask/execute", args("processTaskId", t3o, "operator", "boss", "submitType", 6)));
        Long roApply = doingTaskId(ro, "apply");
        assertNotNull("ROLLBACK_TO_OPERATOR 应重执行首个任务节点 apply", roApply);
        assertEquals("退回发起人 assignee 强制为发起人 zhangsan",
                Arrays.asList("zhangsan"), rawRepo.findTaskActors(roApply));
        assertEquals("退回发起人后实例应保持 DOING(10)", Integer.valueOf(10),
                rawRepo.findInstanceById(ro).getState());

        // ── 负向：非处理人执行被拒（NOT_ALLOWED_EXECUTE）
        Long na = startMultiTaskAt("task1");
        Long t1n = doingTaskId(na, "task1");
        Map<String, Object> nr = call("processTask/execute",
                args("processTaskId", t1n, "operator", "hacker", "submitType", 1));
        assertEquals("非处理人执行应报 99999999", Integer.valueOf(99999999), nr.get("code"));
        assertTrue("非处理人执行应报参与者错误: " + nr.get("msg"),
                String.valueOf(nr.get("msg")).contains("不能执行"));
    }

    @Test
    public void testExecuteReject() throws Exception {
        // submitType=2 REJECT：task1 拒绝 → executeAndJumpToEnd → 实例 REJECT(45)
        // （issues/79：补门面级 submitType=2 参数路径，与 PHP 对齐；Go/Python/Node 同补）
        Long instId = startMultiTaskAt("task1");
        Long t1 = doingTaskId(instId, "task1");
        rawRepo.addTaskActor(t1, Arrays.asList("leader"));
        assertOk(call("processTask/execute", args("processTaskId", t1, "operator", "leader", "submitType", 2)));
        assertEquals("REJECT 后实例应为 REJECT(45)", Integer.valueOf(45),
                rawRepo.findInstanceById(instId).getState());
        assertTrue("REJECT 后应无 DOING 任务", rawRepo.findDoingTasks(instId, null).isEmpty());
    }

    @Test
    public void testExecuteCountersignDisagree() throws Exception {
        // 06-countersign-sequential：apply 自动完成 → task1 串行会签 userA（userB 未开始）
        ProcessInstance.ProcessDefine def = registerFlow("06-countersign-sequential.json");
        Map<String, Object> r = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "user1"));
        assertOk(r);
        Long instanceId = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));
        Long taskA = doingTaskId(instanceId, "task1");
        assertNotNull("会签节点应有 userA 的 DOING 任务", taskA);
        rawRepo.addTaskActor(taskA, Arrays.asList("userA"));
        // submitType=20：门面自动注入 countersignDisagreeFlag=1 → CountersignHandler 一票否决
        // （merged=true → 会签节点提前流转 end），flag 落任务/实例变量
        assertOk(call("processTask/execute", args("processTaskId", taskA, "operator", "userA", "submitType", 20)));
        com.mldong.jeeflow.domain.ProcessInstance inst = rawRepo.findInstanceById(instanceId);
        // 一票否决效果：会签节点被提前流转 end（若否决未生效，串行会签将停在 DOING 等 userB）
        assertEquals("会签否决后实例应完成（end 节点 submitType≠2 → FINISHED 20）",
                Integer.valueOf(20), inst.getState());
        assertEquals("countersignDisagreeFlag=1 应落实例变量", Integer.valueOf(1),
                inst.getVariables().get("countersignDisagreeFlag"));
        com.mldong.jeeflow.domain.ProcessTask doneA = rawRepo.findTaskById(taskA);
        assertEquals("否决任务应已完成", com.mldong.jeeflow.enums.ProcessTaskStateEnum.FINISHED.getCode(),
                doneA.getTaskState());
        assertEquals("countersignDisagreeFlag=1 应落任务变量", Integer.valueOf(1),
                doneA.getVariables().get("countersignDisagreeFlag"));
        assertEquals("否决人应记录为实际操作人", "userA", doneA.getActorId());
    }

    // ═══ 列表字段契约 + 时间格式（issues/05-2 / 05-3）═══

    @Test
    public void testListRowContract() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("01-simple.json");
        call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "zhangsan", "amount", 500));
        Map<String, Object> r = call("processTask/todoList", args("operator", "leader"));
        assertOk(r);
        List<?> rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertFalse(rows.isEmpty());
        Map<String, Object> row = (Map<String, Object>) rows.get(0);
        // 05-2：ext（任务变量对象，空回退实例变量）+ instanceExt + version
        assertNotNull(row.get("ext"));
        assertNotNull(row.get("instanceExt"));
        assertNotNull(row.get("version"));
        // 05-3：时间字段为 yyyy-MM-dd HH:mm:ss 格式字符串
        String ct = String.valueOf(row.get("createTime"));
        assertTrue("时间应格式化为 yyyy-MM-dd HH:mm:ss（无 T）: " + ct, ct.contains(" ") && !ct.contains("T"));

        r = call("processInstance/page", args("operator", "zhangsan"));
        assertOk(r);
        rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertFalse(rows.isEmpty());
        row = (Map<String, Object>) rows.get(0);
        assertNotNull(row.get("ext"));       // 实例变量对象
        assertNotNull(row.get("displayName")); // 定义显示名
        assertNotNull(row.get("version"));     // 定义版本
        // issues/82-2：分页五键整体（pageNum/pageSize/rows/recordCount/totalPage）
        Map<String, Object> pageData = (Map<String, Object>) r.get("data");
        for (String k : new String[]{"pageNum", "pageSize", "rows", "recordCount", "totalPage"}) {
            assertTrue("分页五键应含 " + k + ": " + pageData.keySet(), pageData.containsKey(k));
        }
        assertTrue("totalPage 应为正整数", ((Number) pageData.get("totalPage")).intValue() >= 1);
    }

    // ═══ issues/05-5：m_ 前缀查询参数（前端 m_LIKE_name / m_pd_LIKE_* / m_t_LIKE_*）═══

    @Test
    public void testMQueryParams() throws Exception {
        ProcessInstance.ProcessDefine def1 = registerFlow("01-simple.json");
        registerFlow("02-multi-task.json");

        // 无别名 → 默认主表别名 t（t.name / t.display_name，对齐白名单）
        Map<String, Object> r = call("processDefine/page", args("m_LIKE_name", "simple"));
        assertOk(r);
        List<?> rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertEquals("m_LIKE_name 应过滤到 01-simple: " + r, 1, rows.size());
        assertEquals("01-simple", ((Map<String, Object>) rows.get(0)).get("name"));

        r = call("processDefine/page", args("m_LIKE_displayName", "02"));
        assertOk(r);
        rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertEquals("m_LIKE_displayName 应过滤到 02-multi-task: " + r, 1, rows.size());

        r = call("processDefine/page", args("m_LIKE_displayName", ".json"));
        assertOk(r);
        rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertEquals("m_LIKE_displayName 应匹配全部: " + r, 2, rows.size());

        // 实例列表：m_pd_LIKE_displayName（别名 pd → pd.display_name）
        call("processInstance/startAndExecute",
                args("processDefineId", def1.getId(), "operator", "zhangsan"));
        r = call("processInstance/page", args("operator", "zhangsan", "m_pd_LIKE_displayName", "simple"));
        assertOk(r);
        rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertEquals("m_pd_LIKE_displayName 应命中: " + r, 1, rows.size());

        r = call("processInstance/page", args("operator", "zhangsan", "m_pd_LIKE_displayName", "zzz"));
        assertOk(r);
        rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertEquals("m_pd_LIKE_displayName 不应命中: " + r, 0, rows.size());

        // issues/82-6：实例列表按编码搜 m_pd_LIKE_name（pd.name 白名单列）
        r = call("processInstance/page", args("operator", "zhangsan", "m_pd_LIKE_name", "simple"));
        assertOk(r);
        rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertEquals("m_pd_LIKE_name 应命中 01-simple 实例: " + r, 1, rows.size());

        r = call("processInstance/page", args("operator", "zhangsan", "m_pd_LIKE_name", "zzz"));
        assertOk(r);
        rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertEquals("m_pd_LIKE_name 不应命中: " + r, 0, rows.size());

        // 任务列表：m_t_LIKE_displayName（别名 t → t.display_name）
        r = call("processTask/todoList", args("operator", "leader", "m_t_LIKE_displayName", "审批"));
        assertOk(r);
        rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertEquals("m_t_LIKE_displayName 应命中待办: " + r, 1, rows.size());

        r = call("processTask/todoList", args("operator", "leader", "m_t_LIKE_displayName", "zzz"));
        assertOk(r);
        rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertEquals("m_t_LIKE_displayName 不应命中: " + r, 0, rows.size());
    }

    // ═══ issues/07：设计详情 jsonObject 缺失基本信息时从设计表补齐 ═══

    @Test
    public void testDesignDetailJsonObjectMerge() throws Exception {
        String content = new String(Files.readAllBytes(
                Paths.get("src/test/resources/flows/01-simple.json")), java.nio.charset.StandardCharsets.UTF_8);
        // 无 content 保存 → 无 his → jsonObject 为空对象合并基本信息
        Map<String, Object> r = call("processDesign/save", args("name", "test_display",
                "displayName", "回显测试", "operator", "zhangsan"));
        assertOk(r);
        Long designId = toLong(((Map<String, Object>) r.get("data")).get("id"));

        r = call("processDesign/detail", args("id", designId));
        assertOk(r);
        Map<String, Object> data = (Map<String, Object>) r.get("data");
        Map<String, Object> jo = (Map<String, Object>) data.get("jsonObject");
        assertNotNull("jsonObject 应始终返回（issues/07）: " + r, jo);
        assertEquals("test_display", jo.get("name"));
        assertEquals("回显测试", jo.get("displayName"));
        assertEquals(designId, jo.get("processDesignId"));

        // 已有 his（含 name/displayName）→ 保留 his 内容
        r = call("processDesign/save", args("id", designId, "name", "test_display",
                "displayName", "回显测试", "content", content, "operator", "zhangsan"));
        assertOk(r);
        r = call("processDesign/detail", args("id", designId));
        assertOk(r);
        jo = (Map<String, Object>) ((Map<String, Object>) r.get("data")).get("jsonObject");
        assertEquals("his content 的 name 优先: " + r, "simple", jo.get("name"));
    }

    // ═══ issues/08：部署/重新部署/设计稿变更的 is_deployed 状态同步 ═══

    @Test
    public void testDesignDeployRedeployIsDeployed() throws Exception {
        String content = new String(Files.readAllBytes(
                Paths.get("src/test/resources/flows/01-simple.json")), java.nio.charset.StandardCharsets.UTF_8);

        // 保存（含内容快照）→ 未部署
        Map<String, Object> r = call("processDesign/save", args("name", "leave08",
                "displayName", "请假流程08", "content", content, "operator", "zhangsan"));
        assertOk(r);
        Long designId = toLong(((Map<String, Object>) r.get("data")).get("id"));
        assertEquals(0, (int) extRepo.findDesignById(designId).getIsDeployed());

        // 部署 → is_deployed=1
        r = call("processDesign/deploy", args("id", designId, "operator", "zhangsan"));
        assertOk(r);
        Long defineId = toLong(((Map<String, Object>) r.get("data")).get("processDefineId"));
        assertEquals(1, (int) extRepo.findDesignById(designId).getIsDeployed());
        Integer versionAfterDeploy = repo.findDefineById(defineId).getVersion();

        // 重新部署 → 同一 defineId（内容替换，version 不变）+ is_deployed=1
        r = call("processDesign/redeploy", args("id", designId, "operator", "zhangsan"));
        assertOk(r);
        assertEquals(defineId, toLong(((Map<String, Object>) r.get("data")).get("processDefineId")));
        assertEquals(1, (int) extRepo.findDesignById(designId).getIsDeployed());
        // issues/59：redeploy 是替换语义，version 必须保持（JDBC 仓储曾因 def 未携带 version 兜底误写 1）
        assertEquals("redeploy 后 version 应不变", versionAfterDeploy, repo.findDefineById(defineId).getVersion());

        // 设计稿内容变更（updateDefine，不同 content）→ 新快照 + is_deployed=0
        String content2 = new String(Files.readAllBytes(
                Paths.get("src/test/resources/flows/02-multi-task.json")), java.nio.charset.StandardCharsets.UTF_8);
        r = call("processDesign/updateDefine", args("processDesignId", designId,
                "content", content2, "operator", "zhangsan"));
        assertOk(r);
        assertEquals(0, (int) extRepo.findDesignById(designId).getIsDeployed());
        assertEquals(2, extRepo.listDesignHis(designId).size());
        assertEquals("updateDefine 应同步 name: " + r, "multi-task", extRepo.findDesignById(designId).getName());

        // 基本信息修改（update）→ is_deployed 不变
        r = call("processDesign/update", args("id", designId, "displayName", "改名08", "operator", "zhangsan"));
        assertOk(r);
        assertEquals("改名08", extRepo.findDesignById(designId).getDisplayName());
        assertEquals(0, (int) extRepo.findDesignById(designId).getIsDeployed());

        // 部署 → 再置 1
        r = call("processDesign/deploy", args("id", designId, "operator", "zhangsan"));
        assertOk(r);
        assertEquals(1, (int) extRepo.findDesignById(designId).getIsDeployed());
    }

    // ═══ issues/15：formData / taskFormData / 审批记录 ext 契约 ═══

    @Test
    public void testFormDataContract() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("01-simple.json");
        Map<String, Object> r = call("processInstance/startAndExecute", args("processDefineId", def.getId(),
                "operator", "zhangsan", "f_reasonType", "休假", "f_amount", 500));
        assertOk(r);
        Long instanceId = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));

        // 实例详情：formData（f_ 前缀 + 去前缀副本）+ displayName/name/version
        r = call("processInstance/detail", args("id", instanceId));
        assertOk(r);
        Map<String, Object> data = (Map<String, Object>) r.get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> formData = (Map<String, Object>) data.get("formData");
        assertNotNull("formData 应返回（issues/15）: " + r, formData);
        assertEquals("休假", formData.get("f_reasonType"));
        assertEquals("休假", formData.get("reasonType"));
        assertEquals(500, ((Number) formData.get("f_amount")).intValue());
        assertEquals("01-simple", data.get("name"));
        assertNotNull(data.get("displayName"));
        assertNotNull(data.get("version"));

        // 执行任务（tf_ 前缀变量 → 任务变量）→ 待办/已办行 taskFormData + 审批记录 ext
        r = call("processTask/todoList", args("operator", "leader"));
        assertOk(r);
        List<?> rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertFalse(rows.isEmpty());
        Long taskId = toLong(((Map<String, Object>) rows.get(0)).get("id"));
        r = call("processTask/execute", args("processTaskId", taskId, "operator", "leader",
                "tf_approvalComment", "同意"));
        assertOk(r);

        r = call("processTask/doneList", args("operator", "leader"));
        assertOk(r);
        rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertFalse(rows.isEmpty());
        @SuppressWarnings("unchecked")
        Map<String, Object> taskFormData = (Map<String, Object>) ((Map<String, Object>) rows.get(0)).get("taskFormData");
        assertNotNull("任务行 taskFormData 应返回（issues/15）: " + r, taskFormData);
        assertEquals("同意", taskFormData.get("tf_approvalComment"));
        assertEquals("同意", taskFormData.get("approvalComment"));
        // 82-8：doneList 行 finishTime 已格式化（yyyy-MM-dd HH:mm:ss 无 T）
        Object ft = ((Map<String, Object>) rows.get(0)).get("finishTime");
        assertNotNull("doneList 行 finishTime 应非空（已办任务）: " + r, ft);
        assertTrue("doneList finishTime 应格式化 yyyy-MM-dd HH:mm:ss（无 T）: " + ft,
                ft.toString().matches("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}"));

        r = call("processInstance/approvalRecord", args("id", instanceId));
        assertOk(r);
        rows = (List<?>) r.get("data");
        boolean hasExt = false;
        for (Object o : rows) {
            if (((Map<String, Object>) o).get("ext") != null) hasExt = true;
        }
        assertTrue("审批记录行应含 ext（issues/15）: " + r, hasExt);
    }

    // ═══ candidatePage 双源候选（issues/16 GlobalCandidateHandler 语义）═══

    @Test
    public void testCandidatePageDualSource() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("12-candidate-page.json");
        Map<String, Object> r = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "user1"));
        assertOk(r);
        Long instanceId = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));

        r = call("processTask/todoList", args("operator", "leader"));
        assertOk(r);
        List<?> rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertFalse(rows.isEmpty());
        Long taskId = toLong(((Map<String, Object>) rows.get(0)).get("id"));

        // candidatePage：当前任务 apply → 后继节点 review 的候选
        // （startAndExecute 已自动完成 apply，直接用 engine 启动拿 apply 任务）
        com.mldong.jeeflow.domain.FlowData startArgs = com.mldong.jeeflow.domain.FlowData.create();
        com.mldong.jeeflow.domain.ProcessInstance inst2 =
                engine.startProcessInstanceById(def.getId(), "user1", startArgs);
        List<com.mldong.jeeflow.domain.ProcessTask> applyTasks =
                repo.findDoingTasks(inst2.getInstanceId(), new String[]{});
        assertFalse(applyTasks.isEmpty());
        assertEquals("apply", applyTasks.get(0).getTaskName());
        r = call("processTask/candidatePage", args("processTaskId", applyTasks.get(0).getTaskId()));
        assertOk(r);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>)
                ((Map<String, Object>) r.get("data")).get("rows");
        assertNotNull("candidatePage 应返回候选: " + r, candidates);
        List<String> userIds = new ArrayList<>();
        for (Map<String, Object> c : candidates) userIds.add(String.valueOf(c.get("userId")));
        // issues/80：模型候选行键对齐前端 UserSelect（valueField='id'）——断言 id 键存在且可取值，不再依赖 userId
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> c : candidates) {
            assertNotNull("候选行应有 id 键（前端 valueField='id' 取值）: " + c, c.get("id"));
            assertNotNull("候选行应有 realName 键: " + c, c.get("realName"));
            ids.add(String.valueOf(c.get("id")));
        }
        assertEquals("id 与 userId 应一一对齐（行键归一）", userIds, ids);
        assertTrue("id 应含 candidateUsers 指定人 userA: " + ids, ids.contains("userA"));
        assertTrue("应含 candidateUsers 指定人 userA: " + userIds, userIds.contains("userA"));
        assertTrue("应含 candidateUsers 指定人 userB: " + userIds, userIds.contains("userB"));
        assertTrue("应含 candidateGroups 角色成员 finA: " + userIds, userIds.contains("finA"));
        assertTrue("应含 candidateGroups 角色成员 finB: " + userIds, userIds.contains("finB"));
    }

    // ═══ startAndExecute 预指派人（f_nextNodeOperator → tf_，对齐 boot3）═══

    @Test
    public void testStartAndExecutePreAssign() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("01-simple.json");

        // 发起并预指派人：f_nextNodeOperator=userA → 自动完成 apply → task1 参与者 = userA（非 leader）
        Map<String, Object> r = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "user1",
                        com.mldong.jeeflow.enums.FlowConst.PROCESS_START_NEXT_NODE_OPERATOR, "userA"));
        assertOk(r);
        Long inst1 = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));

        r = call("processTask/todoList", args("operator", "userA"));
        assertOk(r);
        List<?> rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        assertFalse("userA 应收到 task1 待办（预指派人）: " + r, rows.isEmpty());
        assertEquals("task1", ((Map<String, Object>) rows.get(0)).get("taskName"));

        // 未指定时按 assignee（leader）→ 第二个实例 task1 参与者是 leader（userA 待办不应新增）
        r = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "user1"));
        assertOk(r);
        Long inst2 = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));
        java.util.List<com.mldong.jeeflow.domain.ProcessTask> t2 =
                repo.findDoingTasks(inst2, new String[]{});
        assertFalse(t2.isEmpty());
        assertEquals("未指定时 task1 参与者应为 leader: " + t2.get(0).getActorIds(),
                java.util.Arrays.asList("leader"), t2.get(0).getActorIds());
        r = call("processTask/todoList", args("operator", "userA"));
        assertOk(r);
        rows = (List<?>) ((Map<String, Object>) r.get("data")).get("rows");
        for (Object o : rows) {
            Long pid = toLong(((Map<String, Object>) o).get("processInstanceId"));
            assertFalse("第二个实例的 task1 不应出现在 userA 待办: " + r, pid.equals(inst2));
        }
    }


    /** 测试辅助：读 01-simple 流程定义并注入 relTableName（bizData 测试用） */
    private String simpleFlowWithRelTable(String relTableName) throws Exception {
        String json = new String(Files.readAllBytes(
                Paths.get("src/test/resources/flows/01-simple.json")), StandardCharsets.UTF_8);
        return json.replace("\"type\": \"approval\"", "\"type\": \"approval\", \"relTableName\": \"" + relTableName + "\"");
    }

    /** 测试辅助：直接建一条设计记录 */
    private void saveDesign(Long id, String name, String displayName, String type) {
        ProcessDesign d = new ProcessDesign();
        d.setId(id);
        d.setName(name);
        d.setDisplayName(displayName);
        d.setType(type);
        d.setIcon(null);
        d.setRemark(null);
        d.setIsDeployed(0);
        extRepo.saveDesign(d);
    }

    // ═══ issues/27：updateDefine 兼容 boot3 顶层 JSON ═══

    @Test
    public void testUpdateDefineTopLevelJson() throws Exception {
        saveDesign(100L, "old", "旧名", "approval");
        // 保存设计（顶层 JSON 无 content）
        Map<String, Object> r = call("processDesign/updateDefine", args(
                "processDesignId", 100L, "operator", "user1",
                "name", "topjson", "displayName", "顶层JSON流程", "type", "approval",
                "relTableName", "biz_top", "persistMode", "SYNC",
                "nodes", new ArrayList<>(), "edges", new ArrayList<>()));
        assertOk(r);
        // 设计信息同步（name 来自顶层 JSON）
        ProcessDesign d = extRepo.findDesignById(100L);
        assertNotNull(d);
        assertEquals("topjson", d.getName());
        assertEquals("顶层JSON流程", d.getDisplayName());
        // 历史内容 = 顶层 JSON 序列化（含 nodes/edges，不含 processDesignId/operator）
        List<ProcessDesignHis> his = extRepo.listDesignHis(100L);
        assertFalse("应写入内容快照", his.isEmpty());
        String content = new String(his.get(0).getContent(), StandardCharsets.UTF_8);
        assertTrue("content 应含 nodes: " + content, content.contains("\"nodes\""));
        assertFalse("content 不应含 processDesignId", content.contains("processDesignId"));
        assertFalse("content 不应含 operator", content.contains("\"operator\""));
    }

    // ═══ issues/28：批量语义 + listByType + bizData ═══

    @Test
    public void testBatchRemoveAndUpDown() throws Exception {
        // 两个设计 → 发布为两个定义
        Long[] defineIds = new Long[2];
        int i = 0;
        for (Long id : Arrays.asList(201L, 202L)) {
            saveDesign(id, "batch" + id, "批量流程" + id, "approval");
            call("processDesign/updateDefine", args(
                    "processDesignId", id, "operator", "user1",
                    "name", "batch" + id, "displayName", "批量流程" + id, "type", "approval",
                    "nodes", new ArrayList<>(), "edges", new ArrayList<>()));
            Map<String, Object> dr = call("processDesign/deploy", args("id", id, "operator", "user1"));
            assertOk(dr);
            defineIds[i++] = toLong(((Map<String, Object>) dr.get("data")).get("processDefineId"));
        }
        // 批量上下架 {ids, opType}
        Map<String, Object> r = call("processDefine/upAndDown", args(
                "ids", Arrays.asList(defineIds[0], defineIds[1]), "opType", 0));
        assertOk(r);
        assertEquals(Integer.valueOf(0), rawRepo.findDefineById(defineIds[0]).getState());
        assertEquals(Integer.valueOf(0), rawRepo.findDefineById(defineIds[1]).getState());
        // 批量删除设计 {ids}（单 {id} 回归）
        r = call("processDesign/remove", args("ids", Arrays.asList(201L, 202L)));
        assertOk(r);
        assertNull(extRepo.findDesignById(201L));
        assertNull(extRepo.findDesignById(202L));
        r = call("processDesign/remove", args("id", 203L));
        assertOk(r);
    }

    @Test
    public void testDesignListByType() throws Exception {
        saveDesign(301L, "leave1", "请假", "approval");
        saveDesign(302L, "reimburse1", "报销", "finance");
        call("processDesign/updateDefine", args(
                "processDesignId", 301L, "operator", "user1",
                "name", "leave1", "displayName", "请假", "type", "approval",
                "nodes", new ArrayList<>(), "edges", new ArrayList<>()));
        call("processDesign/updateDefine", args(
                "processDesignId", 302L, "operator", "user1",
                "name", "reimburse1", "displayName", "报销", "type", "finance",
                "nodes", new ArrayList<>(), "edges", new ArrayList<>()));
        Map<String, Object> r = call("processDesign/listByType", args());
        assertOk(r);
        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> groups =
                (Map<String, List<Map<String, Object>>>) r.get("data");
        assertTrue("应含 approval 分组: " + groups.keySet(), groups.containsKey("approval"));
        assertTrue("应含 finance 分组: " + groups.keySet(), groups.containsKey("finance"));
        assertEquals("leave1", groups.get("approval").get(0).get("name"));
        assertEquals("reimburse1", groups.get("finance").get(0).get("name"));
        // jsonObject 回显（最新设计稿）
        assertNotNull(groups.get("approval").get(0).get("jsonObject"));
    }

    /** issues/81：listByType items 必含 processDefineState（前端发起按钮硬依赖），取值随定义 state 联动 */
    @Test
    public void testDesignListByTypeProcessDefineState() throws Exception {
        saveDesign(501L, "leave81", "请假81", "approval");
        call("processDesign/updateDefine", args(
                "processDesignId", 501L, "operator", "user1",
                "name", "leave81", "displayName", "请假81", "type", "approval",
                "nodes", new ArrayList<>(), "edges", new ArrayList<>()));
        // 场景 A：deploy → 定义 state=1 → 可发起
        Map<String, Object> dr = call("processDesign/deploy", args("id", 501L, "operator", "user1"));
        assertOk(dr);
        Long defineId = toLong(((Map<String, Object>) dr.get("data")).get("processDefineId"));

        Map<String, Object> r = call("processDesign/listByType", args());
        assertOk(r);
        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> groups =
                (Map<String, List<Map<String, Object>>>) r.get("data");
        Map<String, Object> itemA = groups.get("approval").stream()
                .filter(m -> "leave81".equals(m.get("name")))
                .findFirst().orElseThrow(() -> new AssertionError("缺 leave81 item: " + groups));
        assertEquals("processDefineId 应回显", defineId, itemA.get("processDefineId"));
        assertEquals("启用定义 processDefineState 应为 1（前端可发起）",
                Integer.valueOf(1), itemA.get("processDefineState"));

        // 场景 B：upAndDown 禁用 → 定义 state=0 → 前端置灰
        Map<String, Object> ur = call("processDefine/upAndDown", args("id", defineId, "opType", 0));
        assertOk(ur);
        r = call("processDesign/listByType", args());
        assertOk(r);
        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> groups2 =
                (Map<String, List<Map<String, Object>>>) r.get("data");
        Map<String, Object> itemB = groups2.get("approval").stream()
                .filter(m -> "leave81".equals(m.get("name")))
                .findFirst().orElseThrow(() -> new AssertionError("缺 leave81 item: " + groups2));
        assertEquals("禁用定义 processDefineState 应为 0（前端置灰）",
                Integer.valueOf(0), itemB.get("processDefineState"));
    }

    /** bizData：未注册 MetaTableReader → 清晰报错 */
    @Test
    public void testBizDataUnregistered() throws Exception {
        // 真实实例 + 未注册 MetaTableReader
        saveDesign(400L, "bizflow", "业务流程", "approval");
        call("processDesign/updateDefine", args(
                "processDesignId", 400L, "operator", "user1",
                "content", simpleFlowWithRelTable("biz_leave")));
        Map<String, Object> dr = call("processDesign/deploy", args("id", 400L, "operator", "user1"));
        assertOk(dr);
        Long defineId = toLong(((Map<String, Object>) dr.get("data")).get("processDefineId"));
        Map<String, Object> sr = call("processInstance/startAndExecute", args(
                "processDefineId", defineId, "operator", "user1", "title", "x"));
        assertOk(sr);
        Long instId = toLong(((Map<String, Object>) sr.get("data")).get("processInstanceId"));
        Map<String, Object> r = call("processInstance/bizData", args("processInstanceId", instId));
        assertNotEquals("未注册应报错", Integer.valueOf(0), r.get("code"));
        assertTrue("报错应提示注册: " + r, String.valueOf(r.get("msg")).contains("metaTableReader"));
    }

    /** bizData：注册读取器（mock，同 MetaTableReader 契约）→ 回显正常 */
    @Test
    public void testBizDataRegistered() throws Exception {
        // mock 读取器（public 静态类——facade 反射调用需可访问）
        ServiceContext.put("metaTableReader", new MockMetaTableReader());
        // 发布带 relTableName 的定义
        saveDesign(401L, "bizflow", "业务流程", "approval");
        call("processDesign/updateDefine", args(
                "processDesignId", 401L, "operator", "user1",
                "content", simpleFlowWithRelTable("biz_leave")));
        Map<String, Object> dr = call("processDesign/deploy", args("id", 401L, "operator", "user1"));
        assertOk(dr);
        Long defineId = toLong(((Map<String, Object>) dr.get("data")).get("processDefineId"));
        // 发起实例
        Map<String, Object> r = call("processInstance/startAndExecute", args(
                "processDefineId", defineId, "operator", "user1", "title", "x"));
        assertOk(r);
        Long instId = toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));
        r = call("processInstance/bizData", args("processInstanceId", instId));
        assertOk(r);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) r.get("data");
        assertEquals("biz_leave", data.get("tableName"));
        assertEquals("业务数据", data.get("title"));
    }

    // ═══ issues/29：action 权限码 SPI 默认实现 ═══

    @Test
    public void testActionPermissionDefaultProvider() {
        com.mldong.jeeflow.spi.IActionPermissionProvider provider =
                ServiceContext.find(com.mldong.jeeflow.spi.IActionPermissionProvider.class);
        assertNotNull("引擎 configure 应注册默认实现", provider);
        // 默认规则：wf:{action:/→:}
        assertArrayEquals(new String[]{"wf:processDefine:page"}, provider.permissionCodes("processDefine/page"));
        // OR 语义：detail 任一码
        String[] or = provider.permissionCodes("processDefine/detail");
        assertTrue(or.length >= 2);
        assertTrue(Arrays.asList(or).contains("wf:processDefine:detail"));
        assertTrue(Arrays.asList(or).contains("wf:processDesign:listByType"));
        // 放行（登录即可）
        assertNull(provider.permissionCodes("processInstance/detail"));
        assertNull(provider.permissionCodes("processInstance/bizData"));
    }

}
