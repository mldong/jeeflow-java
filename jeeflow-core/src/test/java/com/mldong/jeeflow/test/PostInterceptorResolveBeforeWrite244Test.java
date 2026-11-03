package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
import com.mldong.jeeflow.enums.ProcessTaskStateEnum;
import com.mldong.jeeflow.spi.IUserProvider;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * issues/244 ①：节点 pre/post 拦截器「类名解析不出来」必须在 {@code completeTask} 之前报错。
 *
 * <p>改前形状：{@code NodeModel.execute} 的顺序是 {@code execPreInterceptors → exec → execPostInterceptors}，
 * 而引擎在 {@code prepareExecution} 里已经先 {@code instance.completeTask(...)} 再把任务行写回仓储。
 * 后置拦截器解析失败时任务已被静默办结、后继任务又没建出来（{@code persistTasks} 在
 * {@code node.execute} 之后）⇒ 内存仓储（无事务回滚）上留下「实例 DOING(10) 却零待办」，
 * 流程原地失联。真库靠入口外层事务整笔回滚，观察不到差别——这正是本族的判据只在内存仓储上照得出来的原因。</p>
 *
 * <p>夹具走内联 JSON（不碰共享 {@code flows/}，那是八仓镜像的另一条腿）：</p>
 * <pre>
 * start → apply(applicant) → review(postInterceptors=不存在的类) → final(userF) → end
 * </pre>
 *
 * <p>三条零写入判据缺一不算做到：原任务行仍 DOING 且 id 未变、实例状态不变、任务行总数不变。</p>
 */
public class PostInterceptorResolveBeforeWrite244Test {

    private static final String MISSING_INTERCEPTOR =
            "com.mldong.jeeflow.test.NoSuchPostInterceptor244";

    private static final int APPLY = ProcessSubmitTypeEnum.APPLY.getCode();
    private static final int AGREE = ProcessSubmitTypeEnum.AGREE.getCode();

    private JeeflowEngine engine;
    private MemoryProcessRepository repo;
    private TestJsonProvider json;

    @Before
    public void setUp() {
        Configuration config = new Configuration();
        repo = new MemoryProcessRepository();
        json = new TestJsonProvider();
        ServiceContext.put("repository", repo);
        ServiceContext.put("json", json);
        ServiceContext.put("expr", new TestExpressionEvaluator());
        ServiceContext.put("user", new IUserProvider() {
            @Override
            public UserInfo getUser(String userId) {
                UserInfo u = new UserInfo();
                u.setUserId(userId);
                u.setRealName("用户" + userId);
                u.setDeptId("D01");
                u.setDeptName("测试部门");
                return u;
            }
        });
        engine = new JeeflowEngineImpl();
        engine.configure(config);
    }

    private static String node(String id, String type, String props, String text) {
        return "{\"id\":\"" + id + "\",\"type\":\"" + type + "\",\"x\":0,\"y\":0,"
                + "\"properties\":{" + props + "},\"text\":{\"value\":\"" + text + "\"}}";
    }

    private static String edge(String id, String from, String to) {
        return "{\"id\":\"" + id + "\",\"type\":\"snaker:transition\",\"sourceNodeId\":\"" + from
                + "\",\"targetNodeId\":\"" + to + "\",\"properties\":{}}";
    }

