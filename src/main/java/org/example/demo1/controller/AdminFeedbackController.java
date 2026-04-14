package org.example.demo1.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.Result;
import org.example.demo1.common.result.ResultCode;
import org.example.demo1.dto.FeedbackProcessDTO;
import org.example.demo1.dto.FeedbackQueryDTO;
import org.example.demo1.service.FeedbackService;
import org.example.demo1.util.UserContext;
import org.example.demo1.vo.FeedbackAdminVO;
import org.example.demo1.vo.FeedbackDashboardVO;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员 - 系统反馈
 */
@RestController
@RequestMapping("/admin/feedback")
@RequiredArgsConstructor
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    /**
     * 仪表盘统计：未处理条数、总条数
     * GET /api/admin/feedback/stats
     */
    @GetMapping("/stats")
    public Result<FeedbackDashboardVO> stats() {
        requireAdmin();
        return Result.success(feedbackService.dashboardStats());
    }

    /**
     * 分页查询反馈列表
     * GET /api/admin/feedback?status=&keyword=&pageNum=&pageSize=
     */
    @GetMapping
    public Result<IPage<FeedbackAdminVO>> page(FeedbackQueryDTO query) {
        requireAdmin();
        return Result.success(feedbackService.pageForAdmin(query));
    }

    /**
     * 更新处理状态与说明
     * PUT /api/admin/feedback/{id}/process
     */
    @PutMapping("/{id}/process")
    public Result<Void> process(@PathVariable Long id, @Valid @RequestBody FeedbackProcessDTO dto) {
        Long adminId = requireAdmin();
        feedbackService.process(id, dto, adminId);
        return Result.success("已更新");
    }

    private Long requireAdmin() {
        if (UserContext.getUserId() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "需要管理员权限");
        }
        return UserContext.getUserId();
    }
}
