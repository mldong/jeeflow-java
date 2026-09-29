package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Context;
import com.mldong.jeeflow.context.SimpleContext;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.spi.IProcessRepository;
import com.mldong.jeeflow.spi.PageQuery;
import com.mldong.jeeflow.spi.PageResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 抄送分页归属条件必填（issues/141 G1 · Java 栈，内存仓一路）。
 *
 * <p>立法逐字依据＝spec 06-facade.md §2.5「抄送分页同一条尺子（owner 2026-09-29 拍）」：
 * {@code pageCcInstances} 这类"抄送我"取数入口，归属条件（{@code cc.actor_id}）<b>必填</b>——
 * 条件缺失或为空值时<b>返回空页</b>，不得退化成"这条条件不加"而返回全部实例。</p>
 *
 * <p>本案的原始症状正是"同一栈两个仓储两个答案"：内存仓只放"有 cc 行的实例"（inner 语义），
 * 而 SQL 仓的 {@code LEFT JOIN wf_process_cc_instance} 不带条件时返回全部实例。
 * 判据要同时钉在两仓上（issues/117 场景 27 那把尺子扩到 ccList）——SQL 仓一路见
 * {@code JdbcCcOwnershipIdempotentTest}，两仓的每一格读数必须相等。</p>
 *
 * <p>门面路径本身不受影响（{@code ccList} 恒挂 {@code cc.actor_id EQ operator}，空串按
 * issues/129 第一层回落缺省 user1），这里打的是<b>直连仓储</b>那一档：门面上的调用方绕过
 * 门面、或下一版门面改动漏挂条件时，仓储这一层必须自己顶住。</p>
 */
public class CcPageOwnershipTest {

    private static final String MINIMAL_FLOW =
            ("{'name':'cc-owner-141','displayName':'抄送归属流程','type':'approval','nodes':["
            + "{'id':'start','type':'snaker:start','x':100,'y':200,'properties':{},'text':{'value':'开始'}},"
            + "{'id':'approval','type':'snaker:task','x':300,'y':200,'properties':{'form':'f','assignee':'leader',"
            + "'taskType':0,'performType':0},'text':{'value':'审批'}},"
            + "{'id':'end','type':'snaker:end','x':500,'y':200,'properties':{},'text':{'value':'结束'}}],"
            + "'edges':["
            + "{'id':'e1','sourceNodeId':'start','targetNodeId':'approval','properties':{}},"
            + "{'id':'e2','sourceNodeId':'approval','targetNodeId':'end','properties':{}}]}").replace('\'', '"');

    private Context savedContext;
    private MemoryProcessRepository repo;

    @Before
    public void setUp() {
        savedContext = ServiceContext.getContext();
        repo = new MemoryProcessRepository();
        // 干净上下文（内存仓 + JSON provider）：本用例直连仓储，不需要引擎/解析器装配
        Context ctx = new com.mldong.jeeflow.context.SimpleContext();
        ServiceContext.setContext(ctx);
        ctx.put("repository", repo);
        ctx.put("json", new TestJsonProvider());
    }

    @After
    public void tearDown() {
        if (savedContext != null) {
            ServiceContext.setContext(savedContext);
        }
    }

    /** 建一个实例并抄送给给定的人，返回实例 id。 */
    private Long instanceCcTo(String actorId) {
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName("cc-owner-" + System.nanoTime());
        def.setDisplayName("抄送归属流程");
        def.setType("approval");
        def.setState(1);
        def.setVersion(1);
        def.setContent(MINIMAL_FLOW.getBytes(StandardCharsets.UTF_8));
        repo.addDefine(def);
        // 直接走仓储写侧（不引引擎），把这一格钉在"分页判据"上而不是抄送流程上。
        // business_no 给非空值：空值列在任何条件上都是 SQL 三值逻辑的"恒不命中"档，
        // 会让下面那格"非归属条件空值仍被忽略"的对照在两仓各说各话（既有形状，非本案范围）。
        ProcessInstance inst = ProcessInstance.create(def, "zhangsan",
                FlowData.create().set(FlowConst.BUSINESS_NO, "CC141-" + actorId));
        repo.saveInstance(inst);
        repo.createCcInstance(inst.getInstanceId(), "zhangsan", actorId);
        assertNotNull(inst.getInstanceId());
        return inst.getInstanceId();
    }

    private int rowCount(PageResult<IProcessRepository.InstanceRow> page) {
        return page.getRows() == null ? -1 : page.getRows().size();
    }

    /** 正向对照：带归属条件时照旧只出"我的"那一页。 */
    @Test
    public void ccPageWithOwnershipConditionReturnsOnlyMine() {
        Long mine = instanceCcTo("user1");
        Long theirs = instanceCcTo("user2");

        PageResult<IProcessRepository.InstanceRow> page =
                repo.pageCcInstances(new PageQuery(1, 50).add("cc.actor_id", "EQ", "user1"));

        assertEquals("带条件应命中我的那 1 条", 1, page.getRecordCount());
        assertEquals("rows 数与 recordCount 同口径", 1, rowCount(page));
        assertEquals("命中的应是我的实例", mine, page.getRows().get(0).getId());
        assertTrue("别人的实例不该串进来", !theirs.equals(page.getRows().get(0).getId()));
    }

    /**
     * 缺陷档：整条归属条件都不给 ⇒ <b>空页</b>。
     * 改前这一格是红的——内存仓会返回"所有有 cc 行的实例"（2 条），
     * 而它的 SQL 仓兄弟返回全部实例，两仓两个答案。
     */
    @Test
    public void ccPageWithoutOwnershipConditionIsEmptyPage() {
        instanceCcTo("user1");
        instanceCcTo("user2");

        PageResult<IProcessRepository.InstanceRow> noCondition = repo.pageCcInstances(new PageQuery(1, 50));
        assertEquals("缺归属条件必须返回空页，而不是所有有 cc 行的实例", 0, noCondition.getRecordCount());
        assertEquals("空页的 rows 也必须是空集合", 0, rowCount(noCondition));

        PageResult<IProcessRepository.InstanceRow> bareQuery = repo.pageCcInstances(new PageQuery());
        assertEquals("默认分页参数同样缺归属条件 ⇒ 空页", 0, bareQuery.getRecordCount());
    }

    /** 空值三形（空串 / 全空白 / null）与"条件整条缺失"同档。 */
    @Test
    public void blankOwnershipConditionIsAlsoEmptyPage() {
        instanceCcTo("user1");
        instanceCcTo("user2");

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

    /**
     * 改动面哨兵：只收归属谓词，不改 {@code PageQuery} 对可选过滤空值的通用放行
     * （{@code m_LIKE_*} 传空串按"没填"处理是对的，issues/129 同一条边界）。
     */
    @Test
    public void blankNonOwnershipConditionIsStillIgnored() {
        Long mine = instanceCcTo("user1");
        assertNotNull(mine);

        PageResult<IProcessRepository.InstanceRow> page = repo.pageCcInstances(new PageQuery(1, 50)
                .add("cc.actor_id", "EQ", "user1")
                .add("t.business_no", "LIKE", ""));

        assertEquals("空值非归属条件应被忽略，归属条件照常生效", 1, page.getRecordCount());
    }
}
