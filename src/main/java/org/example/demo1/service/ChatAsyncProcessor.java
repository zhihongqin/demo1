package org.example.demo1.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo1.agent.LegalQaAgent;
import org.example.demo1.entity.ChatMessage;
import org.example.demo1.entity.ChatSession;
import org.example.demo1.entity.ChatTask;
import org.example.demo1.mapper.ChatMessageMapper;
import org.example.demo1.mapper.ChatSessionMapper;
import org.example.demo1.mapper.ChatTaskMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 独立组件，承载 @Async 方法。
 * 必须与调用方（ChatServiceImpl）分离，确保 Spring 代理生效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatAsyncProcessor {

    private final ChatTaskMapper    chatTaskMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final LegalQaAgent      legalQaAgent;

    /**
     * 在 aiTaskExecutor 线程池中执行：调用 FastGPT → 保存消息 → 更新任务状态。
     *
     * @param taskId       任务 UUID
     * @param fastgptChatId FastGPT 上下文 chatId
     */
    @Async("aiTaskExecutor")
    public void processTask(String taskId, String fastgptChatId) {
        log.info("开始处理问答任务: taskId={}, fastgptChatId={}", taskId, fastgptChatId);

        ChatTask task = chatTaskMapper.selectOne(
                new LambdaQueryWrapper<ChatTask>().eq(ChatTask::getTaskId, taskId));
        if (task == null) {
            log.error("任务记录不存在，已忽略: taskId={}", taskId);
            return;
        }

        try {
            // 1. 调用 FastGPT
            String answer = legalQaAgent.ask(task.getQuestion(), fastgptChatId);

            // 2. 持久化 AI 消息
            ChatMessage aiMsg = new ChatMessage();
            aiMsg.setSessionId(task.getSessionId());
            aiMsg.setUserId(task.getUserId());
            aiMsg.setRole("assistant");
            aiMsg.setContent(answer);
            aiMsg.setTokensUsed(0);
            chatMessageMapper.insert(aiMsg);

            // 3. 更新会话统计（+2：用户消息在主线程已存，此处加 AI 那条；
            //    实际用户消息 +1 在 askAsync 里，AI 消息 +1 在此处，共 +2）
            ChatSession session = chatSessionMapper.selectById(task.getSessionId());
            if (session != null) {
                String q = task.getQuestion();
                session.setMessageCount(session.getMessageCount() + 1);
                session.setLastQuestion(q.length() > 200 ? q.substring(0, 200) : q);
                session.setUpdatedAt(LocalDateTime.now());
                chatSessionMapper.updateById(session);
            }

            // 4. 标记任务完成
            task.setAnswer(answer);
            task.setStatus(1);
            task.setUpdatedAt(LocalDateTime.now());
            chatTaskMapper.updateById(task);

            log.info("问答任务完成: taskId={}", taskId);

        } catch (Exception e) {
            log.error("问答任务失败: taskId={}, error={}", taskId, e.getMessage(), e);
            String errMsg = e.getMessage();
            task.setStatus(2);
            task.setErrorMsg(errMsg != null && errMsg.length() > 500
                    ? errMsg.substring(0, 500) : errMsg);
            task.setUpdatedAt(LocalDateTime.now());
            chatTaskMapper.updateById(task);
        }
    }
}
