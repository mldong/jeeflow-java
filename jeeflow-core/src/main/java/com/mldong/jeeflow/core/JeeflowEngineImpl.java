package com.mldong.jeeflow.core;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.JeeflowException;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.*;
import com.mldong.jeeflow.event.PendingInstanceEnd;
import com.mldong.jeeflow.event.ProcessEvent;
import com.mldong.jeeflow.event.ProcessPublisher;
import com.mldong.jeeflow.handler.impl.CreateTaskHandler;
import com.mldong.jeeflow.interceptor.FlowInterceptor;
import com.mldong.jeeflow.interceptor.impl.SurrogateInterceptor;
import com.mldong.jeeflow.json.IJsonProvider;
import com.mldong.jeeflow.model.*;
import com.mldong.jeeflow.parser.ModelParser;
import com.mldong.jeeflow.spi.IProcessRepository;
import com.mldong.jeeflow.spi.ITransactionTemplate;
import com.mldong.jeeflow.spi.IUserProvider;
import com.mldong.jeeflow.util.FlowUtil;
import com.mldong.jeeflow.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 工作流引擎实现——薄编排层
 *
 * <p>引擎不直接操作持久层，通过 IProcessRepository SPI 与存储交互。
 * 所有状态变更委托给 ProcessInstance 聚合根。</p>
 *
 * @author mldong
 */
public class JeeflowEngineImpl implements JeeflowEngine {

    private IProcessRepository repository;
    private IJsonProvider jsonProvider;
    private IUserProvider userProvider;
    /** 引擎配置（承载 issues/116 委托自动生效开关；configure 时写入） */
    private Configuration config;
    /** 委托代理自动生效（issues/116）：引擎内置、默认开启，任务落库前把代理人并入参与者集合 */
    private final SurrogateInterceptor surrogateApplier = new SurrogateInterceptor();

    @Override
    public JeeflowEngine configure(Configuration config) {
        this.config = config;
        this.repository = ServiceContext.find(IProcessRepository.class);
        this.jsonProvider = ServiceContext.find(IJsonProvider.class);
        this.userProvider = ServiceContext.find(IUserProvider.class);
        if (this.repository == null) {
            throw new JeeflowException(WfErrEnum.SPI_NOT_REGISTERED);
        }
        // issues/29：action 权限码映射默认实现——集成方未注册时内置默认（boot3 注解语义归纳），
        // 特殊集成方可覆盖注册
        if (ServiceContext.find(com.mldong.jeeflow.spi.IActionPermissionProvider.class) == null) {
            ServiceContext.put(com.mldong.jeeflow.spi.IActionPermissionProvider.class.getName(),
                    new com.mldong.jeeflow.spi.DefaultActionPermissionProvider());
        }
        return this;
    }

    @Override
    public IProcessRepository getRepository() {
        return repository;
    }

    // ═══ 启动流程 ═══

    @Override
    public ProcessInstance startProcessInstanceById(Long defineId, String operator, FlowData args) {
        return startProcessInstanceById(defineId, operator, args, null, null);
    }

    @Override
    public ProcessInstance startProcessInstanceById(Long defineId, String operator, FlowData args,
                                                     Long parentId, String parentNodeName) {
        return inTx(() -> {
            // 1. 查流程定义
            ProcessInstance.ProcessDefine define = repository.findDefineById(defineId);
            if (define == null) {
                throw new JeeflowException(WfErrEnum.NOT_FOUND_PROCESS_DEFINE);
            }
            // 2. 解析流程模型
            ProcessModel model = ModelParser.parse(define.getContent());
            // 3. 追加用户信息
            FlowUtil.addUserInfoToArgs(operator, args, userProvider);
            FlowUtil.addAutoGenTitle(define.getDisplayName(), args);
            // 4. 创建聚合根
            ProcessInstance instance = ProcessInstance.create(define, operator, args, parentId, parentNodeName);
            // 5. 计算到期时间
            String expireTime = model.getExpireTime();
            if (StringUtils.isNotEmpty(expireTime)) {
                instance.setExpireTime(FlowUtil.processTime(expireTime, args));
            }
            // 6. 持久化
            repository.saveInstance(instance);
            // 7. 处理抄送（issues/47 E19：f_ccActors 发起时 / tf_ccActors 办理时统一走 handleCcActors）
            handleCcActors(instance.getInstanceId(), operator, args.get(FlowConst.CC_ACTORS_START));
            // 8. 构建 Execution 并执行开始节点
            Execution exec = buildExecution(model, instance, args, operator);
            model.getStart().execute(exec);
            // 9. 持久化产生的任务，并更新实例
            //    TASK_START 事件须在 saveTask 落库（分配 taskId）之后 fire——对齐 spec §4.4
            //    「任务落库后逐任务 fire，监听器可按 taskId 反查」与 Go 参考实现。
            //    CreateTaskHandler 在 handler 阶段（taskId 尚未生成）不再 fire，避免 sourceId=null
            //    导致监听器（onEvent 的 sourceId==null 守卫）漏发「新待办」TODO 消息。
            String surrogateProcessName = SurrogateInterceptor.resolveProcessName(exec);
            for (ProcessTask task : exec.getProcessTaskList()) {
                saveNewTask(task, surrogateProcessName);
            }
            // 记录类节点历史行落库（§6.2 第 1 条）：发起即命中 custom 的流（start→custom→…）走的是
            // 这条腿而不是 persistTasks，两条腿都要有 INSERT，漏一条就是"同一条形状在半数路径上又不落库"。
            // 只 saveTask、不 fire 码 3，判据见 saveHistoryTasks 的方法注释。
            saveHistoryTasks(exec);
            repository.updateInstance(instance);
            // 实例终态事件（码 2）：发起即办结的短流（start→end、decision 直达结束）与
            // 子流程父实例都从这一支落库后播——顺序判据同 persistTasks 的收口。
            flushInstanceEndEvents(exec);
            return instance;
        });
    }

    // ═══ 执行任务 ═══

