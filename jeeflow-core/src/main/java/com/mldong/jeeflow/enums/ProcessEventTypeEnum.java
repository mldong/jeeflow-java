package com.mldong.jeeflow.enums;

/**
 * 流程事件类型
 *
 * <p>规范名＋码值＋载荷的唯一权威契约＝<b>规范 11 · 事件契约</b>
 * （{@code jeeflow-doc/docs/spec/11-events.md} §11.3），issues/127＋132 立章。
 * 本枚举是 A 套整型码的 java 血缘（1..4 连续扩展 5..9），八语言按规范名对齐；
 * <b>集成层跨语言判据一律用规范名，不得拿数字码当判据</b>（§11.6：Go/Node 原 0..5
 * 套、Python 原字符串套都重排到本套）。一个号一旦发出去不许改语义、不许复用
 * （4 号位让位给 {@link #CC_CREATE} 的历史见该项注释）。</p>
 *
 * @author mldong
 */
public enum ProcessEventTypeEnum {
    /** 流程实例开始 */
    PROCESS_INSTANCE_START(1, "流程实例开始事件"),
    /** 流程实例结束 */
    PROCESS_INSTANCE_END(2, "流程实例结束事件"),
    /** 流程任务开始 */
    PROCESS_TASK_START(3, "流程任务开始事件"),
    /**
     * 抄送知会（issues/102 新增，六语言统一；issues/127 补齐办理腿与手动腿）。
     *
     * <p>4 号位原为 {@code PROCESS_TASK_END}（流程任务结束）——引擎从不 fire 独立任务结束事件
     * （spec 04-extensions §7「Java 从不 fire PROCESS_TASK_END」），该枚举值此前从未被任何 fire 点/
     * 监听器消费（全联邦 grep 证实零引用；集成层监听器亦注明「TASK_END 引擎不 fire」）。故 4 号位
     * 复用于 {@code CC_CREATE}，与 PHP 参考实现（v1.3.8 {@code CC_CREATE=4}，已发版）码值对齐。
     * 码值不重排：4 仍为 4，仅语义由「任务结束」改「抄送知会」，1/2/3 不变。</p>
     *
     * <p>触发面三条路径同判（§11.3 码 4「发起 {@code f_ccActors}／办理 {@code tf_ccActors}／
     * 手动 {@code createCCInstance}」＋ §11.2 原则 1「码值表达发生了什么事实，不表达谁触发的」）：
     * cc 行落库后<b>逐抄送人</b> fire 一次，{@code ccActorId} 直传事件体。</p>
     */
    CC_CREATE(4, "抄送知会事件"),
    /**
     * 任务被办掉（同意 / 跳转 / 会签办理）——§11.3 码 5（issues/132 新增）。
     *
     * <p>触发时机：任务行 {@code state} 更新为已完成并<b>落库之后</b>（§11.2 原则 3）。
     * {@code sourceId}＝taskId；直传载荷 instanceId / taskId / operator / submitType。
     * 与 {@link #TASK_REJECT} <b>互斥</b>：同一个办理动作走退回就不再 fire 本支。</p>
     *
     * <p>注：本支不是被废弃的 {@code PROCESS_TASK_END}——后者是"任务节点结束"的独立事件位，
     * §11.4 第 4 条明确它不复活；"任务被办掉"这件事由本支承载，go/py/node 原有的
     * {@code TaskComplete} 升为全栈基准（§11.6）。</p>
     */
    TASK_COMPLETE(5, "任务办结事件"),
    /**
     * 任务被退回 / 拒绝（含退发起人、软拒绝、跳转回退）——§11.3 码 6（issues/132 新增）。
     *
     * <p>码粗载荷细（§11.2 原则 2）：拒绝 / 退回上一步 / 退回发起人 / 会签一票否决
     * <b>共用本支</b>，靠载荷 {@code submitType} 区分，不各开一号。
     * 触发时机：退回动作使任务/实例落库之后；与 {@link #TASK_COMPLETE} 互斥。
     * 实例进入终态另发 {@link #PROCESS_INSTANCE_END}(2)，两支不互相替代。</p>
     */
    TASK_REJECT(6, "任务退回/拒绝事件"),
    /**
     * 转办发生——§11.3 码 7（issues/132 新增，八栈此前零事件）。
     *
     * <p>触发时机：任务参与者在 {@code processTask/transfer}（issues/115）里被替换<b>并落库之后</b>。
     * {@code sourceId}＝taskId；直传载荷 instanceId / taskId / fromActor / toActor / operator。
     * 转办不新建任务行，故本支不伴随 {@link #PROCESS_TASK_START}(3)。</p>
     */
    TASK_TRANSFER(7, "任务转办事件"),
    /**
     * 撤回发生（实例进入 30）——§11.3 码 8（issues/132 新增，八栈此前零事件）。
     *
     * <p>触发时机：撤回把实例 {@code state} 写 30 <b>落库之后</b>，被撤任务行更新完成后
     * <b>每轮撤回只 fire 一次</b>（不逐任务——一次撤回动的是一个事实）。
     * {@code sourceId}＝instanceId；直传载荷 instanceId / operator。
     * 撤回的实例状态守卫（非 10 一律拒，内部码 20010009）见 issues/134，被拒的那次不发本支。</p>
     */
    TASK_WITHDRAW(8, "流程撤回事件"),
    /**
     * 实例被终止（40 强行终止）——§11.3 码 9（issues/132 新增，八栈此前零事件）。
     *
     * <p>触发时机：实例 {@code state} 写 40 <b>落库之后</b>；{@code sourceId}＝instanceId；
     * 直传载荷 instanceId / operator / reason。</p>
     *
     * <p><b>本栈现状＝占号、无触发源</b>（spec §11.3 已把号位占住，"一个号一旦发出去不许改语义、
     * 不许复用"）：java 门面没有"终止实例"的 action（{@code JeeflowFacade} 的 action 分派表里
     * 无 interrupt/terminate/abort/cancel 任何一支），聚合根 {@code ProcessInstance#interrupt}
     * 是全栈唯一把实例 {@code state} 写成 40 的地方，而它<b>生产路径零调用者</b>——grep 证据
     * （1.8.34 工作树，范围＝各模块 {@code src/main}）：
     * ① {@code interrupt(} 命中的 main 源只有定义处三处 {@code ProcessInstance:144}（实例，写
     * 40 于 {@code :148}）、{@code ProcessInstance:146 → ProcessTask:117}（实例内部顺带撤任务，
     * 即调用 {@code ProcessTask#interrupt} 的那一行，本身仍只在 {@code interrupt} 里可达）；
     * ② {@code INTERRUPT} 在 main 源的命中全是定义/读判据（{@code ProcessInstanceStateEnum:16}、
     * {@code ProcessTaskStateEnum:16}、上述两个写点、{@code ProcessTask:136} 的"已终止"判定），
     * 无任何 {@code setState(40)}/{@code instance.interrupt(...)} 调用；
     * ③ 全仓 {@code interrupt} 的调用者只有测试 {@code WithdrawInstanceStateGuardTest:197/282}
     * （拿它造 40 档撤回负例，不经任何门面 action）。issues/134 §5.2 因此把 L2-28 限定在
     * 20 ＋ 正向 10，壳侧同样造不出 40 这一档。</p>
     *
     * <p><b>后续义务（本轮不新增门面 action，那是跨栈契约扩张、待 owner 批）</b>：任何把实例
     * {@code state} 写成 40 的<b>收口点</b>一旦出现——门面的 {@code processInstance/interrupt}
     * 之类 action、{@code ProcessInstance#interrupt} 的首个生产调用者、或任何直接把实例行
     * 更新为 40 的路径——<b>必须</b>在该次写库成功后 fire 本支（时机判据同 §11.2 原则 3，
     * 载荷三键齐 {@code instanceId}/{@code operator}/{@code reason}，落点与码 2 同形：
     * {@code ProcessPublisher} 上加 {@code notifyInstanceTerminated} 收口，勿在调用点手搓载荷）。
     * 在补齐之前，本栈的门格（08-compliance 场景 34／镜像层 L2-30）按 <b>unreachable 记账</b>：
     * 缺的是触发源，不是 fire 点，不得用"集成层主动补发"顶替（§11.1 明令禁止）。</p>
     */
    INSTANCE_TERMINATED(9, "流程实例终止事件");

    // 10+ 预留：超时催办 / 超时自动通过 ……（§11.4 第 1 条「本轮不发」——八栈都没有时钟扫描器，
    // 发了没有触发源；占号只为防下一个新栈再发明一套，见 §11.3 表末行）

    private final Integer code;
    private final String message;

    ProcessEventTypeEnum(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
