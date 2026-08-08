package com.mldong.jeeflow.demo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示站运维端点——对齐 Go/Python/Node demo
 *
 * <ul>
 *   <li>{@code GET /healthz}：健康检查</li>
 *   <li>{@code GET /api/stats}：待办数（task_actor 命中）+ 我发起数（instance.operator，四端统一口径，闭环 issues/14）</li>
 *   <li>{@code POST /api/reset}：清空全部演示数据并重载种子流程定义</li>
 * </ul>
 */
@RestController
public class DemoOpsController {

    private final JdbcTemplate jdbc;
    private final FlowSeedRunner seedRunner;

    public DemoOpsController(JdbcTemplate jdbc, FlowSeedRunner seedRunner) {
        this.jdbc = jdbc;
        this.seedRunner = seedRunner;
    }

    @GetMapping("/healthz")
    public Map<String, Object> healthz() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("backend", "java");
        return body;
    }

    @GetMapping("/api/stats")
    public Map<String, Object> stats(@RequestParam(value = "userId", defaultValue = "user1") String userId) {
        Integer todoCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM wf_process_task t JOIN wf_process_task_actor a " +
                        "ON a.process_task_id = t.id WHERE t.task_state = 10 AND a.actor_id = ?",
                Integer.class, userId);
        Integer myInstanceCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM wf_process_instance WHERE operator = ?",
                Integer.class, userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("todoCount", todoCount == null ? 0 : todoCount);
        data.put("myInstanceCount", myInstanceCount == null ? 0 : myInstanceCount);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 0);
        body.put("msg", "成功");
        body.put("data", data);
        return body;
    }

    @PostMapping("/api/reset")
    public Map<String, Object> reset() {
        // 先子后父清空 8 张表，再重载种子（id=1..N 与其余三端一致）
        for (String table : new String[]{
                "wf_process_task_actor", "wf_process_task", "wf_process_cc_instance",
                "wf_process_instance", "wf_process_define",
                "wf_process_design_his", "wf_process_design", "wf_process_surrogate"}) {
            jdbc.update("DELETE FROM " + table);
        }
        int seeded = seedRunner.seed();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 0);
        body.put("msg", "成功");
        body.put("data", Map.of("seededDefines", seeded));
        return body;
    }
}
