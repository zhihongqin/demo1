package org.example.demo1.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.ResultCode;
import org.example.demo1.vo.CaseEnrichVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 案例字段补全 Agent
 * 从案例内容中提取：案件类型、关键词、涉及法律条文、国家/地区、审理法院
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaseEnrichAgent {

    @Value("${fastgpt.summary-api-key}")
    private String apiKey;

    private final FastGptClient fastGptClient;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            你是一名资深的涉外法律案例分析专家。请从用户提供的法律案例内容中提取关键结构化信息，并以严格的JSON格式输出，不要包含任何其他文字。
            
            输出格式：
            {
              "caseType": 1,
              "keywords": "关键词1,关键词2,关键词3",
              "legalProvisions": "法律条文1,法律条文2",
              "country": "国家/地区名称",
              "court": "法院名称"
            }
            
            字段说明：
            - caseType：案件类型，只能填写以下数字之一：1（民事）、2（刑事）、3（行政）、4（商事）。根据案件性质判断，无法判断时填1。
            - keywords：案例核心关键词，3-8个，用英文逗号分隔，使用中文。
            - legalProvisions：涉及的主要法律条文或法规名称（简要），多个用英文逗号分隔，不超过5条，使用中文表述（如：《美国统一商法典》第2-207条）。若无明确条文则填"无"。
            - country：案件所属国家或地区（中文名称，如：美国、英国、中国香港）。
            - court：审理该案的法院名称（中文译名，保留英文原名在括号内，如：美国联邦第九巡回上诉法院(9th Cir.)）。
            
            要求：
            1. 必须是合法的JSON格式
            2. 所有字段均不能为null，无法确定时填写合理的默认值
            3. caseType必须是整数1、2、3或4
            """;

    /**
     * 从案例内容中提取补全字段
     *
     * @param caseContent 案例内容（建议使用中文翻译内容，也可使用英文原文）
     * @param titleEn     英文标题（辅助判断国家/法院信息）
     * @return 字段补全VO
     */
    public CaseEnrichVO enrichCase(String caseContent, String titleEn) {
        log.info("开始提取案例补全字段，内容长度: {}", caseContent != null ? caseContent.length() : 0);

        // 构建输入：优先使用前 4000 字符，避免超出 token 限制
        String input = buildInput(caseContent, titleEn);

        try {
            String result = fastGptClient.chat(apiKey, SYSTEM_PROMPT, input);
            log.debug("字段提取原始结果: {}", result);

            result = cleanJson(result);

            JsonNode jsonNode = objectMapper.readTree(result);
            CaseEnrichVO vo = new CaseEnrichVO();

            // caseType：确保是 1-4 的整数
            int caseType = jsonNode.path("caseType").asInt(1);
            if (caseType < 1 || caseType > 4) {
                caseType = 1;
            }
            vo.setCaseType(caseType);
            vo.setKeywords(jsonNode.path("keywords").asText(""));
            vo.setLegalProvisions(jsonNode.path("legalProvisions").asText(""));
            vo.setCountry(jsonNode.path("country").asText(""));
            vo.setCourt(jsonNode.path("court").asText(""));

            log.info("字段提取完成: caseType={}, country={}, court={}", vo.getCaseType(), vo.getCountry(), vo.getCourt());
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("案例字段提取失败", e);
            throw new BusinessException(ResultCode.ENRICH_FAIL, "案例字段提取失败: " + e.getMessage());
        }
    }

    private String buildInput(String caseContent, String titleEn) {
        StringBuilder sb = new StringBuilder();
        if (titleEn != null && !titleEn.isBlank()) {
            sb.append("案例标题（英文）：").append(titleEn).append("\n\n");
        }
        if (caseContent != null && !caseContent.isBlank()) {
            // 截取前 4000 字符，足够提取结构化字段
            String content = caseContent.length() > 4000 ? caseContent.substring(0, 4000) : caseContent;
            sb.append("案例内容：\n").append(content);
        }
        return sb.toString();
    }

    private String cleanJson(String result) {
        result = result.trim();
        if (result.startsWith("```json")) {
            result = result.substring(7);
        } else if (result.startsWith("```")) {
            result = result.substring(3);
        }
        if (result.endsWith("```")) {
            result = result.substring(0, result.length() - 3);
        }
        return result.trim();
    }
}
