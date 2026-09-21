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
 * 内置的委托自动生效（{@code SurrogateInterceptor}，issues/116）使用。
 * 未注册本 SPI 时设计/委托功能由集成方自身实现，委托自动生效则静默跳过（不打断建单）。</p>
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
     * 查询指定时间生效中的委托。四条判据（内存仓与 SQL 仓必须同答案，规范 05 §getSurrogate）：
     * <ol>
     *   <li>空 {@code processName} = 全部流程兜底：先按流程名精确查，未命中再查
     *       {@code process_name IS NULL OR process_name = ''}；</li>
     *   <li>时间窗 {@code start_time <= time <= end_time}，任一侧为 NULL = 该侧不限；</li>
     *   <li>自委托过滤：{@code surrogate <> operator}（自己委托给自己不生效）；</li>
     *   <li>{@code enabled} 只认 1：0 / NULL / 脏值均不生效（不得默认当启用）。</li>
     * </ol>
     * 多条命中取最新（id 最大）。
     *
     * @return 命中返回委托记录，否则 null
     */
    ProcessSurrogate getSurrogate(String operator, String processName, LocalDateTime time);
}
