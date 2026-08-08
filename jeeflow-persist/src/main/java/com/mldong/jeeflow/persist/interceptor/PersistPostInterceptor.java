package com.mldong.jeeflow.persist.interceptor;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.enums.FlowConst;
import com.mldong.jeeflow.enums.ProcessInstanceStateEnum;
import com.mldong.jeeflow.enums.ProcessSubmitTypeEnum;
import com.mldong.jeeflow.interceptor.FlowInterceptor;
import com.mldong.jeeflow.model.NodeModel;
import com.mldong.jeeflow.model.TaskModel;
import com.mldong.jeeflow.persist.DynamicTableWriter;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流业务数据入库适配拦截器（issues/18，1.8.0 扩展 SYNC 同步演进）——
 * 按流程定义 {@code persistMode} 分派两种模式：
 *
 * <ul>
 *   <li><b>ARCHIVE（缺省）</b>：结束归档——流程 FINISHED + submitType=AGREE 时 INSERT 业务表</li>
 *   <li><b>SYNC（同步演进）</b>：发起 INSERT → 任务节点 UPDATE（f_ 按字段权限过滤 + tf_ 冗余
 *       + 状态字段）→ 结束 UPDATE 最终状态（同意/驳回都入库）</li>
 * </ul>
 *
 * <p>引擎零改动：集成方在流程设计器配置 {@code postInterceptors} 类名即挂载（模型级，
 * start/任务/结束节点执行后由引擎反射实例化调用）。</p>
 *
 * <p>writer 获取：① {@link #setWriter} 编程式注入；② 无参构造（模型级反射）时从
 * {@link ServiceContext} 按类型查找 {@link DynamicTableWriter}（集成方启动时注册一次）。</p>
 *
 * <p>字段权限（SYNC，vben5-wf 机制）：任务节点 {@code properties.field.PERMISSION_{表单字段名}}
 * 1=只读 / 2=可编辑 / 3=隐藏（缺省=可编辑）——只读/隐藏字段不参与 UPDATE。</p>
 *
 * @author mldong
 */
public class PersistPostInterceptor implements FlowInterceptor {

    /** f_ 前缀（实例表单字段） */
    public static final String FIELD_PREFIX = "f_";
    /** tf_ 前缀（任务字段，如审批意见，SYNC 下冗余写入） */
    public static final String TASK_FIELD_PREFIX = "tf_";
    /** 同步演进模式（流程定义 persistMode） */
    public static final String PERSIST_MODE_SYNC = "SYNC";
    /** 字段权限键前缀（节点 properties.field，vben5-wf 机制） */
    public static final String PERMISSION_PREFIX = "PERMISSION_";
    public static final int PERM_READ_ONLY = 1;
    public static final int PERM_EDIT = 2;
    public static final int PERM_HIDDEN = 3;

    // ─── 元数据（issues/60 注册助手） ────────────────────────────────────────────

    /** SPI 清单字典显示名（wf_flow_interceptor_post_process） */
    public static final String META_DISPLAY_NAME = "业务数据自动入库";
    /** SPI 清单排序 */
    public static final int META_ORDER = 0;
    /** SPI 清单分组（post 后置拦截器） */
    public static final String META_GROUP = "post";

    /**
     * 注册助手（issues/60）：把本拦截器元数据注册到元数据注册中心，
     * 供 SPI 清单字典（wf_flow_interceptor_post_process）展示——集成方在组装
     * persist 实例的同一处一行调用，保证「字典有 ⟺ 实例有」同步，不写死字符串。
     * 同名注册可覆盖引擎默认。
     */
    public static void registerMeta(com.mldong.jeeflow.metadata.HandlerRegistry registry) {
        registry.register(com.mldong.jeeflow.interceptor.FlowInterceptor.class,
                PersistPostInterceptor.class.getName(), META_DISPLAY_NAME, META_ORDER, META_GROUP);
    }

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
        String mode = execution.getProcessModel() != null
                ? execution.getProcessModel().getPersistMode() : null;
        if (PERSIST_MODE_SYNC.equalsIgnoreCase(mode)) {
            interceptSync(execution, instance);
        } else {
            interceptArchive(execution, instance);
        }
    }

    // ─── ARCHIVE（缺省：结束归档，1.6.x 行为不变） ─────────────────────────────

    private void interceptArchive(Execution execution, ProcessInstance instance) {
        // 时机：仅流程正常结束（FINISHED——只有 EndModel 执行才会置此状态）且同意
        if (!ProcessInstanceStateEnum.FINISHED.getCode().equals(instance.getState())) return;
        Integer submitType = toInt(execution.getArgs().get(FlowConst.SUBMIT_TYPE));
        if (submitType == null || submitType != ProcessSubmitTypeEnum.AGREE.getCode()) return;
        String tableName = resolveTableName(execution);
        if (tableName == null) return;
        if (!markChain(execution, instance)) return;      // 同链重复触发防护
        // 幂等：以 process_instance_id 为键，先查后插
        if (writer.exists(tableName, "process_instance_id", instance.getInstanceId())) return;

        Map<String, Object> data = extractFields(instance, null, false, true);   // 只 f_ 全量
        fillContext(data, instance);
        writer.fillSystemFields(data, true);
        writer.insert(tableName, data);
    }

    // ─── SYNC（同步演进：发起 INSERT → 节点 UPDATE → 结束定稿） ────────────────

    private void interceptSync(Execution execution, ProcessInstance instance) {
        String tableName = resolveTableName(execution);
        if (tableName == null) return;
        if (!markChain(execution, instance)) return;      // 同链重复触发防护
        boolean exists = writer.exists(tableName, "process_instance_id", instance.getInstanceId());

        // 任务节点（TaskModel）才更新业务字段：f_ 按字段权限过滤；非任务节点（如结束）只定稿状态，
        // 避免全量覆盖任务节点的只读/隐藏限制
        boolean taskNode = execution.getNodeModel() instanceof TaskModel;
        Map<String, Object> fieldPerm = taskNode ? resolveFieldPermission(execution) : null;
        Map<String, Object> data = extractFields(instance, !exists ? null : fieldPerm,
                !exists || taskNode, !exists || taskNode);

        // 状态字段：优先 {节点ID}_{状态码} 列，无则 {节点ID} 列。
        // 任务节点写 DOING(10)——任务推进状态（execPost 在流转链之后触发，此时 state 可能已被
        // 结束节点置为最终态，不能用 instance.getState()）；结束节点写最终状态（FINISHED/REJECT）。
        String nodeId = execution.getNodeModel() != null ? execution.getNodeModel().getName() : null;
        Integer stateCode = taskNode ? ProcessInstanceStateEnum.DOING.getCode() : instance.getState();
        putStateField(tableName, data, nodeId, stateCode);

        fillContext(data, instance);
        if (!exists) {
            writer.fillSystemFields(data, true);
            writer.insert(tableName, data);
        } else {
            writer.fillSystemFields(data, false);         // 只填 update 组
            writer.update(tableName, data, "process_instance_id", instance.getInstanceId());
        }
    }

    // ─── 公共 ───────────────────────────────────────────────────────────────────

    /** 表名：relTableName 缺省回落流程 name */
    private String resolveTableName(Execution execution) {
        if (execution.getProcessModel() == null) return null;
        String tableName = execution.getProcessModel().getRelTableName();
        if (tableName == null || tableName.trim().isEmpty()) {
            tableName = execution.getProcessModel().getName();
        }
        return (tableName == null || tableName.trim().isEmpty()) ? null : tableName.trim();
    }

    /**
     * 同链重复触发防护（issues/19，1.8.0 改为节点级）：同一执行链中**每个节点**触发一次
     * （任务节点推进更新 + 结束节点定稿是不同节点，都要生效），同节点不重复；exists 兜底跨请求。
     */
    private boolean markChain(Execution execution, ProcessInstance instance) {
        NodeModel node = execution.getNodeModel();
        String chainKey = "__persist_executed_" + instance.getInstanceId() + "_"
                + (node != null && node.getName() != null ? node.getName() : "");
        if (Boolean.TRUE.equals(execution.getArgs().get(chainKey))) return false;
        execution.getArgs().put(chainKey, true);
        return true;
    }

    /** 字段权限（任务节点 properties.field 的 PERMISSION_x；缺省 null=全部可编辑） */
    private Map<String, Object> resolveFieldPermission(Execution execution) {
        if (execution.getNodeModel() instanceof TaskModel) {
            FlowData ext = ((TaskModel) execution.getNodeModel()).getExt();
            if (ext != null && !ext.isEmpty()) return ext;
        }
        return null;
    }

    /**
     * 提取字段：f_ 去前缀（SYNC 下按字段权限过滤——只读/隐藏不更新；includeFormFields=false 时不带出，
     * 用于非任务节点定稿避免覆盖只读限制）；tf_ 去前缀冗余（有列则写，列过滤由 writer 做）。
     */
    private Map<String, Object> extractFields(ProcessInstance instance, Map<String, Object> fieldPerm,
                                              boolean includeTaskFields, boolean includeFormFields) {
        Map<String, Object> data = new LinkedHashMap<>();
        FlowData variables = instance.getVariables();
        if (variables != null) {
            for (Map.Entry<String, Object> e : variables.entrySet()) {
                String key = e.getKey();
                if (key == null) continue;
                if (includeFormFields && key.startsWith(FIELD_PREFIX) && key.length() > FIELD_PREFIX.length()) {
                    String fieldName = key.substring(FIELD_PREFIX.length());
                    if (!isEditable(fieldPerm, fieldName)) continue;
                    data.put(fieldName, e.getValue());
                } else if (includeTaskFields && key.startsWith(TASK_FIELD_PREFIX)
                        && key.length() > TASK_FIELD_PREFIX.length()) {
                    data.put(key.substring(TASK_FIELD_PREFIX.length()), e.getValue());
                }
            }
        }
        return data;
    }

    /**
     * 字段可编辑判定：无声明或 EDIT(2) 可更新；READ_ONLY(1)/HIDDEN(3) 不更新。
     * 键格式兼容两种（issues/25）：
     * - `PERMISSION_f_{表单字段全名}`——前端 vben5-wf 设计器约定（优先）
     * - `PERMISSION_{去前缀名}`——后端 1.8.0 首版格式（兼容）
     */
    private boolean isEditable(Map<String, Object> fieldPerm, String fieldName) {
        if (fieldPerm == null || fieldPerm.isEmpty()) return true;
        Object p = fieldPerm.get(PERMISSION_PREFIX + FIELD_PREFIX + fieldName);
        if (p == null) p = fieldPerm.get(PERMISSION_PREFIX + fieldName);
        if (p == null) return true;
        int perm = toInt(p);
        return perm == PERM_EDIT;
    }

    /** 状态字段写入：优先 {节点ID}_{状态码} 列，无则 {节点ID} 列（列探测过滤） */
    private void putStateField(String tableName, Map<String, Object> data, String nodeId, Integer stateCode) {
        if (nodeId == null || stateCode == null) return;
        List<String> kept = writer.filterColumns(tableName,
                Arrays.asList(nodeId + "_" + stateCode, nodeId));
        if (!kept.isEmpty()) {
            data.put(kept.get(0), stateCode);
        }
    }

    /** 流程上下文字段（蛇形列名约定，与 writer 系统字段一致） */
    private void fillContext(Map<String, Object> data, ProcessInstance instance) {
        FlowData variables = instance.getVariables();
        data.putIfAbsent("process_instance_id", instance.getInstanceId());
        data.putIfAbsent("apply_user_id", instance.getOperator());
        data.putIfAbsent("apply_dept_id", variables != null ? variables.get("u_deptId") : null);
    }

    private static Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
