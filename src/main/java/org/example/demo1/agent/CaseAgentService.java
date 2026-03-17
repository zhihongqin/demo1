package org.example.demo1.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo1.entity.CaseScore;
import org.example.demo1.entity.CaseSummary;
import org.example.demo1.entity.CaseTranslation;
import org.example.demo1.entity.LegalCase;
import org.example.demo1.mapper.CaseScoreMapper;
import org.example.demo1.mapper.CaseSummaryMapper;
import org.example.demo1.mapper.CaseTranslationMapper;
import org.example.demo1.mapper.LegalCaseMapper;
import org.example.demo1.vo.CaseScoreVO;
import org.example.demo1.vo.CaseSummaryVO;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * 案例 AI 处理聚合服务
 * 协调翻译、摘要提取、评分三个 Agent，并将结果持久化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaseAgentService {

    private final TranslationAgent translationAgent;
    private final SummaryAgent summaryAgent;
    private final ScoreAgent scoreAgent;

    private final LegalCaseMapper legalCaseMapper;
    private final CaseTranslationMapper caseTranslationMapper;
    private final CaseSummaryMapper caseSummaryMapper;
    private final CaseScoreMapper caseScoreMapper;

    /**
     * 异步对案例进行完整的 AI 处理（翻译 + 摘要 + 评分）
     */
    @Async("aiTaskExecutor")
    public void processCase(Long caseId) {
        log.info("开始异步AI处理案例: caseId={}", caseId);

        LegalCase legalCase = legalCaseMapper.selectById(caseId);
        if (legalCase == null) {
            log.error("案例不存在: caseId={}", caseId);
            return;
        }

        // 更新状态为处理中
        legalCase.setAiStatus(1);
        legalCaseMapper.updateById(legalCase);

        try {
            // Step 1: 翻译英文→中文
            String translatedContent = doTranslation(legalCase);

            // Step 2: 提取摘要（使用中文内容）
            String contentForAnalysis = translatedContent != null ? translatedContent : legalCase.getContentEn();
            CaseSummaryVO summaryVO = doSummary(legalCase, contentForAnalysis);

            // Step 3: 重要性评分
            String contentForScore = buildScoreContent(legalCase, summaryVO);
            doScore(legalCase, contentForScore);

            // 更新案例状态为已完成
            legalCase.setAiStatus(2);
            legalCaseMapper.updateById(legalCase);
            log.info("案例AI处理完成: caseId={}", caseId);

        } catch (Exception e) {
            log.error("案例AI处理失败: caseId={}", caseId, e);
            legalCase.setAiStatus(3);
            legalCaseMapper.updateById(legalCase);
        }
    }

    /**
     * 异步单独触发翻译并保存
     */
    @Async("aiTaskExecutor")
    @Transactional
    public void triggerTranslation(Long caseId) {
        log.info("开始异步翻译: caseId={}", caseId);
        LegalCase legalCase = legalCaseMapper.selectById(caseId);
        doTranslation(legalCase);
    }

    /**
     * 异步单独触发摘要提取并保存
     */
    @Async("aiTaskExecutor")
    @Transactional
    public void triggerSummary(Long caseId) {
        log.info("开始异步摘要提取: caseId={}", caseId);
        LegalCase legalCase = legalCaseMapper.selectById(caseId);
        String content = legalCase.getContentZh() != null
                ? legalCase.getContentZh()
                : legalCase.getContentEn();
        doSummary(legalCase, content);
    }

    /**
     * 异步单独触发评分并保存
     */
    @Async("aiTaskExecutor")
    @Transactional
    public void triggerScore(Long caseId) {
        log.info("开始异步评分: caseId={}", caseId);
        LegalCase legalCase = legalCaseMapper.selectById(caseId);
        CaseSummaryVO summaryVO = null;
        CaseSummary existing = caseSummaryMapper.selectOne(
                new LambdaQueryWrapper<CaseSummary>().eq(CaseSummary::getCaseId, caseId));
        if (existing != null) {
            summaryVO = new CaseSummaryVO();
            summaryVO.setCaseReason(existing.getCaseReason());
            summaryVO.setDisputeFocus(existing.getDisputeFocus());
            summaryVO.setJudgmentResult(existing.getJudgmentResult());
        }
        String contentForScore = buildScoreContent(legalCase, summaryVO);
        doScore(legalCase, contentForScore);
    }

    // ==================== 私有处理方法 ====================

    private String doTranslation(LegalCase legalCase) {
        try {
            String translated = translationAgent.translateToZh(legalCase.getContentEn());

            // 保存翻译记录
            CaseTranslation translation = new CaseTranslation();
            translation.setCaseId(legalCase.getId());
            translation.setSourceLang("en");
            translation.setTargetLang("zh");
            translation.setTranslatedContent(translated);
            translation.setStatus(2);

            // 删除旧记录
            caseTranslationMapper.delete(
                    new LambdaQueryWrapper<CaseTranslation>().eq(CaseTranslation::getCaseId, legalCase.getId()));
            caseTranslationMapper.insert(translation);

            // 更新案例的中文内容
            legalCase.setContentZh(translated);
            legalCaseMapper.updateById(legalCase);

            return translated;
        } catch (Exception e) {
            log.error("翻译处理失败: caseId={}", legalCase.getId(), e);
            CaseTranslation failRecord = new CaseTranslation();
            failRecord.setCaseId(legalCase.getId());
            failRecord.setSourceLang("en");
            failRecord.setTargetLang("zh");
            failRecord.setStatus(3);
            failRecord.setErrorMsg(e.getMessage());
            caseTranslationMapper.insert(failRecord);
            return null;
        }
    }

    private CaseSummaryVO doSummary(LegalCase legalCase, String content) {
        try {
            CaseSummaryVO vo = summaryAgent.extractSummary(content);

            CaseSummary summary = new CaseSummary();
            summary.setCaseId(legalCase.getId());
            summary.setCaseReason(vo.getCaseReason());
            summary.setDisputeFocus(vo.getDisputeFocus());
            summary.setJudgmentResult(vo.getJudgmentResult());
            summary.setKeyPoints(vo.getKeyPoints());
            summary.setStatus(2);

            caseSummaryMapper.delete(
                    new LambdaQueryWrapper<CaseSummary>().eq(CaseSummary::getCaseId, legalCase.getId()));
            caseSummaryMapper.insert(summary);

            // 同步更新 legal_case 表冗余字段
            legalCase.setCaseReason(vo.getCaseReason());
            legalCase.setDisputeFocus(vo.getDisputeFocus());
            legalCase.setJudgmentResult(vo.getJudgmentResult());
            legalCase.setSummaryZh(vo.getCaseReason() + "；" + vo.getDisputeFocus());
            legalCaseMapper.updateById(legalCase);

            return vo;
        } catch (Exception e) {
            log.error("摘要提取失败: caseId={}", legalCase.getId(), e);
            CaseSummary failRecord = new CaseSummary();
            failRecord.setCaseId(legalCase.getId());
            failRecord.setStatus(3);
            failRecord.setErrorMsg(e.getMessage());
            caseSummaryMapper.insert(failRecord);
            return null;
        }
    }

    private CaseScoreVO doScore(LegalCase legalCase, String content) {
        try {
            CaseScoreVO vo = scoreAgent.scoreCase(content);

            CaseScore score = new CaseScore();
            score.setCaseId(legalCase.getId());
            score.setImportanceScore(vo.getImportanceScore());
            score.setInfluenceScore(vo.getInfluenceScore());
            score.setReferenceScore(vo.getReferenceScore());
            score.setTotalScore(vo.getTotalScore());
            score.setScoreReason(vo.getScoreReason());
            score.setScoreTags(vo.getScoreTags());
            score.setStatus(2);

            caseScoreMapper.delete(
                    new LambdaQueryWrapper<CaseScore>().eq(CaseScore::getCaseId, legalCase.getId()));
            caseScoreMapper.insert(score);

            // 同步更新 legal_case 冗余字段
            legalCase.setImportanceScore(vo.getTotalScore());
            legalCase.setScoreReason(vo.getScoreReason());
            legalCaseMapper.updateById(legalCase);

            return vo;
        } catch (Exception e) {
            log.error("评分失败: caseId={}", legalCase.getId(), e);
            CaseScore failRecord = new CaseScore();
            failRecord.setCaseId(legalCase.getId());
            failRecord.setStatus(3);
            failRecord.setErrorMsg(e.getMessage());
            caseScoreMapper.insert(failRecord);
            return null;
        }
    }

    private String buildScoreContent(LegalCase legalCase, CaseSummaryVO summaryVO) {
        StringBuilder sb = new StringBuilder();
        sb.append("案例标题：").append(legalCase.getTitleZh() != null ? legalCase.getTitleZh() : legalCase.getTitleEn()).append("\n");
        sb.append("所属国家：").append(legalCase.getCountry()).append("\n");
        sb.append("审理法院：").append(legalCase.getCourt()).append("\n");
        if (summaryVO != null) {
            sb.append("案由：").append(summaryVO.getCaseReason()).append("\n");
            sb.append("争议焦点：").append(summaryVO.getDisputeFocus()).append("\n");
            sb.append("判决结果：").append(summaryVO.getJudgmentResult()).append("\n");
            sb.append("核心要点：").append(summaryVO.getKeyPoints()).append("\n");
        } else if (legalCase.getSummaryZh() != null) {
            sb.append("摘要：").append(legalCase.getSummaryZh()).append("\n");
        }
        return sb.toString();
    }
}
