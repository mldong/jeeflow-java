package com.mldong.jeeflow.core;

import com.mldong.jeeflow.Configuration;
import com.mldong.jeeflow.JeeflowException;
import com.mldong.jeeflow.domain.FlowData;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.*;
import com.mldong.jeeflow.json.IJsonProvider;
import com.mldong.jeeflow.model.*;
import com.mldong.jeeflow.parser.ModelParser;
import com.mldong.jeeflow.spi.IProcessRepository;
import com.mldong.jeeflow.spi.ITransactionTemplate;
import com.mldong.jeeflow.spi.IUserProvider;
import com.mldong.jeeflow.util.FlowUtil;
import com.mldong.jeeflow.util.StringUtils;

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

    @Override
    public JeeflowEngine configure(Configuration config) {
        this.repository = ServiceContext.find(IProcessRepository.class);
        this.jsonProvider = ServiceContext.find(IJsonProvider.class);
        this.userProvider = ServiceContext.find(IUserProvider.class);
        if (this.repository == null) {
            throw new JeeflowException(WfErrEnum.SPI_NOT_REGISTERED);
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
            // 7. 处理抄送
            Object ccUserIds = args.get(FlowConst.CC_ACTORS_START);
            if (ccUserIds != null) {
                String[] ccArr = null;
                if (ccUserIds instanceof String) {
                    ccArr = ((String) ccUserIds).split(",");
                } else if (ccUserIds instanceof Collection) {
                    Collection<?> coll = (Collection<?>) ccUserIds;
                    ccArr = coll.stream().map(Object::toString).toArray(String[]::new);
                }
                if (ccArr != null && ccArr.length > 0) {
                    repository.createCcInstance(instance.getInstanceId(), operator, ccArr);
                }
            }
            // 8. 构建 Execution 并执行开始节点
            Execution exec = buildExecution(model, instance, args, operator);
            model.getStart().execute(exec);
            // 9. 持久化产生的任务，并更新实例
            for (ProcessTask task : exec.getProcessTaskList()) {
                repository.saveTask(task);
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
            persistTasks(exec);
            return exec.getProcessTaskList();
        });
    }

    @Override
    public List<ProcessTask> executeAndJumpTask(Long taskId, String operator, FlowData args, String nodeName) {
        return runInTx(() -> {
            Execution exec = prepareExecution(taskId, operator, args);
            if (exec == null) return Collections.emptyList();
            ProcessModel model = exec.getProcessModel();
            if (StringUtils.isEmpty(nodeName)) {
                ProcessTask newTask = exec.getProcessInstance().rejectTask(model, exec.getProcessTask());
                if (newTask != null) exec.addTask(newTask);
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
        for (ProcessTask task : exec.getProcessTaskList()) {
            repository.saveTask(task);
        }
        if (exec.getProcessTask() != null && exec.getProcessTask().getTaskId() != null) {
            repository.updateTask(exec.getProcessTask());
        }
        repository.updateInstance(exec.getProcessInstance());
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
