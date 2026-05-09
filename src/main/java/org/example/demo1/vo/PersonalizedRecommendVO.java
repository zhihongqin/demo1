package org.example.demo1.vo;

import lombok.Data;

import java.util.List;

/**
 * 个性化推荐结果 VO
 */
@Data
public class PersonalizedRecommendVO {

    /**
     * 推荐来源标识
     * <ul>
     *   <li>{@code "personalized"} - 基于用户行为的个性化推荐</li>
     *   <li>{@code "popular"} - 降级为热门推荐（用户无行为数据或未登录）</li>
     * </ul>
     */
    private String basis;

    /**
     * 用户偏好标签（前端可展示"根据您的兴趣推荐"）
     * 未登录或无法推断偏好时为 null
     */
    private List<String> preferTags;

    /** 推荐案例列表 */
    private List<CaseListVO> cases;
}
