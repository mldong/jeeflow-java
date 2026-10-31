package com.mldong.jeeflow.core;

import com.mldong.jeeflow.JeeflowException;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.model.EndModel;
import com.mldong.jeeflow.model.ForkModel;
import com.mldong.jeeflow.model.JoinModel;
import com.mldong.jeeflow.model.NodeModel;
import com.mldong.jeeflow.model.ProcessModel;
import com.mldong.jeeflow.model.TaskModel;
import com.mldong.jeeflow.model.TransitionModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 「退回修改、重提直返」的引擎内部状态读写与分支归属解析。
 *
 * <p>状态落点（spec 04-engine-ops「退回修改、重提直返」）：不新增表/列，全部寄存在
 * 任务变量 JSON 的保留键里。保留键由引擎写入，对外办理入口必须先剥离客户端传入的同名键。</p>
 *
 * @author mldong
 */
public final class ReapplySupport {

    /** 节点属性键：退回发起人后的重提方式 */
    public static final String PROP_REAPPLY_MODE = "reapplyMode";
    /** 任务变量保留键：分支归属栈（外层→内层） */
    public static final String VAR_BRANCH_CTX = "_branchCtx";
    /** 任务变量保留键：本次退回绑定（只出现在申请待办上） */
    public static final String VAR_REAPPLY_CTX = "_reapplyContext";

    public static final String KEY_MODE = "mode";
    public static final String KEY_SOURCE_TASK = "sourceTaskId";
    public static final String KEY_SOURCE_NODE = "sourceNode";
    public static final String KEY_BRANCH = "branch";
    public static final String KEY_FORK = "fork";
    public static final String KEY_JOIN = "join";
    public static final String KEY_ROUND = "round";
    public static final String KEY_ROOT = "root";

    private ReapplySupport() {
    }

    // ═══ 模型静态分析 ═══

