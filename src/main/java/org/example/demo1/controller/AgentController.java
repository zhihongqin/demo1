package org.example.demo1.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.example.demo1.agent.JapanScoreAgent;
import org.example.demo1.agent.JapanSummaryAgent;
import org.example.demo1.agent.ScoreAgent;
import org.example.demo1.agent.SummaryAgent;
import org.example.demo1.agent.TranslationAgent;
import org.example.demo1.common.result.Result;
import org.example.demo1.entity.CaseScore;
import org.example.demo1.entity.CaseSummary;
import org.example.demo1.entity.CaseTranslation;
import org.example.demo1.mapper.CaseScoreMapper;
import org.example.demo1.mapper.CaseSummaryMapper;
import org.example.demo1.mapper.CaseTranslationMapper;
import org.example.demo1.vo.AiProcessingCountVO;
import org.example.demo1.vo.CaseScoreVO;
import org.example.demo1.vo.CaseSummaryVO;
import org.springframework.web.bind.annotation.*;

/**
 * Agent 直接调用接口（用于测试和前端直接请求）
 */
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private final TranslationAgent translationAgent;
    private final SummaryAgent summaryAgent;
    private final ScoreAgent scoreAgent;
    private final JapanSummaryAgent japanSummaryAgent;
    private final JapanScoreAgent japanScoreAgent;
    private final CaseTranslationMapper caseTranslationMapper;
    private final CaseSummaryMapper caseSummaryMapper;
    private final CaseScoreMapper caseScoreMapper;

    /**
     * 查询三张 AI 记录表中正在处理（status=1）的任务数量
     * GET /api/agent/processing-count
     */
    @GetMapping("/processing-count")
    public Result<AiProcessingCountVO> getProcessingCount() {
        long translating = caseTranslationMapper.selectCount(
                new LambdaQueryWrapper<CaseTranslation>().eq(CaseTranslation::getStatus, 1));
        long summarizing = caseSummaryMapper.selectCount(
                new LambdaQueryWrapper<CaseSummary>().eq(CaseSummary::getStatus, 1));
        long scoring = caseScoreMapper.selectCount(
                new LambdaQueryWrapper<CaseScore>().eq(CaseScore::getStatus, 1));

        AiProcessingCountVO vo = new AiProcessingCountVO();
        vo.setTranslating(translating);
        vo.setSummarizing(summarizing);
        vo.setScoring(scoring);
        vo.setTotal(translating + summarizing + scoring);
        return Result.success(vo);
    }

    /**
     * 直接翻译文本（英→中）
     * POST /api/agent/translate
     */
    @PostMapping("/translate")
    public Result<String> translate(@RequestBody TranslateRequest request) {
        String result = translationAgent.translateToZh(request.getContent()).getContentZh();
        return Result.success(result);
    }

    /**
     * 直接提取摘要
     * POST /api/agent/summary
     */
    @PostMapping("/summary")
    public Result<CaseSummaryVO> summary(@RequestBody ContentRequest request) {
        CaseSummaryVO result = summaryAgent.extractSummary(request.getContent());
        return Result.success(result);
    }

    /**
     * 直接评分
     * POST /api/agent/score
     */
    @PostMapping("/score")
    public Result<CaseScoreVO> score(@RequestBody ContentRequest request) {
        CaseScoreVO result = scoreAgent.scoreCase(request.getContent());
        return Result.success(result);
    }

    /**
     * 日本案例：通过 PDF 链接提取摘要
     * POST /api/agent/japan/summary-pdf
     */
    @PostMapping("/japan/summary-pdf")
    public Result<CaseSummaryVO> japanSummaryFromPdf(@RequestBody PdfRequest request) {
        CaseSummaryVO result = japanSummaryAgent.extractSummaryFromPdf(request.getPdfUrl());
        return Result.success(result);
    }

    /**
     * 日本案例：通过 PDF 链接进行重要性评分
     * POST /api/agent/japan/score-pdf
     */
    @PostMapping("/japan/score-pdf")
    public Result<CaseScoreVO> japanScoreFromPdf(@RequestBody PdfRequest request) {
        CaseScoreVO result = japanScoreAgent.scoreCaseFromPdf(request.getPdfUrl());
        return Result.success(result);
    }

    /**
     * 日本案例：通过文本提取摘要（已翻译的中文内容）
     * POST /api/agent/japan/summary
     */
    @PostMapping("/japan/summary")
    public Result<CaseSummaryVO> japanSummaryFromText(@RequestBody ContentRequest request) {
        CaseSummaryVO result = japanSummaryAgent.extractSummaryFromText(request.getContent());
        return Result.success(result);
    }

    /**
     * 日本案例：通过文本进行重要性评分
     * POST /api/agent/japan/score
     */
    @PostMapping("/japan/score")
    public Result<CaseScoreVO> japanScoreFromText(@RequestBody ContentRequest request) {
        CaseScoreVO result = japanScoreAgent.scoreCaseFromText(request.getContent());
        return Result.success(result);
    }

    public static class PdfRequest {
        private String pdfUrl;
        public String getPdfUrl() { return pdfUrl; }
        public void setPdfUrl(String pdfUrl) { this.pdfUrl = pdfUrl; }
    }

    public static class TranslateRequest {
        private String content;
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    public static class ContentRequest {
        private String content;
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
