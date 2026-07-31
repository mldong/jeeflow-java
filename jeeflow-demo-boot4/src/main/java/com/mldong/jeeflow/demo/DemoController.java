package com.mldong.jeeflow.demo;

import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
import com.mldong.jeeflow.json.IJsonProvider;
import com.mldong.jeeflow.spi.IProcessRepository;
import com.mldong.jeeflow.spi.PageQuery;
import com.mldong.jeeflow.spi.PageResult;
import com.mldong.jeeflow.spring.JeeflowQueryParser;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 演示站 REST 控制器——路径和参数格式对齐 mldong-boot2
 *
 * <p>boot2 CommonResult 规范：code=0 成功 / 99999999 失败，字段 code/msg/data；
 * submitType 全枚举（0/1/2/3/4/5/6/20）；
 * highLight / approvalRecord 独立端点。</p>
 */
@RestController
public class DemoController {

    private final JeeflowEngine engine;
    private final IProcessRepository repository;
    private final JeeflowQueryParser queryParser;

    public DemoController(JeeflowEngine engine, IProcessRepository repository, JeeflowQueryParser queryParser) {
        this.engine = engine;
        this.repository = repository;
        this.queryParser = queryParser;
    }

    // ═══ 流程定义 ═══

    @PostMapping("/wf/processDefine/page")
    public Map<String, Object> definePage(@RequestBody Map<String, Object> params) {
        PageQuery query = queryParser.parse(params);
        PageResult<IProcessRepository.DefineRow> page = repository.pageDefines(query);
        return pageResult(page);
    }

    @PostMapping("/wf/processDefine/detail")
    public Map<String, Object> defineDetail(@RequestBody Map<String, Object> params) {
        Long id = toLong(params.get("id"));
        ProcessInstance.ProcessDefine def = repository.findDefineById(id);
        if (def == null) return error("流程定义不存在");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", def.getId());
        data.put("name", def.getName());
        data.put("displayName", def.getDisplayName());
        data.put("type", def.getType());
        data.put("state", def.getState());
        data.put("version", def.getVersion());
        // 流程图数据（供前端设计器预览）——boot2 ProcessDefineVO.jsonObject
        Map<String, Object> graph = parseGraph(def);
        if (graph != null) data.put("jsonObject", graph);
        return ok(data);
    }

    @PostMapping("/wf/processDefine/startAndExecute")
    public Map<String, Object> defineStartAndExecute(@RequestBody Map<String, Object> params) {
        return startAndExecute(params);
    }

    // ═══ 流程实例 ═══

    @PostMapping("/wf/processInstance/startAndExecute")
    public Map<String, Object> startAndExecute(@RequestBody Map<String, Object> params) {
        Long defineId = toLong(params.get(FlowConst.PROCESS_DEFINE_ID_KEY));
        String operator = params.get("operator") != null ? params.get("operator").toString() : "user1";
        // 透传 body 其余参数作为流程变量（演示站不依赖登录态，便于切换人员）
        FlowData args = FlowData.create();
        params.forEach((k, v) -> {
            if (!FlowConst.PROCESS_DEFINE_ID_KEY.equals(k) && !"operator".equals(k)) args.put(k, v);
        });
        ProcessInstance inst = engine.startProcessInstanceById(defineId, operator, args);
        // boot2 startAndExecute：自动完成申请节点（assignee="applicant" → 发起人）
        List<ProcessTask> doingTasks = repository.findDoingTasks(inst.getInstanceId(), new String[]{});
        for (ProcessTask task : doingTasks) {
            repository.addTaskActor(task.getTaskId(), List.of(operator));
            args.put(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.APPLY.getCode());
            engine.executeProcessTask(task.getTaskId(), operator, args);
        }
        return ok();
    }

    @PostMapping("/wf/processInstance/page")
    public Map<String, Object> instancePage(@RequestBody Map<String, Object> params) {
        PageQuery query = queryParser.parse(params);
        String userId = toStr(params.get("operator"), "user1");
        query.add("t.operator", "EQ", userId);
        PageResult<IProcessRepository.InstanceRow> page = repository.pageInstances(query);
        List<Map<String, Object>> rows = page.getRows().stream().map(this::instanceVo).collect(Collectors.toList());
        return pageResult(PageResult.of(page.getPageNum(), page.getPageSize(), page.getRecordCount(), rows));
    }

