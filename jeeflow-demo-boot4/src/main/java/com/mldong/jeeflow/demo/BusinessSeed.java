package com.mldong.jeeflow.demo;

import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.facade.JeeflowFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * T003：业务数据种子 driver——引擎真实启动（startAndExecute + execute），不直插 repo。
 *
 * <p>矩阵 = 八语言共用 canonical（day-shift 已在 Rust demo 实测全绿）：
 * 16 进行中(state=10) + 9 已完成(advance 推到 state=20) + 8 委托，
 * 8 用户 × 5 菜单（待办/已办/发起/抄送/委托）全覆盖。extraVars（deptLeader/amount）
 * 随发起注入实例变量供 assignee 变量解析；advance 读 doing 任务的
 * {@code taskActorIdList[0]} 自办（doing 任务 operator 为 null）。</p>
 */
public final class BusinessSeed {

    private static final Logger log = LoggerFactory.getLogger(BusinessSeed.class);

    private BusinessSeed() {
    }

    /** (defineId, operator, extraVars, 抄送 actorIds) */
    private record Row(long defineId, String operator, Map<String, Object> extra, List<String> cc) {
    }

    /** 进行中 16 条：发起后停 state=10（I3/I15 冻结在决策/驳回前，I14 发起后再办两节点停 boss） */
    private static final List<Row> IN_PROGRESS = List.of(
            new Row(1, "user1", Map.of(), List.of("userA", "userB")),
            new Row(2, "user1", Map.of(), List.of()),
            new Row(3, "userA", Map.of("amount", 500), List.of()),
            new Row(4, "manager", Map.of(), List.of("userC", "leader")),
            new Row(5, "userB", Map.of(), List.of()),
            new Row(6, "director", Map.of(), List.of("manager", "boss")),
            new Row(7, "userC", Map.of(), List.of("user1")),
            new Row(1, "boss", Map.of(), List.of()),
            new Row(12, "user1", Map.of("deptLeader", "manager"), List.of()),
            new Row(12, "userC", Map.of("deptLeader", "director"), List.of()),
            new Row(12, "userB", Map.of("deptLeader", "user1"), List.of()),
            new Row(15, "userA", Map.of(), List.of("boss")),
            new Row(14, "leader", Map.of(), List.of("director", "userC")),
            new Row(2, "userA", Map.of(), List.of()),
            new Row(10, "userB", Map.of(), List.of()),
            new Row(8, "user1", Map.of(), List.of()));

    /** 已完成 9 条：advance() 推到 state=20（分支无关） */
    private static final List<Row> FINISHED = List.of(
            new Row(1, "userA", Map.of(), List.of("user1", "director")),
            new Row(8, "userB", Map.of(), List.of("boss", "manager")),
            new Row(2, "manager", Map.of(), List.of("boss")),
            new Row(10, "director", Map.of(), List.of()),
            new Row(12, "userC", Map.of("deptLeader", "leader"), List.of()),
            new Row(1, "director", Map.of(), List.of()),
            new Row(5, "manager", Map.of(), List.of()),
            new Row(12, "userA", Map.of("deptLeader", "director"), List.of()),
            new Row(12, "userB", Map.of("deptLeader", "user1"), List.of()));

    /** 委托 8 条：processSurrogate/page 无 operator 过滤 → 8 用户委托菜单全非空 */
    private static final List<String[]> SURROGATES = List.of(
            new String[]{"user1", "userA"}, new String[]{"userA", "userB"},
            new String[]{"userB", "userC"}, new String[]{"userC", "leader"},
            new String[]{"leader", "manager"}, new String[]{"manager", "director"},
            new String[]{"director", "boss"}, new String[]{"boss", "user1"});

