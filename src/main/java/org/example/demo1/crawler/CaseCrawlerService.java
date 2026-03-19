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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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
     * 轮流搜索的涉华关键词（运行时可动态增删，使用线程安全列表）
     */
    private final CopyOnWriteArrayList<String> searchQueries = new CopyOnWriteArrayList<>(List.of(
            "China",
            "Chinese",
            "Huawei",
            "ZTE",
            "Alibaba",
            "ByteDance",
            "TikTok",
            "Chinese national",
            "Chinese company",
            "Chinese citizen",
            "People's Republic of China"
    ));

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
        log.info("[采集] 开始全量采集，共 {} 个搜索词，每词最多 {} 页", searchQueries.size(), maxPages);
        int totalSaved = 0;
        try {
            for (String query : searchQueries) {
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
     * 针对单个关键词异步采集（立即返回，后台执行）
     * 与 crawlAll 共享 running 状态标志，防止并发冲突
     *
     * @param query 搜索关键词
     */
    @Async
    public void crawlByQueryAsync(String query) {
        if (!running.compareAndSet(false, true)) {
            log.warn("[采集] 任务已在运行中，跳过关键词 '{}' 的采集", query);
            return;
        }
        log.info("[手动采集] 异步开始，关键词: '{}'", query);
        try {
            int saved = crawlByQuery(query);
            log.info("[手动采集] 关键词 '{}' 采集完成，入库 {} 条", query, saved);
        } finally {
            running.set(false);
        }
    }

    /**
     * 针对单个关键词采集（同步，供内部和 crawlAll 调用）
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

    /** 获取当前关键词列表（只读副本） */
    public List<String> getSearchQueries() {
        return Collections.unmodifiableList(new ArrayList<>(searchQueries));
    }

    /** 新增关键词（已存在则忽略） */
    public boolean addSearchQuery(String keyword) {
        String kw = keyword.trim();
        if (kw.isBlank() || searchQueries.contains(kw)) {
            return false;
        }
        searchQueries.add(kw);
        log.info("[关键词] 新增: '{}'", kw);
        return true;
    }

    /** 删除关键词 */
    public boolean removeSearchQuery(String keyword) {
        boolean removed = searchQueries.remove(keyword.trim());
        if (removed) log.info("[关键词] 删除: '{}'", keyword.trim());
        return removed;
    }

    /** 全量替换关键词列表 */
    public void setSearchQueries(List<String> keywords) {
        List<String> cleaned = keywords.stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
        searchQueries.clear();
        searchQueries.addAll(cleaned);
        log.info("[关键词] 列表已更新，共 {} 个", cleaned.size());
    }
}
