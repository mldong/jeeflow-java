package com.mldong.jeeflow.spi;

import com.mldong.jeeflow.domain.ProcessDesign;
import com.mldong.jeeflow.domain.ProcessDesignHis;
import com.mldong.jeeflow.domain.ProcessSurrogate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 扩展仓储 SPI（v1.1.0，可选）——流程设计 / 设计历史 / 委托代理
 *
 * <p>引擎核心不依赖本接口：设计稿与委托是"周边管理能力"，门面（JeeflowFacade）与
 * SurrogateInterceptor 使用。集成方不接本 SPI 时，设计/委托功能由自身实现。</p>
 *
 * @author mldong
 */
public interface IProcessExtRepository {

    // ═══ 流程设计（wf_process_design） ═══

    ProcessDesign findDesignById(Long designId);
    void saveDesign(ProcessDesign design);      // id 为空由仓储生成
    void updateDesign(ProcessDesign design);
    void removeDesign(Long designId);
    PageResult<ProcessDesign> pageDesigns(PageQuery query);

    // ═══ 设计历史（wf_process_design_his） ═══

    void saveDesignHis(ProcessDesignHis his);   // id 为空由仓储生成
    List<ProcessDesignHis> listDesignHis(Long designId);

    // ═══ 委托代理（wf_process_surrogate） ═══

    ProcessSurrogate findSurrogateById(Long surrogateId);
    void saveSurrogate(ProcessSurrogate surrogate);
    void updateSurrogate(ProcessSurrogate surrogate);
    void removeSurrogate(Long surrogateId);
    PageResult<ProcessSurrogate> pageSurrogates(PageQuery query);

    /**
     * 查询指定时间生效中的委托：enabled=1 且时间窗内（起止为空表示不限）。
     * 优先 processName 精确匹配，其次 processName 为空的"全流程委托"兜底。
     *
     * @return 命中返回委托记录，否则 null
     */
    ProcessSurrogate getSurrogate(String operator, String processName, LocalDateTime time);
}
