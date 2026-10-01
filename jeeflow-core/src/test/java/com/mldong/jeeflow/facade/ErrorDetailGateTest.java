package com.mldong.jeeflow.facade;

import com.mldong.jeeflow.JeeflowException;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * issues/137-G：门面出口 msg 的「内部信息不外泄」判别式（纯函数）单测。
 *
 * <p>两侧各自钉住，缺一侧都会出事：只钉"内部必须挡住"⇒ 会把引擎自己写的中文契约文案一起改掉
 * （其余七栈、十三个集成壳与前端 toast 都按那些原文逐字对齐）；只钉"引擎文案必须放行"⇒ 挡不住
 * JVM/反射/驱动的内部详情（137 案实测出口 {@code msg=For input string: "x"} 就是这么漏的）。</p>
 */
public class ErrorDetailGateTest {

    private static StackTraceElement[] frame(String cls) {
        return new StackTraceElement[]{new StackTraceElement(cls, "m", "F.java", 1)};
    }

    private static final StackTraceElement[] ENGINE = frame("com.mldong.jeeflow.parser.ModelParser");
    private static final StackTraceElement[] FOREIGN = frame("com.mldong.integration.MyJsonProvider");

    /** 引擎用裸 RuntimeException／ISE／IAE 携带中文契约文案（约十处），一律不得判外来 */
    @Test
    public void engineChineseContractTextPassesThrough() {
        assertFalse(JeeflowFacade.isForeignDetail(RuntimeException.class,
                "读取流程定义 JSON 失败", null, ENGINE));
        assertFalse(JeeflowFacade.isForeignDetail(IllegalStateException.class,
                "未配置 IProcessExtRepository（扩展仓储）", null, ENGINE));
        assertFalse(JeeflowFacade.isForeignDetail(IllegalArgumentException.class,
                "id 缺失或非法", null, ENGINE));
        assertFalse(JeeflowFacade.isForeignDetail(RuntimeException.class,
                "移除任务参与者失败", new java.sql.SQLException("db down"), ENGINE));
    }

    /** 契约异常族按类型就放行，与抛出点无关（集成壳的自定义异常子类也覆盖到） */
    @Test
    public void contractExceptionAlwaysPassesThrough() {
        assertFalse(JeeflowFacade.isForeignDetail(JeeflowException.class,
                "自定义契约文案", new IllegalStateException("内部原因"), FOREIGN));
        assertFalse(JeeflowFacade.isForeignDetail(
                new JeeflowException(99990410, "刷新令牌已失效").getClass(),
                "刷新令牌已失效", null, FOREIGN));
    }

    /** JVM/反射/IO/SQL 自己构造的族：即便抛出点就在引擎包里也判外来（NPE 正是在引擎里炸的） */
    @Test
    public void jvmInternalFamiliesAreForeign() {
        assertTrue("NPE 文案由 JVM 写", JeeflowFacade.isForeignDetail(NullPointerException.class,
                "Cannot invoke \"String.length()\" because the return value is null", null, ENGINE));
        assertTrue(JeeflowFacade.isForeignDetail(NumberFormatException.class,
                "For input string: \"x\"", null, ENGINE));
        assertTrue(JeeflowFacade.isForeignDetail(ClassCastException.class,
                "class java.lang.String cannot be cast to class java.util.Map", null, ENGINE));
        assertTrue(JeeflowFacade.isForeignDetail(ArrayIndexOutOfBoundsException.class,
                "Index 5 out of bounds for length 3", null, ENGINE));
        assertTrue(JeeflowFacade.isForeignDetail(ArithmeticException.class,
                "/ by zero", null, ENGINE));
        assertTrue(JeeflowFacade.isForeignDetail(IOException.class,
                "Broken pipe", null, ENGINE));
        assertTrue(JeeflowFacade.isForeignDetail(java.sql.SQLException.class,
                "Communications link failure", null, ENGINE));
        Throwable iteCause = new IllegalStateException("反射目标内部炸了");
        assertTrue(JeeflowFacade.isForeignDetail(InvocationTargetException.class,
                "target threw", iteCause, ENGINE));
        assertTrue(JeeflowFacade.isForeignDetail(UndeclaredThrowableException.class,
                "Undeclared exception: \"boom\"", new RuntimeException("boom"), ENGINE));
    }

    /** 裸包装 {@code new RuntimeException(e)}（JeeflowEngineImpl.runInTx 那两处）：msg 就是 cause 原文 */
    @Test
    public void bareWrapperIsForeign() {
        IllegalStateException inner = new IllegalStateException("内部驱动细节 12345");
        RuntimeException bare = new RuntimeException(inner);
        assertTrue(bare.getMessage().equals(String.valueOf(bare.getCause())));
        assertTrue("抛出点在引擎包内也要判外来",
                JeeflowFacade.isForeignDetail(bare.getClass(), bare.getMessage(), bare.getCause(), ENGINE));
    }

    /** 无文案时旧兜底会吐类名 ⇒ 判外来 */
    @Test
    public void nullMessageIsForeign() {
        assertTrue(JeeflowFacade.isForeignDetail(RuntimeException.class, null, null, ENGINE));
        assertTrue("契约异常也可能没文案：不能把类名给用户",
                JeeflowFacade.isForeignDetail(JeeflowException.class, null, null, ENGINE));
    }

    /** 抛出点不在引擎主包（JDK、集成方 provider）⇒ 外来 */
    @Test
    public void thrownOutsideEngineIsForeign() {
        assertTrue(JeeflowFacade.isForeignDetail(RuntimeException.class,
                "provider blew up", null, FOREIGN));
        assertTrue("空栈（JVM 优化掉栈帧）不得当成引擎文案放行",
                JeeflowFacade.isForeignDetail(RuntimeException.class, "whatever", null, new StackTraceElement[0]));
        assertTrue(JeeflowFacade.isForeignDetail(RuntimeException.class, "whatever", null, null));
    }

    /** 测试包抛的裸 RuntimeException 不算引擎契约文案——否则正向判据能被自家测试桩伪造 */
    @Test
    public void testPackageStubsAreNotEngineContract() {
        assertTrue(JeeflowFacade.isForeignDetail(RuntimeException.class,
                "任务不存在", null, frame("com.mldong.jeeflow.test.FakeRepo")));
    }
}
