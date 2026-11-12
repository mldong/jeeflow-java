package com.mldong.jeeflow.core;

import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 动态会签（征询模式，issues/247）的判据、账本与投影：全部只读/纯函数，
 * 由门面在聚合变更之前调用——"判据前置、报错即零写入"与后加签写入口同一规矩。
 *
 * @author mldong
 */
public final class CollectSupport {

    /** 节点属性键：动态会签（征询模式）开关（properties 平铺层，缺省 false） */
    public static final String PROP_DYNAMIC_COUNTERSIGN = "dynamicCountersign";

    /** 逐字判据文案（跨栈统一；规范 06 失败文案表随参考实现回填） */
    public static final String MSG_PENDING = "节点[%s]动态会签意见未收齐（已提交 %d/%d），不能办理";
    public static final String MSG_DUPLICATE = "操作人[%s]已在节点[%s]的动态会签中";
    public static final String MSG_MEMBER_PULL = "动态会签成员任务不能发起加签";
    public static final String MSG_NO_PENDING = "节点[%s]没有进行中的动态会签";
    public static final String MSG_NO_PERMISSION_REVOKE = "无权限撤回该动态会签";

    /** 全生态时间格式（规范 §2.4），与 tf_addSignHistory / tf_transferHistory 同尺 */
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private CollectSupport() {
    }

    /**
     * 成员行＝parent 指向主办行<b>且同节点同名</b>的行（成员不能再拉人 ⇒ 无更深层级）。
     * 同名过滤不可省：主办办结推进后，下一节点行的 parent 同样是主办行 id（建单不变量），
     * 只认 parent 会把下游行误当成员——撤回腿会误废弃它们。
     */
    public static List<ProcessTask> memberRows(List<ProcessTask> tasks, ProcessTask host) {
        List<ProcessTask> out = new ArrayList<>();
        for (ProcessTask t : tasks) {
            if (host.getTaskId() != null && host.getTaskId().equals(t.getParentTaskId())
                    && host.getTaskName() != null && host.getTaskName().equals(t.getTaskName())
                    // issues/253：协办行（task_type=1）也满足 parent＋同节点同名，但它属另一族
                    // ——征询收齐闸只数征询成员行（task_type=0），两闸各数各的（规范 04「与征询并存」）。
                    && !AidantSupport.isAidantRow(t)) {
                out.add(t);
            }
        }
        return out;
    }

    /** 成员行判定：parent 指向同节点同名行（issues/247 §3.3；成员行禁带拉人参数的判据）。
     *  issues/253：协办行（task_type=1）不是征询成员行——两族靠 task_type 分辨。 */
    public static boolean isMemberRow(List<ProcessTask> tasks, ProcessTask row) {
        if (row.getParentTaskId() == null || row.getParentTaskId() == 0L) {
            return false;
        }
        if (AidantSupport.isAidantRow(row)) {
            return false;
        }
        for (ProcessTask t : tasks) {
            if (row.getParentTaskId().equals(t.getTaskId()) && row.getTaskName().equals(t.getTaskName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 闸门原料：在册成员行总数与已提交（FINISHED）数——已废弃(99) 行（撤回腿清场的）不算在册；
     * 空集＝本行不是征询主办行。
     */
    public static int[] countMembers(List<ProcessTask> tasks, ProcessTask host) {
        int total = 0;
        int submitted = 0;
        for (ProcessTask t : memberRows(tasks, host)) {
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

    /** 未答成员行（DOING），撤回接口的废弃集合。 */
    public static List<ProcessTask> pendingMembers(List<ProcessTask> tasks, ProcessTask host) {
        List<ProcessTask> out = new ArrayList<>();
        for (ProcessTask t : memberRows(tasks, host)) {
            if (t.isDoing()) {
                out.add(t);
            }
        }
        return out;
    }

    /** 投影（规范 06 §2.7）：征询主办行 ext.dynamicCountersign＝{active, round, total, submitted, actors}；无成员行 ⇒ null。 */
    public static Map<String, Object> buildProjection(List<ProcessTask> tasks, ProcessTask host) {
        List<ProcessTask> members = memberRows(tasks, host);
        if (members.isEmpty()) {
            return null;
        }
        int total = 0;
        int submitted = 0;
        List<Map<String, Object>> actors = new ArrayList<>();
        for (ProcessTask m : members) {
            if (Integer.valueOf(99).equals(m.getTaskState())) {
                continue; // 撤回清场的行不进投影
            }
            total++;
            if (m.isFinished()) {
                submitted++;
            }
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("actor", m.getActorIds() == null || m.getActorIds().isEmpty() ? null : m.getActorIds().get(0));
            a.put("taskId", m.getTaskId());
            a.put("state", m.getTaskState());
            actors.add(a);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("active", submitted < total);
        out.put("round", lastRound(host));
        out.put("total", total);
        out.put("submitted", submitted);
        out.put("actors", actors);
        return out;
    }

    /** 主办行当前轮次＝账本条数（每次拉人追加一条＝轮次推进；撤回不消耗）。 */
    public static int lastRound(ProcessTask host) {
        List<Map<String, Object>> history = AddSignSupport.historyOf(host);
        if (history.isEmpty()) {
            return 0;
        }
        Object r = history.get(history.size() - 1).get("round");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /**
     * 发起会签账本追加（只追加不覆盖），落**主办行**（征询模式下主办行未办结，
     * {@code variables.putAll(args)} 的合并序保证账本活到办结后）；每条含 reason（选填）。
     */
    public static void appendCollectHistory(ProcessTask host, String operator,
                                            List<String> toActors, int round, String reason) {
        Map<String, Object> entry = baseEntry(host, operator, toActors, round);
        if (reason != null && !reason.isEmpty()) {
            entry.put("reason", reason);
        }
        append(host, entry);
    }

    /** 撤回会签账本追加：revoked＝true 标记本条是撤回（未答废弃），toActors＝被撤的人。 */
    public static void appendRevokeHistory(ProcessTask host, String operator,
                                           List<String> revokedActors, int round, String reason) {
        Map<String, Object> entry = baseEntry(host, operator, revokedActors, round);
        entry.put("revoked", true);
        if (reason != null && !reason.isEmpty()) {
            entry.put("reason", reason);
        }
        append(host, entry);
    }

    private static Map<String, Object> baseEntry(ProcessTask host, String operator,
                                                 List<String> toActors, int round) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("round", round);
        entry.put("fromActor", operator);
        entry.put("toActors", new ArrayList<>(toActors));
        entry.put("time", LocalDateTime.now().format(TIME_FORMAT));
        entry.put("operator", operator);
        return entry;
    }

    private static void append(ProcessTask host, Map<String, Object> entry) {
        if (host == null || host.getVariables() == null) {
            return;
        }
        FlowData vars = host.getVariables();
        Object existing = vars.get(FlowConst.ADD_SIGN_HISTORY);
        List<Map<String, Object>> history;
        if (existing instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cast = (List<Map<String, Object>>) existing;
            history = cast;
        } else {
            history = new ArrayList<>();
        }
        history.add(entry);
        vars.put(FlowConst.ADD_SIGN_HISTORY, history);
    }
}
