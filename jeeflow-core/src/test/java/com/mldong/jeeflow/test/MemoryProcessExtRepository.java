package com.mldong.jeeflow.test;

import com.mldong.jeeflow.domain.ProcessDesign;
import com.mldong.jeeflow.domain.ProcessDesignHis;
import com.mldong.jeeflow.domain.ProcessSurrogate;
import com.mldong.jeeflow.spi.IProcessExtRepository;
import com.mldong.jeeflow.spi.PageQuery;
import com.mldong.jeeflow.spi.PageResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
        List<ProcessSurrogate> rows = new ArrayList<>(surrogates.values());
        return PageResult.of(query.getPageNum(), query.getPageSize(), rows.size(), rows);
    }

    @Override
    public ProcessSurrogate getSurrogate(String operator, String processName, LocalDateTime time) {
        List<ProcessSurrogate> candidates = surrogates.values().stream()
                .filter(s -> operator.equals(s.getOperator()))
                .filter(s -> s.getEnabled() != null && s.getEnabled() == 1)
                .filter(s -> time == null || (s.getStartTime() == null || !s.getStartTime().isAfter(time))
                        && (s.getEndTime() == null || !s.getEndTime().isBefore(time)))
                .collect(Collectors.toList());
        // 精确匹配流程优先
        for (ProcessSurrogate s : candidates) {
            if (processName != null && processName.equals(s.getProcessName())) return s;
        }
        // 全流程委托兜底
        for (ProcessSurrogate s : candidates) {
            if (s.getProcessName() == null || s.getProcessName().isEmpty()) return s;
        }
        return null;
    }
}