    /**
     * 发起表单数据（f_*）：按流程定义给真实业务值，让演示站「申请信息」开箱就有数据可回显。
     *
     * <p>12 个被种子的定义，apply 节点 form 都是 {@code apply-form}
     * （字段集 f_reason/f_days/f_leaveType/f_startDate/f_endDate）；11 是 mix-form。
     * 日期用固定字面量——八语言各自算"今天"会因时钟/时区漂出分叉（issues/120 同类教训）。
     * 刻意不叫 amount/finalAmount：那是 03/10 两个流程的条件表达式变量，别把路由变量当表单字段种。</p>
     */
    private static final Map<Long, Map<String, Object>> FORM_BY_DEFINE = Map.ofEntries(
            Map.entry(1L, Map.of("f_reason", "家中有事需请假", "f_days", 3, "f_leaveType", "annual", "f_startDate", "2026-09-01", "f_endDate", "2026-09-03")),
            Map.entry(2L, Map.of("f_reason", "项目上线后调休", "f_days", 2, "f_leaveType", "annual", "f_startDate", "2026-09-07", "f_endDate", "2026-09-08")),
            Map.entry(3L, Map.of("f_reason", "出差报销申请", "f_days", 1, "f_leaveType", "personal", "f_startDate", "2026-09-10", "f_endDate", "2026-09-10")),
            Map.entry(4L, Map.of("f_reason", "培训进修请假", "f_days", 5, "f_leaveType", "sick", "f_startDate", "2026-09-14", "f_endDate", "2026-09-18")),
            Map.entry(5L, Map.of("f_reason", "年假出行", "f_days", 4, "f_leaveType", "annual", "f_startDate", "2026-09-21", "f_endDate", "2026-09-24")),
            Map.entry(6L, Map.of("f_reason", "婚假申请", "f_days", 10, "f_leaveType", "personal", "f_startDate", "2026-09-28", "f_endDate", "2026-10-07")),
            Map.entry(7L, Map.of("f_reason", "病假休养", "f_days", 6, "f_leaveType", "sick", "f_startDate", "2026-10-12", "f_endDate", "2026-10-17")),
            Map.entry(8L, Map.of("f_reason", "产检假", "f_days", 3, "f_leaveType", "sick", "f_startDate", "2026-10-19", "f_endDate", "2026-10-21")),
            Map.entry(9L, Map.of("f_reason", "陪产假", "f_days", 5, "f_leaveType", "personal", "f_startDate", "2026-10-26", "f_endDate", "2026-10-30")),
            Map.entry(10L, Map.of("f_reason", "事假处理家务", "f_days", 2, "f_leaveType", "personal", "f_startDate", "2026-11-02", "f_endDate", "2026-11-03")),
            Map.entry(11L, Map.of("f_bizType", "purchase", "f_budget", 12000, "f_urgency", "normal", "f_desc", "采购一批开发板与传感器")),
            Map.entry(12L, Map.of("f_reason", "部门例行调休", "f_days", 1, "f_leaveType", "annual", "f_startDate", "2026-11-09", "f_endDate", "2026-11-09")),
            Map.entry(14L, Map.of("f_reason", "外派学习请假", "f_days", 7, "f_leaveType", "annual", "f_startDate", "2026-11-16", "f_endDate", "2026-11-22")),
            Map.entry(15L, Map.of("f_reason", "丧假", "f_days", 3, "f_leaveType", "personal", "f_startDate", "2026-11-23", "f_endDate", "2026-11-25")));

    /**
     * 办理表单数据（tf_*）：按任务节点 formKey 给值，让「已办 → 我的办理」和审批记录有真实内容。
     * 键名与各语言 demo 自定义表单里的字段名一一对应（带 tf_ 前缀才能与引擎 taskFormData 往返）。
     * tf_approvalComment 是办理抽屉内置字段，所有节点都带。
     */
    private static final Map<String, Map<String, Object>> TF_BY_FORM = Map.ofEntries(
            Map.entry("leave-form", Map.of("tf_approvedDays", 3, "tf_needExtra", "no", "tf_remark", "按项目排期核准，注意工作交接")),
            Map.entry("review-form", Map.of("tf_riskLevel", "low", "tf_needLegalDoc", "no", "tf_reviewOpinion", "条款与预算均无风险")),
            Map.entry("boss-form", Map.of("tf_finalDecision", "agree", "tf_finalAmount", 8000, "tf_bossNote", "同意，走年度预算")),
            Map.entry("check-form", Map.of("tf_invoiceOk", "yes", "tf_amountChecked", 8000, "tf_checkNote", "票据齐全，计入差旅科目")),
            Map.entry("countersign-form", Map.of("tf_signVote", "support", "tf_signAmount", 5000, "tf_signOpinion", "本条线无异议")),
            Map.entry("seq-form", Map.of("tf_seqStage", "first", "tf_seqVote", "pass", "tf_seqOpinion", "初审通过，转下一人")),
            Map.entry("approve-form", Map.of("tf_approveResult", "ok", "tf_approveAmount", 8000, "tf_approveNote", "审批通过")),
            Map.entry("ratio-form", Map.of("tf_ratioVote", "agree", "tf_ratioOpinion", "达到比例即可通过")),
            Map.entry("veto-form", Map.of("tf_vetoResult", "pass", "tf_vetoReason", "无异议")),
            Map.entry("form-a", Map.of("tf_branchA", "a1", "tf_branchANote", "A 分支选方案 A1")),
            Map.entry("form-b", Map.of("tf_branchB", "b1", "tf_branchBNote", "B 分支选方案 B1")),
            Map.entry("field-form", Map.of("tf_ownerName", "张三", "tf_field", "tech", "tf_fieldNote", "技术域评估通过")),
            Map.entry("operator-form", Map.of("tf_selfCheck", "done", "tf_operatorNote", "发起人自查无误")),
            Map.entry("dept-form", Map.of("tf_deptAgree", "yes", "tf_deptQuota", 8000, "tf_deptNote", "同意占用本部门额度")),
            Map.entry("role-form", Map.of("tf_roleResult", "pass", "tf_roleNote", "角色审批通过")));

