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
        // 子流程档：spec 02-flow-definition.md「类型键的三条义务」第 3 条（issues/141 G4 · C 步）
        // 立的是<b>小写</b> `snaker:subprocess`，而 java 参考实现历史上只认大写 `subProcess`
        // ＋ 内部变体 `wfSubProcess` ⇒ 照 spec 写的定义在本栈拿不到子流程节点（查表是
        // SimpleContext 的 HashMap get，大小写敏感，未命中即静默丢节点）。
        // 姿势与 spec 11 §11.6 事件成员名同一条尺子：新写法（小写 subprocess，规范名）＋
        // 旧写法 wfSubProcess / subProcess 作为<b>同值别名保留一代并标 deprecate</b>，下个代次再删。
        // ⚠️ 待办（本轮 owner 明确不做）：spec 02 义务 1「查表前必须大小写归一」还没落地——
        // 归一化要改 SimpleContext.findByName 的查表口径，会影响档位表全部条目，需一次全量回归。
        ServiceContext.put("subprocess", WfSubProcessParser.class);
        // deprecate 别名（规范名 subprocess）：以下两档保留一代
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
