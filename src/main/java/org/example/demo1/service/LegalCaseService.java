package org.example.demo1.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.example.demo1.dto.CaseQueryDTO;
import org.example.demo1.dto.CaseSaveDTO;
import org.example.demo1.dto.CaseUpdateDTO;
import org.example.demo1.entity.LegalCase;
import org.example.demo1.vo.BrowseHistoryVO;
import org.example.demo1.vo.CaseDetailVO;
import org.example.demo1.vo.CaseListVO;
import org.example.demo1.vo.CaseNoteListItemVO;
import org.example.demo1.vo.CaseNoteVO;
import org.example.demo1.vo.CaseScoreRecordVO;
import org.example.demo1.vo.CaseSummaryRecordVO;
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
     * isAdmin=true 时可查看已逻辑删除的案例
     */
    CaseDetailVO getCaseDetail(Long caseId, Long userId, boolean isAdmin);

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
     * 异步触发翻译（立即返回，后台执行）
     */
    void triggerTranslation(Long caseId);

    /**
     * 异步触发字段补全（立即返回，后台执行）
     * 提取：案件类型、关键词、法律条文、国家/地区、法院
     */
    void triggerEnrich(Long caseId);

    /**
     * 异步触发摘要提取（立即返回，后台执行）
     */
    void triggerSummary(Long caseId);

    /**
     * 异步触发重要性评分（立即返回，后台执行）
     */
    void triggerScore(Long caseId);

    /**
     * 手动将案例 AI 处理状态标记为已完成（ai_status = 2）
     * 用于 AI 处理卡住或失败后的人工干预
     */
    void markAiCompleted(Long caseId);

    /**
     * 将案例推送至 FastGPT 知识库（管理员，异步执行）
     */
    void triggerFastgptKnowledgeSync(Long caseId);

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

    /**
     * 分页查询用户浏览记录
     */
    IPage<BrowseHistoryVO> getBrowseHistory(Long userId, Integer pageNum, Integer pageSize);

    /**
     * 删除浏览记录（支持批量）
     */
    void deleteBrowseHistory(Long userId, List<Long> ids);

    /**
     * 获取当前用户对指定案例的笔记（无则 content 为空）
     */
    CaseNoteVO getMyCaseNote(Long caseId, Long userId);

    /**
     * 保存或更新笔记；正文为空则删除笔记
     */
    void saveMyCaseNote(Long caseId, Long userId, String content);

    /**
     * 分页：我的案例笔记列表（含案例标题、摘要预览）
     */
    IPage<CaseNoteListItemVO> getMyCaseNotes(Long userId, Integer pageNum, Integer pageSize);

    /**
     * 代理获取案例原始 PDF 文书字节流（用于小程序绕过日本法院防盗链）
     *
     * @param caseId 案例 ID
     * @return PDF 文件的字节数组
     * @throws org.example.demo1.common.exception.BusinessException 案例不存在或 pdfUrl 为空时抛出
     */
    byte[] proxyPdf(Long caseId);
}
