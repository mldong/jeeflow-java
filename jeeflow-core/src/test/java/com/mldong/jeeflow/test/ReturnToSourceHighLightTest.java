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
import com.mldong.jeeflow.facade.JeeflowFacade;
import com.mldong.jeeflow.spi.IOrgUserProvider;
import com.mldong.jeeflow.spi.IUserProvider;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * issues/241：highLight 重放腿在决策节点必须按**任务行佐证**选边，不得按重提后的
 * 实例变量重算（直返契约：来源节点之前的条件分支不重算）。
 *
 * <p>夹具：start → apply → dept → decision1{amount>1000 → taskHigh(直返档) | amount<=1000 → taskLow} → end。
 * 走单：6666 走 taskHigh → 退回发起人 → 改为 666 重提 → 新待办仍落 taskHigh。
 * 此时高亮必须只点亮实际走过的 e0/e1/e2/e3，不得把 taskLow/e4/e6 算进历史。</p>
 */
public class ReturnToSourceHighLightTest {

    private static final String FIXTURE = "{\"name\":\"i241-return-highlight\","
            + "\"displayName\":\"直返重提高亮\",\"type\":\"approval\",\"nodes\":["
            + "{\"id\":\"start\",\"type\":\"snaker:start\"},"
            + "{\"id\":\"apply\",\"type\":\"snaker:task\",\"properties\":{\"assignee\":\"applicant\",\"taskType\":0,\"performType\":0}},"
            + "{\"id\":\"dept\",\"type\":\"snaker:task\",\"properties\":{\"assignee\":\"leader\",\"taskType\":0,\"performType\":0}},"
            + "{\"id\":\"decision1\",\"type\":\"snaker:decision\",\"properties\":{\"expr\":\"f_amount > 1000\"}},"
            + "{\"id\":\"taskHigh\",\"type\":\"snaker:task\",\"properties\":{\"assignee\":\"director\",\"taskType\":0,\"performType\":0,\"reapplyMode\":\"RETURN_TO_SOURCE\"}},"
            + "{\"id\":\"taskLow\",\"type\":\"snaker:task\",\"properties\":{\"assignee\":\"manager\",\"taskType\":0,\"performType\":0}},"
            + "{\"id\":\"end\",\"type\":\"snaker:end\"}],"
            + "\"edges\":["
            + "{\"id\":\"e0\",\"sourceNodeId\":\"start\",\"targetNodeId\":\"apply\",\"properties\":{}},"
            + "{\"id\":\"e1\",\"sourceNodeId\":\"apply\",\"targetNodeId\":\"dept\",\"properties\":{}},"
            + "{\"id\":\"e2\",\"sourceNodeId\":\"dept\",\"targetNodeId\":\"decision1\",\"properties\":{}},"
            + "{\"id\":\"e3\",\"sourceNodeId\":\"decision1\",\"targetNodeId\":\"taskHigh\",\"properties\":{\"expr\":\"f_amount > 1000\"}},"
            + "{\"id\":\"e4\",\"sourceNodeId\":\"decision1\",\"targetNodeId\":\"taskLow\",\"properties\":{\"expr\":\"f_amount <= 1000\"}},"
            + "{\"id\":\"e5\",\"sourceNodeId\":\"taskHigh\",\"targetNodeId\":\"end\",\"properties\":{}},"
            + "{\"id\":\"e6\",\"sourceNodeId\":\"taskLow\",\"targetNodeId\":\"end\",\"properties\":{}}]}";

