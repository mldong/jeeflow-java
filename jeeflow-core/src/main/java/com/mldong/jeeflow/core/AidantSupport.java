package com.mldong.jeeflow.core;

import com.mldong.jeeflow.JeeflowException;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessTaskTypeEnum;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 动态协办（issues/253，规范 04 · 动态协办）的判据、账本与投影：全部只读/纯函数，
 * 由引擎在聚合变更之前调用——"判据前置、报错即零写入"与后加签/征询/撤回写入口同一规矩。
 *
 * <p><b>与征询（{@link CollectSupport}）的分辨判据＝{@code task_type}</b>：两族子行都是
 * parent 指向主办行＋同节点同名，协办行 {@code task_type=1}、征询成员行 {@code task_type=0}。
 * 等待闸只数协办行、征询收齐闸只数成员行，各数各的（规范 04「与征询允许同节点并存」）。</p>
 *
 * <p><b>无节点开关</b>：协办档只由运行期 {@code processTask/aidant} 派生，节点级 {@code taskType=1}
 * 解析期禁配（issues/252，见 {@code TaskParser}）。</p>
 *
 * @author mldong
 */
public final class AidantSupport {

    /** 逐字判据文案（跨栈统一；规范 06 失败文案表「协办六行」） */
    public static final String MSG_NO_ACTORS = "actorIds 必填";
    public static final String MSG_NO_PERMISSION = "无权限派生协办任务";
    public static final String MSG_NOT_DOING = "任务非进行中，不可派生协办";
    public static final String MSG_DUPLICATE = "该人已有未办结的协办任务";
    public static final String MSG_NO_RECURSION = "协办任务不能再派生协办";
    public static final String MSG_WAIT_PENDING = "节点[%s]协办未办结（已办结 %d/%d），不能办理";
    public static final String MSG_TASK_NOT_FOUND = "任务不存在";

    /** 全生态时间格式（规范 §2.4），与 tf_addSignHistory / tf_transferHistory / tf_recallHistory 同尺 */
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AidantSupport() {
    }

    /** 协办行判定＝{@code task_type=1}（SECONDARY）。这是两族子行唯一的分辨判据。 */
    public static boolean isAidantRow(ProcessTask row) {
        return row != null && ProcessTaskTypeEnum.SECONDARY.equals(row.getTaskType());
    }

    /**
     * 协办行＝parent 指向主办行<b>且同节点同名且 task_type=1</b> 的行。
     *
     * <p>同名过滤不可省（照 {@link CollectSupport#memberRows} 的理由）：主办办结推进后，
     * 下一节点行的 parent 同样是主办行 id（建单不变量），只认 parent 会把下游行误当协办行。
     * {@code task_type} 过滤同样不可省：征询成员行也满足 parent＋同名，混进来会让等待闸
     * 把"意见未收齐"误报成"协办未办结"。</p>
     */
    public static List<ProcessTask> aidantRows(List<ProcessTask> tasks, ProcessTask host) {
        List<ProcessTask> out = new ArrayList<>();
        if (tasks == null || host == null || host.getTaskId() == null) {
            return out;
        }
        for (ProcessTask t : tasks) {
            if (isAidantRow(t)
                    && host.getTaskId().equals(t.getParentTaskId())
                    && host.getTaskName() != null && host.getTaskName().equals(t.getTaskName())) {
                out.add(t);
            }
        }
        return out;
    }

    /**
     * 等待闸原料：在册协办行总数与已办结（FINISHED）数——已废弃(99) 行（离开节点清场的）
     * 不算在册（规范 04「计数跳过已废弃(99) 行」）。
     */
    public static int[] countAidant(List<ProcessTask> tasks, ProcessTask host) {
        int total = 0;
        int submitted = 0;
        for (ProcessTask t : aidantRows(tasks, host)) {
            if (Integer.valueOf(99).equals(t.getTaskState())) {
                continue;
            }
            total++;
            if (t.isFinished()) {
                submitted++;
            }
        }
        return new int[]{total, submitted};
    }

