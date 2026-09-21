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

import java.util.Collections;
import com.mldong.jeeflow.domain.Candidate;
import com.mldong.jeeflow.model.TaskModel;
import com.mldong.jeeflow.enums.CountersignTypeEnum;
import com.mldong.jeeflow.spi.PageQuery;
import com.mldong.jeeflow.spi.PageResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    // ── 统计用常量 ──
    private static final List<Integer> DEFAULT_STATE_IN = Arrays.asList(10, 20, 30, 40, 45, 50);
    private static final int DEFAULT_STATS_LIMIT = 10;
    private static final Set<String> VALID_GRANULARITY = new HashSet<>(Arrays.asList("hour", "day", "week", "month"));
    private static final Set<String> VALID_DIMENSION = new HashSet<>(Arrays.asList(
            "state", "define", "category", "approver", "applicant",
            "node", "stuckNode", "stuckApprover", "durationBucket"));

    /** 注入用户搜索钩子（candidatePage 无模型候选时的用户分页搜索） */
    public JeeflowFacade setUserSearchProvider(IUserSearchProvider provider) {
        this.userSearchProvider = provider;
        return this;
    }

    public JeeflowFacade(JeeflowEngine engine, IProcessRepository repository, IProcessExtRepository extRepository) {
        this.engine = engine;
        this.repository = repository;
        this.extRepository = extRepository;
        publishExtRepository(extRepository);
    }

    /**
     * 把扩展仓储暴露给引擎（issues/116）：委托代理自动生效由引擎内置实现，它只认
     * {@link ServiceContext} 里的 {@code IProcessExtRepository}。而多数集成方（boot 系薄壳、demo）
     * 是 {@code new JeeflowFacade(engine, repo, new JdbcProcessExtRepository(ds))} 直接构造、
     * 并不注册 bean——不桥这一步，委托就永远查不到仓储（表现为"能力又消失了"）。
     *
     * <p>仅在引擎侧尚未有该 SPI 时注册，不覆盖集成方自己的注册。上下文未初始化（纯单测构造门面）
     * 时静默跳过——缺扩展仓储属于正常部署形态，委托自动生效会跟着静默跳过，不打断建单。</p>
     */
    private static void publishExtRepository(IProcessExtRepository extRepository) {
        if (extRepository == null || ServiceContext.getContext() == null) return;
        if (ServiceContext.find(IProcessExtRepository.class) != null) return;
        ServiceContext.put("ext", extRepository);
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
                case "processDesign/update": return designUpdate(args);
                case "processDesign/updateDefine": return designUpdateDefine(args);
                case "processDesign/remove": return designRemove(args);
                case "processDesign/deploy": return designDeploy(args);
                case "processDesign/redeploy": return designRedeploy(args);
                case "processDesign/listByType": return designListByType(args);   // issues/28
                case "processInstance/bizData": return bizData(args);             // issues/28
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
                case "processTask/transfer": return taskTransfer(args);          // issues/115
                case "processTask/latest": return taskLatest(args);
                // ── 委托代理（需扩展仓储）──
                case "processSurrogate/page": return surrogatePage(args);
                case "processSurrogate/save": return surrogateSave(args);
                case "processSurrogate/update": return surrogateUpdate(args);   // issues/77
                case "processSurrogate/detail": return surrogateDetail(args);   // issues/77
                case "processSurrogate/remove": return surrogateRemove(args);
                // ── 统计 ──
                case "processInstance/stats/overview": return statsOverview(args);
                case "processInstance/stats/trend": return statsTrend(args);
                case "processInstance/stats/group": return statsGroup(args);
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
            repository.addTaskActor(task.getTaskId(), Collections.singletonList(operator));
            flowArgs.put(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.APPLY.getCode());
            // 对齐 boot3：f_nextNodeOperator（发起时预指派人）→ tf_nextNodeOperator（引擎执行参数）
            Object startNextOp = flowArgs.get(FlowConst.PROCESS_START_NEXT_NODE_OPERATOR);
            if (startNextOp != null && !startNextOp.toString().isEmpty()) {
                flowArgs.put(FlowConst.NEXT_NODE_OPERATOR, startNextOp);
            }
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
        return ok(Collections.singletonMap(FlowConst.PROCESS_DEFINE_ID_KEY, defineId));
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
        // issues/28：兼容 {ids} 批量与单 {id}（空值报错见 idListArgs）
        for (Long id : idListArgs(args)) {
            repository.removeDefine(id);
        }
        return ok();
    }

    private Map<String, Object> defineUpAndDown(Map<String, Object> args) {
        // issues/28：兼容 {ids, opType} 批量（boot3 前端 IdsParam 惯例）与单 {id, state}
        Object stateObj = args.get("opType") != null ? args.get("opType") : args.get("state");
        int state = Integer.parseInt(stateObj.toString());
        for (Long id : idListArgs(args)) {
            repository.updateDefineState(id, state);
        }
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
        data.put("formData", formDataOf(inst.getVariables(), FlowConst.FORM_DATA_PREFIX)); // issues/15
        data.put("createTime", String.valueOf(inst.getCreateTime()));
        data.put("createUser", inst.getCreateUser());
        ProcessInstance.ProcessDefine def0 = repository.findDefineById(inst.getDefineId());
        if (def0 != null) {
            data.put("displayName", def0.getDisplayName()); // issues/15
            data.put("name", def0.getName());
            data.put("version", def0.getVersion());
        }
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
        // issues/114：operator 硬必填——严禁缺省回落 user1 等固定账号（撤回人会被静默记成
        // 别人，update_user 与审计链一起失真且不报错）。msg 跨栈统一「operator 必填」。
        String operator = toStr(args.get("operator"));
        if (StringUtils.isBlank(operator)) return error("operator 必填");
        ProcessInstance inst = repository.findInstanceById(instanceId);
        if (inst == null) return error("流程实例不存在");
        if (!canWithdraw(inst, operator)) return error("无权限撤回该流程实例");
        inst.withdraw(operator);
        repository.updateInstance(inst); // v1.0.1：级联持久化任务状态
        return ok();
    }

    /**
     * 撤回归属判据（issues/114，命中任一即放行，全不命中拒绝）：
     *
     * <ol>
     *   <li>{@code operator} = 实例发起人（{@code wf_process_instance.operator}）——
     *       <b>不可复用 {@link ProcessTask#isAllowed}</b>：各语言引擎的 isAllowed 只判
     *       "operator 在不在该任务 actorIds"，从不查实例发起人，这一支必须显式补；</li>
     *   <li>{@code operator} 是该实例任一<b>进行中</b>任务的参与者
     *       （{@code wf_process_task_actor.actor_id}，以参与者表为准，不用聚合副本）；</li>
     *   <li>{@code operator} ∈ {{@code flow.auto}, {@code flow.admin}}（沿用 isAllowed 既有放行约定）。</li>
     * </ol>
     */
    private boolean canWithdraw(ProcessInstance inst, String operator) {
        if (isPrivilegedOperator(operator)) return true;
        if (operator.equals(inst.getOperator())) return true;
        List<ProcessTask> doingTasks = repository.findDoingTasks(inst.getInstanceId(), new String[]{});
        for (ProcessTask task : doingTasks) {
            if (repository.findTaskActors(task.getTaskId()).contains(operator)) return true;
        }
        return false;
    }

    /** 系统代执行（flow.auto）/ 超级管理员（flow.admin）放行——isAllowed 既有约定，撤回/转办共用 */
    private static boolean isPrivilegedOperator(String operator) {
        return FlowConst.AUTO_ID.equalsIgnoreCase(operator) || FlowConst.ADMIN_ID.equalsIgnoreCase(operator);
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
        Map<String, Object> nodeProgress = new LinkedHashMap<>();
        if (def != null) {
            try {
                ProcessModel model = ModelParser.parse(def.getContent());
                nodeProgress = buildNodeProgress(model, history);
                collectPath(model.getStart(), activeNodeNames, historyNodeNames, historyEdgeNames,
                        new java.util.HashSet<>(), inst.getVariables(), history);
            } catch (Exception ignored) {
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("activeNodeNames", activeNodeNames);
        data.put("historyNodeNames", historyNodeNames);
        data.put("historyEdgeNames", historyEdgeNames);
        data.put("nodeProgress", nodeProgress);
        return ok(data);
    }

    /** 节点成员进度（issue 41，对齐 Node/Go/Python）：按任务状态组装 nodeProgress——
     *  会签节点带 type（PARALLEL/SEQUENTIAL），成员 done 按完成状态逐人标记、active 为进行中
     *  当前位；动态参与人（无静态 actorIds）不返回；name 走 IUserProvider SPI 解析
     *  （未注册/查不到缺省空串，前端降级显示 id）。完整成员列表优先取会签任务变量
     *  operatorList_{node}（串行会签逐个创建时仅存已建任务，需从变量还原全量办理人——对齐
     *  Go/Python/Node buildNodeProgress），否则回退任务 actorIds 并集 */
    private Map<String, Object> buildNodeProgress(ProcessModel model, List<ProcessTask> history) {
        Map<String, Object> progress = new LinkedHashMap<>();
        List<String> names = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ProcessTask t : history) {
            if (seen.add(t.getTaskName())) names.add(t.getTaskName());
        }
        IUserProvider userProvider = ServiceContext.find(IUserProvider.class);
        for (String name : names) {
            List<ProcessTask> ts = history.stream()
                    .filter(t -> name.equals(t.getTaskName())).collect(Collectors.toList());
            if (ts.isEmpty()) continue;
            // 完整成员列表：会签串行任务变量 operatorList_{node} 优先（逐个创建时仅 1 个任务，
            // 全量办理人存于其变量），否则任务 actorIds 并集（对齐 Go/Python/Node）
            List<String> csMembers = readCountersignOperatorList(ts, name);
            List<String> members;
            if (!csMembers.isEmpty()) {
                members = csMembers;
            } else {
                Set<String> memberSet = new LinkedHashSet<>();
                for (ProcessTask t : ts) memberSet.addAll(t.getActorIds());
                members = new ArrayList<>(memberSet);
            }
            if (members.isEmpty()) continue; // 动态参与人：无静态成员，不返回
            Set<String> doneSet = new HashSet<>();
            for (ProcessTask t : ts) {
                if (ProcessTaskStateEnum.FINISHED.getCode().equals(t.getTaskState())) {
                    doneSet.addAll(t.getActorIds());
                }
            }
            String activeActor = null;
            for (ProcessTask t : ts) {
                if (ProcessTaskStateEnum.DOING.getCode().equals(t.getTaskState())
                        && !t.getActorIds().isEmpty()) {
                    activeActor = t.getActorIds().get(0);
                    break;
                }
            }
            // 会签判定：模型节点属性（TaskParser codeOf 已兼容 'ALL' 字符串，issue 42）
            boolean isCs = false;
            String csType = null;
            NodeModel node = model.getNode(name);
            if (node instanceof TaskModel) {
                TaskModel tm = (TaskModel) node;
                isCs = ProcessTaskPerformTypeEnum.COUNTERSIGN.equals(tm.getPerformType());
                if (tm.getCountersignType() != null) {
                    csType = tm.getCountersignType().name();
                }
            }
            List<Map<String, Object>> memberList = new ArrayList<>();
            for (String id : members) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", id);
                m.put("name", resolveUserName(userProvider, id));
                if (doneSet.contains(id)) m.put("done", true);
                else if (id.equals(activeActor)) m.put("active", true);
                memberList.add(m);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("members", memberList);
            if (isCs && csType != null) item.put("type", csType);
            progress.put(name, item);
        }
        return progress;
    }

    /** 会签全量办理人：从任务变量 operatorList_{node} 还原（issues/93 串行逐个创建时仅 1 个任务，
     *  全量办理人存于该任务变量）。遍历节点全部任务，返回第一个非空列表（首位任务必带）；
     *  兼容 JSON 反序列化后的 Collection 形态 */
    private List<String> readCountersignOperatorList(List<ProcessTask> ts, String name) {
        String key = FlowConst.COUNTERSIGN_OPERATOR_LIST + "_" + name;
        for (ProcessTask t : ts) {
            if (t.getVariables() == null) continue;
            Object value = t.getVariables().get(key);
            if (value instanceof java.util.Collection) {
                List<String> list = new ArrayList<>();
                for (Object o : (java.util.Collection<?>) value) {
                    String s = o == null ? null : o.toString().trim();
                    if (s != null && !s.isEmpty()) list.add(s);
                }
                if (!list.isEmpty()) return list;
            } else if (value != null) {
                String s = value.toString().trim();
                if (!s.isEmpty()) return Collections.singletonList(s);
            }
        }
        return Collections.emptyList();
    }

    /** 成员姓名解析（issue 43/E15）：IUserProvider SPI 解析 realName，查不到缺省空串 */
    private String resolveUserName(IUserProvider userProvider, String userId) {
        if (userProvider == null) return "";
        try {
            IUserProvider.UserInfo info = userProvider.getUser(userId);
            return info != null && StringUtils.isNotEmpty(info.getRealName()) ? info.getRealName() : "";
        } catch (Exception e) {
            return "";
        }
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
            vo.put("finishTime", fmtTime(t.getFinishTime()));
            vo.put("variable", t.getVariables());
            vo.put("ext", t.getVariables() != null ? t.getVariables() : new LinkedHashMap<>()); // issues/15
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
        // issues/82-5：任务级 ext.isFirstTaskNode（前端 detail.vue 双兜底 record.ext?.isFirstTaskNode）
        // 首个任务节点且进行中 → 前端可"重新提交"，与 instance detail 的 activeTaskList 行语义一致
        boolean doing = ProcessTaskStateEnum.DOING.getCode().equals(task.getTaskState());
        Map<String, Object> tExt = task.getVariables() != null
                ? new LinkedHashMap<>(task.getVariables()) : new LinkedHashMap<>();
        tExt.put("isFirstTaskNode", false);
        vo.put("ext", tExt);
        // taskModel：流程定义中对应节点（显示名/表单）
        ProcessInstance inst = repository.findInstanceById(task.getProcessInstanceId());
        if (inst != null) {
            ProcessInstance.ProcessDefine def = repository.findDefineById(inst.getDefineId());
            Map<String, Object> jsonObject = def != null ? parseGraph(def.getContent()) : null; // issues/05
            vo.put("jsonObject", jsonObject);
            if (def != null) {
                tExt.put("isFirstTaskNode", doing && task.getTaskName().equals(firstTaskNodeId(jsonObject)));
                try {
                    ProcessModel model = ModelParser.parse(def.getContent());
                    for (com.mldong.jeeflow.model.NodeModel node : model.getNodes()) {
                        if (task.getTaskName().equals(node.getName())) {
                            Map<String, Object> tm = new LinkedHashMap<>();
                            tm.put("name", node.getName());
                            tm.put("displayName", node.getDisplayName());
                            tm.put("type", node.getClass().getSimpleName().replace("Model", "").toLowerCase());
                            // issues/62：taskModel 补 form/ext（节点字段权限，对齐 boot2 setTaskModel 整份挂载）
                            if (node instanceof TaskModel) {
                                TaskModel taskNode = (TaskModel) node;
                                tm.put("form", taskNode.getForm());
                                tm.put("ext", taskNode.getExt());
                            }
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
            // issues/80：行键对齐前端 UserSelect（valueField='id' / labelField='realName'），
            // 统一出口 {id, realName, userName?, deptName?}（userName/deptName 仅当 provider 可得）
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
                            if (info.getDeptName() != null) {
                                u.put("deptName", info.getDeptName());
                            }
                        }
                    }
                }
                if (u == null) {
                    u = new LinkedHashMap<>();
                    u.put("userId", c.getActorId());
                    u.put("realName", c.getActorId());
                }
                rows.add(candidateRow(c.getActorId(), u));
            }
            return pageResult(PageResult.of(1, 10, rows.size(), rows));
        }
        // 无模型候选 → 用户分页搜索（依赖 IUserSearchProvider）
        if (userSearchProvider == null) {
            return error("未配置 IUserSearchProvider（用户搜索钩子）");
        }
        return pageResult(userSearchProvider.page(queryParser.parse(args)));
    }

    /**
     * candidatePage 模型候选行键归一（issues/80）——对齐前端 UserSelect 契约 {id, realName, userName?, deptName?}。
     *
     * <p>provider 返回行可能用 userId/id 任一作主键、且未必带 realName，此处统一：
     * 主键收敛为 {@code id}（兼容 userId/id 两种来源），realName 缺失时回落 id，
     * userName/deptName 仅在 provider 提供时透传。前端 valueField='id' 取值不再为空。</p>
     */
    private Map<String, Object> candidateRow(String actorId, Map<String, Object> src) {
        Map<String, Object> row = new LinkedHashMap<>();
        Object id = src.get("id") != null ? src.get("id") : src.get("userId");
        if (id == null) {
            id = actorId;
        }
        row.put("id", String.valueOf(id));
        Object realName = src.get("realName");
        row.put("realName", realName != null ? realName : row.get("id"));
        // 兼容旧消费方：provider 若已给出 userId 键则原样保留
        if (src.get("userId") != null) {
            row.put("userId", src.get("userId"));
        }
        if (src.get("userName") != null) {
            row.put("userName", src.get("userName"));
        }
        if (src.get("deptName") != null) {
            row.put("deptName", src.get("deptName"));
        }
        return row;
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

    /**
     * 转办（issues/115）：摘原办理人 + 换新参与人，区别于 {@code processTask/surrogate} 加签的
     * "只追加"（加签后原人保留可办，本 action 会把待办从 A 挪到 B）。契约七条语义（spec 06
     * §processTask/transfer）逐条落地：
     *
     * <ol>
     *   <li><b>摘原人</b>：只删 {@code fromActor} 在该任务的 {@code wf_process_task_actor} 行，
     *       会签节点转的是"自己那一票"，其余成员不受影响；</li>
     *   <li><b>加新人</b>：{@code toActor} 追加为该任务参与人，办理规则不变；</li>
     *   <li><b>任务不新建</b>：沿用同一 {@code processTaskId}，高亮图/节点进度不变；</li>
     *   <li><b>留痕（三件，缺一不可）</b>：
     *     <ul>
     *       <li>任务行 {@code submitType=7}（TRANSFER）作"当前槽位"——{@code approvalRecord} 取实例全部任务行
     *           （不按状态过滤），故 B 办结前这条在审批历史里直接读作"转办"，B 办结后该槽位由 B 的办理动作
     *           （1/2/20）覆盖，属预期。<b>不写任务 {@code actor_id}/{@code operator} 列</b>（契约条款 4 的 ⚠️，
     *           Node 实测纠偏：进行中任务该列恒无值是家族不变量，写入被摘走的人会在撤回/终止后污染其"我已办"
     *           列表），办理人由 {@code update_user} + {@code tf_transferHistory[].operator} 承载；</li>
     *       <li>任务变量 {@code tf_transferHistory} 作<b>跨跳追加式账本</b>，每一跳 append 一条
     *           {@code {submitType, fromActor, toActor, reason, time, operator}}，只追加不覆盖
     *           （动机见下方行内注释）；单跳便捷键 {@code tf_transferTo} / {@code tf_transferReason}
     *           一并写，前端可省一次遍历；</li>
     *       <li>末跳可读文案写审批意见 {@code tf_approvalComment}（"A 转办给 B（原因…）"），
     *           走前端既有读取位（issues/15：approvalRecord 的 variable/ext）；多跳时它只留末跳，
     *           全量账本以 {@code tf_transferHistory} 为准。引擎不新增响应字段、不新造历史表。</li>
     *     </ul></li>
     *   <li><b>变量合并序</b>（本 action 留痕存活的前置条件）：办理提交走"实例变量 ← 任务既有变量 ←
     *       本次提交参数"，见 {@code ProcessTask.finish} 的 {@code variables.putAll(args)}——
     *       合并而非整体替换，故 {@code tf_transferHistory} 能活到办结后；</li>
     *   <li><b>去重</b>：{@code toActor} 已是参与者 / {@code fromActor} 不在参与者里 → 明确报错；</li>
     *   <li><b>前置态</b>：任务非进行中 → 明确报错。</li>
     * </ol>
     *
     * <p>鉴权：只能转自己那一条待办（{@code operator == fromActor}），
     * {@code flow.auto} / {@code flow.admin} 例外，与撤回同口径；operator 硬必填，msg 跨栈统一。</p>
     */
    private Map<String, Object> taskTransfer(Map<String, Object> args) {
        Long taskId = toLong(args.get(FlowConst.PROCESS_TASK_ID_KEY));
        String operator = toStr(args.get("operator"));
        if (StringUtils.isBlank(operator)) return error("operator 必填");
        String fromActor = toStr(args.get("fromActor"));
        String toActor = toStr(args.get("toActor"));
        if (StringUtils.isBlank(fromActor)) return error("fromActor 必填");
        if (StringUtils.isBlank(toActor)) return error("toActor 必填");
        ProcessTask task = taskId == null ? null : repository.findTaskById(taskId);
        if (task == null) return error("任务不存在");
        if (!isPrivilegedOperator(operator) && !operator.equals(fromActor)) return error("无权限转办该任务");
        // 前置态：仅进行中（DOING=10）任务可转办
        if (!task.isDoing()) return error("任务非进行中，不可转办");
        // 以参与者表为判据（聚合副本可能滞后于加签/转办的增量写入）
        List<String> actors = repository.findTaskActors(taskId);
        if (!actors.contains(fromActor)) return error("原办理人不是该任务参与人");
        if (actors.contains(toActor)) return error("目标人已是该任务参与人");
        // 摘原人（仅 fromActor 一行）+ 加新人（同一 taskId，不新建任务）
        repository.removeTaskActor(taskId, Collections.singletonList(fromActor));
        repository.addTaskActor(taskId, Collections.singletonList(toActor));
        // 留痕（契约第 4 条·三件）
        String reason = toStr(args.get("reason"));
        FlowData vars = task.getVariables() != null ? task.getVariables() : FlowData.create();
        // 账本为何必须是追加式列表而非单跳键：本家族审批记录的槽位就是任务行本身，B 办结时
        // submitType 会被 B 的办理参数覆盖；没有追加式账本，多跳转办只剩末跳、办结后转办事实整体消失。
        List<Object> history = new ArrayList<>();
        Object existing = vars.get(FlowConst.TRANSFER_HISTORY);
        if (existing instanceof Collection) {
            history.addAll((Collection<?>) existing);   // 只追加：既往各跳原样带过来，不重排不裁剪
        }
        Map<String, Object> hop = new LinkedHashMap<>();  // LinkedHashMap 锁定键序，与契约同序序列化
        hop.put("submitType", ProcessSubmitTypeEnum.TRANSFER.getCode());
        hop.put("fromActor", fromActor);
        hop.put("toActor", toActor);
        hop.put("reason", StringUtils.isNotBlank(reason) ? reason : "");
        // 账本 time 一律 "yyyy-MM-dd HH:mm:ss"（契约条款 4 + spec §2.4 全生态时间格式，Go/Python/Node/PHP/Rust/C# 同形）：
        // 不得用 LocalDateTime.toString() 的 ISO 方言（带 T 和小数秒），也不塞时刻对象（序列化方言差异）——账本要跨栈同形
        hop.put("time", LocalDateTime.now().format(TIME_FMT));
        hop.put("operator", operator);
        history.add(hop);
        vars.put(FlowConst.TRANSFER_HISTORY, history);
        // 当前槽位（契约第 4 条①）+ 单跳便捷键（②的另一半）
        vars.put(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.TRANSFER.getCode());
        vars.put(FlowConst.TRANSFER_TO, toActor);
        vars.put(FlowConst.TRANSFER_REASON, StringUtils.isNotBlank(reason) ? reason : "");
        // 末跳可读文案（契约第 4 条③）：多跳时它只留末跳，全量以 tf_transferHistory 为准
        String transferText = fromActor + " " + ProcessSubmitTypeEnum.TRANSFER.getMessage() + "给 " + toActor
                + (StringUtils.isNotBlank(reason) ? "（" + reason + "）" : "");
        vars.put(FlowConst.APPROVAL_COMMENT, transferText);
        task.setVariables(vars);
        // ⚠️ 严禁覆写任务 actor_id/operator 列（契约条款 4 的 ⚠️，Node 实测纠偏）：进行中任务该列恒无值是
        // 本家族既有不变量，而 pageDoneTasks 按 state <> 10 AND operator = ? 过滤——把被摘走的原人写进这一列，
        // 该单一旦撤回/终止（离开 DOING 但列值留着）会凭空出现在他从没办过的「我已办」列表里。
        // "办理人记谁"改由 update_user（下行）+ tf_transferHistory[].operator 账本承载。
        task.setUpdateUser(operator);
        task.setUpdateTime(LocalDateTime.now());
        // 仓储 updateTask 会以任务副本的 actorIds 全量覆写参与者行（内存/JDBC 同语义），
        // 必须同步为摘/加之后的最新集合，否则留痕落库时把旧参与者原样写回
        task.setActorIds(new ArrayList<>(repository.findTaskActors(taskId)));
        repository.updateTask(task);
        return ok();
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
        Map<String, Object> jsonObject = null;
        if (!hisList.isEmpty()) {
            jsonObject = parseGraph(hisList.get(0).getContent());
        }
        // issues/07：jsonObject 缺失基本信息时从设计表补齐（对齐 boot3 ProcessDesignServiceImpl.findById）
        if (jsonObject == null) jsonObject = new LinkedHashMap<>();
        if (!jsonObject.containsKey("name")) jsonObject.put("name", design.getName());
        if (!jsonObject.containsKey("displayName")) jsonObject.put("displayName", design.getDisplayName());
        if (!jsonObject.containsKey("type")) jsonObject.put("type", design.getType());
        if (!jsonObject.containsKey("processDesignId")) jsonObject.put("processDesignId", design.getId());
        data.put("jsonObject", jsonObject);
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
            // 内容快照变更 → 置为未部署（对齐 boot3 updateDefine 语义，issues/08）
            if (contentBytes(args) != null) design.setIsDeployed(0);
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
        // issues/28：兼容 {ids} 批量（boot3 前端 IdsParam 惯例）与单 {id}
        for (Long id : idListArgs(args)) {
            ext().removeDesign(id);
        }
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
        return ok(Collections.singletonMap(FlowConst.PROCESS_DEFINE_ID_KEY, defineId));
    }

    /** 修改流程设计基本信息（对齐 boot3 ProcessDesignController.update，不写设计稿快照） */
    private Map<String, Object> designUpdate(Map<String, Object> args) {
        IProcessExtRepository ext = ext();
        Long id = toLong(args.get("id"));
        ProcessDesign design = ext.findDesignById(id);
        if (design == null) return error("流程设计不存在");
        if (args.get("name") != null) design.setName(toStr(args.get("name")));
        if (args.get("displayName") != null) design.setDisplayName(toStr(args.get("displayName")));
        if (args.get("type") != null) design.setType(toStr(args.get("type")));
        if (args.get("icon") != null) design.setIcon(toStr(args.get("icon")));
        if (args.get("remark") != null) design.setRemark(toStr(args.get("remark")));
        design.setUpdateUser(toStr(args.get("operator"), "system"));
        ext.updateDesign(design);
        return ok();
    }

    /** 更新流程设计定义（设计稿保存，issues/08）：content 快照入库 + 同步 name/displayName/type + 置未部署 */
    private Map<String, Object> designUpdateDefine(Map<String, Object> args) {
        IProcessExtRepository ext = ext();
        Long designId = toLong(args.get("processDesignId"));
        ProcessDesign design = ext.findDesignById(designId);
        if (design == null) return error("流程设计不存在");
        byte[] bytes = contentBytes(args);
        if (bytes == null) return error("content 缺失");
        // 与最新一条相同则不重复入库（对齐 boot3 updateDefine）
        List<ProcessDesignHis> hisList = ext.listDesignHis(designId);
        if (hisList.isEmpty() || !Arrays.equals(hisList.get(0).getContent(), bytes)) {
            ProcessDesignHis his = new ProcessDesignHis();
            his.setProcessDesignId(designId);
            his.setContent(bytes);
            his.setCreateUser(toStr(args.get("operator"), "system"));
            ext.saveDesignHis(his);
        }
        // 同步设计基本信息（jsonObject 里的 name/displayName/type）+ 内容变更 → 未部署
        try {
            ProcessModel model = ModelParser.parse(bytes);
            design.setName(model.getName());
            design.setDisplayName(model.getDisplayName());
            design.setType(model.getType());
        } catch (Exception ignored) {
        }
        design.setIsDeployed(0);
        design.setUpdateUser(toStr(args.get("operator"), "system"));
        ext.updateDesign(design);
        return ok();
    }

    /** 重新部署流程定义（issues/08）：替换最新定义内容 + 置已部署（对齐 boot3 redeploy） */
    private Map<String, Object> designRedeploy(Map<String, Object> args) {
        IProcessExtRepository ext = ext();
        Long designId = toLong(args.get("id"));
        ProcessDesign design = ext.findDesignById(designId);
        if (design == null) return error("流程设计不存在");
        List<ProcessDesignHis> hisList = ext.listDesignHis(designId);
        if (hisList.isEmpty()) return error("流程设计没有内容，无法发布");
        byte[] bytes = hisList.get(0).getContent();
        ProcessModel model = ModelParser.parse(bytes);
        // 按 name 取最新定义：有则替换内容（version 不变），无则新建（对齐 boot3 redeploy）
        PageQuery query = new PageQuery(1, 1);
        query.add("t.name", "EQ", model.getName());
        query.setOrderBy("t.version desc");
        PageResult<IProcessRepository.DefineRow> page = repository.pageDefines(query);
        Long defineId;
        if (page.getRows() == null || page.getRows().isEmpty()) {
            defineId = saveDeployedDefine(model, bytes);
        } else {
            IProcessRepository.DefineRow last = page.getRows().get(0);
            ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
            def.setId(last.getId());
            def.setName(model.getName());
            def.setDisplayName(model.getDisplayName());
            def.setType(model.getType());
            def.setContent(bytes);
            // issues/59：保留原 version（替换语义，不递增）；缺失时 JDBC 兜底会误写 1
            def.setVersion(last.getVersion());
            def.setUpdateUser(toStr(args.get("operator"), "system"));
            repository.updateDefine(def);
            defineId = last.getId();
        }
        design.setIsDeployed(1);
        design.setUpdateUser(toStr(args.get("operator"), "system"));
        ext.updateDesign(design);
        return ok(Collections.singletonMap(FlowConst.PROCESS_DEFINE_ID_KEY, defineId));
    }

    // ═══ issues/28：集成适配下沉 ═══

    /**
     * 按类型分组列出流程设计（发起申请页数据契约）——不依赖框架字典：
     * designPage 全量 → 按 type 分组 → 组内每 name 取最新 define 的
     * {processDefineId, name, displayName, icon, remark, jsonObject}。
     */
    private Map<String, Object> designListByType(Map<String, Object> args) {
        IProcessExtRepository ext = ext();
        PageQuery query = queryParser.parse(args);
        query.setPageNum(1);
        query.setPageSize(Integer.MAX_VALUE - 1);
        PageResult<ProcessDesign> page = ext.pageDesigns(query);
        // 每 name 最新 define（version 最大）
        PageQuery defQuery = new PageQuery(1, Integer.MAX_VALUE - 1);
        PageResult<IProcessRepository.DefineRow> defPage = repository.pageDefines(defQuery);
        Map<String, IProcessRepository.DefineRow> latestByName = new LinkedHashMap<>();
        for (IProcessRepository.DefineRow row : defPage.getRows()) {
            IProcessRepository.DefineRow prev = latestByName.get(row.getName());
            if (prev == null || row.getVersion() > prev.getVersion()) {
                latestByName.put(row.getName(), row);
            }
        }
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (ProcessDesign d : page.getRows()) {
            String type = d.getType() == null ? "" : d.getType();
            List<Map<String, Object>> items = groups.computeIfAbsent(type, k -> new ArrayList<>());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("processDesignId", d.getId());
            item.put("name", d.getName());
            item.put("displayName", d.getDisplayName());
            item.put("icon", d.getIcon());
            item.put("remark", d.getRemark());
            IProcessRepository.DefineRow latest = latestByName.get(d.getName());
            item.put("processDefineId", latest != null ? latest.getId() : null);
            item.put("processDefineState", latest != null ? latest.getState() : null);
            // jsonObject：最新设计稿内容（设计器回显）
            List<ProcessDesignHis> his = ext.listDesignHis(d.getId());
            if (!his.isEmpty()) {
                item.put("jsonObject", parseGraph(his.get(0).getContent()));
            }
            items.add(item);
        }
        return ok(groups);
    }

    /** 按流程实例回显业务数据（issues/28）：MetaTableReader 由集成方注册（issues/23），未注册明确报错 */
    private Map<String, Object> bizData(Map<String, Object> args) {
        Long processInstanceId = toLong(args.get("processInstanceId") != null
                ? args.get("processInstanceId") : args.get("id"));
        if (processInstanceId == null) return error("processInstanceId 缺失");
        // 表名：实例 → 定义 relTableName（回落流程 name）
        ProcessInstance inst = repository.findInstanceById(processInstanceId);
        if (inst == null) return error("流程实例不存在");
        ProcessInstance.ProcessDefine define = repository.findDefineById(inst.getDefineId());
        if (define == null) return error("流程定义不存在");
        String tableName = resolveRelTableName(define.getContent());
        if (tableName == null) return error("流程定义未配置 relTableName");
        // issues/28：MetaTableReader 由集成方注册（issues/23 约定注册名 metaTableReader），
        // core 不编译期依赖 persist——按名查找，未注册明确报错
        Object reader = com.mldong.jeeflow.core.ServiceContext.findByName("metaTableReader", Object.class);
        if (reader == null) return error("业务数据读取器未注册（ServiceContext.put(\"metaTableReader\", new MetaTableReader(...))，需引入 jeeflow-persist）");
        try {
            java.lang.reflect.Method m = reader.getClass().getMethod(
                    "readByProcessInstance", String.class, Object.class);
            Object result = m.invoke(reader, tableName, processInstanceId);
            return result == null ? ok() : ok(result);
        } catch (Exception e) {
            return error("业务数据读取失败: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
        }
    }

    /** 从流程定义 content 顶层解析 relTableName（缺省回落 name） */
    private String resolveRelTableName(byte[] content) {
        if (content == null) return null;
        try {
            IJsonProvider json = com.mldong.jeeflow.core.ServiceContext.find(IJsonProvider.class);
            if (json == null) return null;
            Map<String, Object> meta = json.fromJson(new String(content, StandardCharsets.UTF_8), Map.class);
            if (meta == null) return null;
            String tableName = meta.get("relTableName") != null
                    ? meta.get("relTableName").toString().trim() : null;
            if (tableName == null || tableName.isEmpty()) {
                tableName = meta.get("name") != null ? meta.get("name").toString().trim() : null;
            }
            return (tableName == null || tableName.isEmpty()) ? null : tableName;
        } catch (Exception ignored) {
        }
        return null;
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
            surrogate.setOperator(operator); // 授权人 = 操作人（新建必有）
        } else {
            surrogate = ext.findSurrogateById(id);
            if (surrogate == null) return error("委托记录不存在");
        }
        applySurrogateFields(surrogate, args, operator);
        if (id == null) {
            ext.saveSurrogate(surrogate);
        } else {
            ext.updateSurrogate(surrogate);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", surrogate.getId());
        return ok(data);
    }

    /** 委托更新（issues/77）：按 id 全字段更新，授权人缺省时保留原值（前端编辑表单不带 operator） */
    private Map<String, Object> surrogateUpdate(Map<String, Object> args) {
        IProcessExtRepository ext = ext();
        String operator = toStr(args.get("operator"), "user1");
        Long id = toLong(args.get("id"));
        if (id == null) return error("id 缺失");
        ProcessSurrogate surrogate = ext.findSurrogateById(id);
        if (surrogate == null) return error("委托记录不存在");
        applySurrogateFields(surrogate, args, operator);
        ext.updateSurrogate(surrogate);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", surrogate.getId());
        return ok(data);
    }

    /** 委托详情（issues/77）：按 id 查单条，返回行结构（时间格式化） */
    private Map<String, Object> surrogateDetail(Map<String, Object> args) {
        Long id = toLong(args.get("id"));
        ProcessSurrogate surrogate = ext().findSurrogateById(id);
        if (surrogate == null) return error("委托记录不存在");
        return ok(surrogateRowToMap(surrogate));
    }

    /** 委托写入公共字段。授权人（operator）仅在显式传入时覆盖，避免 update 时清空原授权人 */
    private void applySurrogateFields(ProcessSurrogate s, Map<String, Object> args, String operator) {
        s.setProcessName(toStr(args.get("processName")));
        if (args.containsKey("operator")) {
            s.setOperator(toStr(args.get("operator"))); // 授权人 = 操作人
        }
        s.setSurrogate(toStr(args.get("surrogate")));
        s.setStartTime(parseTime(args.get("startTime")));
        s.setEndTime(parseTime(args.get("endTime")));
        // issues/116：enabled 缺省=1（契约默认），显式传入但不可解析为整数的脏值按**停用**落库——
        // 「只有 1 生效」，脏值不得默认当启用（原 toInt(x, 1) 会把 "abc" 折叠成 1 而误生效）
        Object enabledArg = args.get("enabled");
        s.setEnabled(enabledArg == null ? Integer.valueOf(1) : toInt(enabledArg, 0));
        s.setUpdateUser(operator);
    }

    private Map<String, Object> surrogateRemove(Map<String, Object> args) {
        // issues/95：前端「我的委托」行内/批量删除统一发 {ids}，与 define/design remove 同惯例
        for (Long id : idListArgs(args)) {
            ext().removeSurrogate(id);
        }
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
        if (content == null) {
            // issues/27：兼容 boot3 顶层 JSON（无 content 字段，流程 JSON 顶层展开）——
            // 非保留字段（除 processDesignId/operator）整体序列化为 content
            Map<String, Object> copy = new LinkedHashMap<>(args);
            copy.remove("processDesignId");
            copy.remove("operator");
            if (copy.isEmpty()) return null;
            IJsonProvider json = com.mldong.jeeflow.core.ServiceContext.find(IJsonProvider.class);
            if (json != null) {
                content = json.toJson(copy);
            } else {
                return null;
            }
        }
        if (content instanceof Map || content instanceof Collection) {
            // content 为对象（前端直接传 JSON 对象）：序列化为 JSON 字符串
            IJsonProvider json = com.mldong.jeeflow.core.ServiceContext.find(IJsonProvider.class);
            if (json != null) return json.toJson(content).getBytes(StandardCharsets.UTF_8);
        }
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

    /** issues/15：表单数据派生——取 vars 中 prefix 前缀字段，输出「带前缀 + 去前缀副本」（对齐 boot3 getFormData/getTaskFormData） */
    private Map<String, Object> formDataOf(Map<String, Object> vars, String prefix) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (vars == null) return out;
        vars.forEach((k, v) -> {
            if (k != null && k.startsWith(prefix)) {
                out.put(k, v);
                out.put(k.substring(prefix.length()), v);
            }
        });
        return out;
    }

    private Map<String, Object> taskVo(ProcessTask t) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", t.getTaskId());
        vo.put("processInstanceId", t.getProcessInstanceId());
        vo.put("taskName", t.getTaskName());
        vo.put("displayName", t.getDisplayName());
        // issues/78：出口数字 code（对齐 Go/Python/Node/PHP），不吐枚举 name 字符串
        vo.put("taskType", t.getTaskType() != null ? t.getTaskType().getCode() : null);
        vo.put("performType", t.getPerformType() != null ? t.getPerformType().getCode() : null);
        vo.put("taskState", t.getTaskState());
        vo.put("operator", t.getActorId());
        vo.put("formKey", t.getFormKey());
        vo.put("taskParentId", t.getParentTaskId());
        vo.put("taskActorIdList", t.getActorIds());
        vo.put("taskFormData", formDataOf(t.getVariables(), FlowConst.TASK_FORM_DATA_PREFIX)); // issues/15
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
                } else if (row instanceof ProcessDesign) {
                    rows.add(designRowToMap((ProcessDesign) row));
                } else if (row instanceof Map) {
                    // 已转换的行数据（如 candidatePage 候选映射结果）直接透传
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) row;
                    rows.add(m);
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
        m.put("taskFormData", formDataOf(parseJsonMap(r.getVariable()), FlowConst.TASK_FORM_DATA_PREFIX)); // issues/15
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
    /** 流程设计行：时间格式化（processDesign/page） */
    private Map<String, Object> designRowToMap(ProcessDesign d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("name", d.getName());
        m.put("displayName", d.getDisplayName());
        m.put("type", d.getType());
        m.put("icon", d.getIcon());
        m.put("isDeployed", d.getIsDeployed());
        m.put("remark", d.getRemark());
        m.put("createTime", fmtTime(d.getCreateTime()));
        m.put("createUser", d.getCreateUser());
        m.put("updateTime", fmtTime(d.getUpdateTime()));
        m.put("updateUser", d.getUpdateUser());
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
        if (val instanceof Number) {
            Number n = (Number) val;
            if ((n instanceof Double || n instanceof Float) && Math.abs(n.doubleValue()) > 9.007199254740992E15) {
                throw new IllegalArgumentException("id " + n + " 超出 float64 精确范围（2^53），请以字符串传递");
            }
            return n.longValue();
        }
        try { return Long.parseLong(val.toString()); } catch (NumberFormatException e) { return null; }
    }

    /**
     * 删除/启停类 action 的批量主键：mldong IdsParam 惯例下 {@code {ids}} 数组优先，
     * 兼容单 {@code {id}}；两者皆缺失、空数组或含非法值一律报错（issues/95）。
     */
    private static List<Long> idListArgs(Map<String, Object> args) {
        Object ids = args.get("ids");
        List<Long> out = new ArrayList<>();
        if (ids instanceof Collection) {
            for (Object id : (Collection<?>) ids) {
                Long v = toLong(id);
                if (v == null) throw new IllegalArgumentException("id 缺失或非法");
                out.add(v);
            }
        } else {
            Long v = toLong(args.get("id"));
            if (v != null) out.add(v);
        }
        if (out.isEmpty()) throw new IllegalArgumentException("id 缺失或非法");
        return out;
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

    /** 解析时间入参：兼容 `yyyy-MM-dd HH:mm:ss`（前端 RangePicker/SPEC 契约）与 ISO `T`（issues/77） */
    private static LocalDateTime parseTime(Object val) {
        if (val == null) return null;
        String s = val.toString().trim();
        if (s.isEmpty()) return null;
        try { return LocalDateTime.parse(s, TIME_FMT); } catch (Exception ignored) { }
        try { return LocalDateTime.parse(s); } catch (Exception ignored) { }
        return null;
    }

    // ═══════════════════════════════════════
    // 统计 3 动作（issues/103）
    // ═══════════════════════════════════════

    /**
     * statsOverview：总览统计
     * 入参：start?, end?
     */
    private Map<String, Object> statsOverview(Map<String, Object> args) {
        LocalDateTime start = parseTime(args.get("start"));
        LocalDateTime end = parseTime(args.get("end"));
        // B：stateIn 入参（int[]，缺省 DEFAULT_STATE_IN），作用于六个状态计数
        List<Integer> stateIn = parseIntList(args.get("stateIn"));
        if (stateIn == null) stateIn = DEFAULT_STATE_IN;

        // 1. 实例各状态计数
        List<IProcessRepository.InstanceStatsRow> allInst =
                repository.queryInstancesForStats(stateIn, "create_time", start, end);
        Map<Integer, Long> instByState = allInst.stream()
                .collect(Collectors.groupingBy(IProcessRepository.InstanceStatsRow::getState, Collectors.counting()));
        // stats 计数字段一律 int 出参（issues/105）：集成层只对 Long 做 id 字符串化（2.3），
        // Long 计数会被误字符串化，Integer 不受影响且契约（06 §4.2）本就是 int
        int total      = allInst.size();
        int inProgress = instByState.getOrDefault(10, 0L).intValue();
        int completed  = instByState.getOrDefault(20, 0L).intValue();
        int withdrawn  = instByState.getOrDefault(30, 0L).intValue();
        int rejected   = instByState.getOrDefault(45, 0L).intValue();
        int suspended  = instByState.getOrDefault(50, 0L).intValue();

        // 2. todayNew：服务器当日创建的实例数（恒按当天，不受 start/end/stateIn 影响，对齐内置线 countTodayNew）
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.plusDays(1).atStartOfDay();
        List<IProcessRepository.InstanceStatsRow> todayInst =
                repository.queryInstancesForStats(null, "create_time", todayStart, todayEnd);
        int todayNew = todayInst.size();

        // 3. 待办 / 逾期（全量，不受时间范围约束——反映"当前"积压）
        int[] pendingOverdue = repository.statsPendingAndOverdueCount();
        int pendingTaskCount = pendingOverdue[0];
        int overdueTaskCount = pendingOverdue[1];

        // 4. 已完成任务聚合
        int[] taskAgg = repository.statsCompletedTaskAggregate();
        long taskTotal      = taskAgg[0];
        long countersign    = taskAgg[1];
        long onTime         = taskAgg[2];
        long onTimeDenom    = taskAgg[3];
        double countersignRate = taskTotal > 0 ? statsRound4((double) countersign / taskTotal) : 0.0;
        double onTimeRate   = onTimeDenom > 0 ? statsRound4((double) onTime / onTimeDenom) : 0.0;

        // 5. 平均完成时长
        int avgDurationSeconds = repository.statsAvgCompletedDurationSeconds(start, end);

        // 6. rejectRate
        double rejectRate = statsRound4((double) rejected / Math.max(1, completed + rejected));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("inProgress", inProgress);
        data.put("completed", completed);
        data.put("rejected", rejected);
        data.put("withdrawn", withdrawn);
        data.put("suspended", suspended);
        data.put("todayNew", todayNew);
        data.put("avgDurationSeconds", avgDurationSeconds);
        data.put("rejectRate", rejectRate);
        data.put("pendingTaskCount", pendingTaskCount);
        data.put("overdueTaskCount", overdueTaskCount);
        data.put("countersignRate", countersignRate);
        data.put("onTimeRate", onTimeRate);
        return ok(data);
    }

    /**
     * statsTrend：趋势统计
     * 入参：start, end, granularity（均必填）；返回 data 本体为裸数组（契约 spec 06 §4.2）
     */
    private Map<String, Object> statsTrend(Map<String, Object> args) {
        String granularity  = toStr(args.get("granularity"), "");
        LocalDateTime start = parseTime(args.get("start"));
        LocalDateTime end   = parseTime(args.get("end"));
        // C：start/end/granularity 均必填（对齐内置线 20010012 缺参语义）
        if (start == null || end == null || granularity.isEmpty()) {
            return error("trend 缺少必填参数：start/end/granularity");
        }
        if (!VALID_GRANULARITY.contains(granularity)) {
            return error("granularity 参数非法，允许值：hour/day/week/month");
        }

        // 查询时间范围内的实例（started，实例侧无 state 过滤，对齐内置线）
        List<IProcessRepository.InstanceStatsRow> insts =
                repository.queryInstancesForStats(null, "create_time", start, end);

        // 查询时间范围内的已完成任务（finished）
        List<IProcessRepository.TaskStatsRow> finishedTasks =
                repository.queryTasksForStats(20, start, end);

        // 枚举连续桶
        List<String> buckets = statsEnumerateBuckets(start, end, granularity);
        Map<String, int[]> bucketMap = new LinkedHashMap<>();
        for (String b : buckets) {
            bucketMap.put(b, new int[]{0, 0}); // [started, finished]
        }
        // 实例 → started
        for (IProcessRepository.InstanceStatsRow row : insts) {
            String bk = statsBucketKey(row.getCreateTime(), granularity);
            if (bk == null || !bucketMap.containsKey(bk)) continue;
            bucketMap.get(bk)[0]++;
        }
        // 已完成任务 → finished（按 finish_time 分桶）
        for (IProcessRepository.TaskStatsRow row : finishedTasks) {
            String bk = statsBucketKey(row.getFinishTime(), granularity);
            if (bk == null || !bucketMap.containsKey(bk)) continue;
            bucketMap.get(bk)[1]++;
        }

        // 组装返回：data 本体为裸数组（A：去掉 {granularity, series} 包装，对齐契约/前端/内置线）
        List<Map<String, Object>> series = new ArrayList<>();
        for (String b : buckets) {
            int[] counts = bucketMap.get(b);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("bucket", b);
            point.put("started", counts[0]);
            point.put("finished", counts[1]);
            series.add(point);
        }
        return ok(series);
    }

    /**
     * statsGroup：分组统计
     * 入参：start?, end?, dimension?, limit?
     */
    private Map<String, Object> statsGroup(Map<String, Object> args) {
        LocalDateTime start = parseTime(args.get("start"));
        LocalDateTime end   = parseTime(args.get("end"));
        String dimension    = toStr(args.get("dimension"), "define");
        int limit           = toInt(args.get("limit"), DEFAULT_STATS_LIMIT);
        if (!VALID_DIMENSION.contains(dimension)) {
            return error("dimension 参数非法，允许值：state/define/category/approver/applicant/node/stuckNode/stuckApprover/durationBucket");
        }

        List<Map<String, Object>> rows;
        switch (dimension) {
            case "define":
                rows = repository.statsDefineGroup(start, end, limit);
                break;
            case "state": {
                List<IProcessRepository.InstanceStatsRow> insts =
                        repository.queryInstancesForStats(null, "create_time", start, end);
                Map<Integer, int[]> grouped = new LinkedHashMap<>();
                for (IProcessRepository.InstanceStatsRow r : insts) {
                    grouped.computeIfAbsent(r.getState(), k -> new int[1])[0]++;
                }
                rows = statsGroupFromMap(grouped.entrySet().stream()
                        .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                        .limit(limit)
                        .collect(Collectors.toList()),
                        e -> String.valueOf(e.getKey()), e -> null);
                break;
            }
            case "category": {
                List<IProcessRepository.InstanceStatsRow> insts =
                        repository.queryInstancesForStats(null, "create_time", start, end);
                // Look up define.type via processDefineId
                Map<Long, String> instDefineTypes = new HashMap<>();
                for (IProcessRepository.InstanceStatsRow r : insts) {
                    Long defId = r.getProcessDefineId();
                    if (defId != null && !instDefineTypes.containsKey(defId)) {
                        ProcessInstance.ProcessDefine def = repository.findDefineById(defId);
                        instDefineTypes.put(defId, def != null ? (def.getType() != null ? def.getType() : "") : "");
                    }
                }
                Map<String, int[]> grouped = new LinkedHashMap<>();
                for (IProcessRepository.InstanceStatsRow r : insts) {
                    String type = instDefineTypes.getOrDefault(r.getProcessDefineId(), "");
                    grouped.computeIfAbsent(type, k -> new int[1])[0]++;
                }
                rows = statsGroupFromMap(grouped.entrySet().stream()
                        .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                        .limit(limit)
                        .collect(Collectors.toList()),
                        Map.Entry::getKey, e -> null);
                break;
            }
            case "approver": {
                List<IProcessRepository.TaskStatsRow> tasks =
                        repository.queryTasksForStats(20, start, end);
                Map<String, int[]> grouped = new LinkedHashMap<>();
                for (IProcessRepository.TaskStatsRow r : tasks) {
                    String op = r.getOperator();
                    if (op == null || op.isEmpty()) continue;
                    grouped.computeIfAbsent(op, k -> new int[1])[0]++;
                }
                rows = statsGroupFromMap(grouped.entrySet().stream()
                        .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                        .limit(limit)
                        .collect(Collectors.toList()),
                        Map.Entry::getKey, e -> null);
                break;
            }
            case "applicant": {
                List<IProcessRepository.InstanceStatsRow> insts =
                        repository.queryInstancesForStats(null, "create_time", start, end);
                Map<String, int[]> grouped = new LinkedHashMap<>();
                for (IProcessRepository.InstanceStatsRow r : insts) {
                    String op = r.getOperator();
                    if (op == null || op.isEmpty()) continue;
                    grouped.computeIfAbsent(op, k -> new int[1])[0]++;
                }
                rows = statsGroupFromMap(grouped.entrySet().stream()
                        .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                        .limit(limit)
                        .collect(Collectors.toList()),
                        Map.Entry::getKey, e -> null);
                break;
            }
            case "node": {
                List<IProcessRepository.TaskStatsRow> tasks =
                        repository.queryTasksForStats(20, start, end);
                Map<String, long[]> grouped = new LinkedHashMap<>(); // key -> [count, totalDurationSec]
                for (IProcessRepository.TaskStatsRow r : tasks) {
                    String dn = r.getDisplayName();
                    if (dn == null || dn.isEmpty()) continue;
                    long dur = 0;
                    if (r.getFinishTime() != null && r.getCreateTime() != null) {
                        dur = java.time.Duration.between(r.getCreateTime(), r.getFinishTime()).getSeconds();
                    }
                    long[] arr = grouped.computeIfAbsent(dn, k -> new long[2]);
                    arr[0]++;
                    arr[1] += dur;
                }
                rows = grouped.entrySet().stream()
                        .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                        .limit(limit)
                        .map(e -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("key", e.getKey());
                            m.put("label", null);
                            m.put("count", (int) e.getValue()[0]);
                            m.put("avgDurationSeconds", e.getValue()[0] > 0
                                    ? (int) Math.round((double) e.getValue()[1] / e.getValue()[0]) : null);
                            return m;
                        })
                        .collect(Collectors.toList());
                break;
            }
            case "stuckNode": {
                rows = repository.statsStuckNodeGroup(limit);
                // Add label=null and avgDurationSeconds=null
                for (Map<String, Object> m : rows) {
                    m.putIfAbsent("label", null);
                    m.putIfAbsent("avgDurationSeconds", null);
                }
                break;
            }
            case "stuckApprover": {
                rows = repository.statsStuckApproverGroup(limit);
                for (Map<String, Object> m : rows) {
                    m.putIfAbsent("label", null);
                    m.putIfAbsent("avgDurationSeconds", null);
                }
                break;
            }
            case "durationBucket": {
                List<Integer> durations = repository.statsCompletedInstanceDurations(start, end);
                int sameDay = 0, d1to3 = 0, d3to7 = 0, over7d = 0;
                for (int dur : durations) {
                    if (dur < 86400) sameDay++;
                    else if (dur < 259200) d1to3++;
                    else if (dur < 604800) d3to7++;
                    else over7d++;
                }
                rows = new ArrayList<>();
                String[] keys = {"sameDay", "1to3d", "3to7d", "over7d"};
                int[] counts = {sameDay, d1to3, d3to7, over7d};
                for (int i = 0; i < keys.length; i++) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("key", keys[i]);
                    m.put("label", null);
                    m.put("count", counts[i]);
                    m.put("avgDurationSeconds", null);
                    rows.add(m);
                }
                break;
            }
            default:
                rows = new ArrayList<>();
        }

        // A：data 本体为裸数组（去掉 {dimension, rows} 包装，对齐契约/前端/内置线）
        return ok(rows);
    }

    /** Helper：将 Map.Entry 列表转为 statsGroup 标准行格式 [{key, label, count}] */
    private <K> List<Map<String, Object>> statsGroupFromMap(
            List<Map.Entry<K, int[]>> entries,
            java.util.function.Function<Map.Entry<K, int[]>, String> keyFn,
            java.util.function.Function<Map.Entry<K, int[]>, String> labelFn) {
        return entries.stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", keyFn.apply(e));
            m.put("label", labelFn.apply(e));
            m.put("count", e.getValue()[0]);
            m.put("avgDurationSeconds", null);
            return m;
        }).collect(Collectors.toList());
    }

    // ── 统计 helper ──

    /** 枚举时间桶标签列表 */
    private List<String> statsEnumerateBuckets(LocalDateTime start, LocalDateTime end, String granularity) {
        if (start == null) start = LocalDateTime.now().minusDays(30);
        if (end == null) end = LocalDateTime.now();
        List<String> buckets = new ArrayList<>();
        LocalDate s = start.toLocalDate();
        LocalDate e = end.toLocalDate();
        switch (granularity) {
            case "hour": {
                // 按小时枚举（格式 yyyy-MM-dd HH:00）
                LocalDateTime cursor = start;
                // 对齐到整点
                cursor = cursor.withMinute(0).withSecond(0).withNano(0);
                while (!cursor.isAfter(end)) {
                    buckets.add(String.format("%s %02d:00",
                            cursor.toLocalDate().toString(), cursor.getHour()));
                    cursor = cursor.plusHours(1);
                }
                break;
            }
            case "day":
                while (!s.isAfter(e)) {
                    buckets.add(s.toString());
                    s = s.plusDays(1);
                }
                break;
            case "week":
                // 对齐到周一
                s = s.minusDays((s.getDayOfWeek().getValue() - 1));
                while (!s.isAfter(e)) {
                    buckets.add(statsWeekKey(s.atStartOfDay()));
                    s = s.plusWeeks(1);
                }
                break;
            case "month":
                s = s.withDayOfMonth(1);
                YearMonth ymEnd = YearMonth.from(e);
                YearMonth ym = YearMonth.from(s);
                while (!ym.isAfter(ymEnd)) {
                    buckets.add(ym.toString());
                    ym = ym.plusMonths(1);
                }
                break;
        }
        return buckets;
    }

    /** 把 LocalDateTime 按 granularity 转成桶标签 */
    private String statsBucketKey(LocalDateTime ldt, String granularity) {
        if (ldt == null) return null;
        switch (granularity) {
            case "hour":
                return String.format("%s %02d:00", ldt.toLocalDate().toString(), ldt.getHour());
            case "day": return ldt.toLocalDate().toString();
            case "week": return statsWeekKey(ldt);
            case "month": return YearMonth.from(ldt).toString();
            default: return null;
        }
    }

    /** ISO 周标签：YYYY-Www */
    private String statsWeekKey(LocalDateTime ldt) {
        if (ldt == null) return null;
        int year = ldt.get(IsoFields.WEEK_BASED_YEAR);
        int week = ldt.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format("%d-W%02d", year, week);
    }

    private static double statsRound4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    /** 解析整数列表入参 */
    private List<Integer> parseIntList(Object val) {
        if (val == null) return null;
        if (val instanceof List) {
            List<?> list = (List<?>) val;
            List<Integer> result = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Number) result.add(((Number) o).intValue());
                else {
                    try { result.add(Integer.parseInt(o.toString())); } catch (Exception ignored) {}
                }
            }
            return result.isEmpty() ? null : result;
        }
        return null;
    }
}
