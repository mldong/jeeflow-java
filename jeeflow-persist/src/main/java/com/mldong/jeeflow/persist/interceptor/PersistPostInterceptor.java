package com.mldong.jeeflow.persist.interceptor;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessInstanceStateEnum;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
import com.mldong.jeeflow.interceptor.FlowInterceptor;
import com.mldong.jeeflow.model.EndModel;
import com.mldong.jeeflow.persist.DynamicTableWriter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工作流业务数据入库适配拦截器（issues/18）——流程结束同意后，f_ 表单数据写入业务表。
 *
 * <p>引擎零改动：集成方在流程设计器配置 {@code postInterceptors} 类名即挂载（模型级，
 * 流程结束节点执行后由引擎反射实例化调用）。</p>
 *
 * <p>writer 获取：① {@link #setWriter} 编程式注入；② 无参构造（模型级反射）时从
 * {@link ServiceContext} 按类型查找 {@link DynamicTableWriter}（集成方启动时注册一次）。</p>
 *
 * <p>语义（spec 契约）：</p>
 * <ul>
 *   <li>时机：EndModel 执行后 + 实例 FINISHED + submitType=AGREE（不同意/退回不入库）</li>
 *   <li>字段：实例 variables 中 {@code f_} 前缀字段，去前缀</li>
 *   <li>表名：{@code ProcessModel.relTableName}，缺省回落流程 name</li>
 *   <li>系统字段：writer 通用字段 + 流程上下文（process_instance_id / apply_user_id / apply_dept_id，蛇形列名约定）</li>
 *   <li>幂等：bizKey = process_instance_id（先查后插，跨请求有效）</li>
 *   <li>静默跳过：未配置表名 / 非同意 / writer 未注入</li>
 * </ul>
 *
 * @author mldong
 */
public class PersistPostInterceptor implements FlowInterceptor {

    /** f_ 前缀（实例表单字段） */
    public static final String FIELD_PREFIX = "f_";

    private DynamicTableWriter writer;

    public void setWriter(DynamicTableWriter writer) {
        this.writer = writer;
    }

    @Override
    public void intercept(Execution execution) {
        if (writer == null) {
            // 模型级反射实例化（无参构造）场景：从引擎上下文按类型取 writer
            writer = ServiceContext.find(DynamicTableWriter.class);
        }
        if (writer == null) return;                       // 未注入 writer：静默跳过
        ProcessInstance instance = execution.getProcessInstance();
        if (instance == null) return;
        // 时机：仅流程正常结束（FINISHED——只有 EndModel 执行才会置此状态）且同意
        if (!ProcessInstanceStateEnum.FINISHED.getCode().equals(instance.getState())) return;
        Integer submitType = toInt(execution.getArgs().get(FlowConst.SUBMIT_TYPE));
        if (submitType == null || submitType != ProcessSubmitTypeEnum.AGREE.getCode()) return;

        String tableName = execution.getProcessModel().getRelTableName();
        if (tableName == null || tableName.trim().isEmpty()) {
            tableName = execution.getProcessModel().getName();   // 缺省回落流程 name
        }
        if (tableName == null || tableName.trim().isEmpty()) return;

        // 幂等：以 process_instance_id 为键，先查后插
        if (writer.exists(tableName, "process_instance_id", instance.getInstanceId())) return;

        // 提取 f_ 前缀字段（去前缀）
        Map<String, Object> data = new LinkedHashMap<>();
        FlowData variables = instance.getVariables();
        if (variables != null) {
            for (Map.Entry<String, Object> e : variables.entrySet()) {
                String key = e.getKey();
                if (key != null && key.startsWith(FIELD_PREFIX) && key.length() > FIELD_PREFIX.length()) {
                    data.put(key.substring(FIELD_PREFIX.length()), e.getValue());
                }
            }
        }

        // 流程上下文字段（蛇形列名约定，与 writer 系统字段一致）
        data.putIfAbsent("process_instance_id", instance.getInstanceId());
        data.putIfAbsent("apply_user_id", instance.getOperator());
        data.putIfAbsent("apply_dept_id", variables != null ? variables.get("u_deptId") : null);

        // 通用系统字段（writer 按配置列填充）
        writer.fillSystemFields(data, true);

        writer.insert(tableName, data);
    }

    private static Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
