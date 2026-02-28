package org.example.demo1.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.ResultCode;
import org.example.demo1.vo.CaseSummaryVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 摘要提取 Agent：提取案由、争议焦点、判决结果
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummaryAgent {

    @Value("${fastgpt.summary-api-key}")
    private String apiKey;

    private final FastGptClient fastGptClient;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            你是一名资深的法律分析专家。请从用户提供的法律案例内容中提取核心信息，并以严格的JSON格式输出，不要包含任何其他文字。
            
            输出格式：
            {
              "caseReason": "案由（简洁描述案件缘由）",
              "disputeFocus": "争议焦点（列出主要争议点，多个用分号分隔）",
              "judgmentResult": "判决结果（描述最终裁判结果）",
              "keyPoints": "核心要点（3-5条关键法律要点，用分号分隔）"
            }
            
            要求：
            1. 内容需准确、简洁，避免冗余
            2. 案由不超过50字
            3. 争议焦点不超过200字
            4. 判决结果不超过200字
            5. 必须是合法的JSON格式
            """;

    /**
     * 从案例内容中提取核心摘要（含案由、争议焦点、判决结果）
     *
     * @param caseContent 案例内容（中文或英文均可）
     * @return 摘要VO
     */
    public CaseSummaryVO extractSummary(String caseContent) {
        log.info("开始提取案例摘要，内容长度: {}", caseContent.length());
        try {
            String result = fastGptClient.chat(apiKey, SYSTEM_PROMPT, caseContent);
            log.debug("摘要提取原始结果: {}", result);

            // 清理可能的Markdown代码块标记
            result = result.trim();
            if (result.startsWith("```json")) {
                result = result.substring(7);
            } else if (result.startsWith("```")) {
                result = result.substring(3);
            }
            if (result.endsWith("```")) {
                result = result.substring(0, result.length() - 3);
            }
            result = result.trim();

            JsonNode jsonNode = objectMapper.readTree(result);
            CaseSummaryVO vo = new CaseSummaryVO();
            vo.setCaseReason(jsonNode.path("caseReason").asText());
            vo.setDisputeFocus(jsonNode.path("disputeFocus").asText());
            vo.setJudgmentResult(jsonNode.path("judgmentResult").asText());
            vo.setKeyPoints(jsonNode.path("keyPoints").asText());

            log.info("摘要提取完成");
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("摘要提取失败", e);
            throw new BusinessException(ResultCode.SUMMARY_FAIL, "摘要提取失败: " + e.getMessage());
        }
    }
}
