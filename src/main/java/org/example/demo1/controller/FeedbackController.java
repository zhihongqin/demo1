package org.example.demo1.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.Result;
import org.example.demo1.common.result.ResultCode;
import org.example.demo1.dto.FeedbackSubmitDTO;
import org.example.demo1.service.FeedbackService;
import org.example.demo1.util.UserContext;
import org.springframework.web.bind.annotation.*;

/**
 * 小程序用户 - 系统反馈
 */
@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    /**
     * 提交反馈（需登录）
     * POST /api/feedback
     */
    @PostMapping
    public Result<Long> submit(@Valid @RequestBody FeedbackSubmitDTO dto) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long id = feedbackService.submit(userId, dto);
        return Result.success("提交成功", id);
    }
}
