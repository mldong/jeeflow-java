package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.JeeflowException;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessInstanceStateEnum;
import com.mldong.jeeflow.enums.ProcessTaskStateEnum;
import com.mldong.jeeflow.facade.JeeflowFacade;
import com.mldong.jeeflow.model.TaskModel;
import com.mldong.jeeflow.spi.IProcessRepository;
import com.mldong.jeeflow.spi.IOrgUserProvider;
import com.mldong.jeeflow.spi.IUserProvider;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * issues/134 案 A · 撤回的**实例状态守卫**（owner 2026-09-28 拍板）。
 *
 * <p>缺陷：issues/113 只落了**任务行**层面的保护（已完成 20 / 已终止 40 的行不被撤回改写），
 * **实例**层面谁都没判状态——对已办结(20)/已终止(40) 的实例调撤回，会把实例静默改写成 30（已撤回），
 * 已办列表与按状态聚合的统计就此凭空改历史，而调用方看不到任何报错。</p>
 *
 * <p>判据（八栈逐字统一）：聚合根 {@link ProcessInstance#withdraw(String)} 时实例 state != 10(进行中)
 * ⇒ 抛内部码 <b>20010009</b>，文案固定 {@value #EXPECTED_MSG}，**不改写任何行、不落库**；
 * 守卫排在任务行循环之前。门面沿用 issues/121 口径把内部码吞掉 ⇒ 出口 {@code code=99999999}
 * ＋ msg 就是那句原文（**文案里不带码值**）。</p>
 *
 * <p>跨栈门禁格 L2-28 只钉得住 20 档与正向 10 档（壳侧造不出 state=40 的行，issues/134 §5.2 已注明），
 * 40 / 30 两档在本栈栈内单测钉住。</p>
 */
public class WithdrawInstanceStateGuardTest {

    /** 出口文案逐字固定（八栈一致）；门禁按逐字断言，不许用"包含 撤回"这种宽松判据 */
    private static final String EXPECTED_MSG = "流程实例非进行中，无法撤回";
    /** 内部错误码：20010001–20010008 已占，本案取 20010009（八栈 git grep 零命中） */
    private static final int EXPECTED_INTERNAL_CODE = 20010009;
    /** 负向档的撤回人：与夹具里的原始 update_user 不同，才照得出"一行都不改" */
    private static final String LATE_WITHDRAWER = "lateWithdrawer";

    private JeeflowFacade facade;
    private MemoryProcessRepository repo;
    private IProcessRepository rawRepo;

    // ═══ 门面级夹具（与 JeeflowFacadeTest 同形，走 flow() 真实派发）═══

    @Before
    public void setUp() {
        Configuration config = new Configuration();
        repo = new MemoryProcessRepository();

        ServiceContext.put("repository", repo);
        ServiceContext.put("json", new TestJsonProvider());
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
            @Override public List<String> findByRole(String roleCode) { return null; }
        });

        JeeflowEngine engine = new JeeflowEngineImpl();
        engine.configure(config);
        rawRepo = repo;
        facade = new JeeflowFacade(engine, repo, new MemoryProcessExtRepository());
    }

    private ProcessInstance.ProcessDefine registerFlow(String filename) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get("src/test/resources/flows/" + filename));
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
        return facade.flow(action, a);
    }

    private static Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).longValue();
        return Long.parseLong(val.toString());
    }

    /** 起一条实例并返回其 id；data 里的 processInstanceId */
    private Long startInstance(String flowFile, String initiator) throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow(flowFile);
        Map<String, Object> started = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", initiator));
        assertEquals("发起应成功: " + started, Integer.valueOf(0), started.get("code"));
        return toLong(((Map<String, Object>) started.get("data")).get("processInstanceId"));
    }

    // ═══ 聚合根级：负向 20 / 40 / 30 三档 ＋ 正向 10 ═══

    /** 一个进行中(10) 的实例：一行进行中任务 task1，参与者 leader，发起人 zhangsan */
    private ProcessInstance doingInstance() {
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setId(9527L);
        def.setName("134-guard");
        ProcessInstance inst = ProcessInstance.create(def, "zhangsan", FlowData.create());
        inst.setInstanceId(9001L);
        TaskModel model = new TaskModel();
        model.setName("task1");
        model.setDisplayName("审批");
        inst.createTask(model, "审批", new ArrayList<>(Collections.singletonList("leader")),
                "zhangsan", 0L, false);
        return inst;
    }

    /** 把实例**自然**办到 state=20（行真办结 + 聚合根 finish），不用 setState 造假形状 */
    private ProcessInstance finishedInstance() {
        ProcessInstance inst = doingInstance();
        inst.getTasks().get(0).finish("leader", null);
        inst.finish();
        assertEquals("夹具前提：实例已办结",
                ProcessInstanceStateEnum.FINISHED.getCode(), inst.getState());
        return inst;
    }

    /** 断负向抛出：异常类型 ＋ 内部码 20010009 ＋ 文案逐字相等 ＋ 实例/任务行/撤回人一行未改 */
    private void assertRejectedWithoutTouchingRows(ProcessInstance inst, Integer originalState) {
        String updateUserBefore = inst.getUpdateUser();
        List<Integer> rowStatesBefore = new ArrayList<>();
        for (ProcessTask t : inst.getTasks()) rowStatesBefore.add(t.getTaskState());

        try {
            inst.withdraw(LATE_WITHDRAWER);
            fail("非进行中实例撤回必须抛 JeeflowException，不得静默改写为 30");
        } catch (JeeflowException e) {
            assertEquals("内部码应为 20010009", EXPECTED_INTERNAL_CODE, e.getCode());
            assertEquals("文案逐字固定（不带码值、不带前缀）", EXPECTED_MSG, e.getMessage());
        }
        assertEquals("被拒后实例状态必须仍是原值 " + originalState, originalState, inst.getState());
        assertNotEquals("实例状态严禁被改写成 30(已撤回)",
                ProcessInstanceStateEnum.WITHDRAW.getCode(), inst.getState());
        assertEquals("撤回人不得被写进 update_user（守卫排在循环之前，一行都不改）",
                updateUserBefore, inst.getUpdateUser());
        int i = 0;
        for (ProcessTask t : inst.getTasks()) {
            assertEquals("任务行不得被改写：" + t.getTaskName(),
                    rowStatesBefore.get(i++), t.getTaskState());
        }
    }

    /** 负向①：已完成(20) 的实例调撤回 ⇒ 20010009 ＋ 固定文案，实例仍 20、行仍 20 */
    @Test
    public void withdrawOnFinishedInstanceIsRejectedAndKeepsState() {
        ProcessInstance inst = finishedInstance();
        assertEquals(ProcessTaskStateEnum.FINISHED.getCode(), inst.getTasks().get(0).getTaskState());
        assertRejectedWithoutTouchingRows(inst, ProcessInstanceStateEnum.FINISHED.getCode());
    }

    /** 负向②：强行终止(40) 的实例调撤回 ⇒ 同样拒绝，实例仍 40、行仍 40 */
    @Test
    public void withdrawOnInterruptedInstanceIsRejectedAndKeepsState() {
        ProcessInstance inst = doingInstance();
        inst.interrupt("boss");
        assertEquals("夹具前提：实例已终止",
                ProcessInstanceStateEnum.INTERRUPT.getCode(), inst.getState());
        assertRejectedWithoutTouchingRows(inst, ProcessInstanceStateEnum.INTERRUPT.getCode());
    }

    /** 负向③：已撤回(30) 的实例二次撤回同样被拒——重复撤不得把 update_user 改成第二次操作人 */
    @Test
    public void withdrawOnAlreadyWithdrawnInstanceIsRejectedOnSecondCall() {
        ProcessInstance inst = doingInstance();
        inst.withdraw("zhangsan");   // 首次：进行中，照旧成功
        assertEquals(ProcessInstanceStateEnum.WITHDRAW.getCode(), inst.getState());
        assertEquals("zhangsan", inst.getUpdateUser());

        try {
            inst.withdraw(LATE_WITHDRAWER);
            fail("已撤回(30) 的实例二次撤回必须被拒");
        } catch (JeeflowException e) {
            assertEquals(EXPECTED_INTERNAL_CODE, e.getCode());
            assertEquals(EXPECTED_MSG, e.getMessage());
        }
        assertEquals(ProcessInstanceStateEnum.WITHDRAW.getCode(), inst.getState());
        assertEquals("被拒的那次不得把撤回人改成第二次操作人", "zhangsan", inst.getUpdateUser());
    }

    /** 正向对照（聚合根级）：进行中(10) 的实例撤回照旧成功，实例与进行中任务都落 30 */
    @Test
    public void withdrawOnDoingInstanceStillSucceedsAndLandsState30() {
        ProcessInstance inst = doingInstance();
        inst.withdraw("zhangsan");
        assertEquals("进行中实例撤回应落 30(WITHDRAW)",
                ProcessInstanceStateEnum.WITHDRAW.getCode(), inst.getState());
        assertEquals(ProcessTaskStateEnum.WITHDRAW.getCode(), inst.getTasks().get(0).getTaskState());
        assertEquals("zhangsan", inst.getUpdateUser());
    }

    // ═══ 门面级：出口只发明 99999999，msg ＝ 那句原文（不带码值），且不落库 ═══

    /**
     * 负向①（对应跨栈门禁格 L2-28）：真把实例办到 state=20 再调撤回
     * ⇒ 出口 {@code code=99999999} ＋ msg 逐字 ＝ 固定文案；**再读一次实例** state 仍是 20。
     *
     * <p>撤回人用 {@code flow.admin} 哨兵（归属判据③放行），确保报错来自本案的状态守卫，
     * 而不是被鉴权分支的「无权限撤回该流程实例」抢先命中。</p>
     */
    @Test
    public void facadeWithdrawOnFinishedInstanceReturnsVerbatimMsgAndDoesNotPersist() throws Exception {
        Long instanceId = startInstance("01-simple.json", "zhangsan");

        // 办结：leader 同意（submitType=1）→ 实例 state=20
        List<ProcessTask> doing = rawRepo.findDoingTasks(instanceId, new String[]{});
        Map<String, Object> executed = call("processTask/execute",
                args("processTaskId", doing.get(0).getTaskId(), "operator", "leader", "submitType", 1));
        assertEquals("办结应成功: " + executed, Integer.valueOf(0), executed.get("code"));
        ProcessInstance finished = repo.findInstanceById(instanceId);
        assertEquals("夹具前提：实例已办结 state=20",
                ProcessInstanceStateEnum.FINISHED.getCode(), finished.getState());
        String updateUserBefore = finished.getUpdateUser();

        Map<String, Object> resp = call("processInstance/withdraw",
                args("id", instanceId, "operator", FlowConst.ADMIN_ID));
        assertEquals("非进行中实例撤回必须报错（禁止静默成功）: " + resp,
                Integer.valueOf(99999999), resp.get("code"));
        assertEquals("出口 msg 逐字固定，内部码 20010009 不进 msg: " + resp,
                EXPECTED_MSG, resp.get("msg"));

        ProcessInstance reread = repo.findInstanceById(instanceId);
        assertEquals("被拒后**再读一次**实例 state 必须仍是 20（本案病灶）",
                ProcessInstanceStateEnum.FINISHED.getCode(), reread.getState());
        assertEquals("被拒的那次不得落库改写 update_user", updateUserBefore, reread.getUpdateUser());
        for (ProcessTask t : rawRepo.findHistoryTasks(instanceId)) {
            assertEquals("任务行不得被改写：" + t.getTaskName(),
                    ProcessTaskStateEnum.FINISHED.getCode(), t.getTaskState());
        }
    }

    /**
     * 负向②：state=40（强行终止）档。门面没有"终止实例"的 action，壳侧同样造不出这一档
     * （issues/134 §5.2 因此把 L2-28 限定在 20 ＋ 正向 10），故本栈用聚合根自己的 {@code interrupt}
     * 命令把存储里的实例自然推到 40，再走门面撤回。
     */
    @Test
    public void facadeWithdrawOnInterruptedInstanceIsRejected() throws Exception {
        Long instanceId = startInstance("01-simple.json", "zhangsan");
        ProcessInstance inst = repo.findInstanceById(instanceId);
        inst.interrupt("boss");
        repo.updateInstance(inst);
        assertEquals("夹具前提：实例已终止 state=40",
                ProcessInstanceStateEnum.INTERRUPT.getCode(), repo.findInstanceById(instanceId).getState());

        Map<String, Object> resp = call("processInstance/withdraw",
                args("id", instanceId, "operator", FlowConst.ADMIN_ID));
        assertEquals("已终止实例撤回必须报错: " + resp, Integer.valueOf(99999999), resp.get("code"));
        assertEquals(EXPECTED_MSG, resp.get("msg"));
        assertEquals("被拒后实例仍 40", ProcessInstanceStateEnum.INTERRUPT.getCode(),
                repo.findInstanceById(instanceId).getState());
        assertEquals("被拒后不落库：update_user 仍是终止人", "boss",
                repo.findInstanceById(instanceId).getUpdateUser());
        for (ProcessTask t : rawRepo.findHistoryTasks(instanceId)) {
            assertNotEquals("任务行不得被改成 30：" + t.getTaskName(),
                    ProcessTaskStateEnum.WITHDRAW.getCode(), t.getTaskState());
        }
    }

    /** 正向对照（门面级）：进行中(10) 的实例撤回照旧 code=0，实例落 30、进行中任务落 30 */
    @Test
    public void facadeWithdrawOnDoingInstanceStillSucceeds() throws Exception {
        Long instanceId = startInstance("02-multi-task.json", "zhangsan");
        assertFalse("夹具前提：应有进行中任务",
                rawRepo.findDoingTasks(instanceId, new String[]{}).isEmpty());

        Map<String, Object> resp = call("processInstance/withdraw",
                args("id", instanceId, "operator", "zhangsan"));
        assertEquals("进行中实例撤回照旧成功（守卫没写反）: " + resp,
                Integer.valueOf(0), resp.get("code"));
        ProcessInstance after = repo.findInstanceById(instanceId);
        assertEquals(ProcessInstanceStateEnum.WITHDRAW.getCode(), after.getState());
        assertEquals("zhangsan", after.getUpdateUser());
        assertEquals("整单撤回后不应残留进行中任务", 0,
                rawRepo.findDoingTasks(instanceId, new String[]{}).size());
        int withdrawnRows = 0;
        for (ProcessTask t : rawRepo.findHistoryTasks(instanceId)) {
            if ("apply".equals(t.getTaskName())) {
                assertEquals("已完成(20) 行仍不得被撤回改写（issues/113 的既有保护保持原样）",
                        ProcessTaskStateEnum.FINISHED.getCode(), t.getTaskState());
            } else {
                assertEquals(ProcessTaskStateEnum.WITHDRAW.getCode(), t.getTaskState());
                withdrawnRows++;
            }
        }
        assertTrue("应至少撤掉一条进行中任务行", withdrawnRows >= 1);
    }

    /** 回归：既有失败文案不被本案污染（缺 operator／越权的报错顺序仍排在状态守卫之前） */
    @Test
    public void existingWithdrawFailureMessagesUnchanged() throws Exception {
        Long instanceId = startInstance("01-simple.json", "zhangsan");

        Map<String, Object> noOperator = call("processInstance/withdraw", args("id", instanceId));
        assertEquals(Integer.valueOf(99999999), noOperator.get("code"));
        assertTrue("msg 应仍是跨栈统一文案: " + noOperator,
                String.valueOf(noOperator.get("msg")).contains("operator 必填"));

        Map<String, Object> stranger = call("processInstance/withdraw",
                args("id", instanceId, "operator", "nobody"));
        assertEquals(Integer.valueOf(99999999), stranger.get("code"));
        assertTrue("msg 应仍是鉴权文案（守卫在聚合根内，鉴权分支顺序未动）: " + stranger,
                String.valueOf(stranger.get("msg")).contains("无权限撤回该流程实例"));

        assertEquals("两条负向都不该改状态（实例仍 10，其后正向撤回仍可用）",
                ProcessInstanceStateEnum.DOING.getCode(), repo.findInstanceById(instanceId).getState());
        assertEquals("正向撤回仍可用: ", Integer.valueOf(0),
                call("processInstance/withdraw", args("id", instanceId, "operator", "zhangsan")).get("code"));
    }
}
