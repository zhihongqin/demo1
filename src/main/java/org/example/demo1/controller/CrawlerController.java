package org.example.demo1.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo1.common.result.Result;
import org.example.demo1.crawler.CaseCrawlerService;
import org.example.demo1.crawler.PythonCrawlerService;
import org.example.demo1.dto.JapanCrawlerParamDTO;
import org.example.demo1.entity.CrawlJobRecord;
import org.example.demo1.service.CrawlJobRecordService;
import org.example.demo1.util.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 案例采集管理接口
 * 提供手动触发、状态查询等运维能力
 * - /admin/crawler/*        → CourtListener Java 采集（调用官方 API）
 * - /admin/crawler/python/* → Python 爬虫进程管理（针对其他无 API 的网站）
 */
@Slf4j
@RestController
@RequestMapping("/admin/crawler")
@RequiredArgsConstructor
public class CrawlerController {

    private final CaseCrawlerService crawlerService;
    private final PythonCrawlerService pythonCrawlerService;
    private final CrawlJobRecordService crawlJobRecordService;
    private final ObjectMapper objectMapper;

    // ─── CourtListener 采集（Java 实现）───────────────────────────────────

    /**
     * 启动 CourtListener 全量采集（遍历所有关键词，异步执行）
     * POST /api/admin/crawler/start?maxPages=10
     *
     * @param maxPages 每个关键词最多爬取的页数；不传则使用配置 courtlistener.max-pages
     */
    @PostMapping("/start")
    public Result<String> startCrawl(@RequestParam(required = false) Integer maxPages) {
        if (crawlerService.isRunning()) {
            return Result.fail(400, "采集任务正在运行中，请勿重复触发");
        }
        crawlerService.crawlAll(maxPages, UserContext.getUserId(), false);
        return Result.success("全量采集任务已启动（异步执行中）");
    }

    /**
     * 针对单个关键词采集（异步，立即返回，后台执行）
     * POST /api/admin/crawler/query?keyword=Huawei&maxPages=10
     *
     * @param maxPages 该关键词最多爬取的页数；不传则使用配置 courtlistener.max-pages
     */
    @PostMapping("/query")
    public Result<String> crawlByKeyword(@RequestParam String keyword,
                                         @RequestParam(required = false) Integer maxPages) {
        if (crawlerService.isRunning()) {
            return Result.fail(400, "采集任务正在运行中，请稍后再试");
        }
        log.info("[手动采集] 关键词: {}, maxPages: {}", keyword, maxPages);
        crawlerService.crawlByQueryAsync(keyword, maxPages, UserContext.getUserId());
        return Result.success("关键词「" + keyword + "」采集任务已启动（异步执行中）");
    }

    /**
     * 查询 CourtListener 采集任务运行状态
     * GET /api/admin/crawler/status
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus() {
        return Result.success(Map.of(
                "running", crawlerService.isRunning(),
                "message", crawlerService.isRunning() ? "采集任务运行中" : "采集任务空闲"
        ));
    }

    // ─── 关键词管理 ───────────────────────────────────────────────────────

    /**
     * 获取当前全量采集关键词列表
     * GET /api/admin/crawler/keywords
     */
    @GetMapping("/keywords")
    public Result<List<String>> getKeywords() {
        return Result.success(crawlerService.getSearchQueries());
    }