    @PostMapping("/wf/processInstance/detail")
    public Map<String, Object> instanceDetail(@RequestBody Map<String, Object> params) {
        Long id = toLong(params.get("id"));
        ProcessInstance inst = repository.findInstanceById(id);
        if (inst == null) return error("实例不存在");
        ProcessInstance.ProcessDefine def = repository.findDefineById(inst.getDefineId());
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", inst.getInstanceId());
        vo.put("parentId", inst.getParentId());
        vo.put("processDefineId", inst.getDefineId());
        vo.put("state", inst.getState());
        vo.put("parentNodeName", inst.getParentNodeName());
        vo.put("businessNo", inst.getBusinessNo());
        vo.put("operator", inst.getOperator());
        vo.put("expireTime", fmtTime(inst.getExpireTime()));
        vo.put("variable", toJsonString(inst.getVariables()));
        vo.put("createTime", fmtTime(inst.getCreateTime()));
        vo.put("createUser", inst.getCreateUser());
        vo.put("updateTime", fmtTime(inst.getUpdateTime()));
        vo.put("updateUser", inst.getUpdateUser());
        if (def != null) {
            vo.put("displayName", def.getDisplayName());
            vo.put("name", def.getName());
            vo.put("version", def.getVersion());
            Map<String, Object> graph = parseGraph(def);
            if (graph != null) vo.put("jsonObject", graph);
        }
        List<Map<String, Object>> active = new ArrayList<>();
        for (ProcessTask t : repository.findDoingTasks(id, new String[]{})) {
            active.add(taskVo(t, null, null, null));
        }
        vo.put("activeTaskList", active);
        return ok(vo);
    }

