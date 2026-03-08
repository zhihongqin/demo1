package org.example.demo1.crawler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 案例采集定时任务
 * 每天凌晨 2 点自动触发全量采集
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaseCrawlerScheduler {

    private final CaseCrawlerService crawlerService;

    /**
     * 每天凌晨 2:00 自动采集
     * cron 表达式：秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledCrawl() {
        log.info("[定时采集] 触发每日定时采集任务");
        crawlerService.crawlAll();
    }
}