    /** 给 execute 参数并入该任务表单的 tf_*（formKey 未收录时只带审批意见）。 */
    private static void withTaskForm(Map<String, Object> ex, Object formKey) {
        ex.put("tf_approvalComment", "同意，情况已核实");
        Map<String, Object> tf = TF_BY_FORM.get(String.valueOf(formKey == null ? "" : formKey));
        if (tf != null) {
            ex.putAll(tf);
        }
    }

    /** 种业务数据：失败逐条打日志不抛异常（demo 启动不被单条卡死）。 */
    public static void seed(JeeflowFacade facade) {
        int okIn = 0;
        for (Row row : IN_PROGRESS) {
            Map<String, Object> resp = facade.flow("processDefine/startAndExecute", startArgs(row));
            Object iid = instanceId(resp);
            if (iid == null) {
                log.warn("[seed] startAndExecute define={} op={} 失败：{}", row.defineId(), row.operator(), resp);
                continue;
            }
            // I14：发起后再办 leader、manager 两节点 → 停在 boss
            if (row.defineId() == 2 && "userA".equals(row.operator())) {
                for (String actor : List.of("leader", "manager")) {
                    Map<String, Object> todo = todoRow(facade, actor, iid);
                    if (todo != null) {
                        Map<String, Object> ex = new HashMap<>();
                        ex.put("processTaskId", todo.get("id"));
                        ex.put("operator", actor);
                        ex.put("submitType", 1);
                        withTaskForm(ex, todo.get("formKey"));
                        facade.flow("processTask/execute", ex);
                    } else {
                        log.warn("[seed] I14 todoRow actor={} iid={} 未找到", actor, iid);
                    }
                }
            }
            if (!row.cc().isEmpty()) {
                createCC(facade, iid, row.operator(), row.cc());
            }
            okIn++;
        }

        int okFin = 0;
        for (Row row : FINISHED) {
            Map<String, Object> resp = facade.flow("processDefine/startAndExecute", startArgs(row));
            Object iid = instanceId(resp);
            if (iid == null) {
                log.warn("[seed] FIN startAndExecute define={} op={} 失败：{}", row.defineId(), row.operator(), resp);
                continue;
            }
            long state = advance(facade, iid);
            if (state != 20) {
                log.warn("[seed] FIN define={} op={} iid={} 终态={}（期望 20）", row.defineId(), row.operator(), iid, state);
            }
            if (!row.cc().isEmpty()) {
                createCC(facade, iid, row.operator(), row.cc());
            }
            okFin++;
        }

        int okSurr = 0;
        for (String[] s : SURROGATES) {
            Map<String, Object> args = new HashMap<>();
            args.put("operator", s[0]);
            args.put("surrogate", s[1]);
            args.put("processName", "");
            args.put("startTime", "2026-01-01 00:00:00");
            args.put("endTime", "2027-12-31 23:59:59");
            Map<String, Object> resp = facade.flow("processSurrogate/save", args);
            if (isOk(resp)) {
                okSurr++;
            } else {
                log.warn("[seed] surrogate {}->{} 失败：{}", s[0], s[1], resp);
            }
        }
        log.info("[seedBusiness] done: in-progress {}/16, finished {}/9, surrogates {}/8", okIn, okFin, okSurr);
    }

