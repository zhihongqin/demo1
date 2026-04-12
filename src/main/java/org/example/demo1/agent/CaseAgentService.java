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
    private final JapanTranslationAgent japanTranslationAgent;
    private final SummaryAgent summaryAgent;
    private final ScoreAgent scoreAgent;
    private final JapanSummaryAgent japanSummaryAgent;
    private final JapanScoreAgent japanScoreAgent;

    private final LegalCaseMapper legalCaseMapper;
    private final CaseTranslationMapper caseTranslationMapper;
    private final CaseSummaryMapper caseSummaryMapper;
    private final CaseScoreMapper caseScoreMapper;
    private final FastgptKnowledgeSyncService fastgptKnowledgeSyncService;

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
            boolean isJapan = "日本".equals(legalCase.getCountry());

            // Step 1: 翻译原文→中文（含标题翻译）；日本案例走 PDF 链接翻译，其余走英文文本翻译
            String translatedContent = isJapan
                    ? doJapanTranslation(legalCase)
                    : doTranslation(legalCase);

            // Step 2: 提取摘要；日本案例使用专用 Agent
            CaseSummaryVO summaryVO;
            if (isJapan) {
                summaryVO = doJapanSummary(legalCase, translatedContent);
            } else {
                String contentForAnalysis = translatedContent != null ? translatedContent : legalCase.getContentEn();
                summaryVO = doSummary(legalCase, contentForAnalysis);
            }

            // Step 3: 重要性评分；日本案例使用专用 Agent
            if (isJapan) {
                doJapanScore(legalCase, summaryVO);
            } else {
                String contentForScore = buildScoreContent(legalCase, summaryVO);
                doScore(legalCase, contentForScore);
            }

            // 更新案例状态为已完成
            legalCase.setAiStatus(2);
            legalCaseMapper.updateById(legalCase);
            log.info("案例AI处理完成: caseId={}", caseId);

            fastgptKnowledgeSyncService.syncCaseAsync(caseId);

        } catch (Exception e) {
            log.error("案例AI处理失败: caseId={}", caseId, e);
            legalCase.setAiStatus(3);
            legalCaseMapper.updateById(legalCase);
        }
    }

    /**
     * 异步单独触发翻译并保存
     * 日本案例走 PDF 链接翻译，其余走英文文本翻译
     */
    @Async("aiTaskExecutor")
    public void triggerTranslation(Long caseId) {
        log.info("开始异步翻译: caseId={}", caseId);
        LegalCase legalCase = legalCaseMapper.selectById(caseId);
        if (legalCase == null) {
            log.error("案例不存在: caseId={}", caseId);
            return;
        }
        if ("日本".equals(legalCase.getCountry())) {
            doJapanTranslation(legalCase);
        } else {
            doTranslation(legalCase);
        }
    }

    /**
     * 字段补全已整合至翻译流程，此接口保留以兼容已有 API 调用，实际不再执行独立补全。
     * 如需重新补全字段，请重新触发翻译（triggerTranslation）。
     */
    @Async("aiTaskExecutor")
    public void triggerEnrich(Long caseId) {
        log.info("triggerEnrich 已废弃，字段补全已内嵌于翻译流程中: caseId={}", caseId);
    }

    /**
     * 异步单独触发摘要提取并保存。
     * 日本案例：优先 PDF 模式，PDF 失败时降级使用 contentZh 文本模式。
     * 其他案例：使用 contentZh 或 contentEn。
     */
    @Async("aiTaskExecutor")
    public void triggerSummary(Long caseId) {
        log.info("开始异步摘要提取: caseId={}", caseId);
        LegalCase legalCase = legalCaseMapper.selectById(caseId);
        if ("日本".equals(legalCase.getCountry())) {
            doJapanSummary(legalCase, legalCase.getContentZh());
        } else {
            String content = legalCase.getContentZh() != null
                    ? legalCase.getContentZh()
                    : legalCase.getContentEn();
            doSummary(legalCase, content);
        }
    }

    /**
     * 异步单独触发评分并保存。
     * 日本案例：优先 PDF 模式，PDF 失败时降级使用摘要数据文本模式。
     * 其他案例：组装摘要+元数据进行文本评分。
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
        if ("日本".equals(legalCase.getCountry())) {
            doJapanScore(legalCase, summaryVO);
        } else {
            String contentForScore = buildScoreContent(legalCase, summaryVO);
            doScore(legalCase, contentForScore);
        }
    }

    // ==================== 私有处理方法 ====================

    /**
     * 日本案例专用翻译：通过 pdfUrl 上传 PDF 至 FastGPT 后翻译为中文。
     * 若 pdfUrl 为空，降级使用 {@link #doTranslation} 处理 contentEn 文本。
     */
    private String doJapanTranslation(LegalCase legalCase) {
        CaseTranslation record = new CaseTranslation();
        record.setCaseId(legalCase.getId());
        record.setSourceLang("ja");
        record.setTargetLang("zh");
        record.setStatus(1);
        caseTranslationMapper.insert(record);
        log.info("日文翻译任务已创建记录(翻译中): caseId={}, recordId={}", legalCase.getId(), record.getId());

        try {
            // 翻译标题（仅当中文标题为空时）
            if ((legalCase.getTitleZh() == null || legalCase.getTitleZh().isBlank())
                    && legalCase.getTitleEn() != null && !legalCase.getTitleEn().isBlank()) {
                String titleZh = japanTranslationAgent.translateTitle(legalCase.getTitleEn());
                if (titleZh != null && !titleZh.isBlank()) {
                    legalCase.setTitleZh(titleZh);
                    log.info("日文标题翻译完成: caseId={}, titleZh={}", legalCase.getId(), titleZh);
                }
            }

            // 翻译正文：优先走 pdfUrl（多轮分页翻译 + 顺带字段提取），降级到 contentEn 文本
            TranslationResult tr;
            if (legalCase.getPdfUrl() != null && !legalCase.getPdfUrl().isBlank()) {
                log.info("使用 PDF 翻译，pdfUrl={}", legalCase.getPdfUrl());
                tr = japanTranslationAgent.translatePdfToZh(legalCase.getPdfUrl());
            } else {
                log.warn("案例无 pdfUrl，降级使用 contentEn 文本翻译: caseId={}", legalCase.getId());
                tr = translationAgent.translateToZh(legalCase.getContentEn());
            }

            String translated = tr.getContentZh();
            record.setTranslatedContent(translated);
            record.setStatus(2);
            caseTranslationMapper.updateById(record);

            // 回填翻译中顺带提取的字段（country 保持"日本"，applyTranslationResult 内非空才覆盖）
            applyTranslationResult(legalCase, tr);
            legalCaseMapper.updateById(legalCase);

            return translated;
        } catch (Exception e) {
            log.error("日文 PDF 翻译失败: caseId={}", legalCase.getId(), e);
            record.setStatus(3);
            record.setErrorMsg(e.getMessage());
            caseTranslationMapper.updateById(record);
            return null;
        }
    }

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

            // 翻译正文（同时提取结构化字段）
            TranslationResult tr = translationAgent.translateToZh(legalCase.getContentEn());
            String translated = tr.getContentZh();

            // 回填翻译结果，更新为已完成
            record.setTranslatedContent(translated);
            record.setStatus(2);
            caseTranslationMapper.updateById(record);

            // 将翻译中顺带提取的字段写入案例（非空才覆盖）
            applyTranslationResult(legalCase, tr);
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

    /** 将 TranslationResult 中提取的结构化字段回填到 legalCase（非空才覆盖） */
    private void applyTranslationResult(LegalCase legalCase, TranslationResult tr) {
        if (tr.getCaseType() != null) legalCase.setCaseType(tr.getCaseType());
        if (isNotBlank(tr.getCaseReason()))      legalCase.setCaseReason(tr.getCaseReason());
        if (isNotBlank(tr.getKeywords()))         legalCase.setKeywords(tr.getKeywords());
        if (isNotBlank(tr.getLegalProvisions()))  legalCase.setLegalProvisions(tr.getLegalProvisions());
        if (isNotBlank(tr.getCountry()))          legalCase.setCountry(tr.getCountry());
        if (isNotBlank(tr.getCourt()))            legalCase.setCourt(tr.getCourt());
        if (isNotBlank(tr.getContentZh()))        legalCase.setContentZh(tr.getContentZh());
        log.info("字段回填完成: caseId={}, caseType={}, country={}, court={}",
                legalCase.getId(), tr.getCaseType(), tr.getCountry(), tr.getCourt());
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
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

    /**
     * 日本案例专用摘要提取（优先 PDF 模式，降级文本模式）：
     * <ol>
     *   <li>若有 pdfUrl → 优先走 PDF 模式；失败时若有 translatedContent → 降级文本模式</li>
     *   <li>若无 pdfUrl 但有 translatedContent → 直接走文本模式</li>
     *   <li>两者均不可用 → 记录失败并返回 null</li>
     * </ol>
     */
    private CaseSummaryVO doJapanSummary(LegalCase legalCase, String translatedContent) {
        CaseSummary record = new CaseSummary();
        record.setCaseId(legalCase.getId());
        record.setStatus(1);
        caseSummaryMapper.insert(record);
        log.info("日本案例摘要任务已创建记录(提取中): caseId={}, recordId={}", legalCase.getId(), record.getId());

        try {
            CaseSummaryVO vo = null;

            // 第一优先：PDF 模式
            if (isNotBlank(legalCase.getPdfUrl())) {
                try {
                    log.info("日本案例摘要：优先使用 PDF 模式，caseId={}", legalCase.getId());
                    vo = japanSummaryAgent.extractSummaryFromPdf(legalCase.getPdfUrl());
                } catch (Exception pdfEx) {
                    log.warn("日本案例摘要 PDF 模式失败，尝试文本模式降级: caseId={}, 错误={}", legalCase.getId(), pdfEx.getMessage());
                }
            }

            // 降级：文本模式（PDF 失败或无 pdfUrl，且有译文）
            if (vo == null && isNotBlank(translatedContent)) {
                log.info("日本案例摘要：使用文本模式（降级），caseId={}", legalCase.getId());
                vo = japanSummaryAgent.extractSummaryFromText(translatedContent);
            }

            if (vo == null) {
                throw new IllegalStateException("无可用数据源（pdfUrl 和 translatedContent 均为空或均处理失败）");
            }

            record.setCaseReason(vo.getCaseReason());
            record.setDisputeFocus(vo.getDisputeFocus());
            record.setJudgmentResult(vo.getJudgmentResult());
            record.setKeyPoints(vo.getKeyPoints());
            record.setStatus(2);
            caseSummaryMapper.updateById(record);

            legalCase.setCaseReason(vo.getCaseReason());
            legalCase.setDisputeFocus(vo.getDisputeFocus());
            legalCase.setJudgmentResult(vo.getJudgmentResult());
            legalCase.setSummaryZh(vo.getCaseReason() + "；" + vo.getDisputeFocus());
            legalCaseMapper.updateById(legalCase);

            return vo;
        } catch (Exception e) {
            log.error("日本案例摘要提取失败: caseId={}", legalCase.getId(), e);
            record.setStatus(3);
            record.setErrorMsg(e.getMessage());
            caseSummaryMapper.updateById(record);
            return null;
        }
    }

    /**
     * 日本案例专用评分（优先 PDF 模式，降级文本模式）：
     * <ol>
     *   <li>若有 pdfUrl → 优先走 PDF 模式；失败时若有 summaryVO → 降级文本模式</li>
     *   <li>若无 pdfUrl 但有 summaryVO → 直接走文本模式</li>
     *   <li>两者均不可用 → 记录失败并返回 null</li>
     * </ol>
     */
    private CaseScoreVO doJapanScore(LegalCase legalCase, CaseSummaryVO summaryVO) {
        CaseScore record = new CaseScore();
        record.setCaseId(legalCase.getId());
        record.setStatus(1);
        caseScoreMapper.insert(record);
        log.info("日本案例评分任务已创建记录(评分中): caseId={}, recordId={}", legalCase.getId(), record.getId());

        try {
            CaseScoreVO vo = null;

            // 第一优先：PDF 模式
            if (isNotBlank(legalCase.getPdfUrl())) {
                try {
                    log.info("日本案例评分：优先使用 PDF 模式，caseId={}", legalCase.getId());
                    vo = japanScoreAgent.scoreCaseFromPdf(legalCase.getPdfUrl());
                } catch (Exception pdfEx) {
                    log.warn("日本案例评分 PDF 模式失败，尝试文本模式降级: caseId={}, 错误={}", legalCase.getId(), pdfEx.getMessage());
                }
            }

            // 降级：文本模式（PDF 失败或无 pdfUrl，且有摘要数据）
            if (vo == null && summaryVO != null) {
                log.info("日本案例评分：使用文本模式（降级），caseId={}", legalCase.getId());
                String scoreContent = buildScoreContent(legalCase, summaryVO);
                vo = japanScoreAgent.scoreCaseFromText(scoreContent);
            }

            if (vo == null) {
                throw new IllegalStateException("无可用数据源（pdfUrl 和 summaryVO 均为空或均处理失败）");
            }

            record.setImportanceScore(vo.getImportanceScore());
            record.setInfluenceScore(vo.getInfluenceScore());
            record.setReferenceScore(vo.getReferenceScore());
            record.setTotalScore(vo.getTotalScore());
            record.setScoreReason(vo.getScoreReason());
            record.setScoreTags(vo.getScoreTags());
            record.setStatus(2);
            caseScoreMapper.updateById(record);

            legalCase.setImportanceScore(vo.getTotalScore());
            legalCase.setScoreReason(vo.getScoreReason());
            legalCaseMapper.updateById(legalCase);

            return vo;
        } catch (Exception e) {
            log.error("日本案例评分失败: caseId={}", legalCase.getId(), e);
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
