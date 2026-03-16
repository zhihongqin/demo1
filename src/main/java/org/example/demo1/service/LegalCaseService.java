package org.example.demo1.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.example.demo1.dto.CaseQueryDTO;
import org.example.demo1.dto.CaseSaveDTO;
import org.example.demo1.dto.CaseUpdateDTO;
import org.example.demo1.entity.LegalCase;
import org.example.demo1.vo.CaseDetailVO;
import org.example.demo1.vo.CaseListVO;
import org.example.demo1.vo.CaseScoreRecordVO;
import org.example.demo1.vo.CaseScoreVO;
import org.example.demo1.vo.CaseSummaryRecordVO;
import org.example.demo1.vo.CaseSummaryVO;
import org.example.demo1.vo.CaseTranslationRecordVO;

import java.util.List;

public interface LegalCaseService extends IService<LegalCase> {

    /**
     * 分页查询案例列表
     * isAdmin=true 时返回所有案例，否则只返回已通过AI处理且未删除的案例
     */
    IPage<CaseListVO> queryCases(CaseQueryDTO dto, Long userId, boolean isAdmin);

    /**
     * 获取案例详情
     */
    CaseDetailVO getCaseDetail(Long caseId, Long userId);

    /**
     * 保存/更新案例（触发AI处理）
     */
    Long saveCase(CaseSaveDTO dto);

    /**
     * 管理员手动修正案例内容（不触发AI重新处理）
     * 仅更新传入的非 null 字段
     */
    void updateCase(Long caseId, CaseUpdateDTO dto);

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
     * 逻辑删除案例（is_deleted = 1）
     */
    void softDeleteCase(Long caseId);

    /**
     * 恢复逻辑删除的案例（is_deleted = 0）
     */
    void restoreCase(Long caseId);

    /**
     * 物理删除案例（从数据库彻底移除）
     */
    void hardDeleteCase(Long caseId);

    /**
     * 获取指定案例的所有翻译记录（管理员）
     */
    List<CaseTranslationRecordVO> getTranslationRecords(Long caseId);

    /**
     * 获取指定案例的所有摘要记录（管理员）
     */
    List<CaseSummaryRecordVO> getSummaryRecords(Long caseId);

    /**
     * 获取指定案例的所有评分记录（管理员）
     */
    List<CaseScoreRecordVO> getScoreRecords(Long caseId);

    /**
     * 收藏/取消收藏案例
     */
    boolean toggleFavorite(Long caseId, Long userId);

    /**
     * 获取用户收藏列表
     */
    IPage<CaseListVO> getFavorites(Long userId, Integer pageNum, Integer pageSize);
}
