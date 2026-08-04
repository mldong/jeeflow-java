package com.mldong.jeeflow.persist;

import java.util.List;
import java.util.Map;

/**
 * 动态表写入器（引擎无关，issues/18）——给「表名 + 字段 Map」安全写入任意业务表。
 *
 * <p>通用能力：列过滤（按目标表结构）/ 参数化 INSERT（防注入）/ 幂等检查 / 系统字段补充。
 * 不依赖工作流引擎任何类型；引擎侧由 {@code PersistPostInterceptor} 适配调用。</p>
 *
 * @author mldong
 */
public interface DynamicTableWriter {

    /**
     * 列过滤：返回目标表实际存在的列（输入字段 ∩ 表结构列，保序去重）。
     * 表结构首次查询后缓存。
     */
    List<String> filterColumns(String tableName, List<String> columns);

    /**
     * 参数化 INSERT（占位符按驱动，防注入），返回主键（自增/雪花；无主键列返回 null）。
     * data 中不在表结构内的字段自动剔除（内部先 filterColumns）。
     */
    Object insert(String tableName, Map<String, Object> data);

    /**
     * 幂等检查：bizKey 列 = bizKeyValue 的记录是否存在（如 processInstanceId）。
     */
    boolean exists(String tableName, String bizKey, Object bizKeyValue);

    /**
     * 系统字段补充：按配置列名填充（createTime/createUser/updateTime/updateUser/isDeleted），
     * 未配置的列跳过；已存在的值不覆盖。
     *
     * @param data   目标字段 Map（原地修改）
     * @param insert true=插入（填 create*），false=更新（只填 update*）
     */
    void fillSystemFields(Map<String, Object> data, boolean insert);

    /**
     * 按条件列更新（1.8.0 SYNC 同步演进）——data 按列过滤后 SET，条件列不参与 SET。
     * 不支持更新的 writer（默认实现）抛 UnsupportedOperationException。
     *
     * @return 更新行数
     */
    default int update(String tableName, Map<String, Object> data, String whereColumn, Object whereValue) {
        throw new UnsupportedOperationException(
                "当前 writer 不支持 update（SYNC 同步演进需要）：" + getClass().getName());
    }
}
