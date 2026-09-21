package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessSurrogate;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessInstanceStateEnum;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
import com.mldong.jeeflow.facade.JeeflowFacade;
import com.mldong.jeeflow.spi.IProcessExtRepository;
import com.mldong.jeeflow.spi.IUserProvider;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * 委托代理自动生效（内存仓一路，issues/116 / 规范 06 §4.5 运行期语义 / 08-compliance 用例 26）
 *
 * <p>断言一律落在**持久值读回**（{@code findTaskActors} 读参与者表、{@code findTaskById} 重载任务），
 * 不断"返回码 0"——参与者没进表就等于没生效。</p>
 *
 * <p>SQL 仓一路的同口径用例见 jeeflow-repository-jdbc 模块
 * {@code SurrogateAutoApplyJdbcTest}（直接查 {@code wf_process_task_actor}）。</p>
 */
public class SurrogateAutoApplyTest {

    /** 流程名（= 01-simple.json 内容里的 name，委托 processName 用它） */
    private static final String FLOW = "simple";
    /** 01-simple.json 首个任务节点 assignee=applicant → 参与者即发起人 */
    private static final String PRINCIPAL = "zhangsan";
    private static final String AGENT = "lisi";

    private MemoryProcessRepository repo;
    private MemoryProcessExtRepository extRepo;
    private JeeflowEngineImpl engine;

    // ═══ 装配 ═══

    /**
     * @param autoApply   引擎内置开关（Configuration#surrogateAutoApply）
     * @param withExtRepo 是否把内存扩展仓储注册进 ServiceContext（false = 「未配置扩展仓储」部署形态）
     */
    private void boot(boolean autoApply, boolean withExtRepo) {
        Configuration config = new Configuration();          // 每次重置 ServiceContext（全局静态）
        repo = new MemoryProcessRepository();
        extRepo = new MemoryProcessExtRepository();
        ServiceContext.put("repository", repo);
        ServiceContext.put("json", new TestJsonProvider());
        ServiceContext.put("expr", new TestExpressionEvaluator());
        ServiceContext.put("user", new IUserProvider() {
            @Override public IUserProvider.UserInfo getUser(String userId) {
                IUserProvider.UserInfo u = new IUserProvider.UserInfo();
                u.setUserId(userId);
                u.setRealName("用户" + userId);
                return u;
            }
        });
        if (withExtRepo) {
            ServiceContext.put("ext", (IProcessExtRepository) extRepo);
        }
        engine = new JeeflowEngineImpl();
        engine.configure(config.surrogateAutoApply(autoApply));
    }

    private ProcessInstance.ProcessDefine registerSimpleFlow() throws Exception {
        return registerFlow("01-simple.json");
    }

    private ProcessInstance.ProcessDefine registerFlow(String filename) throws Exception {
        return registerFlowAs(filename, FLOW);
    }

    /**
     * 注册流程，但**定义行的 name 与 JSON 模型 name 可以不一致**——
     * 正常 deploy 下 `def.setName(model.getName())` 恒等，只有直接落库的定义（自带导入链路、
     * 本用例夹具）才会不一致。契约 1.1 要求保留这种诱饵行，钉住自己取的是哪一头。
     */
    private ProcessInstance.ProcessDefine registerFlowAs(String filename, String defineName) throws Exception {
        return registerRaw(Files.readAllBytes(Paths.get("src/test/resources/flows/" + filename)), defineName);
    }

    private ProcessInstance.ProcessDefine registerRaw(byte[] content, String defineName) {
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName(defineName);
        def.setDisplayName("简单审批流程");
        def.setType("approval");
        def.setState(1);
        def.setVersion(1);
        def.setContent(content);
        repo.addDefine(def);
        return def;
    }

