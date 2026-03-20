package org.example.demo1.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.ResultCode;
import org.example.demo1.entity.LegalCase;
import org.example.demo1.mapper.LegalCaseMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 将已处理完成的案例文本推送到 FastGPT 知识库（RAG 数据源）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FastgptKnowledgeSyncService {

    private static final int AI_STATUS_DONE = 2;
    private static final int SYNC_PENDING = 0;
    private static final int SYNC_SYNCING = 1;
    private static final int SYNC_SUCCESS = 2;
    private static final int SYNC_FAILED = 3;

    private final LegalCaseMapper legalCaseMapper;
    private final FastGptDatasetClient fastGptDatasetClient;

    @Value("${fastgpt.knowledge-dataset-id}")
    private String knowledgeDatasetId;

    @Value("${fastgpt.api-key}")
    private String defaultApiKey;

    @Value("${fastgpt.knowledge-api-key:}")
    private String knowledgeApiKeyOverride;

    /**
     * 异步推送，供 AI 流水线完成后、管理员手动触发调用，不阻塞调用方线程。
     */
    @Async("fastgptSyncExecutor")
    public void syncCaseAsync(Long caseId) {
        try {
            syncCaseInternal(caseId);
        } catch (Exception e) {
            log.error("FastGPT 知识库异步同步未捕获异常: caseId={}", caseId, e);
        }
    }

    /**
     * 管理员手动触发：先校验状态，再异步执行实际推送。
     */
    public void scheduleManualSync(Long caseId) {
        LegalCase legalCase = legalCaseMapper.selectByIdIgnoreDeleted(caseId);
        if (legalCase == null) {
            throw new BusinessException(ResultCode.CASE_NOT_EXIST);
        }
        if (legalCase.getAiStatus() == null || legalCase.getAiStatus() != AI_STATUS_DONE) {
            throw new BusinessException(ResultCode.CASE_AI_NOT_READY);
        }
        if (legalCase.getIsDeleted() != null && legalCase.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.CASE_NOT_EXIST, "已删除的案例无法同步知识库");
        }
        int st = legalCase.getFastgptSyncStatus() == null ? SYNC_PENDING : legalCase.getFastgptSyncStatus();
        if (st == SYNC_SYNCING) {
            throw new BusinessException(ResultCode.FASTGPT_SYNC_BUSY);
        }
        syncCaseAsync(caseId);
    }

    private void syncCaseInternal(Long caseId) {
        LegalCase legalCase = legalCaseMapper.selectById(caseId);
        if (legalCase == null) {
            log.warn("FastGPT 同步跳过：案例不存在 caseId={}", caseId);
            return;
        }
        if (legalCase.getAiStatus() == null || legalCase.getAiStatus() != AI_STATUS_DONE) {
            log.warn("FastGPT 同步跳过：AI 未完成 caseId={}, aiStatus={}", caseId, legalCase.getAiStatus());
            return;
        }

        int prev = legalCase.getFastgptSyncStatus() == null ? SYNC_PENDING : legalCase.getFastgptSyncStatus();
        if (prev == SYNC_SYNCING) {
            log.info("FastGPT 同步跳过：已在同步中 caseId={}", caseId);
            return;
        }

        legalCase.setFastgptSyncStatus(SYNC_SYNCING);
        legalCase.setFastgptSyncError(null);
        legalCaseMapper.updateById(legalCase);

        String apiKey = resolveApiKey();
        String text = buildKnowledgeText(legalCase);
        if (text == null || text.isBlank()) {
            markFailed(legalCase, "案例正文为空，无法入库");
            return;
        }

        try {
            String collectionId = fastGptDatasetClient.uploadCaseTextAsLocalFile(
                    apiKey, knowledgeDatasetId, legalCase.getId(), text);
            legalCase.setFastgptSyncStatus(SYNC_SUCCESS);
            legalCase.setFastgptSyncedAt(LocalDateTime.now());
            legalCase.setFastgptCollectionId(collectionId);
            legalCase.setFastgptSyncError(null);
            legalCaseMapper.updateById(legalCase);
            log.info("FastGPT 知识库同步成功: caseId={}, collectionId={}", caseId, collectionId);
        } catch (BusinessException e) {
            markFailed(legalCase, e.getMessage());
        } catch (Exception e) {
            log.error("FastGPT 知识库同步异常: caseId={}", caseId, e);
            markFailed(legalCase, e.getMessage() != null ? e.getMessage() : "未知错误");
        }
    }

    private void markFailed(LegalCase legalCase, String err) {
        legalCase.setFastgptSyncStatus(SYNC_FAILED);
        legalCase.setFastgptSyncedAt(LocalDateTime.now());
        legalCase.setFastgptSyncError(truncate(err, 1000));
        legalCaseMapper.updateById(legalCase);
        log.warn("FastGPT 知识库同步失败: caseId={}, err={}", legalCase.getId(), legalCase.getFastgptSyncError());
    }

    private String resolveApiKey() {
        if (knowledgeApiKeyOverride != null && !knowledgeApiKeyOverride.isBlank()) {
            return knowledgeApiKeyOverride.trim();
        }
        return defaultApiKey;
    }

    private static String buildKnowledgeText(LegalCase c) {
        StringBuilder sb = new StringBuilder();
        appendLine(sb, "系统案例ID", c.getId() != null ? String.valueOf(c.getId()) : null);
        appendLine(sb, "案例编号", c.getCaseNo());
        appendLine(sb, "标题（中文）", c.getTitleZh());
        appendLine(sb, "标题（英文）", c.getTitleEn());
        appendLine(sb, "案由", c.getCaseReason());
        appendLine(sb, "案件类型", caseTypeLabel(c.getCaseType()));
        appendLine(sb, "国家/地区", c.getCountry());
        appendLine(sb, "审理法院", c.getCourt());
        appendLine(sb, "判决日期", c.getJudgmentDate() != null ? c.getJudgmentDate().toString() : null);
        appendLine(sb, "关键词", c.getKeywords());
        appendLine(sb, "涉及法律条文", c.getLegalProvisions());
        appendLine(sb, "争议焦点", c.getDisputeFocus());
        appendLine(sb, "判决结果", c.getJudgmentResult());
        appendLine(sb, "核心摘要", c.getSummaryZh());
        appendLine(sb, "重要性评分", c.getImportanceScore() != null ? c.getImportanceScore().toString() : null);
        appendLine(sb, "评分理由", c.getScoreReason());
        appendLine(sb, "来源", c.getSource());
        appendLine(sb, "原始链接", c.getUrl());
        sb.append("\n---\n正文（中文）\n\n");
        if (c.getContentZh() != null && !c.getContentZh().isBlank()) {
            sb.append(c.getContentZh().trim());
        } else {
            sb.append("（无）");
        }
        sb.append("\n\n---\n正文（英文）\n\n");
        if (c.getContentEn() != null && !c.getContentEn().isBlank()) {
            sb.append(c.getContentEn().trim());
        } else {
            sb.append("（无）");
        }
        return sb.toString();
    }

    private static void appendLine(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append("：").append(value.trim()).append('\n');
        }
    }

    private static String caseTypeLabel(Integer caseType) {
        if (caseType == null) {
            return null;
        }
        return switch (caseType) {
            case 1 -> "民事";
            case 2 -> "刑事";
            case 3 -> "行政";
            case 4 -> "商事";
            default -> String.valueOf(caseType);
        };
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
