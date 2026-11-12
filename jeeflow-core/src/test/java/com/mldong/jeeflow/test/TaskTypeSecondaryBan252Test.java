package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.JeeflowException;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
import com.mldong.jeeflow.enums.ProcessTaskTypeEnum;
import com.mldong.jeeflow.model.ProcessModel;
import com.mldong.jeeflow.model.TaskModel;
import com.mldong.jeeflow.parser.ModelParser;
import com.mldong.jeeflow.spi.IUserProvider;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 节点级 taskType=1（协办）解析期禁配（issues/252，规范 02 §4.4）——java 参考实现腿。
 *
 * <p>正向：codeOf 能折成 SECONDARY 的全部输入形（1／"1"／"协办"／"SECONDARY"，
 * 枚举名/中文名大小写不敏感）解析期报错、逐字文案、零写入；回归：0／"Major"／缺省／
 * 未知值 "Aidant" 仍解析成功且回落主办 MAJOR（issues/245 口径不变，不扩成 fail-fast）；
 * custom 记录节点不读 taskType、历史行形状不变（java createHistoryTask 传 null）。</p>
 *
 * <p>夹具内联 JSON，不碰共享 flows 目录（同 DynamicCountersignTest 纪律）。</p>
 */
public class TaskTypeSecondaryBan252Test {

    private static final String MSG = "节点[review]不允许配置协办任务类型（taskType=1）";
    private static final int APPLY = ProcessSubmitTypeEnum.APPLY.getCode();

    private JeeflowEngine engine;
    private MemoryProcessRepository repo;
    private TestJsonProvider json;
    private static int seq;

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

    // ═══ 夹具 ═══

    private static String node(String id, String props, String text) {
        return "{\"id\":\"" + id + "\",\"type\":\"snaker:task\",\"x\":0,\"y\":0,"
                + "\"properties\":{" + props + "},\"text\":{\"value\":\"" + text + "\"}}";
    }

    private static String edge(String id, String from, String to) {
        return "{\"id\":\"" + id + "\",\"type\":\"snaker:transition\",\"sourceNodeId\":\"" + from
                + "\",\"targetNodeId\":\"" + to + "\",\"properties\":{}}";
    }

    /** start → apply → review → final → end；reviewTaskTypeRaw＝review 节点 taskType 的原始 JSON 片段（null＝缺省键）。 */
    private String chain(String reviewTaskTypeRaw) {
        String reviewProps = "\"form\":\"form-r\",\"assignee\":\"userA\",\"performType\":0"
                + (reviewTaskTypeRaw != null ? ",\"taskType\":" + reviewTaskTypeRaw : "");
        return "{\"name\":\"tt252-" + (++seq) + "\",\"displayName\":\"协办禁配夹具\",\"type\":\"approval\","
                + "\"nodes\":["
                + "{\"id\":\"start\",\"type\":\"snaker:start\",\"x\":0,\"y\":0,\"properties\":{},\"text\":{\"value\":\"开始\"}},"
                + node("apply", "\"form\":\"apply-form\",\"taskType\":0,\"performType\":0,\"assignee\":\"applicant\"", "发起申请") + ","
                + node("review", reviewProps, "审批") + ","
                + node("final", "\"form\":\"form-f\",\"assignee\":\"userF\",\"taskType\":0,\"performType\":0", "终审") + ","
                + "{\"id\":\"end\",\"type\":\"snaker:end\",\"x\":0,\"y\":0,\"properties\":{},\"text\":{\"value\":\"结束\"}}"
                + "],\"edges\":["
                + edge("e1", "start", "apply") + ","
                + edge("e2", "apply", "review") + ","
                + edge("e3", "review", "final") + ","
                + edge("e4", "final", "end")
                + "]}";
    }

    /** start → apply → c1(custom，带 taskType:2 属性) → end：记录类节点不读 taskType，历史行形状不变。 */
    private String customChain() {
        return "{\"name\":\"tt252-custom-" + (++seq) + "\",\"displayName\":\"custom 回归夹具\",\"type\":\"approval\","
                + "\"nodes\":["
                + "{\"id\":\"start\",\"type\":\"snaker:start\",\"x\":0,\"y\":0,\"properties\":{},\"text\":{\"value\":\"开始\"}},"
                + node("apply", "\"form\":\"apply-form\",\"taskType\":0,\"performType\":0,\"assignee\":\"applicant\"", "发起申请") + ","
                + "{\"id\":\"c1\",\"type\":\"snaker:custom\",\"x\":0,\"y\":0,\"properties\":{\"taskType\":2},\"text\":{\"value\":\"记录\"}},"
                + "{\"id\":\"end\",\"type\":\"snaker:end\",\"x\":0,\"y\":0,\"properties\":{},\"text\":{\"value\":\"结束\"}}"
                + "],\"edges\":["
                + edge("e1", "start", "apply") + ","
                + edge("e2", "apply", "c1") + ","
                + edge("e3", "c1", "end")
                + "]}";
    }