    /**
     * 改写流程 JSON 的**模型 name**：`name == null` 抹掉该键（模拟"JSON 未带 name"），
     * 否则替换成给定值（传 `""` 即空串形态）。只动首个（顶层）name 键。
     */
    private static byte[] withModelName(byte[] json, String name) {
        String text = new String(json, StandardCharsets.UTF_8);
        String replacement = name == null ? "" : "\"name\": \"" + name + "\",";
        return text.replaceFirst("\"name\"\\s*:\\s*\"[^\"]*\"\\s*,", replacement)
                .getBytes(StandardCharsets.UTF_8);
    }

    /** 台账里写一条委托（走 SPI，与门面 processSurrogate/save 同一存储路径） */
    private ProcessSurrogate ledger(String operator, String agent, String processName,
                                    LocalDateTime start, LocalDateTime end, Integer enabled) {
        return saveLedger(null, operator, agent, processName, start, end, enabled);
    }

    /**
     * 台账里写一条**指定主键 id** 的委托（契约 1.4 的夹具要故意让 id 序与插入序不一致）。
     * `saveSurrogate` 对非 null 的 id 不再自动分配，两仓同约定。
     */
    private ProcessSurrogate ledgerWithId(long id, String operator, String agent, String processName,
                                          LocalDateTime start, LocalDateTime end, Integer enabled) {
        return saveLedger(id, operator, agent, processName, start, end, enabled);
    }

    private ProcessSurrogate saveLedger(Long id, String operator, String agent, String processName,
                                        LocalDateTime start, LocalDateTime end, Integer enabled) {
        ProcessSurrogate s = new ProcessSurrogate();
        s.setId(id);
        s.setOperator(operator);
        s.setSurrogate(agent);
        s.setProcessName(processName);
        s.setStartTime(start);
        s.setEndTime(end);
        s.setEnabled(enabled);
        extRepo.saveSurrogate(s);
        return s;
    }

    private ProcessTask firstDoingTask(Long instanceId) {
        List<ProcessTask> doing = repo.findDoingTasks(instanceId, null);
        assertFalse("建单应产生待办任务", doing.isEmpty());
        return doing.get(0);
    }

    private ProcessTask doingTask(Long instanceId, String taskName) {
        return repo.findDoingTasks(instanceId, null).stream()
                .filter(t -> taskName.equals(t.getTaskName())).findFirst()
                .orElseThrow(() -> new AssertionError("未找到进行中任务 " + taskName));
    }

    // ── 门面写侧辅助（契约 5「enabled 读写两侧分别定」） ──

    /** processSurrogate/save 的"enabled 键缺省"哨兵（区别于显式传 null/空串） */
    private static final Object OMIT_ENABLED_KEY = new Object();

