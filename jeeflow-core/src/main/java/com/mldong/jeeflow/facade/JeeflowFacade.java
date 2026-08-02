package com.mldong.jeeflow.facade;

import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessDesign;
import com.mldong.jeeflow.domain.ProcessDesignHis;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessSurrogate;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
import com.mldong.jeeflow.enums.ProcessTaskStateEnum;
import com.mldong.jeeflow.json.IJsonProvider;
import com.mldong.jeeflow.spi.IExpressionEvaluator;
import com.mldong.jeeflow.model.DecisionModel;
import com.mldong.jeeflow.model.NodeModel;
import com.mldong.jeeflow.model.ProcessModel;
import com.mldong.jeeflow.model.TransitionModel;
import com.mldong.jeeflow.util.StringUtils;
import com.mldong.jeeflow.parser.ModelParser;
import com.mldong.jeeflow.spi.IProcessExtRepository;
import com.mldong.jeeflow.spi.IUserProvider;
import com.mldong.jeeflow.spi.IUserSearchProvider;
import com.mldong.jeeflow.spi.IProcessRepository;
import com.mldong.jeeflow.spi.JeeflowQueryParser;
import com.mldong.jeeflow.enums.ProcessTaskPerformTypeEnum;
import com.mldong.jeeflow.domain.Candidate;
import com.mldong.jeeflow.spi.PageQuery;
import com.mldong.jeeflow.spi.PageResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一门面（v1.1.0）——"接口即 POST + JSON body"风格的单入口
 *
 * <p>集成方只实现一个转发 controller：把 body JSON 转成 {@code Map} 传入
 * {@link #flow(String, Map)}，所有流程能力按 {@code action}（boot2/boot3 端点短名）
 * 路由。返回统一结构 {@code {code, msg, data}}（code=0 成功 / 99999999 失败）。</p>
 *
 * <p>操作人约定：门面不感知登录态，{@code args.operator} 显式传入（demo 风格），
 * 集成方可换为自定义实现（如从登录上下文注入）。</p>
 *
 * @author mldong
 */
public class JeeflowFacade {

    private final JeeflowEngine engine;
    private final IProcessRepository repository;
    private final IProcessExtRepository extRepository; // 可空：未接入扩展仓储时设计/委托 action 报错
    private IUserSearchProvider userSearchProvider;    // 可空：candidatePage 用户搜索依赖
    private final JeeflowQueryParser queryParser = new JeeflowQueryParser();

    /** 注入用户搜索钩子（candidatePage 无模型候选时的用户分页搜索） */
    public JeeflowFacade setUserSearchProvider(IUserSearchProvider provider) {
        this.userSearchProvider = provider;
        return this;
    }

    public JeeflowFacade(JeeflowEngine engine, IProcessRepository repository, IProcessExtRepository extRepository) {
        this.engine = engine;
        this.repository = repository;
        this.extRepository = extRepository;
    }

    /**
     * 统一入口。action 见 spec §11.2 清单。
     */
    public Map<String, Object> flow(String action, Map<String, Object> args) {
        try {
            if (args == null) args = new LinkedHashMap<>();
            switch (action) {
                // ── 流程定义 ──
                case "processDefine/page": return definePage(args);
                case "processDefine/detail": return defineDetail(args);
                case "processDefine/startAndExecute": return startAndExecute(args);
                case "processDefine/deploy": return deploy(args);
                case "processDefine/redeploy": return redeploy(args);
                case "processDefine/remove": return defineRemove(args);
                case "processDefine/upAndDown": return defineUpAndDown(args);
                // ── 流程实例 ──
                case "processInstance/page": return instancePage(args);
                case "processInstance/detail": return instanceDetail(args);
                case "processInstance/startAndExecute": return startAndExecute(args);
                case "processInstance/withdraw": return withdraw(args);
                // ── 流程任务 ──
                case "processTask/todoList": return todoList(args);
                case "processTask/doneList": return doneList(args);
                case "processTask/execute": return execute(args);
                // ── 流程设计（需扩展仓储）──
                case "processDesign/page": return designPage(args);
                case "processDesign/detail": return designDetail(args);
                case "processDesign/save": return designSave(args);
                case "processDesign/remove": return designRemove(args);
                case "processDesign/deploy": return designDeploy(args);
                // ── 视图端点（v1.2.0）──
                case "processDefine/getLastByName": return getLastByName(args);
                case "processInstance/highLight": return highLight(args);
                case "processInstance/approvalRecord": return approvalRecord(args);
                case "processInstance/getAssigneeTextData": return getAssigneeTextData(args);
                case "processInstance/createCCInstance": return createCCInstance(args);
                case "processInstance/updateCCStatus": return updateCCStatus(args);
                case "processInstance/ccList": return ccList(args);
                case "processTask/detail": return taskDetail(args);
                case "processTask/jumpAbleTaskNameList": return jumpAbleTaskNameList(args);
                case "processTask/candidatePage": return candidatePage(args);
                case "processTask/surrogate": return taskSurrogate(args);
                case "processTask/addCandidate": return taskAddCandidate(args);
                case "processTask/latest": return taskLatest(args);
                // ── 委托代理（需扩展仓储）──
                case "processSurrogate/page": return surrogatePage(args);
                case "processSurrogate/save": return surrogateSave(args);
                case "processSurrogate/remove": return surrogateRemove(args);
                default:
                    return error("未知 action: " + action);
            }
        } catch (Exception e) {
            return error(e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    // ═══ 流程定义 ═══

    private Map<String, Object> definePage(Map<String, Object> args) {
        PageQuery query = queryParser.parse(args);
        PageResult<IProcessRepository.DefineRow> page = repository.pageDefines(query);
        return pageResult(page);
    }

    private Map<String, Object> defineDetail(Map<String, Object> args) {
        Long id = toLong(args.get("id"));
        ProcessInstance.ProcessDefine def = repository.findDefineById(id);
        if (def == null) return error("流程定义不存在");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", def.getId());
        data.put("name", def.getName());
        data.put("displayName", def.getDisplayName());
        data.put("type", def.getType());
        data.put("state", def.getState());
        data.put("version", def.getVersion());
        data.put("jsonObject", parseGraph(def.getContent())); // 前端表单渲染/流程图依赖（issues/05）
        return ok(data);
    }

    private Map<String, Object> startAndExecute(Map<String, Object> args) {
        Long defineId = toLong(args.get(FlowConst.PROCESS_DEFINE_ID_KEY));
        String operator = toStr(args.get("operator"), "user1");
        FlowData flowArgs = FlowData.create();
        args.forEach((k, v) -> {
            if (!FlowConst.PROCESS_DEFINE_ID_KEY.equals(k) && !"operator".equals(k)) flowArgs.put(k, v);
        });
        ProcessInstance inst = engine.startProcessInstanceById(defineId, operator, flowArgs);
        // boot2 startAndExecute：自动完成申请节点（assignee="applicant" → 发起人）
        List<ProcessTask> doingTasks = repository.findDoingTasks(inst.getInstanceId(), new String[]{});
        for (ProcessTask task : doingTasks) {
            repository.addTaskActor(task.getTaskId(), List.of(operator));
            flowArgs.put(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.APPLY.getCode());
            engine.executeProcessTask(task.getTaskId(), operator, flowArgs);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(FlowConst.PROCESS_INSTANCE_ID_KEY, inst.getInstanceId());
        return ok(data);
    }

    private Map<String, Object> deploy(Map<String, Object> args) {
        byte[] bytes = contentBytes(args);
        ProcessModel model = ModelParser.parse(bytes);
        Long defineId = saveDeployedDefine(model, bytes);
        return ok(Map.of(FlowConst.PROCESS_DEFINE_ID_KEY, defineId));
    }

    private Map<String, Object> redeploy(Map<String, Object> args) {
        Long defineId = toLong(args.get(FlowConst.PROCESS_DEFINE_ID_KEY));
        byte[] bytes = contentBytes(args);
        ProcessModel model = ModelParser.parse(bytes);
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setId(defineId);
        def.setName(model.getName());
        def.setDisplayName(model.getDisplayName());
        def.setType(model.getType());
        def.setContent(bytes);
        def.setUpdateUser(toStr(args.get("operator"), "system"));
        repository.updateDefine(def);
        return ok();
    }

    private Map<String, Object> defineRemove(Map<String, Object> args) {
        Long id = toLong(args.get("id"));
        repository.removeDefine(id);
        return ok();
    }

    private Map<String, Object> defineUpAndDown(Map<String, Object> args) {
        Long id = toLong(args.get("id"));
        int state = Integer.parseInt(args.get("state").toString());
        repository.updateDefineState(id, state);
        return ok();
    }

    // ═══ 流程实例 ═══

    private Map<String, Object> instancePage(Map<String, Object> args) {
        PageQuery query = queryParser.parse(args);
        String userId = toStr(args.get("operator"), "user1");
        query.add("t.operator", "EQ", userId);
        PageResult<IProcessRepository.InstanceRow> page = repository.pageInstances(query);
        return pageResult(page);
    }

    private Map<String, Object> instanceDetail(Map<String, Object> args) {
        Long id = toLong(args.get("id"));
        ProcessInstance inst = repository.findInstanceById(id);
        if (inst == null) return error("流程实例不存在");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", inst.getInstanceId());
        data.put("parentId", inst.getParentId());
        data.put("processDefineId", inst.getDefineId());
        data.put("state", inst.getState());
        data.put("parentNodeName", inst.getParentNodeName());
        data.put("businessNo", inst.getBusinessNo());
        data.put("operator", inst.getOperator());
        data.put("variables", inst.getVariables());
        data.put("createTime", String.valueOf(inst.getCreateTime()));
        data.put("createUser", inst.getCreateUser());
        ProcessInstance.ProcessDefine def0 = repository.findDefineById(inst.getDefineId());
        data.put("jsonObject", def0 != null ? parseGraph(def0.getContent()) : null); // issues/05
        // 任务列表（issues/05-4）：全量 tasks + activeTaskList（仅 DOING）+ 任务行 ext/isFirstTaskNode
        String firstTaskNodeId = firstTaskNodeId(data.get("jsonObject"));
        List<Map<String, Object>> tasks = new ArrayList<>();
        List<Map<String, Object>> activeTaskList = new ArrayList<>();
        if (inst.getTasks() != null) {
            for (ProcessTask t : inst.getTasks()) {
                Map<String, Object> vo = taskVo(t);
                Map<String, Object> ext = t.getVariables() != null
                        ? new LinkedHashMap<>(t.getVariables()) : new LinkedHashMap<>();
                boolean doing = ProcessTaskStateEnum.DOING.getCode().equals(t.getTaskState());
                // 首个任务节点且进行中 → 前端详情抽屉可"重新提交"（对齐 boot3）
                ext.put("isFirstTaskNode", doing && t.getTaskName().equals(firstTaskNodeId));
                vo.put("ext", ext);
                tasks.add(vo);
                if (doing) activeTaskList.add(vo);
            }
        }
        data.put("tasks", tasks);
        data.put("activeTaskList", activeTaskList);
        return ok(data);
    }

    /** 流程 JSON 中第一个任务节点 id（issues/05-4 isFirstTaskNode 用） */
    private String firstTaskNodeId(Object jsonObject) {
        if (jsonObject instanceof Map && ((Map<?, ?>) jsonObject).get("nodes") instanceof List) {
            for (Object n : (List<?>) ((Map<?, ?>) jsonObject).get("nodes")) {
                if (n instanceof Map && "snaker:task".equals(((Map<?, ?>) n).get("type"))) {
                    Object id = ((Map<?, ?>) n).get("id");
                    return id != null ? id.toString() : null;
                }
            }
        }
        return null;
    }

    private Map<String, Object> withdraw(Map<String, Object> args) {
        Long instanceId = toLong(args.get("id"));
        String operator = toStr(args.get("operator"), "user1");
        ProcessInstance inst = repository.findInstanceById(instanceId);
        if (inst == null) return error("流程实例不存在");
        inst.withdraw(operator);
        repository.updateInstance(inst); // v1.0.1：级联持久化任务状态
        return ok();
    }

    // ═══ 流程任务 ═══

    private Map<String, Object> todoList(Map<String, Object> args) {
        PageQuery query = queryParser.parse(args);
        String userId = toStr(args.get("operator"), "user1");
        query.add("pta.actor_id", "EQ", userId);
        PageResult<IProcessRepository.TaskRow> page = repository.pageTodoTasks(query);
        return pageResult(page);
    }

    private Map<String, Object> doneList(Map<String, Object> args) {
        PageQuery query = queryParser.parse(args);
        String userId = toStr(args.get("operator"), "user1");
        query.add("t.operator", "EQ", userId);
        PageResult<IProcessRepository.TaskRow> page = repository.pageDoneTasks(query);
        return pageResult(page);
    }

    private Map<String, Object> execute(Map<String, Object> args) {
        Long taskId = toLong(args.get(FlowConst.PROCESS_TASK_ID_KEY));
        String operator = toStr(args.get("operator"), "user1");
        Object submitTypeObj = args.getOrDefault(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode());
        Integer submitType = submitTypeObj instanceof Number ? ((Number) submitTypeObj).intValue()
                : Integer.parseInt(submitTypeObj.toString());
        FlowData flowArgs = FlowData.create();
        args.forEach((k, v) -> {
            if (!FlowConst.PROCESS_TASK_ID_KEY.equals(k) && !"operator".equals(k)) flowArgs.put(k, v);
        });
        flowArgs.put(FlowConst.SUBMIT_TYPE, submitType);
        // boot3 execute 分发（spec §11.2）
        if (ProcessSubmitTypeEnum.REJECT.getCode().equals(submitType)) {
            engine.executeAndJumpToEnd(taskId, operator, flowArgs);
        } else if (ProcessSubmitTypeEnum.ROLLBACK.getCode().equals(submitType)) {
            engine.executeAndJumpTask(taskId, operator, flowArgs, null);
        } else if (ProcessSubmitTypeEnum.JUMP.getCode().equals(submitType)) {
            String taskName = toStr(args.get(FlowConst.TASK_NAME));
            engine.executeAndJumpTask(taskId, operator, flowArgs, taskName);
        } else if (ProcessSubmitTypeEnum.ROLLBACK_TO_OPERATOR.getCode().equals(submitType)) {
            engine.executeAndJumpToFirstTaskNode(taskId, operator, flowArgs);
        } else if (ProcessSubmitTypeEnum.COUNTERSIGN_DISAGREE.getCode().equals(submitType)) {
            flowArgs.put(FlowConst.COUNTERSIGN_DISAGREE_FLAG, 1);
            engine.executeProcessTask(taskId, operator, flowArgs);
        } else {
            // 默认执行（0 APPLY / 1 AGREE / 5 重新提交）
            engine.executeProcessTask(taskId, operator, flowArgs);
        }
        return ok();
    }

    // ═══ 视图端点（v1.2.0） ═══

    private Map<String, Object> getLastByName(Map<String, Object> args) {
        String name = toStr(args.get("processDefineName"));
        PageQuery query = new PageQuery(1, 1);
        query.add("t.name", "EQ", name);
        query.setOrderBy("t.version desc");
        PageResult<IProcessRepository.DefineRow> page = repository.pageDefines(query);
        if (page.getRows() == null || page.getRows().isEmpty()) return error("流程定义不存在: " + name);
        IProcessRepository.DefineRow def = page.getRows().get(0);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", def.getId());
        data.put("name", def.getName());
        data.put("displayName", def.getDisplayName());
        data.put("type", def.getType());
        data.put("state", def.getState());
        data.put("version", def.getVersion());
        return ok(data);
    }

    private Map<String, Object> highLight(Map<String, Object> args) {
        Long instanceId = toLong(args.get("id"));
        ProcessInstance inst = repository.findInstanceById(instanceId);
        if (inst == null) return error("流程实例不存在");
        List<String> activeNodeNames = new ArrayList<>();
        List<String> historyNodeNames = new ArrayList<>();
        List<String> historyEdgeNames = new ArrayList<>();
        // 活跃节点 = 进行中任务
        List<ProcessTask> doing = repository.findDoingTasks(instanceId, null);
        for (ProcessTask t : doing) {
            if (!activeNodeNames.contains(t.getTaskName())) activeNodeNames.add(t.getTaskName());
        }
        // 历史节点 = 已完成任务 + 模型路径补全（start 沿 outputs 递归，遇活跃节点停止）
        List<ProcessTask> history = repository.findHistoryTasks(instanceId);
        for (ProcessTask t : history) {
            if (!activeNodeNames.contains(t.getTaskName()) && !historyNodeNames.contains(t.getTaskName())) {
                historyNodeNames.add(t.getTaskName());
            }
        }
        ProcessInstance.ProcessDefine def = repository.findDefineById(inst.getDefineId());
        if (def != null) {
            try {
                ProcessModel model = ModelParser.parse(def.getContent());
                collectPath(model.getStart(), activeNodeNames, historyNodeNames, historyEdgeNames,
                        new java.util.HashSet<>(), inst.getVariables(), history);
            } catch (Exception ignored) {
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("activeNodeNames", activeNodeNames);
        data.put("historyNodeNames", historyNodeNames);
        data.put("historyEdgeNames", historyEdgeNames);
        return ok(data);
    }

    private void collectPath(NodeModel node, List<String> active,
                             List<String> history, List<String> edges, java.util.Set<String> visited,
                             Map<String, Object> instanceVars, List<ProcessTask> historyTasks) {
        if (node == null || node.getOutputs() == null || visited.contains(node.getName())) return;
        visited.add(node.getName());
        for (TransitionModel tm : node.getOutputs()) {
            // 决策节点：输出边表达式求值过滤（对齐 boot3 recursionModel，issues/06）——
            // 表达式为 false 的分支未实际执行，不收集进高亮路径
            if (node instanceof DecisionModel && StringUtils.isNotEmpty(tm.getExpr())
                    && !evalDecisionExpr((DecisionModel) node, tm, instanceVars, historyTasks)) {
                continue;
            }
            String edgeName = tm.getName();
            if (edgeName != null && !edges.contains(edgeName)) edges.add(edgeName);
            NodeModel next = tm.getTarget();
            if (next == null) continue;
            if (!active.contains(next.getName()) && !history.contains(next.getName())) {
                history.add(next.getName());
            }
            if (active.contains(next.getName())) continue; // 遇活跃节点停止深入
            collectPath(next, active, history, edges, visited, instanceVars, historyTasks);
        }
    }

    /**
     * 决策输出边表达式求值（对齐 boot3 recursionModel）：
     * args = 实例变量 + 决策节点前置任务（输入第一个源节点）的任务变量，与引擎运行时 DecisionModel.exec 同源
     */
    private boolean evalDecisionExpr(DecisionModel decision, TransitionModel tm,
                                     Map<String, Object> instanceVars, List<ProcessTask> historyTasks) {
        IExpressionEvaluator evaluator = ServiceContext.find(IExpressionEvaluator.class);
        if (evaluator == null) return false;
        Map<String, Object> args = new HashMap<>();
        if (instanceVars != null) args.putAll(instanceVars);
        List<TransitionModel> inputs = decision.getInputs();
        if (inputs != null && !inputs.isEmpty()) {
            NodeModel src = inputs.get(0).getSource();
            if (src != null && src.getName() != null && historyTasks != null) {
                for (ProcessTask t : historyTasks) {
                    if (src.getName().equals(t.getTaskName()) && t.getVariables() != null) {
                        args.putAll(t.getVariables());
                        break;
                    }
                }
            }
        }
        return Boolean.TRUE.equals(evaluator.eval(tm.getExpr(), args));
    }

    private Map<String, Object> approvalRecord(Map<String, Object> args) {
        Long instanceId = toLong(args.get("id"));
        List<ProcessTask> history = repository.findHistoryTasks(instanceId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ProcessTask t : history) {
            Map<String, Object> vo = new LinkedHashMap<>();
            vo.put("taskName", t.getTaskName());
            vo.put("displayName", t.getDisplayName());
            vo.put("taskType", t.getTaskType() != null ? t.getTaskType().getCode() : null);
            vo.put("performType", t.getPerformType() != null ? t.getPerformType().getCode() : null);
            vo.put("taskState", t.getTaskState());
            vo.put("operator", t.getActorId());
            vo.put("finishTime", String.valueOf(t.getFinishTime()));
            vo.put("variable", t.getVariables());
            rows.add(vo);
        }
        return ok(rows);
    }

    private Map<String, Object> getAssigneeTextData(Map<String, Object> args) {
        Long instanceId = toLong(args.get("id"));
        boolean includeNodeName = !Boolean.FALSE.equals(args.get("includeNodeName"));
        List<Map<String, Object>> rows = new ArrayList<>();
        List<ProcessTask> doing = repository.findDoingTasks(instanceId, null);
        for (ProcessTask t : doing) {
            List<String> actors = repository.findTaskActors(t.getTaskId());
            for (String actor : actors) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("value", actor);
                item.put("label", includeNodeName ? t.getDisplayName() + ":" + actor : actor);
                rows.add(item);
            }
        }
        return ok(rows);
    }

    private Map<String, Object> createCCInstance(Map<String, Object> args) {
        Long instanceId = toLong(args.get("processInstanceId"));
        String operator = toStr(args.get("operator"), "user1");
        Object actorIds = args.get("actorIds");
        if (!(actorIds instanceof java.util.Collection) || ((java.util.Collection<?>) actorIds).isEmpty()) {
            return error("actorIds 缺失");
        }
        java.util.Collection<?> coll = (java.util.Collection<?>) actorIds;
        repository.createCcInstance(instanceId, operator,
                coll.stream().map(Object::toString).toArray(String[]::new));
        return ok();
    }

    private Map<String, Object> updateCCStatus(Map<String, Object> args) {
        Long instanceId = toLong(args.get("processInstanceId"));
        String operator = toStr(args.get("operator"), "user1");
        repository.updateCcStatus(instanceId, operator);
        return ok();
    }

    private Map<String, Object> ccList(Map<String, Object> args) {
        PageQuery query = queryParser.parse(args);
        String userId = toStr(args.get("operator"), "user1");
        query.add("cc.actor_id", "EQ", userId);
        PageResult<IProcessRepository.InstanceRow> page = repository.pageCcInstances(query);
        return pageResult(page);
    }

    private Map<String, Object> taskDetail(Map<String, Object> args) {
        Long taskId = toLong(args.get("id"));
        String operator = toStr(args.get("operator"), "user1");
        ProcessTask task = repository.findTaskById(taskId);
        if (task == null) return error("任务不存在");
        Map<String, Object> vo = taskVo(task);
        vo.put("taskActorIdList", repository.findTaskActors(taskId));
        vo.put("executable", task.isAllowed(operator));
        // taskModel：流程定义中对应节点（显示名/表单）
        ProcessInstance inst = repository.findInstanceById(task.getProcessInstanceId());
        if (inst != null) {
            ProcessInstance.ProcessDefine def = repository.findDefineById(inst.getDefineId());
            vo.put("jsonObject", def != null ? parseGraph(def.getContent()) : null); // issues/05
            if (def != null) {
                try {
                    ProcessModel model = ModelParser.parse(def.getContent());
                    for (com.mldong.jeeflow.model.NodeModel node : model.getNodes()) {
                        if (task.getTaskName().equals(node.getName())) {
                            Map<String, Object> tm = new LinkedHashMap<>();
                            tm.put("name", node.getName());
                            tm.put("displayName", node.getDisplayName());
                            tm.put("type", node.getClass().getSimpleName().replace("Model", "").toLowerCase());
                            vo.put("taskModel", tm);
                            break;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return ok(vo);
    }

    private Map<String, Object> jumpAbleTaskNameList(Map<String, Object> args) {
        Long instanceId = toLong(args.get("processInstanceId"));
        List<Map<String, Object>> rows = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        List<ProcessTask> done = repository.findDoneTasks(instanceId, null);
        for (ProcessTask t : done) {
            if (ProcessTaskPerformTypeEnum.COUNTERSIGN.equals(t.getPerformType())) continue;
            if (seen.add(t.getTaskName())) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("label", t.getDisplayName());
                item.put("value", t.getTaskName());
                rows.add(item);
            }
        }
        return ok(rows);
    }

    private Map<String, Object> candidatePage(Map<String, Object> args) {
        Long taskId = toLong(args.get(FlowConst.PROCESS_TASK_ID_KEY));
        if (taskId == null) taskId = toLong(args.get("id"));
        if (taskId == null) return error("processTaskId 缺失");
        ProcessTask task = repository.findTaskById(taskId);
        if (task == null) return error("任务不存在");
        ProcessInstance inst = repository.findInstanceById(task.getProcessInstanceId());
        if (inst == null) return error("流程实例不存在");
        ProcessInstance.ProcessDefine def = repository.findDefineById(inst.getDefineId());
        if (def == null) return error("流程定义不存在");
        List<Candidate> candidateList = null;
        try {
            ProcessModel model = ModelParser.parse(def.getContent());
            candidateList = model.getNextTaskModelCandidates(task.getTaskName());
        } catch (Exception ignored) {
        }
        if (candidateList != null && !candidateList.isEmpty()) {
            // 候选配置命中 → 用户信息映射（IUserSearchProvider 优先，其次 IUserProvider）
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Candidate c : candidateList) {
                Map<String, Object> u = null;
                if (userSearchProvider != null) {
                    u = userSearchProvider.findById(c.getActorId());
                }
                if (u == null) {
                    IUserProvider userProvider = com.mldong.jeeflow.core.ServiceContext.find(IUserProvider.class);
                    if (userProvider != null) {
                        IUserProvider.UserInfo info = userProvider.getUser(c.getActorId());
                        if (info != null) {
                            u = new LinkedHashMap<>();
                            u.put("userId", info.getUserId());
                            u.put("realName", info.getRealName());
                        }
                    }
                }
                if (u == null) {
                    u = new LinkedHashMap<>();
                    u.put("userId", c.getActorId());
                    u.put("realName", c.getActorId());
                }
                rows.add(u);
            }
            return pageResult(PageResult.of(1, 10, rows.size(), rows));
        }
        // 无模型候选 → 用户分页搜索（依赖 IUserSearchProvider）
        if (userSearchProvider == null) {
            return error("未配置 IUserSearchProvider（用户搜索钩子）");
        }
        return pageResult(userSearchProvider.page(queryParser.parse(args)));
    }

    private Map<String, Object> taskSurrogate(Map<String, Object> args) {
        Long taskId = toLong(args.get("processTaskId"));
        java.util.List<String> actors = toStringList(args.get("actorIds"));
        if (taskId == null || actors.isEmpty()) return error("processTaskId/actorIds 缺失");
        repository.addTaskActor(taskId, actors);
        return ok();
    }

    private Map<String, Object> taskAddCandidate(Map<String, Object> args) {
        return taskSurrogate(args);
    }

    private Map<String, Object> taskLatest(Map<String, Object> args) {
        Long instanceId = toLong(args.get("processInstanceId"));
        List<ProcessTask> doing = repository.findDoingTasks(instanceId, null);
        if (doing.isEmpty()) return ok(null);
        return ok(taskVo(doing.get(0)));
    }

    private static java.util.List<String> toStringList(Object val) {
        java.util.List<String> list = new ArrayList<>();
        if (val instanceof java.util.Collection) {
            for (Object o : (java.util.Collection<?>) val) list.add(String.valueOf(o));
        } else if (val instanceof String && !((String) val).isEmpty()) {
            for (String s : ((String) val).split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) list.add(t);
            }
        }
        return list;
    }

    // ═══ 流程设计（需扩展仓储） ═══

    private Map<String, Object> designPage(Map<String, Object> args) {
        PageQuery query = queryParser.parse(args);
        PageResult<ProcessDesign> page = ext().pageDesigns(query);
        return pageResult(page);
    }

    private Map<String, Object> designDetail(Map<String, Object> args) {
        Long id = toLong(args.get("id"));
        ProcessDesign design = ext().findDesignById(id);
        if (design == null) return error("流程设计不存在");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", design.getId());
        data.put("name", design.getName());
        data.put("displayName", design.getDisplayName());
        data.put("type", design.getType());
        data.put("icon", design.getIcon());
        data.put("isDeployed", design.getIsDeployed());
        data.put("remark", design.getRemark());
        // 最新设计稿内容 + 历史列表
        List<ProcessDesignHis> hisList = ext().listDesignHis(id);
        if (!hisList.isEmpty()) {
            data.put("jsonObject", parseGraph(hisList.get(0).getContent()));
        }
        data.put("his", hisList);
        return ok(data);
    }

    private Map<String, Object> designSave(Map<String, Object> args) {
        IProcessExtRepository ext = ext();
        String operator = toStr(args.get("operator"), "user1");
        Long id = toLong(args.get("id"));
        ProcessDesign design;
        if (id == null) {
            design = new ProcessDesign();
            design.setName(toStr(args.get("name")));
            design.setDisplayName(toStr(args.get("displayName")));
            design.setType(toStr(args.get("type"), "approval"));
            design.setIcon(toStr(args.get("icon")));
            design.setRemark(toStr(args.get("remark")));
            design.setIsDeployed(0);
            design.setCreateUser(operator);
            design.setUpdateUser(operator);
            ext.saveDesign(design);
        } else {
            design = ext.findDesignById(id);
            if (design == null) return error("流程设计不存在");
            if (args.get("displayName") != null) design.setDisplayName(toStr(args.get("displayName")));
            if (args.get("type") != null) design.setType(toStr(args.get("type")));
            if (args.get("icon") != null) design.setIcon(toStr(args.get("icon")));
            if (args.get("remark") != null) design.setRemark(toStr(args.get("remark")));
            design.setUpdateUser(operator);
            ext.updateDesign(design);
        }
        // 内容快照（设计稿内容存历史表）
        byte[] content = contentBytes(args);
        if (content != null) {
            ProcessDesignHis his = new ProcessDesignHis();
            his.setProcessDesignId(design.getId());
            his.setContent(content);
            his.setCreateUser(operator);
            ext.saveDesignHis(his);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", design.getId());
        return ok(data);
    }

    private Map<String, Object> designRemove(Map<String, Object> args) {
        Long id = toLong(args.get("id"));
        ext().removeDesign(id);
        return ok();
    }

    private Map<String, Object> designDeploy(Map<String, Object> args) {
        IProcessExtRepository ext = ext();
        Long designId = toLong(args.get("id"));
        ProcessDesign design = ext.findDesignById(designId);
        if (design == null) return error("流程设计不存在");
        List<ProcessDesignHis> hisList = ext.listDesignHis(designId);
        if (hisList.isEmpty()) return error("流程设计没有内容，无法发布");
        byte[] bytes = hisList.get(0).getContent();
        ProcessModel model = ModelParser.parse(bytes);
        Long defineId = saveDeployedDefine(model, bytes);
        design.setIsDeployed(1);
        design.setUpdateUser(toStr(args.get("operator"), "system"));
        ext.updateDesign(design);
        return ok(Map.of(FlowConst.PROCESS_DEFINE_ID_KEY, defineId));
    }

    // ═══ 委托代理（需扩展仓储） ═══

    private Map<String, Object> surrogatePage(Map<String, Object> args) {
        PageQuery query = queryParser.parse(args);
        PageResult<ProcessSurrogate> page = ext().pageSurrogates(query);
        return pageResult(page);
    }

    private Map<String, Object> surrogateSave(Map<String, Object> args) {
        IProcessExtRepository ext = ext();
        String operator = toStr(args.get("operator"), "user1");
        Long id = toLong(args.get("id"));
        ProcessSurrogate surrogate;
        if (id == null) {
            surrogate = new ProcessSurrogate();
            surrogate.setCreateUser(operator);
            surrogate.setCreateTime(LocalDateTime.now());
        } else {
            surrogate = ext.findSurrogateById(id);
            if (surrogate == null) return error("委托记录不存在");
        }
        surrogate.setProcessName(toStr(args.get("processName")));
        surrogate.setOperator(toStr(args.get("operator"))); // 授权人 = 操作人
        surrogate.setSurrogate(toStr(args.get("surrogate")));
        surrogate.setStartTime(parseTime(args.get("startTime")));
        surrogate.setEndTime(parseTime(args.get("endTime")));
        surrogate.setEnabled(toInt(args.get("enabled"), 1));
        surrogate.setUpdateUser(operator);
        if (id == null) {
            ext.saveSurrogate(surrogate);
        } else {
            ext.updateSurrogate(surrogate);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", surrogate.getId());
        return ok(data);
    }

    private Map<String, Object> surrogateRemove(Map<String, Object> args) {
        Long id = toLong(args.get("id"));
        ext().removeSurrogate(id);
        return ok();
    }

    // ═══ 内部工具 ═══

    private IProcessExtRepository ext() {
        if (extRepository == null) {
            throw new IllegalStateException("未配置 IProcessExtRepository（扩展仓储）");
        }
        return extRepository;
    }

    /** deploy 版本管理（对齐 boot3）：按 name 查最新定义，存在 version+1 插新记录，否则从 0 起 */
    private Long saveDeployedDefine(ProcessModel model, byte[] bytes) {
        PageQuery query = new PageQuery(1, 1);
        query.add("t.name", "EQ", model.getName());
        query.add("t.state", "GT", -1); // 不过滤状态（含停用）
        PageResult<IProcessRepository.DefineRow> page = repository.pageDefines(query);
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        int version = 0;
        if (page.getRows() != null && !page.getRows().isEmpty()) {
            Integer latest = page.getRows().get(0).getVersion();
            version = (latest != null ? latest : 0) + 1;
        }
        def.setName(model.getName());
        def.setDisplayName(model.getDisplayName());
        def.setType(model.getType());
        def.setState(1);
        def.setContent(bytes);
        def.setVersion(version);
        repository.saveDefine(def);
        return def.getId();
    }

    private byte[] contentBytes(Map<String, Object> args) {
        Object content = args.get("content");
        if (content == null) return null;
        if (content instanceof byte[]) return (byte[]) content;
        return content.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Map<String, Object> parseGraph(byte[] content) {
        if (content == null) return null;
        try {
            IJsonProvider json = com.mldong.jeeflow.core.ServiceContext.find(IJsonProvider.class);
            if (json == null) return null;
            return json.fromJson(new String(content, StandardCharsets.UTF_8), Map.class);
        } catch (Exception ignored) {
        }
        return null;
    }

    private Map<String, Object> taskVo(ProcessTask t) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", t.getTaskId());
        vo.put("processInstanceId", t.getProcessInstanceId());
        vo.put("taskName", t.getTaskName());
        vo.put("displayName", t.getDisplayName());
        vo.put("taskType", t.getTaskType());
        vo.put("performType", t.getPerformType());
        vo.put("taskState", t.getTaskState());
        vo.put("operator", t.getActorId());
        vo.put("formKey", t.getFormKey());
        vo.put("taskParentId", t.getParentTaskId());
        vo.put("taskActorIdList", t.getActorIds());
        return vo;
    }

    // ═══ 响应工具（boot2 CommonResult：code=0 成功 / 99999999 失败）═══

    private Map<String, Object> ok() {
        return ok(null);
    }

    private Map<String, Object> ok(Object data) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 0);
        r.put("msg", "成功");
        r.put("data", data);
        return r;
    }

    private Map<String, Object> error(String msg) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 99999999);
        r.put("msg", msg);
        return r;
    }

    private Map<String, Object> pageResult(PageResult<?> page) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pageNum", page.getPageNum());
        data.put("pageSize", page.getPageSize());
        data.put("recordCount", page.getRecordCount());
        data.put("totalPage", page.getTotalPage());
        List<Map<String, Object>> rows = new ArrayList<>();
        if (page.getRows() != null) {
            for (Object row : page.getRows()) {
                if (row instanceof IProcessRepository.TaskRow) {
                    rows.add(taskRowToMap((IProcessRepository.TaskRow) row));
                } else if (row instanceof IProcessRepository.InstanceRow) {
                    rows.add(instanceRowToMap((IProcessRepository.InstanceRow) row));
                } else if (row instanceof IProcessRepository.DefineRow) {
                    rows.add(defineRowToMap((IProcessRepository.DefineRow) row));
                } else if (row instanceof ProcessSurrogate) {
                    rows.add(surrogateRowToMap((ProcessSurrogate) row));
                } else {
                    rows.add(beanToMap(row));
                }
            }
        }
        data.put("rows", rows);
        return ok(data);
    }

    // ═══ 行输出转换（issues/05-2 字段契约 + 05-3 时间格式）═══

    private static final java.time.format.DateTimeFormatter TIME_FMT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static String fmtTime(java.time.LocalDateTime t) {
        return t != null ? t.format(TIME_FMT) : null;
    }

    /** 实例行：ext（实例变量对象）+ displayName/version（定义显示名/版本），时间格式化 */
    private Map<String, Object> instanceRowToMap(IProcessRepository.InstanceRow r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("parentId", r.getParentId());
        m.put("processDefineId", r.getProcessDefineId());
        m.put("state", r.getState());
        m.put("parentNodeName", r.getParentNodeName());
        m.put("businessNo", r.getBusinessNo());
        m.put("operator", r.getOperator());
        m.put("expireTime", fmtTime(r.getExpireTime()));
        m.put("variable", r.getVariable());
        m.put("createTime", fmtTime(r.getCreateTime()));
        m.put("createUser", r.getCreateUser());
        m.put("updateTime", fmtTime(r.getUpdateTime()));
        m.put("updateUser", r.getUpdateUser());
        m.put("processDefineName", r.getProcessDefineName());
        m.put("processDefineDisplayName", r.getProcessDefineDisplayName());
        m.put("processDefineVersion", r.getProcessDefineVersion());
        m.put("ext", parseJsonMap(r.getVariable()));
        m.put("displayName", r.getProcessDefineDisplayName());
        m.put("version", r.getProcessDefineVersion());
        return m;
    }

    /** 任务行：ext（任务变量，空回退实例变量）+ instanceExt + version，时间格式化 */
    private Map<String, Object> taskRowToMap(IProcessRepository.TaskRow r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("processInstanceId", r.getProcessInstanceId());
        m.put("taskName", r.getTaskName());
        m.put("displayName", r.getDisplayName());
        m.put("taskType", r.getTaskType());
        m.put("performType", r.getPerformType());
        m.put("taskState", r.getTaskState());
        m.put("operator", r.getOperator());
        m.put("finishTime", fmtTime(r.getFinishTime()));
        m.put("expireTime", fmtTime(r.getExpireTime()));
        m.put("formKey", r.getFormKey());
        m.put("taskParentId", r.getTaskParentId());
        m.put("variable", r.getVariable());
        m.put("createTime", fmtTime(r.getCreateTime()));
        m.put("createUser", r.getCreateUser());
        m.put("updateTime", fmtTime(r.getUpdateTime()));
        m.put("updateUser", r.getUpdateUser());
        m.put("processDefineName", r.getProcessDefineName());
        m.put("processDefineDisplayName", r.getProcessDefineDisplayName());
        m.put("instanceVariable", r.getInstanceVariable());
        m.put("instanceCreateTime", fmtTime(r.getInstanceCreateTime()));
        Map<String, Object> instanceExt = parseJsonMap(r.getInstanceVariable());
        Map<String, Object> ext = parseJsonMap(r.getVariable());
        if (ext.isEmpty()) ext = instanceExt;
        m.put("ext", ext);
        m.put("instanceExt", instanceExt);
        m.put("version", r.getProcessDefineVersion());
        return m;
    }

    /** 定义行：时间格式化 */
    private Map<String, Object> defineRowToMap(IProcessRepository.DefineRow r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("name", r.getName());
        m.put("displayName", r.getDisplayName());
        m.put("type", r.getType());
        m.put("state", r.getState());
        m.put("version", r.getVersion());
        m.put("createTime", fmtTime(r.getCreateTime()));
        m.put("createUser", r.getCreateUser());
        m.put("updateTime", fmtTime(r.getUpdateTime()));
        m.put("updateUser", r.getUpdateUser());
        return m;
    }

    /** 委托行：时间格式化 */
    private Map<String, Object> surrogateRowToMap(ProcessSurrogate s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("processName", s.getProcessName());
        m.put("operator", s.getOperator());
        m.put("surrogate", s.getSurrogate());
        m.put("startTime", fmtTime(s.getStartTime()));
        m.put("endTime", fmtTime(s.getEndTime()));
        m.put("enabled", s.getEnabled());
        m.put("createTime", fmtTime(s.getCreateTime()));
        m.put("createUser", s.getCreateUser());
        m.put("updateTime", fmtTime(s.getUpdateTime()));
        m.put("updateUser", s.getUpdateUser());
        return m;
    }

    /** JSON 字符串 → Map（坏 JSON 返回空 Map） */
    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isEmpty()) return new LinkedHashMap<>();
        try {
            IJsonProvider provider = com.mldong.jeeflow.core.ServiceContext.find(IJsonProvider.class);
            if (provider == null) return new LinkedHashMap<>();
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) provider.fromJson(json, Map.class);
            return m != null ? new LinkedHashMap<>(m) : new LinkedHashMap<>();
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    /** 对象转 map（简单 getter 反射兜底） */
    private Map<String, Object> beanToMap(Object bean) {
        Map<String, Object> m = new LinkedHashMap<>();
        try {
            for (java.lang.reflect.Method method : bean.getClass().getMethods()) {
                if (method.getName().startsWith("get") && method.getParameterCount() == 0
                        && !"getClass".equals(method.getName())) {
                    String key = Character.toLowerCase(method.getName().charAt(3))
                            + method.getName().substring(4);
                    m.put(key, method.invoke(bean));
                }
            }
        } catch (Exception ignored) {
        }
        return m;
    }

    private static Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).longValue();
        try { return Long.parseLong(val.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static Integer toInt(Object val, int def) {
        if (val == null) return def;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); } catch (NumberFormatException e) { return def; }
    }

    private static String toStr(Object val) {
        return val != null ? val.toString() : null;
    }

    private static String toStr(Object val, String def) {
        String s = toStr(val);
        return s != null ? s : def;
    }

    private static LocalDateTime parseTime(Object val) {
        if (val == null) return null;
        try { return LocalDateTime.parse(val.toString()); } catch (Exception e) { return null; }
    }
}
