package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessInstanceStateEnum;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
import com.mldong.jeeflow.enums.ProcessTaskPerformTypeEnum;
import com.mldong.jeeflow.enums.ProcessTaskStateEnum;
import com.mldong.jeeflow.spi.IUserProvider;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * issues/244 ②：串行会签「零候选人」不得抛 {@code IndexOutOfBoundsException}，而是
 * <b>建一行、不挂任何参与者</b>（跨栈已定语义；go/python/node/moon 四栈现行行为即如此，java 这是补齐多数派）。
 *
 * <p>改前形状：{@code createCountersignTasks} 的 SEQUENTIAL 分支直接
 * {@code actorIds.get(0)}，名册为空时越界；调用点 {@code CreateTaskHandler} 没有 size 校验，
 * 且此刻当前任务已办结（{@code prepareExecution} 里 {@code completeTask} 先跑）
 * ⇒ 内存仓储上同样是「当前行 DONE、新行没建」的失联形状。</p>
 *
 * <p>夹具走内联 JSON（不碰共享 {@code flows/}）：</p>
 * <pre>
 * start → apply(applicant) → cs(performType=1, countersignType=SEQUENTIAL, 无人可解析) → end
 * </pre>
 * <p>cs 只挂 {@link EmptyAssigneeHandler244}、不写 assignee，保证名册解析结果为空。</p>
 *
 * <p>本格只覆盖 SEQUENTIAL 这一支；PARALLEL「零人 0 行」不在本笔范围，不做断言也不改行为。</p>
 */
public class CountersignZeroActor244Test {

    private static final int APPLY = ProcessSubmitTypeEnum.APPLY.getCode();

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

    private ProcessInstance.ProcessDefine registerFlowWithZeroActorSequentialCountersign() throws Exception {
        String jsonStr = "{\"name\":\"i244-zero-actor\",\"displayName\":\"零候选人串行会签(244夹具)\",\"type\":\"approval\","
                + "\"nodes\":["
                + node("start", "snaker:start", "", "开始") + ","
                + node("apply", "snaker:task", "\"form\":\"apply-form\",\"assignee\":\"applicant\",\"taskType\":0,\"performType\":0", "发起申请") + ","
                + node("cs", "snaker:task", "\"form\":\"cs-form\",\"taskType\":0,\"performType\":1,"
                        + "\"countersignType\":\"SEQUENTIAL\","
                        + "\"assignmentHandler\":\"com.mldong.jeeflow.test.EmptyAssigneeHandler244\"", "串行会签") + ","
                + node("end", "snaker:end", "", "结束")
                + "],\"edges\":["
                + edge("e0", "start", "apply") + ","
                + edge("e1", "apply", "cs") + ","
                + edge("e2", "cs", "end")
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

    private long rows(Long instanceId, String name) {
        return repo.findHistoryTasks(instanceId).stream()
                .filter(t -> name.equals(t.getTaskName()))
                .count();
    }

    @Test
    public void zeroActorSequentialCreatesOneRowWithoutActors() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlowWithZeroActorSequentialCountersign();
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "applicant", FlowData.create());
        Long iid = inst.getInstanceId();

        ProcessTask apply = doing(iid, "apply");
        assertNotNull("发起后应停在申请节点", apply);
        // 改前：这一步里的 createCountersignTasks 会对空名册 get(0) 越界（IndexOutOfBoundsException）
        engine.executeProcessTask(apply.getTaskId(), "applicant",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, APPLY));

        assertEquals("引擎不得报错，且恰好建出 1 行会签待办",
                1, repo.findDoingTasks(iid, new String[]{"cs"}).size());
        assertEquals("会签节点总共只有这 1 行", 1, rows(iid, "cs"));

        ProcessTask cs = repo.findDoingTasks(iid, new String[]{"cs"}).get(0);
        assertEquals("行状态 DOING(10)", ProcessTaskStateEnum.DOING.getCode(), cs.getTaskState());
        assertEquals("perform_type 是会签", ProcessTaskPerformTypeEnum.COUNTERSIGN, cs.getPerformType());
        assertTrue("参与者行数 0", repo.findTaskActors(cs.getTaskId()).isEmpty());
        assertTrue("任务聚合上的参与者同样为空", cs.getActorIds() == null || cs.getActorIds().isEmpty());

        // 会签三枚任务变量照旧写：空名单 / 0 / 0
        assertEquals(Collections.emptyList(),
                cs.getVariables().get(FlowConst.COUNTERSIGN_OPERATOR_LIST + "_cs"));
        assertEquals(Integer.valueOf(0), cs.getVariables().getInt(FlowConst.LOOP_COUNTER + "_cs"));
        assertEquals(Integer.valueOf(0), cs.getVariables().getInt(FlowConst.NR_OF_INSTANCES + "_cs"));

        assertEquals("实例仍在进行中（没有凭空前进或办结）",
                ProcessInstanceStateEnum.DOING.getCode(), repo.findInstanceById(iid).getState());
    }
}
