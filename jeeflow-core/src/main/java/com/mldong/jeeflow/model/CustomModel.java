package com.mldong.jeeflow.model;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.handler.IHandler;
import com.mldong.jeeflow.util.StringUtils;
import com.mldong.jeeflow.domain.ProcessTask;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 自定义节点模型
 *
 * <p>记录类节点（spec 02 §6.1）：执行 {@code clazz}、落一条 {@code task_state=20} 的历史行、
 * 令牌沿出边继续流转。它<b>不解析参与者、不产生待办</b>。</p>
 *
 * @author mldong
 */
public class CustomModel extends NodeModel {

    /**
     * JUL 而非 slf4j：与 {@code ModelParser}／{@code ProcessPublisher} 同口径，core 零外部运行时依赖。
     */
    private static final Logger log = Logger.getLogger(CustomModel.class.getName());

    private String clazz;
    private String methodName;
    private String args;
    private String var;
    private Object invokeObject;

    @Override
    public void exec(Execution execution) {
        // 规范 02 §6.2 第 2 条（issues/142 · owner 2026-09-30 拍）：
        // 「clazz 解析不了 ⇒ 记日志 + 照常落历史行 + 令牌继续流转，严禁抛错打断建单」。
        // 与 spec 04「误配的到期档落穿→NULL、节点属性配错不该把流程炸掉」（java 6bdf41b）同一条哲学。
        //
        // 只有 HandlerConfigException（**配置形状错**：clazz 空/未注册/反射不起来/方法找不到/
        // 调用参数形状对不上）走这条豁免；处理器**自身执行抛异常**是业务错误，
        // 从 invokeHandler 里自然外抛，不在豁免内（§6.2 明写）。
        try {
            invokeHandler(execution);
        } catch (HandlerConfigException e) {
            log.warning(e.getMessage());
        }

        // 记录历史任务
        // 建单不变量（规范 04 · 退回上一步）：历史行同样要带 parent 与首节点标记（issues/121 P1）
        //
        // 返回值不再丢弃（issues/142 · §6.2 第 1 条）：登记到 execution 的历史行通道，由引擎的
        // saveHistoryTasks 落库。⚠️ 不能改成 execution.addTask(...)——那条腿是
        // saveNewTask→notifyTaskStart，会给一条生来已完成的行 fire 码 3「新待办产生」，
        // 正是 §6.1 禁止的形状。解耦理由见 Execution#historyTasks 字段注释。
        ProcessTask historyTask = execution.getProcessInstance().createHistoryTask(this,
                execution.getOperator(),
                execution.getProcessTaskId(),
                com.mldong.jeeflow.util.FlowUtil.isFirstTaskName(execution.getProcessModel(), getName()));
        execution.addHistoryTask(historyTask);
        runOutTransition(execution);
    }