    @Override
    public List<ProcessTask> executeProcessTask(Long taskId, String operator, FlowData args) {
        return inTxFor(taskId, () -> {
            ProcessTask snapshot = repository.findTaskById(taskId);
            if (snapshot != null && snapshot.isDoing() && ReapplySupport.reapplyOf(snapshot) != null) {
                return executeReapply(snapshot, operator, args);
            }
            Execution exec = prepareExecution(taskId, operator, args);
            if (exec == null) return Collections.emptyList();
            ProcessModel model = exec.getProcessModel();
            NodeModel node = model.getNode(exec.getProcessTask().getTaskName());
            if (node != null) {
                node.execute(exec);
            }
            // issues/47 E19：办理时抄送（tf_ccActors）创建 cc 实例（对齐发起时 f_ccActors 语义）
            //
            // 抄送腿排在 persistTasks **之后**：实例行的 updateInstance（以及排在它之后的码 2
            // flush，见 flushInstanceEndEvents）得先走完。两支各自"落在自己那次写库之后"，
            // 跨事件的相对次序与本轮修复前逐字一致（recorder 主用例钉的 1,3,5,2,4,4）。
            persistTasks(exec);
            handleCcActors(exec.getProcessInstance().getInstanceId(), operator, args.get(FlowConst.CC_ACTORS));
            return exec.getProcessTaskList();
        });
    }

    /** 抄送处理（issues/47 E19 抽取）：f_ccActors/tf_ccActors 统一走此逻辑 */
    private void handleCcActors(Long instanceId, String operator, Object ccUserIds) {
        if (ccUserIds == null) return;
        // issues/141 G10「空不创建行」（spec 06 §2.10）＋ issues/142 B 批（spec 06 §2.11 尾注）：
        // 逗号串与数组两形共用同一条归一腿，且与任务侧（addCandidate/surrogate/transfer/
        // nextNodeOperator）用的是<b>同一枚单点</b> {@code StringUtils.normalizeActorArg}——
        // 空串/纯空白/数组里的空元素一律丢弃，丢完为空 ⇒ 不建 cc 行、也不 fire 码 4。
        // 旧形状两处病："".split(",") 得到一个空元素 ⇒ 落一条 actor_id='' 的行（129 那族"空归属值"
        // 的病根）；数组腿 {@code Object::toString} 遇 null 元素直接 NPE。归一腿要求的是丢弃，不是崩。
        // 漏斗这一层之外，两仓写侧还各有一层兜底（两层都挡，硬要求①）。
        List<String> actors = StringUtils.normalizeActorArg(ccUserIds);
        if (!actors.isEmpty()) {
            // issues/141 G2 写侧判重＝幂等空操作（spec 06 §4）：同一 (实例, 被抄送人) 已有 cc 行时
            // 跳过——不新增行、不重置未读、不更新原行时间；新建子集才拿去 fire。
            List<String> created = repository.createCcInstanceIfAbsent(instanceId, operator,
                    actors.toArray(new String[0]));
            // CC_CREATE（issues/102 新增，六语言统一；spec §11.3 码 4）：逐抄送人 fire，与
            // createCcInstance 逐行 INSERT 的粒度一一对应（对齐 PHP 参考实现 v1.3.8）。
            // sourceId=instanceId，ccActorId=抄送人 id（直传事件体，监听器免反查 cc 表）。
            // fire 在 runInTx 事务内（与 PHP/Java 同事务一致）；监听器侧用 afterCommit 延迟反查
            // （见集成层监听器注释）。收口在 ProcessPublisher.notifyCcCreate——门面手动
            // createCCInstance 与本方法是同一条腿（spec §11.2 原则 1／§11.7）。
            // 入参＝实际新建的子集而不是原始 ccArr（issues/141 G2）：spec §11.2 原则 1「码=事实」
            // ⇒ 重复抄送没发生"创建"，就不该发这个事件；子集为空整支不 fire（不空转、也不照旧全量 fire）。
            if (!created.isEmpty()) {
                ProcessPublisher.notifyCcCreate(instanceId, created.toArray(new String[0]));
            }
        }
    }

    @Override
    public List<ProcessTask> executeAndJumpTask(Long taskId, String operator, FlowData args, String nodeName) {
        return inTxFor(taskId, () -> {
            ProcessTask snapshot = repository.findTaskById(taskId);
            rejectMarkedApplicant(snapshot, "跳转");
            if (StringUtils.isEmpty(nodeName)) {
                // issues/243 甲案：血缘守卫排在 prepareExecution 之前——prepareExecution 内部
                // completeTask 会把当前行办结，内存仓储无回滚，守卫后置 ⇒ 报错那次待办凭空消失。
                checkRollbackPossible(snapshot);
            } else {
                // issues/243 §8-2：命名跳转的「找不到节点模型」同一族缺口，同规矩前置。
                checkJumpTargetExists(snapshot, nodeName);
            }
            Execution exec = prepareExecution(taskId, operator, args);
            if (exec == null) return Collections.emptyList();
            ProcessModel model = exec.getProcessModel();
            if (StringUtils.isEmpty(nodeName)) {
                // 血缘版 rejectTask 要么建出复活行、要么抛 20010007/20010008，不再返回 null
                exec.addTask(exec.getProcessInstance().rejectTask(model, exec.getProcessTask()));
            } else {
                NodeModel targetNode = model.getNode(nodeName);
                if (targetNode == null) {
                    throw new JeeflowException("根据节点名称[" + nodeName + "]无法找到节点模型");
                }
                if (targetNode instanceof TaskModel) {
                    TaskModel tm = (TaskModel) targetNode;
                    if (FlowUtil.isFirstTaskName(model, tm.getName())) {
                        tm.setAssignee(exec.getProcessInstance().getOperator());
                    }
                }
                TransitionModel tm = new TransitionModel();
                tm.setTarget(targetNode);
                tm.setEnabled(true);
                tm.execute(exec);
            }
            persistTasks(exec);
            return exec.getProcessTaskList();
        });
    }

