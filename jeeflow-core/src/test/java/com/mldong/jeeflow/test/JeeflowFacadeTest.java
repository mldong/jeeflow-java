package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.facade.JeeflowFacade;
import com.mldong.jeeflow.interceptor.AssignmentHandler;
import com.mldong.jeeflow.metadata.EnumDictRegistry;
import com.mldong.jeeflow.metadata.HandlerMeta;
import com.mldong.jeeflow.metadata.HandlerRegistry;
import com.mldong.jeeflow.spi.IExpressionEvaluator;
import com.mldong.jeeflow.spi.IProcessExtRepository;
import com.mldong.jeeflow.spi.IProcessRepository;
import com.mldong.jeeflow.spi.IUserProvider;
import com.mldong.jeeflow.spi.IUserProvider.UserInfo;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * JeeflowFacade 统一门面测试（v1.1.0）——flow(action, map) 全 action 路由
 */
public class JeeflowFacadeTest {

    private JeeflowFacade facade;
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

        JeeflowEngine engine = new JeeflowEngineImpl();
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

    // ═══ 引擎元数据 action（v1.4.0）═══

    @Test
    public void testMetadataActions() {
        // dictKeys：7 个 key 对齐 boot3
        Map<String, Object> r1 = facade.flow("metadata/dictKeys", new java.util.HashMap<>());
        assertEquals(0, r1.get("code"));
        @SuppressWarnings("unchecked")
        List<String> keys = (List<String>) r1.get("data");
        assertEquals(7, keys.size());
        assertTrue(keys.contains("wf_process_instance_state"));

        // dict：按 key 取字典
        Map<String, Object> r2 = facade.flow("metadata/dict",
                java.util.Collections.singletonMap("key", "wf_process_instance_state"));
        assertEquals(0, r2.get("code"));
        @SuppressWarnings("unchecked")
        List<EnumDictRegistry.DictItem> items = (List<EnumDictRegistry.DictItem>) r2.get("data");
        assertEquals(7, items.size());
        assertEquals("10", items.get(0).getValue());
        assertEquals("进行中", items.get(0).getLabel());

        // dict：key 缺失报错
        Map<String, Object> r3 = facade.flow("metadata/dict", new java.util.HashMap<>());
        assertEquals(99999999, r3.get("code"));

        // handlers：未注入注册中心返回空清单
        Map<String, Object> r4 = facade.flow("metadata/handlers",
                java.util.Collections.singletonMap("type", "AssignmentHandler"));
        assertEquals(0, r4.get("code"));
        assertTrue(((List<?>) r4.get("data")).isEmpty());

        // handlers：注入注册中心后按类型/分组列出
        HandlerRegistry registry = new HandlerRegistry();
        registry.register(AssignmentHandler.class, "com.example.DeptLeaderHandler", "部门领导审批", 2, null);
        registry.register(AssignmentHandler.class, "com.example.BossHandler", "老板审批", 1, null);
        facade.setHandlerRegistry(registry);
        Map<String, Object> r5 = facade.flow("metadata/handlers",
                java.util.Collections.singletonMap("type", "AssignmentHandler"));
        assertEquals(0, r5.get("code"));
        @SuppressWarnings("unchecked")
        List<HandlerMeta> metas = (List<HandlerMeta>) r5.get("data");
        assertEquals(2, metas.size());
        assertEquals("com.example.BossHandler", metas.get(0).getClassName());

        // handlers：type 缺失报错
        Map<String, Object> r6 = facade.flow("metadata/handlers", new java.util.HashMap<>());
        assertEquals(99999999, r6.get("code"));
    }
}
