package org.example.demo1.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.example.demo1.dto.CaseQueryDTO;
import org.example.demo1.dto.CaseSaveDTO;
import org.example.demo1.entity.LegalCase;
import org.example.demo1.vo.CaseDetailVO;
import org.example.demo1.vo.CaseListVO;
import org.example.demo1.vo.CaseScoreVO;
import org.example.demo1.vo.CaseSummaryVO;

public interface LegalCaseService extends IService<LegalCase> {

    /**
     * 分页查询案例列表
     */
    IPage<CaseListVO> queryCases(CaseQueryDTO dto, Long userId);

    /**
     * 获取案例详情
     */
    CaseDetailVO getCaseDetail(Long caseId, Long userId);

    /**
     * 保存/更新案例（触发AI处理）
     */
    Long saveCase(CaseSaveDTO dto);

    /**
     * 触发翻译
     */
    String triggerTranslation(Long caseId);

    /**
     * 触发摘要提取
     */
    CaseSummaryVO triggerSummary(Long caseId);

    /**
     * 触发重要性评分
     */
    CaseScoreVO triggerScore(Long caseId);

    /**
     * 收藏/取消收藏案例
     */
    boolean toggleFavorite(Long caseId, Long userId);

    /**
     * 获取用户收藏列表
     */
    IPage<CaseListVO> getFavorites(Long userId, Integer pageNum, Integer pageSize);
}
