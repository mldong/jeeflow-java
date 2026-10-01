package com.mldong.jeeflow.test;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.JeeflowException;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.JeeflowEngineImpl;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.facade.JeeflowFacade;
import com.mldong.jeeflow.spi.IOrgUserProvider;
import com.mldong.jeeflow.spi.IProcessRepository;
import com.mldong.jeeflow.spi.IUserProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * issues/137-G：门面顶层 catch 的出口形状（走真实调用路径，不是只测纯函数）。
 *
 * <p>判据两侧都要有牙：<b>负向</b>＝内部异常（JVM 抛的、只包了 cause 的裸包装）原文不得进 msg，
 * 只能进日志；<b>正向／回归</b>＝引擎写的中文契约文案必须逐字留在 msg——这条不是"顺手保旧行为"，
 * 而是其余七栈、十三个集成壳与前端 toast 都按原文对齐，一旦收窄就是静默改契约面。</p>
 */
public class FacadeInternalErrorNoLeakTest {

    /** 门面顶层用的就是这个名字的 logger（cause 分离的"日志"那一半在这儿验） */
    private static final Logger FACADE_LOG = Logger.getLogger(JeeflowFacade.class.getName());

    private static final List<LogRecord> CAPTURED = new ArrayList<>();

    private static final Handler CAPTURE_HANDLER = new Handler() {
        @Override public void publish(LogRecord record) { CAPTURED.add(record); }
        @Override public void flush() { }
        @Override public void close() { }
    };

    @Before
    public void startCapture() {
        CAPTURED.clear();
        FACADE_LOG.addHandler(CAPTURE_HANDLER);
        FACADE_LOG.setLevel(Level.ALL);
    }

    @After
    public void stopCapture() {
        FACADE_LOG.removeHandler(CAPTURE_HANDLER);
    }

    // ── 夹具 ──

    private JeeflowEngine engine;
    private MemoryProcessRepository repo;

    /**  methodName → 该次调用要抛的异常（没有则原样委托给内存仓） */
    private final Map<String, Throwable> toThrow = new LinkedHashMap<>();

    @Before
    public void setUp() {
        Configuration config = new Configuration();
        repo = new MemoryProcessRepository();
        ServiceContext.put("repository", repo);
        ServiceContext.put("json", new TestJsonProvider());
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
    }

    private JeeflowFacade facadeWithThrowingRepo() {
        IProcessRepository proxy = (IProcessRepository) Proxy.newProxyInstance(
                IProcessRepository.class.getClassLoader(),
                new Class<?>[]{IProcessRepository.class},
                (p, method, methodArgs) -> {
                    Throwable t = toThrow.get(method.getName());
                    if (t != null) {
                        throw t;
                    }
                    return method.invoke(repo, methodArgs);
                });
        return new JeeflowFacade(engine, proxy, new MemoryProcessExtRepository());
    }

    private Map<String, Object> call(JeeflowFacade facade, String action) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("operator", "user1");
        args.put("pageNum", 1);
        args.put("pageSize", 10);
        @SuppressWarnings("unchecked")
        Map<String, Object> r = (Map<String, Object>) facade.flow(action, args);
        return r;
    }

    // ── 负向：内部异常原文不得进 msg ──

    @Test
    public void npeFromEnginePathKeepsInternalsOutOfMsg() {
        NullPointerException npe = new NullPointerException(
                "Cannot invoke \"String.length()\" because the return value of \"getRaw()\" is null");
        toThrow.put("pageInstances", npe);

        Map<String, Object> r = call(facadeWithThrowingRepo(), "processInstance/page");

        assertEquals(99999999, r.get("code"));
        assertEquals("流程处理失败", r.get("msg"));
        String msg = String.valueOf(r.get("msg"));
        for (String marker : Arrays.asList("Cannot invoke", "getRaw", "java.lang", "NullPointerException")) {
            assertFalse("内部文案不得进 msg，泄漏了：" + marker, msg.contains(marker));
        }
        // cause 分离的另一半：原文连同栈进了日志
        LogRecord rec = severeRecord();
        assertSame("日志里要拿到原异常对象", npe, rec.getThrown());
        assertTrue("日志要指出是哪个 action：" + rec.getMessage(), rec.getMessage().contains("processInstance/page"));
    }

    @Test
    public void bareWrapperKeepsCauseTextOutOfMsg() {
        // JeeflowEngineImpl.runInTx 那两处的形状：new RuntimeException(e) ⇒ message 就是 cause 原文
        RuntimeException bare = new RuntimeException(new IllegalStateException("内部驱动细节 12345"));
        toThrow.put("pageInstances", bare);

        Map<String, Object> r = call(facadeWithThrowingRepo(), "processInstance/page");

        assertEquals(99999999, r.get("code"));
        assertEquals("流程处理失败", r.get("msg"));
        assertFalse("裸包装的 cause 原文不得进 msg", String.valueOf(r.get("msg")).contains("12345"));
        assertTrue("原文要进日志", severeRecord().getThrown().getCause().getMessage().contains("12345"));
    }

    @Test
    public void numberFormatExceptionTextKeepsInternalsOutOfMsg() {
        // 137 案实测的那条出口：msg=For input string: "x"
        toThrow.put("pageInstances", new NumberFormatException("For input string: \"x\""));

        Map<String, Object> r = call(facadeWithThrowingRepo(), "processInstance/page");

        assertEquals("流程处理失败", r.get("msg"));
        assertFalse(String.valueOf(r.get("msg")).contains("For input string"));
    }

    // ── 正向／回归：引擎契约文案逐字透出，没被收窄 ──

    @Test
    public void contractExceptionMessageStillPassesThroughVerbatim() {
        JeeflowException biz = new JeeflowException(99990410, "刷新令牌已失效或不存在");
        toThrow.put("pageInstances", biz);

        Map<String, Object> r = call(facadeWithThrowingRepo(), "processInstance/page");

        assertEquals(99999999, r.get("code"));
        assertEquals("契约文案必须逐字留在 msg", "刷新令牌已失效或不存在", r.get("msg"));
    }

    @Test
    public void engineIllegalStateOutsideContractTypeStillPassesThrough() {
        // 这条是"不能按类型收窄成只透 JeeflowException"的活证：
        // ext() 抛的是裸 IllegalStateException("未配置 IProcessExtRepository（扩展仓储）")，
        // 抛出点在引擎包内 ⇒ 文案照旧逐字透出，改成固定文案就会打断集成方排障与其余七栈的逐字对齐。
        JeeflowFacade noExt = new JeeflowFacade(engine, repo, null);

        Map<String, Object> r = call(noExt, "processDesign/page");

        assertEquals(99999999, r.get("code"));
        assertEquals("未配置 IProcessExtRepository（扩展仓储）", r.get("msg"));
        assertTrue("引擎自己写的文案不该被记成内部异常日志", CAPTURED.isEmpty());
    }

    private static LogRecord severeRecord() {
        for (LogRecord rec : CAPTURED) {
            if (rec.getLevel() == Level.SEVERE) {
                return rec;
            }
        }
        throw new AssertionError("期望门面记一条 SEVERE 日志（cause 进日志那一半没兑现）。实际条数="
                + CAPTURED.size());
    }
}
