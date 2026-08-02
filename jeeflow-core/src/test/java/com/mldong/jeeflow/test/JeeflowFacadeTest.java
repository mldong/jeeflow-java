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
}