    /**
     * 解析并调用处理器。
     *
     * <p><b>判档口径（本方法把"配错形状"与"业务错误"分开，这是 §6.2 第 2 条的核心）</b>：</p>
     * <ul>
     *   <li>{@code clazz} 为空串/null ⇒ <b>配错</b>：节点写成了 custom 却没挂处理器，
     *       流程定义本身的问题，不该让一次发起替它买单 ⇒ 抛 {@link HandlerConfigException}（记 WARNING 续流）。
     *       ⚠️ 与下一档**文案分开**（§6.2 要求"未注册处理器"与"clazz 为空串"分别可诊断，
     *       不许像 c# 那样合成同一条）。</li>
     *   <li>{@code clazz} 有值但类加载不了（{@code ClassNotFoundException}）⇒ <b>未注册处理器</b>，
     *       同样是配错形状 ⇒ WARNING 续流。</li>
     *   <li>类能加载但**反射实例化失败**（无公共无参构造／构造器不可访问／{@code InstantiationException}）
     *       ⇒ 仍归"反射失败"这一档（owner 口径：「clazz 为空串／未注册／反射失败 ⇒ 记 WARNING 继续」），
     *       处理器根本没跑起来，谈不上业务错误 ⇒ WARNING 续流。</li>
     *   <li>{@code methodName} 找不到（{@code NoSuchMethodException}）⇒ <b>本实现判它归"配错"</b>，
     *       理由写在这里：方法名是<b>流程定义里的配置项</b>，"按配置的名字在本类上找不到这个签名的公共方法"
     *       这件事在调用<b>之前</b>就成立，处理器一行业务代码都没执行，失败原因和用户把
     *       {@code clazz} 写错、把到期档写成 {@code +2h} 落穿是同族（都是节点属性配错）；
     *       而"打断整条建单"给出的 diagnosability 并不比一条带 class/method 名的 WARNING 更好。
     *       ⇒ WARNING 续流。<br>
     *       同族的 {@code IllegalAccessException}（方法找到了但不可访问）、
     *       {@code IllegalArgumentException}（{@code invoke} 在进方法体<b>之前</b>就以参数类型不匹配返回，
     *       即"调用形状对不上"）一并归这一档。</li>
     *   <li>{@code InvocationTargetException} ⇒ <b>处理器找到了、也进去了、是它自己跑炸的</b>：
     *       业务错误，<b>照旧外抛</b>（保持 1.8.35 的 {@code 方法调用失败} 包装文案，cause 换成解包后的
     *       目标异常，便于集成层按原始异常判型）。这一档若也吞掉，就是把豁免做成"什么都吞"。</li>
     *   <li>{@code IHandler.handle(...)} 内部抛出的任何异常 ⇒ 同样是业务错误，直接外抛，不拦。</li>
     * </ul>
     */
    private void invokeHandler(Execution execution) {
        // 用 isBlank 而不是 isEmpty：纯空白的 clazz 也归"没配"这一档，否则 Class.forName("")
        // 会把它报成"类路径上找不到"，§6.2 要求分别可诊断的两档文案就混成一条了
        if (StringUtils.isBlank(clazz)) {
            throw new HandlerConfigException("自定义节点[name=" + getName()
                    + ", displayName=" + getDisplayName()
                    + "]未配置 clazz（处理器类名为空），跳过处理器执行；历史行照常落库、令牌继续流转");
        }
        if (invokeObject == null) {
            Object instance;
            try {
                instance = Class.forName(clazz.trim()).getDeclaredConstructor().newInstance();
            } catch (ClassNotFoundException e) {
                throw new HandlerConfigException("自定义节点[name=" + getName() + "]的 clazz=" + clazz
                        + " 在类路径上找不到（处理器未注册），跳过处理器执行；历史行照常落库、令牌继续流转");
            } catch (Exception e) {
                throw new HandlerConfigException("自定义节点[name=" + getName() + "]的 clazz=" + clazz
                        + " 实例化对象失败（需要公共无参构造器）：" + e
                        + "，跳过处理器执行；历史行照常落库、令牌继续流转");
            }
            invokeObject = instance;
        }

        if (invokeObject instanceof IHandler) {
            // 处理器找到了并在这里执行——它抛的一切都是业务错误，不套 HandlerConfigException
            ((IHandler) invokeObject).handle(execution);
            return;
        }

        if (StringUtils.isBlank(methodName)) {
            throw new HandlerConfigException("自定义节点[name=" + getName() + "]的 clazz=" + clazz
                    + " 不是 IHandler 且未配置 methodName，跳过处理器执行；历史行照常落库、令牌继续流转");
        }

        Object[] params = getArgs(execution.getArgs(), args);
        Class<?>[] paramTypes = new Class<?>[params == null ? 0 : params.length];
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                paramTypes[i] = params[i] != null ? params[i].getClass() : Object.class;
            }
        }
        Method method;
        try {
            method = invokeObject.getClass().getMethod(methodName, paramTypes);
        } catch (NoSuchMethodException e) {
            throw new HandlerConfigException("自定义模型[class=" + clazz + "]无法找到方法名称:" + methodName
                    + "（参数形状 " + java.util.Arrays.toString(paramTypes) + "），跳过处理器执行；"
                    + "历史行照常落库、令牌继续流转");
        }

        Object returnValue;
        try {
            returnValue = method.invoke(invokeObject, params);
        } catch (InvocationTargetException e) {
            // 业务错误：处理器方法体里抛出来的 ⇒ 外抛（§6.2「处理器自身执行失败不在本条豁免内」）
            Throwable target = e.getTargetException() == null ? e : e.getTargetException();
            if (target instanceof Error) {
                throw (Error) target;
            }
            throw new RuntimeException("自定义模型[class=" + clazz + "]方法调用失败", target);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            // 调用形状错（不可访问／参数类型不匹配），方法体一行都没跑 ⇒ 配错档
            throw new HandlerConfigException("自定义模型[class=" + clazz + "]方法 " + methodName
                    + " 无法按配置的形状调用：" + e + "，跳过处理器执行；历史行照常落库、令牌继续流转");
        }
        if (StringUtils.isNotEmpty(var)) {
            execution.getArgs().put(var, returnValue);
        }
    }

    private static Object[] getArgs(Map<String, Object> execArgs, String argStr) {
        if (StringUtils.isEmpty(argStr)) return null;
        String[] argArray = argStr.split(",");
        Object[] objects = new Object[argArray.length];
        for (int i = 0; i < argArray.length; i++) {
            objects[i] = execArgs.get(argArray[i]);
        }
        return objects;
    }

    /**
     * "配置形状错"专用标记（区别于处理器抛出的业务异常）：
     * 只由 {@link #invokeHandler(Execution)} 在 clazz／methodName 解析失败时抛出，
     * {@link #exec(Execution)} 捕获后<b>记 WARNING 并继续流转</b>——
     * 用它做判档，才不至于把 §6.2 的豁免扩成"什么异常都吞"。
     */
    private static final class HandlerConfigException extends RuntimeException {
        HandlerConfigException(String message) {
            super(message);
        }
    }

    // ---- getters/setters ----
    public String getClazz() { return clazz; }
    public void setClazz(String clazz) { this.clazz = clazz; }
    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
    public String getArgs() { return args; }
    public void setArgs(String args) { this.args = args; }
    public String getVar() { return var; }
    public void setVar(String var) { this.var = var; }
    public Object getInvokeObject() { return invokeObject; }
    public void setInvokeObject(Object invokeObject) { this.invokeObject = invokeObject; }
}
