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
        // 四条生效判据与 SQL 仓 JdbcProcessExtRepository#querySurrogate 逐条对齐（规范 05 §getSurrogate）：
        // enabled 只认 1（脏值/ null 均不生效）/ 时间窗 NULL=该侧不限 / 自委托过滤 surrogate<>operator /
        // 多条命中取 id 最大（SQL 侧 ORDER BY id DESC LIMIT 1）——08-compliance 用例 27 要求双仓同答案。
        if (operator == null) return null;
        List<ProcessSurrogate> candidates = surrogates.values().stream()
                .filter(s -> operator.equals(s.getOperator()))
                .filter(s -> Integer.valueOf(1).equals(s.getEnabled()))
                .filter(s -> !operator.equals(s.getSurrogate()))
                // surrogate 为 NULL 的行不参与命中（SQL 侧 `surrogate <> ?` 对 NULL 求值为 UNKNOWN，同效）
                .filter(s -> s.getSurrogate() != null)
                .filter(s -> time == null || (s.getStartTime() == null || !s.getStartTime().isAfter(time))
                        && (s.getEndTime() == null || !s.getEndTime().isBefore(time)))
                .sorted(Comparator.comparingLong(
                        (ProcessSurrogate s) -> s.getId() == null ? 0L : s.getId()).reversed())
                .collect(Collectors.toList());
        // 精确匹配流程优先
        for (ProcessSurrogate s : candidates) {
            if (processName != null && processName.equals(s.getProcessName())) return s;
        }
        // 全流程委托兜底（processName 为空 = 全部流程）
        for (ProcessSurrogate s : candidates) {
            if (s.getProcessName() == null || s.getProcessName().isEmpty()) return s;
        }
        return null;
    }
}
