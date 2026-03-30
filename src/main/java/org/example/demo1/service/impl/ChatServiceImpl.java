package org.example.demo1.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo1.agent.LegalQaAgent;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.ResultCode;
import org.example.demo1.dto.ChatAskDTO;
import org.example.demo1.entity.ChatMessage;
import org.example.demo1.entity.ChatSession;
import org.example.demo1.mapper.ChatMessageMapper;
import org.example.demo1.mapper.ChatSessionMapper;
import org.example.demo1.service.ChatService;
import org.example.demo1.vo.ChatAnswerVO;
import org.example.demo1.vo.ChatSessionVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession>
        implements ChatService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final LegalQaAgent legalQaAgent;

    /** 会话标题最大长度（截取首条提问） */
    private static final int TITLE_MAX_LEN = 50;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatAnswerVO ask(Long userId, ChatAskDTO dto) {
        String question = dto.getQuestion().trim();
        String chatId = (dto.getChatId() != null && !dto.getChatId().isBlank())
                ? dto.getChatId().trim() : null;

        // 1. 查找或创建会话记录
        ChatSession session = findOrCreateSession(userId, chatId, question);

        // 2. 持久化用户消息
        saveMessage(session.getId(), userId, "user", question, 0);

        // 3. 调用 FastGPT 获取回答
        String answer = legalQaAgent.ask(question, session.getChatId());

        // 4. 持久化 AI 消息
        saveMessage(session.getId(), userId, "assistant", answer, 0);

        // 5. 更新会话统计
        updateSessionStats(session, question);

        // 6. 组装返回结果
        ChatAnswerVO vo = new ChatAnswerVO();
        vo.setAnswer(answer);
        vo.setChatId(session.getChatId());
        vo.setMessageCount(session.getMessageCount());
        return vo;
    }

    @Override
    public IPage<ChatSessionVO> listSessions(Long userId, int page, int pageSize) {
        Page<ChatSession> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .orderByDesc(ChatSession::getUpdatedAt);

        IPage<ChatSession> sessionPage = chatSessionMapper.selectPage(pageParam, wrapper);

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
                        .orderByAsc(ChatMessage::getCreatedAt)
        );

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
        // 逻辑删除会话及其消息
        chatSessionMapper.deleteById(session.getId());
        chatMessageMapper.delete(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, session.getId())
        );
        log.info("删除会话: userId={}, chatId={}", userId, chatId);
    }

    // ─── 私有工具方法 ──────────────────────────────────────────────────────────

    private ChatSession findOrCreateSession(Long userId, String chatId, String question) {
        if (chatId != null) {
            ChatSession existing = chatSessionMapper.selectOne(
                    new LambdaQueryWrapper<ChatSession>()
                            .eq(ChatSession::getChatId, chatId)
                            .eq(ChatSession::getUserId, userId)
            );
            if (existing != null) {
                return existing;
            }
        }
        // 新建会话
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

    private void updateSessionStats(ChatSession session, String question) {
        session.setMessageCount(session.getMessageCount() + 2); // 用户1条 + AI1条
        session.setLastQuestion(question.length() > 200 ? question.substring(0, 200) : question);
        session.setUpdatedAt(LocalDateTime.now());
        chatSessionMapper.updateById(session);
    }

    private ChatSession getSessionByUserAndChatId(Long userId, String chatId) {
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getChatId, chatId)
                        .eq(ChatSession::getUserId, userId)
        );
        if (session == null) {
            throw new BusinessException(ResultCode.CHAT_SESSION_NOT_EXIST);
        }
        return session;
    }
}
