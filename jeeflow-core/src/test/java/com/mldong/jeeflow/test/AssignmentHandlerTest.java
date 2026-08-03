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
import com.mldong.jeeflow.spi.IUserProvider.UserInfo;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.*;

/**
 * issues/16 参与者解析测试 — 内置通用 handler（表单字段 / 发起人 / 部门领导 / 角色）
 *
 * <p>流程 11-assignment-handler.json 四个任务节点：
 * task1=FormFieldAssigneeHandler、task2=OperatorAssignmentHandler、
 * task3=DeptLeaderAssignmentHandler、task4=TaskRoleAssigneeHandler。</p>
 */
public class AssignmentHandlerTest {

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
            @Override public UserInfo getUser(String userId) {
                UserInfo u = new UserInfo();
                u.setUserId(userId);
                u.setRealName("用户" + userId);
                u.setDeptId("D01");
                u.setDeptName("测试部门");
                u.setPostId("P01");
                u.setPostName("测试岗位");
                return u;
            }
        });
        ServiceContext.put("org", new MemoryOrgUserProvider());

        engine = new JeeflowEngineImpl();
        engine.configure(config);
    }

    private ProcessInstance.ProcessDefine registerFlow(String filename) throws Exception {
        byte[] bytes = Files.readAllBytes(
                Paths.get("src/test/resources/flows/" + filename));
        String jsonStr = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
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

    private List<ProcessTask> doingTasks(ProcessInstance inst) {
        return repo.findDoingTasks(inst.getInstanceId(), null);
    }

    private ProcessTask firstDoing(ProcessInstance inst) {
        List<ProcessTask> tasks = doingTasks(inst);
        assertEquals(1, tasks.size());
        return tasks.get(0);
    }

    private void complete(ProcessInstance inst, String operator) throws Exception {
        ProcessTask task = firstDoing(inst);
        FlowData args = FlowData.create().set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode());
        engine.executeProcessTask(task.getTaskId(), operator, args);
    }

    /** 四个内置 handler 全链路：字段 → 发起人 → 部门领导 → 角色 → 结束 */
    @Test
    public void testBuiltinAssignmentHandlers() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow("11-assignment-handler.json");

        // 发起（user1），表单字段 task1 传两个参与者
        FlowData startArgs = FlowData.create().set("task1", "userA,userB");
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "user1", startArgs);
        assertEquals(ProcessInstanceStateEnum.DOING.getCode(), inst.getState());

        // ① FormFieldAssigneeHandler：节点 task1 → args.task1 = userA,userB
        ProcessTask t1 = firstDoing(inst);
        assertEquals("task1", t1.getTaskName());
        assertEquals(2, t1.getActorIds().size());
        assertTrue(t1.getActorIds().contains("userA"));
        assertTrue(t1.getActorIds().contains("userB"));
        complete(inst, "userA");

        // ② OperatorAssignmentHandler：task2 → 发起人 user1
        ProcessTask t2 = firstDoing(inst);
        assertEquals("task2", t2.getTaskName());
        assertEquals(1, t2.getActorIds().size());
        assertEquals("user1", t2.getActorIds().get(0));
        complete(inst, "user1");

        // ③ DeptLeaderAssignmentHandler：task3 → user1 部门 D01 领导 = leader1,leader2
        ProcessTask t3 = firstDoing(inst);
        assertEquals("task3", t3.getTaskName());
        assertEquals(2, t3.getActorIds().size());
        assertTrue(t3.getActorIds().contains("leader1"));
        assertTrue(t3.getActorIds().contains("leader2"));
        complete(inst, "leader1");

        // ④ TaskRoleAssigneeHandler：task4 → roleCode=task4 → roleA,roleB
        ProcessTask t4 = firstDoing(inst);
        assertEquals("task4", t4.getTaskName());
        assertEquals(2, t4.getActorIds().size());
        assertTrue(t4.getActorIds().contains("roleA"));
        assertTrue(t4.getActorIds().contains("roleB"));
        complete(inst, "roleA");

        // 结束
        ProcessInstance updated = repo.findInstanceById(inst.getInstanceId());
        assertEquals(ProcessInstanceStateEnum.FINISHED.getCode(), updated.getState());
    }
}
