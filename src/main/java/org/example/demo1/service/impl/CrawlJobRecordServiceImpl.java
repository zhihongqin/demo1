package org.example.demo1.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.example.demo1.dto.CrawlJobQueryDTO;
import org.example.demo1.entity.CrawlJobRecord;
import org.example.demo1.mapper.CrawlJobRecordMapper;
import org.example.demo1.service.CrawlJobRecordService;
import org.example.demo1.vo.CrawlJobRecordVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CrawlJobRecordServiceImpl implements CrawlJobRecordService {

    private final CrawlJobRecordMapper crawlJobRecordMapper;

    @Override
    @Transactional
    public Long startJob(String crawlType, String paramsJson, Long startedBy) {
        CrawlJobRecord r = new CrawlJobRecord();
        r.setCrawlType(crawlType);
        r.setParamsJson(paramsJson);
        r.setStatus(CrawlJobRecord.STATUS_RUNNING);
        r.setStartedBy(startedBy);
        r.setStartedAt(LocalDateTime.now());
        crawlJobRecordMapper.insert(r);
        return r.getId();
    }

    @Override
    @Transactional
    public void finishSuccess(Long jobId, Integer savedCount) {
        CrawlJobRecord r = crawlJobRecordMapper.selectById(jobId);
        if (r == null) {
            return;
        }
        r.setStatus(CrawlJobRecord.STATUS_SUCCESS);
        r.setSavedCount(savedCount);
        r.setFinishedAt(LocalDateTime.now());
        r.setErrorMessage(null);
        crawlJobRecordMapper.updateById(r);
    }

    @Override
    @Transactional
    public void finishFailure(Long jobId, String errorMessage) {
        CrawlJobRecord r = crawlJobRecordMapper.selectById(jobId);
        if (r == null) {
            return;
        }
        r.setStatus(CrawlJobRecord.STATUS_FAILED);
        r.setFinishedAt(LocalDateTime.now());
        r.setErrorMessage(truncate(errorMessage, 1000));
        crawlJobRecordMapper.updateById(r);
    }

    @Override
    public IPage<CrawlJobRecordVO> pageForAdmin(CrawlJobQueryDTO query) {
        int pn = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int ps = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 50);

        LambdaQueryWrapper<CrawlJobRecord> w = new LambdaQueryWrapper<>();
        if (query.getCrawlType() != null && !query.getCrawlType().isBlank()) {
            w.eq(CrawlJobRecord::getCrawlType, query.getCrawlType().trim());
        }
        if (query.getStatus() != null) {
            w.eq(CrawlJobRecord::getStatus, query.getStatus());
        }
        w.orderByDesc(CrawlJobRecord::getStartedAt);

        Page<CrawlJobRecord> page = new Page<>(pn, ps);
        IPage<CrawlJobRecord> raw = crawlJobRecordMapper.selectPage(page, w);

        Page<CrawlJobRecordVO> voPage = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        List<CrawlJobRecordVO> vos = raw.getRecords().stream().map(this::toVo).collect(Collectors.toList());
        voPage.setRecords(vos);
        return voPage;
    }

    private CrawlJobRecordVO toVo(CrawlJobRecord r) {
        CrawlJobRecordVO vo = new CrawlJobRecordVO();
        vo.setId(r.getId());
        vo.setCrawlType(r.getCrawlType());
        vo.setCrawlTypeLabel(typeLabel(r.getCrawlType()));
        vo.setParamsJson(r.getParamsJson());
        vo.setStatus(r.getStatus());
        vo.setStatusLabel(statusLabel(r.getStatus()));
        vo.setSavedCount(r.getSavedCount());
        vo.setErrorMessage(r.getErrorMessage());
        vo.setStartedBy(r.getStartedBy());
        vo.setStartedAt(r.getStartedAt());
        vo.setFinishedAt(r.getFinishedAt());
        return vo;
    }

    private static String typeLabel(String type) {
        if (CrawlJobRecord.TYPE_COURTLISTENER.equals(type)) {
            return "CourtListener";
        }
        if (CrawlJobRecord.TYPE_JAPAN_COURTS.equals(type)) {
            return "日本裁判所";
        }
        return type == null ? "" : type;
    }

    private static String statusLabel(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case CrawlJobRecord.STATUS_RUNNING -> "运行中";
            case CrawlJobRecord.STATUS_SUCCESS -> "已成功结束";
            case CrawlJobRecord.STATUS_FAILED -> "已失败结束";
            default -> "未知";
        };
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
