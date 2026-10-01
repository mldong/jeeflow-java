package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.Context;
import com.mldong.jeeflow.context.SimpleContext;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.model.ProcessModel;
import com.mldong.jeeflow.model.TaskModel;
import com.mldong.jeeflow.parser.ModelParser;
import com.mldong.jeeflow.spi.IOrgUserProvider;
import com.mldong.jeeflow.spi.IUserProvider;
import com.mldong.jeeflow.util.FlowUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * issues/137 A · 裁定 A · <b>实例级</b> {@code expire_time} ＝「定义<b>顶层</b>表达式的<b>求值结果</b>」
 * （批二 §3-4；基准就是本栈 {@code JeeflowEngineImpl.java:93-96}，同形状 boot2
 * {@code ProcessInstanceServiceImpl.java:157-160}）。
 *
 * <p><b>本案的病灶不在引擎那一句，而在它上游那一跳</b>：{@code JeeflowEngineImpl:93} 读的是
 * {@code ProcessModel.getExpireTime()}，而 {@link ModelParser#parse} 逐字段搬了
 * name/displayName/type/instanceUrl/instanceNoClass/post|preInterceptors/relTableName/persistMode
 * （{@code :58-66}）<b>恰恰没搬 expireTime</b>——{@code LfModel:15/27} 那个字段是在的，
 * 全 {@code src/main} 里 {@code ProcessModel.setExpireTime} 零调用者。⇒ 引擎那句守卫永远读到 null，
 * {@code wf_process_instance.expire_time} <b>在参考实现里恒 NULL</b>，"A 的形状"只是形。</p>
 *
 * <p>这条断链是批二 §3-4 的 go 腿普查查出来的（go 无独立解析阶段、根键由结构体直接收，落地后
 * <b>反超基准</b>）；c# 同病（{@code ModelParser.cs:81-92} 的初始化器同样没有 {@code ExpireTime}）。
 * 所以 §3-4 真正的整改面是<b>八栈</b>而不是六栈。本文件第 1 格就是把这一跳单独钉住——
 * 它红了，后面四格根本没机会绿。</p>
 *
 * <p><b>四条口径</b>（各栈测试同尺）：①进列的是<b>时刻</b>不是表达式原串；②变量源＝<b>发起参数</b>
 * 那份（{@code addUserInfoToArgs}/{@code addAutoGenTitle} 之后，与 {@code :93} 就地用的同一个
 * {@code args} 同档）；③没配（缺键／空串／纯空白）⇒ 该列 NULL，不赋 {@code now()}、不赋空串；
 * ④配了但算不出（误配／负数档，§3-2／§3-3 已定的落穿语义）⇒ 同样 NULL，<b>任何一档都不许兜底当前时间</b>。</p>
 *
 * <p>上下文隔离同兄弟测试：{@code ServiceContext} 是静态的，每个用例换一枚干净 {@code SimpleContext}，
 * {@code @After} 还原。{@link MemoryProcessRepository#findInstanceById} 返回<b>活引用</b>
 * （{@code InstanceEndEventTimingTest} 类注释记着这一点），所以"读回值"在这里只能证"这一路真的走到并赋过值"，
 * 证不了落库时序——那半由 php/rust/moon/go/python/node 六栈的仓储读回格与真库腿各自钉住，本栈不装。</p>
 */
public class InstanceExpireTimeOnStartTest {

    private Context savedContext;
    private MemoryProcessRepository repo;
    private JeeflowEngine engine;

    @Before
    public void setUp() {
        savedContext = ServiceContext.getContext();
        repo = new MemoryProcessRepository();
        SimpleContext ctx = new SimpleContext();
        Configuration config = new Configuration(ctx);
        ctx.put("repository", repo);
        ctx.put("json", new TestJsonProvider());
        ctx.put("expr", new TestExpressionEvaluator());
        ctx.put("user", new IUserProvider() {
            @Override
            public IUserProvider.UserInfo getUser(String userId) {
                IUserProvider.UserInfo u = new IUserProvider.UserInfo();
                u.setUserId(userId);
                // realName 直接取 userId 本身：这样把 operator 造成为一个合法时刻串时，
                // 注入后的 u_realName 就是那个时刻，口径②那一格才有可判的正面判点。
                u.setRealName(userId);
                return u;
            }
        });
        ctx.put("org", new IOrgUserProvider() {
            @Override public List<String> findDeptLeaders(String deptId) { return null; }
            @Override public List<String> findDeptMainLeaders(String deptId) { return null; }
            @Override public List<String> findByRole(String roleCode) { return null; }
        });
        engine = new JeeflowEngineImpl();
        engine.configure(config);
    }

    @After
    public void tearDown() {
        if (savedContext != null) {
            ServiceContext.setContext(savedContext);
        }
    }

    /** 根上带不带 {@code expireTime} 由参数决定：{@code null} ＝压根没这个键（"没配"档的正解形状）。 */
    private static String flow(String rootExpire, String nodeId, String nodeExpire) {
        String root = rootExpire == null ? "" : "'expireTime':'" + rootExpire + "',";
        String nodeExpirePart = nodeExpire == null
                ? "'assignee':'applicant','taskType':0,'performType':0"
                : "'assignee':'applicant','taskType':0,'performType':0,'expireTime':'" + nodeExpire + "'";
        return ("{'name':'i137a-INSTANCE','displayName':'实例级到期','type':'approval'," + root
                + "'nodes':["
                + "{'id':'start','type':'snaker:start','x':100,'y':200,'properties':{},'text':{'value':'开始'}},"
                + "{'id':'" + nodeId + "','type':'snaker:task','x':300,'y':200,'properties':{" + nodeExpirePart
                + "},'text':{'value':'审批'}},"
                + "{'id':'end','type':'snaker:end','x':500,'y':200,'properties':{},'text':{'value':'结束'}}],"
                + "'edges':["
                + "{'id':'e0','sourceNodeId':'start','targetNodeId':'" + nodeId + "','properties':{}},"
                + "{'id':'e1','sourceNodeId':'" + nodeId + "','targetNodeId':'end','properties':{}}]}")
                .replace('\'', '"');
    }

    private ProcessInstance.ProcessDefine addDefine(String name, String json) {
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName(name);
        def.setDisplayName("实例级到期用例");
        def.setType("approval");
        def.setState(1);
        def.setVersion(1);
        def.setContent(json.getBytes(StandardCharsets.UTF_8));
        repo.addDefine(def);
        assertNotNull(def.getId());
        return def;
    }

    private ProcessInstance start(String name, String rootExpire, FlowData args) {
        ProcessInstance.ProcessDefine def = addDefine(name, flow(rootExpire, "approve", null));
        return engine.startProcessInstanceById(def.getId(), "user1", args);
    }

    // ═══ 1. 断链那一跳：解析期必须把根上的表达式搬进 ProcessModel ═══

    /**
     * <b>本案的改前红样就在这一格</b>：{@code ModelParser.parse} 没搬 expireTime 时这里读到 null，
     * 于是 {@code JeeflowEngineImpl:93-96} 那句守卫永远不成立、实例列恒 NULL——
     * 「形状是 A、链路断在解析」正是这一格要取的证，故排在最前面。
     * 同时钉住"没配"与"配了"两档在解析层的读法：缺键 ⇒ null（不是空串，交给守卫），
     * 空串 ⇒ 空串（守卫同样挡下）。
     */
    @Test
    public void parserCarriesRootExpireTimeIntoProcessModel() {
        ProcessModel with = ModelParser.parse(flow("2h", "approve", null).getBytes(StandardCharsets.UTF_8));
        assertEquals("根上 expireTime 必须被搬进 ProcessModel（引擎那句读的就是它）",
                "2h", with.getExpireTime());

        ProcessModel absent = ModelParser.parse(flow(null, "approve", null).getBytes(StandardCharsets.UTF_8));
        assertNull("根上没这个键 ⇒ 解析层就该是 null（不是空串占位）", absent.getExpireTime());

        // 定义级与节点级是**两层两个键**，解析层不许互相污染
        ProcessModel twoLevels = ModelParser.parse(
                flow("3h", "approve", "1d").getBytes(StandardCharsets.UTF_8));
        assertEquals("3h", twoLevels.getExpireTime());
        String nodeExpr = null;
        for (TaskModel t : twoLevels.getTasks()) {
            if ("approve".equals(t.getName())) nodeExpr = t.getExpireTime();
        }
        assertEquals("节点级那一档仍是节点自己的 properties.expireTime，两层互不污染", "1d", nodeExpr);
    }

    // ═══ 2. 口径①：进列的是时刻，且≈表达式偏移（同一行 expire − create）═══

    /**
     * 配 {@code 2h} ⇒ 实例列是<b>时刻</b>，同一行 {@code expire_time − create_time ≈ 2h}。
     * 判据形状照任务行那一族（带宽 [-5s,+60s]，秒级量纲同因）：<b>不是</b>"非空"空判——
     * 占位 {@code now()} 算出的 ≈0 与"根本没算"的 NULL 都得被这一格夹在外面。
     * 再钉一条"列上不是原串"：求值结果与 {@code "2h"} 逐字不同 ⇒ 谁把搬运改回来就红。
     */
    @Test
    public void relativeTierOnInstanceBecomesAMomentNotRawExpression() {
        ProcessInstance inst = start("i137a-2h", "2h", FlowData.create());
        LocalDateTime expire = inst.getExpireTime();
        assertNotNull("定义配了 2h ⇒ 实例 expire_time 必须有值（本栈改前恒 NULL，就是本案病灶）", expire);
        long delta = Duration.between(inst.getCreateTime(), expire).getSeconds();
        assertTrue("expire − create 应≈2h（实得 " + delta + "s）；占位 now() 会算出≈0 ⇒ 发起即逾期",
                delta >= 2 * 3600L - 5L && delta <= 2 * 3600L + 60L);

        ProcessInstance reread = repo.findInstanceById(inst.getInstanceId());
        assertNotNull("仓储读回实例（证明这一列随实例进了仓）", reread);
        assertEquals("读回值与引擎返回同一个时刻", expire, reread.getExpireTime());
    }

    /** {@code d} 档走<b>日历加天</b>（{@code Calendar.add(DAY_OF_MONTH)}），与 s/m/h 的毫秒加法不同形 ⇒ 单独一格。 */
    @Test
    public void dayTierOnInstanceIsCalendarBased() {
        ProcessInstance inst = start("i137a-3d", "3d", FlowData.create());
        LocalDateTime expire = inst.getExpireTime();
        assertNotNull(expire);
        long days = Duration.between(inst.getCreateTime(), expire).toDays();
        assertTrue("3d 应≈3 天（实得 " + days + " 天）；乘 86400 与日历加天在跨月末/夏令时不等价",
                days >= 2 && days <= 4);
    }

    /** 绝对档：表达式本身就是期望完成时刻 ⇒ 实例列等于那一刻（逐值，不留带宽）。 */
    @Test
    public void absoluteTierOnInstanceIsThatInstant() {
        ProcessInstance inst = start("i137a-abs", "2027-03-04 05:06:07", FlowData.create());
        assertEquals(LocalDateTime.of(2027, 3, 4, 5, 6, 7), inst.getExpireTime());
    }

    // ═══ 3. 口径②：变量源＝发起参数那一份 ═══

    /**
     * 表达式是<b>发起时提交的变量名</b> ⇒ 取该变量的值当到期时刻（{@code processTime} 第①档）。
     * 这一格同时钉"传的是注入之后那份 {@code args}"：引擎在 {@code :87-90} 就地往同一个
     * {@code args} 里注了 {@code u_*} 与 {@code autoGenTitle}，若实现改成"取调用方原始 dict"，
     * {@code u_realName} 那档就取不到（见下一格），本档也会因少了注入键而分叉。
     */
    @Test
    public void variableTierTakesTheStartArgs() {
        Map<String, Object> seed = new HashMap<String, Object>();
        seed.put("dueAt", "2026-12-31 10:00:00");
        ProcessInstance inst = start("i137a-var", "dueAt", FlowData.of(seed));
        assertEquals(LocalDateTime.of(2026, 12, 31, 10, 0, 0), inst.getExpireTime());
    }

    /**
     * 表达式是 {@code u_realName}——该键<b>只存在于引擎注入之后</b>那份 args 里
     * （{@code FlowUtil.addUserInfoToArgs}）。用户桩把 realName 做成合法时刻串，
     * 于是"取对了那份 args"能读出一个时刻、"取错那份"读不到 ⇒ 落穿后算不出 ⇒ NULL。
     * 这就是口径②的牙。
     */
    @Test
    public void variableTierReadsTheInjectedArgsCopy() {
        // operator 造成一个合法时刻串 ⇒ 注入的 u_realName 就是它（本类用户桩 realName＝userId）
        ProcessInstance.ProcessDefine def =
                addDefine("i137a-u", flow("u_realName", "approve", null));
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(),
                "2030-05-06 07:08:09", FlowData.create());
        assertEquals("表达式 u_realName 只存在于注入之后那份 args ⇒ 取对了就是那一刻",
                LocalDateTime.of(2030, 5, 6, 7, 8, 9), inst.getExpireTime());
    }

    /**
     * 变量档<b>优先于</b>相对档：根上表达式恰好是个变量名而 args 里真有那个键时取变量值。
     * 顺带钉"两层不串"——节点自己配 {@code 1d} 不影响实例那一列取 {@code 2h}。
     */
    @Test
    public void variableTierBeatsRelativeTierAndTwoLevelsDoNotCrossFeed() {
        ProcessInstance.ProcessDefine def =
                addDefine("i137a-two", flow("2h", "approve", "1d"));
        Map<String, Object> seed = new HashMap<String, Object>();
        seed.put("2h", "2028-08-08 08:08:08");
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "user1", FlowData.of(seed));
        assertEquals("变量档压过相对档", LocalDateTime.of(2028, 8, 8, 8, 8, 8), inst.getExpireTime());
    }

    // ═══ 4. 口径③：没配 ⇒ NULL（三档）；口径④：算不出 ⇒ NULL（含 §3-2 负档）═══

    /**
     * 缺键／空串／纯空白三档都算"没配" ⇒ 实例列必须 NULL。
     * 每档都配一枚"同行 create_time 有值"的对照，防"根本没建实例"冒充"这一列为空"（恒真陷阱）。
     */
    @Test
    public void unconfiguredDefinitionKeepsInstanceColumnNull() {
        for (String root : new String[]{null, "", "   "}) {
            ProcessInstance inst = start("i137a-none-" + String.valueOf(root).length(), root, FlowData.create());
            assertNotNull("对照：实例确实建出来了（create_time 有值）", inst.getCreateTime());
            assertNull("根上「" + root + "」算没配 ⇒ 这一列必须 NULL，不赋 now()、不赋空串",
                    inst.getExpireTime());
        }
    }

    /**
     * 配了但<b>算不出</b>：误配（{@code not-a-time}／{@code xh}／小数前缀 {@code 2.5h}）
     * 与<b>负数档</b>（{@code -5h}／{@code -3d}，§3-2；带空白的 {@code " -5h"}，§3-3 裁完仍判负）
     * ⇒ 一律 NULL。红样形状是"兜底成当前时间"或"把原串写进列"，两种都被这一格夹住。
     */
    @Test
    public void unparsableExpressionKeepsInstanceColumnNull() {
        for (String root : new String[]{"not-a-time", "xh", "2.5h", "-5h", "-3d", " -5h", "2h "}) {
            ProcessInstance inst = start("i137a-bad-" + root.length(), root, FlowData.create());
            assertNotNull("对照：实例确实建出来了", inst.getCreateTime());
            assertNull("表达式「" + root + "」算不出 ⇒ 这一列必须 NULL（不兜底 now、不写原串）",
                    inst.getExpireTime());
        }
    }

    /**
     * 尺子同一枚（口径⑤）：实例那一列的求值结果 ＝ {@code FlowUtil.processTime} 直调同一表达式的输出。
     * 谁给实例级另造一把尺子（比如自己拼 {@code now+2h}），这一格当场红。
     */
    @Test
    public void instanceAndEvaluatorGiveSameAnswer() {
        FlowData args = FlowData.create();
        ProcessInstance inst = start("i137a-ruler", "90s", args);
        LocalDateTime direct = FlowUtil.processTime("90s", FlowData.create());
        assertNotNull(inst.getExpireTime());
        long diff = Math.abs(Duration.between(direct, inst.getExpireTime()).getSeconds());
        assertTrue("实例列应与 processTime 直调同解（差 " + diff + "s）", diff <= 61L);
    }
}
