package org.example.demo1.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo1.common.result.Result;
import org.example.demo1.crawler.CaseCrawlerService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 案例采集管理接口
 * 提供手动触发、状态查询等运维能力
 */
@Slf4j
@RestController
@RequestMapping("/admin/crawler")
@RequiredArgsConstructor
public class CrawlerController {

    private final CaseCrawlerService crawlerService;

    /**
     * 启动全量采集（遍历所有关键词）
     * POST /api/admin/crawler/start
     */
    @PostMapping("/start")
    public Result<String> startCrawl() {
        if (crawlerService.isRunning()) {
            return Result.fail(400, "采集任务正在运行中，请勿重复触发");
        }
        crawlerService.crawlAll();
        return Result.success("全量采集任务已启动（异步执行中）");
    }

    /**
     * 针对单个关键词采集（用于测试或补采）
     * POST /api/admin/crawler/query?keyword=Huawei
     */
    @PostMapping("/query")
    public Result<Map<String, Object>> crawlByKeyword(@RequestParam String keyword) {
        if (crawlerService.isRunning()) {
            return Result.fail(400, "采集任务正在运行中，请稍后再试");
        }
        log.info("[手动采集] 关键词: {}", keyword);
        int saved = crawlerService.crawlByQuery(keyword);
        return Result.success(Map.of(
                "keyword", keyword,
                "savedCount", saved
        ));
    }

    /**
     * 查询采集任务运行状态
     * GET /api/admin/crawler/status
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus() {
        return Result.success(Map.of(
                "running", crawlerService.isRunning(),
                "message", crawlerService.isRunning() ? "采集任务运行中" : "采集任务空闲"
        ));
    }
}
