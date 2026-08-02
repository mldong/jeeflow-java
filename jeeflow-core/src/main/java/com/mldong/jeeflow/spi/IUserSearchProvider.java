package com.mldong.jeeflow.spi;

import java.util.Map;

/**
 * 用户搜索钩子（v1.2.0，可选）——candidatePage 的"用户分页搜索"依赖集成方用户系统
 *
 * <p>未注入时：模型候选命中仍可用 {@link IUserProvider#getUser} 逐个映射；
 * 用户分页搜索返回明确错误。集成方实现后通过门面 setter 注入。</p>
 *
 * @author mldong
 */
public interface IUserSearchProvider {

    /**
     * 用户分页搜索（query 透传 pageNum/pageSize/搜索条件 m_*）
     */
    PageResult<Map<String, Object>> page(PageQuery query);

    /**
     * 单用户信息（候选映射用）
     */
    Map<String, Object> findById(String userId);
}
