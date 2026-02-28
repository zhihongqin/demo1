package org.example.demo1.controller;

import lombok.RequiredArgsConstructor;
import org.example.demo1.agent.ScoreAgent;
import org.example.demo1.agent.SummaryAgent;
import org.example.demo1.agent.TranslationAgent;
import org.example.demo1.common.result.Result;
import org.example.demo1.vo.CaseScoreVO;
import org.example.demo1.vo.CaseSummaryVO;
import org.springframework.web.bind.annotation.*;

/**
 * Agent 直接调用接口（可用于测试和前端直接请求）
 */
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private final TranslationAgent translationAgent;
    private final SummaryAgent summaryAgent;
    private final ScoreAgent scoreAgent;

    /**
     * 直接翻译文本（英→中）
     * POST /api/agent/translate
     */
    @PostMapping("/translate")
    public Result<String> translate(@RequestBody TranslateRequest request) {
        String result = translationAgent.translateToZh(request.getContent());
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
