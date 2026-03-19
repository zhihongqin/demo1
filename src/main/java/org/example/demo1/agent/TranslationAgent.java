package org.example.demo1.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 翻译 Agent：英文法律案例 → 中文
 * 超长内容自动分段翻译后拼接，避免超出 FastGPT 输入长度限制（8192 tokens）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TranslationAgent {

    @Value("${fastgpt.translation-api-key}")
    private String apiKey;

    private final FastGptClient fastGptClient;

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

    /**
     * 翻译案例标题（英文→中文）
     *
     * @param englishTitle 英文标题
     * @return 中文标题
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
     * 将英文法律案例内容翻译为中文
     * 内容超过 MAX_CHUNK_SIZE 时自动分段翻译后拼接
     *
     * @param englishContent 英文原文
     * @return 中文翻译
     */
    public String translateToZh(String englishContent) {
        if (englishContent == null || englishContent.isBlank()) {
            return "";
        }

        log.info("开始翻译案例，内容长度: {}", englishContent.length());

        if (englishContent.length() <= MAX_CHUNK_SIZE) {
            return translateChunk(englishContent);
        }

        // 超长内容分段翻译，使用同一 chatId 关联会话，让 AI 记住历史上下文
        List<String> chunks = splitIntoChunks(englishContent);
        log.info("内容过长，分为 {} 段翻译", chunks.size());

        String chatId = UUID.randomUUID().toString();
        log.info("分段翻译会话 chatId: {}", chatId);

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            log.info("翻译第 {}/{} 段，长度: {}", i + 1, chunks.size(), chunks.get(i).length());
            String translated = translateChunk(chunks.get(i), chatId);
            result.append(translated);
            if (i < chunks.size() - 1) {
                result.append("\n\n");
            }
        }

        String finalResult = result.toString();
        log.info("翻译完成，共 {} 段，结果长度: {}", chunks.size(), finalResult.length());
        return finalResult;
    }

    /**
     * 翻译单个文本片段（单次对话，不携带会话记忆）
     */
    private String translateChunk(String chunk) {
        return translateChunk(chunk, null);
    }

    /**
     * 翻译单个文本片段
     *
     * @param chunk  待翻译的文本片段
     * @param chatId 会话 ID，不为 null 时 FastGPT 将关联历史对话以保持上下文一致性
     */
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
     * 将长文本按段落切分为不超过 MAX_CHUNK_SIZE 的片段
     * 优先按双换行（段落）切分，避免在句子中间截断；
     * 单个段落超长时按 MAX_CHUNK_SIZE 强制截断
     */
    private List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\n\n");
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            if (para.isBlank()) continue;

            if (para.length() > MAX_CHUNK_SIZE) {
                // 当前缓冲区先入队
                if (!current.isEmpty()) {
                    chunks.add(current.toString().trim());
                    current = new StringBuilder();
                }
                // 超长段落按字符数强制截断
                for (int i = 0; i < para.length(); i += MAX_CHUNK_SIZE) {
                    chunks.add(para.substring(i, Math.min(i + MAX_CHUNK_SIZE, para.length())));
                }
            } else if (current.length() + para.length() + 2 > MAX_CHUNK_SIZE) {
                // 加入当前段落会超限，先提交缓冲区
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
