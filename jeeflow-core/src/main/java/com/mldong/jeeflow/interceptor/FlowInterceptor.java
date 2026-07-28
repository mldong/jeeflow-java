package com.mldong.jeeflow.interceptor;

import com.mldong.jeeflow.core.Execution;

/**
 * 流程拦截器接口
 *
 * @author mldong
 */
public interface FlowInterceptor {

    void intercept(Execution execution);
}
