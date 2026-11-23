package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.Context;
import com.mldong.jeeflow.context.SimpleContext;
import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
import com.mldong.jeeflow.interceptor.impl.FormFieldAssigneeHandler;
import com.mldong.jeeflow.interceptor.impl.FormUserDeptLeaderAssignmentHandler;
import com.mldong.jeeflow.interceptor.impl.FormUserDeptMainLeaderAssignmentHandler;
import com.mldong.jeeflow.model.TaskModel;
import com.mldong.jeeflow.spi.IOrgUserProvider;
import com.mldong.jeeflow.spi.IUserProvider;
import com.mldong.jeeflow.spi.IUserProvider.UserInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * issues/199 「表单指定用户所属部门（分管）领导」——八语言契约 §3.2 四档并集／§3.3 取人链／§3.4 失败语义。
 *
 * <p>场景＝HR 代员工提交／代理申请：表单里给出被代理人的用户 id，取<b>该员工</b>部门的经理／分管领导，
 * 与当前操作人无关。链路＝字段 → user → dept → leader。</p>
 *
 * <p>夹具形状：用户/部门/领导三张表都是本类内联的合成数据（{@link #DEPT_OF_USER} 等），
 * 流程 JSON 也内联——不往共享 {@code src/test/resources/flows/} 加文件：那个目录的份数是跨栈对账的读数，
 * 且 demo 播种按"文件名排序 → id=i+1"，中间插一格会把八栈 {@code seed_business} 的硬编码 defineId 整体后移。</p>
 *
 * <p>上下文隔离：{@code ServiceContext} 是静态的，兄弟测试类会往里塞自己的 user/org，
 * 于是每个用例都换一枚干净的 {@code SimpleContext}（{@code new Configuration(ctx)} 即绑），
 * {@code @After} 还原 ⇒ "SPI 未注册"这类负向档才不会被串味成假绿。</p>
 */
public class FormUserAssignmentHandlerTest {

    private static final String LEADER_FQN =
            "com.mldong.jeeflow.interceptor.impl.FormUserDeptLeaderAssignmentHandler";
    private static final String MAIN_LEADER_FQN =
            "com.mldong.jeeflow.interceptor.impl.FormUserDeptMainLeaderAssignmentHandler";

    /** 每枚用户钉一个专属部门，部门经理名＝该部门的唯一读数 ⇒ 命中哪一档一目了然 */
    private static final Map<String, String> DEPT_OF_USER = new HashMap<>();
    private static final Map<String, List<String>> DEPT_LEADERS = new HashMap<>();
    private static final Map<String, List<String>> DEPT_MAIN_LEADERS = new HashMap<>();

    static {
        DEPT_OF_USER.put("u_f", "deptF");
        DEPT_OF_USER.put("u_b", "deptB");
        DEPT_OF_USER.put("u_h", "deptH");
        DEPT_OF_USER.put("u_m", "deptM");
        DEPT_OF_USER.put("emp1", "dept2");
        DEPT_OF_USER.put("nodept", "");
        DEPT_OF_USER.put("dept3user", "dept3");
        // 零号也是合法用户 id（真值判据会把它吃掉）
        DEPT_OF_USER.put("0", "dept2");
        // "hr"／"ghost" 之外：ghost 不在表里＝用户不存在
        DEPT_OF_USER.put("hr", "deptHR");

        DEPT_LEADERS.put("deptF", Arrays.asList("LF"));
        DEPT_LEADERS.put("deptB", Arrays.asList("LB"));
        DEPT_LEADERS.put("deptH", Arrays.asList("LH"));
        DEPT_LEADERS.put("deptM", Arrays.asList("LM"));
        DEPT_LEADERS.put("dept2", Arrays.asList("mgr2", "mgr2b"));
        // 操作人 hr 所在部门的经理／分管领导：值刻意与被代理人的不同，用来证"与操作人无关"
        DEPT_LEADERS.put("deptHR", Arrays.asList("leaderHR"));
        DEPT_MAIN_LEADERS.put("deptHR", Arrays.asList("mainHR"));
        // dept3 有部门无经理
        DEPT_MAIN_LEADERS.put("dept2", Arrays.asList("main2"));
    }

    private Context savedContext;
    private MemoryProcessRepository repo;
    private JeeflowEngine engine;

    @Before
    public void setUp() {
        savedContext = ServiceContext.getContext();
        freshStack(true, true);
    }

    @After
    public void tearDown() {
        if (savedContext != null) ServiceContext.setContext(savedContext);
    }

    /** 干净上下文；withUser／withOrg 任意为 false 即模拟对应 SPI 未注册 */
    private void freshStack(boolean withUser, boolean withOrg) {
        SimpleContext ctx = new SimpleContext();
        Configuration config = new Configuration(ctx);
        repo = new MemoryProcessRepository();
        ctx.put("repository", repo);
        ctx.put("json", new TestJsonProvider());
        ctx.put("expr", new TestExpressionEvaluator());
        if (withUser) {
            ctx.put("user", new IUserProvider() {
                @Override
                public UserInfo getUser(String userId) {
                    String deptId = DEPT_OF_USER.get(userId);
                    if (deptId == null) return null;
                    UserInfo u = new UserInfo();
                    u.setUserId(userId);
                    u.setRealName("用户" + userId);
                    u.setDeptId(deptId);
                    return u;
                }
            });
        }
        if (withOrg) {
            ctx.put("org", new IOrgUserProvider() {
                @Override
                public List<String> findDeptLeaders(String deptId) {
                    return DEPT_LEADERS.get(deptId);
                }

                @Override
                public List<String> findDeptMainLeaders(String deptId) {
                    return DEPT_MAIN_LEADERS.get(deptId);
                }

                @Override
                public List<String> findByRole(String roleCode) {
                    return null;
                }
            });
        }
        engine = new JeeflowEngineImpl();
        engine.configure(config);
    }

    /** 只带节点唯一编码＋变量表，不起引擎链 */
    private static Execution execOf(String nodeKey, FlowData args) {
        Execution execution = new Execution();
        execution.setOperator("hr");
        if (nodeKey != null) {
            TaskModel model = new TaskModel();
            model.setName(nodeKey);
            execution.setNodeModel(model);
        }
        execution.setArgs(args);
        return execution;
    }

    private static String leaders(String nodeKey, FlowData args) {
        return new FormUserDeptLeaderAssignmentHandler().assign(execOf(nodeKey, args));
    }

    private static String mainLeaders(String nodeKey, FlowData args) {
        return new FormUserDeptMainLeaderAssignmentHandler().assign(execOf(nodeKey, args));
    }

    // ═══════════════════════════════════════════
    // §3.2 四档并集（顺序即优先级）
    // ═══════════════════════════════════════════

    @Test
    public void t01_tier1_prefixedWinsOverBare() {
        FlowData args = FlowData.create().set("f_leader", "u_f").set("leader", "u_b");
        assertEquals("LF", leaders("leader", args));
    }

    @Test
    public void t02_tier2_bareFallbackAndMiss() {
        assertEquals("LB", leaders("leader", FlowData.create().set("leader", "u_b")));
        assertNull(leaders("leader", FlowData.create().set("other", "u_b")));
    }

    /** 参照实现只有裸名一档；jeeflow 家族变量存 f_，故 # 右半补 f_ 变体（本栈相对参照的唯一增量之一） */
    @Test
    public void t03_tier3_hashRightHalfBareThenPrefixed() {
        FlowData bareFirst = FlowData.create().set("userId", "u_h").set("f_userId", "u_f");
        assertEquals("LH", leaders("manager#userId", bareFirst));

        FlowData onlyPrefixed = FlowData.create().set("f_userId", "u_h");
        assertEquals("LH", leaders("manager#userId", onlyPrefixed));

        // 右半取**最后一个** # 之后的部分
        assertEquals("LH", leaders("a#b#userId", FlowData.create().set("userId", "u_h")));
    }

    @Test
    public void t04_tier4_numberSuffixBasePrefixedThenBare() {
        FlowData prefixed = FlowData.create().set("f_manager", "u_m").set("manager", "u_b");
        assertEquals("LM", leaders("manager_01", prefixed));
        assertEquals("LM", leaders("manager_01", FlowData.create().set("manager", "u_m")));
    }

    /** {@code ^(.+?)_(\d+)$} 的惰性量词要的是**最早**那个下划线；按第一个下划线切会读到另一档 */
    @Test
    public void t05_numberSuffixTierMatchesLazyRegex() {
        // a_b_01 的 base＝"a_b"；若切成 "a" 会读成 u_b⇒LB
        FlowData earliest = FlowData.create().set("a_b", "u_m").set("a", "u_b");
        assertEquals("LM", leaders("a_b_01", earliest));

        // "_01" 前缀为空 ⇒ 不落这一档，退化成 f__01/__01 两档 ⇒ 全缺
        assertNull(leaders("_01", FlowData.create().set("f_", "u_m")));

        // 尾部含非数字 ⇒ 不命中
        assertNull(leaders("manager_01x", FlowData.create().set("manager", "u_m")));
    }

    /** 键存在即命中（值为 null 也算命中，由 firstUserId 折成空串），不是"值非空才查下一档" */
    @Test
    public void t06_keyExistenceNotTruthiness() {
        FlowData args = new FlowData();
        args.put("f_leader", null);
        args.put("leader", "u_b");
        assertNull(leaders("leader", args));
    }

    // ═══════════════════════════════════════════
    // §3.3 取人链：只取第一个非空 id，多值语义不在本轮承诺
    // ═══════════════════════════════════════════

    @Test
    public void t07_firstIdFromCommaStringAndArray() {
        assertEquals("LF", leaders("k", FlowData.create().set("k", "u_f,u_b")));
        assertEquals("LF", leaders("k", FlowData.create().set("k", " , u_f ")));
        assertEquals("LF", leaders("k",
                FlowData.create().set("k", Arrays.asList("", "u_f", "u_b"))));
    }

    /** 数组元素不再二次拆逗号（与 Go/Node 同形）⇒ 整枚元素当一个 id，查不到人即空 */
    @Test
    public void t08_arrayElementIsNotResplit() {
        assertNull(leaders("k", FlowData.create().set("k", Arrays.asList("u_b,u_f"))));
    }

    /** 零号 id 合法（判空只比空串） */
    @Test
    public void t09_zeroIdIsLegal() {
        assertEquals("mgr2,mgr2b", leaders("leader", FlowData.create().set("f_leader", 0L)));
    }

    /** 多人经理保持配置顺序、逗号串出口（与现存部门类 handler 同形） */
    @Test
    public void t10_multipleLeadersKeepOrderAndCommaShape() {
        String out = leaders("leader", FlowData.create().set("f_leader", "emp1"));
        assertEquals("mgr2,mgr2b", out);
        assertEquals(2, out.split(",").length);
    }

    /** 部门经理／分管领导两档共用同一条链，只换最后一跳 */
    @Test
    public void t11_mainLeaderVariant() {
        FlowData args = FlowData.create().set("f_leader", "emp1");
        assertEquals("mgr2,mgr2b", leaders("leader", args));
        assertEquals("main2", mainLeaders("leader", args));
    }

    // ═══════════════════════════════════════════
    // §3.4 失败语义：一律空结果且不抛（取人失败不许炸流程）
    // ═══════════════════════════════════════════

    @Test
    public void t20_ghostUserIsEmpty() {
        assertNull(leaders("leader", FlowData.create().set("f_leader", "ghost")));
    }

    @Test
    public void t21_userWithoutDeptIsEmpty() {
        assertNull(leaders("leader", FlowData.create().set("f_leader", "nodept")));
    }

    @Test
    public void t22_deptWithoutLeaderIsEmpty() {
        assertNull(leaders("leader", FlowData.create().set("f_leader", "dept3user")));
        assertNull(mainLeaders("leader", FlowData.create().set("f_leader", "u_f")));
    }

    @Test
    public void t23_emptyAndNullValueAreEmpty() {
        assertNull(leaders("leader", FlowData.create().set("f_leader", "")));
        FlowData nul = new FlowData();
        nul.put("f_leader", null);
        assertNull(leaders("leader", nul));
    }

    @Test
    public void t24_missingSpiIsEmpty() {
        FlowData args = FlowData.create().set("f_leader", "emp1");
        freshStack(false, true);   // user SPI 未注册
        assertNull(leaders("leader", args));
        freshStack(true, false);   // org SPI 未注册
        assertNull(leaders("leader", args));
        assertNull(mainLeaders("leader", args));
    }

    /** 节点未设置（nodeModel 为空 / 唯一编码为空）⇒ 空且不撞变量表 */
    @Test
    public void t25_noNodeIsEmptyAndDoesNotTouchArgs() {
        FlowData args = FlowData.create().set("f_", "u_m");
        Execution execution = new Execution();
        execution.setArgs(args);
        assertNull(new FormUserDeptLeaderAssignmentHandler().assign(execution));
        assertNull(leaders("", args));
    }

    /** 对象/数组之外的形状（Map 值）没有 id 形状 ⇒ 空，不抛 ClassCastException */
    @Test
    public void t26_nonScalarValueIsEmpty() {
        Map<String, Object> obj = new HashMap<>();
        obj.put("k", "u_f");
        assertNull(leaders("leader", FlowData.create().set("f_leader", obj)));
    }

    // ═══════════════════════════════════════════
    // 注册名可解析（CreateTaskHandler 走 Class.forName + newInstance）
    // ═══════════════════════════════════════════

    @Test
    public void t30_registeredNamesAreTopLevelAndInstantiable() {
        for (String fqn : Arrays.asList(LEADER_FQN, MAIN_LEADER_FQN)) {
            assertFalse(fqn.contains("$"));
            try {
                Object instance = Class.forName(fqn).getDeclaredConstructor().newInstance();
                assertTrue(instance instanceof com.mldong.jeeflow.interceptor.AssignmentHandler);
            } catch (Exception e) {
                fail(fqn + " 反射不可实例化：" + e);
            }
        }
    }

    // ═══════════════════════════════════════════
    // 引擎级：HR 代员工提交 ⇒ 被代理人部门的经理收到待办（与操作人无关）
    // ═══════════════════════════════════════════

    @Test
    public void e01_hrProxyAssignsProxiedEmployeeDeptLeader() throws Exception {
        ProcessInstance inst = startHrProxy(LEADER_FQN);
        List<String> actors = allActors(inst);
        assertTrue("被代理人部门经理应收到待办：" + actors, actors.contains("mgr2"));
        assertTrue(actors.contains("mgr2b"));
        // 操作人 hr 所在 deptHR 的经理不得收到
        assertTrue("操作人所属部门不得参与取人：" + actors, !actors.contains("leaderHR"));
    }

    @Test
    public void e02_hrProxyMainLeaderVariant() throws Exception {
        List<String> actors = allActors(startHrProxy(MAIN_LEADER_FQN));
        assertTrue("分管领导应收到待办：" + actors, actors.contains("main2"));
        assertTrue("操作人所属部门的分管不得参与取人：" + actors, !actors.contains("mainHR"));
    }

    /** 负向：引擎链上取不到人 ⇒ 待办参与者为空，流程照走不报错 */
    @Test
    public void e03_hrProxyGhostUserYieldsEmptyActorsWithoutBreakingFlow() throws Exception {
        ProcessInstance.ProcessDefine def = addDefine(flowJson(LEADER_FQN));
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "hr",
                FlowData.create().set("f_approver", "ghost"));
        ProcessTask apply = firstDoing(inst);
        assertEquals("apply", apply.getTaskName());
        engine.executeProcessTask(apply.getTaskId(), "hr",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));

        List<ProcessTask> tasks = repo.findDoingTasks(inst.getInstanceId(), null);
        assertEquals(1, tasks.size());
        assertEquals("approver", tasks.get(0).getTaskName());
        assertTrue("取人失败应为空参与者，实际：" + tasks.get(0).getActorIds(),
                tasks.get(0).getActorIds().isEmpty());
    }

    // ═══════════════════════════════════════════
    // 回归钉子：现存 handler 的查找行为不许跟着扩面
    // ═══════════════════════════════════════════

    @Test
    public void rf01_existingFormFieldHandlerUnchanged() {
        FormFieldAssigneeHandler old = new FormFieldAssigneeHandler();

        // 旧支返回字段值全量（两枚都算参与者），且 f_ 优先裸名
        FlowData prefixed = FlowData.create().set("f_node1", "user2,user3").set("node1", "user9");
        assertEquals("user2,user3", old.assign(execOf("node1", prefixed)));
        assertEquals("user3", old.assign(execOf("node1", FlowData.create().set("node1", "user3"))));

        // #／_NN 两档是新 handler 专属——旧 handler 里 # 不认
        FlowData hashOnly = FlowData.create().set("userId", "u_h");
        assertNull(old.assign(execOf("manager#userId", hashOnly)));
    }

    /** 旧支的 {@code _NN} 档只认裸名 base（没有 f_ 变体），本轮没动它 */
    @Test
    public void rf02_existingFormFieldHandlerNumberSuffixBareOnly() {
        FormFieldAssigneeHandler old = new FormFieldAssigneeHandler();
        assertEquals("user5", old.assign(
                execOf("manager_01", FlowData.create().set("manager", "user5"))));
        FlowData onlyPrefixed = FlowData.create().set("f_manager", "user6");
        assertNull(old.assign(execOf("manager_01", onlyPrefixed)));
    }

    // ═══════════════════════════════════════════
    // 夹具
    // ═══════════════════════════════════════════

    /** start → apply（发起人）→ approver（本档 handler）→ end；节点唯一编码＝approver ⇒ 字段键 f_approver */
    private static String flowJson(String handlerFqn) {
        return ("{'name':'hr-proxy','displayName':'HR 代员工提交','type':'approval','nodes':["
                + "{'id':'start','type':'snaker:start','x':100,'y':200,'properties':{},'text':{'value':'开始'}},"
                + "{'id':'apply','type':'snaker:task','x':200,'y':200,'properties':{'assignee':'applicant','form':'apply-form','taskType':0,'performType':0},'text':{'value':'发起申请'}},"
                + "{'id':'approver','type':'snaker:task','x':300,'y':200,'properties':{'assignmentHandler':'HANDLER_FQN','form':'approve-form','taskType':0,'performType':0},'text':{'value':'部门审批'}},"
                + "{'id':'end','type':'snaker:end','x':400,'y':200,'properties':{},'text':{'value':'结束'}}],"
                + "'edges':["
                + "{'id':'e1','sourceNodeId':'start','targetNodeId':'apply','properties':{}},"
                + "{'id':'e2','sourceNodeId':'apply','targetNodeId':'approver','properties':{}},"
                + "{'id':'e3','sourceNodeId':'approver','targetNodeId':'end','properties':{}}]}")
                .replace("HANDLER_FQN", handlerFqn)
                .replace('\'', '"');
    }

    private ProcessInstance.ProcessDefine addDefine(String json) {
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName("hr-proxy-" + System.nanoTime());
        def.setDisplayName("HR 代员工提交");
        def.setType("approval");
        def.setState(1);
        def.setVersion(1);
        def.setContent(json.getBytes(StandardCharsets.UTF_8));
        repo.addDefine(def);
        assertNotNull(def.getId());
        return def;
    }

    /** HR（操作人，属 deptHR）代 emp1（属 dept2）发起，并自动办结 apply 档 */
    private ProcessInstance startHrProxy(String handlerFqn) throws Exception {
        ProcessInstance.ProcessDefine def = addDefine(flowJson(handlerFqn));
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "hr",
                FlowData.create().set("f_approver", "emp1"));
        ProcessTask apply = firstDoing(inst);
        assertEquals("apply", apply.getTaskName());
        assertEquals(Arrays.asList("hr"), apply.getActorIds());
        engine.executeProcessTask(apply.getTaskId(), "hr",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));
        return inst;
    }

    private ProcessTask firstDoing(ProcessInstance inst) {
        List<ProcessTask> tasks = repo.findDoingTasks(inst.getInstanceId(), null);
        assertEquals(1, tasks.size());
        return tasks.get(0);
    }

    private List<String> allActors(ProcessInstance inst) {
        List<String> actors = new ArrayList<>();
        for (ProcessTask task : repo.findDoingTasks(inst.getInstanceId(), null)) {
            actors.addAll(task.getActorIds());
        }
        return actors;
    }
}
