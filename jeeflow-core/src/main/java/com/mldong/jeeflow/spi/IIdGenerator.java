package com.mldong.jeeflow.spi;

/**
 * ID 生成器 SPI
 *
 * <p>引擎需要生成主键时调用此接口。集成方可注入雪花算法、数据库序列、UUID 等实现。
 * 若不注册，引擎使用内置的 {@code SimpleIdGenerator}（基于时间戳）。</p>
 *
 * @author mldong
 */
public interface IIdGenerator {

    /** 生成下一个唯一 ID */
    long nextId();
}
