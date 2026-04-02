package org.example.demo1.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.ResultCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 法律智能问答 Agent
 * 使用 FastGptQaClient（不传 model / system 消息），通过问答应用 API Key 调用 FastGPT
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegalQaAgent {

    @Value("${fastgpt.qa-api-key}")
    private String apiKey;

    private final FastGptQaClient fastGptQaClient;

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
            String answer = fastGptQaClient.chat(apiKey, question, chatId);
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