    @PostMapping("/wf/processInstance/highLight")
    public Map<String, Object> highLight(@RequestBody Map<String, Object> params) {
        Long id = toLong(params.get("id"));
        ProcessInstance inst = repository.findInstanceById(id);
        if (inst == null) return error("实例不存在");
        Set<String> finished = new LinkedHashSet<>();
        Set<String> active = new LinkedHashSet<>();
        for (ProcessTask t : repository.findHistoryTasks(id)) {
            if (t.getTaskState() != null && t.getTaskState() == 20) {
                finished.add(t.getTaskName());
            } else if (t.getTaskState() != null && t.getTaskState() == 10) {
                active.add(t.getTaskName());
            }
        }
        // 高亮边：连接已完成节点的边
        List<String> finishedEdges = new ArrayList<>();
        ProcessInstance.ProcessDefine def = repository.findDefineById(inst.getDefineId());
        Map<String, Object> graph = parseGraph(def);
        if (graph != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> edges = (List<Map<String, Object>>) graph.getOrDefault("edges", Collections.emptyList());
            for (Map<String, Object> edge : edges) {
                String src = (String) edge.get("sourceNodeId");
                String tgt = (String) edge.get("targetNodeId");
                if (finished.contains(src) && finished.contains(tgt)) {
                    finishedEdges.add((String) edge.get("id"));
                }
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("historyNodeNames", new ArrayList<>(finished));
        data.put("historyEdgeNames", finishedEdges);
        data.put("activeNodeNames", new ArrayList<>(active));
        return ok(data);
    }

    @PostMapping("/wf/processInstance/approvalRecord")
    public Map<String, Object> approvalRecord(@RequestBody Map<String, Object> params) {
        Long id = toLong(params.get("id"));
        ProcessInstance inst = repository.findInstanceById(id);
        if (inst == null) return error("实例不存在");
        ProcessInstance.ProcessDefine def = repository.findDefineById(inst.getDefineId());
        List<Map<String, Object>> records = new ArrayList<>();
        for (ProcessTask t : repository.findHistoryTasks(id)) {
            records.add(taskVo(t,
                    def != null ? def.getName() : null,
                    def != null ? def.getDisplayName() : null,
                    inst.getCreateTime()));
        }
        return ok(records);
    }

    // ═══ 流程任务 ═══

    @PostMapping("/wf/processTask/todoList")
    public Map<String, Object> todoList(@RequestBody Map<String, Object> params) {
        PageQuery query = queryParser.parse(params);
        String userId = toStr(params.getOrDefault("userId", params.get("operator")), "user1");
        query.add("pta.actor_id", "EQ", userId);
        PageResult<IProcessRepository.TaskRow> page = repository.pageTodoTasks(query);
        List<Map<String, Object>> rows = page.getRows().stream().map(this::taskVo).collect(Collectors.toList());
        return pageResult(PageResult.of(page.getPageNum(), page.getPageSize(), page.getRecordCount(), rows));
    }

    @PostMapping("/wf/processTask/doneList")
    public Map<String, Object> doneList(@RequestBody Map<String, Object> params) {
        PageQuery query = queryParser.parse(params);
        String userId = toStr(params.getOrDefault("userId", params.get("operator")), "user1");
        query.add("pta.actor_id", "EQ", userId);
        PageResult<IProcessRepository.TaskRow> page = repository.pageDoneTasks(query);
        List<Map<String, Object>> rows = page.getRows().stream().map(this::taskVo).collect(Collectors.toList());
        return pageResult(PageResult.of(page.getPageNum(), page.getPageSize(), page.getRecordCount(), rows));
    }

    @PostMapping("/wf/processTask/detail")
    public Map<String, Object> taskDetail(@RequestBody Map<String, Object> params) {
        Long id = toLong(params.get("id"));
        ProcessTask task = repository.findTaskById(id);
        if (task == null) return error("任务不存在");
        return ok(taskVo(task, null, null, null));
    }

    @PostMapping("/wf/processTask/execute")
    public Map<String, Object> execute(@RequestBody Map<String, Object> params) {
        Long taskId = toLong(params.get(FlowConst.PROCESS_TASK_ID_KEY));
        String operator = params.get("operator") != null ? params.get("operator").toString() : "leader";
        Integer submitType = toInt(params.get(FlowConst.SUBMIT_TYPE), ProcessSubmitTypeEnum.AGREE.getCode());
        // 透传 body 其余参数（taskName 供跳转、comment 审批意见等）
        FlowData args = FlowData.create();
        params.forEach((k, v) -> {
            if (!FlowConst.PROCESS_TASK_ID_KEY.equals(k) && !"operator".equals(k)) args.put(k, v);
        });
        args.put(FlowConst.SUBMIT_TYPE, submitType);
        try {
            if (ProcessSubmitTypeEnum.REJECT.getCode().equals(submitType)) {
                // 2 拒绝 → 跳结束（实例→45）
                engine.executeAndJumpToEnd(taskId, operator, args);
            } else if (ProcessSubmitTypeEnum.ROLLBACK.getCode().equals(submitType)) {
                // 3 退回上一步（回溯上一任务节点）
                rollbackToPrev(taskId, operator, args);
            } else if (ProcessSubmitTypeEnum.JUMP.getCode().equals(submitType)) {
                // 4 跳指定节点
                String taskName = params.get("taskName") != null ? params.get("taskName").toString() : null;
                engine.executeAndJumpTask(taskId, operator, args, taskName);
            } else if (ProcessSubmitTypeEnum.ROLLBACK_TO_OPERATOR.getCode().equals(submitType)) {
                // 6 退回发起人（第一个任务节点）
                engine.executeAndJumpToFirstTaskNode(taskId, operator, args);
            } else if (ProcessSubmitTypeEnum.COUNTERSIGN_DISAGREE.getCode().equals(submitType)) {
                // 20 会签不同意
                args.put("countersignDisagreeFlag", 1);
                engine.executeProcessTask(taskId, operator, args);
            } else {
                // 0/1/5 及默认 → 执行
                engine.executeProcessTask(taskId, operator, args);
            }
            return ok();
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @PostMapping("/wf/processTask/jumpAbleTaskNameList")
    public Map<String, Object> jumpAbleTaskNameList(@RequestBody Map<String, Object> params) {
        Long instanceId = toLong(params.get(FlowConst.PROCESS_INSTANCE_ID_KEY));
        List<ProcessTask> doneTasks = repository.findDoneTasks(instanceId, new String[]{});
        Set<String> seen = new LinkedHashSet<>();
        List<Map<String, Object>> list = new ArrayList<>();
        for (ProcessTask t : doneTasks) {
            if (!seen.contains(t.getTaskName())) {
                seen.add(t.getTaskName());
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("label", t.getDisplayName());
                item.put("value", t.getTaskName());
                list.add(item);
            }
        }
        return ok(list);
    }

    // ═══ 仪表盘统计 ═══

    @GetMapping("/api/debug/taskActors")
    public Map<String, Object> debugTaskActors(@RequestParam("taskId") Long taskId) {
        List<String> actors = repository.findTaskActors(taskId);
        ProcessTask task = repository.findTaskById(taskId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", taskId);
        data.put("taskName", task != null ? task.getDisplayName() : "null");
        data.put("taskState", task != null ? task.getTaskState() : null);
        data.put("actorIds_in_task", task != null ? task.getActorIds() : null);
        data.put("actorIds_in_db", actors);
        return ok(data);
    }

    @GetMapping("/api/stats")
    public Map<String, Object> stats(@RequestParam(name = "userId", defaultValue = "user1") String userId) {
        PageQuery todoQ = new PageQuery(1, 1);
        todoQ.add("pta.actor_id", "EQ", userId);
        int todoCount = repository.pageTodoTasks(todoQ).getRecordCount();

        PageQuery instQ = new PageQuery(1, 1);
        instQ.add("t.operator", "EQ", userId);
        int myInstanceCount = repository.pageInstances(instQ).getRecordCount();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("todoCount", todoCount);
        data.put("myInstanceCount", myInstanceCount);
        return ok(data);
    }

    // ═══ VO 转换（对齐 boot2 ProcessInstanceVO / ProcessTaskVO）═══

    private Map<String, Object> instanceVo(IProcessRepository.InstanceRow r) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", r.getId());
        vo.put("parentId", r.getParentId());
        vo.put("processDefineId", r.getProcessDefineId());
        vo.put("state", r.getState());
        vo.put("parentNodeName", r.getParentNodeName());
        vo.put("businessNo", r.getBusinessNo());
        vo.put("operator", r.getOperator());
        vo.put("expireTime", fmtTime(r.getExpireTime()));
        vo.put("variable", r.getVariable());
        vo.put("createTime", fmtTime(r.getCreateTime()));
        vo.put("createUser", r.getCreateUser());
        vo.put("updateTime", fmtTime(r.getUpdateTime()));
        vo.put("updateUser", r.getUpdateUser());
        vo.put("displayName", r.getProcessDefineDisplayName());
        vo.put("name", r.getProcessDefineName());
        vo.put("version", r.getProcessDefineVersion());
        // jsonObject + activeTaskList（boot2 ProcessInstanceVO）
        ProcessInstance.ProcessDefine def = repository.findDefineById(r.getProcessDefineId());
        Map<String, Object> graph = parseGraph(def);
        if (graph != null) vo.put("jsonObject", graph);
        List<Map<String, Object>> active = new ArrayList<>();
        for (ProcessTask t : repository.findDoingTasks(r.getId(), new String[]{})) {
            active.add(taskVo(t, null, null, null));
        }
        vo.put("activeTaskList", active);
        return vo;
    }

    private Map<String, Object> taskVo(IProcessRepository.TaskRow r) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", r.getId());
        vo.put("processInstanceId", r.getProcessInstanceId());
        vo.put("taskName", r.getTaskName());
        vo.put("displayName", r.getDisplayName());
        vo.put("taskType", r.getTaskType());
        vo.put("performType", r.getPerformType());
        vo.put("taskState", r.getTaskState());
        vo.put("operator", r.getOperator());
        vo.put("finishTime", fmtTime(r.getFinishTime()));
        vo.put("expireTime", fmtTime(r.getExpireTime()));
        vo.put("formKey", r.getFormKey());
        vo.put("taskParentId", r.getTaskParentId());
        vo.put("variable", r.getVariable());
        vo.put("createTime", fmtTime(r.getCreateTime()));
        vo.put("createUser", r.getCreateUser());
        vo.put("updateTime", fmtTime(r.getUpdateTime()));
        vo.put("updateUser", r.getUpdateUser());
        vo.put("processDefineName", r.getProcessDefineName());
        vo.put("processDefineDisplayName", r.getProcessDefineDisplayName());
        vo.put("instanceVariable", r.getInstanceVariable());
        vo.put("instanceCreateTime", fmtTime(r.getInstanceCreateTime()));
        vo.put("taskActorIdList", repository.findTaskActors(r.getId()));
        return vo;
    }

    private Map<String, Object> taskVo(ProcessTask t, String defineName, String defineDisplayName,
                                       LocalDateTime instanceCreateTime) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", t.getTaskId());
        vo.put("processInstanceId", t.getProcessInstanceId());
        vo.put("taskName", t.getTaskName());
        vo.put("displayName", t.getDisplayName());
        vo.put("taskType", t.getTaskType() != null ? t.getTaskType().getCode() : null);
        vo.put("performType", t.getPerformType() != null ? t.getPerformType().getCode() : null);
        vo.put("taskState", t.getTaskState());
        vo.put("operator", t.getActorId());
        vo.put("finishTime", fmtTime(t.getFinishTime()));
        vo.put("expireTime", fmtTime(t.getExpireTime()));
        vo.put("formKey", t.getFormKey());
        vo.put("taskParentId", t.getParentTaskId());
        vo.put("variable", toJsonString(t.getVariables()));
        vo.put("createTime", fmtTime(t.getCreateTime()));
        vo.put("createUser", t.getCreateUser());
        vo.put("updateTime", fmtTime(t.getUpdateTime()));
        vo.put("updateUser", t.getUpdateUser());
        if (defineName != null) {
            vo.put("processDefineName", defineName);
            vo.put("processDefineDisplayName", defineDisplayName);
            vo.put("instanceCreateTime", fmtTime(instanceCreateTime));
        }
        vo.put("taskActorIdList", t.getActorIds());
        return vo;
    }

    // ═══ 响应工具（boot2 CommonResult：code=0 成功 / 99999999 失败，字段 code/msg/data）═══

    private Map<String, Object> ok() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 0);
        r.put("msg", "成功");
        r.put("data", null);
        return r;
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
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 0);
        r.put("msg", "成功");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pageNum", page.getPageNum());
        data.put("pageSize", page.getPageSize());
        data.put("recordCount", page.getRecordCount());
        data.put("totalPage", page.getTotalPage());
        data.put("rows", page.getRows());
        r.put("data", data);
        return r;
    }

    // ═══ 工具 ═══

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static String fmtTime(LocalDateTime t) {
        return t == null ? null : t.format(FMT);
    }

    private String toJsonString(Object obj) {
        try {
            IJsonProvider json = ServiceContext.find(IJsonProvider.class);
            return json != null ? json.toJson(obj) : null;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseGraph(ProcessInstance.ProcessDefine def) {
        if (def == null || def.getContent() == null) return null;
        try {
            IJsonProvider json = ServiceContext.find(IJsonProvider.class);
            if (json == null) return null;
            return json.fromJson(new String(def.getContent(), StandardCharsets.UTF_8), Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** 退回上一步：找到当前任务节点的上一个任务节点并跳转（boot2 ROLLBACK=3） */
    private void rollbackToPrev(Long taskId, String operator, FlowData args) {
        ProcessTask task = repository.findTaskById(taskId);
        ProcessInstance inst = task != null ? repository.findInstanceById(task.getProcessInstanceId()) : null;
        if (task == null || inst == null) {
            engine.executeAndJumpToEnd(taskId, operator, args);
            return;
        }
        Map<String, Object> graph = parseGraph(repository.findDefineById(inst.getDefineId()));
        if (graph == null) {
            engine.executeAndJumpToEnd(taskId, operator, args);
            return;
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) graph.getOrDefault("edges", Collections.emptyList());
        // 找当前节点的上一个节点
        String prev = null;
        for (Map<String, Object> e : edges) {
            if (task.getTaskName().equals(e.get("targetNodeId"))) {
                prev = (String) e.get("sourceNodeId");
                break;
            }
        }
        // 沿 prev 回溯到任务节点
        String target = prev;
        Set<String> seen = new HashSet<>();
        while (target != null) {
            if (!seen.add(target)) break;
            Map<String, Object> node = null;
            for (Map<String, Object> n : (List<Map<String, Object>>) graph.getOrDefault("nodes", Collections.emptyList())) {
                if (target.equals(n.get("id"))) { node = n; break; }
            }
            if (node == null) break;
            if ("snaker:task".equals(node.get("type")) || "snaker:custom".equals(node.get("type"))) break;
            String found = null;
            for (Map<String, Object> e : edges) {
                if (target.equals(e.get("targetNodeId"))) { found = (String) e.get("sourceNodeId"); break; }
            }
            target = found;
        }
        if (target != null) engine.executeAndJumpTask(taskId, operator, args, target);
        else engine.executeAndJumpToEnd(taskId, operator, args);
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static Integer toInt(Object v, Integer def) {
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return def; }
    }

    private static String toStr(Object v, String def) {
        return v != null ? v.toString() : def;
    }
}
