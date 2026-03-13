package org.example.demo1.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.Result;
import org.example.demo1.common.result.ResultCode;
import org.example.demo1.dto.CaseQueryDTO;
import org.example.demo1.dto.CaseSaveDTO;
import org.example.demo1.service.LegalCaseService;
import org.example.demo1.util.UserContext;
import org.example.demo1.vo.CaseDetailVO;
import org.example.demo1.vo.CaseListVO;
import org.example.demo1.vo.CaseScoreVO;
import org.example.demo1.vo.CaseSummaryVO;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cases")
@RequiredArgsConstructor
public class LegalCaseController {

    private final LegalCaseService legalCaseService;

    /**
     * 分页查询案例列表
     * GET /api/cases
     */
    @GetMapping
    public Result<IPage<CaseListVO>> queryCases(CaseQueryDTO dto) {
        Long userId = UserContext.getUserId();
        return Result.success(legalCaseService.queryCases(dto, userId));
    }

    /**
     * 获取案例详情
     * GET /api/cases/{id}
     */
    @GetMapping("/{id}")
    public Result<CaseDetailVO> getCaseDetail(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        userId = 1L;
        return Result.success(legalCaseService.getCaseDetail(id, userId));
    }

    /**
     * 新增/更新案例（管理员）
     * POST /api/cases
     */
    @PostMapping
    public Result<Long> saveCase(@Valid @RequestBody CaseSaveDTO dto) {
        requireAdmin();
        Long caseId = legalCaseService.saveCase(dto);
        return Result.success("案例保存成功，AI处理已在后台启动", caseId);
    }

    /**
     * 触发翻译（英→中）
     * POST /api/cases/{id}/translate
     */
    @PostMapping("/{id}/translate")
    public Result<String> triggerTranslation(@PathVariable Long id) {
//        requireLogin();
        String result = legalCaseService.triggerTranslation(id);
        return Result.success("翻译完成", result);
    }

    /**
     * 触发摘要提取
     * POST /api/cases/{id}/summary
     */
    @PostMapping("/{id}/summary")
    public Result<CaseSummaryVO> triggerSummary(@PathVariable Long id) {
//        requireLogin();
        return Result.success(legalCaseService.triggerSummary(id));
    }

    /**
     * 触发重要性评分
     * POST /api/cases/{id}/score
     */
    @PostMapping("/{id}/score")
    public Result<CaseScoreVO> triggerScore(@PathVariable Long id) {
//        requireLogin();
        return Result.success(legalCaseService.triggerScore(id));
    }

    /**
     * 收藏/取消收藏案例
     * POST /api/cases/{id}/favorite
     */
    @PostMapping("/{id}/favorite")
    public Result<Boolean> toggleFavorite(@PathVariable Long id) {
        Long userId = requireLogin();
        boolean isFavorited = legalCaseService.toggleFavorite(id, userId);
        return Result.success(isFavorited ? "收藏成功" : "已取消收藏", isFavorited);
    }

    /**
     * 获取我的收藏列表
     * GET /api/cases/favorites
     */
    @GetMapping("/favorites")
    public Result<IPage<CaseListVO>> getFavorites(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = requireLogin();
        return Result.success(legalCaseService.getFavorites(userId, pageNum, pageSize));
    }

    private Long requireLogin() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }

    private void requireAdmin() {
        Long userId = requireLogin();
        // 实际项目可从数据库查角色，此处简化
        if (userId == null) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }
}
