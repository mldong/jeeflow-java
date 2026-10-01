package com.mldong.jeeflow.test;

import com.mldong.jeeflow.util.StringUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 参与者<b>删除腿</b>归属值判据（issues/137 §3-6 · owner 2026-10-02 拍「两形并集」· Java 基准腿）。
 *
 * <p>立法逐字依据＝spec 06-facade.md <b>§processTask/removeTaskActor 语义 6</b> ＋ <b>§2.11</b> 写点表末行
 * （删除腿那一行）。判据本体只有一枚＝{@link StringUtils#actorDeleteForms(String...)}，
 * 本文件既钉这枚单点自己，也钉内存仓 {@code removeTaskActor} 确实走了它。</p>
 *
 * <p><b>为什么是"两形并集"而不是只取一头</b>——1.8.36 之前八栈正好分成相反的两派，各自都有一种假成功：</p>
 * <ul>
 *   <li>只取 <b>trim 值</b>（php/csharp/rust/moon 四栈八处的旧形状）⇒ 门面按语义 6 交出的历史脏行原值
 *       {@code " 9101 "} 被削成 {@code 9101}，真库 NO PAD 排序规则下那一行删不掉而门面报成功
 *       ——<b>被摘的人待办还在</b>（{@link #untrimmedLegacyRowIsDeletedByItsRawForm} 钉这一档）；</li>
 *   <li>只取 <b>原值</b>（go/node/python/java 四栈九处的旧形状）⇒ 第三方绕过门面直连仓储传
 *       {@code " 8601 "} 时删不掉写侧归一后落库的规范行 {@code 8601}（issues/142 §9.2 那一路，
 *       {@link #normalizedRowIsStillDeletedByTrimmedForm} 钉这一档）；且空值照喂 {@code DELETE}，
 *       把历史 {@code actor_id=''} 脏行批量误删（{@link #blankInputNeverDeletesEmptyActorIdDirtyRow}
 *       与 {@link #allBlankInputIsNoOpAndNeverClearsAllActors} 钉这两档）。</li>
 * </ul>
 *
 * <p>两形并集同时满足两侧，且按 §2.11 归一口径 {@code " 9101 "} 与 {@code 9101} 本就是<b>同一个人</b>，
 * 两行都删才是"摘掉这个人"的正确结果，不构成误删。<b>并集包含 trim 形</b>，因此 issues/142 B 批
 * "删除位 trim"的既有测试无需反向改（rust {@code test_i142_b_remove_task_actor_trims_and_blank_is_noop}、
 * moon {@code i142b_task_actor_write_test.mbt} 那几格照绿）。</p>
 *
 * <p>SQL 仓一路（含真库 NO PAD 排序规则下脏行能否真删掉）在 {@code jeeflow-repository-jdbc} 的
 * {@code JdbcTaskActorDeleteFormsTest}，两仓必须同答案（issues/117 场景 27 那把尺子）。</p>
 */
public class TaskActorDeleteFormsTest {

    private static final Long TASK = 7301L;

    private MemoryProcessRepository repo;

    @Before
    public void setUp() {
        repo = new MemoryProcessRepository();
    }

    // ═══ 一、单点纯函数：StringUtils.actorDeleteForms ═══

    /** 义务①：{@code null}／空串／纯空白／制表换行一律丢弃，一个都不进删除集合。 */
    @Test
    public void blankAndNullElementsAreDroppedEntirely() {
        assertEquals("空值一律丢弃（不得喂进 DELETE，否则误删 actor_id='' 脏行）",
                Collections.emptyList(),
                StringUtils.actorDeleteForms(null, "", "   ", "\t", "\r\n", null));
        assertEquals("入参数组本身为 null ⇒ 空列表（早退，不发 DELETE）",
                Collections.emptyList(), StringUtils.actorDeleteForms((String[]) null));
    }

    /** 义务②：非空值<b>同时</b>产出「原值」与「trim 值」两形，原值在前（保序）。 */
    @Test
    public void nonBlankValueYieldsBothRawAndTrimmedForms() {
        assertEquals("带空格的值必须两形都进集合：原值删脏行、trim 形删规范行",
                Arrays.asList(" 9101 ", "9101"),
                StringUtils.actorDeleteForms(" 9101 "));
    }

    /** 已经 trim 过的值两形相同 ⇒ 只一份，不得让 {@code IN} 列表白白翻倍。 */
    @Test
    public void alreadyTrimmedValueCollapsesToASingleForm() {
        assertEquals(Arrays.asList("9101"), StringUtils.actorDeleteForms("9101"));
    }

    /**
     * 跨元素去重：{@code " 9101 "} 与 {@code "9101"} 是<b>同一个人</b>的两种写法（§2.11 归一口径），
     * 并集里只该出现那两形各一次，不得出现四份。
     *
     * <p>⚠️ 去重是按<b>字面</b>做的，不是按"trim 后相同"折叠原值形：{@code "  9101  "}（两个空格）
     * 与 {@code " 9101 "}（一个空格）是<b>两种不同的原值形</b>，都得进集合——库里可能正是其中任一种
     * 脏法，少带一种就删不掉那一行。这一格把两种写法都钉住。</p>
     */
    @Test
    public void duplicateFormsCollapseAcrossElements() {
        assertEquals("同一原值形重复给 ⇒ 折叠成一份",
                Arrays.asList(" 9101 ", "9101"),
                StringUtils.actorDeleteForms(" 9101 ", "9101", "9101", " 9101 "));
        assertEquals("不同原值形（一个空格 / 两个空格）⇒ 各自保留，trim 形仍只一份",
                Arrays.asList(" 9101 ", "9101", "  9101  "),
                StringUtils.actorDeleteForms(" 9101 ", "  9101  "));
    }

    /**
     * 反向哨兵（§2.11 硬要求④）：判据只吃"trim 后为空"，<b>不吃</b>"看起来像空"的正常 id。
     * {@code "0"} 必须留下（php 无回调 {@code array_filter}、python {@code if not x}、
     * js {@code filter(Boolean)} 都会吃掉它），且 {@code "0"} 与 {@code "00"} 是<b>两个不同的人</b>。
     */
    @Test
    public void zeroLikeIdsSurviveAndStayDistinct() {
        List<String> forms = StringUtils.actorDeleteForms("0", "00", " 0 ");
        assertTrue("'0' 是合法 id，不得被当成空值丢掉: " + forms, forms.contains("0"));
        assertTrue("'00' 与 '0' 是两个人，不得折叠: " + forms, forms.contains("00"));
        assertTrue("' 0 ' 的原值形也要在（删未 trim 脏行）: " + forms, forms.contains(" 0 "));
        assertEquals("三个入参 ⇒ 原值形 3 份 ＋ trim 形去重后 2 份（' 0 '→'0' 与既有 '0' 折叠）",
                Arrays.asList("0", "00", " 0 "), forms);
    }

    /** 保序：多个人按入参顺序展开，便于各栈 {@code IN} 列表与日志逐字对照。 */
    @Test
    public void orderIsPreservedAcrossMultipleActors() {
        assertEquals(Arrays.asList("a", " b ", "b", "c"),
                StringUtils.actorDeleteForms("a", " b ", "c"));
    }

    // ═══ 二、仓储删除腿：内存仓 removeTaskActor 确实走了这枚单点 ═══

    /**
     * <b>N 档（假成功修复）</b>：库里躺着修复前落下的未 trim 历史脏行 {@code " 9101 "}，
     * 门面按语义 6 交出<b>行上的原值</b>去删 ⇒ 必须真删掉。
     *
     * <p>只取 trim 形的实现（1.8.36 之前的 php/csharp/rust/moon）在这一格会把 {@code " 9101 "}
     * 削成 {@code 9101}，脏行留在库里、门面报成功——被摘的人待办还在。</p>
     */
    @Test
    public void untrimmedLegacyRowIsDeletedByItsRawForm() {
        repo.seedTaskActorsForTest(TASK, new ArrayList<>(Arrays.asList(" 9101 ", "leader")));

        repo.removeTaskActor(TASK, Arrays.asList(" 9101 "));

        assertEquals("未 trim 的历史脏行必须被原值形删掉（否则是门面报成功的假成功）",
                Arrays.asList("leader"), repo.findTaskActors(TASK));
    }

    /**
     * <b>N 档（142 §9.2 那一路不破）</b>：库里是写侧归一后的规范行 {@code 8601}，
     * 第三方绕过门面直连仓储传 {@code " 8601 "} ⇒ 也必须删得掉（靠 trim 形命中）。
     *
     * <p>这一格是 issues/142 B 批"删除位 trim"的既有判据，<b>并集方案下照绿</b>——
     * 所以本轮不需要反向改任何 142 的既有测试。</p>
     */
    @Test
    public void normalizedRowIsStillDeletedByTrimmedForm() {
        repo.seedTaskActorsForTest(TASK, new ArrayList<>(Arrays.asList("8601", "leader")));

        repo.removeTaskActor(TASK, Arrays.asList(" 8601 "));

        assertEquals("规范行由 trim 形命中（issues/142 §9.2 的既有判据不破）",
                Arrays.asList("leader"), repo.findTaskActors(TASK));
    }

    /** 两形同时在库里（脏行＋规范行并存）⇒ 同一个人名下两行都要摘掉，其余人一行不动。 */
    @Test
    public void bothFormsOfTheSamePersonAreRemovedTogether() {
        repo.seedTaskActorsForTest(TASK,
                new ArrayList<>(Arrays.asList(" 9101 ", "9101", "leader", "boss")));

        repo.removeTaskActor(TASK, Arrays.asList(" 9101 "));

        assertEquals("归一后是同一个人 ⇒ 两行都摘（§2.11 口径），其余参与人原样保留（语义 1）",
                Arrays.asList("leader", "boss"), repo.findTaskActors(TASK));
    }

    /**
     * <b>P 档（脏行保护）</b>：空串／纯空白／{@code null} 入参<b>一律不参与匹配</b>——
     * 历史 {@code actor_id=''} 脏行是待另案清洗的取证痕迹，不得被一次空值入参批量做掉。
     */
    @Test
    public void blankInputNeverDeletesEmptyActorIdDirtyRow() {
        repo.seedTaskActorsForTest(TASK, new ArrayList<>(Arrays.asList("", "   ", "leader")));

        repo.removeTaskActor(TASK, Arrays.asList("", "   ", null));

        assertEquals("空值入参一行都不许删（含历史 actor_id=''/纯空白脏行）",
                Arrays.asList("", "   ", "leader"), repo.findTaskActors(TASK));
    }

    /**
     * <b>P 档（不得退化成清空）</b>：展开后为空 ⇒ 早退，<b>一次删除都不发生</b>。
     * 少了这一条，一次误传空串就会把该任务全部参与者清空，留下永远无人可办的死任务
     * （语义 5「至少需保留一名参与人」的仓储侧对偶）。
     */
    @Test
    public void allBlankInputIsNoOpAndNeverClearsAllActors() {
        repo.seedTaskActorsForTest(TASK, new ArrayList<>(Arrays.asList("zhangsan", "leader")));

        repo.removeTaskActor(TASK, Arrays.asList("", "  "));
        repo.removeTaskActor(TASK, Collections.<String>emptyList());
        repo.removeTaskActor(TASK, null);

        assertEquals("空入参三形（纯空白/空列表/null）都是零删除，不得清空参与者",
                Arrays.asList("zhangsan", "leader"), repo.findTaskActors(TASK));
    }

    /**
     * {@code null} 元素<b>不得</b>被串化成 {@code "null"} 再去删——那会删掉一个真名叫
     * {@code "null"} 的人（§2.11 第 1 行的写侧义务，删除腿同样适用）。
     */
    @Test
    public void nullElementIsNeverStringifiedIntoAMatch() {
        repo.seedTaskActorsForTest(TASK, new ArrayList<>(Arrays.asList("null", "leader")));

        repo.removeTaskActor(TASK, Arrays.asList(null, "leader"));

        assertEquals("null 元素丢弃、不得串化成 \"null\" 参与匹配",
                Arrays.asList("null"), repo.findTaskActors(TASK));
    }

    /** 非参与者静默忽略（语义 7 幂等）：一个都没命中 ⇒ 零删除＋不抛异常。 */
    @Test
    public void unknownActorIsSilentlyIgnored() {
        repo.seedTaskActorsForTest(TASK, new ArrayList<>(Arrays.asList("zhangsan", "leader")));

        repo.removeTaskActor(TASK, Arrays.asList("stranger", " 9999 "));

        assertEquals("非参与者静默忽略，既有参与者一行不动",
                Arrays.asList("zhangsan", "leader"), repo.findTaskActors(TASK));
    }

    /** 任务不存在（无任何参与者行）⇒ 不得抛异常（删除腿对不存在的 taskId 是零操作）。 */
    @Test
    public void unknownTaskIsNoOpWithoutThrowing() {
        repo.removeTaskActor(404404L, Arrays.asList(" 9101 "));

        assertEquals(Collections.emptyList(), repo.findTaskActors(404404L));
    }
}
