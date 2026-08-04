package com.mldong.jeeflow.persist.meta;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mldong.jeeflow.persist.DynamicTableWriter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 元数据驱动的动态写入引擎（issues/23）——DynamicTableWriter 的增强实现（纯写职责）。
 *
 * <p>按 {@link TableMeta} 的 storageType 语义执行插入（NORMAL 直写 / JSON 序列化 /
 * EXPAND 展开 / ONE2ONE·ONE2MANY 子表递归），系统字段/主键生成/幂等沿用基础 writer。</p>
 *
 * <p>读取（流程回显）由 {@link MetaTableReader} 提供——读写职责分离。</p>
 *
 * <p>回落：集成方未提供元数据（{@code provider.loadTableMeta == null}）时完全委托基础 writer
 * （现状行为，零破坏）。</p>
 *
 * @author mldong
 */
public class MetaTableWriter implements DynamicTableWriter {

    private final DynamicTableWriter base;
    private final IDynamicMetaProvider provider;
    private final ObjectMapper mapper = new ObjectMapper();

    public MetaTableWriter(DynamicTableWriter base, IDynamicMetaProvider provider) {
        this.base = base;
        this.provider = provider;
    }

    public IDynamicMetaProvider getProvider() {
        return provider;
    }

    // ─── 写 ────────────────────────────────────────────────────────────────────

    @Override
    public List<String> filterColumns(String tableName, List<String> columns) {
        return base.filterColumns(tableName, columns);
    }

    @Override
    public Object insert(String tableName, Map<String, Object> data) {
        TableMeta meta = provider.loadTableMeta(tableName);
        if (meta == null) {
            return base.insert(tableName, data);   // 无元数据：回落现状
        }
        // 子表数据先收集（主表插入后处理，外键=主表主键）
        Map<String, Object> subData = new LinkedHashMap<>();
        Map<String, Object> row = new LinkedHashMap<>();
        for (FieldMeta f : meta.getFields()) {
            Object v = data.get(f.getName());
            if (v == null) continue;
            switch (f.getStorageType()) {
                case JSON:
                    row.put(f.getColumnName(), toJson(v));
                    break;
                case EXPAND:
                    expandInto(f, v, row);
                    break;
                case ONE2ONE:
                case ONE2MANY:
                    subData.put(f.getName(), v);
                    break;
                default:
                    row.put(f.getColumnName(), v);
            }
        }
        // 未消费的字段（流程上下文 process_instance_id 等 + 集成方自定义字段）直通基础 writer
        for (Map.Entry<String, Object> e : data.entrySet()) {
            if (meta.findField(e.getKey()) == null) {
                row.putIfAbsent(e.getKey(), e.getValue());
            }
        }
        base.fillSystemFields(row, true);
        Object pk = base.insert(tableName, row);   // 主表插入（自增/生成器返回主键）
        if (pk == null) {
            pk = findRowValue(row, meta.getPrimaryKey());   // 兜底：data 显式主键
        }
        // 子表递归插入（外键=主表主键，同事务语义由基础 writer 连接管理）
        for (Map.Entry<String, Object> e : subData.entrySet()) {
            insertSubTable(meta, meta.findField(e.getKey()), e.getValue(), pk);
        }
        return pk;
    }

    /** EXPAND：对象字段展开为多列（子字段名 → 表列名） */
    private void expandInto(FieldMeta f, Object v, Map<String, Object> row) {
        if (!(v instanceof Map)) return;
        Map<?, ?> obj = (Map<?, ?>) v;
        for (Map.Entry<String, String> ef : f.safeExpandFields().entrySet()) {
            Object fv = obj.get(ef.getKey());
            if (fv != null) row.put(ef.getValue(), fv);
        }
    }

    /** ONE2ONE/ONE2MANY：子表递归插入（阶段③，外键=主表主键） */
    private void insertSubTable(TableMeta parentMeta, FieldMeta f, Object v, Object parentPk) {
        if (parentPk == null) {
            throw new IllegalStateException("主表主键缺失，无法插入子表: " + f.getName());
        }
        String fk = f.getForeignKey() != null ? f.getForeignKey() : parentMeta.getPrimaryKey();
        if (f.getStorageType() == StorageType.ONE2ONE && v instanceof Map) {
            insertSubRow(f, (Map<?, ?>) v, fk, parentPk);
        } else if (f.getStorageType() == StorageType.ONE2MANY && v instanceof List) {
            for (Object item : (List<?>) v) {
                if (item instanceof Map) insertSubRow(f, (Map<?, ?>) item, fk, parentPk);
            }
        }
    }

    /** 子表单条插入（外键注入 + 递归走子表自身元数据） */
    @SuppressWarnings("unchecked")
    private void insertSubRow(FieldMeta f, Map<?, ?> subData, String fk, Object parentPk) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : subData.entrySet()) {
            row.put(String.valueOf(e.getKey()), e.getValue());
        }
        row.put(fk, parentPk);
        insert(f.getTargetTable(), row);
    }

    @Override
    public boolean exists(String tableName, String bizKey, Object bizKeyValue) {
        return base.exists(tableName, bizKey, bizKeyValue);
    }

    @Override
    public void fillSystemFields(Map<String, Object> data, boolean insert) {
        base.fillSystemFields(data, insert);
    }

    // ─── 内部 ───────────────────────────────────────────────────────────────────

    /** 按列名取值（宽松：忽略大小写） */
    protected Object findRowValue(Map<String, Object> row, String columnName) {
        if (row == null || columnName == null) return null;
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey().equalsIgnoreCase(columnName)) return e.getValue();
        }
        return null;
    }

    private String toJson(Object v) {
        try {
            return mapper.writeValueAsString(v);
        } catch (Exception e) {
            throw new RuntimeException("JSON 序列化失败: " + e.getMessage(), e);
        }
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