    @Override
    public List<ProcessTask> executeAndJumpToEnd(Long taskId, String operator, FlowData args) {
        return inTxFor(taskId, () -> {
            rejectMarkedApplicant(repository.findTaskById(taskId), "拒绝");
            Execution exec = prepareExecution(taskId, operator, args);
            if (exec == null) return Collections.emptyList();
            ProcessModel model = exec.getProcessModel();
            // 拒绝结束：本节点未完成的加签任务一并废弃，不留孤儿待办（规范 04 · 后加签）
            AddSignSupport.abandonDoingOnNode(model, exec.getProcessInstance(),
                    exec.getProcessTask().getTaskName(), operator);
            for (EndModel end : model.getModels(EndModel.class)) {
                TransitionModel tm = new TransitionModel();
                tm.setTarget(end);
                tm.setEnabled(true);
                tm.execute(exec);
            }
            persistTasks(exec);
            return exec.getProcessTaskList();
        });
    }

    @Override
    public List<ProcessTask> executeAndJumpToFirstTaskNode(Long taskId, String operator, FlowData args) {
        return inTxFor(taskId, () -> {
            ProcessTask snapshot = repository.findTaskById(taskId);
            rejectMarkedApplicant(snapshot, "再次退回");
            ProcessModel model = null;
            TaskModel sourceNode = null;
            if (snapshot != null && snapshot.isDoing()) {
                ProcessInstance instance = repository.findInstanceById(snapshot.getProcessInstanceId());
                if (instance != null) {
                    ProcessInstance.ProcessDefine define = repository.findDefineById(instance.getDefineId());
                    if (define != null) {
                        model = ModelParser.parse(define.getContent());
                        NodeModel node = model.getNode(snapshot.getTaskName());
                        if (node instanceof TaskModel) {
                            sourceNode = (TaskModel) node;
                        }
                    }
                }
            }
            // 校验放在任何状态变更之前：非法配置/不支持的形状不得先完成任务、先发通知
            boolean direct = sourceNode != null
                    && ReapplyModeEnum.RETURN_TO_SOURCE.equals(sourceNode.getReapplyMode());
            if (direct) {
                validateReturnToSource(model, sourceNode);
            }
            Execution exec = prepareExecution(taskId, operator, args);
            if (exec == null) return Collections.emptyList();
            model = exec.getProcessModel();
            // 退回发起人：本节点未完成的加签任务一并废弃，不留孤儿待办（规范 04 · 后加签）
            AddSignSupport.abandonDoingOnNode(model, exec.getProcessInstance(),
                    exec.getProcessTask().getTaskName(), operator);
            if (direct) {
                createReturnToSourceApplication(exec, sourceNode, snapshot);
            } else {
                for (TransitionModel tm : model.getStart().getOutputs()) {
                    tm.setEnabled(true);
                    if (tm.getTarget() instanceof TaskModel) {
                        ((TaskModel) tm.getTarget()).setAssignee(exec.getProcessInstance().getOperator());
                    }
                    tm.execute(exec);
                }
            }
            persistTasks(exec);
            return exec.getProcessTaskList();
        });
    }

    // ═══ 退回修改、重提直返 ═══

    /**
     * 带直返上下文的进行中申请待办只接受 submitType=5（重新提交）。
     * 其他跳转/退回/拒绝入口一律在状态变更之前拒绝，避免绕过来源绑定或丢等待。
     */
    private static void rejectMarkedApplicant(ProcessTask snapshot, String action) {
        if (snapshot != null && snapshot.isDoing() && ReapplySupport.reapplyOf(snapshot) != null) {
            throw new JeeflowException("带重提直返标记的申请待办不允许" + action
                    + "，只接受重新提交(submitType=5)");
        }
    }

    /** 来源节点配置了 RETURN_TO_SOURCE 时的准入校验（不改任何状态） */
    private void validateReturnToSource(ProcessModel model, TaskModel sourceNode) {
        if (ProcessTaskPerformTypeEnum.COUNTERSIGN.equals(sourceNode.getPerformType())) {
            throw new JeeflowException("会签节点[" + sourceNode.getName() + "]不支持退回修改直返");
        }
        if (FlowUtil.isFirstTaskName(model, sourceNode.getName())) {
            throw new JeeflowException("申请节点[" + sourceNode.getName() + "]不能配置重提直返");
        }
        if (firstTaskModel(model) == null) {
            throw new JeeflowException("流程定义缺少申请节点，无法退回发起人");
        }
    }

    /**
     * submitType=6 的直返落点：完成来源任务（prepareExecution 已做）后，在原实例里
     * 给发起人建一条申请待办，并把本次退回绑定写进任务变量；不重放来源出边、不重触发分叉。
     */
    private void createReturnToSourceApplication(Execution exec, TaskModel sourceNode, ProcessTask sourceTask) {
        ProcessInstance instance = exec.getProcessInstance();
        TaskModel applyModel = firstTaskModel(exec.getProcessModel());
        ProcessTask appTask = instance.createTask(applyModel, applyModel.getDisplayName(),
                new ArrayList<>(Collections.singletonList(instance.getOperator())),
                instance.getOperator(), sourceTask.getTaskId(), true);
        ReapplySupport.putScopes(appTask, ReapplySupport.emptyScopes());
        ReapplySupport.putReapply(appTask, ReapplySupport.newReapplyContext(
                ReapplyModeEnum.RETURN_TO_SOURCE.name(), sourceTask.getTaskId(), sourceNode.getName(),
                ReapplySupport.scopesOf(sourceTask)));
        exec.addTask(appTask);
        // 与 CreateTaskHandler 同序：建单拦截器 → 完成节点的 post 拦截器 → 落库/通知（persistTasks）
        runCreateInterceptors(exec, applyModel);
        NodeModel completed = exec.getProcessModel().getNode(sourceNode.getName());
        if (completed != null) {
            completed.executePostInterceptors(exec);
        }
    }

