package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.ProcessEventTypeEnum;
import com.mldong.jeeflow.event.ProcessEvent;
import com.mldong.jeeflow.event.ProcessEventListener;
import com.mldong.jeeflow.spi.IOrgUserProvider;
import com.mldong.jeeflow.spi.IUserProvider;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * TASK_START 事件时序契约回归（issues/13 messagePage 恒空 · 引擎侧根因）。
 *
 * <p>spec §4.4 / Go 参考实现约定：任务「任务开始」事件在任务行<b>落库之后</b>触发，
 * 事件 {@code sourceId = taskId} 必须能被监听器 {@code findTaskById} 反查。
 *
 * <p>缺陷（本测试钉死）：{@code CreateTaskHandler} 曾在 handler 阶段 fire 事件，
 * 而 {@code taskId} 由 {@code saveTask→nextId()} 在 handler 之后才分配，故事件
 * {@code sourceId=null}；集成层监听器 {@code onEvent} 的 {@code sourceId==null} 守卫
 * 直接 return → 「新待办」TODO 消息恒漏发（NOTICE 因实例 id 早于 fire 而侥幸正常）。
 * 修复：引擎在 {@code saveTask} 之后 fire（{@code JeeflowEngineImpl.notifyTaskStart}）。</p>
 */
public class TaskStartEventOrderTest {

    private JeeflowEngineImpl engine;
    private MemoryProcessRepository repo;
    private final List<ProcessEvent> taskStartEvents = new CopyOnWriteArrayList<>();

    @Before
    public void setUp() {
        Configuration config = new Configuration();
        repo = new MemoryProcessRepository();

        ServiceContext.put("repository", repo);
        ServiceContext.put("json", new TestJsonProvider());
        ServiceContext.put("expr", new TestExpressionEvaluator());
        ServiceContext.put("user", new IUserProvider() {
            @Override
            public UserInfo getUser(String userId) {
                UserInfo u = new UserInfo();
                u.setUserId(userId);
                u.setRealName("用户" + userId);
                return u;
            }
        });
        ServiceContext.put("org", new IOrgUserProvider() {
            @Override
            public List<String> findDeptLeaders(String deptId) { return null; }
            @Override
            public List<String> findDeptMainLeaders(String deptId) { return null; }
            @Override
            public List<String> findByRole(String roleCode) {
                if ("finance".equals(roleCode)) return Arrays.asList("finA", "finB");
                return null;
            }
        });

        engine = new JeeflowEngineImpl();
        engine.configure(config);

        // 捕获监听器：findList(ProcessEventListener.class) 按 isInstance 扫描全部注册项，
        // 故字符串名注册即可被发现（与集成层 @PostConstruct put 的装配方式一致）。
        ProcessEventListener capture = event -> {
            if (event.getEventType() == ProcessEventTypeEnum.PROCESS_TASK_START) {
                taskStartEvents.add(event);
            }
        };
        ServiceContext.put("taskStartCapture", capture);
    }

    @Test
    public void taskStartEventSourceIdIsPersistedAndQueryable() throws Exception {
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName("simple");
        def.setDisplayName("简单审批流程");
        def.setType("approval");
        def.setState(1);
        def.setVersion(1);
        def.setContent(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get("src/test/resources/flows/01-simple.json")));
        repo.addDefine(def);

        taskStartEvents.clear();
        ProcessInstance inst = engine.startProcessInstanceById(
                def.getId(), "zhangsan", FlowData.create());
        assertNotNull("实例应已创建", inst.getInstanceId());

        // 契约①：发起至少产生一个 TASK_START 事件（apply 首任务节点）
        assertTrue("发起后应 fire 至少一个 TASK_START 事件，实际 " + taskStartEvents.size(),
                !taskStartEvents.isEmpty());

        // 契约②：每个 TASK_START 的 sourceId 非空（落库后 fire，而非 handler 阶段的 null）
        for (ProcessEvent e : taskStartEvents) {
            assertNotNull("TASK_START.sourceId 不得为 null（引擎须在 saveTask 落库后 fire）",
                    e.getSourceId());
            // 契约③：sourceId 可作为 taskId 反查到该任务行（监听器 findTaskById 前置条件）
            ProcessTask task = repo.findTaskById(e.getSourceId());
            assertNotNull("TASK_START.sourceId=" + e.getSourceId()
                    + " 必须可被 findTaskById 反查（spec §4.4 监听器反查前提）", task);
        }
    }
}
