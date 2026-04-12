package org.example.demo1.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 日本案例翻译 Agent：日文法律文书（PDF）→ 中文
 *
 * 与 {@link TranslationAgent} 的主要区别：
 * <ol>
 *   <li>正文来源：通过 pdfUrl 上传文件至 FastGPT，而非直接传递文本</li>
 *   <li>翻译语言对：日文 → 中文（而非英文 → 中文）</li>
 *   <li>元号处理：令和/平成/昭和等年号需附注公历年份</li>
 * </ol>
 *
 * 降级策略：若 pdfUrl 为空或 FastGPT 文件接口不可用，
 * 调用方（{@link CaseAgentService}）应退回使用 {@link TranslationAgent} 处理已存的 contentEn 文本。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JapanTranslationAgent {

    @Value("${fastgpt.japan-translation-api-key}")
    private String apiKey;

    private final FastGptClient fastGptClient;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            你是一名专业的法律翻译专家，擅长日文法律文书的翻译工作。
            本次任务为将一份完整的日文裁判文书（判决书/决定书）翻译为规范的中文法律文书。

            【翻译要求】
            1. 保持法律术语的准确性和专业性，术语在全文中必须保持一致
            2. 严格保留原文的段落结构，不合并、不拆分段落
            3. 元号年份（令和、平成、昭和、大正、明治）翻译时在括号内附注公历年份
               示例："令和6年3月15日" 译为 "令和6年（2024年）3月15日"
            4. 当事人姓名、机构名称、案件编号保留日文原文并在括号内注明中文含义
               示例："株式会社○○（○○股份有限公司）"
            5. 日本法律条文引用（如"民法第709条"）保留原文并括注中文法律名称
            6. 使用中国法律语境下的规范表达，例如"原告/被告"而非"申请人/相对方"
            7. 只输出翻译结果，不添加任何额外说明、注释或前缀

            【格式要求】
            1. 禁止使用任何 Markdown 格式符号，包括但不限于：#、##、###、**、*、`、>、---
            2. 段落之间使用空行分隔，与原文保持一致
            3. 不得添加"译文："、"翻译："等任何标注性文字
            """;

    private static final String TITLE_SYSTEM_PROMPT = """
            你是一名专业的法律翻译专家。请将用户提供的日文法律案件名称准确翻译为中文。
            要求：
            1. 保持法律术语的准确性和专业性
            2. 当事人姓名保留日文原文并在括号内注明中文（如需要）
            3. 只输出翻译后的中文标题，不添加任何额外说明或标点
            """;

    /** 每轮翻译的页数 */
    private static final int PAGES_PER_ROUND = 5;

    /** 最大翻译轮次，对应最多 MAX_ROUNDS * PAGES_PER_ROUND = 150 页 */
    private static final int MAX_ROUNDS = 30;

    /**
     * 模型翻译完指定页后，若文档仍有后续页则输出此标记，否则输出 [DONE]。
     * 通过标记而非依赖截断检测，结果更可控。
     */
    private static final String CONTINUE_MARKER = "[CONTINUE]";
    private static final String DONE_MARKER = "[DONE]";

    /** 第 1 轮指令模板：携带 PDF，指定翻译页码范围 */
    private static final String FIRST_INSTRUCTION_TEMPLATE =
            "请翻译上述日文裁判文书第 %d 至第 %d 页的内容（仅翻译这几页，不要超出范围）。\n" +
            "【格式要求（必须严格遵守）】\n" +
            "1. 禁止使用任何 Markdown 格式符号，包括 #、##、###、**、*、`、>、--- 等\n" +
            "2. 段落之间使用空行分隔，与原文保持一致\n" +
            "3. 只输出翻译结果，不添加任何说明、注释、标注或前缀\n" +
            "翻译完成后：\n" +
            "- 若文档还有第 %d 页及以后的内容，在末尾单独一行输出 " + CONTINUE_MARKER + "\n" +
            "- 若文档到此已结束（不足 %d 页），在末尾单独一行输出 " + DONE_MARKER;

    /** 后续轮次指令模板：纯文字，通过 chatId 复用上下文，并重申格式要求 */
    private static final String NEXT_INSTRUCTION_TEMPLATE =
            "请翻译第 %d 至第 %d 页的内容（仅翻译这几页，不要超出范围）。\n" +
            "【格式要求（必须严格遵守）】\n" +
            "1. 禁止使用任何 Markdown 格式符号，包括 #、##、###、**、*、`、>、--- 等\n" +
            "2. 段落之间使用空行分隔，与原文保持一致\n" +
            "3. 只输出翻译结果，不添加任何说明、注释、标注或前缀\n" +
            "翻译完成后：\n" +
            "- 若文档还有第 %d 页及以后的内容，在末尾单独一行输出 " + CONTINUE_MARKER + "\n" +
            "- 若文档到此已结束（不足 %d 页），在末尾单独一行输出 " + DONE_MARKER;

    /** 翻译完成后，在同一会话中追加的字段提取指令 */
    private static final String ENRICH_INSTRUCTION = """
            请根据你刚才翻译的这份日文裁判文书，提取以下结构化信息，以严格的JSON格式输出，不要包含任何其他文字：
            {
              "caseType": 1,
              "caseReason": "案由简述",
              "keywords": "关键词1,关键词2,关键词3",
              "legalProvisions": "法律条文1,法律条文2"
            }
            字段说明：
            - caseType：案件类型，只能填1（民事）、2（刑事）、3（行政）、4（商事）之一，无法判断填1
            - caseReason：案由，简短描述本案核心法律争议，不超过30字
            - keywords：核心关键词3-8个，中文，英文逗号分隔
            - legalProvisions：涉及的主要日本法律条文（保留日文原名并附中文名），多个用英文逗号分隔，不超过5条，无明确条文则填"无"
            """;

    /**
     * 通过 PDF 链接翻译日文裁判文书为中文，并在同一会话中顺带提取结构化字段。
     * 采用"每 {@value #PAGES_PER_ROUND} 页一轮"的多轮对话策略。
     *
     * @param pdfUrl 日本裁判所 PDF 全文链接
     * @return 包含翻译正文及提取字段的结果对象
     */
    public TranslationResult translatePdfToZh(String pdfUrl) {
        if (pdfUrl == null || pdfUrl.isBlank()) {
            throw new IllegalArgumentException("pdfUrl 不能为空");
        }

        log.info("开始日文 PDF 分页翻译（每轮 {} 页），pdfUrl={}", PAGES_PER_ROUND, pdfUrl);

        String chatId = UUID.randomUUID().toString();
        String fileName = extractFileName(pdfUrl);
        List<String> parts = new ArrayList<>();

        int pageFrom = 1;
        int pageTo = PAGES_PER_ROUND;

        // 第 1 轮：携带 file_url
        String firstInstruction = String.format(FIRST_INSTRUCTION_TEMPLATE,
                pageFrom, pageTo, pageTo + 1, pageTo + 1);
        log.info("翻译第 1 轮（第 {}-{} 页），chatId={}", pageFrom, pageTo, chatId);

        String round1 = fastGptClient.chatWithFile(
                apiKey, SYSTEM_PROMPT, pdfUrl, fileName, firstInstruction, chatId);

        if (round1 == null || round1.isBlank()) {
            log.warn("第 1 轮翻译结果为空，pdfUrl={}", pdfUrl);
            TranslationResult empty = new TranslationResult();
            empty.setContentZh("");
            return empty;
        }

        parts.add(stripMarkers(round1));
        log.info("第 1 轮完成，长度={}", round1.length());

        if (!isDone(round1)) {
            // 后续翻译轮次
            for (int round = 2; round <= MAX_ROUNDS; round++) {
                pageFrom = pageTo + 1;
                pageTo = pageFrom + PAGES_PER_ROUND - 1;

                String instruction = String.format(NEXT_INSTRUCTION_TEMPLATE,
                        pageFrom, pageTo, pageTo + 1, pageTo + 1);
                log.info("翻译第 {} 轮（第 {}-{} 页），chatId={}", round, pageFrom, pageTo, chatId);

                String result = fastGptClient.chat(apiKey, null, instruction, chatId);

                if (result == null || result.isBlank()) {
                    log.warn("第 {} 轮翻译结果为空，停止", round);
                    break;
                }

                parts.add(stripMarkers(result));
                log.info("第 {} 轮完成，长度={}", round, result.length());

                if (isDone(result)) {
                    log.info("文档已全部翻译完成（第 {} 轮，共翻译至第 {} 页）", round, pageTo);
                    break;
                }

                if (round == MAX_ROUNDS) {
                    log.warn("已达最大轮次 {}（{}页），翻译可能不完整，pdfUrl={}",
                            MAX_ROUNDS, MAX_ROUNDS * PAGES_PER_ROUND, pdfUrl);
                }
            }
        }

        String contentZh = String.join("\n\n", parts);
        log.info("翻译阶段完成，共 {} 轮，总长度={}", parts.size(), contentZh.length());

        // 字段提取：利用同一 chatId，模型已有完整文档上下文，无需重传内容
        TranslationResult result = new TranslationResult();
        result.setContentZh(contentZh);
        extractEnrichFields(chatId, result);

        return result;
    }

    /**
     * 在已有翻译会话中追加字段提取请求，将结果填入 result 对象。
     * 提取失败不抛异常，仅记录日志，由调用方降级处理。
     */
    private void extractEnrichFields(String chatId, TranslationResult result) {
        log.info("开始字段提取（复用翻译会话），chatId={}", chatId);
        try {
            String raw = fastGptClient.chat(apiKey, null, ENRICH_INSTRUCTION, chatId);
            if (raw == null || raw.isBlank()) {
                log.warn("字段提取返回为空，chatId={}", chatId);
                return;
            }

            String json = cleanJson(raw);
            JsonNode node = objectMapper.readTree(json);

            int caseType = node.path("caseType").asInt(1);
            result.setCaseType(caseType >= 1 && caseType <= 4 ? caseType : 1);
            result.setCaseReason(node.path("caseReason").asText(""));
            result.setKeywords(node.path("keywords").asText(""));
            result.setLegalProvisions(node.path("legalProvisions").asText(""));

            log.info("字段提取完成，caseType={}, caseReason={}", result.getCaseType(), result.getCaseReason());
        } catch (Exception e) {
            log.error("字段提取失败，chatId={}，错误: {}", chatId, e.getMessage());
        }
    }

    private static String cleanJson(String raw) {
        raw = raw.trim();
        if (raw.startsWith("```json")) raw = raw.substring(7);
        else if (raw.startsWith("```")) raw = raw.substring(3);
        if (raw.endsWith("```")) raw = raw.substring(0, raw.length() - 3);
        return raw.trim();
    }

    private static boolean isDone(String text) {
        return text.contains(DONE_MARKER) || !text.contains(CONTINUE_MARKER);
    }

    /** 去除 [CONTINUE] 和 [DONE] 标记及其所在行 */
    private static String stripMarkers(String text) {
        return text.lines()
                .filter(line -> !line.strip().equals(CONTINUE_MARKER)
                        && !line.strip().equals(DONE_MARKER))
                .collect(Collectors.joining("\n"))
                .stripTrailing();
    }

    /** 从 URL 路径中提取文件名，提取失败时返回默认值 */
    private static String extractFileName(String pdfUrl) {
        try {
            String path = URI.create(pdfUrl).getPath();
            String name = path.substring(path.lastIndexOf('/') + 1);
            return name.isBlank() ? "case.pdf" : name;
        } catch (Exception e) {
            return "case.pdf";
        }
    }

    /**
     * 翻译日文案件标题为中文
     *
     * @param japaneseTitle 日文标题（事件名）
     * @return 中文标题
     */
    public String translateTitle(String japaneseTitle) {
        if (japaneseTitle == null || japaneseTitle.isBlank()) {
            return "";
        }
        log.info("开始翻译日文案件标题: {}", japaneseTitle);
        try {
            String result = fastGptClient.chat(apiKey, TITLE_SYSTEM_PROMPT, japaneseTitle);
            if (result == null || result.isBlank()) {
                log.warn("标题翻译结果为空，原标题: {}", japaneseTitle);
                return "";
            }
            String title = result.trim();
            log.info("日文标题翻译完成: {}", title);
            return title;
        } catch (Exception e) {
            log.error("日文标题翻译失败，原标题: {}", japaneseTitle, e);
            throw e;
        }
    }
}
