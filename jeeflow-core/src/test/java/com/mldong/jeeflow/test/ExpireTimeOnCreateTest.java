package com.mldong.jeeflow.test;

import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.model.TaskModel;
import com.mldong.jeeflow.util.FlowUtil;
import org.junit.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * issues/126 案 A · 任务行 expire_time 由**建单路径**按节点到期表达式写。
 *
 * <p>基准取 boot2 内置版 {@code ProcessTaskServiceImpl} 的三处写（:213 普通建单 / :386 回退新建 /
 * :524 会签建单），三处都是 {@code FlowUtil.processTime(node.getExpireTime(), args)}；
 * 节点没配就留 NULL（owner 2026-09-28：不造默认值）。
 *
 * <p>本栈原形状是 {@code ProcessInstance} 里那句占位
 * {@code task.setExpireTime(LocalDateTime.now()); // will be overridden by util later}
 * ——注释承诺的 util 从来没跑过，于是"配了到期表达式的节点"建单一瞬就成了**已逾期**（差值≈0），
 * 逾期类统计在常规流上全失真。跨栈判据在门禁 L2-27（同一行内 expire − create 必须≈表达式偏移）。
 */
public class ExpireTimeOnCreateTest {

    private static ProcessInstance instance() {
        ProcessInstance inst = new ProcessInstance();
        inst.setInstanceId(9001L);
        return inst;
    }

    private static TaskModel node(String name, String expireTime) {
        TaskModel model = new TaskModel();
        model.setName(name);
        model.setDisplayName(name);
        model.setExpireTime(expireTime);
        return model;
    }

    private static List<String> one() {
        return new ArrayList<>(Collections.singletonList("u1"));
    }

    /** 正向①：配 "2h" ⇒ 到期时间＝建单那一刻 + 2 小时（不是 now，也不是 0 点） */
    @Test
    public void relativeExpressionIsAppliedAtCreation() {
        ProcessTask task = instance().createTask(node("approve", "2h"), "审批", one(), "op", 0L, true);
        assertNotNull("配了到期表达式的行必须带 expire_time", task.getExpireTime());
        long delta = Duration.between(task.getCreateTime(), task.getExpireTime()).getSeconds();
        // 下界取 7195 而不是 7200：FlowUtil 的 toLocalDateTime(Date) 落在**秒级**（毫秒被抹平），
        // 而行上 createTime 带毫秒 ⇒ 差值实测 7199.x s。这不是缺陷，只是量纲；
        // 要照出的病灶是占位写法算出的 ≈0s（建单即逾期），带宽 5s/60s 足够把它夹在外面。
        assertTrue("差值应≈2h（实得 " + delta + "s）；占位写法会算出≈0 而把新建任务判成已逾期",
                delta >= 2 * 3600L - 5L && delta <= 2 * 3600L + 60L);
    }

    /** 正向②：表达式是个变量名 ⇒ 取该变量的值当到期时间（FlowUtil.processTime 第一档） */
    @Test
    public void expressionNamingAVariableTakesItsValue() {
        ProcessInstance inst = instance();
        Map<String, Object> seed = new HashMap<>();
        seed.put("dueAt", "2026-12-31 10:00:00");
        inst.addVariable(FlowData.of(seed));

        ProcessTask task = inst.createTask(node("approve", "dueAt"), "审批", one(), "op", 0L, true);
        assertEquals(LocalDateTime.of(2026, 12, 31, 10, 0, 0), task.getExpireTime());
    }

    /** 负向①：节点没配 ⇒ 这一列必须留 NULL，不许造默认值（含不许写 now()） */
    @Test
    public void unconfiguredNodeKeepsColumnNull() {
        ProcessTask task = instance().createTask(node("approve", null), "审批", one(), "op", 0L, true);
        assertNull("未配到期表达式的行不得被赋任何时间", task.getExpireTime());

        ProcessTask blank = instance().createTask(node("approve", ""), "审批", one(), "op", 0L, true);
        assertNull("空串同样算没配", blank.getExpireTime());
    }

    /** 负向②：表达式解析不出来 ⇒ NULL，而不是退回成 now()（那等于静默造一个"建单即逾期"的值） */
    @Test
    public void unparsableExpressionStaysNull() {
        ProcessTask task = instance().createTask(node("approve", "not-a-time"), "审批", one(), "op", 0L, true);
        assertNull(task.getExpireTime());
    }

    /**
     * 负向③（issues/137 D，owner 2026-10-01 拍"判非负"）：负数相对档不是合法偏移。
     *
     * <p>放行 {@code -5h} 会算出一个<b>过去</b>的时刻 ⇒ 新建的行当场即逾期，比"没配到期时间"更难发现；
     * 与本卡"算不出就 NULL、绝不退化成 now"的精神同向。{@code d} 档同病（走
     * {@code Calendar.add(DAY_OF_MONTH, -5)}，是历日倒退不是乘 86400）⇒ 两档各自钉一格。
     * 加号档保持合法：各栈整数解析（python {@code [+-]?}、node {@code [-+]?\d+}、php
     * {@code [+-]?\d{1,18}}）都收 '+'，只裁负不裁加，免得新造一处跨栈分叉。</p>
     */
    @Test
    public void negativeRelativeExpressionStaysNull() {
        ProcessTask minusHours = instance().createTask(node("approve", "-5h"), "审批", one(), "op", 0L, true);
        assertNull("负数时档必须落穿 ⇒ NULL（放行即建单即逾期）", minusHours.getExpireTime());

        ProcessTask minusDays = instance().createTask(node("approve", "-5d"), "审批", one(), "op", 0L, true);
        assertNull("负数天档同样落穿（日历加天会倒退五天）", minusDays.getExpireTime());

        ProcessTask minusSeconds = instance().createTask(node("approve", "-30s"), "审批", one(), "op", 0L, true);
        assertNull("负数秒档同样落穿", minusSeconds.getExpireTime());

        ProcessTask plusHours = instance().createTask(node("approve", "+2h"), "审批", one(), "op", 0L, true);
        assertNotNull("正向对照：加号档仍合法（本格保证上面三判不是恒真）", plusHours.getExpireTime());
    }

    /**
     * issues/137 E：相对档**前缀**的两端空白必须裁掉（java 是本条最后一个没裁的栈）。
     *
     * <p>各栈整数解析对空白的容忍度天然不同：go 在 {@code Atoi} 前显式 {@code TrimSpace}、rust
     * {@code .trim()}、.NET {@code int.TryParse} 与 python {@code int()} 默认就收前后空白，
     * 而 java {@code Integer.parseInt(" 2")} 直接抛 ⇒ 同一份流程定义"别家有到期时间、java 没有"。
     * 裁的位置只在前缀，**单位符与表达式末尾的空白不动**：{@code "2h "} 末位是空格、认不出单位，
     * 仍按误配落穿（把整串去空白是另一件没立过法的事）。</p>
     */
    @Test
    public void paddedRelativePrefixStillApplies() {
        ProcessTask leading = instance().createTask(node("approve", " 2h"), "审批", one(), "op", 0L, true);
        assertNotNull("前缀带一个空格的 2h 必须照样算得出（java 旧形状在这里抛 NFE ⇒ NULL）",
                leading.getExpireTime());
        long delta = Duration.between(leading.getCreateTime(), leading.getExpireTime()).getSeconds();
        assertTrue("带空前缀的差值仍应≈2h（实得 " + delta + "s）",
                delta >= 2 * 3600L - 5L && delta <= 2 * 3600L + 60L);

        ProcessTask tabPlus = instance().createTask(node("approve", "	+2h "), "审批", one(), "op", 0L, true);
        assertNull("单位符后面还带空格 ⇒ 末位不是 h，认不出单位，仍按误配落穿成 NULL（实得 "
                + tabPlus.getExpireTime() + "）", tabPlus.getExpireTime());

        // "2 h" 里那个空格**落在前缀区内**（末位仍是单位符 h）⇒ 属"前缀带空白"同一档，与 go 的
        // `TrimSpace(expr[:len-1])` 同解。真正的分界线是单位符后面：`"2h "` 末位是空格、认不出单位。
        ProcessTask inside = instance().createTask(node("approve", "2 h"), "审批", one(), "op", 0L, true);
        assertNotNull("前缀区内的空白（数字与单位符之间）同样要裁掉——裁的是前缀，不是整串",
                inside.getExpireTime());
        assertTrue("且算出的仍是 2h 量（实得 " + inside.getExpireTime() + "）",
                Duration.between(inside.getCreateTime(), inside.getExpireTime()).getSeconds()
                        >= 2 * 3600L - 5L);

        ProcessTask stillBad = instance().createTask(node("approve", " 2.5h"), "审批", one(), "op", 0L, true);
        assertNull("trim 之后照样是误配（小数）⇒ 仍落穿，别把裁空白做成裁容错", stillBad.getExpireTime());
    }

    /**
     * 并行会签全员（案文 §1.8 写点③）：每个成员行都必须带到期时间。
     *
     * <p>这一格是补 csharp 执行者报出的缺口——它说本栈"并行全员"无专测格，摘掉那处没有格会红；
     * 一查 java 参考实现同样只有四格形状（串行首成员/推进位在 JeeflowFacadeTest 里，并行没人钉）。
     * 参考实现自己没覆盖的分支，等于给七栈派活时长老自己缺一块证据，故在这里补上。
     */
    @Test
    public void parallelCountersignEveryMemberGetsExpire() {
        TaskModel model = node("cs", "2h");
        model.setPerformType(com.mldong.jeeflow.enums.ProcessTaskPerformTypeEnum.COUNTERSIGN);
        model.setCountersignType(com.mldong.jeeflow.enums.CountersignTypeEnum.PARALLEL);
        List<String> actors = new ArrayList<>();
        actors.add("u1");
        actors.add("u2");
        actors.add("u3");

        ProcessInstance inst = instance();
        List<ProcessTask> tasks = inst.createCountersignTasks(model, actors, "op", 0L, true);
        assertEquals("并行会签应为每位成员各建一行", 3, tasks.size());
        for (ProcessTask task : tasks) {
            assertNotNull("成员 " + task.getActorIds() + " 的行必须带到期时间", task.getExpireTime());
            long delta = Duration.between(task.getCreateTime(), task.getExpireTime()).getSeconds();
            assertTrue("成员 " + task.getActorIds() + " 的 expire − create 应≈2h（实得 " + delta + "s）",
                    delta >= 2 * 3600L - 5L && delta <= 2 * 3600L + 60L);
        }
    }

    /** 同一分支的负向档：并行会签且节点没配到期表达式 ⇒ 三行都留空 */
    @Test
    public void parallelCountersignUnconfiguredKeepsExpireNull() {
        TaskModel model = node("cs", null);
        model.setPerformType(com.mldong.jeeflow.enums.ProcessTaskPerformTypeEnum.COUNTERSIGN);
        model.setCountersignType(com.mldong.jeeflow.enums.CountersignTypeEnum.PARALLEL);
        List<String> actors = new ArrayList<>();
        actors.add("u1");
        actors.add("u2");
        ProcessInstance inst = instance();
        List<ProcessTask> tasks = inst.createCountersignTasks(model, actors, "op", 0L, true);
        assertEquals(2, tasks.size());
        for (ProcessTask task : tasks) {
            assertNotNull("行本身要读到，且 createTime 有值（内部对照）", task.getCreateTime());
            assertNull("并行会签未配到期表达式的成员行不该有到期时间", task.getExpireTime());
        }
    }

    // ═══ issues/137 C（2026-09-29 owner 拍 · spec 04 §任务行 expire_time）：
    //     **误配**的相对档（写坏的前缀/后缀）不得抛异常打断建单——落穿到绝对时刻档、
    //     解析不出即 NULL。改前 java 参考实现在 FlowUtil.processTime 里 Integer.parseInt 直抛
    //     NumberFormatException（其余七栈一律落穿），于是"节点属性配错＝流程炸掉"。
    //     两格负向各自带"行必须建出来"的对照，防止把"建单失败"读成"值为空"；
    //     正向回归钉四个后缀都还算得出来，防止把求值器改成恒 NULL 的哑器。═══

    /**
     * 误配档①②③（行级）：`xh`（前缀非整数）/ `2.5h`（小数）/ `3hh`（前缀还带字母）
     * ⇒ 建单照常成功，那一列落穿成 NULL。
     *
     * <p>改前这里三条全都抛 NumberFormatException（createTask 直接炸，测试以异常红）；
     * 不许退化成取当前时间（那等于建单即逾期，比不写更难发现），也不许写 0。
     */
    @Test
    public void misconfiguredRelativeExpressionFallsThroughToNull() {
        for (String expr : new String[]{"xh", "2.5h", "3hh"}) {
            ProcessTask task = instance().createTask(node("approve", expr), "审批", one(), "op", 0L, true);
            assertNotNull("误配表达式 " + expr + " 也要照常建出任务行（建单不得失败）", task.getCreateTime());
            assertNull("误配表达式 " + expr + " 应落穿成 NULL，不得抛错、不得取当前时间、不得取 0",
                    task.getExpireTime());
        }
    }

    /**
     * 误配档①②③（求值器级）：直调 {@link FlowUtil#processTime} 不得抛，三档全 NULL。
     *
     * <p>和上一格分开钉：上一格走建单腿（ProcessInstance.applyExpireTime 会把 null/空串短路掉），
     * 这一格证明抛点就在求值器本身——实例级到期时间（JeeflowEngineImpl 的 processTime 调用）
     * 与任务行共用同一把尺子，那条腿同样不得炸。
     */
    @Test
    public void evaluatorDoesNotThrowOnMisconfiguredRelativeExpression() {
        for (String expr : new String[]{"xh", "2.5h", "3hh"}) {
            assertNull("求值器对误配表达式 " + expr + " 应返回 null（不抛）",
                    FlowUtil.processTime(expr, FlowData.create()));
        }
    }

    /**
     * 正向回归：四个合法相对档后缀（s/m/h/d）依然各算各的偏移——误配落穿不是把求值器改哑。
     *
     * <p>{@code 2h} 就是本栈 spec 的基准样例（create + 7200s）；四档全钉是因为修复动了四个分支，
     * 任何一支的守卫写错（比如把 null 判成 0）都会让对应后缀算成 0s 或 NULL 而红。
     */
    @Test
    public void everyValidRelativeSuffixStillComputesItsOffset() {
        assertEvaluatorOffset("2h", 2 * 3600L);
        assertEvaluatorOffset("90m", 90 * 60L);
        assertEvaluatorOffset("45s", 45L);
        assertEvaluatorOffset("3d", 3 * 24 * 3600L);
    }

    /**
     * 边界档（同一处病灶的第四种写法）：只有后缀、前缀是空串（`h`）——改前 parseInt("") 同样抛。
     * 裁定口径一致：落穿 → 绝对档解析不出 → NULL。
     */
    @Test
    public void suffixOnlyExpressionFallsThroughToNull() {
        for (String expr : new String[]{"h", "s", "m", "d"}) {
            assertNull("光有后缀的表达式 " + expr + " 属误配，应落穿成 NULL 且不抛",
                    FlowUtil.processTime(expr, FlowData.create()));
        }
    }

    private static void assertEvaluatorOffset(String expr, long expectedSeconds) {
        LocalDateTime before = LocalDateTime.now();
        LocalDateTime expire = FlowUtil.processTime(expr, FlowData.create());
        assertNotNull(expr + " 是合法相对档，必须算出到期时间（算成 NULL 就是把求值器改哑了）", expire);
        long delta = Duration.between(before, expire).getSeconds();
        // 带宽 −5/+60：toLocalDateTime(Date) 落在秒级（毫秒被抹平）而 before 取在求值之前，
        // 差值天然少 0~1s；合法档算成 0 或 NULL 才是病灶。
        assertTrue(expr + " 的偏移应≈" + expectedSeconds + "s（实得 " + delta + "s）",
                delta >= expectedSeconds - 5L && delta <= expectedSeconds + 60L);
    }
}