    private static final String FIXTURE_JOIN = "{\"name\":\"i241-return-highlight-join\","
            + "\"displayName\":\"直返重提高亮（汇合后分支）\",\"type\":\"approval\",\"nodes\":["
            + "{\"id\":\"start\",\"type\":\"snaker:start\"},"
            + "{\"id\":\"apply\",\"type\":\"snaker:task\",\"properties\":{\"assignee\":\"applicant\",\"taskType\":0,\"performType\":0}},"
            + "{\"id\":\"fork1\",\"type\":\"snaker:fork\",\"properties\":{}},"
            + "{\"id\":\"taskA\",\"type\":\"snaker:task\",\"properties\":{\"assignee\":\"userA\",\"taskType\":0,\"performType\":0}},"
            + "{\"id\":\"taskB\",\"type\":\"snaker:task\",\"properties\":{\"assignee\":\"userB\",\"taskType\":0,\"performType\":0}},"
            + "{\"id\":\"join1\",\"type\":\"snaker:join\",\"properties\":{}},"
            + "{\"id\":\"decision1\",\"type\":\"snaker:decision\",\"properties\":{\"expr\":\"f_amount > 1000\"}},"
            + "{\"id\":\"taskHigh\",\"type\":\"snaker:task\",\"properties\":{\"assignee\":\"director\",\"taskType\":0,\"performType\":0,\"reapplyMode\":\"RETURN_TO_SOURCE\"}},"
            + "{\"id\":\"taskLow\",\"type\":\"snaker:task\",\"properties\":{\"assignee\":\"manager\",\"taskType\":0,\"performType\":0}},"
            + "{\"id\":\"end\",\"type\":\"snaker:end\"}],"
            + "\"edges\":["
            + "{\"id\":\"e0\",\"sourceNodeId\":\"start\",\"targetNodeId\":\"apply\",\"properties\":{}},"
            + "{\"id\":\"e1\",\"sourceNodeId\":\"apply\",\"targetNodeId\":\"fork1\",\"properties\":{}},"
            + "{\"id\":\"e2\",\"sourceNodeId\":\"fork1\",\"targetNodeId\":\"taskA\",\"properties\":{}},"
            + "{\"id\":\"e3\",\"sourceNodeId\":\"fork1\",\"targetNodeId\":\"taskB\",\"properties\":{}},"
            + "{\"id\":\"e4\",\"sourceNodeId\":\"taskA\",\"targetNodeId\":\"join1\",\"properties\":{}},"
            + "{\"id\":\"e5\",\"sourceNodeId\":\"taskB\",\"targetNodeId\":\"join1\",\"properties\":{}},"
            + "{\"id\":\"e6\",\"sourceNodeId\":\"join1\",\"targetNodeId\":\"decision1\",\"properties\":{}},"
            + "{\"id\":\"e7\",\"sourceNodeId\":\"decision1\",\"targetNodeId\":\"taskHigh\",\"properties\":{\"expr\":\"f_amount > 1000\"}},"
            + "{\"id\":\"e8\",\"sourceNodeId\":\"decision1\",\"targetNodeId\":\"taskLow\",\"properties\":{\"expr\":\"f_amount <= 1000\"}},"
            + "{\"id\":\"e9\",\"sourceNodeId\":\"taskHigh\",\"targetNodeId\":\"end\",\"properties\":{}},"
            + "{\"id\":\"e10\",\"sourceNodeId\":\"taskLow\",\"targetNodeId\":\"end\",\"properties\":{}}]}";

    private JeeflowFacade facade;
    private JeeflowEngine engine;
    private MemoryProcessRepository repo;

    @Before
    public void setUp() {
        Configuration config = new Configuration();
        repo = new MemoryProcessRepository();
        MemoryProcessExtRepository extRepo = new MemoryProcessExtRepository();
        TestJsonProvider json = new TestJsonProvider();

        ServiceContext.put("repository", repo);
        ServiceContext.put("json", json);
        ServiceContext.put("expr", new TestExpressionEvaluator());
        ServiceContext.put("user", new IUserProvider() {
            @Override public UserInfo getUser(String userId) {
                UserInfo u = new UserInfo();
                u.setUserId(userId);
                u.setRealName("用户" + userId);
                return u;
            }
        });
        ServiceContext.put("org", new IOrgUserProvider() {
            @Override public List<String> findDeptLeaders(String deptId) { return null; }
            @Override public List<String> findDeptMainLeaders(String deptId) { return null; }
            @Override public List<String> findByRole(String roleCode) { return null; }
        });

        engine = new JeeflowEngineImpl();
        engine.configure(config);
        facade = new JeeflowFacade(engine, repo, extRepo);
    }

