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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 日本案例摘要提取 Agent：支持两种输入模式
 * <ol>
 *   <li>PDF 模式：直接上传日文裁判文书 PDF，在同一会话中提取中文摘要</li>
 *   <li>文本模式：接收已翻译的中文文本，分段阅读后提取摘要（兜底方案）</li>
 * </ol>
 *
 * 调用优先级（由 {@link CaseAgentService} 决定）：
 * <ul>
 *   <li>若翻译已完成且有 contentZh → 直接走文本模式（无需重复上传 PDF）</li>
 *   <li>若翻译失败但有 pdfUrl → 走 PDF 模式直接从原文提取摘要</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JapanSummaryAgent {

    @Value("${fastgpt.japan-summary-api-key}")
    private String apiKey;

    private final FastGptClient fastGptClient;
    private final ObjectMapper objectMapper;

    /** 每段最大字符数（文本模式分段阅读时使用） */
    private static final int MAX_CHUNK_SIZE = 6000;

    private static final String SYSTEM_PROMPT = """
            你是一名资深的法律分析专家，专门从事日本法律文书的分析工作。
            你将对一份日文裁判文书（判决书/决定书）进行摘要提取。
            请仔细阅读文书内容，准确理解日本法律术语和裁判结构，并以中文输出分析结果。
            """;

    /** PDF 模式第 1 轮：上传文件后让模型确认阅读 */
    private static final String PDF_READ_INSTRUCTION = """
            请仔细阅读上述日文裁判文书，充分理解其内容，包括：
            1. 当事人情况（原告/被告）
            2. 案件起因与事实经过
            3. 争议焦点与核心法律问题
            4. 法院的裁判理由
            5. 最终判决结果
            阅读完毕后，只需回复"已阅读完毕，准备提取摘要"，不要输出其他内容。
            """;

    /** 文本模式分段阅读时的系统提示 */
    private static final String TEXT_CHUNK_SYSTEM_PROMPT = """
            你是一名资深的法律分析专家，负责对一份完整的日本法律文书（已翻译为中文）进行摘要提取。
            由于文书较长，内容将分段发送给你，请仔细阅读每一段并记住关键信息。
            在收到所有分段内容后，你将收到一条汇总指令，届时再输出最终的JSON摘要。
            在收到汇总指令之前，每段只需回复"已阅读第X段，继续"，不要输出JSON。
            """;

    /** PDF 模式第 2 轮 / 文本模式汇总轮：输出 JSON 摘要 */
    private static final String EXTRACT_PROMPT = """
            请根据你刚才阅读的裁判文书，提取核心摘要信息，以严格的JSON格式输出，不要包含任何其他文字。

            输出格式：
            {
              "caseReason": "案由（简洁描述案件缘由，中文，不超过50字）",
              "disputeFocus": "争议焦点（列出主要争议点，中文，多个用分号分隔，不超过200字）",
              "judgmentResult": "判决结果（描述最终裁判结果，中文，不超过200字）",
              "keyPoints": "核心要点（3-5条关键法律要点，中文，用分号分隔）"
            }

            要求：
            1. 内容需准确、简洁，避免冗余
            2. 必须是合法的JSON格式，不得包含任何Markdown符号
            3. 所有字段值均用中文输出
            4. 如字段内容来自日文文书，须准确翻译为中文后填入
            """;

    // ==================== 公开方法 ====================

    /**
     * 【PDF 模式】通过 PDF 链接直接提取日文裁判文书摘要（中文输出）。
     * 适用于翻译失败但有 pdfUrl 的场景，或希望直接从原文提取摘要的场景。
     *
     * @param pdfUrl 日本裁判所 PDF 全文链接
     * @return 摘要VO（所有字段均为中文）
     */
    public CaseSummaryVO extractSummaryFromPdf(String pdfUrl) {
        if (pdfUrl == null || pdfUrl.isBlank()) {
            throw new IllegalArgumentException("pdfUrl 不能为空");
        }
        log.info("【PDF模式】开始提取日文裁判文书摘要，pdfUrl={}", pdfUrl);
        try {
            String chatId = UUID.randomUUID().toString();
            String fileName = extractFileName(pdfUrl);

            // 第 1 轮：上传 PDF，让模型阅读并确认
            log.info("第1轮：上传 PDF 并阅读，chatId={}", chatId);
            String readAck = fastGptClient.chatWithFile(
                    apiKey, SYSTEM_PROMPT, pdfUrl, fileName, PDF_READ_INSTRUCTION, chatId);
            log.info("PDF 阅读确认：{}", readAck);

            // 第 2 轮：请求输出 JSON 摘要
            log.info("第2轮：请求摘要 JSON，chatId={}", chatId);
            String rawJson = fastGptClient.chat(apiKey, null, EXTRACT_PROMPT, chatId);
            log.debug("摘要提取原始结果: {}", rawJson);

            return parseJsonResult(rawJson);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("【PDF模式】日文裁判文书摘要提取失败，pdfUrl={}", pdfUrl, e);
            throw new BusinessException(ResultCode.SUMMARY_FAIL, "摘要提取失败: " + e.getMessage());
        }
    }

    /**
     * 【文本模式】从已翻译的中文文本中提取摘要。
     * 内容超过 {@value #MAX_CHUNK_SIZE} 时自动分段阅读后汇总输出。
     * 兜底方案：翻译成功后优先使用此模式，避免重复上传 PDF。
     *
     * @param caseContent 已翻译的中文案例文本
     * @return 摘要VO
     */
    public CaseSummaryVO extractSummaryFromText(String caseContent) {
        if (caseContent == null || caseContent.isBlank()) {
            throw new IllegalArgumentException("caseContent 不能为空");
        }
        log.info("【文本模式】开始提取日本案例摘要，内容长度={}", caseContent.length());
        try {
            String rawJson;
            if (caseContent.length() <= MAX_CHUNK_SIZE) {
                rawJson = fastGptClient.chat(
                        apiKey, TEXT_CHUNK_SYSTEM_PROMPT, caseContent + "\n\n" + EXTRACT_PROMPT);
            } else {
                rawJson = extractInChunks(caseContent);
            }
            log.debug("摘要提取原始结果: {}", rawJson);
            return parseJsonResult(rawJson);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("【文本模式】日本案例摘要提取失败", e);
            throw new BusinessException(ResultCode.SUMMARY_FAIL, "摘要提取失败: " + e.getMessage());
        }
    }

    // ==================== 私有辅助方法 ====================

    /** 分段阅读长文本，最后发送汇总指令获取 JSON */
    private String extractInChunks(String caseContent) {
        List<String> chunks = splitIntoChunks(caseContent);
        log.info("内容过长，分为 {} 段阅读后提取摘要", chunks.size());
        String chatId = UUID.randomUUID().toString();

        for (int i = 0; i < chunks.size(); i++) {
            String userMsg = String.format("第%d段（共%d段）：\n%s", i + 1, chunks.size(), chunks.get(i));
            log.info("发送第 {}/{} 段，长度={}", i + 1, chunks.size(), chunks.get(i).length());
            fastGptClient.chat(apiKey, TEXT_CHUNK_SYSTEM_PROMPT, userMsg, chatId);
        }

        log.info("所有段落已发送，请求输出摘要 JSON");
        return fastGptClient.chat(apiKey, TEXT_CHUNK_SYSTEM_PROMPT, EXTRACT_PROMPT, chatId);
    }

    private CaseSummaryVO parseJsonResult(String result) throws Exception {
        result = result.trim();
        if (result.startsWith("```json")) result = result.substring(7);
        else if (result.startsWith("```")) result = result.substring(3);
        if (result.endsWith("```")) result = result.substring(0, result.length() - 3);
        result = result.trim();

        JsonNode node = objectMapper.readTree(result);
        CaseSummaryVO vo = new CaseSummaryVO();
        vo.setCaseReason(node.path("caseReason").asText());
        vo.setDisputeFocus(node.path("disputeFocus").asText());
        vo.setJudgmentResult(node.path("judgmentResult").asText());
        vo.setKeyPoints(node.path("keyPoints").asText());
        log.info("摘要提取完成，caseReason={}", vo.getCaseReason());
        return vo;
    }

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
