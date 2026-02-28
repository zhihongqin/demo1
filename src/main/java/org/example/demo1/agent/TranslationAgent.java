package org.example.demo1.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 翻译 Agent：英文法律案例 → 中文
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TranslationAgent {

    @Value("${fastgpt.translation-api-key}")
    private String apiKey;

    private final FastGptClient fastGptClient;

    private static final String SYSTEM_PROMPT = """
            你是一名专业的法律翻译专家，擅长涉外法律文书的翻译工作。
            请将用户提供的英文法律案例内容准确翻译为中文，要求：
            1. 保持法律术语的准确性和专业性
            2. 保留原文的段落结构
            3. 对专有名词（人名、机构名）保留英文并在括号内注明中文含义
            4. 使用中国法律语境下的规范表达
            5. 只输出翻译结果，不添加任何额外说明
            """;

    /**
     * 将英文法律案例内容翻译为中文
     *
     * @param englishContent 英文原文
     * @return 中文翻译
     */
    public String translateToZh(String englishContent) {
        log.info("开始翻译案例，内容长度: {}", englishContent.length());
        try {
            String result = fastGptClient.chat(apiKey, SYSTEM_PROMPT, englishContent);
            log.info("翻译完成，结果长度: {}", result.length());
            return result;
        } catch (Exception e) {
            log.error("翻译失败", e);
            throw e;
        }
    }
}
