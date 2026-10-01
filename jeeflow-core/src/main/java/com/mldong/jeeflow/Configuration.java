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
        // 子流程档（issues/141 G4，owner 2026-10-01 补拍＋二拍终局；spec 02-flow-definition.md §6.1 义务 1/3）：
        // **规范名＝设计器实际输出的驼峰 `snaker:subProcess`**（mldong-flow-designer-plus 两模式都输出它，
        // vben5-wf 原样透传无改写层，库里全部存量定义也是驼峰）。查表是 SimpleContext 的 HashMap get、
        // 大小写敏感、未命中即丢节点，所以三种写法在这里同值并列注册，谁都不能少。
        // 下面三档**就地保留、不删不管**：原「小写 subprocess 是规范名 ＋ 旧写法保一代并标 deprecate、
        // 下个代次再删」的计划**已作废**——删驼峰 `subProcess` 会打断设计器输出与全部存量定义。
        // 义务 1「查表前大小写归一」**不做**（不是待办）：归一化要改 SimpleContext.findByName 的查表口径、
        // 牵动档位表全部条目，而 owner 裁定「只能按照设计器做，历史旧账不管」。
        // 二拍：**子流程暂不进契约面**——本栈 `WfSubProcessParser` 也只解析 form/version、不执行子流程，
        // 真实现后续单独开案；六栈（php/rust/moon/go/python/node）不补此档，靠未知档日志（义务 2）暴露。
        ServiceContext.put("subprocess", WfSubProcessParser.class);
        // 同值别名（规范名＝驼峰 subProcess）：以下两档与上面那档一并保留，不标 deprecate、不排期删除
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