    /** 全局建单拦截器（ServiceContext 注册的 FlowInterceptor），对齐 CreateTaskHandler */
    private static void runCreateInterceptors(Execution exec, NodeModel node) {
        exec.setNodeModel(node);
        List<FlowInterceptor> interceptors = ServiceContext.findList(FlowInterceptor.class);
        if (interceptors != null) {
            for (FlowInterceptor interceptor : interceptors) {
                interceptor.intercept(exec);
            }
        }
    }

    /**
     * submitType=5 且申请待办带直返上下文：校验来源后，在来源节点直接建新待办。
     * 不执行申请节点出边（不重算来源前条件、不重触发分叉），也不执行来源节点出边。
     */
    private List<ProcessTask> executeReapply(ProcessTask applyTask, String operator, FlowData args) {
        args = ReapplySupport.stripReserved(args);
        Integer submitType = args.getInt(FlowConst.SUBMIT_TYPE);
        if (submitType == null) {
            submitType = ProcessSubmitTypeEnum.RE_APPLY.getCode();
        }
        if (!ProcessSubmitTypeEnum.RE_APPLY.getCode().equals(submitType)) {
            throw new JeeflowException("带重提直返标记的申请待办只接受重新提交(submitType=5)");
        }
        ProcessInstance instance = repository.findInstanceById(applyTask.getProcessInstanceId());
        if (instance == null) {
            throw new JeeflowException("流程实例不存在");
        }
        ProcessInstance.ProcessDefine define = repository.findDefineById(instance.getDefineId());
        if (define == null) {
            throw new JeeflowException(WfErrEnum.NOT_FOUND_PROCESS_DEFINE);
        }
        ProcessModel model = ModelParser.parse(define.getContent());
        Map<String, Object> ctx = ReapplySupport.reapplyOf(applyTask);
        Long sourceTaskId = ReapplySupport.requireSourceTaskId(ctx);
        ProcessTask source = null;
        for (ProcessTask t : instance.getTasks()) {
            if (sourceTaskId.equals(t.getTaskId())) {
                source = t;
                break;
            }
        }
        if (source == null || !source.isFinished()) {
            throw new JeeflowException("重提直返来源任务无效或未完成");
        }
        NodeModel sourceModel = model.getNode(source.getTaskName());
        if (!(sourceModel instanceof TaskModel)
                || !ReapplyModeEnum.RETURN_TO_SOURCE.equals(((TaskModel) sourceModel).getReapplyMode())) {
            throw new JeeflowException("重提直返来源节点配置已变更，拒绝执行");
        }
        if (!FlowUtil.isFirstTaskName(model, applyTask.getTaskName())) {
            throw new JeeflowException("重提直返必须从申请待办发起");
        }
        TaskModel target = (TaskModel) sourceModel;

        Execution exec = prepareExecution(applyTask.getTaskId(), operator, args);
        if (exec == null) return Collections.emptyList();
        // 遗留的下一节点指定人参数不得影响本次建单（历史记录不删）
        exec.getArgs().remove(FlowConst.NEXT_NODE_OPERATOR);
        List<String> actors = CreateTaskHandler.resolveActors(target, model, exec);
        if (actors == null || actors.isEmpty()) {
            throw new JeeflowException("目标节点[" + target.getName() + "]没有可用参与者，重提已回滚");
        }
        List<Map<String, Object>> branch = ReapplySupport.branchOf(ctx);
        ProcessTask newTask = instance.createTask(target, target.getDisplayName(), actors, operator,
                applyTask.getTaskId(), false);
        ReapplySupport.putScopes(newTask, branch);
        exec.addTask(newTask);
        // 与 CreateTaskHandler 同序：建单拦截器 → 申请节点 post 拦截器 → 落库/通知（persistTasks）
        runCreateInterceptors(exec, target);
        NodeModel applyNode = exec.getProcessModel().getNode(applyTask.getTaskName());
        if (applyNode != null) {
            applyNode.executePostInterceptors(exec);
        }
        persistTasks(exec);
        return exec.getProcessTaskList();
    }

    private static TaskModel firstTaskModel(ProcessModel model) {
        for (TaskModel t : model.getTasks()) {
            if (FlowUtil.isFirstTaskName(model, t.getName())) {
                return t;
            }
        }
        return null;
    }

    // ═══ 内部方法 ═══

    /**
     * 退回上一步（{@code nodeName} 空 ⇒ 血缘腿）的写前置守卫（issues/243 甲案）。
     *
     * <p>只做一件事：把 {@code ProcessInstance.checkRejectable} 的两道判据（20010007／20010008）
     * 提到任何状态变更之前。<b>不</b>在这里报"任务不在进行中／无权办理"，也不在这里发明第二套文案——
     * 那几档留给 {@code prepareExecution} 的既有固定错（它们在 completeTask 之前，本来就零写入）；
     * 取不到实例或定义时同样一律放行，由 {@code prepareExecution} 返回 null 走它的原形状。</p>
     */
    private void checkRollbackPossible(ProcessTask current) {
        if (current == null || !current.isDoing()) return;
        ProcessInstance instance = repository.findInstanceById(current.getProcessInstanceId());
        if (instance == null) return;
        ProcessInstance.ProcessDefine define = repository.findDefineById(instance.getDefineId());
        if (define == null) return;
        instance.checkRejectable(ModelParser.parse(define.getContent()), current);
    }

