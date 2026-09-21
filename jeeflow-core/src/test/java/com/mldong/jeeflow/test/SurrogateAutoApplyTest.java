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
import java.util.List;

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
        byte[] bytes = Files.readAllBytes(Paths.get("src/test/resources/flows/" + filename));
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName(FLOW);
        def.setDisplayName("简单审批流程");
        def.setType("approval");
        def.setState(1);
        def.setVersion(1);
        def.setContent(bytes);
        repo.addDefine(def);
        return def;
    }

    /** 台账里写一条委托（走 SPI，与门面 processSurrogate/save 同一存储路径） */
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

        // 多条命中取最新（与 SQL 侧 ORDER BY id DESC LIMIT 1 同答案）
        ledger("opE", "sE-old", FLOW, null, null, 1);
        ledger("opE", "sE-new", FLOW, null, null, 1);
        assertEquals("双仓一致：多条命中取 id 最大", "sE-new",
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
