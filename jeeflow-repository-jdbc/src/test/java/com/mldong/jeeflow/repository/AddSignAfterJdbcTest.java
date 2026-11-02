package com.mldong.jeeflow.repository;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.core.AddSignSupport;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessInstanceStateEnum;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * 「后加签（可重复）」在 JDBC 仓储上的落库往返与引擎重建。
 *
 * <p>内存仓储用例证明语义；本类额外证明：轮次号 {@code addSignRound_{节点}} 与追加式账本
 * {@code tf_addSignHistory} 经 {@code wf_process_task.variable} 的 SQL 列往返后仍可消费，
 * 换一个新引擎实例（等价于服务重启）后加签链仍能续办——这是规范 04 那条
 * 「消费资格由服务端状态确定，不靠内存布尔值，服务重启后行为不变」的同一判据面。</p>
 */
public class AddSignAfterJdbcTest {

    private static final int AGREE = ProcessSubmitTypeEnum.AGREE.getCode();
    private static final int APPLY = ProcessSubmitTypeEnum.APPLY.getCode();

    private JdbcDataSource ds;
    private JdbcProcessRepository repo;
    private JeeflowEngine engine;
    private ObjectMapper mapper;
    private Configuration config;

    @Before
    public void setUp() throws Exception {
        ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:jeeflow_addsign;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        String ddl = new String(Files.readAllBytes(
                Paths.get("src/test/resources/schema-h2.sql")), StandardCharsets.UTF_8);
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            for (String sql : ddl.split(";")) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
        }

        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        IJsonProvider jsonProvider = new IJsonProvider() {
            @Override
            public String toJson(Object obj) {
                try {
                    return mapper.writeValueAsString(obj);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public <T> T fromJson(String json, Class<T> type) {
                try {
                    return mapper.readValue(json, type);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T fromJson(String json, TypeReference<T> typeRef) {
                try {
                    return (T) mapper.readValue(json, mapper.constructType(typeRef.getType()));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public boolean isJson(String str) {
                return str != null && (str.trim().startsWith("{") || str.trim().startsWith("["));
            }
        };

        repo = new JdbcProcessRepository(ds);
        config = new Configuration();
        ServiceContext.put("repository", repo);
        ServiceContext.put("json", jsonProvider);
        ServiceContext.put("expr", new IExpressionEvaluator() {
            @Override
            public Object eval(String expression, Map<String, Object> variables) {
                return Boolean.TRUE;
            }
        });
        ServiceContext.put("user", new IUserProvider() {
            @Override
            public UserInfo getUser(String userId) {
                UserInfo u = UserInfo.of(userId);
                u.setDeptId("D01");
                u.setDeptName("测试部门");
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

    private static String node(String id, String type, String props, String text) {
        return "{\"id\":\"" + id + "\",\"type\":\"" + type + "\",\"x\":0,\"y\":0,"
                + "\"properties\":{" + props + "},\"text\":{\"value\":\"" + text + "\"}}";
    }

    private static String edge(String id, String from, String to) {
        return "{\"id\":\"" + id + "\",\"type\":\"snaker:transition\",\"sourceNodeId\":\"" + from
                + "\",\"targetNodeId\":\"" + to + "\",\"properties\":{}}";
    }

    private ProcessInstance.ProcessDefine registerFlow() throws Exception {
        String jsonStr = "{\"name\":\"addsign-jdbc\",\"displayName\":\"后加签JDBC\",\"type\":\"approval\","
                + "\"nodes\":["
                + node("start", "snaker:start", "", "开始") + ","
                + node("apply", "snaker:task", "\"form\":\"apply-form\",\"taskType\":0,\"performType\":0,\"assignee\":\"applicant\"", "发起申请") + ","
                + node("review", "snaker:task", "\"form\":\"form-r\",\"assignee\":\"userA\",\"taskType\":0,\"performType\":0,\"allowAddSign\":true", "审批") + ","
                + node("final", "snaker:task", "\"form\":\"form-f\",\"assignee\":\"userF\",\"taskType\":0,\"performType\":0", "终审") + ","
                + node("end", "snaker:end", "", "结束")
                + "],\"edges\":["
                + edge("e1", "start", "apply") + "," + edge("e2", "apply", "review") + ","
                + edge("e3", "review", "final") + "," + edge("e4", "final", "end") + "]}";
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

    private Long startToReview() throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow();
        ProcessInstance inst = engine.startProcessInstanceById(def.getId(), "applicant", FlowData.create());
        for (ProcessTask t : repo.findDoingTasks(inst.getInstanceId(), null)) {
            engine.executeProcessTask(t.getTaskId(), "applicant",
                    FlowData.create().set(FlowConst.SUBMIT_TYPE, APPLY));
        }
        return inst.getInstanceId();
    }

    private ProcessTask doing(Long iid, String name) {
        for (ProcessTask t : repo.findDoingTasks(iid, null)) {
            if (name.equals(t.getTaskName())) {
                return t;
            }
        }
        return null;
    }

    private long rows(Long iid, String name) {
        return repo.findHistoryTasks(iid).stream().filter(t -> name.equals(t.getTaskName())).count();
    }

    private int doingCount(Long iid, String name) {
        int n = 0;
        for (ProcessTask t : repo.findDoingTasks(iid, null)) {
            if (name.equals(t.getTaskName())) {
                n++;
            }
        }
        return n;
    }

    private static String firstActor(ProcessTask t) {
        List<String> ids = t.getActorIds();
        return ids == null || ids.isEmpty() ? null : ids.get(0);
    }

    private FlowData agree(String operators) {
        return FlowData.create().set(FlowConst.SUBMIT_TYPE, AGREE)
                .set(FlowConst.ADD_SIGN_OPERATORS, operators);
    }

    /** 轮次号与账本经 SQL 列往返仍在；换新引擎实例（等价重启）后续办与放行都正确。 */
    @Test
    public void test01_roundAndHistorySurviveSqlRoundTrip() throws Exception {
        Long iid = startToReview();
        ProcessTask a = doing(iid, "review");
        assertNotNull(a);

        engine.executeProcessTask(a.getTaskId(), "userA", agree("userB"));
        ProcessTask b = doing(iid, "review");
        assertNotNull(b);

        // 从库里重读，确认保留键经 variable 列往返仍在
        ProcessTask bFromDb = repo.findTaskById(b.getTaskId());
        assertEquals("轮次号应经 SQL 往返可读", "1", String.valueOf(
                bFromDb.getVariables().get(FlowConst.ADD_SIGN_ROUND + "_review")));
        ProcessTask aFromDb = repo.findTaskById(a.getTaskId());
        List<Map<String, Object>> histA = AddSignSupport.historyOf(aFromDb);
        assertEquals("已完成行的账本应读回一条", 1, histA.size());
        assertEquals("userA", histA.get(0).get("fromActor"));
        assertTrue(String.valueOf(histA.get(0).get("toActors")).contains("userB"));

        // 重建引擎（等价服务重启）：仅凭仓储状态继续这条链
        engine = new JeeflowEngineImpl();
        engine.configure(config);

        engine.executeProcessTask(b.getTaskId(), firstActor(bFromDb), agree("userC"));
        ProcessTask c = repo.findTaskById(doing(iid, "review").getTaskId());
        assertEquals("第二轮轮次应为 2", "2", String.valueOf(
                c.getVariables().get(FlowConst.ADD_SIGN_ROUND + "_review")));
        assertEquals("链上血缘应串到上一轮", b.getTaskId(), c.getParentTaskId());

        engine = new JeeflowEngineImpl();
        engine.configure(config);
        engine.executeProcessTask(c.getTaskId(), firstActor(c),
                FlowData.create().set(FlowConst.SUBMIT_TYPE, AGREE));
        assertNotNull("不点人后应流出本节点", doing(iid, "final"));
        assertEquals("review 共三行", 3, rows(iid, "review"));

        engine.executeProcessTask(doing(iid, "final").getTaskId(), "userF",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, AGREE));
        assertEquals(ProcessInstanceStateEnum.FINISHED.getCode(), repo.findInstanceById(iid).getState());
    }

    /** JDBC 仓上同圈两个加签任务并发办结：后续节点只生成一条待办。 */
    @Test
    public void test02_concurrentSiblingsReleaseOnce() throws Exception {
        Long iid = startToReview();
        ProcessTask a = doing(iid, "review");
        engine.executeProcessTask(a.getTaskId(), "userA",
                FlowData.create().set(FlowConst.SUBMIT_TYPE, AGREE)
                        .set(FlowConst.ADD_SIGN_OPERATORS, Arrays.asList("userB", "userC")));
        assertEquals(2, repo.findDoingTasks(iid, null).size());

        final List<Long> ids = new java.util.ArrayList<>();
        final List<String> actors = new java.util.ArrayList<>();
        for (ProcessTask t : repo.findDoingTasks(iid, null)) {
            ids.add(t.getTaskId());
            actors.add(firstActor(t));
        }
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(2);
        final AtomicInteger errors = new AtomicInteger();
        for (int i = 0; i < ids.size(); i++) {
            final Long taskId = ids.get(i);
            final String actor = actors.get(i);
            Thread th = new Thread(() -> {
                try {
                    start.await();
                    engine.executeProcessTask(taskId, actor,
                            FlowData.create().set(FlowConst.SUBMIT_TYPE, AGREE));
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
            th.start();
        }
        start.countDown();
        done.await();

        assertEquals("并发办结不应报错", 0, errors.get());
        assertEquals("后续节点只应生成一行", 1, rows(iid, "final"));
        assertEquals("本节点不得留未完成加签待办", 0, doingCount(iid, "review"));
        assertEquals("全实例只应剩终审那一条待办", 1, repo.findDoingTasks(iid, null).size());
    }
}
