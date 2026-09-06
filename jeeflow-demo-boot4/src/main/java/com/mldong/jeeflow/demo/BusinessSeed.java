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
