package org.example.demo1.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.ResultCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 法律智能问答 Agent
 * 使用 FastGptClient（传入系统提示词），通过问答应用 API Key 调用 FastGPT
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegalQaAgent {

    @Value("${fastgpt.qa-api-key}")
    private String apiKey;

    private final FastGptClient fastGptClient;

    private static final String SYSTEM_PROMPT = """
            你是一名专业的涉外法律案例研究助手，服务于"涉外法律案例查询"微信小程序。

            【角色与能力】
            你拥有丰富的国际法律知识，熟悉中国、日本、美国等主要司法体系的裁判逻辑。
            你可以综合利用以下两类工具充分获取信息：
            - 知识库检索：收录了真实法律案例（含案号、法院、判决结论）以及中华人民共和国\
            民法典、刑法、劳动合同法、民事诉讼法、宪法等法律法规原文
            - 网络搜索：获取最新法律动态、司法解释、权威判例、学术观点等公开信息

            【工具使用策略】
            两类工具地位平等，应根据问题性质灵活、充分地使用：
            - 对于案例查询类问题，同时检索知识库和网络，交叉验证后综合呈现
            - 对于法条解读类问题，检索知识库原文，再通过网络补充最新司法解释或修订信息
            - 对于法律动态类问题，以网络搜索为主，知识库为辅
            - 若单次检索结果不充分，应主动调整关键词进行多轮检索，直到获取到足够的信息再作答
            - 两类工具均无结果时，如实告知，绝对不得编造案例或法律条文

            【回答规范】
            1. 明确说明每条信息的来源（知识库或网络），让用户清楚信息依据
            2. 使用规范的法律语言，表达专业、严谨、客观
            3. 所有回答使用中文；日文、英文案例的分析也使用中文阐述
            4. 禁止使用 Markdown 格式（#、**、- 等符号），用自然段落组织内容
            5. 先给出核心结论，再展开分析，避免冗余铺垫

            【边界说明】
            本系统仅用于学术研究和案例参考，不构成正式的法律意见或诉讼建议。
            """;

    /**
     * 回答用户的法律问题（支持多轮对话）
     *
     * @param question 用户提问
     * @param chatId   会话ID，传入相同值可保持上下文；为 null 时为单轮问答
     * @return AI 回答文本
     */
    public String ask(String question, String chatId) {
        log.info("法律问答请求: chatId={}, question={}", chatId,
                question.length() > 50 ? question.substring(0, 50) + "..." : question);
        try {
            String answer = fastGptClient.chat(apiKey, SYSTEM_PROMPT, question, chatId);
            log.info("法律问答完成: chatId={}, answerLength={}", chatId, answer.length());
            return answer;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("法律问答调用失败: chatId={}", chatId, e);
            throw new BusinessException(ResultCode.CHAT_FAIL, "智能问答服务暂时不可用，请稍后重试");
        }
    }
}
