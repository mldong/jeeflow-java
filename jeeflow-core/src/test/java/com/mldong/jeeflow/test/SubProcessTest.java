package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.JeeflowException;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.core.StartAndExecuteSupport;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessEventTypeEnum;
import com.mldong.jeeflow.enums.ProcessInstanceStateEnum;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
import com.mldong.jeeflow.enums.ProcessTaskStateEnum;
import com.mldong.jeeflow.event.ProcessEvent;
import com.mldong.jeeflow.event.ProcessEventListener;
import com.mldong.jeeflow.model.NodeModel;
import com.mldong.jeeflow.model.ProcessModel;
import com.mldong.jeeflow.model.SubProcessModel;
import com.mldong.jeeflow.model.TaskModel;
import com.mldong.jeeflow.parser.ModelParser;
import com.mldong.jeeflow.spi.IExpressionEvaluator;
import com.mldong.jeeflow.spi.IUserProvider;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.*;

public class SubProcessTest {
    private JeeflowEngine engine;
    private MemoryProcessRepository repo;
    private final List<ProcessEvent> events = new CopyOnWriteArrayList<>();

    @Before
    public void setUp() {
        Configuration config = new Configuration();
        repo = new MemoryProcessRepository();
        ServiceContext.put("repository", repo);
        ServiceContext.put("json", new TestJsonProvider());
        ServiceContext.put("expr", new TestExpressionEvaluator());
        ServiceContext.put("user", new IUserProvider() {
            @Override public UserInfo getUser(String userId) {
                return UserInfo.of(userId);
            }
        });
        ServiceContext.put("subProcessEvents", (ProcessEventListener) events::add);
        engine = new JeeflowEngineImpl();
        engine.configure(config);
    }

    @Test
    public void sharedParentAndChildFlowsParseAndRun() throws Exception {
        String parentJson = new String(Files.readAllBytes(Paths.get(
                "src/test/resources/flows/20-subprocess-parent.json")), StandardCharsets.UTF_8);
        String childJson = new String(Files.readAllBytes(Paths.get(
                "src/test/resources/flows/21-subprocess-child.json")), StandardCharsets.UTF_8);
        ProcessInstance.ProcessDefine child = addDefine("subprocess-child", 1, childJson);
        ProcessInstance.ProcessDefine parent = addDefine("subprocess-parent", 1, parentJson);
        ProcessInstance parentInstance = engine.startProcessInstanceById(parent.getId(), "applicant", FlowData.create());
        StartAndExecuteSupport.applyInitialTasks(engine, parentInstance.getInstanceId(), "applicant", FlowData.create());
        ProcessInstance childInstance = onlyChild(parentInstance.getInstanceId());
        assertEquals(child.getId(), childInstance.getDefineId());
        assertEquals("childReview", onlyDoing(childInstance.getInstanceId()).getTaskName());
    }

    @Test
    public void parentChildClosesLoopAndChildApplyIsAutoExecutedOnce() {
        ProcessInstance.ProcessDefine child = addDefine("child257", 1,
                flow("child257", "start", "apply", "childReview", "end"));
        ProcessInstance.ProcessDefine parent = addDefine("parent257", 1,
                flow("parent257", "start", "apply", "sub257", "parentAfter", "end"));

        ProcessInstance parentInstance = engine.startProcessInstanceById(parent.getId(), "applicant", FlowData.create());
        StartAndExecuteSupport.applyInitialTasks(engine, parentInstance.getInstanceId(), "applicant", FlowData.create());

        ProcessInstance childInstance = onlyChild(parentInstance.getInstanceId());
        assertNotNull(childInstance);
        assertEquals(parentInstance.getInstanceId(), childInstance.getParentId());
        assertEquals("sub257", childInstance.getParentNodeName());
        assertEquals(child.getId(), childInstance.getDefineId());
        assertEquals(ProcessInstanceStateEnum.DOING.getCode(), childInstance.getState());
        assertEquals(ProcessTaskStateEnum.FINISHED.getCode(),
                repo.findDoneTasks(childInstance.getInstanceId(), new String[]{"apply"}).get(0).getTaskState());
        assertEquals(FlowConst.AUTO_ID,
                repo.findDoneTasks(childInstance.getInstanceId(), new String[]{"apply"}).get(0).getActorId());
        assertEquals("childReview", onlyDoing(childInstance.getInstanceId()).getTaskName());
        assertEquals(ProcessInstanceStateEnum.DOING.getCode(), parentInstance.getState());
        assertTrue(repo.findDoingTasks(parentInstance.getInstanceId(), new String[]{"parentAfter"}).isEmpty());
        assertEquals(1, countTaskStart(childInstance.getInstanceId(), "childReview"));

        ProcessTask childReview = onlyDoing(childInstance.getInstanceId());
        engine.executeProcessTask(childReview.getTaskId(), "leader", FlowData.create()
                .set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));
        assertEquals(ProcessInstanceStateEnum.FINISHED.getCode(),
                repo.findInstanceById(childInstance.getInstanceId()).getState());
        assertEquals("parentAfter", onlyDoing(parentInstance.getInstanceId()).getTaskName());
        assertEquals(ProcessInstanceStateEnum.DOING.getCode(),
                repo.findInstanceById(parentInstance.getInstanceId()).getState());

