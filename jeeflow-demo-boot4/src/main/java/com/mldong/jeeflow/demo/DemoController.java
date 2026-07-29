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
import java.util.*;
import java.util.stream.Collectors;

/**
 * 演示站 REST 控制器——路径和参数格式对齐 mldong-boot2
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
        return ok(data);
    }

    // ═══ 流程实例 ═══

    @PostMapping("/wf/processInstance/startAndExecute")
    public Map<String, Object> startAndExecute(@RequestBody Map<String, Object> params) {
        Long defineId = toLong(params.get(FlowConst.PROCESS_DEFINE_ID_KEY));
        String operator = params.get("operator") != null ? params.get("operator").toString() : "user1";
        FlowData args = FlowData.create();
        if (params.containsKey("amount")) args.put("amount", toLong(params.get("amount")));
        if (params.containsKey("reason")) args.put("reason", params.get("reason"));
        args.put(FlowConst.BUSINESS_NO, "BIZ-" + System.currentTimeMillis());
        ProcessInstance inst = engine.startProcessInstanceById(defineId, operator, args);
        return ok(Map.of("processInstanceId", inst.getInstanceId()));
    }

    @PostMapping("/wf/processInstance/page")
    public Map<String, Object> instancePage(@RequestBody Map<String, Object> params) {
        PageQuery query = queryParser.parse(params);
        String userId = toStr(params.get("operator"), "user1");
        query.add("t.operator", "EQ", userId);
        PageResult<IProcessRepository.InstanceRow> page = repository.pageInstances(query);
        return pageResult(page);
    }

    @PostMapping("/wf/processInstance/detail")
    public Map<String, Object> instanceDetail(@RequestBody Map<String, Object> params) {
        Long id = toLong(params.get("id"));
        ProcessInstance inst = repository.findInstanceById(id);
        if (inst == null) return error("实例不存在");

        // 流程定义信息
        ProcessInstance.ProcessDefine def = repository.findDefineById(inst.getDefineId());

        // 审批记录
        List<Map<String, Object>> records = new ArrayList<>();
        List<ProcessTask> history = repository.findHistoryTasks(id);
        Set<String> finishedNodeNames = new LinkedHashSet<>();
        Set<String> activeNodeNames = new LinkedHashSet<>();
        for (ProcessTask t : history) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", t.getTaskId());
            r.put("taskName", t.getTaskName());
            r.put("displayName", t.getDisplayName());
            r.put("taskState", t.getTaskState());
            r.put("operator", t.getActorId());
            r.put("createTime", t.getCreateTime());
            r.put("finishTime", t.getFinishTime());
            r.put("variables", t.getVariables());
            records.add(r);
            // 收集高亮节点名
            if (t.getTaskState() != null && t.getTaskState() == 20) {
                finishedNodeNames.add(t.getTaskName());
            } else if (t.getTaskState() != null && t.getTaskState() == 10) {
                activeNodeNames.add(t.getTaskName());
            }
        }

        // 流程定义 JSON（供前端设计器渲染）
        Map<String, Object> graphData = null;
        if (def != null && def.getContent() != null) {
            try {
                IJsonProvider json = ServiceContext.find(IJsonProvider.class);
                if (json != null) {
                    String contentStr = new String(def.getContent(), java.nio.charset.StandardCharsets.UTF_8);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsed = json.fromJson(contentStr, Map.class);
                    graphData = parsed;
                }
            } catch (Exception ignored) {}
        }

        // 计算高亮边（连接已完成节点的边）
        List<String> finishedEdgeNames = new ArrayList<>();
        if (graphData != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> edges = (List<Map<String, Object>>) graphData.getOrDefault("edges", java.util.Collections.emptyList());
            for (Map<String, Object> edge : edges) {
                String src = (String) edge.get("sourceNodeId");
                String tgt = (String) edge.get("targetNodeId");
                if (finishedNodeNames.contains(src) && finishedNodeNames.contains(tgt)) {
                    finishedEdgeNames.add((String) edge.get("id"));
                }
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", inst.getInstanceId());
        data.put("state", inst.getState());
        data.put("operator", inst.getOperator());
        data.put("businessNo", inst.getBusinessNo());
        data.put("createTime", inst.getCreateTime());
        data.put("defineName", def != null ? def.getDisplayName() : "");
        data.put("graphData", graphData);
        data.put("approvalRecords", records);
        // 高亮数据
        Map<String, Object> highLight = new LinkedHashMap<>();
        highLight.put("historyNodeNames", new ArrayList<>(finishedNodeNames));
        highLight.put("historyEdgeNames", finishedEdgeNames);
        highLight.put("activeNodeNames", new ArrayList<>(activeNodeNames));
        data.put("highLight", highLight);
        return ok(data);
    }

    @PostMapping("/wf/processInstance/approvalRecord")
    public Map<String, Object> approvalRecord(@RequestBody Map<String, Object> params) {
        return instanceDetail(params); // 复用
    }

    // ═══ 流程任务 ═══

    @PostMapping("/wf/processTask/todoList")
    public Map<String, Object> todoList(@RequestBody Map<String, Object> params) {
        PageQuery query = queryParser.parse(params);
        String userId = toStr(params.getOrDefault("userId", params.get("operator")), "user1");
        query.add("pta.actor_id", "EQ", userId);
        PageResult<IProcessRepository.TaskRow> page = repository.pageTodoTasks(query);
        return pageResult(page);
    }

    @PostMapping("/wf/processTask/doneList")
    public Map<String, Object> doneList(@RequestBody Map<String, Object> params) {
        PageQuery query = queryParser.parse(params);
        String userId = toStr(params.getOrDefault("userId", params.get("operator")), "user1");
        query.add("pta.actor_id", "EQ", userId);
        PageResult<IProcessRepository.TaskRow> page = repository.pageDoneTasks(query);
        return pageResult(page);
    }

    @PostMapping("/wf/processTask/detail")
    public Map<String, Object> taskDetail(@RequestBody Map<String, Object> params) {
        Long id = toLong(params.get("id"));
        ProcessTask task = repository.findTaskById(id);
        if (task == null) return error("任务不存在");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", task.getTaskId());
        data.put("processInstanceId", task.getProcessInstanceId());
        data.put("taskName", task.getTaskName());
        data.put("displayName", task.getDisplayName());
        data.put("taskState", task.getTaskState());
        data.put("operator", task.getActorId());
        data.put("formKey", task.getFormKey());
        data.put("createTime", task.getCreateTime());
        data.put("actorIds", task.getActorIds());
        return ok(data);
    }

    @PostMapping("/wf/processTask/execute")
    public Map<String, Object> execute(@RequestBody Map<String, Object> params) {
        Long taskId = toLong(params.get(FlowConst.PROCESS_TASK_ID_KEY));
        String operator = params.get("operator") != null ? params.get("operator").toString() : "leader";
        Integer submitType = toInt(params.get(FlowConst.SUBMIT_TYPE), ProcessSubmitTypeEnum.AGREE.getCode());
        FlowData args = FlowData.create();
        args.put(FlowConst.SUBMIT_TYPE, submitType);
        if (params.containsKey("comment")) args.put(FlowConst.APPROVAL_COMMENT, params.get("comment"));
        try {
            if (ProcessSubmitTypeEnum.REJECT.getCode().equals(submitType)) {
                engine.executeAndJumpToEnd(taskId, operator, args);
            } else if (ProcessSubmitTypeEnum.ROLLBACK.getCode().equals(submitType)) {
                engine.executeAndJumpTask(taskId, operator, args, null);
            } else {
                engine.executeProcessTask(taskId, operator, args);
            }
            return ok(Map.of("message", "处理成功"));
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
        int todoCount = repository.countTodoTasks(toLong(userId) != null ? toLong(userId) : 1L);

        PageQuery q1 = new PageQuery(1,1);
        q1.add("t.operator", "EQ", userId);
        PageResult<IProcessRepository.InstanceRow> insts = repository.pageInstances(q1);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("todoCount", todoCount);
        data.put("myInstanceCount", insts.getRecordCount());
        return ok(data);
    }

    // ═══ 响应工具 ═══

    private Map<String, Object> ok(Object data) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 200);
        r.put("message", "成功");
        r.put("data", data);
        return r;
    }

    private Map<String, Object> error(String msg) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 500);
        r.put("message", msg);
        return r;
    }

    private Map<String, Object> pageResult(PageResult<?> page) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 200);
        r.put("message", "成功");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pageNum", page.getPageNum());
        data.put("pageSize", page.getPageSize());
        data.put("recordCount", page.getRecordCount());
        data.put("totalPage", page.getTotalPage());
        data.put("rows", page.getRows());
        r.put("data", data);
        return r;
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
