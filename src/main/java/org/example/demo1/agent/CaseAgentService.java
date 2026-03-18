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
import org.example.demo1.vo.CaseEnrichVO;
import org.example.demo1.vo.CaseScoreVO;
import org.example.demo1.vo.CaseSummaryVO;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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
    private final CaseEnrichAgent caseEnrichAgent;

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
            // Step 1: 翻译英文→中文（含标题翻译）
            String translatedContent = doTranslation(legalCase);

            // Step 2: 提取补全字段（案件类型、关键词、法律条文、国家/地区、法院）
            String contentForEnrich = translatedContent != null ? translatedContent : legalCase.getContentEn();
            doEnrich(legalCase, contentForEnrich);

            // Step 3: 提取摘要（使用中文内容）
            String contentForAnalysis = translatedContent != null ? translatedContent : legalCase.getContentEn();
            CaseSummaryVO summaryVO = doSummary(legalCase, contentForAnalysis);

            // Step 4: 重要性评分
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
    public void triggerTranslation(Long caseId) {
        log.info("开始异步翻译: caseId={}", caseId);
        LegalCase legalCase = legalCaseMapper.selectById(caseId);
        doTranslation(legalCase);
    }

    /**
     * 异步单独触发字段补全并保存
     * 提取：案件类型、关键词、法律条文、国家/地区、法院
     */
    @Async("aiTaskExecutor")
    public void triggerEnrich(Long caseId) {
        log.info("开始异步字段补全: caseId={}", caseId);
        LegalCase legalCase = legalCaseMapper.selectById(caseId);
        if (legalCase == null) {
            log.error("案例不存在: caseId={}", caseId);
            return;
        }
        String content = legalCase.getContentZh() != null && !legalCase.getContentZh().isBlank()
                ? legalCase.getContentZh()
                : legalCase.getContentEn();
        doEnrich(legalCase, content);
    }

    /**
     * 异步单独触发摘要提取并保存
     */
    @Async("aiTaskExecutor")
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
    public void triggerScore(Long caseId) {
        log.info("开始异步评分: caseId={}", caseId);
        LegalCase legalCase = legalCaseMapper.selectById(caseId);
        CaseSummaryVO summaryVO = null;
        CaseSummary existing = caseSummaryMapper.selectOne(
                new LambdaQueryWrapper<CaseSummary>()
                        .eq(CaseSummary::getCaseId, caseId)
                        .eq(CaseSummary::getStatus, 2)
                        .orderByDesc(CaseSummary::getId)
                        .last("LIMIT 1"));
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
        // 立即追加一条"翻译中"记录
        CaseTranslation record = new CaseTranslation();
        record.setCaseId(legalCase.getId());
        record.setSourceLang("en");
        record.setTargetLang("zh");
        record.setStatus(1);
        caseTranslationMapper.insert(record);
        log.info("翻译任务已创建记录(翻译中): caseId={}, recordId={}", legalCase.getId(), record.getId());

        try {
            // 翻译标题（仅当中文标题为空时）
            if ((legalCase.getTitleZh() == null || legalCase.getTitleZh().isBlank())
                    && legalCase.getTitleEn() != null && !legalCase.getTitleEn().isBlank()) {
                String titleZh = translationAgent.translateTitle(legalCase.getTitleEn());
                if (titleZh != null && !titleZh.isBlank()) {
                    legalCase.setTitleZh(titleZh);
                    log.info("标题翻译完成: caseId={}, titleZh={}", legalCase.getId(), titleZh);
                }
            }

            // 翻译正文
            String translated = translationAgent.translateToZh(legalCase.getContentEn());

            // 回填翻译结果，更新为已完成
            record.setTranslatedContent(translated);
            record.setStatus(2);
            caseTranslationMapper.updateById(record);

            // 更新案例的中文标题和中文正文
            legalCase.setContentZh(translated);
            legalCaseMapper.updateById(legalCase);

            return translated;
        } catch (Exception e) {
            log.error("翻译处理失败: caseId={}", legalCase.getId(), e);
            record.setStatus(3);
            record.setErrorMsg(e.getMessage());
            caseTranslationMapper.updateById(record);
            return null;
        }
    }

    private CaseEnrichVO doEnrich(LegalCase legalCase, String content) {
        try {
            CaseEnrichVO vo = caseEnrichAgent.enrichCase(content, legalCase.getTitleEn());

            // 直接覆盖所有提取到的字段
            boolean needUpdate = false;

            if (vo.getCaseType() != null) {
                legalCase.setCaseType(vo.getCaseType());
                needUpdate = true;
            }
            if (vo.getKeywords() != null && !vo.getKeywords().isBlank()) {
                legalCase.setKeywords(vo.getKeywords());
                needUpdate = true;
            }
            if (vo.getLegalProvisions() != null && !vo.getLegalProvisions().isBlank()) {
                legalCase.setLegalProvisions(vo.getLegalProvisions());
                needUpdate = true;
            }
            if (vo.getCountry() != null && !vo.getCountry().isBlank()) {
                legalCase.setCountry(vo.getCountry());
                needUpdate = true;
            }
            if (vo.getCourt() != null && !vo.getCourt().isBlank()) {
                legalCase.setCourt(vo.getCourt());
                needUpdate = true;
            }

            if (needUpdate) {
                legalCaseMapper.updateById(legalCase);
                log.info("案例字段补全完成并已更新数据库: caseId={}", legalCase.getId());
            }

            return vo;
        } catch (Exception e) {
            log.error("字段补全失败: caseId={}", legalCase.getId(), e);
            // 字段补全失败不中断主流程，记录日志后继续
            return null;
        }
    }

    private CaseSummaryVO doSummary(LegalCase legalCase, String content) {
        // 立即追加一条"提取中"记录
        CaseSummary record = new CaseSummary();
        record.setCaseId(legalCase.getId());
        record.setStatus(1);
        caseSummaryMapper.insert(record);
        log.info("摘要提取任务已创建记录(提取中): caseId={}, recordId={}", legalCase.getId(), record.getId());

        try {
            CaseSummaryVO vo = summaryAgent.extractSummary(content);

            // 回填提取结果，更新为已完成
            record.setCaseReason(vo.getCaseReason());
            record.setDisputeFocus(vo.getDisputeFocus());
            record.setJudgmentResult(vo.getJudgmentResult());
            record.setKeyPoints(vo.getKeyPoints());
            record.setStatus(2);
            caseSummaryMapper.updateById(record);

            // 同步更新 legal_case 表冗余字段
            legalCase.setCaseReason(vo.getCaseReason());
            legalCase.setDisputeFocus(vo.getDisputeFocus());
            legalCase.setJudgmentResult(vo.getJudgmentResult());
            legalCase.setSummaryZh(vo.getCaseReason() + "；" + vo.getDisputeFocus());
            legalCaseMapper.updateById(legalCase);

            return vo;
        } catch (Exception e) {
            log.error("摘要提取失败: caseId={}", legalCase.getId(), e);
            record.setStatus(3);
            record.setErrorMsg(e.getMessage());
            caseSummaryMapper.updateById(record);
            return null;
        }
    }

    private CaseScoreVO doScore(LegalCase legalCase, String content) {
        // 立即追加一条"评分中"记录
        CaseScore record = new CaseScore();
        record.setCaseId(legalCase.getId());
        record.setStatus(1);
        caseScoreMapper.insert(record);
        log.info("评分任务已创建记录(评分中): caseId={}, recordId={}", legalCase.getId(), record.getId());

        try {
            CaseScoreVO vo = scoreAgent.scoreCase(content);

            // 回填评分结果，更新为已完成
            record.setImportanceScore(vo.getImportanceScore());
            record.setInfluenceScore(vo.getInfluenceScore());
            record.setReferenceScore(vo.getReferenceScore());
            record.setTotalScore(vo.getTotalScore());
            record.setScoreReason(vo.getScoreReason());
            record.setScoreTags(vo.getScoreTags());
            record.setStatus(2);
            caseScoreMapper.updateById(record);

            // 同步更新 legal_case 冗余字段
            legalCase.setImportanceScore(vo.getTotalScore());
            legalCase.setScoreReason(vo.getScoreReason());
            legalCaseMapper.updateById(legalCase);

            return vo;
        } catch (Exception e) {
            log.error("评分失败: caseId={}", legalCase.getId(), e);
            record.setStatus(3);
            record.setErrorMsg(e.getMessage());
            caseScoreMapper.updateById(record);
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
