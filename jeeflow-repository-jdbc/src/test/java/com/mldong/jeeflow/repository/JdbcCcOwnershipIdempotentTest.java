package com.mldong.jeeflow.repository;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.enums.ProcessEventTypeEnum;
import com.mldong.jeeflow.event.ProcessEvent;
import com.mldong.jeeflow.event.ProcessEventListener;
import com.mldong.jeeflow.facade.JeeflowFacade;
import com.mldong.jeeflow.json.IJsonProvider;
import com.mldong.jeeflow.json.TypeReference;
import com.mldong.jeeflow.spi.IIdGenerator;
import com.mldong.jeeflow.spi.IProcessRepository;
import com.mldong.jeeflow.spi.IUserProvider;
import com.mldong.jeeflow.spi.PageQuery;
import com.mldong.jeeflow.spi.PageResult;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 抄送两格的 SQL 仓一路（issues/141 G1 归属条件必填 ＋ G2 写侧判重＝幂等空操作 · Java 栈）。
 *
 * <p>与 jeeflow-core 的 {@code CcPageOwnershipTest}（内存仓一路）/ {@code CcWriteIdempotentTest}
 * 跑同一条判据：<b>同一份数据，SQL 仓与内存仓必须给同一个答案</b>（spec 06 §2.5，
 * issues/117 场景 27 那把尺子扩到 ccList）。G1 的旧形状在这一侧是
 * {@code FROM wf_process_instance t LEFT JOIN wf_process_cc_instance cc} 不带条件时<b>返回全部实例</b>
 * （php PDO 仓的同款反面教材），而它自家内存仓只放"有 cc 行的实例"——两仓相反。</p>
 *
 * <p>G2 的四档逐字照 spec 06 §4：同一 {@code (实例, 人)} 已有 cc 行时<b>跳过</b>——
 * ①不新增行 ②不重置未读状态（state）③不更新原行时间（create_time/update_time 与原行 id 逐字不变）
 * ④不 fire CC_CREATE（码 4）。查询侧不引入 DISTINCT、历史重复行不清理（owner 拍为接受既成事实），
 * 所以这里只钉写侧。</p>
 *
 * <p>断言直接查 {@code wf_process_cc_instance} 的真实行——只看返回值或只看内存对象都不作数。</p>
 */
public class JdbcCcOwnershipIdempotentTest {

    private JdbcDataSource ds;
    private JdbcProcessRepository repo;
    private JeeflowFacade facade;
    private final AtomicLong idSeq = new AtomicLong(5_000_000L);
    private long instanceSeq = 1_000_000L;
    private final List<ProcessEvent> ccEvents = new CopyOnWriteArrayList<>();