    /** 走门面 processSurrogate/save 写台账——写侧口径的用例入口，不直接调 SPI */
    private Map<String, Object> saveSurrogateViaFacade(String operator, String agent,
                                                       String processName, Object enabled) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("operator", operator);
        body.put("surrogate", agent);
        body.put("processName", processName);
        if (enabled != OMIT_ENABLED_KEY) body.put("enabled", enabled);
        body.put("startTime", "2000-01-01 00:00:00");
        body.put("endTime", "2099-12-31 23:59:59");
        @SuppressWarnings("unchecked")
        Map<String, Object> r = (Map<String, Object>)
                new JeeflowFacade(engine, repo, extRepo).flow("processSurrogate/save", body);
        return r;
    }

    private static Long savedId(Map<String, Object> saveResult) {
        Object id = ((Map<?, ?>) saveResult.get("data")).get("id");
        assertNotNull("save 应返回 data.id: " + saveResult, id);
        return Long.valueOf(String.valueOf(id));
    }

    /** enabled 的"读回"走台账 API processSurrogate/detail，不碰内存对象 */
    private Integer detailEnabled(Long id) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        @SuppressWarnings("unchecked")
        Map<String, Object> r = (Map<String, Object>)
                new JeeflowFacade(engine, repo, extRepo).flow("processSurrogate/detail", body);
        assertEquals("detail 应成功: " + r, Integer.valueOf(0), r.get("code"));
        return (Integer) ((Map<?, ?>) r.get("data")).get("enabled");
    }

    private ProcessInstance startSimpleAsPrincipal() throws Exception {
        return engine.startProcessInstanceById(registerSimpleFlow().getId(), PRINCIPAL, FlowData.create());
    }

    /** 跨层对拍：写侧落 0 的行，读侧（建单那一刻）必须查不到生效委托 */
    private void assertAgentNotApplied(String message) throws Exception {
        assertEquals(message + "——参与者应只有授权人", java.util.Collections.singletonList(PRINCIPAL),
                repo.findTaskActors(firstDoingTask(startSimpleAsPrincipal().getInstanceId()).getTaskId()));
    }

    /** 跨层对拍：写侧落 1 的行，读侧（建单那一刻）必须真把代理人并进参与者表 */
    private void assertAgentApplied(String message) throws Exception {
        List<String> persisted =
                repo.findTaskActors(firstDoingTask(startSimpleAsPrincipal().getInstanceId()).getTaskId());
        assertTrue(message + "（实际=" + persisted + "）", persisted.contains(AGENT));
        assertTrue("授权人保留、任一可办（实际=" + persisted + "）", persisted.contains(PRINCIPAL));
    }

    // ═══ 用例 26 ①：窗口内配「张三→李四」→ 李四真进参与者表，张三那行仍在 ═══

    @Test
    public void inWindowSurrogateAppendsAgentIntoPersistedActors() throws Exception {
        boot(true, true);
        ledger(PRINCIPAL, AGENT, FLOW, LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1), 1);
        ProcessInstance inst = engine.startProcessInstanceById(registerSimpleFlow().getId(),
                PRINCIPAL, FlowData.create());
        assertNotNull(inst);

        ProcessTask apply = firstDoingTask(inst.getInstanceId());
        // 读回**已落库**的参与者（不是内存对象上的集合）
        List<String> persisted = repo.findTaskActors(apply.getTaskId());
        assertTrue("代理人必须出现在参与者表里（实际=" + persisted + "）", persisted.contains(AGENT));
        assertTrue("授权人必须保留、任一可办（实际=" + persisted + "）", persisted.contains(PRINCIPAL));
        assertEquals("只应多出代理人一行: " + persisted, 2, persisted.size());

        // 重载任务后代理人可办（持久值驱动鉴权，不是当场内存对象）
        ProcessTask reloaded = repo.findTaskById(apply.getTaskId());
        assertTrue("代理人应可办理该任务", reloaded.isAllowed(AGENT));
        assertTrue("授权人应仍可办理该任务", reloaded.isAllowed(PRINCIPAL));
    }

    // ═══ 用例 26 ②：窗外 / enabled=0 / 自己委托给自己 → 代理人无行 ═══

    @Test
    public void outOfWindowNotApplied() throws Exception {
        boot(true, true);
        String op = "op-out";
        ledger(op, AGENT, FLOW, LocalDateTime.now().minusDays(10),
                LocalDateTime.now().minusDays(5), 1);      // 已过期
        ProcessInstance inst = engine.startProcessInstanceById(registerSimpleFlow().getId(),
                op, FlowData.create());
        assertEquals("窗外委托不得追加代理人", java.util.Collections.singletonList(op),
                repo.findTaskActors(firstDoingTask(inst.getInstanceId()).getTaskId()));
    }

    @Test
    public void disabledSurrogateNotApplied() throws Exception {
        boot(true, true);
        String op = "op-off";
        ledger(op, AGENT, FLOW, LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1), 0);       // 停用
        ProcessInstance inst = engine.startProcessInstanceById(registerSimpleFlow().getId(),
                op, FlowData.create());
        assertEquals("enabled=0 不得追加代理人", java.util.Collections.singletonList(op),
                repo.findTaskActors(firstDoingTask(inst.getInstanceId()).getTaskId()));
    }

    @Test
    public void selfDelegationNotApplied() throws Exception {
        boot(true, true);
        String op = "op-self";
        ledger(op, op, FLOW, null, null, 1);               // 自己委托给自己
        ProcessInstance inst = engine.startProcessInstanceById(registerSimpleFlow().getId(),
                op, FlowData.create());
        assertEquals("自委托不得生效（也不得把自己加第二行）", java.util.Collections.singletonList(op),
                repo.findTaskActors(firstDoingTask(inst.getInstanceId()).getTaskId()));
    }

    // ═══ 用例 26：未配置 IProcessExtRepository → 建单不被打断 ═══

    @Test
    public void missingExtRepositorySilentlySkipsAndDoesNotBreakStart() throws Exception {
        boot(true, false);                                 // 无扩展仓储
        ProcessInstance inst = engine.startProcessInstanceById(registerSimpleFlow().getId(),
                PRINCIPAL, FlowData.create());
        assertNotNull("缺扩展仓储不得打断建单", inst);
        assertEquals(ProcessInstanceStateEnum.DOING.getCode(), inst.getState());
        ProcessTask apply = firstDoingTask(inst.getInstanceId());
        assertEquals(java.util.Collections.singletonList(PRINCIPAL),
                repo.findTaskActors(apply.getTaskId()));
        // 后续推进同样不受影响
        engine.executeProcessTask(apply.getTaskId(), PRINCIPAL,
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));
        assertFalse("推进后应产生下一节点待办", repo.findDoingTasks(inst.getInstanceId(), null).isEmpty());
    }

    // ═══ 用例 26：显式关闭 → 回到「仅台账」 ═══

    @Test
    public void explicitDisabledFallsBackToLedgerOnly() throws Exception {
        boot(false, true);                                 // 关掉引擎内置开关
        ledger(PRINCIPAL, AGENT, FLOW, null, null, 1);
        ProcessInstance inst = engine.startProcessInstanceById(registerSimpleFlow().getId(),
                PRINCIPAL, FlowData.create());
        assertEquals("关闭后参与者只应有授权人", java.util.Collections.singletonList(PRINCIPAL),
                repo.findTaskActors(firstDoingTask(inst.getInstanceId()).getTaskId()));
        // 「仅台账」= 委托记录本身仍可查得到，只是不参与运行期
        assertEquals("台账仍应可查（关闭的是运行期应用，不是数据）", AGENT,
                extRepo.getSurrogate(PRINCIPAL, FLOW, LocalDateTime.now()).getSurrogate());
    }

    // ═══ 挂点覆盖：办理推进产生的新单同样应用委托 ═══

    @Test
    public void surrogateAlsoAppliedOnTaskCreatedByAdvance() throws Exception {
        boot(true, true);
        ledger("leader", "lisi2", FLOW, null, null, 1);    // 下一节点 assignee=leader 的委托
        ProcessInstance inst = engine.startProcessInstanceById(registerSimpleFlow().getId(),
                PRINCIPAL, FlowData.create());
        ProcessTask apply = firstDoingTask(inst.getInstanceId());
        engine.executeProcessTask(apply.getTaskId(), PRINCIPAL,
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));

        ProcessTask task1 = firstDoingTask(inst.getInstanceId());
        assertEquals("task1", task1.getTaskName());
        List<String> persisted = repo.findTaskActors(task1.getTaskId());
        assertTrue("推进出的新单也应并入代理人（实际=" + persisted + "）", persisted.contains("lisi2"));
        assertTrue(persisted.contains("leader"));
    }

    // ═══ 挂点覆盖：串行会签推进出的下一位成员任务（不经 CreateTaskHandler）同样应用委托 ═══
    //
    // ⚠️ 建任务路径**逐条独立**用例（契约 1「覆盖全部建任务路径」）：发起 / 办理推进 /
    // 串行会签的每一步推进 / 跳转(JUMP) / 回退(ROLLBACK) 各一条，各自断言**只由该路径产出**
    // 的那个任务的参与者——不共用一条"推进后新单有代理人"。
    // 判据：把任意一条路径的委托应用改坏（例如让该路径绕过 saveNewTask 直接 saveTask），
    // 应当**恰好只有它那条红**，其余路径的用例仍绿。为此每条用例都选一个只有该路径才会成为
    // 参与者的账号（userB 只由会签第二步产生、manager 只由 JUMP 产生、回退后的上一节点参与者
    // 只由 ROLLBACK 产生），且各自用专属代理人 id（lisiB / lisiJump / lisiRollback）。

    @Test
    public void surrogateAppliedOnSequentialCountersignNextTask() throws Exception {
        boot(true, true);
        ledger("userB", "lisiB", "countersign-sequential", null, null, 1);
        ProcessInstance inst = engine.startProcessInstanceById(registerFlow("06-countersign-sequential.json").getId(),
                PRINCIPAL, FlowData.create());
        engine.executeProcessTask(firstDoingTask(inst.getInstanceId()).getTaskId(), PRINCIPAL,
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));

        // 第一位成员 userA 无委托 → 参与者就只有他自己
        ProcessTask first = doingTask(inst.getInstanceId(), "task1");
        assertEquals("userA 无委托时不得追加任何人", java.util.Collections.singletonList("userA"),
                repo.findTaskActors(first.getTaskId()));

        // 推进到第二位 userB（由 CountersignHandler 直接建单，不走 CreateTaskHandler）→ 委托仍须生效
        engine.executeProcessTask(first.getTaskId(), "userA",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));
        ProcessTask second = doingTask(inst.getInstanceId(), "task1");
        List<String> persisted = repo.findTaskActors(second.getTaskId());
        assertTrue("串行会签推进出的下一位任务也应并入代理人（实际=" + persisted + "）",
                persisted.contains("lisiB"));
        assertTrue(persisted.contains("userB"));
    }

    // ═══ 建任务路径·跳转 JUMP（facade submitType=4 → executeAndJumpTask(nodeName)）═══

    @Test
    public void surrogateAppliedOnJumpPath() throws Exception {
        boot(true, true);
        // manager 只在"跳到 task2"这一步成为参与者：发起/推进/会签/回退都产不出它，
        // 故这条只会被 JUMP 路径的失能判红
        ledger("manager", "lisiJump", "with-reject", null, null, 1);
        ProcessInstance inst = engine.startProcessInstanceById(
                registerFlow("09-with-reject.json").getId(), PRINCIPAL, FlowData.create());
        engine.executeProcessTask(firstDoingTask(inst.getInstanceId()).getTaskId(), PRINCIPAL,
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));
        ProcessTask task1 = doingTask(inst.getInstanceId(), "task1");

        engine.executeAndJumpTask(task1.getTaskId(), "leader",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.JUMP.getCode()),
                "task2");

        ProcessTask jumped = doingTask(inst.getInstanceId(), "task2");
        List<String> persisted = repo.findTaskActors(jumped.getTaskId());
        assertTrue("JUMP 出的新单也应并入代理人（实际=" + persisted + "）", persisted.contains("lisiJump"));
        assertTrue("授权人保留、任一可办（实际=" + persisted + "）", persisted.contains("manager"));
    }

    // ═══ 建任务路径·回退 ROLLBACK（facade submitType=3 → executeAndJumpTask(null) → rejectTask）═══

    @Test
    public void surrogateAppliedOnRollbackPath() throws Exception {
        boot(true, true);
        // 回退新建的"上一节点"任务参与者 = 执行回退的那个人（ProcessInstance#rejectTask 取
        // currentTask.actorId），只有这一步会产出 apply 节点的 DOING 单
        ledger("leader", "lisiRollback", "with-reject", null, null, 1);
        ProcessInstance inst = engine.startProcessInstanceById(
                registerFlow("09-with-reject.json").getId(), PRINCIPAL, FlowData.create());
        engine.executeProcessTask(firstDoingTask(inst.getInstanceId()).getTaskId(), PRINCIPAL,
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));
        ProcessTask task1 = doingTask(inst.getInstanceId(), "task1");

        engine.executeAndJumpTask(task1.getTaskId(), "leader",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.ROLLBACK.getCode()), null);

        ProcessTask rolled = doingTask(inst.getInstanceId(), "apply");
        List<String> persisted = repo.findTaskActors(rolled.getTaskId());
        assertTrue("回退(ROLLBACK)出的新单也应并入代理人（实际=" + persisted + "）",
                persisted.contains("lisiRollback"));
        assertTrue("授权人保留、任一可办（实际=" + persisted + "）", persisted.contains("leader"));
    }

    // ═══ 契约 1.1：processName 取值口径 —— 模型 name 优先，模型未带才回落 define.name ═══

    @Test
    public void surrogateProcessNamePrefersModelNameOverDefineName() throws Exception {
        boot(true, true);
        // 诱饵：定义行 name 与模型 name 不一致（01-simple.json 的模型 name = "simple"）。
        // 两条委托各绑一头，只有取对那一头的代理人会进参与者。
        String defineName = "stale-define-name";
        ledger(PRINCIPAL, AGENT, FLOW, null, null, 1);              // 绑模型 name（应命中）
        ledger(PRINCIPAL, "agentDefineName", defineName, null, null, 1); // 绑定义行 name（不应命中）
        ProcessInstance inst = engine.startProcessInstanceById(
                registerFlowAs("01-simple.json", defineName).getId(), PRINCIPAL, FlowData.create());

        List<String> persisted = repo.findTaskActors(firstDoingTask(inst.getInstanceId()).getTaskId());
        assertTrue("迁移基线=模型 name（内置版 SurrogateInterceptor 取 processModel.getName()），"
                + "必须命中绑模型 name 的委托（实际=" + persisted + "）", persisted.contains(AGENT));
        assertFalse("不得改取 wf_process_define.name（实际=" + persisted + "）",
                persisted.contains("agentDefineName"));
    }

    @Test
    public void surrogateProcessNameFallsBackToDefineNameWhenModelNameMissing() throws Exception {
        boot(true, true);
        String defineName = "define-only-name";
        // 诱饵①：绑 define.name 的委托——回落生效才会命中
        ledger(PRINCIPAL, AGENT, defineName, null, null, 1);
        // 诱饵②：全流程兜底委托（processName=''）——没实现回落时 processName 为 null，
        // 只会命中这一条，故它出现在参与者里即证明"回落没走通"
        ledger(PRINCIPAL, "agentFallback", "", null, null, 1);

        byte[] json = Files.readAllBytes(Paths.get("src/test/resources/flows/01-simple.json"));
        // 模型未带 name 的两种形态：键缺失 / 空串（契约 1.1 都算"未带"）
        for (byte[] content : new byte[][]{withModelName(json, null), withModelName(json, "")}) {
            ProcessInstance inst = engine.startProcessInstanceById(
                    registerRaw(content, defineName).getId(), PRINCIPAL, FlowData.create());
            List<String> persisted = repo.findTaskActors(firstDoingTask(inst.getInstanceId()).getTaskId());
            assertTrue("模型未带 name 时应回落 wf_process_define.name 命中该流程的委托（实际=" + persisted + "）",
                    persisted.contains(AGENT));
            assertFalse("不得只命中全流程兜底（实际=" + persisted + "）", persisted.contains("agentFallback"));
        }
    }

    // ═══ 契约 5 写侧（跨栈分叉点）：enabled 空串落 0、缺键落 1；读写同一套语义 ═══

    @Test
    public void facadeSaveWithEmptyEnabledStringPersistsZeroAndNotApplied() throws Exception {
        boot(true, true);
        Map<String, Object> saved = saveSurrogateViaFacade(PRINCIPAL, AGENT, FLOW, "");
        assertEquals("空串不得抛错变 500: " + saved, Integer.valueOf(0), saved.get("code"));
        Long id = savedId(saved);
        assertEquals("enabled=\"\" 必须落 0（不可解析脏值按停用，不得当启用）",
                Integer.valueOf(0), extRepo.findSurrogateById(id).getEnabled());
        assertEquals("门面 detail 读回也必须是 0", Integer.valueOf(0), detailEnabled(id));
        assertAgentNotApplied("写侧落 0 的行读侧必须查不到生效委托");
    }

    @Test
    public void facadeSaveWithoutEnabledKeyDefaultsToOneAndApplied() throws Exception {
        boot(true, true);
        Map<String, Object> saved = saveSurrogateViaFacade(PRINCIPAL, AGENT, FLOW, OMIT_ENABLED_KEY);
        assertEquals("缺 enabled 键不得报错: " + saved, Integer.valueOf(0), saved.get("code"));
        Long id = savedId(saved);
        assertEquals("缺键按默认 1（save 表的\"默认 1\"）",
                Integer.valueOf(1), extRepo.findSurrogateById(id).getEnabled());
        assertEquals("门面 detail 读回也必须是 1", Integer.valueOf(1), detailEnabled(id));
        assertAgentApplied("缺键落 1 的委托必须生效");
    }

    // ═══ 门面桥：集成方只把扩展仓储交给 JeeflowFacade 构造器，引擎侧也应生效 ═══

    @Test
    public void facadeConstructorPublishesExtRepositoryToEngine() throws Exception {
        boot(true, false);                                 // 不手动注册 "ext"
        assertNull(ServiceContext.find(IProcessExtRepository.class));
        new JeeflowFacade(engine, repo, extRepo);          // ← boot 系薄壳/demo 的真实装配姿势
        assertNotNull("门面构造应把扩展仓储暴露给引擎", ServiceContext.find(IProcessExtRepository.class));

        ledger(PRINCIPAL, AGENT, FLOW, null, null, 1);
        ProcessInstance inst = engine.startProcessInstanceById(registerSimpleFlow().getId(),
                PRINCIPAL, FlowData.create());
        assertTrue("门面桥接后委托应生效",
                repo.findTaskActors(firstDoingTask(inst.getInstanceId()).getTaskId()).contains(AGENT));
    }

    // ═══ 用例 27（内存仓一路）：四条判据 —— 与 SQL 仓同答案 ═══

    @Test
    public void fourQueryCriteriaOnMemoryRepository() {
        boot(true, true);
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
        ledger("opA2", "agentEmpty", "", null, null, 1);   // process_name = '' 同义于 NULL
        assertEquals("process_name='' 也算全流程委托", "agentEmpty",
                extRepo.getSurrogate("opA2", FLOW, now).getSurrogate());

        // b. 时间窗，任一侧 NULL = 该侧不限
        ledger("opB1", "sB1", FLOW, now.minusDays(1), now.plusDays(1), 1);
        ledger("opB2", "sB2", FLOW, now.plusDays(1), null, 1);            // 未到窗
        ledger("opB3", "sB3", FLOW, null, now.minusDays(1), 1);           // 已过窗
        ledger("opB4", "sB4", FLOW, now.minusDays(1), null, 1);           // 只写下界且在窗内
        ledger("opB5", "sB5", FLOW, null, now.plusDays(1), 1);            // 只写上界且在窗内
        assertEquals("在窗", "sB1", extRepo.getSurrogate("opB1", FLOW, now).getSurrogate());
        assertNull("未到窗不生效", extRepo.getSurrogate("opB2", FLOW, now));
        assertNull("已过窗不生效", extRepo.getSurrogate("opB3", FLOW, now));
        assertEquals("start=NULL 视为下界不限", "sB4", extRepo.getSurrogate("opB4", FLOW, now).getSurrogate());
        assertEquals("end=NULL 视为上界不限", "sB5", extRepo.getSurrogate("opB5", FLOW, now).getSurrogate());

        // c. 自委托过滤
        ledger("opC", "opC", FLOW, null, null, 1);
        assertNull("自己委托给自己不生效", extRepo.getSurrogate("opC", FLOW, now));

        // d. enabled 只认 1（脏值不得当启用）
        ledger("opD0", "sD0", FLOW, null, null, 0);
        ledger("opD2", "sD2", FLOW, null, null, 2);
        ledger("opD9", "sD9", FLOW, null, null, -1);
        assertNull("enabled=0 不生效", extRepo.getSurrogate("opD0", FLOW, now));
        assertNull("enabled=2 不生效", extRepo.getSurrogate("opD2", FLOW, now));
        assertNull("enabled=-1 不生效", extRepo.getSurrogate("opD9", FLOW, now));

        // 多条命中取哪条（契约 1.4：按主键 id 最大，与 SQL 侧 ORDER BY id DESC LIMIT 1 同答案）。
        // ⚠️ 夹具故意**打乱 id 插入序**：先插 id 大的、后插 id 小的。
        // 若按自然序插入（id 序 == 插入序），"取遍历到的末条"/"最后写入者胜"与"取 id 最大"
        // 会给出同一个答案，用例钉不住 1.4——Node 栈就是这么发现假绿的，故此处不留自然序。
        // 探针实测：把内存仓实现改成"最后写入的那条胜"，自然序夹具全绿（假绿复现），
        // 本打乱序夹具必红 expected:<sE-new> but was:<sE-old>。
        ledgerWithId(900002L, "opE", "sE-new", FLOW, null, null, 1);   // 先插：id 最大 = 应命中
        ledgerWithId(900001L, "opE", "sE-old", FLOW, null, null, 1);   // 后插：id 更小 = 不应命中
        assertEquals("双仓一致：多条命中取 id 最大（不是插入序末条）", "sE-new",
                extRepo.getSurrogate("opE", FLOW, now).getSurrogate());
    }

    // ═══ 幂等：同一任务重复应用不得重复加人 ═══

    @Test
    public void applyIsIdempotent() throws Exception {
        boot(true, true);
        ledger(PRINCIPAL, AGENT, FLOW, null, null, 1);
        ProcessInstance inst = engine.startProcessInstanceById(registerSimpleFlow().getId(),
                PRINCIPAL, FlowData.create());
        ProcessTask apply = firstDoingTask(inst.getInstanceId());
        com.mldong.jeeflow.interceptor.impl.SurrogateInterceptor itc =
                new com.mldong.jeeflow.interceptor.impl.SurrogateInterceptor(extRepo);
        itc.apply(apply, FLOW, LocalDateTime.now());
        itc.apply(apply, FLOW, LocalDateTime.now());
        repo.saveTask(apply);
        assertEquals("重复应用不得重复追加", 2, repo.findTaskActors(apply.getTaskId()).size());
    }

    // ═══ 空快照保护：不进 ServiceContext 也不抛（独立实例化） ═══

    @Test
    public void interceptorWithoutExtRepositoryIsNoOp() {
        boot(true, false);                                 // 有上下文，但未注册扩展仓储
        ProcessTask task = new ProcessTask();
        task.setTaskState(com.mldong.jeeflow.enums.ProcessTaskStateEnum.DOING.getCode());
        task.setActorIds(new java.util.ArrayList<>(java.util.Collections.singletonList(PRINCIPAL)));
        new com.mldong.jeeflow.interceptor.impl.SurrogateInterceptor()
                .apply(task, FLOW, LocalDateTime.now());   // 仓储未配置：静默返回
        assertEquals(java.util.Collections.singletonList(PRINCIPAL), task.getActorIds());
    }
}
