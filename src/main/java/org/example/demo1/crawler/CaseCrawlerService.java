package org.example.demo1.crawler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo1.agent.CaseAgentService;
import org.example.demo1.entity.LegalCase;
import org.example.demo1.mapper.LegalCaseMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CourtListener 案例采集服务
 * 负责调用 API、过滤涉华案例并写入数据库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaseCrawlerService {

    /**
     * 轮流搜索的涉华关键词
     * 多个关键词覆盖不同角度，避免遗漏
     */
    private static final List<String> SEARCH_QUERIES = List.of(
            "China",
            "Chinese",
            "PRC",
            "Huawei",
            "ZTE",
            "Alibaba",
            "ByteDance",
            "TikTok",
            "Chinese national",
            "Chinese company",
            "Chinese citizen",
            "People's Republic of China"
    );

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 防止重复运行 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final CourtListenerClient apiClient;
    private final ChinaRelatedFilter filter;
    private final LegalCaseMapper legalCaseMapper;
    private final CaseAgentService caseAgentService;

    @Value("${courtlistener.max-pages:10}")
    private int maxPages;

    @Value("${courtlistener.request-delay-ms:500}")
    private long requestDelayMs;

    @Value("${courtlistener.filter-threshold:2}")
    private int filterThreshold;

    /**
     * 异步启动全量采集
     * 遍历所有搜索关键词，逐页采集并过滤涉华案例入库
     */
    @Async
    public void crawlAll() {
        if (!running.compareAndSet(false, true)) {
            log.warn("[采集] 任务已在运行中，跳过本次触发");
            return;
        }
        log.info("[采集] 开始全量采集，共 {} 个搜索词，每词最多 {} 页", SEARCH_QUERIES.size(), maxPages);
        int totalSaved = 0;
        try {
            for (String query : SEARCH_QUERIES) {
                int saved = crawlByQuery(query);
                totalSaved += saved;
                sleep(2000);
            }
        } finally {
            running.set(false);
            log.info("[采集] 全量采集完成，本次共入库 {} 条案例", totalSaved);
        }
    }

    /**
     * 针对单个关键词采集
     *
     * @param query 搜索关键词
     * @return 本次入库数量
     */
    public int crawlByQuery(String query) {
        log.info("[采集] 开始搜索关键词: '{}'", query);
        int savedCount = 0;
        String nextUrl = null;

        for (int page = 1; page <= maxPages; page++) {
            JsonNode data = apiClient.searchOpinions(query, nextUrl);
            if (data == null) {
                log.warn("[采集] 关键词 '{}' 第 {} 页请求失败，停止", query, page);
                break;
            }

            JsonNode results = data.path("results");
            if (!results.isArray() || results.isEmpty()) {
                log.info("[采集] 关键词 '{}' 第 {} 页无结果，停止", query, page);
                break;
            }

            log.info("[采集] 关键词 '{}' 第 {} 页，共 {} 条结果", query, page, results.size());

            for (JsonNode item : results) {
                boolean saved = processItem(item);
                if (saved) savedCount++;
                sleep(requestDelayMs);
            }

            // 获取下一页 URL
            String next = data.path("next").asText("");
            nextUrl = next.isBlank() ? null : next;
            if (nextUrl == null) {
                log.info("[采集] 关键词 '{}' 已到最后一页", query);
                break;
            }
            sleep(1000);
        }

        log.info("[采集] 关键词 '{}' 完成，本次入库 {} 条", query, savedCount);
        return savedCount;
    }

    /**
     * 处理单条搜索结果
     *
     * @return true 表示已入库
     */
    private boolean processItem(JsonNode item) {
        String clusterId = item.path("cluster_id").asText(item.path("id").asText("")).trim();
        String caseName  = item.path("caseName").asText(item.path("case_name").asText("")).trim();
        String court     = item.path("court_id").asText("").trim();
        String dateFiled = item.path("dateFiled").asText(item.path("date_filed").asText("")).trim();
        String absUrl    = item.path("absolute_url").asText("").trim();
        String fullUrl   = "https://www.courtlistener.com" + absUrl;

        if (clusterId.isBlank() || absUrl.isBlank()) {
            return false;
        }

        // 一次过滤：对 case_name + snippet 进行宽松过滤（threshold=1）
        String snippet = item.path("snippet").asText("").trim();
        if (!filter.isChinaRelated(caseName + " " + snippet, 1)) {
            return false;
        }

        // 去重判断
        if (existsByUrl(fullUrl)) {
            log.debug("[跳过] 已存在: {}", caseName);
            return false;
        }

        // 获取全文
        String content = apiClient.fetchOpinionText(clusterId);

        // 二次过滤：全文命中涉华指标 >= filterThreshold
        if (!filter.isChinaRelated(content, filterThreshold)) {
            log.debug("[过滤] 全文涉华度不足，跳过: {}", caseName);
            return false;
        }

        // 截断内容，防止超出数据库字段限制（LONGTEXT 理论上不限，但防止异常）
        String contentToSave = content.length() > 200_000 ? content.substring(0, 200_000) : content;

        LegalCase legalCase = new LegalCase();
        legalCase.setCaseNo(clusterId);
        legalCase.setTitleEn(caseName.length() > 500 ? caseName.substring(0, 500) : caseName);
        legalCase.setCountry("USA");
        legalCase.setCourt(court.length() > 200 ? court.substring(0, 200) : court);
        legalCase.setJudgmentDate(parseDate(dateFiled));
        legalCase.setContentEn(contentToSave);
        legalCase.setSource("CourtListener");
        legalCase.setUrl(fullUrl);
        legalCase.setAiStatus(0);
        legalCase.setViewCount(0);
        legalCase.setFavoriteCount(0);
        legalCase.setCreatedAt(LocalDateTime.now());
        legalCase.setUpdatedAt(LocalDateTime.now());

        legalCaseMapper.insert(legalCase);
        log.info("[入库] {}", caseName);

        // 入库后异步触发 AI 处理（翻译 + 摘要 + 评分）
        caseAgentService.processCase(legalCase.getId());
        log.info("[AI处理] 已触发异步AI处理: caseId={}", legalCase.getId());
        return true;
    }

    private boolean existsByUrl(String url) {
        return legalCaseMapper.selectCount(
                new LambdaQueryWrapper<LegalCase>().eq(LegalCase::getUrl, url)
        ) > 0;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isRunning() {
        return running.get();
    }
}
