package org.example.demo1.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.ResultCode;
import org.example.demo1.vo.CaseScoreVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 重要性评分 Agent：对涉外法律案例进行多维度评分
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreAgent {

    @Value("${fastgpt.score-api-key}")
    private String apiKey;

    private final FastGptClient fastGptClient;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            你是一名专业的涉外法律案例评估专家。请对用户提供的法律案例进行多维度评分，并以严格的JSON格式输出，不要包含任何其他文字。
            
            评分维度（均为0-100分）：
            - importanceScore：案例重要性（对法律实践的影响程度）
            - influenceScore：影响力评分（判决影响范围、是否具有里程碑意义）
            - referenceScore：参考价值评分（对类似案件的参考价值）
            - totalScore：综合评分（三个维度的加权平均）
            
            输出格式：
            {
              "importanceScore": 85,
              "influenceScore": 70,
              "referenceScore": 90,
              "totalScore": 82,
              "scoreReason": "评分理由（详细说明各维度评分依据，不超过300字）",
              "scoreTags": "案例标签（3-5个关键标签，逗号分隔，如：里程碑案例,跨境侵权,合同纠纷）"
            }
            
            评分标准：
            90-100：极具重要性，属于里程碑式案例
            70-89：重要性较高，有显著参考价值
            50-69：中等重要性，有一定参考价值
            30-49：重要性一般
            0-29：重要性较低
            """;

    /**
     * 对案例进行重要性评分
     *
     * @param caseContent 案例内容（建议使用中文摘要+判决结果）
     * @return 评分VO
     */
    public CaseScoreVO scoreCase(String caseContent) {
        log.info("开始对案例进行重要性评分，内容长度: {}", caseContent.length());
        try {
            String result = fastGptClient.chat(apiKey, SYSTEM_PROMPT, caseContent);
            log.debug("评分原始结果: {}", result);

            // 清理Markdown代码块标记
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
            CaseScoreVO vo = new CaseScoreVO();
            vo.setImportanceScore(jsonNode.path("importanceScore").asInt());
            vo.setInfluenceScore(jsonNode.path("influenceScore").asInt());
            vo.setReferenceScore(jsonNode.path("referenceScore").asInt());
            vo.setTotalScore(jsonNode.path("totalScore").asInt());
            vo.setScoreReason(jsonNode.path("scoreReason").asText());
            vo.setScoreTags(jsonNode.path("scoreTags").asText());

            log.info("评分完成: totalScore={}", vo.getTotalScore());
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("重要性评分失败", e);
            throw new BusinessException(ResultCode.SCORE_FAIL, "重要性评分失败: " + e.getMessage());
        }
    }
}
