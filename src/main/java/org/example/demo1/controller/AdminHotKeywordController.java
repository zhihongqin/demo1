package org.example.demo1.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.Result;
import org.example.demo1.common.result.ResultCode;
import org.example.demo1.dto.HotKeywordCreateDTO;
import org.example.demo1.dto.HotKeywordQueryDTO;
import org.example.demo1.dto.HotKeywordUpdateDTO;
import org.example.demo1.service.HotKeywordService;
import org.example.demo1.util.UserContext;
import org.example.demo1.vo.HotKeywordVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员 - 热门搜索词维护
 */
@RestController
@RequestMapping("/admin/hot-keywords")
@RequiredArgsConstructor
public class AdminHotKeywordController {

    private final HotKeywordService hotKeywordService;

    /**
     * 分页条件查询
     * GET /admin/hot-keywords?keyword=&isEnabled=&isPinned=&origin=&pageNum=&pageSize=
     */
    @GetMapping
    public Result<IPage<HotKeywordVO>> page(HotKeywordQueryDTO query) {
        requireAdmin();
        return Result.success(hotKeywordService.pageForAdmin(query));
    }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody HotKeywordCreateDTO dto) {
        requireAdmin();
        return Result.success("添加成功", hotKeywordService.createByAdmin(dto));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody HotKeywordUpdateDTO dto) {
        requireAdmin();
        hotKeywordService.updateByAdmin(id, dto);
        return Result.success("更新成功");
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        requireAdmin();
        hotKeywordService.deleteByAdmin(id);
        return Result.success("已删除");
    }

    /** 批量删除（使用 POST，避免与 DELETE /{id} 路径冲突） */
    @PostMapping("/batch-delete")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        requireAdmin();
        hotKeywordService.deleteBatchByAdmin(ids);
        return Result.success("批量删除成功");
    }

    @PostMapping("/refresh")
    public Result<Void> refresh() {
        requireAdmin();
        hotKeywordService.refreshFromSearchHistory();
        return Result.success("已从搜索历史同步");
    }

    private void requireAdmin() {
        if (UserContext.getUserId() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "需要管理员权限");
        }
    }
}
