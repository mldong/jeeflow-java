package com.mldong.jeeflow.core;

import com.mldong.jeeflow.JeeflowException;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.model.NodeModel;
import com.mldong.jeeflow.model.TaskModel;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 已办撤回（issues/246，规范 04 · 已办撤回）的判据与账本：全部只读/纯函数，
 * 由引擎在聚合变更之前调用——"判据前置、报错即零写入"与后加签写入口同一规矩。
 *
 * @author mldong
 */
public final class RecallSupport {

    /** 节点属性键：已办撤回开关（properties 平铺层，缺省 false） */
    public static final String PROP_ALLOW_RECALL = "allowRecall";

    /** 逐字判据文案（跨栈统一；规范 06 失败文案表随参考实现回填） */
    public static final String MSG_WINDOW = "任务[%s]的后续节点已办理，不能撤回";
    public static final String MSG_SWITCH_OFF = "节点[%s]未开启 allowRecall，不能撤回";
    public static final String MSG_NOT_FINISHED = "任务非已完成状态，不可撤回";
    public static final String MSG_NO_PERMISSION = "无权限撤回该任务";
    public static final String MSG_INSTANCE_NOT_DOING = "流程实例非进行中状态，不可撤回";

    /** 全生态时间格式（规范 §2.4），与 tf_addSignHistory / tf_transferHistory 同尺 */
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private RecallSupport() {
    }

    /**
     * 办理时把当前节点的 allowRecall 开关快照进行变量（issues/251）：已办行置灰的"节点开关"
     * 预判从行上读，查询出口只搬运不解析定义。{@code tf_} 前缀使实例合并自动剥键（不传播，
     * completeTask 立法）；撤回校验仍以现读定义为权威，快照仅供前端预判（漂移为设计内）。
     * 非 TaskModel（start/end/decision 等不办理的节点）与 null args 均静默跳过。
     */
    public static void stampAllowRecall(FlowData args, NodeModel node) {
        if (args == null || !(node instanceof TaskModel)) {
            return;
        }
        args.put(FlowConst.ALLOW_RECALL, ((TaskModel) node).isAllowRecall());
    }

    /**
     * 窗口判据（owner 2026-11-03 拍宽口径）：从 target 沿 task_parent_id 链产出的全部下游行中
     * <b>存在任何 FINISHED</b> ⇒ 报错；否则返回其中仍 DOING 的行（供废弃——后加签/征询成员行、
     * 并行分支多支都自然覆盖；已废弃(99)/已撤回(30) 行既不拦也不重复废弃）。
     */
    public static List<ProcessTask> validateWindow(List<ProcessTask> tasks, ProcessTask target) {
        List<ProcessTask> doing = new ArrayList<>();
        for (ProcessTask t : collectDescendants(tasks, target.getTaskId())) {
            if (t.isFinished()) {
                throw new JeeflowException(String.format(MSG_WINDOW, target.getTaskName()));
            }
            if (t.isDoing()) {
                doing.add(t);
            }
        }
        return doing;
    }

    /** 按 task_parent_id 血缘收集 target 的全部后代行（BFS；血缘在建单不变量里必写，0＝无父）。 */
    private static List<ProcessTask> collectDescendants(List<ProcessTask> tasks, Long ancestorId) {
        List<ProcessTask> result = new ArrayList<>();
        List<Long> frontier = new ArrayList<>();
        frontier.add(ancestorId);
        while (!frontier.isEmpty()) {
            Long parent = frontier.remove(frontier.size() - 1);
            for (ProcessTask t : tasks) {
                if (parent.equals(t.getParentTaskId())) {
                    result.add(t);
                    frontier.add(t.getTaskId());
                }
            }
        }
        return result;
    }

    /**
     * 账本追加（只追加不覆盖），落<b>被撤回的旧行</b>变量；recalledTaskId 指向复活的新行
     * （从历史记录可一步跳到唯一活待办）。{@code tf_} 前缀使退回复活腿天然剔掉它。
     */
    public static void appendRecallHistory(ProcessTask target, String operator, Long resurrectedTaskId) {
        if (target == null || target.getVariables() == null) {
            return;
        }
        FlowData vars = target.getVariables();
        Object existing = vars.get(FlowConst.RECALL_HISTORY);
        List<Map<String, Object>> history;
        if (existing instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cast = (List<Map<String, Object>>) existing;
            history = cast;
        } else {
            history = new ArrayList<>();
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("operator", operator);
        entry.put("time", LocalDateTime.now().format(TIME_FORMAT));
        entry.put("recalledTaskId", resurrectedTaskId);
        history.add(entry);
        vars.put(FlowConst.RECALL_HISTORY, history);
    }
}
