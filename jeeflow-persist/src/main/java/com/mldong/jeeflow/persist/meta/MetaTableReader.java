package com.mldong.jeeflow.persist.meta;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mldong.jeeflow.persist.jdbc.JdbcTableReader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 元数据驱动的动态读取引擎（issues/23 读侧最小闭环）——与写入侧 {@link MetaTableWriter} 职责分离。
 *
 * <p>{@link #readByProcessInstance}：按 relTableName + process_instance_id 回显业务数据，
 * 按 {@link TableMeta} 的 storageType 反序列化（NORMAL 直读 / JSON 反序列化 /
 * EXPAND 反展开 / ONE2ONE·ONE2MANY 子表递归组装），与写入共用同一份元数据。</p>
 *
 * <p>边界（不做）：通用条件分页 / 动态条件语法 / 数据权限 / 排序——集成方查询体系的领域。</p>
 *
 * <p>回落：未提供元数据时原样返回原始行（列名→值）。</p>
 *
 * @author mldong
 */
public class MetaTableReader {

    private final JdbcTableReader reader;
    private final IDynamicMetaProvider provider;
    private final ObjectMapper mapper = new ObjectMapper();

    public MetaTableReader(JdbcTableReader reader, IDynamicMetaProvider provider) {
        this.reader = reader;
        this.provider = provider;
    }

    /**
     * 按流程实例回显业务数据：relTableName + process_instance_id 查主表单条，
     * 按 TableMeta.storageType 反序列化（子表为对象/数组）。
     *
     * @return 表单字段名 → 值；无记录返回 null；无元数据回落原始行
     */
    public Map<String, Object> readByProcessInstance(String tableName, Object processInstanceId) {
        Map<String, Object> row = reader.queryFirst(tableName, "process_instance_id", processInstanceId);
        if (row == null) return null;
        TableMeta meta = provider.loadTableMeta(tableName);
        if (meta == null) return row;   // 无元数据：原样返回（列名→值）
        return assemble(meta, row);
    }

    /** 按元数据组装回显结果（字段名 → 值） */
    public Map<String, Object> assemble(TableMeta meta, Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (FieldMeta f : meta.getFields()) {
            Object v = findRowValue(row, f.getColumnName());
            switch (f.getStorageType()) {
                case JSON:
                    result.put(f.getName(), fromJson(v));
                    break;
                case EXPAND:
                    Map<String, Object> obj = expandFrom(row, f);
                    if (obj != null) result.put(f.getName(), obj);
                    break;
                case ONE2ONE:
                case ONE2MANY:
                    Object sub = readSubTable(meta, f, row);
                    if (sub != null) result.put(f.getName(), sub);
                    break;
                default:
                    if (v != null) result.put(f.getName(), v);
            }
        }
        // 未在元数据中的列（process_instance_id/apply_user_id/系统字段）原样带出（key 统一小写，跨方言一致）
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (meta.findFieldByColumn(e.getKey()) == null) {
                result.putIfAbsent(e.getKey().toLowerCase(), e.getValue());
            }
        }
        return result;
    }

    /** EXPAND 反展开：多列 → 对象 */
    private Map<String, Object> expandFrom(Map<String, Object> row, FieldMeta f) {
        Map<String, Object> obj = new LinkedHashMap<>();
        for (Map.Entry<String, String> ef : f.safeExpandFields().entrySet()) {
            Object v = findRowValue(row, ef.getValue());
            if (v != null) obj.put(ef.getKey(), v);
        }
        return obj.isEmpty() ? null : obj;
    }

    /** ONE2ONE/ONE2MANY 子表读取（外键=主表主键，递归按子表元数据组装） */
    private Object readSubTable(TableMeta parentMeta, FieldMeta f, Map<String, Object> row) {
        Object parentPk = findRowValue(row, parentMeta.getPrimaryKey());
        if (parentPk == null) return null;
        String fk = f.getForeignKey() != null ? f.getForeignKey() : parentMeta.getPrimaryKey();
        TableMeta subMeta = provider.loadTableMeta(f.getTargetTable());
        if (f.getStorageType() == StorageType.ONE2ONE) {
            Map<String, Object> sub = reader.queryFirst(f.getTargetTable(), fk, parentPk);
            if (sub == null) return null;
            return subMeta != null ? assemble(subMeta, sub) : sub;
        }
        // ONE2MANY
        List<Map<String, Object>> subs = reader.queryList(f.getTargetTable(), fk, parentPk, 0);
        List<Object> result = new ArrayList<>();
        for (Map<String, Object> sub : subs) {
            result.add(subMeta != null ? assemble(subMeta, sub) : sub);
        }
        return result;
    }

    /** 按列名取值（宽松：忽略大小写） */
    private Object findRowValue(Map<String, Object> row, String columnName) {
        if (row == null || columnName == null) return null;
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey().equalsIgnoreCase(columnName)) return e.getValue();
        }
        return null;
    }

    private Object fromJson(Object v) {
        if (v == null) return null;
        try {
            return mapper.readValue(v.toString(), Object.class);
        } catch (Exception e) {
            return v;   // 非 JSON 串（容错原样返回）
        }
    }
}
