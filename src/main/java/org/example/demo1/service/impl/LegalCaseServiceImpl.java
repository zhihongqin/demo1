package org.example.demo1.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo1.agent.CaseAgentService;
import org.example.demo1.agent.FastgptKnowledgeSyncService;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.ResultCode;
import org.example.demo1.dto.CaseQueryDTO;
import org.example.demo1.dto.CaseSaveDTO;
import org.example.demo1.dto.CaseUpdateDTO;
import org.example.demo1.entity.*;
import org.example.demo1.mapper.*;
import org.example.demo1.service.LegalCaseService;
import org.example.demo1.vo.*;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegalCaseServiceImpl extends ServiceImpl<LegalCaseMapper, LegalCase> implements LegalCaseService {

    private static final OkHttpClient PDF_HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    private static final Set<String> ALLOWED_ORDER_BY = Set.of(
            "created_at", "importance_score", "view_count", "judgment_date");

    private final CaseAgentService caseAgentService;
    private final FastgptKnowledgeSyncService fastgptKnowledgeSyncService;
    private final UserFavoriteMapper userFavoriteMapper;
    private final CaseTranslationMapper caseTranslationMapper;
    private final CaseSummaryMapper caseSummaryMapper;
    private final CaseScoreMapper caseScoreMapper;
    private final SearchHistoryMapper searchHistoryMapper;
    private final BrowseHistoryMapper browseHistoryMapper;

    @Override
    public IPage<CaseListVO> queryCases(CaseQueryDTO dto, Long userId, boolean isAdmin) {
        // 白名单校验排序字段，防止 SQL 注入（${}拼接不会自动转义）
        String orderBy = ALLOWED_ORDER_BY.contains(dto.getOrderBy()) ? dto.getOrderBy() : "created_at";
        String orderDir = "asc".equalsIgnoreCase(dto.getOrderDir()) ? "ASC" : "DESC";

        Page<CaseListVO> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        IPage<CaseListVO> result = baseMapper.searchCases(page, dto.getKeyword(),
                dto.getCaseType(), dto.getCountry(), userId, isAdmin, orderBy, orderDir);

        // 记录搜索历史（含未登录用户，用于热门词统计）
        if (dto.getKeyword() != null && !dto.getKeyword().isBlank()) {
            SearchHistory history = new SearchHistory();
            history.setUserId(userId);
            history.setKeyword(dto.getKeyword().trim());
            history.setSearchType(1);
            history.setResultCount((int) result.getTotal());
            searchHistoryMapper.insert(history);
        }
        return result;
    }

    @Override
    public CaseDetailVO getCaseDetail(Long caseId, Long userId, boolean isAdmin) {
        LegalCase legalCase = isAdmin
                ? baseMapper.selectByIdIgnoreDeleted(caseId)
                : getById(caseId);
        if (legalCase == null) {
            throw new BusinessException(ResultCode.CASE_NOT_EXIST);
        }

        // 增加查看次数
        legalCase.setViewCount(legalCase.getViewCount() == null ? 1 : legalCase.getViewCount() + 1);
        updateById(legalCase);

        CaseDetailVO vo = new CaseDetailVO();
        BeanUtils.copyProperties(legalCase, vo);

        // 登录用户：记录浏览历史（已存在则更新时间，不存在则插入）
        if (userId != null) {
            BrowseHistory existing = browseHistoryMapper.selectOne(
                    new LambdaQueryWrapper<BrowseHistory>()
                            .eq(BrowseHistory::getUserId, userId)
                            .eq(BrowseHistory::getCaseId, caseId));
            if (existing != null) {
                existing.setCreatedAt(java.time.LocalDateTime.now());
                browseHistoryMapper.updateById(existing);
            } else {
                BrowseHistory browseHistory = new BrowseHistory();
                browseHistory.setUserId(userId);
                browseHistory.setCaseId(caseId);
                browseHistoryMapper.insert(browseHistory);
            }
        }

        // 是否收藏
        if (userId != null) {
            UserFavorite favorite = userFavoriteMapper.selectOne(
                    new LambdaQueryWrapper<UserFavorite>()
                            .eq(UserFavorite::getUserId, userId)
                            .eq(UserFavorite::getCaseId, caseId));
            vo.setIsFavorited(favorite != null);
        } else {
            vo.setIsFavorited(false);
        }

        // 摘要信息（取最新一条已完成的记录）
        CaseSummary caseSummary = caseSummaryMapper.selectOne(
                new LambdaQueryWrapper<CaseSummary>()
                        .eq(CaseSummary::getCaseId, caseId)
                        .eq(CaseSummary::getStatus, 2)
                        .orderByDesc(CaseSummary::getId)
                        .last("LIMIT 1"));
        if (caseSummary != null) {
            CaseSummaryVO summaryVO = new CaseSummaryVO();
            BeanUtils.copyProperties(caseSummary, summaryVO);
            vo.setSummary(summaryVO);
        }

        // 评分信息（取最新一条已完成的记录）
        CaseScore caseScore = caseScoreMapper.selectOne(
                new LambdaQueryWrapper<CaseScore>()
                        .eq(CaseScore::getCaseId, caseId)
                        .eq(CaseScore::getStatus, 2)
                        .orderByDesc(CaseScore::getId)
                        .last("LIMIT 1"));
        if (caseScore != null) {
            CaseScoreVO scoreVO = new CaseScoreVO();
            BeanUtils.copyProperties(caseScore, scoreVO);
            vo.setScore(scoreVO);
        }

        return vo;
    }

    @Override
    @Transactional
    public Long saveCase(CaseSaveDTO dto) {
        LegalCase legalCase;
        if (dto.getId() != null) {
            legalCase = getById(dto.getId());
            if (legalCase == null) {
                throw new BusinessException(ResultCode.CASE_NOT_EXIST);
            }
        } else {
            legalCase = new LegalCase();
            legalCase.setAiStatus(0);
            legalCase.setViewCount(0);
            legalCase.setFavoriteCount(0);
        }

        BeanUtils.copyProperties(dto, legalCase, "id");
        legalCase.setAiStatus(0);

        saveOrUpdate(legalCase);

        // 异步触发AI处理
        caseAgentService.processCase(legalCase.getId());

        return legalCase.getId();
    }

    @Override
    @Transactional
    public void updateCase(Long caseId, CaseUpdateDTO dto) {
        // 管理员编辑时允许操作已逻辑删除的案例
        LegalCase legalCase = baseMapper.selectByIdIgnoreDeleted(caseId);
        if (legalCase == null) {
            throw new BusinessException(ResultCode.CASE_NOT_EXIST);
        }
        if (dto.getCaseNo() != null)          legalCase.setCaseNo(dto.getCaseNo());
        if (dto.getTitleZh() != null)         legalCase.setTitleZh(dto.getTitleZh());
        if (dto.getTitleEn() != null)         legalCase.setTitleEn(dto.getTitleEn());
        if (dto.getCaseReason() != null)      legalCase.setCaseReason(dto.getCaseReason());
        if (dto.getCaseType() != null)        legalCase.setCaseType(dto.getCaseType());
        if (dto.getCountry() != null)         legalCase.setCountry(dto.getCountry());
        if (dto.getCourt() != null)           legalCase.setCourt(dto.getCourt());
        if (dto.getJudgmentDate() != null)    legalCase.setJudgmentDate(dto.getJudgmentDate());
        if (dto.getContentEn() != null)       legalCase.setContentEn(dto.getContentEn());
        if (dto.getContentZh() != null)       legalCase.setContentZh(dto.getContentZh());
        if (dto.getDisputeFocus() != null)    legalCase.setDisputeFocus(dto.getDisputeFocus());
        if (dto.getJudgmentResult() != null)  legalCase.setJudgmentResult(dto.getJudgmentResult());
        if (dto.getSummaryZh() != null)       legalCase.setSummaryZh(dto.getSummaryZh());
        if (dto.getImportanceScore() != null) legalCase.setImportanceScore(dto.getImportanceScore());
        if (dto.getScoreReason() != null)     legalCase.setScoreReason(dto.getScoreReason());
        if (dto.getKeywords() != null)        legalCase.setKeywords(dto.getKeywords());
        if (dto.getLegalProvisions() != null) legalCase.setLegalProvisions(dto.getLegalProvisions());
        if (dto.getSource() != null)          legalCase.setSource(dto.getSource());
        if (dto.getUrl() != null)             legalCase.setUrl(dto.getUrl());
        updateById(legalCase);
        log.info("案例内容手动修正: caseId={}", caseId);
    }

    @Override
    public void triggerTranslation(Long caseId) {
        checkCaseExists(caseId);
        caseAgentService.triggerTranslation(caseId);
    }

    @Override
    public void triggerEnrich(Long caseId) {
        checkCaseExists(caseId);
        caseAgentService.triggerEnrich(caseId);
    }

    @Override
    public void triggerSummary(Long caseId) {
        checkCaseExists(caseId);
        caseAgentService.triggerSummary(caseId);
    }

    @Override
    public void triggerScore(Long caseId) {
        checkCaseExists(caseId);
        caseAgentService.triggerScore(caseId);
    }

    @Override
    @Transactional
    public boolean toggleFavorite(Long caseId, Long userId) {
        LegalCase legalCase = getById(caseId);
        if (legalCase == null) {
            throw new BusinessException(ResultCode.CASE_NOT_EXIST);
        }

        UserFavorite existing = userFavoriteMapper.selectOne(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getCaseId, caseId));

        if (existing != null) {
            userFavoriteMapper.deleteById(existing.getId());
            // 减少收藏数
            legalCase.setFavoriteCount(Math.max(0, (legalCase.getFavoriteCount() == null ? 0 : legalCase.getFavoriteCount()) - 1));
            updateById(legalCase);
            return false;
        } else {
            UserFavorite favorite = new UserFavorite();
            favorite.setUserId(userId);
            favorite.setCaseId(caseId);
            userFavoriteMapper.insert(favorite);
            // 增加收藏数
            legalCase.setFavoriteCount((legalCase.getFavoriteCount() == null ? 0 : legalCase.getFavoriteCount()) + 1);
            updateById(legalCase);
            return true;
        }
    }

    @Override
    public IPage<CaseListVO> getFavorites(Long userId, Integer pageNum, Integer pageSize) {
        Page<CaseListVO> page = new Page<>(pageNum, pageSize);
        return baseMapper.selectFavoritesByUserId(page, userId);
    }

    @Override
    public void markAiCompleted(Long caseId) {
        LegalCase legalCase = baseMapper.selectByIdIgnoreDeleted(caseId);
        if (legalCase == null) {
            throw new BusinessException(ResultCode.CASE_NOT_EXIST);
        }
        legalCase.setAiStatus(2);
        baseMapper.updateById(legalCase);
        log.info("案例 AI 状态手动标记为已完成: caseId={}", caseId);
        if (legalCase.getIsDeleted() == null || legalCase.getIsDeleted() == 0) {
            fastgptKnowledgeSyncService.syncCaseAsync(caseId);
        }
    }

    @Override
    public void triggerFastgptKnowledgeSync(Long caseId) {
        fastgptKnowledgeSyncService.scheduleManualSync(caseId);
    }

    @Override
    public void softDeleteCase(Long caseId) {
        // getById 会自动过滤已删除记录，所以这里直接 removeById（触发逻辑删除）
        LegalCase legalCase = getById(caseId);
        if (legalCase == null) {
            throw new BusinessException(ResultCode.CASE_NOT_EXIST);
        }
        removeById(caseId);
        log.info("案例逻辑删除: caseId={}", caseId);
    }

    @Override
    public void restoreCase(Long caseId) {
        // 使用自定义 SQL 绕过 @TableLogic，将 is_deleted=1 的记录恢复
        int restored = baseMapper.restoreById(caseId);
        if (restored == 0) {
            throw new BusinessException(ResultCode.CASE_NOT_EXIST, "案例不存在或未处于删除状态");
        }
        log.info("案例逻辑恢复: caseId={}", caseId);
    }

    @Override
    @Transactional
    public void hardDeleteCase(Long caseId) {
        // 使用自定义 SQL 绕过 @TableLogic，无论是否已逻辑删除均彻底移除
        int deleted = baseMapper.physicalDeleteById(caseId);
        if (deleted == 0) {
            throw new BusinessException(ResultCode.CASE_NOT_EXIST);
        }
        log.info("案例物理删除: caseId={}", caseId);
    }

    @Override
    public List<CaseTranslationRecordVO> getTranslationRecords(Long caseId) {
        checkCaseExists(caseId);
        return caseTranslationMapper.selectList(
                        new LambdaQueryWrapper<CaseTranslation>()
                                .eq(CaseTranslation::getCaseId, caseId)
                                .orderByDesc(CaseTranslation::getCreatedAt))
                .stream()
                .filter(Objects::nonNull)
                .map(e -> toTranslationRecordVO(e))
                .collect(Collectors.toList());
    }

    @Override
    public List<CaseSummaryRecordVO> getSummaryRecords(Long caseId) {
        checkCaseExists(caseId);
        return caseSummaryMapper.selectList(
                        new LambdaQueryWrapper<CaseSummary>()
                                .eq(CaseSummary::getCaseId, caseId)
                                .orderByDesc(CaseSummary::getCreatedAt))
                .stream()
                .filter(Objects::nonNull)
                .map(e -> toSummaryRecordVO(e))
                .collect(Collectors.toList());
    }

    @Override
    public List<CaseScoreRecordVO> getScoreRecords(Long caseId) {
        checkCaseExists(caseId);
        return caseScoreMapper.selectList(
                        new LambdaQueryWrapper<CaseScore>()
                                .eq(CaseScore::getCaseId, caseId)
                                .orderByDesc(CaseScore::getCreatedAt))
                .stream()
                .filter(Objects::nonNull)
                .map(e -> toScoreRecordVO(e))
                .collect(Collectors.toList());
    }

    private CaseTranslationRecordVO toTranslationRecordVO(CaseTranslation e) {
        CaseTranslationRecordVO vo = new CaseTranslationRecordVO();
        BeanUtils.copyProperties(Objects.requireNonNull(e), vo);
        return vo;
    }

    private CaseSummaryRecordVO toSummaryRecordVO(CaseSummary e) {
        CaseSummaryRecordVO vo = new CaseSummaryRecordVO();
        BeanUtils.copyProperties(Objects.requireNonNull(e), vo);
        return vo;
    }

    private CaseScoreRecordVO toScoreRecordVO(CaseScore e) {
        CaseScoreRecordVO vo = new CaseScoreRecordVO();
        BeanUtils.copyProperties(Objects.requireNonNull(e), vo);
        return vo;
    }

    @Override
    public IPage<BrowseHistoryVO> getBrowseHistory(Long userId, Integer pageNum, Integer pageSize) {
        Page<BrowseHistoryVO> page = new Page<>(pageNum, pageSize);
        return browseHistoryMapper.selectBrowseHistoryPage(page, userId);
    }

    @Override
    @Transactional
    public void deleteBrowseHistory(Long userId, List<Long> ids) {
        browseHistoryMapper.delete(
                new LambdaQueryWrapper<BrowseHistory>()
                        .eq(BrowseHistory::getUserId, userId)
                        .in(BrowseHistory::getId, ids));
    }

    @Override
    public byte[] proxyPdf(Long caseId) {
        LegalCase legalCase = getById(caseId);
        if (legalCase == null) {
            throw new BusinessException(ResultCode.CASE_NOT_EXIST);
        }
        String pdfUrl = legalCase.getPdfUrl();
        if (pdfUrl == null || pdfUrl.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该案例无 PDF 文书链接");
        }

        log.info("代理获取 PDF，caseId={}，pdfUrl={}", caseId, pdfUrl);

        Request request = new Request.Builder()
                .url(pdfUrl)
                // 模拟浏览器请求，绕过日本法院防盗链校验
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/124.0.0.0 Safari/537.36")
                .header("Referer", "https://www.courts.go.jp/")
                .header("Accept", "application/pdf,*/*")
                .build();

        try (Response response = PDF_HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("PDF 获取失败，caseId={}，statusCode={}", caseId, response.code());
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "PDF 文书获取失败，状态码：" + response.code());
            }
            byte[] bytes = response.body().bytes();
            log.info("PDF 获取成功，caseId={}，大小={}KB", caseId, bytes.length / 1024);
            return bytes;
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("PDF 代理请求异常，caseId={}，pdfUrl={}", caseId, pdfUrl, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "PDF 文书网络请求失败：" + e.getMessage());
        }
    }

    private void checkCaseExists(Long caseId) {
        if (baseMapper.selectByIdIgnoreDeleted(caseId) == null) {
            throw new BusinessException(ResultCode.CASE_NOT_EXIST);
        }
    }
}
