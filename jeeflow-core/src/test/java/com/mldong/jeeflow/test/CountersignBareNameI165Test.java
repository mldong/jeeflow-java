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
import com.mldong.jeeflow.spi.IUserProvider;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * issues/165 · 并行会签门控「裸名」判据——`#nrOfCompletedInstances>=2`（文档/设计器形状）
 * 在生产求值器上必须真能放行。
 *
 * <p>改前形状：{@code buildCountersignVars} 只挂前缀键 {@code csv_<node>_nrOf*}，
 * 生产 SpEL 的 `#变量` 精确查表查不到裸名 ⇒ 恒 false（活栈实测 3/3 全办完仍停住）。
 * 旧 {@link TestExpressionEvaluator} 靠 endsWith 后缀桥自证绿——桥已按生产形状拆除
 * （issues/165 拍板附案），本类判据只有在 handler 真挂了裸名时才可能绿。</p>
 *
 * <p>夹具＝<b>内联</b>三成员并行会签（applicant → cs1(userA,userB,userC) → end），
 * 不动共享 flows/（编辑源在 java，各仓副本随 resolver 镜像）。前缀键形状另立一格
 * 证明兼容面没丢。</p>
 */
public class CountersignBareNameI165Test {

    private JeeflowEngine engine;
    private MemoryProcessRepository repo;

    @Before
    public void setUp() {
        Configuration config = new Configuration();
        repo = new MemoryProcessRepository();
        ServiceContext.put("repository", repo);
        ServiceContext.put("json", new TestJsonProvider());
        ServiceContext.put("expr", new TestExpressionEvaluator());
        ServiceContext.put("user", new IUserProvider() {
            @Override public IUserProvider.UserInfo getUser(String userId) {
                IUserProvider.UserInfo u = new IUserProvider.UserInfo();
                u.setUserId(userId);
                u.setRealName("用户" + userId);
                u.setDeptId("D01");
                u.setDeptName("测试部门");
                u.setPostId("P01");
                u.setPostName("测试岗位");
                return u;
            }
        });
        engine = new JeeflowEngineImpl();
        engine.configure(config);
    }

