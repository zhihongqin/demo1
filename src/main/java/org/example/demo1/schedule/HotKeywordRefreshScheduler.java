package org.example.demo1.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo1.service.HotKeywordService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每天凌晨 3 点从搜索历史聚合更新热门词（仅影响 origin=0 且未置顶的记录）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotKeywordRefreshScheduler {

    private final HotKeywordService hotKeywordService;

    @Scheduled(cron = "0 0 3 * * ?")
    public void refreshDaily() {
        try {
            hotKeywordService.refreshFromSearchHistory();
        } catch (Exception e) {
            log.error("定时同步热门搜索词失败", e);
        }
    }
}
