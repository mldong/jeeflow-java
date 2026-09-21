package com.mldong.jeeflow.core;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.JeeflowException;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.*;
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
            handleCcActors(exec.getProcessInstance().getInstanceId(), operator, args.get(FlowConst.CC_ACTORS));
            persistTasks(exec);
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
            repository.createCcInstance(instanceId, operator, ccArr);
            // CC_CREATE（issues/102 新增，六语言统一）：逐抄送人 fire，与 createCcInstance 逐行
            // INSERT 的粒度一一对应（对齐 PHP 参考实现 v1.3.8）。sourceId=instanceId，
            // ccActorId=抄送人 id（直传事件体，监听器免反查 cc 表）。fire 在 runInTx 事务内
            // （与 PHP/Java 同事务一致）；监听器侧用 afterCommit 延迟反查（见集成层监听器注释）。
            notifyCcCreate(instanceId, ccArr);
        }
    }

    /**
     * 抄送知会事件（CC_CREATE / issues/102）：逐抄送人 fire，{@code ccActorId} 直传事件体。
     * 接收人过滤（trim / 非空 / 纯数字 / 去重）由集成层监听器负责，引擎只按 cc 行粒度 fire。
     */
    private void notifyCcCreate(Long instanceId, String[] ccArr) {
        if (instanceId == null) {
            return;
        }
        for (String ccActorId : ccArr) {
            ProcessPublisher.notify(ProcessEvent.builder()
                    .eventType(ProcessEventTypeEnum.CC_CREATE)
                    .sourceId(instanceId)
                    .ccActorId(ccActorId)
                    .build());
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
     * fire「任务开始」事件（TASK_START / 新待办）。
     *
     * <p>引擎契约（spec §4.4 / Go 参考实现）：事件在任务行**落库之后**触发，事件
     * {@code sourceId = taskId} 必须可被监听器 {@code findTaskById} 反查。故本方法只在
     * {@code saveTask}（分配 taskId）之后调用——{@link CreateTaskHandler} 在 handler 阶段
     * 不再自行 fire（那时 taskId 尚为 null，监听器 sourceId==null 守卫会漏发）。</p>
     */
    private void notifyTaskStart(ProcessTask task) {
        if (task == null || task.getTaskId() == null) {
            return;
        }
        ProcessPublisher.notify(ProcessEvent.builder()
                .eventType(ProcessEventTypeEnum.PROCESS_TASK_START)
                .sourceId(task.getTaskId())
                .build());
    }

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