        ProcessTask parentAfter = onlyDoing(parentInstance.getInstanceId());
        engine.executeProcessTask(parentAfter.getTaskId(), "manager", FlowData.create()
                .set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));
        assertEquals(ProcessInstanceStateEnum.FINISHED.getCode(),
                repo.findInstanceById(parentInstance.getInstanceId()).getState());
        assertEquals(1, countEvent(ProcessEventTypeEnum.PROCESS_INSTANCE_START, childInstance.getInstanceId()));
        assertEquals(1, countEvent(ProcessEventTypeEnum.PROCESS_INSTANCE_END, childInstance.getInstanceId()));
        assertEquals(1, countEvent(ProcessEventTypeEnum.PROCESS_INSTANCE_START, parentInstance.getInstanceId()));
        assertEquals(1, countEvent(ProcessEventTypeEnum.PROCESS_INSTANCE_END, parentInstance.getInstanceId()));
    }

    @Test
    public void missingDefinitionFailsBeforeParentTaskIsWritten() {
        ProcessInstance.ProcessDefine parent = addDefine("parentMissing257", 1,
                flowWithProps("parentMissing257", "start", "apply", "subMissing257", "end",
                        "\"processName\":\"missing257\",\"version\":1"));
        ProcessInstance parentInstance = engine.startProcessInstanceById(parent.getId(), "applicant", FlowData.create());
        ProcessTask apply = onlyDoing(parentInstance.getInstanceId());
        int taskCount = repo.findHistoryTasks(parentInstance.getInstanceId()).size();
        int instanceCount = repo.pageInstances(new com.mldong.jeeflow.spi.PageQuery(1, 100)).getRecordCount();

        try {
            StartAndExecuteSupport.applyInitialTasks(engine, parentInstance.getInstanceId(), "applicant", FlowData.create());
            fail("缺子流程定义必须报错");
        } catch (JeeflowException e) {
            assertEquals("子流程missing257定义不存在", e.getMessage());
        }
        assertEquals(ProcessTaskStateEnum.DOING.getCode(), repo.findTaskById(apply.getTaskId()).getTaskState());
        assertEquals(taskCount, repo.findHistoryTasks(parentInstance.getInstanceId()).size());
        assertEquals(instanceCount, repo.pageInstances(new com.mldong.jeeflow.spi.PageQuery(1, 100)).getRecordCount());
        assertEquals(ProcessInstanceStateEnum.DOING.getCode(),
                repo.findInstanceById(parentInstance.getInstanceId()).getState());
    }

    @Test
    public void processNameNameAndIdFallbackPlusLatestVersion() {
        ProcessModel byProcessName = ModelParser.parse(json(flowWithProps("p1", "start", "apply", "sub1", "end",
                "\"processName\":\"target\",\"name\":\"wrong\",\"form\":\"__wrong__\",\"version\":1")));
        SubProcessModel processNameModel = (SubProcessModel) byProcessName.getNode("sub1");
        assertEquals("target", processNameModel.getProcessName());
        assertEquals("__wrong__", processNameModel.getForm());

        ProcessModel byName = ModelParser.parse(json(flowWithProps("p2", "start", "apply", "sub2", "end",
                "\"name\":\"target\",\"form\":\"__wrong__\"")));
        assertEquals("target", ((SubProcessModel) byName.getNode("sub2")).getProcessName());

        ProcessModel byId = ModelParser.parse(json(flowWithProps("p3", "start", "apply", "target", "end", "")));
        assertEquals("target", ((SubProcessModel) byId.getNode("target")).getProcessName());

        ProcessInstance.ProcessDefine v1 = addDefine("versioned257", 1,
                flow("versioned257", "start", "apply", "reviewV1", "end"));
        ProcessInstance.ProcessDefine v2 = addDefine("versioned257", 2,
                flow("versioned257", "start", "apply", "reviewV2", "end"));
        ProcessInstance.ProcessDefine parent = addDefine("parentVersion257", 1,
                flowWithProps("parentVersion257", "start", "apply", "subVersion257", "end",
                        "\"processName\":\"versioned257\",\"form\":\"__wrong__\""));
        ProcessInstance parentInstance = engine.startProcessInstanceById(parent.getId(), "applicant", FlowData.create());
        StartAndExecuteSupport.applyInitialTasks(engine, parentInstance.getInstanceId(), "applicant", FlowData.create());
        assertEquals(v2.getId(), onlyChild(parentInstance.getInstanceId()).getDefineId());

        ProcessInstance.ProcessDefine exactParent = addDefine("parentExact257", 1,
                flowWithProps("parentExact257", "start", "apply", "subExact257", "end",
                        "\"processName\":\"versioned257\",\"version\":1,\"form\":\"__wrong__\""));
        ProcessInstance exactInstance = engine.startProcessInstanceById(exactParent.getId(), "applicant", FlowData.create());
        StartAndExecuteSupport.applyInitialTasks(engine, exactInstance.getInstanceId(), "applicant", FlowData.create());
        assertEquals(v1.getId(), onlyChild(exactInstance.getInstanceId()).getDefineId());
    }

    @Test
    public void childWithoutApplySelfHandlesFirstTask() {
        addDefine("childNoApply257", 1,
                flow("childNoApply257", "start", "first257", "second257", "end"));
        ProcessInstance.ProcessDefine parent = addDefine("parentNoApply257", 1,
                flowWithProps("parentNoApply257", "start", "apply", "subNoApply257", "end",
                        "\"processName\":\"childNoApply257\",\"version\":1"));
        ProcessInstance parentInstance = engine.startProcessInstanceById(parent.getId(), "applicant", FlowData.create());
        StartAndExecuteSupport.applyInitialTasks(engine, parentInstance.getInstanceId(), "applicant", FlowData.create());

        ProcessInstance child = onlyChild(parentInstance.getInstanceId());
        assertEquals("second257", onlyDoing(child.getInstanceId()).getTaskName());
        ProcessTask first = repo.findDoneTasks(child.getInstanceId(), new String[]{"first257"}).get(0);
        assertEquals(FlowConst.AUTO_ID, first.getActorId());
        assertTrue(repo.findTaskActors(first.getTaskId()).contains("applicant"));
    }

    @Test
    public void childRejectStillReturnsToParent() {
        addDefine("childReject257", 1,
                flow("childReject257", "start", "apply", "childReview", "end"));
        ProcessInstance.ProcessDefine parent = addDefine("parentReject257", 1,
                flowWithProps("parentReject257", "start", "apply", "subReject257", "end",
                        "\"processName\":\"childReject257\",\"version\":1"));
        ProcessInstance parentInstance = engine.startProcessInstanceById(parent.getId(), "applicant", FlowData.create());
        StartAndExecuteSupport.applyInitialTasks(engine, parentInstance.getInstanceId(), "applicant", FlowData.create());
        ProcessInstance child = onlyChild(parentInstance.getInstanceId());
        engine.executeProcessTask(onlyDoing(child.getInstanceId()).getTaskId(), "leader", FlowData.create()
                .set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.REJECT.getCode()));
        assertEquals(ProcessInstanceStateEnum.REJECT.getCode(),
                repo.findInstanceById(child.getInstanceId()).getState());
        assertEquals(ProcessInstanceStateEnum.REJECT.getCode(),
                repo.findInstanceById(parentInstance.getInstanceId()).getState());
    }

    @Test
    public void childCannotFinishWhileAnotherMajorTaskIsActive() {
        String childJson = "{\"name\":\"childGuard257\",\"nodes\":["
                + node("start", "snaker:start", "") + ","
                + node("apply", "snaker:task", "\"assignee\":\"applicant\"") + ","
                + node("fork", "snaker:fork", "") + ","
                + node("taskA", "snaker:task", "\"assignee\":\"leader\"") + ","
                + node("taskB", "snaker:task", "\"assignee\":\"manager\"") + ","
                + node("end", "snaker:end", "") + "],\"edges\":["
                + edge("e1", "start", "apply") + "," + edge("e2", "apply", "fork") + ","
                + edge("e3", "fork", "taskA") + "," + edge("e4", "fork", "taskB") + ","
                + edge("e5", "taskA", "end") + "," + edge("e6", "taskB", "end") + "]}";
        addDefine("childGuard257", 1, childJson);
        ProcessInstance.ProcessDefine parent = addDefine("parentGuard257", 1,
                flowWithProps("parentGuard257", "start", "apply", "subGuard257", "end",
                        "\"processName\":\"childGuard257\",\"version\":1"));
        ProcessInstance parentInstance = engine.startProcessInstanceById(parent.getId(), "applicant", FlowData.create());
        StartAndExecuteSupport.applyInitialTasks(engine, parentInstance.getInstanceId(), "applicant", FlowData.create());
        ProcessInstance child = onlyChild(parentInstance.getInstanceId());
        ProcessTask taskA = repo.findDoingTasks(child.getInstanceId(), new String[]{"taskA"}).get(0);
        try {
            engine.executeProcessTask(taskA.getTaskId(), "leader", FlowData.create()
                    .set(FlowConst.SUBMIT_TYPE, ProcessSubmitTypeEnum.AGREE.getCode()));
            fail("存在未完成主办任务时不允许结束子流程");
        } catch (JeeflowException e) {
            assertEquals("存在未完成的主办任务,请确认.", e.getMessage());
        }
        assertEquals(ProcessInstanceStateEnum.DOING.getCode(),
                repo.findInstanceById(child.getInstanceId()).getState());
        assertEquals(1, repo.findDoingTasks(child.getInstanceId(), new String[]{"taskB"}).size());
    }

    @Test
    public void rollbackCannotCrossSubProcessAndCountersignParentIsRejected() {
        ProcessModel model = ModelParser.parse(json(flow("guard257", "start", "apply", "subGuard257", "after257", "end")));
        assertFalse(NodeModel.canRejected(model.getNode("after257"), model.getNode("apply")));

        ProcessModel countersign = ModelParser.parse(json(
                "{\"name\":\"cs257\",\"nodes\":["
                        + node("start", "snaker:start", "") + ","
                        + node("apply", "snaker:task", "\"assignee\":\"applicant\"") + ","
                        + node("cs", "snaker:task", "\"assignee\":\"leader\",\"performType\":1") + ","
                        + node("after", "snaker:task", "\"assignee\":\"manager\"") + ","
                        + node("end", "snaker:end", "")
                        + "],\"edges\":[" + edge("e1", "start", "apply") + "," + edge("e2", "apply", "cs")
                        + "," + edge("e3", "cs", "after") + "," + edge("e4", "after", "end") + "]}"));
        assertFalse(NodeModel.canRejected(countersign.getNode("after"), countersign.getNode("cs")));
        assertTrue(NodeModel.canRejected(countersign.getNode("after"), countersign.getNode("apply")));
    }

    private ProcessInstance onlyChild(Long parentId) {
        List<ProcessInstance> children = repo.pageInstances(new com.mldong.jeeflow.spi.PageQuery(1, 100)).getRows().stream()
                .map(row -> repo.findInstanceById(row.getId()))
                .filter(instance -> parentId.equals(instance.getParentId()))
                .collect(java.util.stream.Collectors.toList());
        assertEquals(1, children.size());
        return children.get(0);
    }

    private ProcessTask onlyDoing(Long instanceId) {
        List<ProcessTask> doing = repo.findDoingTasks(instanceId, null);
        assertEquals(1, doing.size());
        return doing.get(0);
    }

    private int countTaskStart(Long instanceId, String taskName) {
        return (int) events.stream()
                .filter(e -> e.getEventType() == ProcessEventTypeEnum.PROCESS_TASK_START)
                .filter(e -> instanceId.equals(e.getData().get("instanceId")))
                .filter(e -> {
                    ProcessTask task = repo.findTaskById(e.getSourceId());
                    return task != null && taskName.equals(task.getTaskName());
                }).count();
    }

    private int countEvent(ProcessEventTypeEnum type, Long instanceId) {
        return (int) events.stream().filter(e -> e.getEventType() == type)
                .filter(e -> instanceId.equals(e.getSourceId())).count();
    }

    private ProcessInstance.ProcessDefine addDefine(String name, int version, String content) {
        ProcessInstance.ProcessDefine define = new ProcessInstance.ProcessDefine();
        define.setName(name);
        define.setDisplayName(name);
        define.setType("approval");
        define.setState(1);
        define.setVersion(version);
        define.setContent(content.getBytes(StandardCharsets.UTF_8));
        repo.addDefine(define);
        return define;
    }

    private static String flow(String name, String... nodeIds) {
        StringBuilder nodes = new StringBuilder();
        StringBuilder edges = new StringBuilder();
        for (int i = 0; i < nodeIds.length; i++) {
            if (i > 0) nodes.append(',');
            String id = nodeIds[i];
            String props = "";
            if ("apply".equals(id)) props = "\"assignee\":\"applicant\",\"taskType\":0,\"performType\":0";
            else if (id.startsWith("sub")) props = "\"processName\":\"" + name.replace("parent", "child") + "\",\"version\":1,\"form\":\"__wrong__\"";
            else if (!"start".equals(id) && !"end".equals(id)) {
                String actor = id.contains("child") || id.contains("first") ? "leader" : "manager";
                props = "\"assignee\":\"" + actor + "\",\"taskType\":0,\"performType\":0";
            }
            String type;
            if (id.startsWith("sub")) type = "snaker:subProcess";
            else if ("start".equals(id) || "end".equals(id)) type = "snaker:" + id;
            else type = "snaker:task";
            nodes.append(node(id, type, props));
            if (i > 0) {
                if (i > 1) edges.append(',');
                edges.append(edge("e" + i, nodeIds[i - 1], id));
            }
        }
        return "{\"name\":\"" + name + "\",\"displayName\":\"" + name + "\",\"type\":\"approval\",\"nodes\":["
                + nodes + "],\"edges\":[" + edges + "]}";
    }

    private static String flowWithProps(String name, String start, String apply, String sub, String end, String props) {
        return "{\"name\":\"" + name + "\",\"displayName\":\"" + name + "\",\"type\":\"approval\",\"nodes\":["
                + node(start, "snaker:start", "") + ","
                + node(apply, "snaker:task", "\"assignee\":\"applicant\",\"taskType\":0,\"performType\":0") + ","
                + node(sub, "snaker:subProcess", props) + ","
                + node(end, "snaker:end", "") + "],\"edges\":["
                + edge("e1", start, apply) + "," + edge("e2", apply, sub) + "," + edge("e3", sub, end) + "]}";
    }

    private static String node(String id, String type, String props) {
        return "{\"id\":\"" + id + "\",\"type\":\"" + type + "\",\"x\":0,\"y\":0,\"properties\":{" + props
                + "},\"text\":{\"value\":\"" + id + "\"}}";
    }

    private static String edge(String id, String from, String to) {
        return "{\"id\":\"" + id + "\",\"sourceNodeId\":\"" + from + "\",\"targetNodeId\":\"" + to + "\",\"properties\":{}}";
    }

    private static byte[] json(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }
}
