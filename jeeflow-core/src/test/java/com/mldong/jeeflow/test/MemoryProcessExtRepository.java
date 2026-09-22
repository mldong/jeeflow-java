package com.mldong.jeeflow.test;

import com.mldong.jeeflow.domain.ProcessDesign;
import com.mldong.jeeflow.domain.ProcessDesignHis;
import com.mldong.jeeflow.domain.ProcessSurrogate;
import com.mldong.jeeflow.spi.IProcessExtRepository;
import com.mldong.jeeflow.spi.PageQuery;
import com.mldong.jeeflow.spi.PageResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 扩展仓储内存实现（测试用）——v1.1.0
 */
public class MemoryProcessExtRepository implements IProcessExtRepository {

    private final Map<Long, ProcessDesign> designs = new ConcurrentHashMap<>();
    private final Map<Long, List<ProcessDesignHis>> designHis = new ConcurrentHashMap<>();
    private final Map<Long, ProcessSurrogate> surrogates = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1);

    // ═══ 流程设计 ═══

    @Override
    public ProcessDesign findDesignById(Long designId) {
        return designs.get(designId);
    }

    @Override
    public void saveDesign(ProcessDesign design) {
        if (design.getId() == null) design.setId(idSeq.getAndIncrement());
        if (design.getCreateTime() == null) design.setCreateTime(LocalDateTime.now());
        if (design.getUpdateTime() == null) design.setUpdateTime(LocalDateTime.now());
        designs.put(design.getId(), design);
    }

    @Override
    public void updateDesign(ProcessDesign design) {
        design.setUpdateTime(LocalDateTime.now());
        designs.put(design.getId(), design);
    }

    @Override
    public void removeDesign(Long designId) {
        designs.remove(designId);
        designHis.remove(designId);
    }

    @Override
    public PageResult<ProcessDesign> pageDesigns(PageQuery query) {
        List<ProcessDesign> rows = new ArrayList<>(designs.values());
        return PageResult.of(query.getPageNum(), query.getPageSize(), rows.size(), rows);
    }

    // ═══ 设计历史 ═══

    @Override
    public void saveDesignHis(ProcessDesignHis his) {
        if (his.getId() == null) his.setId(idSeq.getAndIncrement());
        if (his.getCreateTime() == null) his.setCreateTime(LocalDateTime.now());
        designHis.computeIfAbsent(his.getProcessDesignId(), k -> new ArrayList<>()).add(0, his);
    }

    @Override
    public List<ProcessDesignHis> listDesignHis(Long designId) {
        return designHis.getOrDefault(designId, new ArrayList<>());
    }

    // ═══ 委托代理 ═══

    @Override
    public ProcessSurrogate findSurrogateById(Long surrogateId) {
        return surrogates.get(surrogateId);
    }

    @Override
    public void saveSurrogate(ProcessSurrogate surrogate) {
        if (surrogate.getId() == null) surrogate.setId(idSeq.getAndIncrement());
        if (surrogate.getCreateTime() == null) surrogate.setCreateTime(LocalDateTime.now());
        if (surrogate.getUpdateTime() == null) surrogate.setUpdateTime(LocalDateTime.now());
        if (surrogate.getEnabled() == null) surrogate.setEnabled(1);
        surrogates.put(surrogate.getId(), surrogate);
    }

    @Override
    public void updateSurrogate(ProcessSurrogate surrogate) {
        surrogate.setUpdateTime(LocalDateTime.now());
        surrogates.put(surrogate.getId(), surrogate);
    }

    @Override
    public void removeSurrogate(Long surrogateId) {
        surrogates.remove(surrogateId);
    }

    @Override
    public PageResult<ProcessSurrogate> pageSurrogates(PageQuery query) {
        // m_ 条件过滤（issues/82-7）：对齐 JDBC buildWhere（白名单 + EQ/LIKE/IN…）+ buildOrder 默认 t.id DESC。
        // 内存约定同核心仓储 pageDefines：过滤后返回全部行（不切片），recordCount=过滤后总数。
        List<ProcessSurrogate> rows = surrogates.values().stream()
                .filter(s -> MemoryProcessRepository.matches(query.getConditions(), surrogateFields(s)))
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .collect(Collectors.toList());
        return PageResult.of(query.getPageNum(), query.getPageSize(), rows.size(), rows);
    }

    /** 委托行字段（t.* 键，对齐 JDBC SURROGATE_WHITELIST 列名） */
    private static Map<String, Object> surrogateFields(ProcessSurrogate s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("t.id", s.getId());
        m.put("t.process_name", s.getProcessName());
        m.put("t.operator", s.getOperator());
        m.put("t.surrogate", s.getSurrogate());
        m.put("t.enabled", s.getEnabled());
        m.put("t.start_time", s.getStartTime());
        m.put("t.end_time", s.getEndTime());
        m.put("t.create_time", s.getCreateTime());
        m.put("t.update_time", s.getUpdateTime());
        return m;
    }

    @Override
    public ProcessSurrogate getSurrogate(String operator, String processName, LocalDateTime time) {
        // 与 SQL 仓 JdbcProcessExtRepository#getSurrogate 同形（规范 06 §4.5 条款 1.4）：
        // 先在指定流程作用域内按 id 取**最新一条**，交 ProcessSurrogate#isEffective 裁决；
        // 该作用域没有记录才看"全流程"作用域（各自取自己作用域里最新的一条）。
        // 不得"先滤生效再取最新"——那等于上一条窗内委托把用户后续改停用/改到未来的设置永久盖掉（issues/123）。
        if (operator == null) return null;
        ProcessSurrogate exact = newestInScope(operator, processName, false);
        if (exact != null && exact.isEffective(operator, time)) return exact;
        ProcessSurrogate global = newestInScope(operator, processName, true);
        return global != null && global.isEffective(operator, time) ? global : null;
    }

    /** scopeGlobal=false ⇒ processName 精确匹配；true ⇒ 全流程委托（processName 为空）。取 id 最大的一条。 */
    private ProcessSurrogate newestInScope(String operator, String processName, boolean scopeGlobal) {
        Comparator<ProcessSurrogate> newestFirst = Comparator.comparingLong(
                (ProcessSurrogate s) -> s.getId() == null ? 0L : s.getId()).reversed();
        return surrogates.values().stream()
                .filter(s -> operator.equals(s.getOperator()))
                .filter(s -> scopeGlobal
                        ? (s.getProcessName() == null || s.getProcessName().isEmpty())
                        : (processName != null && processName.equals(s.getProcessName())))
                .min(newestFirst)
                .orElse(null);
    }
}
