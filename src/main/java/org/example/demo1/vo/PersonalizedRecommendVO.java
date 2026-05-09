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
     *   <li>{@code "new_user"}     - 已登录但无有效行为数据（冷启动），展示热门但可提示用户</li>
     *   <li>{@code "popular"}      - 未登录或候选集无匹配，纯热门推荐</li>
     * </ul>
     */
    private String basis;

    /**
     * 用户偏好标签（前端可展示"根据您的兴趣推荐"）
     * 仅 basis="personalized" 时有值，其余为 null
     */
    private List<String> preferTags;

    /** 推荐案例列表 */
    private List<CaseListVO> cases;
}
