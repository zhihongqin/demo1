package org.example.demo1.controller;

import lombok.RequiredArgsConstructor;
import org.example.demo1.common.result.Result;
import org.example.demo1.service.RecommendService;
import org.example.demo1.util.UserContext;
import org.example.demo1.vo.PersonalizedRecommendVO;
import org.example.demo1.vo.RecommendCaseVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 案例推荐接口
 *
 * <p>提供三类推荐：
 * <ul>
 *   <li>相似案例推荐（公开）：{@code GET /cases/{id}/similar}</li>
 *   <li>个性化推荐（登录后增强）：{@code GET /recommend/personalized}</li>
 *   <li>热门案例推荐（公开）：{@code GET /recommend/popular}</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendService recommendService;

    /**
     * 相似案例推荐（公开接口）
     * <p>基于关键词 Jaccard 相似度、案由相似度、国家/案件类型综合打分，
     * 推荐与目标案例内容相似的其他案例，用于详情页"相关案例"模块。
     *
     * @param id    目标案例ID
     * @param limit 返回数量，默认6，最多20
     */
    @GetMapping("/cases/{id}/similar")
    public Result<List<RecommendCaseVO>> similarCases(
            @PathVariable Long id,
            @RequestParam(defaultValue = "6") Integer limit) {
        int n = limit == null ? 6 : Math.min(Math.max(limit, 1), 20);
        return Result.success(recommendService.similarCases(id, n));
    }

    /**
     * 个性化推荐（登录后基于行为数据增强；未登录自动降级为热门推荐）
     * <p>基于用户近期浏览记录（权重1）与收藏记录（权重3）构建兴趣画像，
     * 匹配案件类型、国家、关键词等维度，结合 AI 评分作为内容质量因子综合排序。
     *
     * @param limit 返回数量，默认10，最多20
     */
    @GetMapping("/recommend/personalized")
    public Result<PersonalizedRecommendVO> personalizedRecommend(
            @RequestParam(defaultValue = "10") Integer limit) {
        Long userId = UserContext.getUserId();
        int n = limit == null ? 10 : Math.min(Math.max(limit, 1), 20);
        return Result.success(recommendService.personalizedRecommend(userId, n));
    }

    /**
     * 热门案例推荐（公开接口）
     * <p>综合 AI 重要性评分(50%)、浏览量(30%)、收藏量(20%) 加权计算热门度，
     * 用于首页榜单、未登录用户的默认推荐等场景。
     *
     * @param limit 返回数量，默认10，最多20
     */
    @GetMapping("/recommend/popular")
    public Result<List<RecommendCaseVO>> popularCases(
            @RequestParam(defaultValue = "10") Integer limit) {
        int n = limit == null ? 10 : Math.min(Math.max(limit, 1), 20);
        return Result.success(recommendService.popularCases(n));
    }
}