    /**
     * 命名跳转（{@code nodeName} 非空那一支）的节点存在性前置守卫（issues/243 §8-2）。
     *
     * <p>与 {@link #checkRollbackPossible} 同族同规矩：从前这句"根据节点名称[X]无法找到节点模型"
     * 是在 {@code prepareExecution}（内部 {@code completeTask} 已把当前行办结）之后才抛的，
     * 内存仓储无回滚 ⇒ 名字写错一次就把自己的待办弄丢。真库有事务整笔回滚，观察不到差别，
     * 所以这一档同样只有内存仓储照得出来（L2-55 钉的"报错后原任务仍 DOING"靠事务兜住）。</p>
     *
     * <p>只读；取不到任务/实例/定义时一律放行给 {@code prepareExecution} 报它的既有固定错，
     * 也不在这里发明第二种文案——抛的还是原来那一句。</p>
     */
    private void checkJumpTargetExists(ProcessTask current, String nodeName) {
        if (current == null || !current.isDoing()) return;
        ProcessInstance instance = repository.findInstanceById(current.getProcessInstanceId());
        if (instance == null) return;
        ProcessInstance.ProcessDefine define = repository.findDefineById(instance.getDefineId());
        if (define == null) return;
        if (ModelParser.parse(define.getContent()).getNode(nodeName) == null) {
            throw new JeeflowException("根据节点名称[" + nodeName + "]无法找到节点模型");
        }
    }

    /**
     * 已办撤回（issues/246，规范 04 · 已办撤回）：办理人撤回自己已办结的那一手，同实例内复活重办。
     *
     * <p>判据全部排在任何状态变更之前（报错即零写入——与后加签写入口/退回守卫同一规矩）：
     * 行存在且已办结 → 参与者/特权 → 实例 DOING → 节点开关 → 窗口（下游无任何 FINISHED）。
     * 落库顺序：废弃行 updateTask → 复活行 saveTask（拿 taskId，全量写参与者行）→ 原行盖
     * {@code tf_recallHistory}（recalledTaskId 指向复活行）updateTask → 实例 updateTime →
     * fire 码 10 TASK_RECALL（sourceId＝被撤回的旧行，复活行自己的待办另走码 3）。</p>
     */
    @Override
    public ProcessTask recallTask(Long taskId, String operator) {
        ProcessTask target = repository.findTaskById(taskId);
        if (target == null || !target.isFinished()) {
            throw new JeeflowException(RecallSupport.MSG_NOT_FINISHED);
        }
        boolean privileged = FlowConst.AUTO_ID.equalsIgnoreCase(operator)
                || FlowConst.ADMIN_ID.equalsIgnoreCase(operator);
        if (!privileged && (target.getActorIds() == null || !target.getActorIds().contains(operator))) {
            throw new JeeflowException(RecallSupport.MSG_NO_PERMISSION);
        }
        ProcessInstance instance = repository.findInstanceById(target.getProcessInstanceId());
        if (instance == null) {
            throw new JeeflowException("流程实例不存在");
        }
        if (!com.mldong.jeeflow.enums.ProcessInstanceStateEnum.DOING.getCode().equals(instance.getState())) {
            throw new JeeflowException(RecallSupport.MSG_INSTANCE_NOT_DOING);
        }
        ProcessInstance.ProcessDefine define = repository.findDefineById(instance.getDefineId());
        ProcessModel model = ModelParser.parse(define.getContent());
        NodeModel node = model.getNode(target.getTaskName());
        if (!(node instanceof TaskModel) || !((TaskModel) node).isAllowRecall()) {
            throw new JeeflowException(String.format(RecallSupport.MSG_SWITCH_OFF, target.getTaskName()));
        }
        List<ProcessTask> doingDescendants = RecallSupport.validateWindow(instance.getTasks(), target);
        // ── 判据全过，以下才允许写 ──
        boolean isFirstTaskNode = FlowUtil.isFirstTaskName(model, target.getTaskName());
        ProcessTask resurrected = instance.recallTask((TaskModel) node, target, operator,
                doingDescendants, isFirstTaskNode);
        for (ProcessTask t : doingDescendants) {
            repository.updateTask(t);
        }
        repository.saveTask(resurrected);
        // 复活行自己的待办另走码 3（规范 04 · 已办撤回；issues/247 python 对齐轮纠：首版漏 fire，
        // go v1.8.61/python 均按 spec 走码 3，Java 参考实现就此对齐）
        notifyTaskStart(resurrected);
        RecallSupport.appendRecallHistory(target, operator, resurrected.getTaskId());
        repository.updateTask(target);
        repository.updateInstance(instance);
        // 码 10 TASK_RECALL：撤回事实落库之后 fire 一次（复活行的待办另走码 3，见 saveNewTask）
        ProcessPublisher.notify(ProcessEvent.builder()
                .eventType(com.mldong.jeeflow.enums.ProcessEventTypeEnum.TASK_RECALL)
                .sourceId(target.getTaskId())
                .data(FlowData.create()
                        .set(ProcessPublisher.KEY_INSTANCE_ID, instance.getInstanceId())
                        .set(ProcessPublisher.KEY_TASK_ID, target.getTaskId())
                        .set(ProcessPublisher.KEY_OPERATOR, operator))
                .build());
        return resurrected;
    }

