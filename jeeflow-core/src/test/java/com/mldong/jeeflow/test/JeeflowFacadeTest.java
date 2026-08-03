package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.ProcessInstance;
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

        // 保存设计（含内容快照）
        Map<String, Object> r = call("processDesign/save", args(
                "name", "leave", "displayName", "请假流程", "content", content, "operator", "zhangsan"));
        assertOk(r);
        Long designId = toLong(((Map<String, Object>) r.get("data")).get("id"));
        assertNotNull(designId);

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

        // 抄送：创建 + 我的抄送 + 已读
        r = call("processInstance/createCCInstance",
                args("processInstanceId", instanceId, "operator", "zhangsan", "actorIds", List.of("lisi", "wangwu")));
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
        r = call("processTask/addCandidate", args("processTaskId", doing.get(0).getTaskId(), "actorIds", List.of("zhaoliu")));
        assertOk(r);
        assertTrue(rawRepo.findTaskActors(doing.get(0).getTaskId()).contains("zhaoliu"));
        r = call("processTask/surrogate", args("processTaskId", doing.get(0).getTaskId(), "actorIds", List.of("sunqi")));
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
                rawRepo.addTaskActor(t.getTaskId(), java.util.List.of("leader"));
                call("processTask/execute", args("processTaskId", t.getTaskId(), "operator", "leader", "submitType", 1));
            }
        }
        doing = rawRepo.findDoingTasks(instanceId, null);
        for (com.mldong.jeeflow.domain.ProcessTask t : doing) {
            if ("task3".equals(t.getTaskName())) {
                rawRepo.addTaskActor(t.getTaskId(), java.util.List.of("director"));
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

        // 重新部署 → 同一 defineId（内容替换，version 不变）+ is_deployed=1
        r = call("processDesign/redeploy", args("id", designId, "operator", "zhangsan"));
        assertOk(r);
        assertEquals(defineId, toLong(((Map<String, Object>) r.get("data")).get("processDefineId")));
        assertEquals(1, (int) extRepo.findDesignById(designId).getIsDeployed());

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
        Long instanceId = toLong(((Map<String, Object>) r.get("data")).get("id"));

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
        assertTrue("应含 candidateUsers 指定人 userA: " + userIds, userIds.contains("userA"));
        assertTrue("应含 candidateUsers 指定人 userB: " + userIds, userIds.contains("userB"));
        assertTrue("应含 candidateGroups 角色成员 finA: " + userIds, userIds.contains("finA"));
        assertTrue("应含 candidateGroups 角色成员 finB: " + userIds, userIds.contains("finB"));
    }
}
