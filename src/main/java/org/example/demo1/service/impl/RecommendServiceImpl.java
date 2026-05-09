package org.example.demo1.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.ResultCode;
import org.example.demo1.entity.BrowseHistory;
import org.example.demo1.entity.LegalCase;
import org.example.demo1.entity.UserFavorite;
import org.example.demo1.mapper.BrowseHistoryMapper;
import org.example.demo1.mapper.LegalCaseMapper;
import org.example.demo1.mapper.UserFavoriteMapper;
import org.example.demo1.service.RecommendService;
import org.example.demo1.util.SimilarityUtil;
import org.example.demo1.vo.CaseListVO;
import org.example.demo1.vo.PersonalizedRecommendVO;
import org.example.demo1.vo.RecommendCaseVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 案例推荐服务实现
 *
 * <p>三种推荐策略：
 * <ol>
 *   <li><b>相似推荐</b>：关键词Jaccard(50%) + 案由相似(30%) + 国家/类型(20%) 加权打分</li>
 *   <li><b>个性化推荐</b>：构建用户兴趣画像（收藏3分/浏览1分），对候选案例打匹配分</li>
 *   <li><b>热门推荐</b>：importanceScore(50%) + log(viewCount+1)(30%) + log(favoriteCount+1)(20%)</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendServiceImpl implements RecommendService {

    /** 用于个性化推荐时拉取的最近浏览记录条数 */
    private static final int BROWSE_HISTORY_LIMIT = 30;

    /** 相似度阈值：低于此值的候选案例直接过滤 */
    private static final double SIMILARITY_THRESHOLD = 0.15;

    /** 候选池大小：每次最多从数据库拉取参与计算的案例数量 */
    private static final int CANDIDATE_POOL_SIZE = 200;

    private final LegalCaseMapper legalCaseMapper;
    private final BrowseHistoryMapper browseHistoryMapper;
    private final UserFavoriteMapper userFavoriteMapper;

    // ==================== 相似案例推荐 ====================

    @Override
    public List<RecommendCaseVO> similarCases(Long caseId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);

        LegalCase target = legalCaseMapper.selectById(caseId);
        if (target == null) {
            throw new BusinessException(ResultCode.CASE_NOT_EXIST);
        }

        // 候选集：同案件类型或同国家的已完成案例（预过滤，降低全库扫描开销）
        List<LegalCase> candidates = fetchCandidates(target.getCaseType(), target.getCountry(), caseId);

        // 计算相似度并排序
        return candidates.stream()
                .map(c -> scoreSimilarity(target, c))
                .filter(vo -> vo.getSimilarityScore() >= SIMILARITY_THRESHOLD)
                .sorted(Comparator.comparingDouble(RecommendCaseVO::getSimilarityScore).reversed())
                .limit(safeLimit)
                .collect(Collectors.toList());
    }

    /** 查询候选案例：同类型或同国家，已完成AI处理，排除目标案例自身 */
    private List<LegalCase> fetchCandidates(Integer caseType, String country, Long excludeId) {
        LambdaQueryWrapper<LegalCase> wrapper = new LambdaQueryWrapper<LegalCase>()
                .eq(LegalCase::getAiStatus, 2)
                .ne(LegalCase::getId, excludeId)
                .and(w -> w
                        .eq(caseType != null, LegalCase::getCaseType, caseType)
                        .or()
                        .eq(country != null && !country.isBlank(), LegalCase::getCountry, country)
                )
                .last("LIMIT " + CANDIDATE_POOL_SIZE);
        return legalCaseMapper.selectList(wrapper);
    }

    /** 计算目标案例与候选案例的综合相似度，并生成相似理由 */
    private RecommendCaseVO scoreSimilarity(LegalCase target, LegalCase candidate) {
        double kwSim = SimilarityUtil.jaccardSimilarity(target.getKeywords(), candidate.getKeywords());
        double reasonSim = SimilarityUtil.caseReasonSimilarity(target.getCaseReason(), candidate.getCaseReason());
        double typeSim = SimilarityUtil.typeAndCountrySimilarity(
                target.getCaseType(), target.getCountry(),
                candidate.getCaseType(), candidate.getCountry());

        double composite = kwSim * 0.5 + reasonSim * 0.3 + typeSim * 0.2;

        RecommendCaseVO vo = new RecommendCaseVO();
        BeanUtils.copyProperties(candidate, vo);
        vo.setSimilarityScore(Math.round(composite * 100.0) / 100.0);
        vo.setSimilarityReason(buildSimilarityReason(kwSim, reasonSim, typeSim));
        return vo;
    }

    /** 根据各维度相似度生成可读的相似理由描述 */
    private String buildSimilarityReason(double kwSim, double reasonSim, double typeSim) {
        List<String> reasons = new ArrayList<>();
        if (kwSim >= 0.3) reasons.add("关键词高度重合");
        else if (kwSim > 0) reasons.add("关键词部分重合");
        if (reasonSim >= 0.6) reasons.add("案由相似");
        if (typeSim >= 1.0) reasons.add("同国家同类型");
        else if (typeSim >= 0.5) reasons.add("同案件类型");
        else if (typeSim >= 0.3) reasons.add("同国家");
        return reasons.isEmpty() ? "内容相关" : String.join("·", reasons);
    }

    // ==================== 个性化推荐 ====================

    @Override
    public PersonalizedRecommendVO personalizedRecommend(Long userId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        PersonalizedRecommendVO result = new PersonalizedRecommendVO();

        // 未登录或无行为数据时降级为热门推荐
        if (userId == null) {
            result.setBasis("popular");
            result.setCases(toCaseListVO(popularCases(safeLimit)));
            return result;
        }

        // 构建用户兴趣画像
        UserProfile profile = buildUserProfile(userId);

        if (profile.isEmpty()) {
            log.debug("用户无行为数据，降级热门推荐: userId={}", userId);
            result.setBasis("popular");
            result.setCases(toCaseListVO(popularCases(safeLimit)));
            return result;
        }

        // 拉取候选案例（已完成AI处理，排除已交互）
        List<LegalCase> candidates = fetchPersonalizedCandidates(profile.getInteractedCaseIds());

        // 按兴趣匹配分 × 质量权重 排序
        List<CaseListVO> ranked = candidates.stream()
                .map(c -> {
                    double score = calcPersonalizedScore(c, profile);
                    CaseListVO vo = new CaseListVO();
                    BeanUtils.copyProperties(c, vo);
                    return Map.entry(score, vo);
                })
                .filter(e -> e.getKey() > 0)
                .sorted(Map.Entry.<Double, CaseListVO>comparingByKey().reversed())
                .limit(safeLimit)
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());

        if (ranked.isEmpty()) {
            result.setBasis("popular");
            result.setCases(toCaseListVO(popularCases(safeLimit)));
            return result;
        }

        result.setBasis("personalized");
        result.setPreferTags(profile.getTopTags(5));
        result.setCases(ranked);
        return result;
    }

    /** 构建用户兴趣画像：收藏权重3分，浏览权重1分 */
    private UserProfile buildUserProfile(Long userId) {
        UserProfile profile = new UserProfile();

        // 收藏记录（显式强信号，权重3）
        List<UserFavorite> favorites = userFavoriteMapper.selectList(
                new LambdaQueryWrapper<UserFavorite>().eq(UserFavorite::getUserId, userId));
        Set<Long> favoriteCaseIds = favorites.stream()
                .map(UserFavorite::getCaseId).collect(Collectors.toSet());

        // 浏览记录（近30条，权重1）
        List<BrowseHistory> browseList = browseHistoryMapper.selectList(
                new LambdaQueryWrapper<BrowseHistory>()
                        .eq(BrowseHistory::getUserId, userId)
                        .orderByDesc(BrowseHistory::getCreatedAt)
                        .last("LIMIT " + BROWSE_HISTORY_LIMIT));
        Set<Long> browsedCaseIds = browseList.stream()
                .map(BrowseHistory::getCaseId).collect(Collectors.toSet());

        profile.getInteractedCaseIds().addAll(favoriteCaseIds);
        profile.getInteractedCaseIds().addAll(browsedCaseIds);

        // 拉取交互过的案例，提取特征
        Set<Long> allIds = new HashSet<>(profile.getInteractedCaseIds());
        if (allIds.isEmpty()) return profile;

        List<LegalCase> interactedCases = legalCaseMapper.selectBatchIds(allIds);
        for (LegalCase lc : interactedCases) {
            double weight = favoriteCaseIds.contains(lc.getId()) ? 3.0 : 1.0;
            // 案件类型偏好
            if (lc.getCaseType() != null) {
                profile.getCaseTypeWeights().merge(lc.getCaseType(), weight, Double::sum);
            }
            // 国家偏好
            if (lc.getCountry() != null && !lc.getCountry().isBlank()) {
                profile.getCountryWeights().merge(lc.getCountry(), weight, Double::sum);
            }
            // 关键词偏好
            for (String kw : SimilarityUtil.parseKeywords(lc.getKeywords())) {
                profile.getKeywordWeights().merge(kw, weight, Double::sum);
            }
        }
        return profile;
    }

    /** 拉取个性化候选案例：已完成AI处理，排除已交互案例 */
    private List<LegalCase> fetchPersonalizedCandidates(Set<Long> excludeIds) {
        LambdaQueryWrapper<LegalCase> wrapper = new LambdaQueryWrapper<LegalCase>()
                .eq(LegalCase::getAiStatus, 2);
        if (!excludeIds.isEmpty()) {
            wrapper.notIn(LegalCase::getId, excludeIds);
        }
        wrapper.last("LIMIT " + CANDIDATE_POOL_SIZE);
        return legalCaseMapper.selectList(wrapper);
    }

    /** 计算案例对当前用户的个性化推荐分（兴趣匹配 × 内容质量加权） */
    private double calcPersonalizedScore(LegalCase lc, UserProfile profile) {
        double interestScore = 0.0;

        // 案件类型匹配
        if (lc.getCaseType() != null) {
            interestScore += profile.getCaseTypeWeights().getOrDefault(lc.getCaseType(), 0.0);
        }
        // 国家匹配
        if (lc.getCountry() != null) {
            interestScore += profile.getCountryWeights().getOrDefault(lc.getCountry(), 0.0);
        }
        // 关键词匹配
        for (String kw : SimilarityUtil.parseKeywords(lc.getKeywords())) {
            interestScore += profile.getKeywordWeights().getOrDefault(kw, 0.0);
        }

        if (interestScore <= 0) return 0.0;

        // 以 importanceScore 作为内容质量因子（对数平滑，避免高分案例垄断结果）
        int score = lc.getImportanceScore() != null ? lc.getImportanceScore() : 50;
        double qualityFactor = Math.log1p(score / 10.0);

        return interestScore * qualityFactor;
    }

    // ==================== 热门推荐 ====================

    @Override
    public List<RecommendCaseVO> popularCases(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);

        // 拉取已完成AI处理的案例，按热门分排序
        List<LegalCase> candidates = legalCaseMapper.selectList(
                new LambdaQueryWrapper<LegalCase>()
                        .eq(LegalCase::getAiStatus, 2)
                        .isNotNull(LegalCase::getImportanceScore)
                        .last("LIMIT " + CANDIDATE_POOL_SIZE));

        return candidates.stream()
                .map(lc -> {
                    double hotScore = calcPopularScore(lc);
                    RecommendCaseVO vo = new RecommendCaseVO();
                    BeanUtils.copyProperties(lc, vo);
                    vo.setSimilarityScore(Math.round(hotScore * 100.0) / 100.0);
                    vo.setSimilarityReason("综合热门推荐");
                    return vo;
                })
                .sorted(Comparator.comparingDouble(RecommendCaseVO::getSimilarityScore).reversed())
                .limit(safeLimit)
                .collect(Collectors.toList());
    }

    /**
     * 热门分 = importanceScore(50%) + log(viewCount+1)(30%) + log(favoriteCount+1)(20%)
     * 各维度归一化到 [0,1] 后加权
     */
    private double calcPopularScore(LegalCase lc) {
        double importance = (lc.getImportanceScore() != null ? lc.getImportanceScore() : 0) / 100.0;
        int vc = lc.getViewCount() != null ? lc.getViewCount() : 0;
        int fc = lc.getFavoriteCount() != null ? lc.getFavoriteCount() : 0;
        // log 归一化（假设最大浏览量约1000，收藏量约100）
        double viewNorm = Math.log1p(vc) / Math.log1p(1000);
        double favNorm = Math.log1p(fc) / Math.log1p(100);
        return importance * 0.5 + viewNorm * 0.3 + favNorm * 0.2;
    }

    // ==================== 工具方法 ====================

    /** 将 RecommendCaseVO 列表转换为 CaseListVO 列表 */
    private List<CaseListVO> toCaseListVO(List<RecommendCaseVO> source) {
        return source.stream().map(r -> {
            CaseListVO vo = new CaseListVO();
            BeanUtils.copyProperties(r, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    // ==================== 用户画像内部类 ====================

    /**
     * 用户兴趣画像（仅在推荐计算期间临时使用）
     */
    private static class UserProfile {

        /** 已交互（浏览/收藏）的案例ID，推荐时排除 */
        private final Set<Long> interactedCaseIds = new HashSet<>();

        /** 案件类型 → 偏好权重 */
        private final Map<Integer, Double> caseTypeWeights = new HashMap<>();

        /** 国家 → 偏好权重 */
        private final Map<String, Double> countryWeights = new HashMap<>();

        /** 关键词 → 偏好权重 */
        private final Map<String, Double> keywordWeights = new HashMap<>();

        public Set<Long> getInteractedCaseIds() { return interactedCaseIds; }
        public Map<Integer, Double> getCaseTypeWeights() { return caseTypeWeights; }
        public Map<String, Double> getCountryWeights() { return countryWeights; }
        public Map<String, Double> getKeywordWeights() { return keywordWeights; }

        public boolean isEmpty() {
            return caseTypeWeights.isEmpty() && countryWeights.isEmpty() && keywordWeights.isEmpty();
        }

        /**
         * 提取权重最高的偏好标签（用于前端展示"根据您的兴趣"）
         */
        public List<String> getTopTags(int topN) {
            Map<String, Double> merged = new HashMap<>();
            countryWeights.forEach((k, v) -> merged.merge(k, v, Double::sum));
            keywordWeights.forEach((k, v) -> merged.merge(k, v, Double::sum));
            return merged.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(topN)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }
    }
}
