package com.mldong.jeeflow.test;

import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.model.TaskModel;
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
}