    /** 未办结协办行（DOING）——等待闸的判据集合，也是离开节点时的清场集合。 */
    public static List<ProcessTask> pendingAidant(List<ProcessTask> tasks, ProcessTask host) {
        List<ProcessTask> out = new ArrayList<>();
        for (ProcessTask t : aidantRows(tasks, host)) {
            if (t.isDoing()) {
                out.add(t);
            }
        }
        return out;
    }

    /**
     * 等待闸开关（{@code waitCompletion}）：派生时盖在<b>主办行</b>变量上，缺省 false＝不等。
     * 多轮派生时后一次的值生效（投影 {@code ext.aidant.wait} 同源）。
     */
    public static boolean waitOf(ProcessTask host) {
        if (host == null || host.getVariables() == null) {
            return false;
        }
        Object v = host.getVariables().get(FlowConst.AIDANT_WAIT);
        return v != null && Boolean.parseBoolean(String.valueOf(v));
    }

    /**
     * 等待闸（规范 04「推进闸与等待闸」）：{@code waitCompletion=true} 时主办行办结前判
     * "不存在 DOING 协办行"，未齐 ⇒ 逐字文案报错、零写入。协办行自身不受本闸约束
     * （它办结本来就不驱动流程）；{@code waitCompletion=false}（缺省）原样放行。
     *
     * <p>必须由引擎排在 {@code ProcessInstance#completeTask} 之前——内存仓储无回滚，
     * 判据后置时"报错"那一次调用结束后主办待办已经消失（与后加签/退回守卫同一规矩）。</p>
     */
    public static void gateWait(List<ProcessTask> tasks, ProcessTask host) {
        if (host == null || isAidantRow(host) || !waitOf(host)) {
            return;
        }
        if (pendingAidant(tasks, host).isEmpty()) {
            return;
        }
        int[] count = countAidant(tasks, host);
        throw new JeeflowException(String.format(MSG_WAIT_PENDING, host.getTaskName(), count[1], count[0]));
    }

    /**
     * 离开节点时清场未办结协办行（规范 04：{@code waitCompletion=false} 时主办可随时推进，
     * 未办结协办行随废弃腿清场、账本记废弃，不留孤儿待办）。
     *
     * <p>只对协办行（{@code task_type=1}）动手，且<b>不要求节点开任何开关</b>——协办无节点开关，
     * 普通节点上的协办行同样要清。按 parent 分组，废弃事实追加到各自主办行的账本上。
     * 幂等：已废弃(99)/已办结(20) 的行不再命中，重复调用不重复记账。</p>
     *
     * @return 实际废弃的行数
     */
    public static int abandonPendingOnNode(ProcessInstance instance, String nodeName, String operator) {
        if (instance == null || nodeName == null || instance.getTasks() == null) {
            return 0;
        }
        // parent → 该主办行下待清场的协办行（LinkedHashMap 锁定记账顺序＝行序）
        Map<Long, List<ProcessTask>> byHost = new LinkedHashMap<>();
        for (ProcessTask t : instance.getTasks()) {
            if (nodeName.equals(t.getTaskName()) && t.isDoing() && isAidantRow(t)) {
                Long parent = t.getParentTaskId();
                List<ProcessTask> bucket = byHost.get(parent);
                if (bucket == null) {
                    bucket = new ArrayList<>();
                    byHost.put(parent, bucket);
                }
                bucket.add(t);
            }
        }
        int abandoned = 0;
        for (Map.Entry<Long, List<ProcessTask>> e : byHost.entrySet()) {
            List<String> actors = new ArrayList<>();
            for (ProcessTask t : e.getValue()) {
                t.abandon(operator);
                actors.add(firstActor(t));
                abandoned++;
            }
            ProcessTask host = findTask(instance, e.getKey());
            if (host != null) {
                appendAbandonHistory(host, operator, actors, lastRound(host));
            }
        }
        return abandoned;
    }

