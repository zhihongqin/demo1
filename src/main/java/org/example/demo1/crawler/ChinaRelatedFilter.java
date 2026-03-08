package org.example.demo1.crawler;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 涉华案例过滤器
 * 通过关键词命中数判断案例是否真正涉及中国
 */
@Component
public class ChinaRelatedFilter {

    /**
     * 涉华指标关键词列表
     * 覆盖：国家/政治实体、知名中国企业、地名、当事人身份描述
     */
    private static final List<String> CHINA_INDICATORS = List.of(
            // 国家/政治实体
            "china", "chinese", "prc", "people's republic of china",
            // 常见中国企业
            "huawei", "zte", "alibaba", "tencent", "bytedance", "tiktok",
            "cnooc", "sinopec", "baidu", "xiaomi", "dji", "hikvision",
            "china national", "china-based",
            // 中国主要城市
            "beijing", "shanghai", "shenzhen", "guangzhou", "chengdu",
            "wuhan", "hangzhou", "nanjing",
            // 当事人身份描述
            "chinese national", "chinese citizen", "chinese company",
            "chinese defendant", "chinese plaintiff", "chinese corporation",
            "chinese manufacturer", "chinese government",
            // 其他涉华表达
            "sino-", "made in china", "chinese-made", "china-made"
    );

    /**
     * 判断文本是否涉及中国
     *
     * @param text      待检测文本
     * @param threshold 最少命中关键词数（建议：宽松=1，严格=2~3）
     * @return 是否涉华
     */
    public boolean isChinaRelated(String text, int threshold) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase();
        long hits = CHINA_INDICATORS.stream()
                .filter(lower::contains)
                .count();
        return hits >= threshold;
    }
}