    @Before
    public void setUp() throws Exception {
        ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:jeeflow_cc141_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        String ddl = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get("src/test/resources/schema-h2.sql")), StandardCharsets.UTF_8);
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            for (String sql : ddl.split(";")) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) stmt.execute(trimmed);
            }
            // DB_CLOSE_DELAY=-1 会跨测试类存活，逐表清干净再跑
            for (String t : new String[]{"wf_process_cc_instance", "wf_process_task_actor",
                    "wf_process_task", "wf_process_instance", "wf_process_define"}) {
                stmt.execute("DELETE FROM " + t);
            }
        }
        repo = new JdbcProcessRepository(ds);

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        IJsonProvider jsonProvider = new IJsonProvider() {
            @Override public String toJson(Object obj) {
                try { return mapper.writeValueAsString(obj); } catch (Exception e) { throw new RuntimeException(e); }
            }
            @Override public <T> T fromJson(String json, Class<T> type) {
                try { return mapper.readValue(json, type); } catch (Exception e) { throw new RuntimeException(e); }
            }
            @Override @SuppressWarnings("unchecked")
            public <T> T fromJson(String json, TypeReference<T> typeRef) {
                try { return (T) mapper.readValue(json, mapper.constructType(typeRef.getType())); }
                catch (Exception e) { throw new RuntimeException(e); }
            }
            @Override public boolean isJson(String str) {
                return str != null && (str.trim().startsWith("{") || str.trim().startsWith("["));
            }
        };

        Configuration config = new Configuration();      // 重置 ServiceContext（全局静态）
        ServiceContext.put("idGen", (IIdGenerator) idSeq::incrementAndGet);
        ServiceContext.put("repository", repo);
        ServiceContext.put("json", jsonProvider);
        ServiceContext.put("expr", new JdbcRepositoryTest.TestExprEvaluator());
        ServiceContext.put("user", new IUserProvider() {
            @Override public IUserProvider.UserInfo getUser(String userId) {
                return IUserProvider.UserInfo.of(userId);
            }
        });
        // 唯一监听器：只收 CC_CREATE，"重复抄送没有新事件"断在这里
        ServiceContext.put("ccCapture", (ProcessEventListener) event -> {
            if (event.getEventType() == ProcessEventTypeEnum.CC_CREATE) ccEvents.add(event);
        });
        JeeflowEngineImpl engine = new JeeflowEngineImpl();
        engine.configure(config);
        facade = new JeeflowFacade(engine, repo, null);
        ccEvents.clear();
    }

    @After
    public void tearDown() throws Exception {
        ccEvents.clear();
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
        }
    }

    // ═══ 夹具与取证辅助 ═══

    /** 直插一条实例行（cc 的归属对象），返回实例 id。 */
    private long newInstance(String businessNo) throws Exception {
        long id = ++instanceSeq;
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO wf_process_instance (id, process_define_id, state, business_no, operator,"
                             + " create_time, create_user) VALUES (?,1,10,?,'zhangsan',?,?)")) {
            ps.setLong(1, id);
            ps.setString(2, businessNo);
            ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            ps.setString(4, "zhangsan");
            ps.executeUpdate();
        }
        return id;
    }

    /** cc 表取证：某 (实例, 人) 的真实行 [id, state, create_time, update_time]。 */
    private List<Object[]> ccRows(long instanceId, String actorId) throws Exception {
        List<Object[]> rows = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, state, create_time, update_time FROM wf_process_cc_instance"
                             + " WHERE process_instance_id = ? AND actor_id = ? ORDER BY id")) {
            ps.setLong(1, instanceId);
            ps.setString(2, actorId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Object[]{rs.getLong("id"), rs.getInt("state"),
                            rs.getTimestamp("create_time"), rs.getTimestamp("update_time")});
                }
            }
        }
        return rows;
    }

    /** cc 表全量计数（某实例的所有 cc 行，不带 actor 条件）。 */
    private int ccRowCount(long instanceId) throws Exception {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM wf_process_cc_instance WHERE process_instance_id = ?")) {
            ps.setLong(1, instanceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private Map<String, Object> args(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i].toString(), kv[i + 1]);
        return m;
    }

    private void manualCc(long instanceId, String... actorIds) {
        Map<String, Object> resp = facade.flow("processInstance/createCCInstance", args(
                "processInstanceId", instanceId, "operator", "zhangsan",
                "actorIds", Arrays.asList((Object[]) actorIds)));
        assertEquals("手动抄送应成功: " + resp, Integer.valueOf(0), resp.get("code"));
    }

    private static List<String> ccActorIdsOf(List<ProcessEvent> events) {
        List<String> ids = new ArrayList<>();
        for (ProcessEvent event : events) ids.add(event.getCcActorId());
        return ids;
    }

    /** 让两次操作的时间戳必然可分（H2 TIMESTAMP 精度到毫秒）。 */
    private static void tick() throws InterruptedException {
        Thread.sleep(15L);
    }

    // ═══ G1：归属条件必填，缺条件 ⇒ 空页（SQL 仓） ═══

    /** 正向对照：带 {@code cc.actor_id} 条件时只出"我的"。 */
    @Test
    public void ccPageWithOwnershipConditionReturnsOnlyMine() throws Exception {
        long mine = newInstance("CC141-MINE");
        long theirs = newInstance("CC141-THEIRS");
        repo.createCcInstance(mine, "zhangsan", "user1");
        repo.createCcInstance(theirs, "zhangsan", "user2");

        PageResult<IProcessRepository.InstanceRow> page =
                repo.pageCcInstances(new PageQuery(1, 50).add("cc.actor_id", "EQ", "user1"));

        assertEquals("带条件应命中我的那 1 条", 1, page.getRecordCount());
        assertEquals("rows 与 recordCount 同口径", 1, page.getRows().size());
        assertEquals("命中的应是我的实例", Long.valueOf(mine), page.getRows().get(0).getId());
        assertTrue("别人的实例不该串进来", !Long.valueOf(theirs).equals(page.getRows().get(0).getId()));
    }

    /**
     * 缺陷档：整条归属条件都不给 ⇒ 空页。改前这一格是红的——LEFT JOIN 不带条件时
     * {@code WHERE 1=1} 直接放出<b>全部实例</b>（本用例库里就有 2 条实例），
     * 与它自家内存仓"只放有 cc 行的实例"两个答案。
     */
    @Test
    public void ccPageWithoutOwnershipConditionIsEmptyPage() throws Exception {
        long mine = newInstance("CC141-MINE");
        long theirs = newInstance("CC141-THEIRS");
        repo.createCcInstance(mine, "zhangsan", "user1");
        repo.createCcInstance(theirs, "zhangsan", "user2");

        PageResult<IProcessRepository.InstanceRow> noCondition = repo.pageCcInstances(new PageQuery(1, 50));
        assertEquals("缺归属条件必须返回空页，而不是全部实例", 0, noCondition.getRecordCount());
        assertEquals("空页的 rows 也必须是空集合", 0, noCondition.getRows().size());

        PageResult<IProcessRepository.InstanceRow> bareQuery = repo.pageCcInstances(new PageQuery());
        assertEquals("默认分页参数同样缺归属条件 ⇒ 空页", 0, bareQuery.getRecordCount());
    }

    /** 空值三形与"条件整条缺失"同档（issues/129 那一层已钉空串，这里补齐 SQL 仓的整套判据）。 */
    @Test
    public void blankOwnershipConditionIsAlsoEmptyPage() throws Exception {
        long mine = newInstance("CC141-MINE");
        repo.createCcInstance(mine, "zhangsan", "user1");

        assertEquals("空串归属条件 ⇒ 空页", 0,
                repo.pageCcInstances(new PageQuery(1, 50).add("cc.actor_id", "EQ", "")).getRecordCount());
        assertEquals("全空白与空串同档", 0,
                repo.pageCcInstances(new PageQuery(1, 50).add("cc.actor_id", "EQ", "   ")).getRecordCount());
        assertEquals("null 归属条件同样 ⇒ 空页", 0,
                repo.pageCcInstances(new PageQuery(1, 50).add("cc.actor_id", "EQ", null)).getRecordCount());
        assertEquals("空集合条件同样 ⇒ 空页（IN 给空集＝没有人）", 0,
                repo.pageCcInstances(new PageQuery(1, 50)
                        .add("cc.actor_id", "IN", Arrays.asList())).getRecordCount());
    }

    /** 改动面哨兵：只收归属谓词，非归属列的空值仍按"没填"忽略（可选过滤不许改成空页）。 */
    @Test
    public void blankNonOwnershipConditionIsStillIgnored() throws Exception {
        long mine = newInstance("CC141-MINE");
        repo.createCcInstance(mine, "zhangsan", "user1");

        PageResult<IProcessRepository.InstanceRow> page = repo.pageCcInstances(new PageQuery(1, 50)
                .add("cc.actor_id", "EQ", "user1")
                .add("t.business_no", "LIKE", ""));

        assertEquals("空值非归属条件应被忽略，归属条件照常生效", 1, page.getRecordCount());
    }

    // ═══ G2：写侧判重＝幂等空操作（SQL 仓） ═══

    /** 正向对照：全新的一次抄送照旧建行＋逐人 fire 码 4。 */
    @Test
    public void firstCcStillCreatesRowAndFiresPerActor() throws Exception {
        long instanceId = newInstance("CC141-FIRE");
        ccEvents.clear();
        tick();

        manualCc(instanceId, "8101", "8102");

        assertEquals("全新抄送应逐人建行", 2, ccRowCount(instanceId));
        assertEquals("全新抄送应逐人 fire（码 4）", 2, ccEvents.size());
        assertEquals("ccActorId 顺序与入参一致", Arrays.asList("8101", "8102"), ccActorIdsOf(ccEvents));
        assertEquals("首行应是未读（state=0）", Integer.valueOf(0), ccRows(instanceId, "8101").get(0)[1]);
    }

    /** ①不新增行 ＋ ④不 fire 码 4：门面手动腿连发两次同一个人（SQL 仓一路）。 */
    @Test
    public void repeatCcAddsNoRowAndFiresNothing() throws Exception {
        long instanceId = newInstance("CC141-REPEAT");
        manualCc(instanceId, "8201");
        assertEquals("首次抄送落 1 行", 1, ccRowCount(instanceId));
        assertEquals("首次抄送 fire 1 次", 1, ccEvents.size());
        long rowId = (Long) ccRows(instanceId, "8201").get(0)[0];

        ccEvents.clear();
        tick();
        manualCc(instanceId, "8201");

        assertEquals("①重复抄送不得新增行", 1, ccRowCount(instanceId));
        assertEquals("①原行 id 不变（没有删旧插新）", Long.valueOf(rowId),
                (Long) ccRows(instanceId, "8201").get(0)[0]);
        assertEquals("④没发生创建就不得发码 4（spec 11.2 原则 1「码=事实」）", 0, ccEvents.size());
    }

    /** ②不重置未读：SQL 置已读（state=1）后重复抄送，state 必须仍是 1。 */
    @Test
    public void repeatCcDoesNotResetUnreadState() throws Exception {
        long instanceId = newInstance("CC141-READ");
        repo.createCcInstance(instanceId, "zhangsan", "8301");
        repo.updateCcStatus(instanceId, "8301");
        assertEquals("置读后 state 应为 1", Integer.valueOf(1), ccRows(instanceId, "8301").get(0)[1]);

        tick();
        repo.createCcInstance(instanceId, "zhangsan", "8301");

        assertEquals("①重复抄送不得新增行", 1, ccRowCount(instanceId));
        assertEquals("②重复抄送不得把已读抹回未读", Integer.valueOf(1), ccRows(instanceId, "8301").get(0)[1]);
    }

    /** ③不更新原行时间：create_time / update_time 逐字不变（跳过式判重不走 UPDATE，也不重插）。 */
    @Test
    public void repeatCcDoesNotTouchOriginalRowTimes() throws Exception {
        long instanceId = newInstance("CC141-TIME");
        repo.createCcInstance(instanceId, "zhangsan", "8401");
        Object[] before = ccRows(instanceId, "8401").get(0);
        Timestamp createTime = (Timestamp) before[2];
        Timestamp updateTime = (Timestamp) before[3];
        assertNotNull(createTime);

        tick();
        repo.createCcInstance(instanceId, "zhangsan", "8401");

        Object[] after = ccRows(instanceId, "8401").get(0);
        assertEquals("③重复抄送不得刷新原行 create_time", createTime, after[2]);
        assertEquals("③重复抄送不得刷新原行 update_time", updateTime, after[3]);
    }

    /** 子集档：第二次给「已知人＋新人」⇒ 只为新人建行、只为新人 fire。 */
    @Test
    public void repeatCcFiresOnlyForNewlyCreatedSubset() throws Exception {
        long instanceId = newInstance("CC141-SUBSET");
        manualCc(instanceId, "8501", "8502");
        assertEquals("首轮 2 行", 2, ccRowCount(instanceId));
        assertEquals("首轮 fire 2 次", 2, ccEvents.size());

        ccEvents.clear();
        tick();
        manualCc(instanceId, "8501", "8503");

        assertEquals("只为实际新人建行", 3, ccRowCount(instanceId));
        assertEquals("逐人 fire 的入参应是实际新建的子集", Arrays.asList("8503"), ccActorIdsOf(ccEvents));
        assertEquals("子集只有 1 人 ⇒ 只 fire 1 次", 1, ccEvents.size());
        assertEquals("新人 8503 的行应真在库里", 1, ccRows(instanceId, "8503").size());
    }

    /** SPI 读侧：{@code findCcActorIds} 反映真实行集（判重的依据不能是内存猜测）。 */
    @Test
    public void findCcActorIdsReadsTheRealRows() throws Exception {
        long instanceId = newInstance("CC141-READ-IDS");
        assertEquals("空实例没有 cc 行", Arrays.asList(), repo.findCcActorIds(instanceId));
        repo.createCcInstance(instanceId, "zhangsan", "8601", "8602");
        assertEquals("两行两个人", Arrays.asList("8601", "8602"), repo.findCcActorIds(instanceId));
        repo.createCcInstance(instanceId, "zhangsan", "8601", "8603");
        assertEquals("重复的 8601 不新增", Arrays.asList("8601", "8602", "8603"),
                repo.findCcActorIds(instanceId));
    }

    // ═══ G10：空抄送人不建 cc 行（owner 2026-09-29 拍「空不创建行」，spec 06 §2.10） ═══

    /**
     * 手动腿给全空白 ⇒ 库里一行都不许有、码 4 一支都不发。
     * java 旧形状：{@code Arrays.asList("")} → {@code actor_id=''} 真落一行——
     * 空归属值正是 issues/129 那族"空 operator 读全库"的病根，不能从抄送侧继续灌。
     */
    @Test
    public void blankCcActorsCreateNoRowAtAll() throws Exception {
        long instanceId = newInstance("CC141-G10-ALL-BLANK");
        ccEvents.clear();

        Map<String, Object> resp = facade.flow("processInstance/createCCInstance", args(
                "processInstanceId", instanceId, "operator", "zhangsan",
                "actorIds", Arrays.asList("", "   ")));

        assertEquals("G10：全空白与空集合同档（spec 06 §2.10）", "actorIds 缺失", resp.get("msg"));
        assertEquals("G10：cc 表必须零行", 0, ccRowCount(instanceId));
        assertEquals("G10：actor 集合必须为空", Arrays.asList(), repo.findCcActorIds(instanceId));
        assertEquals("G10：全空白不得 fire 码 4", 0, ccEvents.size());
    }

    /** 混着给：只丢空元素，有效的人照旧落行＋fire（逗号串与数组两形同判据在 core 侧钉）。 */
    @Test
    public void blankElementsAreDroppedValidOnesRemain() throws Exception {
        long instanceId = newInstance("CC141-G10-MIXED");
        ccEvents.clear();

        manualCc(instanceId, "8701", "", "  ", "8702");

        assertEquals("G10：空元素丢弃、有效元素保留", Arrays.asList("8701", "8702"),
                repo.findCcActorIds(instanceId));
        assertEquals("G10：库里只有两行", 2, ccRowCount(instanceId));
        assertEquals("G10：fire 的入参只含有效的人", Arrays.asList("8701", "8702"), ccActorIdsOf(ccEvents));
    }

    /**
     * 写侧兜底：绕过引擎/门面直连仓储时，空串／纯空白／{@code null} 同样建不出行。
     * 只修漏斗（{@code handleCcActors}）不修写侧，第三方仓储直投就还能灌进空值——本条钉第二层。
     */
    @Test
    public void repoWritePathAlsoDropsBlankActors() throws Exception {
        long instanceId = newInstance("CC141-G10-REPO");

        repo.createCcInstance(instanceId, "zhangsan", "", "   ", null, "8801");

        assertEquals("G10：SQL 仓写侧空串/纯空白/null 都不建行", Arrays.asList("8801"),
                repo.findCcActorIds(instanceId));
        assertEquals("G10：只落那一行", 1, ccRowCount(instanceId));
    }

    /** 落库值取 trim 后的串：{@code " 8901 "} 与 {@code "8901"} 是同一个人（与 G2 判重咬合）。 */
    @Test
    public void ccActorValueIsTrimmedAndHitsTheDedupRule() throws Exception {
        long instanceId = newInstance("CC141-G10-TRIM");
        repo.createCcInstance(instanceId, "zhangsan", " 8901 ");
        assertEquals("G10：入库值应是 trim 后的串", Arrays.asList("8901"), repo.findCcActorIds(instanceId));

        ccEvents.clear();
        tick();
        manualCc(instanceId, "8901");

        assertEquals("G10：带空格与不带空格判为同一人 ⇒ 不新增行", 1, ccRowCount(instanceId));
        assertEquals("G10：判重命中 ⇒ 不 fire 码 4", 0, ccEvents.size());
    }

    /** {@code createCcInstanceIfAbsent} 返回的子集也不得含空值——子集直接拿去 fire。 */
    @Test
    public void ifAbsentSubsetExcludesBlankActors() throws Exception {
        long instanceId = newInstance("CC141-G10-SUBSET");

        List<String> created = repo.createCcInstanceIfAbsent(instanceId, "zhangsan",
                "", "8951", "  ", " 8952 ");

        assertEquals("G10：实际新建子集只含有效且 trim 后的人", Arrays.asList("8951", "8952"), created);
        assertEquals("G10：子集与库里真行一致", Arrays.asList("8951", "8952"), repo.findCcActorIds(instanceId));
    }

    /** 反向哨兵：判据只吃空值，不吃 {@code "0"} 这类"看起来像空"的正常 id。 */
    @Test
    public void normalActorIdsAreNotMistakenForBlank() throws Exception {
        long instanceId = newInstance("CC141-G10-SENTINEL");
        ccEvents.clear();

        manualCc(instanceId, "0", "user-1");

        assertEquals("G10 只丢空串/纯空白：'0' 不得被吃掉", Arrays.asList("0", "user-1"),
                repo.findCcActorIds(instanceId));
        assertEquals("反向哨兵：照旧逐人 fire", 2, ccEvents.size());
    }
}