    /** 投影（规范 06 §2.7）：主办行 ext.aidant＝{active, round, total, submitted, actors, wait}；无协办行 ⇒ null。 */
    public static Map<String, Object> buildProjection(List<ProcessTask> tasks, ProcessTask host) {
        List<ProcessTask> rows = aidantRows(tasks, host);
        if (rows.isEmpty()) {
            return null;
        }
        int total = 0;
        int submitted = 0;
        List<Map<String, Object>> actors = new ArrayList<>();
        for (ProcessTask t : rows) {
            if (Integer.valueOf(99).equals(t.getTaskState())) {
                continue; // 清场的行不进投影（与征询投影同口径）
            }
            total++;
            if (t.isFinished()) {
                submitted++;
            }
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("actor", firstActor(t));
            a.put("taskId", t.getTaskId());
            a.put("state", t.getTaskState());
            actors.add(a);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("active", submitted < total);
        out.put("round", lastRound(host));
        out.put("total", total);
        out.put("submitted", submitted);
        out.put("actors", actors);
        out.put("wait", waitOf(host));
        return out;
    }

    /** 主办行当前轮次＝账本条数（每次派生追加一条＝轮次推进；清场记账不消耗轮次）。 */
    public static int lastRound(ProcessTask host) {
        List<Map<String, Object>> history = historyOf(host);
        for (int i = history.size() - 1; i >= 0; i--) {
            Object r = history.get(i).get("round");
            if (r instanceof Number) {
                return ((Number) r).intValue();
            }
        }
        return 0;
    }

    /**
     * 派生账本追加（只追加不覆盖），落<b>主办行</b>变量；键序锁定
     * {@code {round, fromActor, toActors, reason, time, operator}}（规范 06 §2.7），
     * {@code reason} 空串兜底、{@code time} 一律 §2.4 字符串。
     *
     * <p>主办行在派生时不办结，{@code ProcessTask#finish} 的 {@code variables.putAll(args)}
     * 是合并而非整体替换，故账本能活到主办办结之后（与转办/征询账本同一条存活理由）。</p>
     */
    public static void appendAidantHistory(ProcessTask host, String operator,
                                           List<String> toActors, int round, String reason) {
        append(host, baseEntry(operator, toActors, round, reason));
    }

    /**
     * 清场账本追加：{@code abandoned=true} 标记本条是"离开节点废弃"（形状照征询撤回条目的
     * {@code revoked=true}），六键原序不动，{@code toActors}＝被废弃的人。
     */
    public static void appendAbandonHistory(ProcessTask host, String operator,
                                            List<String> abandonedActors, int round) {
        Map<String, Object> entry = baseEntry(operator, abandonedActors, round, "");
        entry.put("abandoned", true);
        append(host, entry);
    }

    /** 读账本（测试与投影用）。 */
    public static List<Map<String, Object>> historyOf(ProcessTask task) {
        if (task == null || task.getVariables() == null) {
            return new ArrayList<>();
        }
        Object v = task.getVariables().get(FlowConst.AIDANT_HISTORY);
        if (v instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cast = (List<Map<String, Object>>) v;
            return cast;
        }
        return new ArrayList<>();
    }

    private static Map<String, Object> baseEntry(String operator, List<String> toActors,
                                                 int round, String reason) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("round", round);
        entry.put("fromActor", operator);
        entry.put("toActors", new ArrayList<>(toActors));
        entry.put("reason", reason == null ? "" : reason);
        entry.put("time", LocalDateTime.now().format(TIME_FORMAT));
        entry.put("operator", operator);
        return entry;
    }

    private static void append(ProcessTask host, Map<String, Object> entry) {
        if (host == null || host.getVariables() == null) {
            return;
        }
        FlowData vars = host.getVariables();
        Object existing = vars.get(FlowConst.AIDANT_HISTORY);
        List<Map<String, Object>> history;
        if (existing instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cast = (List<Map<String, Object>>) existing;
            history = cast;
        } else {
            history = new ArrayList<>();
        }
        history.add(entry);
        vars.put(FlowConst.AIDANT_HISTORY, history);
    }

    private static ProcessTask findTask(ProcessInstance instance, Long taskId) {
        if (taskId == null) {
            return null;
        }
        for (ProcessTask t : instance.getTasks()) {
            if (taskId.equals(t.getTaskId())) {
                return t;
            }
        }
        return null;
    }

    private static String firstActor(ProcessTask t) {
        return t.getActorIds() == null || t.getActorIds().isEmpty() ? null : t.getActorIds().get(0);
    }
}