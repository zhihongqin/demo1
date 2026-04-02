package org.example.demo1.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.ResultCode;
import org.example.demo1.dto.ChatAskDTO;
import org.example.demo1.entity.ChatMessage;
import org.example.demo1.entity.ChatSession;
import org.example.demo1.entity.ChatTask;
import org.example.demo1.mapper.ChatMessageMapper;
import org.example.demo1.mapper.ChatSessionMapper;
import org.example.demo1.mapper.ChatTaskMapper;
import org.example.demo1.service.ChatAsyncProcessor;
import org.example.demo1.service.ChatService;
import org.example.demo1.vo.ChatSessionVO;
import org.example.demo1.vo.ChatTaskVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession>
        implements ChatService {

    private final ChatSessionMapper  chatSessionMapper;
    private final ChatMessageMapper  chatMessageMapper;
    private final ChatTaskMapper     chatTaskMapper;
    private final ChatAsyncProcessor chatAsyncProcessor;

    private static final int TITLE_MAX_LEN = 50;

    // ─── 异步问答 ──────────────────────────────────────────────────────────────

    /**
     * 不加 @Transactional，确保每步 insert 立即提交，
     * 避免异步线程读取时事务尚未提交导致找不到记录。
     */
    @Override
    public ChatTaskVO askAsync(Long userId, ChatAskDTO dto) {
        String question = dto.getQuestion().trim();
        String chatId   = (dto.getChatId() != null && !dto.getChatId().isBlank())
                          ? dto.getChatId().trim() : null;

        // 1. 查找或创建会话（立即提交）
        ChatSession session = findOrCreateSession(userId, chatId, question);

        // 2. 持久化用户消息，并将会话消息数 +1
        saveMessage(session.getId(), userId, "user", question, 0);
        session.setMessageCount(session.getMessageCount() + 1);
        session.setUpdatedAt(LocalDateTime.now());
        chatSessionMapper.updateById(session);

        // 3. 创建异步任务记录（status=0 PENDING）
        ChatTask task = new ChatTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setSessionId(session.getId());
        task.setUserId(userId);
        task.setQuestion(question);
        task.setStatus(0);
        chatTaskMapper.insert(task);

        log.info("创建问答任务: taskId={}, chatId={}, userId={}", task.getTaskId(), session.getChatId(), userId);

        // 4. 提交异步任务（主线程已提交所有 DB 变更，安全）
        chatAsyncProcessor.processTask(task.getTaskId(), session.getChatId());

        // 5. 立即返回 taskId
        ChatTaskVO vo = new ChatTaskVO();
        vo.setTaskId(task.getTaskId());
        vo.setChatId(session.getChatId());
        vo.setStatus("PENDING");
        return vo;
    }

    @Override
    public ChatTaskVO pollTask(Long userId, String taskId) {
        ChatTask task = chatTaskMapper.selectOne(
                new LambdaQueryWrapper<ChatTask>()
                        .eq(ChatTask::getTaskId, taskId)
                        .eq(ChatTask::getUserId, userId));
        if (task == null) {
            throw new BusinessException(ResultCode.CHAT_TASK_NOT_EXIST);
        }

        ChatTaskVO vo = new ChatTaskVO();
        vo.setTaskId(task.getTaskId());
        vo.setStatus(statusLabel(task.getStatus()));
        vo.setAnswer(task.getAnswer());
        vo.setErrorMsg(task.getErrorMsg());

        // 附上 chatId（通过 sessionId 查询）
        ChatSession session = chatSessionMapper.selectById(task.getSessionId());
        if (session != null) {
            vo.setChatId(session.getChatId());
        }
        return vo;
    }

    // ─── 历史会话 ──────────────────────────────────────────────────────────────

    @Override
    public IPage<ChatSessionVO> listSessions(Long userId, int page, int pageSize) {
        Page<ChatSession> pageParam = new Page<>(page, pageSize);
        IPage<ChatSession> sessionPage = chatSessionMapper.selectPage(pageParam,
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .orderByDesc(ChatSession::getUpdatedAt));

        return sessionPage.convert(s -> {
            ChatSessionVO vo = new ChatSessionVO();
            vo.setId(s.getId());
            vo.setChatId(s.getChatId());
            vo.setTitle(s.getTitle());
            vo.setMessageCount(s.getMessageCount());
            vo.setLastQuestion(s.getLastQuestion());
            vo.setUpdatedAt(s.getUpdatedAt());
            return vo;
        });
    }

    @Override
    public ChatSessionVO getSessionDetail(Long userId, String chatId) {
        ChatSession session = getSessionByUserAndChatId(userId, chatId);

        List<ChatMessage> messages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, session.getId())
                        .orderByAsc(ChatMessage::getCreatedAt));

        ChatSessionVO vo = new ChatSessionVO();
        vo.setId(session.getId());
        vo.setChatId(session.getChatId());
        vo.setTitle(session.getTitle());
        vo.setMessageCount(session.getMessageCount());
        vo.setLastQuestion(session.getLastQuestion());
        vo.setUpdatedAt(session.getUpdatedAt());
        vo.setMessages(messages.stream().map(m -> {
            ChatSessionVO.ChatMessageVO msgVO = new ChatSessionVO.ChatMessageVO();
            msgVO.setId(m.getId());
            msgVO.setRole(m.getRole());
            msgVO.setContent(m.getContent());
            msgVO.setCreatedAt(m.getCreatedAt());
            return msgVO;
        }).collect(Collectors.toList()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long userId, String chatId) {
        ChatSession session = getSessionByUserAndChatId(userId, chatId);
        chatSessionMapper.deleteById(session.getId());
        chatMessageMapper.delete(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, session.getId()));
        // 顺便清理该会话下的任务记录
        chatTaskMapper.delete(
                new LambdaQueryWrapper<ChatTask>()
                        .eq(ChatTask::getSessionId, session.getId()));
        log.info("删除会话: userId={}, chatId={}", userId, chatId);
    }

    // ─── 私有工具方法 ──────────────────────────────────────────────────────────

    private ChatSession findOrCreateSession(Long userId, String chatId, String question) {
        if (chatId != null) {
            ChatSession existing = chatSessionMapper.selectOne(
                    new LambdaQueryWrapper<ChatSession>()
                            .eq(ChatSession::getChatId, chatId)
                            .eq(ChatSession::getUserId, userId));
            if (existing != null) return existing;
        }
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setChatId(chatId != null ? chatId : "chat_" + System.currentTimeMillis() + "_" + userId);
        session.setTitle(question.length() > TITLE_MAX_LEN
                ? question.substring(0, TITLE_MAX_LEN) + "…" : question);
        session.setMessageCount(0);
        session.setLastQuestion(question.length() > 200 ? question.substring(0, 200) : question);
        chatSessionMapper.insert(session);
        log.info("创建新会话: userId={}, chatId={}", userId, session.getChatId());
        return session;
    }

    private void saveMessage(Long sessionId, Long userId, String role, String content, int tokensUsed) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setUserId(userId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setTokensUsed(tokensUsed);
        chatMessageMapper.insert(msg);
    }

    private ChatSession getSessionByUserAndChatId(Long userId, String chatId) {
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getChatId, chatId)
                        .eq(ChatSession::getUserId, userId));
        if (session == null) throw new BusinessException(ResultCode.CHAT_SESSION_NOT_EXIST);
        return session;
    }

    private static String statusLabel(Integer status) {
        if (status == null) return "PENDING";
        return switch (status) {
            case 1  -> "DONE";
            case 2  -> "ERROR";
            default -> "PENDING";
        };
    }
}
