package org.example.demo1.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.ResultCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 法律智能问答 Agent
 * 使用专属问答应用的 API Key，调用 FastGPT 为用户解答涉外法律问题
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegalQaAgent {

    @Value("${fastgpt.qa-api-key}")
    private String apiKey;

    private final FastGptClient fastGptClient;

    private static final String SYSTEM_PROMPT = """
            你是一位专业的涉外法律顾问，专注于国际贸易法、国际投资法、跨国合同纠纷、知识产权保护、
            海事法、国际仲裁及各国涉外法律实务等领域。
            
            回答要求：
            1. 以中文回答，语言简洁、专业、易懂
            2. 涉及具体法律条文时，请引用相关法规名称
            3. 如问题超出涉外法律范围，礼貌说明并建议咨询方向
            4. 不提供具体案件的代理意见，仅提供法律知识咨询
            5. 回答长度适中，重点突出，避免冗余
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
