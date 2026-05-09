package org.example.demo1.service;

import org.example.demo1.vo.PersonalizedRecommendVO;
import org.example.demo1.vo.RecommendCaseVO;

import java.util.List;

/**
 * 案例推荐服务接口
 */
public interface RecommendService {

    /**
     * 相似案例推荐（内容相似度）
     * 基于关键词 Jaccard 相似度、案由相似度、国家/案件类型匹配度加权计算
     *
     * @param caseId 目标案例ID
     * @param limit  返回数量（最多20）
     * @return 相似案例列表（含相似度分数和相似理由）
     */
    List<RecommendCaseVO> similarCases(Long caseId, int limit);

    /**
     * 个性化推荐（登录用户）
     * 基于用户近期浏览记录和收藏记录构建兴趣画像，匹配相关案例；
     * 无行为数据时自动降级为热门推荐
     *
     * @param userId 当前用户ID（null 时降级为热门推荐）
     * @param limit  返回数量（最多20）
     * @return 个性化推荐结果（含推荐来源标识和偏好标签）
     */
    PersonalizedRecommendVO personalizedRecommend(Long userId, int limit);

    /**
     * 热门案例推荐（综合评分+浏览+收藏）
     * 公开接口，无需登录
     *
     * @param limit 返回数量（最多20）
     * @return 热门案例列表
     */
    List<RecommendCaseVO> popularCases(int limit);
}