    /**
     * 新增单个关键词
     * POST /api/admin/crawler/keywords?keyword=xxx
     */
    @PostMapping("/keywords")
    public Result<List<String>> addKeyword(@RequestParam String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Result.fail(400, "关键词不能为空");
        }
        boolean added = crawlerService.addSearchQuery(keyword);
        if (!added) {
            return Result.fail(400, "关键词「" + keyword.trim() + "」已存在");
        }
        return Result.success("关键词已添加", crawlerService.getSearchQueries());
    }

    /**
     * 删除单个关键词
     * DELETE /api/admin/crawler/keywords?keyword=xxx
     */
    @DeleteMapping("/keywords")
    public Result<List<String>> removeKeyword(@RequestParam String keyword) {
        boolean removed = crawlerService.removeSearchQuery(keyword);
        if (!removed) {
            return Result.fail(400, "关键词「" + keyword.trim() + "」不存在");
        }
        return Result.success("关键词已删除", crawlerService.getSearchQueries());
    }

    /**
     * 全量替换关键词列表
     * PUT /api/admin/crawler/keywords
     */
    @PutMapping("/keywords")
    public Result<List<String>> setKeywords(@RequestBody List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return Result.fail(400, "关键词列表不能为空");
        }
        crawlerService.setSearchQueries(keywords);
        return Result.success("关键词列表已更新", crawlerService.getSearchQueries());
    }

    // ─── Python 爬虫进程管理 ──────────────────────────────────────────────

    /**
     * 启动指定 Python 爬虫（异步，不阻塞）
     * 爬虫脚本命名规范：{scriptDir}/{crawler}_crawler.py
     * POST /api/admin/crawler/python/start?crawler=site_a
     */
    @PostMapping("/python/start")
    public Result<String> startPythonCrawler(@RequestParam String crawler) {
        if (pythonCrawlerService.isRunning(crawler)) {
            return Result.fail(400, crawler + " 爬虫正在运行中，请勿重复启动");
        }
        pythonCrawlerService.start(crawler);
        return Result.success(crawler + " 爬虫已启动（异步执行中，日志见 logs/crawler_" + crawler + ".log）");
    }

    /**
     * 停止指定 Python 爬虫
     * POST /api/admin/crawler/python/stop?crawler=site_a
     */
    @PostMapping("/python/stop")
    public Result<String> stopPythonCrawler(@RequestParam String crawler) {
        boolean stopped = pythonCrawlerService.stop(crawler);
        return stopped
                ? Result.success(crawler + " 爬虫已停止")
                : Result.fail(400, crawler + " 爬虫未在运行");
    }

    /**
     * 查询所有可用 Python 爬虫及运行状态
     * GET /api/admin/crawler/python/status
     */
    @GetMapping("/python/status")
    public Result<Map<String, Boolean>> getPythonStatus() {
        return Result.success(pythonCrawlerService.getAllStatus());
    }

    // ─── 日本裁判所专属爬虫 ───────────────────────────────────────────────────

    /**
     * 携带检索参数启动日本裁判所爬虫
     * POST /api/admin/crawler/python/start-japan
     * Body: { "query1":"中華", "maxPages":20, ... }
     */
    @PostMapping("/python/start-japan")
    public Result<String> startJapanCrawler(@RequestBody JapanCrawlerParamDTO params) {
        final String CRAWLER = "japan_courts";
        if (pythonCrawlerService.isRunning(CRAWLER)) {
            return Result.fail(400, "日本裁判所爬虫正在运行中，请勿重复启动");
        }
        if (params.getQuery1() == null || params.getQuery1().isBlank()) {
            return Result.fail(400, "第1检索关键词（query1）不能为空");
        }
        List<String> argList = params.toArgList();
        log.info("[日本裁判所爬虫] 启动参数: {}", argList);

        final String paramsJson;
        try {
            paramsJson = objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            return Result.fail(400, "参数序列化失败");
        }
        Long jobId = crawlJobRecordService.startJob(
                CrawlJobRecord.TYPE_JAPAN_COURTS, paramsJson, UserContext.getUserId());
        pythonCrawlerService.startWithArgs(CRAWLER, argList, exitCode -> {
            if (exitCode == 0) {
                crawlJobRecordService.finishSuccess(jobId, null);
            } else {
                String msg = exitCode == -1
                        ? "爬虫进程未启动（脚本不存在或启动失败）"
                        : "进程退出码: " + exitCode;
                crawlJobRecordService.finishFailure(jobId, msg);
            }
        });

        return Result.success(
            "日本裁判所爬虫已启动（异步执行中）\n关键词: " + params.getQuery1()
            + "，最大页数: " + params.getMaxPages()
            + "\n日志见 logs/crawler_japan_courts.log"
        );
    }

    /**
     * 停止日本裁判所爬虫
     * POST /api/admin/crawler/python/stop-japan
     */
    @PostMapping("/python/stop-japan")
    public Result<String> stopJapanCrawler() {
        final String CRAWLER = "japan_courts";
        boolean stopped = pythonCrawlerService.stop(CRAWLER);
        return stopped
                ? Result.success("日本裁判所爬虫已停止")
                : Result.fail(400, "日本裁判所爬虫未在运行");
    }

    /**
     * 查询日本裁判所爬虫运行状态
     * GET /api/admin/crawler/python/status-japan
     */
    @GetMapping("/python/status-japan")
    public Result<Map<String, Object>> getJapanCrawlerStatus() {
        final String CRAWLER = "japan_courts";
        boolean running = pythonCrawlerService.isRunning(CRAWLER);
        return Result.success(Map.of(
                "running", running,
                "message", running ? "日本裁判所爬虫运行中" : "日本裁判所爬虫空闲"
        ));
    }
}
