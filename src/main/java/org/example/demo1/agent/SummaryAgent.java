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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    /** 每段最大字符数，与 TranslationAgent 保持一致 */
    private static final int MAX_CHUNK_SIZE = 6000;

    private static final String SYSTEM_PROMPT = """
            你是一名资深的法律分析专家，负责对一份完整的法律文书进行摘要提取。
            由于文书较长，内容将分段发送给你，请仔细阅读每一段并记住关键信息。
            在收到所有分段内容后，你将收到一条汇总指令，届时再输出最终的JSON摘要。
            在收到汇总指令之前，每段只需回复"已阅读第X段，继续"，不要输出JSON。
            """;

    private static final String EXTRACT_PROMPT = """
            以上是完整的法律文书内容，请综合所有段落，提取核心信息并以严格的JSON格式输出，不要包含任何其他文字。

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
            5. 必须是合法的JSON格式，不得包含任何Markdown符号
            """;

    /**
     * 从案例内容中提取核心摘要（含案由、争议焦点、判决结果）
     * 内容超过 MAX_CHUNK_SIZE 时自动分段阅读，最终汇总输出 JSON
     *
     * @param caseContent 案例内容（中文或英文均可）
     * @return 摘要VO
     */
    public CaseSummaryVO extractSummary(String caseContent) {
        log.info("开始提取案例摘要，内容长度: {}", caseContent.length());
        try {
            String jsonResult;
            if (caseContent.length() <= MAX_CHUNK_SIZE) {
                // 内容较短，直接提取
                jsonResult = fastGptClient.chat(apiKey, SYSTEM_PROMPT, caseContent + "\n\n" + EXTRACT_PROMPT);
            } else {
                jsonResult = extractSummaryInChunks(caseContent);
            }

            log.debug("摘要提取原始结果: {}", jsonResult);
            return parseJsonResult(jsonResult);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("摘要提取失败", e);
            throw new BusinessException(ResultCode.SUMMARY_FAIL, "摘要提取失败: " + e.getMessage());
        }
    }

    /**
     * 分段阅读文书，最后发送汇总指令获取 JSON 摘要
     */
    private String extractSummaryInChunks(String caseContent) {
        List<String> chunks = splitIntoChunks(caseContent);
        log.info("内容过长，分为 {} 段阅读后提取摘要", chunks.size());

        String chatId = UUID.randomUUID().toString();
        log.info("摘要提取会话 chatId: {}", chatId);

        // 逐段发送，让 AI 阅读并记住内容
        for (int i = 0; i < chunks.size(); i++) {
            String userMsg = String.format("第%d段（共%d段）：\n%s", i + 1, chunks.size(), chunks.get(i));
            log.info("发送第 {}/{} 段，长度: {}", i + 1, chunks.size(), chunks.get(i).length());
            fastGptClient.chat(apiKey, SYSTEM_PROMPT, userMsg, chatId);
        }

        // 所有段落发送完毕，发送汇总指令
        log.info("所有段落已发送，请求 AI 输出摘要 JSON");
        return fastGptClient.chat(apiKey, SYSTEM_PROMPT, EXTRACT_PROMPT, chatId);
    }

    /**
     * 解析 AI 返回的 JSON 字符串，清理 Markdown 标记后映射为 VO
     */
    private CaseSummaryVO parseJsonResult(String result) throws Exception {
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
    }

    /**
     * 将长文本按段落切分为不超过 MAX_CHUNK_SIZE 的片段
     */
    private List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\n\n");
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            if (para.isBlank()) continue;

            if (para.length() > MAX_CHUNK_SIZE) {
                if (!current.isEmpty()) {
                    chunks.add(current.toString().trim());
                    current = new StringBuilder();
                }
                for (int i = 0; i < para.length(); i += MAX_CHUNK_SIZE) {
                    chunks.add(para.substring(i, Math.min(i + MAX_CHUNK_SIZE, para.length())));
                }
            } else if (current.length() + para.length() + 2 > MAX_CHUNK_SIZE) {
                chunks.add(current.toString().trim());
                current = new StringBuilder();
                current.append(para).append("\n\n");
            } else {
                current.append(para).append("\n\n");
            }
        }

        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }
}
