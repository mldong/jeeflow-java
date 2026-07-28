package com.mldong.jeeflow;

import com.mldong.jeeflow.context.SimpleContext;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.parser.impl.*;

/**
 * 引擎配置
 *
 * <p>初始化引擎时调用，注册所有内置的节点解析器到 {@link ServiceContext}。
 * 默认使用 {@link SimpleContext}（纯 Map 实现）。</p>
 *
 * @author mldong
 */
public class Configuration {

    public Configuration() {
        this(new SimpleContext());
    }

    public Configuration(Context context) {
        ServiceContext.setContext(context);
        ServiceContext.put("start", StartParser.class);
        ServiceContext.put("end", EndParser.class);
        ServiceContext.put("task", TaskParser.class);
        ServiceContext.put("decision", DecisionParser.class);
        ServiceContext.put("fork", ForkParser.class);
        ServiceContext.put("join", JoinParser.class);
        ServiceContext.put("custom", CustomParser.class);
        ServiceContext.put("wfSubProcess", WfSubProcessParser.class);
        ServiceContext.put("subProcess", WfSubProcessParser.class);
    }
}
