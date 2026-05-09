package org.example.demo1.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 相似度计算工具类，用于案例推荐
 */
public final class SimilarityUtil {

    private SimilarityUtil() {}

    /**
     * 计算两个逗号分隔关键词字符串之间的 Jaccard 相似度
     * Jaccard = |交集| / |并集|
     *
     * @param keywordsA 关键词字符串A（逗号分隔）
     * @param keywordsB 关键词字符串B（逗号分隔）
     * @return 相似度 [0.0, 1.0]；任一为空则返回 0
     */
    public static double jaccardSimilarity(String keywordsA, String keywordsB) {
        Set<String> setA = parseKeywords(keywordsA);
        Set<String> setB = parseKeywords(keywordsB);
        if (setA.isEmpty() || setB.isEmpty()) {
            return 0.0;
        }
        long intersectionSize = setA.stream().filter(setB::contains).count();
        long unionSize = setA.size() + setB.size() - intersectionSize;
        return unionSize == 0 ? 0.0 : (double) intersectionSize / unionSize;
    }

    /**
     * 计算两个案由字符串的相似度（基于包含关系）
     *
     * @return 完全相同返回1.0，互相包含返回0.6，无关返回0.0
     */
    public static double caseReasonSimilarity(String reasonA, String reasonB) {
        if (reasonA == null || reasonA.isBlank() || reasonB == null || reasonB.isBlank()) {
            return 0.0;
        }
        String a = reasonA.trim();
        String b = reasonB.trim();
        if (a.equals(b)) {
            return 1.0;
        }
        if (a.contains(b) || b.contains(a)) {
            return 0.6;
        }
        return 0.0;
    }

    /**
     * 计算国家与案件类型的组合匹配度
     *
     * @return 国家+类型均匹配=1.0，仅类型匹配=0.5，仅国家匹配=0.3，均不匹配=0.0
     */
    public static double typeAndCountrySimilarity(Integer typeA, String countryA,
                                                   Integer typeB, String countryB) {
        boolean sameType = typeA != null && typeA.equals(typeB);
        boolean sameCountry = countryA != null && countryA.equals(countryB);
        if (sameType && sameCountry) return 1.0;
        if (sameType) return 0.5;
        if (sameCountry) return 0.3;
        return 0.0;
    }

    /**
     * 综合相似度：关键词(50%) + 案由(30%) + 国家类型(20%)
     */
    public static double compositeSimilarity(String keywordsA, String reasonA,
                                              Integer typeA, String countryA,
                                              String keywordsB, String reasonB,
                                              Integer typeB, String countryB) {
        double kwSim = jaccardSimilarity(keywordsA, keywordsB);
        double reasonSim = caseReasonSimilarity(reasonA, reasonB);
        double typeSim = typeAndCountrySimilarity(typeA, countryA, typeB, countryB);
        return kwSim * 0.5 + reasonSim * 0.3 + typeSim * 0.2;
    }

    /**
     * 将逗号分隔的关键词字符串解析为 Set（自动去空格）
     */
    public static Set<String> parseKeywords(String keywords) {
        if (keywords == null || keywords.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>();
        for (String kw : keywords.split(",")) {
            String trimmed = kw.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
