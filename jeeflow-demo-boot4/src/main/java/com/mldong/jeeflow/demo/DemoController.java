package com.mldong.jeeflow.demo;

import com.mldong.jeeflow.facade.JeeflowFacade;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示站 REST 控制器——**单入口门面转发**
 *
 * <p>所有流程能力走统一门面：URL 路径段拼成 action（如 {@code /wf/processDefine/page} →
 * {@code "processDefine/page"}），body JSON 原样透传，返回 {@code {code, msg, data}}
 * （code=0 成功 / 99999999 失败，对齐 boot2 CommonResult）。
 * 路径和参数格式对齐 mldong-boot2，与门面 action 清单（spec §06）一一对应。</p>
 *
 * <p>门面 Bean 由 {@link DemoConfig} 装配（含扩展仓储与用户搜索钩子）。</p>
 */
@RestController
public class DemoController {

    private final JeeflowFacade facade;

    public DemoController(JeeflowFacade facade) {
        this.facade = facade;
    }

    /** 统一门面入口：/wf/{action}，action 支持多段（如 processDefine/page，{*action} 捕获剩余路径） */
    @PostMapping("/wf/{*action}")
    public Map<String, Object> flow(@PathVariable("action") String action, @RequestBody(required = false) Map<String, Object> params) {
        // {*action} 捕获值带前导斜杠（/processDefine/page），去掉后作为门面 action
        if (action != null && action.startsWith("/")) {
            action = action.substring(1);
        }
        return facade.flow(action, params != null ? params : new LinkedHashMap<>());
    }
}