    private Execution prepareExecution(Long taskId, String operator, FlowData args) {
        args = ReapplySupport.stripReserved(args);
        ProcessTask task = repository.findTaskById(taskId);
        if (task == null || !task.isDoing()) {
            throw new JeeflowException(WfErrEnum.NOT_FOUND_DOING_PROCESS_TASK);
        }
        if (!task.isAllowed(operator)) {
            throw new JeeflowException(WfErrEnum.NOT_ALLOWED_EXECUTE);
        }

        ProcessInstance instance = repository.findInstanceById(task.getProcessInstanceId());
        if (instance == null) return null;

        ProcessInstance.ProcessDefine define = repository.findDefineById(instance.getDefineId());
        if (define == null) return null;

        ProcessModel model = ModelParser.parse(define.getContent());

        // issues/26：办理提交的 f_ 字段按任务节点字段权限过滤（只读/隐藏不入变量）——
        // 被拒值无法经流程变量落到下游节点写入，上游只读声明不可被绕过
        args = FlowUtil.filterFieldByPerm(args, model, task.getTaskName());

        // 后加签写入口校验（规范 04 · 后加签）：必须排在 completeTask 之前——内存仓储无回滚，
        // 报错时不得留下已完成的任务行（"报错且无写入"是这条链的硬要求）。
        AddSignSupport.validateSubmission(model, task, args, operator);

        // issues/244 ①：当前节点 pre/post 拦截器的类名解析必须排在 completeTask 之前。
        // 改前顺序是 completeTask → updateTask → node.execute，而 NodeModel.execute 的 post 拦截器
        // 排在流转之后；类名写错时任务行已被静默办结、后继任务又没落库（persistTasks 在
        // node.execute 之后）⇒ 内存仓储上留下「实例 DOING(10) 却零待办」。真库有事务整笔回滚，
        // 观察不到差别。这里只做**只读**校验（不实例化、不跑 intercept，理由见
        // NodeModel#validateDeclaredInterceptors 的注释），失败抛的还是执行期那一句原文案。
        // 能力边界：只覆盖 类名解析不出来 / 没实现 FlowInterceptor / 没有无参构造器 三类，
        // 构造器自己抛异常仍会在写之后炸（本轮不管）。
        NodeModel currentNode = model.getNode(task.getTaskName());
        if (currentNode != null) {
            currentNode.validateDeclaredInterceptors(model);
        }

        // 完成任务——聚合根内部修改了 instance 中的 task 状态
        instance.completeTask(taskId, operator, args);

        // 将 instance 中的已完成 task 状态同步到 task 对象，并持久化
        ProcessTask completedInInstance = null;
        for (ProcessTask t : instance.getTasks()) {
            if (taskId.equals(t.getTaskId())) {
                completedInInstance = t;
                break;
            }
        }
        if (completedInInstance != null) {
            task.setTaskState(completedInInstance.getTaskState());
            task.setActorId(completedInInstance.getActorId());
            task.setFinishTime(completedInInstance.getFinishTime());
            task.setVariables(completedInInstance.getVariables());
            task.setUpdateTime(completedInInstance.getUpdateTime());
            task.setUpdateUser(completedInInstance.getUpdateUser());
        }
        repository.updateTask(task);

        // 任务被办掉 / 被退回（spec §11.3 码 5 TASK_COMPLETE / 码 6 TASK_REJECT，issues/132 新增）：
        // 紧跟上面这次 updateTask（任务行 state 落库）之后 fire，§11.2 原则 3 的"落库之后"即此。
        // 两条互斥（§11.3 码 6「同一动作走 reject 就不再 fire complete」）：判据取载荷 submitType，
        // 不为"拒绝/退回上一步/退发起人/会签否决"各开一号（§11.2 原则 2）。
        notifyTaskFinished(instance, task, operator, args);

        // 合并流程变量
        FlowData mergedArgs = FlowData.create();
        mergedArgs.setAll(instance.getVariables());
        if (args != null) mergedArgs.setAll(args);

        Execution exec = buildExecution(model, instance, mergedArgs, operator);
        exec.setProcessTask(task);
        exec.setProcessTaskId(taskId);
        // 加签人取"本次请求"的值显式带上，不让处理器读合并后的 args（判据与病因见
        // Execution#addSignOperators 的注释——实例变量会把上一轮的 tf_ 键复活）。
        exec.setAddSignOperators(AddSignSupport.operators(args));
        return exec;
    }

    private Execution buildExecution(ProcessModel model, ProcessInstance instance, FlowData args, String operator) {
        Execution exec = new Execution();
        exec.setProcessModel(model);
        exec.setProcessInstance(instance);
        exec.setProcessInstanceId(instance.getInstanceId());
        exec.setEngine(this);
        exec.setArgs(args);
        exec.setOperator(operator);
        return exec;
    }

    private void persistTasks(Execution exec) {
        // 委托查询的流程名逐次 execution 解析一次后复用（对齐 Go/Node）：回落路径要读定义行
        // （含 content BLOB），不能逐任务重复读
        String surrogateProcessName = SurrogateInterceptor.resolveProcessName(exec);
        for (ProcessTask task : exec.getProcessTaskList()) {
            saveNewTask(task, surrogateProcessName);
        }
        saveHistoryTasks(exec);
        if (exec.getProcessTask() != null && exec.getProcessTask().getTaskId() != null) {
            repository.updateTask(exec.getProcessTask());
        }
        repository.updateInstance(exec.getProcessInstance());
        // 实例终态事件（码 2）：紧跟上面那次 updateInstance —— 行的 state 已落库才允许播
        flushInstanceEndEvents(exec);
    }

    /**
     * 记录类（custom）节点的历史行落库唯一收口（issues/142 · spec 02 §6.2 第 1 条，
     * owner 2026-09-30 拍：「落历史行＝{@code wf_process_task} 里查得到那一行，
     * 只在聚合内存对象里 append 一条不算做到」）。
     *
     * <p><b>缺陷原形（本方法存在的理由）</b>：{@code CustomModel.exec} 过去把
     * {@code ProcessInstance.createHistoryTask} 的返回值<b>丢弃</b>，行只进了
     * {@code instance.tasks}；而这条腿过去只遍历 {@code exec.getProcessTaskList()}，
     * {@code JdbcProcessRepository.updateInstance} 的级联又只对 {@code taskId != null} 的行发
     * {@code UPDATE}，{@code ProcessTask.create} 从不赋 taskId ⇒ 那条 {@code task_state=20}
     * 的行永远没有 INSERT。自家 {@code JeeflowFullTest#test08CustomNode} 对历史行零断言，
     * 所以测试照不出来。</p>
     *
     * <p><b>为什么这里只 {@code saveTask}、不走 {@link #saveNewTask}</b>（"落库"与"fire 码 3"
     * 的解耦点）：{@code saveNewTask} 是 {@code saveTask} + {@code notifyTaskStart} 的成对腿，
     * 码 3 {@code PROCESS_TASK_START} 表达的事实是"<b>新待办产生</b>"（spec §11.3）。
     * 记录类节点按 §6.1 定性"本来就不该有参与者，也不该有待办"，它的行生来是已完成态 ⇒
     * 落库了也<b>不许</b> fire 码 3，否则待办列表/站内信凭空多一条办不动的单
     * （§6.1 禁止形状 ②"伪造一条他不该收到的待办"的另一实现形态）。
     * 委托并入（issues/116）同样跳过——那是给待办收单人用的，历史行不是待办。
     * 对照实现：python 本轮那条腿 {@code engine.py#_exec_custom_node} 也是
     * {@code save_task} 而不发码 3。</p>
     *
     * <p>排位（{@code updateInstance} 之前）：历史行的 INSERT 要先于实例行的写入与码 2 flush，
     * 这样"流程已办结"的监听器按 instanceId 反查任务流水时，那条留痕一定查得到
     * （§11.2 原则 3 的"写在前、播在后"）。</p>
     */
    private void saveHistoryTasks(Execution exec) {
        for (ProcessTask task : exec.drainHistoryTasks()) {
            repository.saveTask(task);
        }
    }