    /** 内联并行会签定义；condition 直接进节点 countersignCompletionCondition */
    private ProcessInstance.ProcessDefine registerInline(String name, String condition) throws Exception {
        String json = "{"
                + "\"name\": \"" + name + "\","
                + "\"displayName\": \"裸名会签(165夹具)\","
                + "\"type\": \"approval\","
                + "\"nodes\": ["
                + "{\"id\":\"start\",\"type\":\"snaker:start\",\"x\":100,\"y\":200,"
                + "  \"properties\":{\"width\":50,\"height\":50},\"text\":{\"value\":\"开始\"}},"
                + "{\"id\":\"apply\",\"type\":\"snaker:task\",\"x\":200,\"y\":200,"
                + "  \"properties\":{\"width\":100,\"height\":50,\"form\":\"apply-form\","
                + "    \"assignee\":\"applicant\",\"taskType\":0,\"performType\":0},"
                + "  \"text\":{\"value\":\"发起申请\"}},"
                + "{\"id\":\"cs1\",\"type\":\"snaker:task\",\"x\":350,\"y\":200,"
                + "  \"properties\":{\"width\":120,\"height\":60,\"form\":\"countersign-form\","
                + "    \"assignee\":\"userA,userB,userC\",\"taskType\":0,\"performType\":\"1\","
                + "    \"countersignType\":\"PARALLEL\","
                + "    \"countersignCompletionCondition\":\"" + condition + "\"},"
                + "  \"text\":{\"value\":\"会签审批\"}},"
                + "{\"id\":\"end\",\"type\":\"snaker:end\",\"x\":600,\"y\":200,"
                + "  \"properties\":{\"width\":50,\"height\":50},\"text\":{\"value\":\"结束\"}}"
                + "],"
                + "\"edges\": ["
                + "{\"id\":\"e0\",\"sourceNodeId\":\"start\",\"targetNodeId\":\"apply\",\"properties\":{}},"
                + "{\"id\":\"e1\",\"sourceNodeId\":\"apply\",\"targetNodeId\":\"cs1\",\"properties\":{}},"
                + "{\"id\":\"e2\",\"sourceNodeId\":\"cs1\",\"targetNodeId\":\"end\",\"properties\":{}}"
                + "]}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        com.mldong.jeeflow.model.logicflow.LfModel lfModel = new TestJsonProvider().getMapper()
                .readValue(json, com.mldong.jeeflow.model.logicflow.LfModel.class);
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

    private ProcessInstance startFlow(ProcessInstance.ProcessDefine def) throws Exception {
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "applicant", FlowData.create());
        for (ProcessTask task : repo.findDoingTasks(inst.getInstanceId(), null)) {
            repo.addTaskActor(task.getTaskId(), Arrays.asList("applicant"));
            task.getActorIds().add("applicant");
            engine.executeProcessTask(task.getTaskId(), "applicant",
                    FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.APPLY.getCode()));
        }
        return repo.findInstanceById(inst.getInstanceId());
    }

    private void completeOne(ProcessInstance inst, String actor) throws Exception {
        ProcessTask task = repo.findDoingTasks(inst.getInstanceId(), null).get(0);
        repo.addTaskActor(task.getTaskId(), Arrays.asList(actor));
        task.getActorIds().add(actor);
        engine.executeProcessTask(task.getTaskId(), actor,
                FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));
    }

    @Test
    public void bareNameReleasesAtThreshold() throws Exception {
        // 文档/设计器形状：#nrOfCompletedInstances>=2，3 人会签 2 票即放行
        ProcessInstance.ProcessDefine def = registerInline("i165-bare", "#nrOfCompletedInstances>=2");
        ProcessInstance inst = startFlow(def);
        assertEquals(3, repo.findDoingTasks(inst.getInstanceId(), null).size());

        completeOne(inst, "userA");
        ProcessInstance afterOne = repo.findInstanceById(inst.getInstanceId());
        assertEquals("1/3 不得放行", 2, repo.findDoingTasks(inst.getInstanceId(), null).size());
        assertEquals(ProcessInstanceStateEnum.DOING.getCode(), afterOne.getState());

        completeOne(inst, "userB");
        ProcessInstance afterTwo = repo.findInstanceById(inst.getInstanceId());
        assertEquals("2/3 必须放行（裸名形状）——改前此处 3/3 全办完也停住",
                ProcessInstanceStateEnum.FINISHED.getCode(), afterTwo.getState());
    }

    @Test
    public void prefixKeyStillReleases() throws Exception {
        // 前缀键形状（引擎内部命名）保留兼容：#csv_cs1_nrOfCompletedInstances>=2
        ProcessInstance.ProcessDefine def =
                registerInline("i165-prefix", "#csv_cs1_nrOfCompletedInstances>=2");
        ProcessInstance inst = startFlow(def);
        completeOne(inst, "userA");
        completeOne(inst, "userC");
        assertEquals("前缀键形状 2/3 照常放行（165 不改既有兼容面）",
                ProcessInstanceStateEnum.FINISHED.getCode(),
                repo.findInstanceById(inst.getInstanceId()).getState());
    }

    @Test
    public void prefixKeyOnlyNeverSatisfiesBareName() throws Exception {
        // 病根档（求值器侧形状）：条件引用裸名、上下文只有前缀键 ⇒ 必须不放行。
        // 该格在 handler 修复后只能由"上下文真没有裸名"触发——用 candidate 流程变量
        // 绕不进来，故直接打求值器：TestExpressionEvaluator（已拆桥，生产形状）。
        java.util.Map<String, Object> vars = new java.util.HashMap<>();
        vars.put("csv_cs1_nrOfCompletedInstances", 2);
        Object result = new TestExpressionEvaluator().eval("#nrOfCompletedInstances>=2", vars);
        assertEquals("只挂前缀键时裸名条件必须求不出真（165 病根本形）", Boolean.FALSE, result);
    }
}
