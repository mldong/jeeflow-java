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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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
        return runInTx(() -> {
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
        return runInTx(() -> {
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
        String[] ccArr = null;
        if (ccUserIds instanceof String) {
            ccArr = ((String) ccUserIds).split(",");
        } else if (ccUserIds instanceof Collection) {
            Collection<?> coll = (Collection<?>) ccUserIds;
            ccArr = coll.stream().map(Object::toString).toArray(String[]::new);
        }
        if (ccArr != null && ccArr.length > 0) {
            // issues/141 G10「空不创建行」（spec 06 §2.10）：逗号串与数组两形共用同一条归一腿——
            // 空串/纯空白/数组里的空元素一律丢弃，丢完为空 ⇒ 不建 cc 行、也不 fire 码 4。
            // java 旧形状是 "".split(",") 得到一个空元素 ⇒ 落一条 actor_id='' 的行（129 那族
            // "空归属值"的病根），归一放在漏斗这一层，两仓写侧还各有一层兜底（见下）。
            List<String> actors = StringUtils.normalizeCcActors(ccArr);
            if (actors.isEmpty()) return;
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
        return runInTx(() -> {
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
        return runInTx(() -> {
            Execution exec = prepareExecution(taskId, operator, args);
            if (exec == null) return Collections.emptyList();
            ProcessModel model = exec.getProcessModel();
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
        return runInTx(() -> {
            Execution exec = prepareExecution(taskId, operator, args);
            if (exec == null) return Collections.emptyList();
            ProcessModel model = exec.getProcessModel();
            for (TransitionModel tm : model.getStart().getOutputs()) {
                tm.setEnabled(true);
                if (tm.getTarget() instanceof TaskModel) {
                    ((TaskModel) tm.getTarget()).setAssignee(exec.getProcessInstance().getOperator());
                }
                tm.execute(exec);
            }
            persistTasks(exec);
            return exec.getProcessTaskList();
        });
    }

    // ═══ 内部方法 ═══

    private Execution prepareExecution(Long taskId, String operator, FlowData args) {
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
        if (exec.getProcessTask() != null && exec.getProcessTask().getTaskId() != null) {
            repository.updateTask(exec.getProcessTask());
        }
        repository.updateInstance(exec.getProcessInstance());
        // 实例终态事件（码 2）：紧跟上面那次 updateInstance —— 行的 state 已落库才允许播
        flushInstanceEndEvents(exec);
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

    private <T> T runInTx(ITransactionTemplate.Supplier<T> action) {
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

    @Override
    public String toString() {
        return "JeeflowEngineImpl{" + "repository=" + repository + '}';
    }
}
