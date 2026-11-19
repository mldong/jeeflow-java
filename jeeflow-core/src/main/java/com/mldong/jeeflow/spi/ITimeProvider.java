package com.mldong.jeeflow.spi;

import java.time.LocalDateTime;

/**
 * 引擎时钟提供者（spec 04 · 时间基准）：不注册时用 {@code LocalDateTime.now()}。
 * 边界测试必须注入固定时钟，禁止混用浏览器钟/应用本地钟/数据库会话钟。
 */
public interface ITimeProvider {

    LocalDateTime now();
}
