package com.mldong.jeeflow.persist.meta;

/**
 * 动态元数据提供者 SPI（issues/23）——集成方只实现这一件事：提供表元数据。
 *
 * <p>来源可插拔：内置 {@link JsonMetaProvider}（JSON 配置）或集成方自实现
 * （如把 mldong dev_schema 映射为 storageType 语义）。</p>
 *
 * <p>写、读共用：写入引擎与回显读取都按本接口返回的元数据执行，
 * 保证 storageType 语义两侧一致。</p>
 *
 * @author mldong
 */
public interface IDynamicMetaProvider {

    /**
     * 加载表元数据；未定义返回 null（调用方回落表结构探测，全 NORMAL 语义）。
     *
     * @param tableName 业务表名（relTableName 或流程名）
     */
    TableMeta loadTableMeta(String tableName);
}
