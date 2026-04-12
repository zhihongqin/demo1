package org.example.demo1.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 翻译 Agent：英文法律案例 → 中文
 * 超长内容自动分段翻译后拼接；翻译完成后在同一会话中顺带提取结构化字段，
 * 无需再单独调用 CaseEnrichAgent。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TranslationAgent {

    @Value("${fastgpt.translation-api-key}")
    private String apiKey;

    private final FastGptClient fastGptClient;
    private final ObjectMapper objectMapper;

    /** 每段最大字符数（英文约 6000 字符 ≈ 1500 tokens，留出 prompt 和回复空间） */
    private static final int MAX_CHUNK_SIZE = 6000;

    private static final String SYSTEM_PROMPT = """
            你是一名专业的法律翻译专家，擅长涉外法律文书的翻译工作。
            本次翻译任务为分段翻译一份完整的英文法律文书，你将逐段收到原文内容，请结合已翻译的历史内容保持全文一致性。

            【翻译要求】
            1. 保持法律术语的准确性和专业性，术语在全文中必须保持一致
            2. 严格保留原文的段落结构，不合并、不拆分段落
            3. 对专有名词（人名、机构名、案件编号）保留英文并在括号内注明中文含义
            4. 使用中国法律语境下的规范表达
            5. 只输出翻译结果，不添加任何额外说明、注释或前缀

            【格式要求】
            1. 禁止使用任何 Markdown 格式符号，包括但不限于：#、##、###、**、*、`、>、---
            2. 段落之间使用空行分隔，与原文保持一致
            3. 不得添加"第X段"、"译文："等任何标注性文字
            """;

    private static final String TITLE_SYSTEM_PROMPT = """
            你是一名专业的法律翻译专家。请将用户提供的英文法律案例标题准确翻译为中文。
            要求：
            1. 保持法律术语的准确性和专业性
            2. 对当事人姓名保留英文并在括号内注明（如需要）
            3. 只输出翻译后的中文标题，不添加任何额外说明或标点
            """;

    /** 翻译完成后，在同一会话中追加的字段提取指令 */
    private static final String ENRICH_INSTRUCTION = """
            请根据你刚才翻译的这份法律案例内容，提取以下结构化信息，以严格的JSON格式输出，不要包含任何其他文字：
            {
              "caseType": 1,
              "caseReason": "案由简述",
              "keywords": "关键词1,关键词2,关键词3",
              "legalProvisions": "法律条文1,法律条文2",
              "country": "国家/地区名称",
              "court": "法院名称"
            }
            字段说明：
            - caseType：案件类型，只能填1（民事）、2（刑事）、3（行政）、4（商事）之一，无法判断填1
            - caseReason：案由，简短描述本案核心法律争议，不超过30字
            - keywords：核心关键词3-8个，中文，英文逗号分隔
            - legalProvisions：涉及的主要法律条文或法规名称（保留原文名并附中文名），多个用英文逗号分隔，不超过5条，无明确条文则填"无"
            - country：案件所属国家或地区（中文名称，如：美国、英国、中国香港）
            - court：审理该案的法院名称（中文译名，保留英文原名在括号内，如：美国联邦第九巡回上诉法院(9th Cir.)）
            """;

    /**
     * 翻译案例标题（英文→中文）
     */
    public String translateTitle(String englishTitle) {
        if (englishTitle == null || englishTitle.isBlank()) {
            return "";
        }
        log.info("开始翻译案例标题: {}", englishTitle);
        try {
            String result = fastGptClient.chat(apiKey, TITLE_SYSTEM_PROMPT, englishTitle);
            if (result == null || result.isBlank()) {
                log.warn("标题翻译结果为空，原标题: {}", englishTitle);
                return "";
            }
            String title = result.trim();
            log.info("标题翻译完成: {}", title);
            return title;
        } catch (Exception e) {
            log.error("标题翻译失败，原标题: {}", englishTitle, e);
            throw e;
        }
    }

    /**
     * 将英文法律案例内容翻译为中文，并在同一会话中顺带提取结构化字段。
     * 内容超过 MAX_CHUNK_SIZE 时自动分段翻译后拼接。
     *
     * @param englishContent 英文原文
     * @return 包含翻译正文及提取字段的结果对象
     */
    public TranslationResult translateToZh(String englishContent) {
        if (englishContent == null || englishContent.isBlank()) {
            TranslationResult empty = new TranslationResult();
            empty.setContentZh("");
            return empty;
        }

        log.info("开始翻译案例，内容长度: {}", englishContent.length());

        List<String> chunks = splitIntoChunks(englishContent);
        String chatId = UUID.randomUUID().toString();
        log.info("共 {} 段，翻译会话 chatId: {}", chunks.size(), chatId);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            log.info("翻译第 {}/{} 段，长度: {}", i + 1, chunks.size(), chunks.get(i).length());
            String translated = translateChunk(chunks.get(i), chatId);
            sb.append(translated);
            if (i < chunks.size() - 1) {
                sb.append("\n\n");
            }
        }

        String contentZh = sb.toString();
        log.info("翻译完成，共 {} 段，结果长度: {}", chunks.size(), contentZh.length());

        TranslationResult result = new TranslationResult();
        result.setContentZh(contentZh);

        // 字段提取：复用同一 chatId，模型已有完整文档上下文，无需重传内容
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
            result.setCountry(node.path("country").asText(""));
            result.setCourt(node.path("court").asText(""));

            log.info("字段提取完成，caseType={}, country={}, court={}",
                    result.getCaseType(), result.getCountry(), result.getCourt());
        } catch (Exception e) {
            log.error("字段提取失败，chatId={}，错误: {}", chatId, e.getMessage());
        }
    }

    private String translateChunk(String chunk, String chatId) {
        try {
            String result = fastGptClient.chat(apiKey, SYSTEM_PROMPT, chunk, chatId);
            if (result == null || result.isBlank()) {
                log.warn("当前片段翻译结果为空，长度: {}", chunk.length());
                return "";
            }
            return result;
        } catch (Exception e) {
            log.error("片段翻译失败，长度: {}", chunk.length(), e);
            throw e;
        }
    }

    /**
     * 将长文本按段落切分为不超过 MAX_CHUNK_SIZE 的片段。
     * 优先按双换行（段落）切分；单段超长时按字符数强制截断。
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

        // 内容不足一段时直接作为单段返回
        if (chunks.isEmpty() && !text.isBlank()) {
            chunks.add(text.trim());
        }

        return chunks;
    }

    private static String cleanJson(String raw) {
        raw = raw.trim();
        if (raw.startsWith("```json")) raw = raw.substring(7);
        else if (raw.startsWith("```")) raw = raw.substring(3);
        if (raw.endsWith("```")) raw = raw.substring(0, raw.length() - 3);
        return raw.trim();
    }
}
