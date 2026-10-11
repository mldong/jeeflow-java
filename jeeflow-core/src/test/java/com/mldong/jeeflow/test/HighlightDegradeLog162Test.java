package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.facade.JeeflowFacade;
import com.mldong.jeeflow.spi.IOrgUserProvider;
import com.mldong.jeeflow.spi.IUserProvider;
import com.mldong.jeeflow.spi.IUserProvider.UserInfo;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.*;

/**
 * issues/162 java 腿（队列 R5/R7 搭车）：highLight 块级 catch 保留降级面＋落可诊断 WARN。
 *
 * 判据两枚（形状照 §6.1，php 156／python 160／go·node·rust 已同形收口）：
 * ① 坏定义 ⇒ 对外仍 code=0、边集空、nodeProgress 空、活跃行不受影响，且日志真拿到
 *    instanceId=／defineId=／err= 三件（java.util.logging Handler 进程内捕获，不接受"注释自称"）；
 * ② 好定义 ⇒ 非空边集且**不得出现**该 WARN。
 */
public class HighlightDegradeLog162Test {

    private JeeflowFacade facade;
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
            @Override public List<String> findByRole(String roleCode) {
                if ("finance".equals(roleCode)) return Arrays.asList("finA", "finB");
                return null;
            }
        });

        JeeflowEngine engine = new JeeflowEngineImpl();
        engine.configure(config);
        facade = new JeeflowFacade(engine, repo, extRepo);
    }

    private static final class CollectHandler extends Handler {
        final List<LogRecord> records = new ArrayList<>();
        @Override public void publish(LogRecord r) { records.add(r); }
        @Override public void flush() { }
        @Override public void close() { }
    }

    private ProcessInstance.ProcessDefine registerFlow(String filename) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get("src/test/resources/flows/" + filename));
        ProcessInstance.ProcessDefine def = new ProcessInstance.ProcessDefine();
        def.setName(filename.replace(".json", ""));
        def.setDisplayName(filename);
        def.setType("approval");
        def.setState(1);
        def.setVersion(1);
        def.setContent(bytes);
        repo.addDefine(def);
        return def;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String action, Map<String, Object> a) {
        return (Map<String, Object>) facade.flow(action, a);
    }

    private void assertOk(Map<String, Object> r) {
        assertEquals("code 应为 0: " + r, Integer.valueOf(0), r.get("code"));
    }

    private Map<String, Object> args(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i].toString(), kv[i + 1]);
        return m;
    }

    private Long toLong(Object v) {
        return v == null ? null : Long.valueOf(v.toString());
    }

    private Long startOne(String flowFile) throws Exception {
        ProcessInstance.ProcessDefine def = registerFlow(flowFile);
        Map<String, Object> r = call("processInstance/startAndExecute",
                args("processDefineId", def.getId(), "operator", "zhangsan", "amount", "1000"));
        assertOk(r);
        return toLong(((Map<String, Object>) r.get("data")).get("processInstanceId"));
    }

    @Test
    public void testBadDefineDegradesWithWarnLog() throws Exception {
        Logger logger = Logger.getLogger(JeeflowFacade.class.getName());
        CollectHandler h = new CollectHandler();
        logger.addHandler(h);
        try {
            Long instanceId = startOne("01-simple.json");
            ProcessInstance.ProcessDefine def = repo.findDefineById(
                    repo.findInstanceById(instanceId).getDefineId());

            // 换成坏内容（直改仓储，绕开解析层）→ highLight 补全腿必走降级支
            def.setContent("{ not-json".getBytes(StandardCharsets.UTF_8));
            repo.updateDefine(def);
            h.records.clear();

            Map<String, Object> r = call("processInstance/highLight", args("id", instanceId));
            assertOk(r);
            Map<String, Object> data = (Map<String, Object>) r.get("data");
            assertEquals("坏定义下补全腿应留空: " + r, Collections.emptyList(), data.get("historyEdgeNames"));
            assertEquals("坏定义下 nodeProgress 应空: " + r, Collections.emptyMap(), data.get("nodeProgress"));
            List<String> active = (List<String>) data.get("activeNodeNames");
            assertTrue("活跃行仍在 task1: " + active, active.contains("task1"));

            List<String> warns = new ArrayList<>();
            for (LogRecord rec : h.records) warns.add(String.valueOf(rec.getMessage()));
            String joined = String.join("|", warns);
            assertTrue("日志须带 instanceId=" + instanceId + "（实得: " + joined + "）",
                    joined.contains("instanceId=" + instanceId));
            assertTrue("日志须带 defineId=" + def.getId() + "（实得: " + joined + "）",
                    joined.contains("defineId=" + def.getId()));
            assertTrue("日志须带 err=（实得: " + joined + "）", joined.contains("err="));
        } finally {
            logger.removeHandler(h);
        }
    }

    @Test
    public void testGoodDefineNoWarnLog() throws Exception {
        Logger logger = Logger.getLogger(JeeflowFacade.class.getName());
        CollectHandler h = new CollectHandler();
        logger.addHandler(h);
        try {
            Long instanceId = startOne("01-simple.json");
            h.records.clear();
            Map<String, Object> r = call("processInstance/highLight", args("id", instanceId));
            assertOk(r);
            Map<String, Object> data = (Map<String, Object>) r.get("data");
            List<String> edges = (List<String>) data.get("historyEdgeNames");
            assertFalse("好定义下补全腿应有边: " + r, edges.isEmpty());
            for (LogRecord rec : h.records) {
                assertFalse("好定义不得出现降级 WARN: " + rec.getMessage(),
                        String.valueOf(rec.getMessage()).contains("highLight 模型补全腿解码失败"));
            }
        } finally {
            logger.removeHandler(h);
        }
    }
}
