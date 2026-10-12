package com.mldong.jeeflow.test;

import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.ProcessInstanceStateEnum;
import com.mldong.jeeflow.enums.ProcessTaskStateEnum;
import com.mldong.jeeflow.model.TaskModel;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * issues/180 · abandonTask 摘副作用（owner 2026-10-13 拍板 A）——聚合根级。
 *
 * <p>缺陷：abandonTask 把「任务废弃」连带写成「流程实例也置 99(ABANDON)」，spec/04:329 只写
 * 「废弃单个任务」，go/py/node 三栈实例状态不动（对照形状），boot2/boot3 原版全仓零命中
 * （非产品血统、五栈自创）。修复＝删实例 state 写点；任务级废弃与实例元数据（updateTime/
 * updateUser）照旧。</p>
 *
 * <p>判据：① 正向——任务落 99 且实例 state 仍 10；② 负向——非进行中任务照旧抛；
 * ③ 回归——其余任务行不动。</p>
 */
public class AbandonTaskInstanceState180Test {

    private ProcessInstance doingInstance() {
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setId(9527L);
        def.setName("180-abandon");
        ProcessInstance inst = ProcessInstance.create(def, "zhangsan", FlowData.create());
        inst.setInstanceId(9001L);
        TaskModel model = new TaskModel();
        model.setName("task1");
        model.setDisplayName("审批");
        ProcessTask task = inst.createTask(model, "审批",
                new ArrayList<>(Collections.singletonList("leader")),
                "zhangsan", 0L, false);
        task.setTaskId(9527L);
        return inst;
    }

    /** ① 正向：废弃后任务=99、实例 state 仍 10（改前被连带置 99——issues/180 病灶）。 */
    @Test
    public void test180AbandonKeepsInstanceState() {
        ProcessInstance inst = doingInstance();
        assertEquals("夹具前提：实例进行中",
                ProcessInstanceStateEnum.DOING.getCode(), inst.getState());
        inst.abandonTask(9527L, "user9");
        assertEquals("任务应落 99(ABANDON)",
                ProcessTaskStateEnum.ABANDON.getCode(), inst.getTasks().get(0).getTaskState());
        assertEquals("实例 state 不得被连带置 99（issues/180：对齐 spec 04 与 go/py/node）",
                ProcessInstanceStateEnum.DOING.getCode(), inst.getState());
    }

    /** ② 负向：非进行中任务照旧拒（夹具先办结），实例态不动。 */
    @Test
    public void test180AbandonRejectsNonDoingTask() {
        ProcessInstance inst = doingInstance();
        inst.getTasks().get(0).finish("leader", null);
        try {
            inst.abandonTask(9527L, "user9");
            fail("非进行中任务废弃必须抛（保持原守卫，不得静默放行）");
        } catch (RuntimeException expected) {
            // 既有文案：任务[x]不是进行中状态
        }
        assertEquals("被拒后实例 state 不得动",
                ProcessInstanceStateEnum.DOING.getCode(), inst.getState());
    }

    /** ③ 回归：只废目标任务，其余行不动。 */
    @Test
    public void test180AbandonLeavesOtherRowsUntouched() {
        ProcessInstance inst = doingInstance();
        TaskModel model2 = new TaskModel();
        model2.setName("task2");
        model2.setDisplayName("审批2");
        ProcessTask t2 = inst.createTask(model2, "审批2",
                new ArrayList<>(Collections.singletonList("leader")),
                "zhangsan", 0L, false);
        t2.setTaskId(9528L);
        inst.abandonTask(9527L, "user9");
        assertEquals(ProcessTaskStateEnum.ABANDON.getCode(), inst.getTasks().get(0).getTaskState());
        assertEquals("其余任务行不得被波及",
                ProcessTaskStateEnum.DOING.getCode(), inst.getTasks().get(1).getTaskState());
    }
}