    /** review 节点声明一个不存在的后置拦截器；其余节点保持最简。 */
    private ProcessInstance.ProcessDefine registerFlowWithBrokenPostInterceptor() throws Exception {
        String jsonStr = "{\"name\":\"i244-post\",\"displayName\":\"后置拦截器解析前置(244夹具)\",\"type\":\"approval\","
                + "\"nodes\":["
                + node("start", "snaker:start", "", "开始") + ","
                + node("apply", "snaker:task", "\"form\":\"apply-form\",\"assignee\":\"applicant\",\"taskType\":0,\"performType\":0", "发起申请") + ","
                + node("review", "snaker:task", "\"form\":\"review-form\",\"assignee\":\"userA\",\"taskType\":0,\"performType\":0,"
                        + "\"postInterceptors\":\"" + MISSING_INTERCEPTOR + "\"", "审批") + ","
                + node("final", "snaker:task", "\"form\":\"final-form\",\"assignee\":\"userF\",\"taskType\":0,\"performType\":0", "终审") + ","
                + node("end", "snaker:end", "", "结束")
                + "],\"edges\":["
                + edge("e0", "start", "apply") + ","
                + edge("e1", "apply", "review") + ","
                + edge("e2", "review", "final") + ","
                + edge("e3", "final", "end")
                + "]}";
        byte[] bytes = jsonStr.getBytes(StandardCharsets.UTF_8);
        com.mldong.jeeflow.model.logicflow.LfModel lfModel =
                json.getMapper().readValue(jsonStr, com.mldong.jeeflow.model.logicflow.LfModel.class);
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName(lfModel.getName());
        def.setDisplayName(lfModel.getDisplayName());
        def.setType(lfModel.getType());
        def.setState(1);
        def.setVersion(1);
        def.setContent(bytes);
        repo.addDefine(def);
        return def;
    }

    private ProcessTask doing(Long instanceId, String name) {
        for (ProcessTask t : repo.findDoingTasks(instanceId, null)) {
            if (name.equals(t.getTaskName())) {
                return t;
            }
        }
        return null;
    }

    /** 发起并办结申请节点，停在 review（该节点带坏的后置拦截器）。 */
    private ProcessInstance startToReview() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlowWithBrokenPostInterceptor();
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "applicant", FlowData.create());
        ProcessTask apply = doing(inst.getInstanceId(), "apply");
        assertNotNull("发起后应停在申请节点", apply);
        engine.executeProcessTask(apply.getTaskId(), "applicant",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, APPLY));
        return repo.findInstanceById(inst.getInstanceId());
    }

    @Test
    public void unresolvablePostInterceptorFailsBeforeAnyWrite() throws Exception {
        ProcessInstance inst = startToReview();
        Long iid = inst.getInstanceId();
        ProcessTask review = doing(iid, "review");
        assertNotNull("申请办结后应停在审批节点", review);

        Long reviewIdBefore = review.getTaskId();
        Integer instanceStateBefore = repo.findInstanceById(iid).getState();
        int rowCountBefore = repo.findHistoryTasks(iid).size();
        assertEquals("前置条件：review 尚未有后继待办", 0,
                repo.findDoingTasks(iid, new String[]{"final"}).size());

        RuntimeException error = null;
        try {
            engine.executeProcessTask(reviewIdBefore, "userA",
                    FlowData.create().set(FlowConst.SUBMIT_TYPE, AGREE));
        } catch (RuntimeException e) {
            error = e;
        }

        assertNotNull("声明了不存在的后置拦截器，办理必须报错", error);
        assertNotNull(error.getMessage());
        assertTrue("文案保持原句（外部出口与日志不变）：" + error.getMessage(),
                error.getMessage().contains("无法实例化拦截器"));

        // ── 本族三条零写入判据 ──
        ProcessTask reviewAfter = repo.findTaskById(reviewIdBefore);
        assertNotNull("原任务行不得消失", reviewAfter);
        assertEquals("id 未变", reviewIdBefore, reviewAfter.getTaskId());
        assertEquals("报错零写入：原任务行必须仍 DOING（改前此处已被静默办结为 20）",
                ProcessTaskStateEnum.DOING.getCode(), reviewAfter.getTaskState());
        assertEquals("实例状态不变", instanceStateBefore, repo.findInstanceById(iid).getState());
        assertEquals("任务行总数不变（新任务未建＝不得凭空多行，原行也不得消失）",
                rowCountBefore, repo.findHistoryTasks(iid).size());
    }
}
