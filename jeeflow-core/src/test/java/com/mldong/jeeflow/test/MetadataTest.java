package com.mldong.jeeflow.test;

import com.mldong.jeeflow.interceptor.AssignmentHandler;
import com.mldong.jeeflow.interceptor.FlowInterceptor;
import com.mldong.jeeflow.metadata.EnumDictRegistry;
import com.mldong.jeeflow.metadata.HandlerMeta;
import com.mldong.jeeflow.metadata.HandlerRegistry;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * 引擎元数据能力测试（v1.4.0，issues/04）
 * —— EnumDictRegistry 枚举字典 + HandlerRegistry 处理器清单
 */
public class MetadataTest {

    // ═══ EnumDictRegistry：枚举字典 ═══

    @Test
    public void testEnumDictKeys() {
        EnumDictRegistry registry = new EnumDictRegistry();
        List<String> keys = registry.listDictKeys();
        // 对齐 boot3 字典 key（存量前端零改动）
        assertTrue(keys.contains("wf_process_define_state"));
        assertTrue(keys.contains("wf_process_instance_state"));
        assertTrue(keys.contains("wf_process_submit_type"));
        assertTrue(keys.contains("wf_process_task_state"));
        assertTrue(keys.contains("wf_process_task_type"));
        assertTrue(keys.contains("wf_process_task_perform_type"));
        assertTrue(keys.contains("wf_countersign_type"));
        assertEquals(7, keys.size());
    }

    @Test
    public void testInstanceStateDict() {
        EnumDictRegistry registry = new EnumDictRegistry();
        List<EnumDictRegistry.DictItem> items = registry.getDict("wf_process_instance_state");
        assertEquals(7, items.size());
        assertEquals("10", items.get(0).getValue());
        assertEquals("进行中", items.get(0).getLabel());
        assertEquals("45", items.get(4).getValue());
        assertEquals("已拒绝", items.get(4).getLabel());
        assertEquals("99", items.get(6).getValue());
        assertEquals("已废弃", items.get(6).getLabel());
    }

    @Test
    public void testSubmitTypeDict() {
        EnumDictRegistry registry = new EnumDictRegistry();
        List<EnumDictRegistry.DictItem> items = registry.getDict("wf_process_submit_type");
        assertEquals(8, items.size());
        assertEquals("0", items.get(0).getValue());
        assertEquals("发起申请", items.get(0).getLabel());
        assertEquals("20", items.get(7).getValue());
        assertEquals("拒绝申请", items.get(7).getLabel());
    }

    @Test
    public void testUnknownKeyReturnsEmpty() {
        EnumDictRegistry registry = new EnumDictRegistry();
        assertTrue(registry.getDict("wf_no_such_dict").isEmpty());
    }

    // ═══ HandlerRegistry：SPI 实现清单 ═══

    @Test
    public void testRegisterAndList() {
        HandlerRegistry registry = new HandlerRegistry();
        registry.register(AssignmentHandler.class, "com.example.DeptLeaderHandler", "部门领导审批", 2, null);
        registry.register(AssignmentHandler.class, "com.example.BossHandler", "老板审批", 1, null);
        registry.register(FlowInterceptor.class, "com.example.TimeInterceptor", "耗时记录", 0, "post");
        registry.register(FlowInterceptor.class, "com.example.LogInterceptor", "日志记录", 1, "pre");

        List<HandlerMeta> assignments = registry.listHandlers(AssignmentHandler.class);
        assertEquals(2, assignments.size());
        // order 升序
        assertEquals("com.example.BossHandler", assignments.get(0).getClassName());
        assertEquals("老板审批", assignments.get(0).getDisplayName());
        assertEquals("AssignmentHandler", assignments.get(0).getTypeName());

        // 拦截器分组过滤
        List<HandlerMeta> pre = registry.listHandlers(FlowInterceptor.class, "pre");
        assertEquals(1, pre.size());
        assertEquals("com.example.LogInterceptor", pre.get(0).getClassName());
        List<HandlerMeta> post = registry.listHandlers(FlowInterceptor.class, "post");
        assertEquals(1, post.size());
        assertEquals("com.example.TimeInterceptor", post.get(0).getClassName());
        // 未分组过滤返回空
        assertTrue(registry.listHandlers(FlowInterceptor.class, "unknown").isEmpty());
    }

    @Test
    public void testEmptyRegistry() {
        HandlerRegistry registry = new HandlerRegistry();
        assertTrue(registry.listHandlers(AssignmentHandler.class).isEmpty());
        assertTrue(registry.listHandlerTypes().isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidMeta() {
        new HandlerMeta(AssignmentHandler.class, "", "x", 0, null);
    }
}
