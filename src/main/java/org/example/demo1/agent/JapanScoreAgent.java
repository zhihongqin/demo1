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

import java.net.URI;
import java.util.UUID;

/**
 * 日本案例重要性评分 Agent：支持两种输入模式
 * <ol>
 *   <li>PDF 模式：直接上传日文裁判文书 PDF，在同一会话中进行多维度评分</li>
 *   <li>文本模式：接收案例摘要/元数据文本，直接评分（兜底方案）</li>
 * </ol>
 *
 * 调用优先级（由 {@link CaseAgentService} 决定）：
 * <ul>
 *   <li>若有摘要数据可组装评分内容 → 走文本模式（轻量高效）</li>
 *   <li>若无摘要数据但有 pdfUrl → 走 PDF 模式直接从原文评分</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JapanScoreAgent {

    @Value("${fastgpt.japan-score-api-key}")
    private String apiKey;

    private final FastGptClient fastGptClient;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            你是一名专业的涉外法律案例评估专家，专注于日本法律案例的重要性评估。
            你将对一份日文裁判文书进行多维度重要性评分，重点关注：
            1. 该判决对日本法律实践的影响程度
            2. 判决在日本司法体系中的地位与里程碑意义
            3. 对中国企业、组织或涉外当事人的参考和借鉴价值
            评分结果须以中文输出。
            """;

    /** PDF 模式第 1 轮：上传文件后让模型阅读确认 */
    private static final String PDF_READ_INSTRUCTION = """
            请仔细阅读上述日文裁判文书，充分理解其法律背景、案件事实、裁判理由和判决结果，
            以便准确评估该案例的法律重要性和参考价值。
            阅读完毕后，只需回复"已阅读完毕，准备进行重要性评分"，不要输出其他内容。
            """;

    /** PDF 模式第 2 轮 / 文本模式：输出 JSON 评分 */
    private static final String SCORE_PROMPT = """
            请对该日本法律案例进行多维度重要性评分，以严格的JSON格式输出，不要包含任何其他文字。

            评分维度（均为0-100分）：
            - importanceScore：案例重要性（对日本法律实践的影响程度）
            - influenceScore：影响力评分（判决影响范围、是否具有里程碑意义）
            - referenceScore：参考价值评分（对中国企业或涉外当事人的参考价值）
            - totalScore：综合评分（三个维度的加权平均，保留整数）

            输出格式：
            {
              "importanceScore": 85,
              "influenceScore": 70,
              "referenceScore": 90,
              "totalScore": 82,
              "scoreReason": "评分理由（中文，详细说明各维度评分依据，不超过300字）",
              "scoreTags": "案例标签（中文，3-5个关键标签，英文逗号分隔，如：里程碑案例,跨境侵权,合同纠纷）"
            }

            评分标准：
            90-100：极具重要性，属于里程碑式案例
            70-89：重要性较高，有显著参考价值
            50-69：中等重要性，有一定参考价值
            30-49：重要性一般
            0-29：重要性较低
            """;

    // ==================== 公开方法 ====================

    /**
     * 【PDF 模式】通过 PDF 链接直接对日文裁判文书进行重要性评分。
     * 适用于无摘要数据时的降级场景，或希望直接从原文进行深度评分的场景。
     *
     * @param pdfUrl 日本裁判所 PDF 全文链接
     * @return 评分VO
     */
    public CaseScoreVO scoreCaseFromPdf(String pdfUrl) {
        if (pdfUrl == null || pdfUrl.isBlank()) {
            throw new IllegalArgumentException("pdfUrl 不能为空");
        }
        log.info("【PDF模式】开始对日文裁判文书进行重要性评分，pdfUrl={}", pdfUrl);
        try {
            String chatId = UUID.randomUUID().toString();
            String fileName = extractFileName(pdfUrl);

            // 第 1 轮：上传 PDF，让模型阅读并确认
            log.info("第1轮：上传 PDF 并阅读，chatId={}", chatId);
            String readAck = fastGptClient.chatWithFile(
                    apiKey, SYSTEM_PROMPT, pdfUrl, fileName, PDF_READ_INSTRUCTION, chatId);
            log.info("PDF 阅读确认：{}", readAck);

            // 第 2 轮：请求输出 JSON 评分
            log.info("第2轮：请求评分 JSON，chatId={}", chatId);
            String rawJson = fastGptClient.chat(apiKey, null, SCORE_PROMPT, chatId);
            log.debug("评分原始结果: {}", rawJson);

            return parseJsonResult(rawJson);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("【PDF模式】日文裁判文书评分失败，pdfUrl={}", pdfUrl, e);
            throw new BusinessException(ResultCode.SCORE_FAIL, "重要性评分失败: " + e.getMessage());
        }
    }

    /**
     * 【文本模式】对已组装好的案例文本（摘要+元数据）进行重要性评分。
     * 适用于已有摘要数据的场景，轻量高效，无需重新上传 PDF。
     *
     * @param caseContent 案例内容（建议包含标题、案由、争议焦点、判决结果等）
     * @return 评分VO
     */
    public CaseScoreVO scoreCaseFromText(String caseContent) {
        if (caseContent == null || caseContent.isBlank()) {
            throw new IllegalArgumentException("caseContent 不能为空");
        }
        log.info("【文本模式】开始对日本案例进行重要性评分，内容长度={}", caseContent.length());
        try {
            String combined = caseContent + "\n\n" + SCORE_PROMPT;
            String rawJson = fastGptClient.chat(apiKey, SYSTEM_PROMPT, combined);
            log.debug("评分原始结果: {}", rawJson);
            return parseJsonResult(rawJson);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("【文本模式】日本案例评分失败", e);
            throw new BusinessException(ResultCode.SCORE_FAIL, "重要性评分失败: " + e.getMessage());
        }
    }

    // ==================== 私有辅助方法 ====================

    private CaseScoreVO parseJsonResult(String result) throws Exception {
        result = result.trim();
        if (result.startsWith("```json")) result = result.substring(7);
        else if (result.startsWith("```")) result = result.substring(3);
        if (result.endsWith("```")) result = result.substring(0, result.length() - 3);
        result = result.trim();

        JsonNode node = objectMapper.readTree(result);
        CaseScoreVO vo = new CaseScoreVO();
        vo.setImportanceScore(node.path("importanceScore").asInt());
        vo.setInfluenceScore(node.path("influenceScore").asInt());
        vo.setReferenceScore(node.path("referenceScore").asInt());
        vo.setTotalScore(node.path("totalScore").asInt());
        vo.setScoreReason(node.path("scoreReason").asText());
        vo.setScoreTags(node.path("scoreTags").asText());
        log.info("评分完成: totalScore={}", vo.getTotalScore());
        return vo;
    }

    private static String extractFileName(String pdfUrl) {
        try {
            String path = URI.create(pdfUrl).getPath();
            String name = path.substring(path.lastIndexOf('/') + 1);
            return name.isBlank() ? "case.pdf" : name;
        } catch (Exception e) {
            return "case.pdf";
        }
    }
}