    private static Map<String, Object> startArgs(Row row) {
        Map<String, Object> args = new HashMap<>();
        args.put("processDefineId", row.defineId());
        args.put("operator", row.operator());
        Map<String, Object> form = FORM_BY_DEFINE.get(row.defineId());
        if (form != null) {
            args.putAll(form);
        }
        args.putAll(row.extra());
        return args;
    }

    /** advance 原语：循环读 detail，对每个 doing 任务以其自身 actor execute(submitType=1)。 */
    private static long advance(JeeflowFacade facade, Object iid) {
        for (int i = 0; i < 30; i++) {
            Map<String, Object> resp = facade.flow("processInstance/detail", Map.of("id", iid));
            Map<String, Object> d = dataMap(resp);
            if (d == null) {
                return -1;
            }
            long state = toLong(d.get("state"), -1);
            if (state != 10) {
                return state;
            }
            List<?> tasks = d.get("tasks") instanceof List<?> l ? l : List.of();
            boolean progress = false;
            for (Object tObj : tasks) {
                if (!(tObj instanceof Map<?, ?> tRaw)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> t = (Map<String, Object>) tRaw;
                if (toLong(t.get("taskState"), 0) != 10) {
                    continue;
                }
                String actor = t.get("operator") == null || String.valueOf(t.get("operator")).isBlank()
                        ? firstActor(t.get("taskActorIdList"))
                        : String.valueOf(t.get("operator"));
                if (actor == null) {
                    continue;
                }
                Map<String, Object> ex = new HashMap<>();
                ex.put("processTaskId", t.get("id"));
                ex.put("operator", actor);
                ex.put("submitType", 1);
                withTaskForm(ex, t.get("formKey"));
                Map<String, Object> r = facade.flow("processTask/execute", ex);
                if (isOk(r)) {
                    progress = true;
                } else {
                    log.warn("[seed] advance execute iid={} actor={} 失败：{}", iid, actor, r);
                }
            }
            if (!progress) {
                return state;
            }
        }
        return -1;
    }

    /** 仅 I14 用：在该实例里找 op 的 doing 任务行。 */
    private static Map<String, Object> todoRow(JeeflowFacade facade, String op, Object iid) {
        Map<String, Object> args = new HashMap<>();
        args.put("operator", op);
        args.put("pageNum", 1);
        args.put("pageSize", 200);
        Map<String, Object> resp = facade.flow("processTask/todoList", args);
        Map<String, Object> d = dataMap(resp);
        if (d == null || !(d.get("rows") instanceof List<?> rows)) {
            return null;
        }
        for (Object rObj : rows) {
            if (!(rObj instanceof Map<?, ?> rRaw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> r = (Map<String, Object>) rRaw;
            if (String.valueOf(r.get("processInstanceId")).equals(String.valueOf(iid))
                    && toLong(r.get("taskState"), 0) == 10) {
                return r;
            }
        }
        return null;
    }

    private static void createCC(JeeflowFacade facade, Object iid, String op, List<String> actors) {
        Map<String, Object> args = new HashMap<>();
        args.put("processInstanceId", iid);
        args.put("operator", op);
        args.put("actorIds", actors);
        facade.flow("processInstance/createCCInstance", args);
    }

    private static Object instanceId(Map<String, Object> resp) {
        if (!isOk(resp)) {
            return null;
        }
        Map<String, Object> d = dataMap(resp);
        return d == null ? null : d.get(FlowConst.PROCESS_INSTANCE_ID_KEY);
    }

    private static boolean isOk(Map<String, Object> resp) {
        return resp != null && resp.get("code") instanceof Number n && n.intValue() == 0;
    }

    private static Map<String, Object> dataMap(Map<String, Object> resp) {
        if (resp != null && resp.get("data") instanceof Map<?, ?> raw) {
            @SuppressWarnings("unchecked")
            Map<String, Object> d = (Map<String, Object>) raw;
            return d;
        }
        return null;
    }

    private static String firstActor(Object actorIdList) {
        if (actorIdList instanceof List<?> l && !l.isEmpty() && l.get(0) != null) {
            return String.valueOf(l.get(0));
        }
        return null;
    }

    private static long toLong(Object v, long fallback) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
