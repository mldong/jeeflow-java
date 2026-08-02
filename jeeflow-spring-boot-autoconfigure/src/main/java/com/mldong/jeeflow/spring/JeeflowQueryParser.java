package com.mldong.jeeflow.spring;

import com.mldong.jeeflow.spi.PageQuery;

import java.util.Map;

/**
 * m_* 查询参数解析器（spring 模块兼容版）
 *
 * <p>v1.1.0：解析逻辑移至 core（{@code com.mldong.jeeflow.spi.JeeflowQueryParser}，
 * JeeflowFacade 门面内部使用），本类继承以保持既有引用兼容。</p>
 *
 * @author mldong
 */
public class JeeflowQueryParser extends com.mldong.jeeflow.spi.JeeflowQueryParser {

    public JeeflowQueryParser() {
        super();
    }

    @Override
    public PageQuery parse(Map<String, Object> params) {
        return super.parse(params);
    }
}