    /**
     * 节点所在的全部分支范围（外层→内层）。按「从汇合点回溯、遇 fork 即止」求集合，
     * 含该节点的汇合点按集合大小降序排列即为外→内。
     */
    public static List<Map<String, Object>> staticScopes(ProcessModel model, String nodeName) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (model == null || nodeName == null) {
            return result;
        }
        List<JoinModel> joins = new ArrayList<>();
        // 直接遍历节点表：ProcessModel#getModels 走 getNextModels 的图遍历，遇悬空边（target=null）
        // 会 NPE；分支归属推导不能被一条脏边打断（spec 02 §6.2「配错不该炸流程」）。
        for (NodeModel node : model.getNodes()) {
            if (node instanceof JoinModel) {
                joins.add((JoinModel) node);
            }
        }
        if (joins.isEmpty()) {
            return result;
        }
        List<Object[]> matched = new ArrayList<>();
        for (JoinModel join : joins) {
            String fork = findForkName(model, join);
            if (fork == null) {
                continue;
            }
            // 回溯到与该汇合点配对的分叉为止：嵌套时可穿过内层 fork，否则内层分支根会被漏掉
            Set<String> inside = backwardTaskNames(join, fork);
            if (inside.contains(nodeName)) {
                matched.add(new Object[]{fork, join.getName(), inside.size()});
            }
        }
        matched.sort((a, b) -> Integer.compare((Integer) b[2], (Integer) a[2]));
        for (Object[] m : matched) {
            result.add(scope((String) m[0], (String) m[1], 0L, 0L));
        }
        return result;
    }

    /** 从节点回溯到指定分叉之间的任务节点名集合（可穿过嵌套分叉，遇到 stopFork 即止） */
    private static Set<String> backwardTaskNames(NodeModel node, String stopFork) {
        Set<String> names = new LinkedHashSet<>();
        List<NodeModel> stack = new ArrayList<>();
        for (TransitionModel tm : node.getInputs()) {
            if (tm.getSource() != null) {
                stack.add(tm.getSource());
            }
        }
        Set<String> visited = new LinkedHashSet<>();
        while (!stack.isEmpty()) {
            NodeModel cur = stack.remove(stack.size() - 1);
            if (cur == null || !visited.add(cur.getName())) {
                continue;
            }
            if (cur instanceof ForkModel && cur.getName().equals(stopFork)) {
                continue;
            }
            if (cur instanceof TaskModel) {
                names.add(cur.getName());
            }
            for (TransitionModel tm : cur.getInputs()) {
                if (tm.getSource() != null) {
                    stack.add(tm.getSource());
                }
            }
        }
        return names;
    }

    /**
     * 汇合点对应的分叉节点名。不能取"回溯遇到的第一个 fork"——嵌套并行时外层汇合点沿内层分支
     * 回溯会先撞到内层 fork。判据：先求每个 fork 的**最近后支配汇合点**（每条分支都要经过它），
     * 再反查哪个 fork 把它当作最近汇合点；同一汇合点有多个候选时取最外层。
     */
    private static String findForkName(ProcessModel model, JoinModel join) {
        String best = null;
        int bestDepth = -1;
        Set<String> joins = new LinkedHashSet<>();
        for (NodeModel node : model.getNodes()) {
            if (node instanceof JoinModel) {
                joins.add(node.getName());
            }
        }
        for (NodeModel node : model.getNodes()) {
            if (!(node instanceof ForkModel)) {
                continue;
            }
            String nearest = nearestPostDominatingJoin(node, joins);
            if (join.getName().equals(nearest)) {
                int depth = maxDistanceToJoin(node, join.getName());
                if (depth > bestDepth) {
                    bestDepth = depth;
                    best = node.getName();
                }
            }
        }
        return best;
    }

    /** fork 的最近后支配汇合点（每条分支都必须经过，且距离最近） */
    private static String nearestPostDominatingJoin(NodeModel fork, Set<String> joins) {
        String best = null;
        int bestMax = Integer.MAX_VALUE;
        for (String j : joins) {
            if (!postDominates(fork, j)) {
                continue;
            }
            int d = maxDistanceToJoin(fork, j);
            if (d >= 0 && d < bestMax) {
                bestMax = d;
                best = j;
            }
        }
        return best;
    }

    /** 屏蔽该汇合点后，fork 的任一分支仍能走到结束 ⇒ 不是后支配 */
    private static boolean postDominates(NodeModel fork, String joinName) {
        List<NodeModel> stack = new ArrayList<>();
        for (TransitionModel out : fork.getOutputs()) {
            if (out.getTarget() != null) {
                stack.add(out.getTarget());
            }
        }
        Set<String> visited = new LinkedHashSet<>();
        boolean reachesEnd = false;
        while (!stack.isEmpty()) {
            NodeModel cur = stack.remove(stack.size() - 1);
            if (cur == null || joinName.equals(cur.getName()) || !visited.add(cur.getName())) {
                continue;
            }
            if (cur instanceof EndModel) {
                reachesEnd = true;
                break;
            }
            for (TransitionModel tm : cur.getOutputs()) {
                if (tm.getTarget() != null) {
                    stack.add(tm.getTarget());
                }
            }
        }
        return !reachesEnd;
    }

    /** 分支走到汇合点的最大跳数（衡量"外层程度"）；不可达返回 -1 */
    private static int maxDistanceToJoin(NodeModel fork, String joinName) {
        Map<String, Integer> dist = new LinkedHashMap<>();
        List<NodeModel> frontier = new ArrayList<>();
        for (TransitionModel out : fork.getOutputs()) {
            if (out.getTarget() != null && !dist.containsKey(out.getTarget().getName())) {
                dist.put(out.getTarget().getName(), 1);
                frontier.add(out.getTarget());
            }
        }
        int d = 1;
        int found = -1;
        Set<String> visited = new LinkedHashSet<>();
        frontier.forEach(n -> visited.add(n.getName()));
        while (!frontier.isEmpty()) {
            List<NodeModel> next = new ArrayList<>();
            for (NodeModel cur : frontier) {
                if (joinName.equals(cur.getName())) {
                    found = Math.max(found, d);
                }
                for (TransitionModel tm : cur.getOutputs()) {
                    NodeModel target = tm.getTarget();
                    if (target != null && visited.add(target.getName())) {
                        next.add(target);
                    }
                }
            }
            frontier = next;
            d++;
        }
        return found;
    }

    // ═══ 任务变量的读写 ═══

    public static List<Map<String, Object>> scopesOf(ProcessTask task) {
        return scopesOf(task == null ? null : task.getVariables());
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> scopesOf(FlowData vars) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (vars == null) {
            return result;
        }
        Object raw = vars.getObj(VAR_BRANCH_CTX);
        if (!(raw instanceof List)) {
            return result;
        }
        for (Object item : (List<Object>) raw) {
            if (item instanceof Map) {
                result.add(new LinkedHashMap<>((Map<String, Object>) item));
            }
        }
        return result;
    }

    public static void putScopes(ProcessTask task, List<Map<String, Object>> scopes) {
        if (task == null) {
            return;
        }
        task.getVariables().put(VAR_BRANCH_CTX, scopes == null ? new ArrayList<>() : scopes);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> reapplyOf(ProcessTask task) {
        if (task == null || task.getVariables() == null) {
            return null;
        }
        Object raw = task.getVariables().getObj(VAR_REAPPLY_CTX);
        if (!(raw instanceof Map)) {
            return null;
        }
        Map<String, Object> ctx = new LinkedHashMap<>((Map<String, Object>) raw);
        return ctx.isEmpty() ? null : ctx;
    }

    public static void putReapply(ProcessTask task, Map<String, Object> ctx) {
        if (task == null || ctx == null) {
            return;
        }
        task.getVariables().put(VAR_REAPPLY_CTX, ctx);
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> branchOf(Map<String, Object> reapply) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (reapply == null) {
            return result;
        }
        Object raw = reapply.get(KEY_BRANCH);
        if (!(raw instanceof List)) {
            return result;
        }
        for (Object item : (List<Object>) raw) {
            if (item instanceof Map) {
                result.add(new LinkedHashMap<>((Map<String, Object>) item));
            }
        }
        return result;
    }

    /** 在范围内找指定汇合点的那一档 */
    public static Map<String, Object> findScope(List<Map<String, Object>> scopes, String joinName) {
        if (scopes == null || joinName == null) {
            return null;
        }
        for (Map<String, Object> s : scopes) {
            if (joinName.equals(asStr(s.get(KEY_JOIN)))) {
                return s;
            }
        }
        return null;
    }

    // ═══ 建单时计算分支归属 ═══

    /**
     * 新任务的分支归属：静态范围逐档继承父任务同档的 round/root；父任务不在该档
     * （本任务是该分叉的分支根）时 round=父任务ID、root=本任务ID（落库后由引擎回填）。
     */
    public static List<Map<String, Object>> buildScopes(ProcessTask parent, ProcessModel model,
                                                        String nodeName, Long selfTaskId) {
        List<Map<String, Object>> statics = staticScopes(model, nodeName);
        if (statics.isEmpty()) {
            return statics;
        }
        List<Map<String, Object>> parentScopes = scopesOf(parent);
        long parentId = parent != null && parent.getTaskId() != null ? parent.getTaskId() : 0L;
        long selfId = selfTaskId != null ? selfTaskId : 0L;
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> s : statics) {
            String fork = asStr(s.get(KEY_FORK));
            String join = asStr(s.get(KEY_JOIN));
            Map<String, Object> inherited = null;
            for (Map<String, Object> p : parentScopes) {
                if (fork.equals(asStr(p.get(KEY_FORK))) && join.equals(asStr(p.get(KEY_JOIN)))) {
                    inherited = p;
                    break;
                }
            }
            if (inherited != null) {
                result.add(scope(fork, join, asLong(inherited.get(KEY_ROUND)), asLong(inherited.get(KEY_ROOT))));
            } else {
                result.add(scope(fork, join, parentId, selfId));
            }
        }
        return result;
    }

    /** 分支根任务落库拿到 ID 后回填自身 root（建单时 ID 尚未生成）；返回是否发生回填 */
    public static boolean fillRootSelf(ProcessTask task) {
        if (task == null || task.getTaskId() == null) {
            return false;
        }
        List<Map<String, Object>> scopes = scopesOf(task);
        boolean changed = false;
        for (Map<String, Object> s : scopes) {
            if (asLong(s.get(KEY_ROOT)) == 0L) {
                s.put(KEY_ROOT, task.getTaskId());
                changed = true;
            }
        }
        if (changed) {
            putScopes(task, scopes);
        }
        return changed;
    }

    // ═══ 直返上下文 ═══

    public static Map<String, Object> newReapplyContext(String mode, Long sourceTaskId,
                                                        String sourceNode, List<Map<String, Object>> branch) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put(KEY_MODE, mode);
        ctx.put(KEY_SOURCE_TASK, sourceTaskId == null ? null : String.valueOf(sourceTaskId));
        ctx.put(KEY_SOURCE_NODE, sourceNode);
        ctx.put(KEY_BRANCH, branch == null ? new ArrayList<>() : branch);
        return ctx;
    }

    /** 客户端不得伪造/覆盖引擎控制键 */
    public static FlowData stripReserved(FlowData args) {
        if (args == null) {
            return FlowData.create();
        }
        args.remove(VAR_BRANCH_CTX);
        args.remove(VAR_REAPPLY_CTX);
        return args;
    }

    /** 结构损坏的直返上下文明确失败，不按无标记处理 */
    public static Long requireSourceTaskId(Map<String, Object> ctx) {
        Long id = asLongObj(ctx.get(KEY_SOURCE_TASK));
        if (id == null || id <= 0L) {
            throw new JeeflowException("重提直返上下文缺少有效来源任务");
        }
        return id;
    }

    // ═══ 基础转换（JSON 往返后数字可能是 Integer/Long/Double/String） ═══

    public static long asLong(Object v) {
        Long r = asLongObj(v);
        return r == null ? 0L : r;
    }

    public static Long asLongObj(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String asStr(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Map<String, Object> scope(String fork, String join, long round, long root) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(KEY_FORK, fork);
        m.put(KEY_JOIN, join);
        m.put(KEY_ROUND, round);
        m.put(KEY_ROOT, root);
        return m;
    }

    public static List<Map<String, Object>> emptyScopes() {
        return Collections.emptyList();
    }
}
