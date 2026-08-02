package com.mldong.jeeflow.enums;

/**
 * 字典枚举统一契约（v1.4.0，引擎元数据能力）
 *
 * <p>内置状态枚举均含 code/message，实现本接口后可由 {@link com.mldong.jeeflow.metadata.EnumDictRegistry}
 * 直接生成字典（value = code，label = message），对齐 boot3 字典 key。</p>
 *
 * @author mldong
 */
public interface IDictEnum {

    /** 字典 value（数字编码） */
    Integer getCode();

    /** 字典 label（显示名） */
    String getMessage();
}
