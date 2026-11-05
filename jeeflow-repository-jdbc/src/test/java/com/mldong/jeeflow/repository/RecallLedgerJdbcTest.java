package com.mldong.jeeflow.repository;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.mldong.jeeflow.json.IJsonProvider;
import com.mldong.jeeflow.json.TypeReference;
import com.mldong.jeeflow.spi.IExpressionEvaluator;
import com.mldong.jeeflow.spi.IUserProvider;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * 「已办撤回账本落库往返」在 JDBC 仓储上的真读回（issues/249）。
 *
 * <p>内存仓储里账本写在同一份对象引用上，测试怎么断都绿；本类在 H2（MODE=MySQL）上走
 * 真实 SQL 往返：<code>recallTask</code> 后从库里重新 SELECT 被撤回那一行，断言
 * <code>tf_recallHistory</code> 是长度 +1 的数组、条目含 {operator, time, recalledTaskId} 三键——
 * 这一格在内存腿能过、在真库腿必须也过（updateInstance 级联用聚合内旧副本盖写会把账本抹掉）。</p>
 */
public class RecallLedgerJdbcTest {

    private JdbcDataSource ds;
    private JdbcProcessRepository repo;
    private JeeflowEngine engine;
    private ObjectMapper mapper;

    @Before
    public void setUp() throws Exception {
        ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:jeeflow_recall_ledger;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        String ddl = new String(Files.readAllBytes(
                Paths.get("src/test/resources/schema-h2.sql")), StandardCharsets.UTF_8);
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            for (String sql : ddl.split(";")) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) stmt.execute(trimmed);
            }
        }

        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        IJsonProvider jsonProvider = new IJsonProvider() {
            @Override public String toJson(Object obj) {
                try { return mapper.writeValueAsString(obj); } catch (Exception e) { throw new RuntimeException(e); }
            }
            @Override public <T> T fromJson(String json, Class<T> type) {
                try { return mapper.readValue(json, type); } catch (Exception e) { throw new RuntimeException(e); }
            }
            @Override @SuppressWarnings("unchecked")
            public <T> T fromJson(String json, TypeReference<T> typeRef) {
                try { return (T) mapper.readValue(json, mapper.constructType(typeRef.getType())); }
                catch (Exception e) { throw new RuntimeException(e); }
            }
            @Override public boolean isJson(String str) {
                return str != null && (str.trim().startsWith("{") || str.trim().startsWith("["));
            }
        };

        repo = new JdbcProcessRepository(ds);
        Configuration config = new Configuration();
        ServiceContext.put("repository", repo);
        ServiceContext.put("json", jsonProvider);
        ServiceContext.put("expr", new IExpressionEvaluator() {
            @Override public Object eval(String expression, Map<String, Object> variables) {
                return Boolean.TRUE;
            }
        });
        ServiceContext.put("user", new IUserProvider() {
            @Override public UserInfo getUser(String userId) {
                UserInfo u = UserInfo.of(userId);
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

    @After
    public void tearDown() throws Exception {
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
        }
    }

    @Test
    public void jdbcRecallPersistsLedgerOnRecalledRow() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlowJson(chain("\"allowRecall\":true"));
        Long iid = startToReview(def);
        ProcessTask review = doing(iid, "review");
        assertNotNull(review);

        engine.executeProcessTask(review.getTaskId(), "userA", submit(AGREE));
        assertNull("撤回前被撤行不应有账本",
                repo.findTaskById(review.getTaskId()).getVariables().get(FlowConst.RECALL_HISTORY));

        ProcessTask resurrected = engine.recallTask(review.getTaskId(), "userA");
        assertNotNull(resurrected);
        assertEquals("review", resurrected.getTaskName());

        // 真库读回（重新 SELECT，非内存引用）：账本必须还在——issues/249 的病灶正是
        // updateInstance 级联用聚合内旧副本把刚写的 variable 盖回去
        ProcessTask row = repo.findTaskById(review.getTaskId());
        Object histObj = row.getVariables().get(FlowConst.RECALL_HISTORY);
        assertNotNull("撤回后真库读回被撤行应有 tf_recallHistory", histObj);
        assertTrue(histObj instanceof List);
        List<?> ledger = (List<?>) histObj;
        assertEquals("账本应恰好 +1 条", 1, ledger.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> entry = (Map<String, Object>) ledger.get(0);
        assertEquals("userA", entry.get("operator"));
        assertNotNull("条目应有 time", entry.get("time"));
        assertEquals("recalledTaskId 应指向复活行",
                String.valueOf(resurrected.getTaskId()), String.valueOf(entry.get("recalledTaskId")));

        // 复活三件套 sanity：复活行 DOING 停在 review、原行仍 FINISHED、下游 final 已废弃
        ProcessTask doingAgain = doing(iid, "review");
        assertNotNull(doingAgain);
        assertEquals(String.valueOf(resurrected.getTaskId()), String.valueOf(doingAgain.getTaskId()));
        assertEquals(ProcessTaskStateEnum.FINISHED.getCode(),
                repo.findTaskById(review.getTaskId()).getTaskState());
    }

    // ═══ 夹具 ═══

    private static int seq;

    private static final int APPLY = ProcessSubmitTypeEnum.APPLY.getCode();
    private static final int AGREE = ProcessSubmitTypeEnum.AGREE.getCode();

    private FlowData submit(int type) {
        return FlowData.create().set(FlowConst.SUBMIT_TYPE, type);
    }

    private static String node(String id, String props, String text) {
        return "{\"id\":\"" + id + "\",\"type\":\"snaker:task\",\"x\":0,\"y\":0,"
                + "\"properties\":{" + props + "},\"text\":{\"value\":\"" + text + "\"}}";
    }

    private static String edge(String id, String from, String to) {
        return "{\"id\":\"" + id + "\",\"type\":\"snaker:transition\",\"sourceNodeId\":\"" + from
                + "\",\"targetNodeId\":\"" + to + "\",\"properties\":{}}";
    }

    private String chain(String reviewProps) {
        return "{\"name\":\"recallledger-" + (++seq) + "\",\"displayName\":\"撤回账本真库腿\",\"type\":\"approval\","
                + "\"nodes\":["
                + "{\"id\":\"start\",\"type\":\"snaker:start\",\"x\":0,\"y\":0,\"properties\":{},\"text\":{\"value\":\"开始\"}},"
                + node("apply", "\"form\":\"apply-form\",\"taskType\":0,\"performType\":0,\"assignee\":\"applicant\"", "发起申请") + ","
                + node("review", "\"form\":\"form-r\",\"taskType\":0,\"performType\":0,\"assignee\":\"userA\"," + reviewProps, "审批") + ","
                + node("final", "\"form\":\"form-f\",\"assignee\":\"userF\",\"taskType\":0,\"performType\":0", "终审") + ","
                + "{\"id\":\"end\",\"type\":\"snaker:end\",\"x\":0,\"y\":0,\"properties\":{},\"text\":{\"value\":\"结束\"}}"
                + "],\"edges\":["
                + edge("e1", "start", "apply") + ","
                + edge("e2", "apply", "review") + ","
                + edge("e3", "review", "final") + ","
                + edge("e4", "final", "end")
                + "]}";
    }

    private ProcessInstance.ProcessDefine registerFlowJson(String jsonStr) throws Exception {
        byte[] bytes = jsonStr.getBytes(StandardCharsets.UTF_8);
        com.mldong.jeeflow.model.logicflow.LfModel lfModel =
                mapper.readValue(jsonStr, com.mldong.jeeflow.model.logicflow.LfModel.class);
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName(lfModel.getName());
        def.setDisplayName(lfModel.getDisplayName());
        def.setType(lfModel.getType());
        def.setState(1);
        def.setVersion(1);
        def.setContent(bytes);
        repo.saveDefine(def);
        return def;
    }

    private Long startToReview(ProcessInstance.ProcessDefine def) {
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "applicant", FlowData.create());
        for (ProcessTask t : repo.findDoingTasks(inst.getInstanceId(), null)) {
            engine.executeProcessTask(t.getTaskId(), "applicant", submit(APPLY));
        }
        return inst.getInstanceId();
    }

    private ProcessTask doing(Long instanceId, String name) {
        for (ProcessTask t : repo.findDoingTasks(instanceId, null)) {
            if (name.equals(t.getTaskName())) return t;
        }
        return null;
    }
}
