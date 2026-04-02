package org.example.demo1.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.example.demo1.dto.ChatAskDTO;
import org.example.demo1.vo.ChatSessionVO;
import org.example.demo1.vo.ChatTaskVO;

public interface ChatService {

    /**
     * 异步提问：立即返回 taskId，后台线程调用 FastGPT。
     *
     * @param userId 当前登录用户ID
     * @param dto    提问内容 + 会话ID
     * @return 任务 VO（status=PENDING）
     */
    ChatTaskVO askAsync(Long userId, ChatAskDTO dto);

    /**
     * 轮询任务结果。
     *
     * @param userId 当前用户ID（校验归属权）
     * @param taskId 任务 UUID
     * @return 任务 VO（PENDING / DONE / ERROR）
     */
    ChatTaskVO pollTask(Long userId, String taskId);

    /**
     * 分页查询当前用户的历史会话列表。
     */
    IPage<ChatSessionVO> listSessions(Long userId, int page, int pageSize);

    /**
     * 查询指定会话的消息详情。
     */
    ChatSessionVO getSessionDetail(Long userId, String chatId);

    /**
     * 逻辑删除指定会话。
     */
    void deleteSession(Long userId, String chatId);
}