    /**
     * 实例终态事件（码 2 {@code PROCESS_INSTANCE_END}）的统一收口——
     * spec §11.2 原则 3「只在落库之后 fire」／§11.3 码 2「实例 state 更新为
     * 20/30/40/45/50/99 之一并落库之后」／08-compliance 场景 32。
     *
     * <p>处理器（{@code EndProcessHandler}）只往 execution 挂 {@link PendingInstanceEnd}，
     * 本方法在实例行<b>真正落库之后</b>把它们播出去。两条路径都要覆盖，缺一即丢事件：</p>
     * <ul>
     *   <li><b>正常路径</b>：登记的就是本次 execution 的实例，行已由调用方那次
     *       {@code repository.updateInstance}（或发起路径的 {@code saveInstance}+{@code updateInstance}）
     *       写好 ⇒ 这里只补播，不重复写；</li>
     *   <li><b>子流程父实例路径</b>：子实例办结时处理器在<b>父实例</b>的 execution 上继续流转，
     *       父实例<b>不走</b>子流程这次的 {@code updateInstance}（历史缺口：父实例终态只改内存，
     *       那一行永远停在 10）⇒ 这里按登记带的聚合根补一次 {@code updateInstance}，再播。
     *       先写后播的顺序对父实例同样成立。</li>
     * </ul>
     *
     * <p>载荷 state 取登记时刻的快照整数（＝刚落库那一行的值），不重读聚合根——登记之后
     * 流转还可能继续触碰该对象，重读会播出一个没写过的中间值。</p>
     *
     * <p>本栈可到达的终态档位：码 2 只由结束节点产生（办结 20／拒绝 45，{@code EndProcessHandler}
     * 是 {@code finish()}/{@code reject()} 的唯一调用者）。其余档位各自的归宿——
     * 30 撤回走门面 {@code withdraw}（先 {@code updateInstance} 后 fire 码 8，写后播已满足；
     * "撤回这一档是否还要另发一支码 2"是规范解释问题，本轮按现状不扩火——§11.4 第 3 条
     * 明写 {@code updateInstance} 的级联落库不构成独立事实，扩之前需 spec 侧先定）；
     * 40 终止、50 挂起、99 废弃在 main 源<b>没有任何生产者</b>（{@code ProcessInstance#interrupt}
     * ／{@code pending}／{@code abandonTask} 生产零调用者，见
     * {@link com.mldong.jeeflow.enums.ProcessEventTypeEnum#INSTANCE_TERMINATED} 的 grep 证据），
     * 对应门格按 unreachable 记账；将来出现写这些档位的收口点时，须在该次落库后补 fire，
     * 不得在集成层主动补发（spec §11.1）。</p>
     *
     * <p>副作用（顺带收口）：修复前码 2 在 {@code node.execute} 里就地 fire，流转后续步骤抛异常
     * 导致事务回滚时事件已经漏出去；现在事件排在写库之后，回滚的那次不再播。</p>
     */
    private void flushInstanceEndEvents(Execution exec) {
        List<PendingInstanceEnd> pending = exec.drainPendingEnds();
        if (pending.isEmpty()) {
            return;
        }
        for (PendingInstanceEnd end : pending) {
            ProcessInstance instance = end.getInstance();
            boolean ownInstance = instance == null
                    || (exec.getProcessInstanceId() != null
                        && exec.getProcessInstanceId().equals(end.getInstanceId()));
            if (!ownInstance) {
                // 父实例（或更上层）被这一支流转连带办结：它的行不在本次 updateInstance 范围内，补写
                repository.updateInstance(instance);
            }
            ProcessPublisher.notifyInstanceEnd(end.getInstanceId(), end.getState());
        }
    }

    /**
     * 新任务落库唯一收口（发起 / 办理推进 / 串行会签的每一步推进 / 跳转(JUMP) / 回退(ROLLBACK)
     * 都走这里——契约 1「覆盖全部建任务路径」，只挂发起一处就会漏掉流转中产生的新单）。
     *
     * <p>落库前先应用生效委托（issues/116）：被委托人**并入参与者集合本身**，
     * 再由 {@code saveTask} 随任务一起全量写入 {@code wf_process_task_actor}。
     * 顺序不能反——此刻 taskId 尚未分配，走「事后 addTaskActor 补写」会打在空 id 上静默无效。
     * 开关：{@code Configuration#surrogateAutoApply(false)}（默认开启）；
     * 未配置 {@code IProcessExtRepository} 时静默跳过，不打断建单。</p>
     *
     * @param processName 委托查询用的流程名，由调用方经
     *                    {@link SurrogateInterceptor#resolveProcessName} 解析（契约 1.1：
     *                    模型 name 优先、缺失回落 {@code wf_process_define.name}）后传入
     */
    private void saveNewTask(ProcessTask task, String processName) {
        if (config == null || config.isSurrogateAutoApply()) {
            surrogateApplier.apply(task, processName, LocalDateTime.now());
        }
        repository.saveTask(task);
        // 分支根任务建单时自身 ID 尚未生成，落库后回填 _branchCtx.root；JDBC 仓储在 INSERT 时
        // 已序列化变量，回填必须补一次 UPDATE 才落盘
        if (ReapplySupport.fillRootSelf(task)) {
            repository.updateTask(task);
        }
        // TASK_START 在落库（分配 taskId）后 fire，见 start 内注释与 spec §4.4
        notifyTaskStart(task);
    }

