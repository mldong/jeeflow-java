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
 * <p>也承载引擎级开关，当前一项：{@link #surrogateAutoApply(boolean)} 委托代理自动生效
 * （issues/116，默认开启）。</p>
 *
 * @author mldong
 */
public class Configuration {

    /**
     * 委托代理自动生效开关（issues/116）：任务落库前把生效中的被委托人并入参与者集合。
     * 默认开启——集成方零配置即生效；关闭一行：
     * {@code new Configuration().surrogateAutoApply(false)}
     * （Spring Boot 侧对应配置项 {@code jeeflow.surrogate.auto-apply=false}）。
     */
    private boolean surrogateAutoApply = true;

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

    /** 委托代理自动生效是否开启（issues/116，默认 true） */
    public boolean isSurrogateAutoApply() {
        return surrogateAutoApply;
    }

    /**
     * 开/关委托代理自动生效（引擎内置行为，issues/116）。
     * 关闭后 {@code processSurrogate/*} 回到"仅台账"语义：配了委托也不会追加到任务参与者。
     */
    public Configuration surrogateAutoApply(boolean enabled) {
        this.surrogateAutoApply = enabled;
        return this;
    }
}
