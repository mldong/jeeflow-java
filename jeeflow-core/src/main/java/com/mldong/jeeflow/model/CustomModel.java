package com.mldong.jeeflow.model;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.handler.IHandler;
import com.mldong.jeeflow.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 自定义节点模型
 *
 * @author mldong
 */
public class CustomModel extends NodeModel {

    private String clazz;
    private String methodName;
    private String args;
    private String var;
    private Object invokeObject;

    @Override
    public void exec(Execution execution) {
        if (invokeObject == null) {
            try {
                invokeObject = Class.forName(clazz.trim()).getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("自定义模型[class=" + clazz + "]实例化对象失败", e);
            }
        }

        if (invokeObject instanceof IHandler) {
            ((IHandler) invokeObject).handle(execution);
        } else {
            Object[] params = getArgs(execution.getArgs(), args);
            Class<?>[] paramTypes = new Class<?>[params == null ? 0 : params.length];
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    paramTypes[i] = params[i] != null ? params[i].getClass() : Object.class;
                }
            }
            try {
                Method method = invokeObject.getClass().getMethod(methodName, paramTypes);
                Object returnValue = method.invoke(invokeObject, params);
                if (StringUtils.isNotEmpty(var)) {
                    execution.getArgs().put(var, returnValue);
                }
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("自定义模型[class=" + clazz + "]无法找到方法名称:" + methodName, e);
            } catch (Exception e) {
                throw new RuntimeException("自定义模型[class=" + clazz + "]方法调用失败", e);
            }
        }

        // 记录历史任务
        execution.getProcessInstance().createHistoryTask(this, execution.getOperator());
        runOutTransition(execution);
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