    /**
     * fire「任务开始」事件（TASK_START / 新待办，spec §11.3 码 3）。
     *
     * <p>引擎契约（spec §4.4 / 规范 11 §11.3 触发时机列）：事件在任务行<b>落库之后</b>触发，事件
     * {@code sourceId = taskId} 必须可被监听器 {@code findTaskById} 反查。故本方法只在
     * {@code saveTask}（分配 taskId）之后调用——{@link CreateTaskHandler} 在 handler 阶段
     * 不再自行 fire（那时 taskId 尚为 null，监听器 sourceId==null 守卫会漏发）。</p>
     *
     * <p>直传载荷键按 §11.3 码 3 补齐：{@code instanceId} / {@code taskId} / {@code actors}
     * （actors 取落库后的参与者行集合，与 {@code saveTask} 写入的 {@code wf_process_task_actor}
     * 同源；委托自动生效 issues/116 已在 saveTask 前并入集合，故这里读到的就是最终收单人）。</p>
     */
    private void notifyTaskStart(ProcessTask task) {
        if (task == null || task.getTaskId() == null) {
            return;
        }
        List<String> actors = repository.findTaskActors(task.getTaskId());
        if (actors == null || actors.isEmpty()) {
            actors = task.getActorIds();
        }
        ProcessPublisher.notify(ProcessEvent.builder()
                .eventType(ProcessEventTypeEnum.PROCESS_TASK_START)
                .sourceId(task.getTaskId())
                .data(FlowData.create()
                        .set(ProcessPublisher.KEY_INSTANCE_ID, task.getProcessInstanceId())
                        .set(ProcessPublisher.KEY_TASK_ID, task.getTaskId())
                        .set(ProcessPublisher.KEY_ACTORS, actors == null ? Collections.<String>emptyList() : actors))
                .build());
    }

    /**
     * fire「任务办结 / 任务退回」事件（spec §11.3 码 5 {@code TASK_COMPLETE} / 码 6
     * {@code TASK_REJECT}，issues/132 新增）。
     *
     * <p>调用点唯一：{@link #prepareExecution} 里 {@code repository.updateTask(task)} 之后
     * ——四个办理入口（常规办理 / 跳转 / 退结束 / 退发起人）都汇过这里，任务行的 {@code state}
     * 就是在这次 updateTask 落库的（契约 1「覆盖全部流转路径」，与 {@link #saveNewTask}
     * 之于 TASK_START 同构）。</p>
     *
     * <p>分派判据＝载荷 {@code submitType}：{@link #REJECT_SUBMIT_TYPES} 命中发 6，其余发 5，
     * 二者互斥；缺省按 {@code AGREE} 处理（与 {@code EndProcessHandler} 读 submitType 的缺省同口径）。
     * 「跳转回退」（submitType=4 且目标在上游）本栈仍归 5——引擎不为此做图回溯，
     * 监听器按载荷 submitType 自判（§11.2 原则 2）。</p>
     */
    private void notifyTaskFinished(ProcessInstance instance, ProcessTask task, String operator, FlowData args) {
        if (task == null || task.getTaskId() == null) {
            return;
        }
        Integer submitType = args == null ? null : args.getInt(FlowConst.SUBMIT_TYPE);
        if (submitType == null) {
            submitType = ProcessSubmitTypeEnum.AGREE.getCode();
        }
        boolean rejected = REJECT_SUBMIT_TYPES.contains(submitType);
        ProcessPublisher.notify(ProcessEvent.builder()
                .eventType(rejected ? ProcessEventTypeEnum.TASK_REJECT : ProcessEventTypeEnum.TASK_COMPLETE)
                .sourceId(task.getTaskId())
                .data(FlowData.create()
                        .set(ProcessPublisher.KEY_INSTANCE_ID,
                                instance != null ? instance.getInstanceId() : task.getProcessInstanceId())
                        .set(ProcessPublisher.KEY_TASK_ID, task.getTaskId())
                        .set(ProcessPublisher.KEY_OPERATOR, operator)
                        .set(ProcessPublisher.KEY_SUBMIT_TYPE, submitType))
                .build());
    }

    /**
     * 退回族 submitType（共用 {@code TASK_REJECT} 一号，载荷再分）：2 拒绝 / 3 退回上一步 /
     * 6 退回发起人 / 20 会签拒绝（软拒绝，issues/91 一票否决走这支）。
     * 0 发起 / 1 同意 / 4 跳转 / 5 重新提交 ⇒ {@code TASK_COMPLETE}。
     */
    private static final List<Integer> REJECT_SUBMIT_TYPES = Collections.unmodifiableList(Arrays.asList(
            ProcessSubmitTypeEnum.REJECT.getCode(),
            ProcessSubmitTypeEnum.ROLLBACK.getCode(),
            ProcessSubmitTypeEnum.ROLLBACK_TO_OPERATOR.getCode(),
            ProcessSubmitTypeEnum.COUNTERSIGN_DISAGREE.getCode()));

    @Override
    public <T> T inTx(ITransactionTemplate.Supplier<T> action) {
        ITransactionTemplate tx = ServiceContext.find(ITransactionTemplate.class);
        if (tx != null) {
            try {
                return tx.execute(action);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        try {
            return action.get();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 同实例串行化：锁在事务外层，保证「重验状态 → 建后继任务」是同一临界区
     * （汇合一次性放行、同一任务重复办理、退回与另一支汇合交错）。
     */
    private <T> T inTxFor(Long taskId, java.util.function.Supplier<T> action) {
        java.util.concurrent.locks.ReentrantLock lock = InstanceLock.forTask(repository, taskId);
        lock.lock();
        try {
            return inTx(action::get);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        return "JeeflowEngineImpl{" + "repository=" + repository + '}';
    }
}
