package org.example.demo1.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.Result;
import org.example.demo1.common.result.ResultCode;
import org.example.demo1.dto.CrawlJobQueryDTO;
import org.example.demo1.service.CrawlJobRecordService;
import org.example.demo1.util.UserContext;
import org.example.demo1.vo.CrawlJobRecordVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员 - 爬取任务历史
 */
@RestController
@RequestMapping("/admin/crawl-jobs")
@RequiredArgsConstructor
public class AdminCrawlJobController {

    private final CrawlJobRecordService crawlJobRecordService;

    /**
     * 分页查询爬取记录
     * GET /api/admin/crawl-jobs?crawlType=&status=&pageNum=&pageSize=
     */
    @GetMapping
    public Result<IPage<CrawlJobRecordVO>> page(CrawlJobQueryDTO query) {
        requireAdmin();
        return Result.success(crawlJobRecordService.pageForAdmin(query));
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
