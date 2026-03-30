package org.example.demo1.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.example.demo1.dto.ChatAskDTO;
import org.example.demo1.vo.ChatAnswerVO;
import org.example.demo1.vo.ChatSessionVO;

public interface ChatService {

    /**
     * 提问并获取 AI 回答，同时持久化会话与消息记录
     *
     * @param userId 当前登录用户ID
     * @param dto    提问内容 + 会话ID
     * @return AI 回答及会话信息
     */
    ChatAnswerVO ask(Long userId, ChatAskDTO dto);

    /**
     * 分页查询当前用户的历史会话列表
     *
     * @param userId   用户ID
     * @param page     页码（从1开始）
     * @param pageSize 每页条数
     */
    IPage<ChatSessionVO> listSessions(Long userId, int page, int pageSize);

    /**
     * 查询指定会话的消息详情
     *
     * @param userId  当前用户ID（校验归属权）
     * @param chatId  会话标识
     */
    ChatSessionVO getSessionDetail(Long userId, String chatId);

    /**
     * 删除指定会话（逻辑删除）
     *
     * @param userId 当前用户ID（校验归属权）
     * @param chatId 会话标识
     */
    void deleteSession(Long userId, String chatId);
}