    private ProcessInstance.ProcessDefine register(String flowJson) {
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName("tt252-def-" + (++seq));
        def.setDisplayName("协办禁配夹具");
        def.setType("approval");
        def.setState(1);
        def.setVersion(1);
        def.setContent(flowJson.getBytes(StandardCharsets.UTF_8));
        repo.addDefine(def);
        return def;
    }

    // ═══ 正向 ═══

    /** 格 1：折成协办的全部输入形 ⇒ 解析期报错，逐字文案（解析层直断，未经引擎包裹）。 */
    @Test
    public void test01_secondaryFormsRejectedAtParse() {
        String[] forms = {"1", "\"1\"", "\"协办\"", "\"SECONDARY\"", "\"secondary\""};
        for (String raw : forms) {
            try {
                ModelParser.parse(chain(raw).getBytes(StandardCharsets.UTF_8));
                fail("应拦：taskType=" + raw);
            } catch (JeeflowException e) {
                assertEquals("逐字文案（taskType=" + raw + "）", MSG, e.getMessage());
            }
        }
    }

    /** 格 2：发起腿报错且零写入——实例不得进仓（解析先于聚合根创建与 saveInstance）。 */
    @Test
    public void test02_startFailsWithZeroWrites() {
        ProcessInstance.ProcessDefine def = register(chain("1"));
        try {
            engine.startProcessInstanceById(def.getId(), "applicant", FlowData.create());
            fail("应拦：节点级协办");
        } catch (Exception e) {
            assertTrue("报错应含逐字文案，实得：" + e.getMessage(), e.getMessage().contains(MSG));
        }
        assertFalse("解析报错必须零写入", repo.hasInstancesByDefineId(def.getId()));
    }

    // ═══ 回归 ═══

    /** 格 3：0／"Major"／缺省／未知值 "Aidant" 仍解析成功且回落主办（issues/245 口径不变）。 */
    @Test
    public void test03_majorAndUnknownFormsStillParse() {
        String[] forms = {"0", "\"0\"", "\"Major\"", "\"MAJOR\"", "\"主办\"", "\"Aidant\"", null};
        for (String raw : forms) {
            ProcessModel model = ModelParser.parse(chain(raw).getBytes(StandardCharsets.UTF_8));
            TaskModel review = (TaskModel) model.getNode("review");
            assertEquals("taskType=" + raw + " 应回落主办", ProcessTaskTypeEnum.MAJOR, review.getTaskType());
        }
    }

    /** 格 4：未知值与缺省档发起照常推进，待办行 taskType 落主办。 */
    @Test
    public void test04_unknownAndDefaultFormsStartAndFallback() {
        String[] forms = {"\"Aidant\"", null};
        for (String raw : forms) {
            ProcessInstance.ProcessDefine def = register(chain(raw));
            ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "applicant", FlowData.create());
            for (ProcessTask t : repo.findDoingTasks(inst.getInstanceId(), null)) {
                engine.executeProcessTask(t.getTaskId(), "applicant",
                        FlowData.create().set(FlowConst.SUBMIT_TYPE, APPLY));
            }
            List<ProcessTask> doing = repo.findDoingTasks(inst.getInstanceId(), null);
            assertEquals("应停在 review", 1, doing.size());
            assertEquals("review", doing.get(0).getTaskName());
            assertEquals("待办行回落主办", ProcessTaskTypeEnum.MAJOR, doing.get(0).getTaskType());
        }
    }

    /** 格 5：custom 记录节点历史行不变——taskType:2 属性不触发禁配，DONE 行照落（java 形状 taskType=null）。 */
    @Test
    public void test05_customHistoryRowUnchanged() {
        String flow = customChain();
        // 解析层：custom 节点带 taskType 属性也不拦（禁配只落 TaskParser）
        ModelParser.parse(flow.getBytes(StandardCharsets.UTF_8));
        ProcessInstance.ProcessDefine def = register(flow);
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "applicant", FlowData.create());
        for (ProcessTask t : repo.findDoingTasks(inst.getInstanceId(), null)) {
            engine.executeProcessTask(t.getTaskId(), "applicant",
                    FlowData.create().set(FlowConst.SUBMIT_TYPE, APPLY));
        }
        List<ProcessTask> rows = repo.findHistoryTasks(inst.getInstanceId());
        ProcessTask history = null;
        for (ProcessTask t : rows) {
            if ("c1".equals(t.getTaskName())) {
                history = t;
            }
        }
        assertTrue("custom 记录行必须照落", history != null && history.isFinished());
        assertNull("java 历史行 taskType 形状不变（createHistoryTask 传 null）", history.getTaskType());
        assertTrue("令牌应续流到 end", repo.findDoingTasks(inst.getInstanceId(), null).isEmpty());
    }
}