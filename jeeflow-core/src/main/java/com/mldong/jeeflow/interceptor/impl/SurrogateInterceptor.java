package com.mldong.jeeflow.interceptor.impl;

import com.mldong.jeeflow.core.Execution;
import com.mldong.jeeflow.core.JeeflowEngine;
import com.mldong.jeeflow.core.ServiceContext;
import com.mldong.jeeflow.domain.ProcessInstance;
import com.mldong.jeeflow.domain.ProcessSurrogate;
import com.mldong.jeeflow.domain.ProcessTask;
import com.mldong.jeeflow.enums.ProcessTaskStateEnum;
import com.mldong.jeeflow.interceptor.FlowInterceptor;
import com.mldong.jeeflow.model.ProcessModel;
import com.mldong.jeeflow.spi.IProcessExtRepository;
import com.mldong.jeeflow.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 委托代理拦截器（issues/116）——**引擎内置、默认开启**
 *
 * <p>任务参与者解析完成后，对**每个** actor 查询生效中的委托
 * （{@link IProcessExtRepository#getSurrogate}），命中则把被委托人
 * **并入该任务的参与者集合本身**，随后随任务一起落库
 * （{@code saveTask → saveTaskActors} 全量写 {@code wf_process_task_actor}）；
 * 原授权人保留，任一可办（委托不是转办）。</p>
 *
 * <p><b>为什么不走"事后 {@code addTaskActor} 补写"</b>（本类首版的做法，也是 issues/116
 * 的第二层病根）：挂点在建单期，此刻 {@code saveTask} 尚未执行、{@code taskId} 还是 null
 * （taskId 由 {@code JdbcProcessRepository.saveTask} 里的 {@code nextId()} 才分配），
 * 那次补写打在空 id 上是**静默无效**的——能力看起来实现了，实际一单都没代理出去。
 * 故本类只改集合，不再补写；引擎的内置调用点见
 * {@code JeeflowEngineImpl#saveNewTask}（在 {@code saveTask} 之前，taskId 未分配也没关系，
 * 因为追加落在集合上）。</p>
 *
 * <p><b>默认开启</b>：{@code JeeflowEngineImpl.configure()} 在
 * {@link com.mldong.jeeflow.Configuration#isSurrogateAutoApply()} 为真时内置本行为，
 * 集成方零配置即生效（对齐内置版引擎的"白拿"体验）。</p>
 *
 * <p><b>显式关闭</b>（关闭后回到"仅台账"行为）：</p>
 * <ul>
 *   <li>纯 Java：{@code engine.configure(new Configuration().surrogateAutoApply(false));}</li>
 *   <li>Spring Boot：配置项 {@code jeeflow.surrogate.auto-apply=false}</li>
 *   <li>或自建扩展仓储 {@code getSurrogate} 恒返回 null（等价于注册空实现）</li>
 * </ul>
 *
 * <p><b>未配置 {@code IProcessExtRepository} 时静默跳过</b>：委托是增强能力，缺仓储属于正常
 * 部署形态，不得抛"未配置扩展仓储"打断建单。仓储自身报错（如表未建）同样只记日志不阻断。</p>
 *
 * <p>本类也是 {@link FlowInterceptor} 的普通实现，集成方可自行注册（或配置到节点的
 * {@code preInterceptors}）叠加自定义逻辑；追加是幂等的（已在集合里的代理人不重复加），
 * 与内置调用点同时生效也不会重复落库。</p>
 *
 * <p>委托在 todoList 的合并展示属集成方视图层职责（参考 boot3 {@code ProcessTaskServiceImpl.todoList}）。</p>
 *
 * @author mldong
 */
public class SurrogateInterceptor implements FlowInterceptor {

    /** 引擎内置注册的 ServiceContext 键（集成方自行注册/覆盖时用同一键） */
    public static final String CONTEXT_KEY = "surrogateInterceptor";

    /** 可为 null：运行时从 {@link ServiceContext} 解析（未配置则静默跳过） */
    private final IProcessExtRepository extRepository;

    public SurrogateInterceptor() {
        this(null);
    }

    public SurrogateInterceptor(IProcessExtRepository extRepository) {
        this.extRepository = extRepository;
    }

    @Override
    public void intercept(Execution execution) {
        if (execution == null) return;
        List<ProcessTask> tasks = execution.getProcessTaskList();
        if (tasks == null || tasks.isEmpty()) return;
        String processName = resolveProcessName(execution);
        for (ProcessTask task : tasks) {
            apply(task, processName, LocalDateTime.now());
        }
    }

    /**
     * 委托查询的 processName 取值口径（规范 06 §4.5「运行期语义」条款 1.1）：
     * **流程模型 {@code name} 优先**，模型未带（键缺失 / 空串 / 纯空白）时才回落
     * {@code wf_process_define.name}。
     *
     * <p>依据是迁移基线：内置版（mldong-wf）{@code SurrogateInterceptor} 用的正是
     * {@code execution.getProcessModel().getName()}；用户在内置版配的委托，迁到 jeeflow
     * 后必须命中同一条。正常 deploy 两者恒等（{@code def.setName(model.getName())}），
     * 但直接落库的定义（自带导入链路 / 测试夹具）会不一致——只取任一头都会漏。</p>
     *
     * <p>回落路径读定义行失败按「拿不到流程名」处理（只能命中全流程兜底委托），
     * 绝不打断建单（条款 4：委托是增强能力）。返回 {@code null} 与空串在两条判据里同义。</p>
     *
     * <p>⚠️ 定义行读取带 {@code content} BLOB，故只在**模型未带 name** 的异常形态下发生；
     * 调用方应逐次 execution 解析一次后复用（见 {@code JeeflowEngineImpl#persistTasks}），
     * 不要逐任务解析。</p>
     */
    public static String resolveProcessName(Execution execution) {
        if (execution == null) return null;
        ProcessModel model = execution.getProcessModel();
        if (model != null && StringUtils.isNotBlank(model.getName())) {
            return model.getName().trim();
        }
        ProcessInstance instance = execution.getProcessInstance();
        JeeflowEngine engine = execution.getEngine();
        if (instance == null || instance.getDefineId() == null || engine == null
                || engine.getRepository() == null) {
            return null;
        }
        try {
            ProcessInstance.ProcessDefine define = engine.getRepository().findDefineById(instance.getDefineId());
            return (define == null || StringUtils.isBlank(define.getName()))
                    ? null : define.getName().trim();
        } catch (RuntimeException e) {
            System.err.println("[jeeflow] findDefineById(" + instance.getDefineId()
                    + ") failed, surrogate processName unresolved: " + e.getMessage());
            return null;
        }
    }

    /**
     * 对单个任务应用生效委托：逐个 actor 查一次生效委托，命中的被委托人**并入参与者集合**
     * （授权人保留，任一可办；不做 {@code addTaskActor} 事后补写，见类注释）。
     *
     * <p>幂等：代理人已在集合里则不重复追加，可安全地对同一任务多次调用。</p>
     *
     * @param task        任务（参与者集合被就地更新，供随后的 saveTask 全量落库）
     * @param processName 当前流程名（取值口径见 {@link #resolveProcessName}，null 时只命中全流程委托）
     * @param now         判定时间（一般传 {@link LocalDateTime#now()}）
     */
    public void apply(ProcessTask task, String processName, LocalDateTime now) {
        if (task == null || !isDoing(task)) return;
        IProcessExtRepository repo = resolveRepository();
        if (repo == null) return;                     // 未配置扩展仓储：静默跳过（不打断建单）
        List<String> actors = task.getActorIds();
        if (actors == null || actors.isEmpty()) return;

        List<String> additions = new ArrayList<>();
        // 快照原始参与者逐个查一次委托：代理人自身的委托不级联展开（避免环状委托死循环）
        for (String actor : new ArrayList<>(actors)) {
            ProcessSurrogate hit;
            try {
                hit = repo.getSurrogate(actor, processName, now);
            } catch (RuntimeException e) {
                // 委托是增强能力：查询报错不得打断建单
                System.err.println("[jeeflow] getSurrogate(" + actor + ", " + processName
                        + ") failed, surrogate skipped: " + e.getMessage());
                continue;
            }
            String agent = hit == null ? null : hit.getSurrogate();
            if (agent == null || agent.trim().isEmpty()) continue;
            if (actors.contains(agent) || additions.contains(agent)) continue;
            additions.add(agent);
        }
        if (additions.isEmpty()) return;
        // 落在参与者集合本身（setActorIds 兼容只读列表；引擎随后 saveTask 全量落库）
        List<String> merged = new ArrayList<>(actors);
        merged.addAll(additions);
        task.setActorIds(merged);
    }

    /** 只对待办（DOING）生效：已完成/废弃的历史记录不追加代理人 */
    private static boolean isDoing(ProcessTask task) {
        return ProcessTaskStateEnum.DOING.getCode().equals(task.getTaskState());
    }

    private IProcessExtRepository resolveRepository() {
        return extRepository != null ? extRepository : ServiceContext.find(IProcessExtRepository.class);
    }
}
