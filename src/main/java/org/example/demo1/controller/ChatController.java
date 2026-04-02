package org.example.demo1.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.Result;
import org.example.demo1.common.result.ResultCode;
import org.example.demo1.dto.ChatAskDTO;
import org.example.demo1.service.ChatService;
import org.example.demo1.util.UserContext;
import org.example.demo1.vo.ChatSessionVO;
import org.example.demo1.vo.ChatTaskVO;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 异步提问 —— 立即返回 taskId，不阻塞等待 AI 回答。
     * POST /api/chat/ask
     */
    @PostMapping("/ask")
    public Result<ChatTaskVO> ask(@Valid @RequestBody ChatAskDTO dto) {
        Long userId = requireLogin();
        return Result.success(chatService.askAsync(userId, dto));
    }

    /**
     * 轮询任务结果。
     * GET /api/chat/result/{taskId}
     * 返回 status: PENDING / DONE / ERROR
     */
    @GetMapping("/result/{taskId}")
    public Result<ChatTaskVO> pollResult(@PathVariable String taskId) {
        Long userId = requireLogin();
        return Result.success(chatService.pollTask(userId, taskId));
    }

    /**
     * 查询历史会话列表（分页）。
     * GET /api/chat/sessions?page=1&pageSize=10
     */
    @GetMapping("/sessions")
    public Result<IPage<ChatSessionVO>> listSessions(
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        Long userId = requireLogin();
        return Result.success(chatService.listSessions(userId, page, pageSize));
    }

    /**
     * 查询会话消息详情。
     * GET /api/chat/sessions/{chatId}
     */
    @GetMapping("/sessions/{chatId}")
    public Result<ChatSessionVO> getSessionDetail(@PathVariable String chatId) {
        Long userId = requireLogin();
        return Result.success(chatService.getSessionDetail(userId, chatId));
    }

    /**
     * 删除会话（逻辑删除）。
     * DELETE /api/chat/sessions/{chatId}
     */
    @DeleteMapping("/sessions/{chatId}")
    public Result<Void> deleteSession(@PathVariable String chatId) {
        Long userId = requireLogin();
        chatService.deleteSession(userId, chatId);
        return Result.success();
    }

    private Long requireLogin() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }
}
