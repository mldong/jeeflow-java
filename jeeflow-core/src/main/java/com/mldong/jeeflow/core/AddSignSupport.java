package com.mldong.jeeflow.core;

import com.mldong.jeeflow.JeeflowException;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.model.NodeModel;
import com.mldong.jeeflow.model.ProcessModel;
import com.mldong.jeeflow.model.TaskModel;
import com.mldong.jeeflow.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后加签（可重复）的配置解析、写入口校验、轮次与账本读写。
 *
 * <p><b>为什么校验一律排在改动状态之前</b>：内存仓储没有事务回滚，一旦先 {@code completeTask}
 * 再判错，那条任务行就在聚合里变成已完成而无人能改回来——"报错且无写入"是这条链的硬要求，
 * 所以 {@link #validateSubmission} 由引擎在 {@code prepareExecution} 里、
 * {@code instance.completeTask(...)} 之前调用，而不是放进 {@code AddSignHandler}。</p>
 *
 * @author mldong
 */
public final class AddSignSupport {

    /** 节点属性键：后加签开关（{@code properties} 平铺层，缺省 false） */
    public static final String PROP_ALLOW_ADD_SIGN = "allowAddSign";
    /** 节点属性键：加签轮次上限（0＝不限，缺省 0） */
    public static final String PROP_ADD_SIGN_MAX_ROUNDS = "addSignMaxRounds";

    /** 逐字判据文案（跨栈统一；规范 06 失败文案表随参考实现回填） */
    public static final String MSG_MUTEX = "tf_addSignOperators 与 tf_nextNodeOperator 互斥，不能同时提交";
    public static final String MSG_SWITCH_OFF = "节点[%s]未开启 allowAddSign，不接受 tf_addSignOperators";
    public static final String MSG_SELF = "不能把任务加签给自己";
    public static final String MSG_MAX_ROUNDS = "节点[%s]的加签轮次已达上限 %d，本次提交被拒绝";

    /** 全生态时间格式（规范 §2.4），与 {@code tf_transferHistory} 同尺 */
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AddSignSupport() {
    }

    /** 解析布尔开关：缺省 false；非布尔报配置错误，不得静默当缺省（allowAddSign/allowRecall 同尺）。 */
    public static boolean parseAllow(Object value, String nodeName, String keyName) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String v = String.valueOf(value).trim();
        if (v.isEmpty()) {
            return false;
        }
        if ("true".equalsIgnoreCase(v)) {
            return true;
        }
        if ("false".equalsIgnoreCase(v)) {
            return false;
        }
        throw new JeeflowException("节点[" + nodeName + "]的 " + keyName + " 配置非法: " + v);
    }

    /** 解析 {@code addSignMaxRounds}：缺省 0＝不限；非整数或负数报配置错误。 */
    public static int parseMaxRounds(Object value, String nodeName) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            int n = ((Number) value).intValue();
            if (((Number) value).doubleValue() != n) {
                throw new JeeflowException("节点[" + nodeName + "]的 " + PROP_ADD_SIGN_MAX_ROUNDS
                        + " 配置非法: " + value + "（必须是整数）");
            }
            if (n < 0) {
                throw new JeeflowException("节点[" + nodeName + "]的 " + PROP_ADD_SIGN_MAX_ROUNDS
                        + " 配置非法: " + n + "（不得为负，0 表示不限）");
            }
            return n;
        }
        String v = String.valueOf(value).trim();
        if (v.isEmpty()) {
            return 0;
        }
        int n;
        try {
            n = Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new JeeflowException("节点[" + nodeName + "]的 " + PROP_ADD_SIGN_MAX_ROUNDS
                    + " 配置非法: " + v + "（必须是整数，0 表示不限）");
        }
        if (n < 0) {
            throw new JeeflowException("节点[" + nodeName + "]的 " + PROP_ADD_SIGN_MAX_ROUNDS
                    + " 配置非法: " + n + "（不得为负，0 表示不限）");
        }
        return n;
    }

    /** 本次提交指定的加签人（逗号串与数组两形共用 §2.11 那枚归一单点；没点人返回空表）。 */
    public static List<String> operators(FlowData args) {
        if (args == null) {
            return new ArrayList<>();
        }
        return StringUtils.normalizeActorArg(args.get(FlowConst.ADD_SIGN_OPERATORS));
    }

    /**
     * 写入口校验（互斥／开关未开／加给自己／超上限）。必须在改动任何状态之前调用。
     *
     * <p>互斥这一条的理由不是语义矛盾（一个管本节点再来一轮、一个管流出后交给谁，逻辑可共存），
     * 而是<b>参数生命周期</b>：{@code tf_*} 键只在当次请求的执行参数与已完成任务行变量里可读，
     * 加签使令牌停留本节点、流出发生在后续请求，届时先传的 {@code tf_nextNodeOperator} 已不可读
     * ——同传时它是死参数。静默择一＝用户选的"下一节点处理人"无声消失，报错是把它变响话。</p>
     */
    public static void validateSubmission(ProcessModel model, ProcessTask task, FlowData args, String operator) {
        if (args == null || task == null) {
            return;
        }
        List<String> ops = operators(args);
        Object nextOp = args.get(FlowConst.NEXT_NODE_OPERATOR);
        boolean nextPresent = nextOp != null && !String.valueOf(nextOp).trim().isEmpty();
        if (!ops.isEmpty() && nextPresent) {
            throw new JeeflowException(MSG_MUTEX);
        }
        if (ops.isEmpty()) {
            // 没点人＝正常流转档，其余判据不涉及（也不要求节点开开关）
            return;
        }
        NodeModel node = model == null ? null : model.getNode(task.getTaskName());
        if (!(node instanceof TaskModel) || !((TaskModel) node).isAllowAddSign()) {
            throw new JeeflowException(String.format(MSG_SWITCH_OFF, task.getTaskName()));
        }
        TaskModel tm = (TaskModel) node;
        String me = StringUtils.normalizeActor(operator);
        for (String actor : ops) {
            if (me != null && me.equals(actor)) {
                throw new JeeflowException(MSG_SELF);
            }
        }
        int max = tm.getAddSignMaxRounds();
        if (max > 0 && roundOf(task, tm.getName()) + 1 > max) {
            throw new JeeflowException(String.format(MSG_MAX_ROUNDS, tm.getName(), max));
        }
    }

    /** 本节点是否还有未完成任务（刚办结那条此刻已是 FINISHED，不计入）。 */
    public static boolean hasDoingOnNode(ProcessInstance instance, String nodeName) {
        if (instance == null || nodeName == null) {
            return false;
        }
        for (ProcessTask t : instance.getTasks()) {
            if (nodeName.equals(t.getTaskName()) && t.isDoing()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 废弃本节点未完成的加签任务（拒绝／退回发起人时调用，不留孤儿待办）。
     * 只对开了 {@code allowAddSign} 或 {@code dynamicCountersign} 的节点动手（issues/247：
     * 征询成员行同属本节点 DOING，实例撤回/退回路径一并清场）。
     *
     * @return 实际废弃的行数
     */
    public static int abandonDoingOnNode(ProcessModel model, ProcessInstance instance,
                                         String nodeName, String operator) {
        if (model == null || instance == null || nodeName == null) {
            return 0;
        }
        NodeModel node = model.getNode(nodeName);
        if (!(node instanceof TaskModel) || (!((TaskModel) node).isAllowAddSign()
                && !((TaskModel) node).isDynamicCountersign())) {
            return 0;
        }
        int abandoned = 0;
        for (ProcessTask t : instance.getTasks()) {
            if (nodeName.equals(t.getTaskName()) && t.isDoing()) {
                t.abandon(operator);
                abandoned++;
            }
        }
        return abandoned;
    }

    /** 读轮次号：首圈（普通建单产生的那条）没有该键 ⇒ 0。 */
    public static int roundOf(ProcessTask task, String nodeName) {
        if (task == null || task.getVariables() == null) {
            return 0;
        }
        Integer v = task.getVariables().getInt(FlowConst.ADD_SIGN_ROUND + "_" + nodeName);
        return v == null ? 0 : v;
    }

    /** 账本追加（只追加不覆盖），落已完成任务行变量；{@code tf_} 前缀使退回复活腿天然剔掉它。 */
    public static void appendHistory(ProcessTask completed, String operator,
                                     List<String> toActors, int round) {
        if (completed == null) {
            return;
        }
        FlowData vars = completed.getVariables();
        if (vars == null) {
            return;
        }
        Object existing = vars.get(FlowConst.ADD_SIGN_HISTORY);
        List<Map<String, Object>> history;
        if (existing instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cast = (List<Map<String, Object>>) existing;
            history = cast;
        } else {
            history = new ArrayList<>();
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("round", round);
        entry.put("fromActor", operator);
        entry.put("toActors", new ArrayList<>(toActors));
        entry.put("time", LocalDateTime.now().format(TIME_FORMAT));
        entry.put("operator", operator);
        history.add(entry);
        vars.put(FlowConst.ADD_SIGN_HISTORY, history);
    }

    /** 读账本（测试与投影用）。 */
    public static List<Map<String, Object>> historyOf(ProcessTask task) {
        if (task == null || task.getVariables() == null) {
            return new ArrayList<>();
        }
        Object v = task.getVariables().get(FlowConst.ADD_SIGN_HISTORY);
        if (v instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cast = (List<Map<String, Object>>) v;
            return cast;
        }
        return new ArrayList<>();
    }
}