    private ProcessInstance.ProcessDefine registerFlowJson(String jsonStr) throws Exception {
        byte[] bytes = jsonStr.getBytes(StandardCharsets.UTF_8);
        com.mldong.jeeflow.model.logicflow.LfModel lfModel =
                new TestJsonProvider().getMapper().readValue(jsonStr, com.mldong.jeeflow.model.logicflow.LfModel.class);
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
            if (name.equals(t.getTaskName())) return t;
        }
        return null;
    }

    private FlowData submit(ProcessSubmitTypeEnum type) {
        return FlowData.create().set(FlowConst.SUBMIT_TYPE, type.getCode());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testReapplyDoesNotLightUnvisitedDecisionBranch() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlowJson(FIXTURE);
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "zhangsan",
                FlowData.create().set("f_amount", 6666));
        Long iid = inst.getInstanceId();
        for (ProcessTask t : repo.findDoingTasks(iid, null)) {
            engine.executeProcessTask(t.getTaskId(), "zhangsan", submit(ProcessSubmitTypeEnum.APPLY));
        }
        // dept 同意 → decision1 判 amount>1000 → taskHigh
        ProcessTask dept = doing(iid, "dept");
        assertNotNull("夹具自证：应走到 dept", dept);
        engine.executeProcessTask(dept.getTaskId(), "leader", submit(ProcessSubmitTypeEnum.AGREE));
        ProcessTask high = doing(iid, "taskHigh");
        assertNotNull("夹具自证：6666 应走 taskHigh", high);
        assertNull(doing(iid, "taskLow"));

        // taskHigh 退回发起人 → 申请人改成 666 重提，直返契约要求仍回 taskHigh
        engine.executeAndJumpToFirstTaskNode(high.getTaskId(), "director",
                submit(ProcessSubmitTypeEnum.ROLLBACK_TO_OPERATOR));
        ProcessTask apply = doing(iid, "apply");
        assertNotNull("退回后应有申请待办", apply);
        engine.executeProcessTask(apply.getTaskId(), "zhangsan",
                submit(ProcessSubmitTypeEnum.RE_APPLY).set("f_amount", 666));
        assertNotNull("重提必须回原来源节点", doing(iid, "taskHigh"));
        assertNull("不得按改后的条件改走 taskLow", doing(iid, "taskLow"));

        Map<String, Object> r = (Map<String, Object>) facade.flow("processInstance/highLight",
                new LinkedHashMap<String, Object>() {{ put("id", iid); }});
        assertEquals("code 应为 0: " + r, Integer.valueOf(0), r.get("code"));
        Map<String, Object> data = (Map<String, Object>) r.get("data");
        List<String> edges = (List<String>) data.get("historyEdgeNames");
        List<String> nodes = (List<String>) data.get("historyNodeNames");

        for (String e : new String[]{"e0", "e1", "e2", "e3"}) {
            assertTrue("实际走过的边 " + e + " 必须点亮（实际 " + edges + "）", edges.contains(e));
        }
        assertFalse("未走分支 e4 不得点亮（实际 " + edges + "）", edges.contains("e4"));
        assertFalse("未走分支 e6 不得点亮（实际 " + edges + "）", edges.contains("e6"));
        assertFalse("未走节点 taskLow 不得进历史（实际 " + nodes + "）", nodes.contains("taskLow"));
        assertFalse("未走节点 end 不得进历史（实际 " + nodes + "）", nodes.contains("end"));
    }

    /**
     * 同一病状的通用红样：决策节点的入边来源改成 join（网关不产任务行）。
     * 任务行快照实例变量的栈（go/python/node/moon）在案卷夹具上会被「最老一条前置任务行」
     * 偶然救活；join 入边让那条通道失效，八栈同夹具都能红。
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testReapplyDoesNotLightUnvisitedBranchBehindJoin() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlowJson(FIXTURE_JOIN);
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "zhangsan",
                FlowData.create().set("f_amount", 6666));
        Long iid = inst.getInstanceId();
        for (ProcessTask t : repo.findDoingTasks(iid, null)) {
            engine.executeProcessTask(t.getTaskId(), "zhangsan", submit(ProcessSubmitTypeEnum.APPLY));
        }
        for (String name : new String[]{"taskA", "taskB"}) {
            ProcessTask t = doing(iid, name);
            assertNotNull("夹具自证：并行分支 " + name + " 应就绪", t);
            engine.executeProcessTask(t.getTaskId(), t.getActorIds().get(0),
                    submit(ProcessSubmitTypeEnum.AGREE));
        }
        ProcessTask high = doing(iid, "taskHigh");
        assertNotNull("夹具自证：汇合后 6666 应走 taskHigh", high);
        assertNull(doing(iid, "taskLow"));

        engine.executeAndJumpToFirstTaskNode(high.getTaskId(), "director",
                submit(ProcessSubmitTypeEnum.ROLLBACK_TO_OPERATOR));
        ProcessTask apply = doing(iid, "apply");
        assertNotNull("退回后应有申请待办", apply);
        engine.executeProcessTask(apply.getTaskId(), "zhangsan",
                submit(ProcessSubmitTypeEnum.RE_APPLY).set("f_amount", 666));
        assertNotNull("重提必须回原来源节点", doing(iid, "taskHigh"));

        Map<String, Object> r = (Map<String, Object>) facade.flow("processInstance/highLight",
                new LinkedHashMap<String, Object>() {{ put("id", iid); }});
        assertEquals("code 应为 0: " + r, Integer.valueOf(0), r.get("code"));
        Map<String, Object> data = (Map<String, Object>) r.get("data");
        List<String> edges = (List<String>) data.get("historyEdgeNames");
        List<String> nodes = (List<String>) data.get("historyNodeNames");

        assertTrue("汇合后的实际分支 e7 必须点亮（实际 " + edges + "）", edges.contains("e7"));
        assertFalse("未走分支 e8 不得点亮（实际 " + edges + "）", edges.contains("e8"));
        assertFalse("未走分支 e10 不得点亮（实际 " + edges + "）", edges.contains("e10"));
        assertFalse("未走节点 taskLow 不得进历史（实际 " + nodes + "）", nodes.contains("taskLow"));
        assertFalse("未走节点 end 不得进历史（实际 " + nodes + "）", nodes.contains("end"));
    }
}
