package org.example.demo1.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.ResultCode;
import org.example.demo1.dto.FeedbackProcessDTO;
import org.example.demo1.dto.FeedbackQueryDTO;
import org.example.demo1.dto.FeedbackSubmitDTO;
import org.example.demo1.entity.SystemFeedback;
import org.example.demo1.mapper.SystemFeedbackMapper;
import org.example.demo1.service.FeedbackService;
import org.example.demo1.vo.FeedbackAdminVO;
import org.example.demo1.vo.FeedbackDashboardVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private final SystemFeedbackMapper systemFeedbackMapper;

    @Override
    @Transactional
    public Long submit(Long userId, FeedbackSubmitDTO dto) {
        SystemFeedback f = new SystemFeedback();
        f.setUserId(userId);
        f.setContent(dto.getContent().trim());
        f.setContact(trimToNull(dto.getContact()));
        f.setClientInfo(trimToNull(dto.getClientInfo()));
        f.setStatus(0);
        systemFeedbackMapper.insert(f);
        log.info("用户提交反馈: userId={}, feedbackId={}", userId, f.getId());
        return f.getId();
    }

    @Override
    public IPage<FeedbackAdminVO> pageForAdmin(FeedbackQueryDTO query) {
        int pn = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int ps = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 50);
        Page<FeedbackAdminVO> page = new Page<>(pn, ps);
        String kw = query.getKeyword() == null ? null : query.getKeyword().trim();
        if (kw != null && kw.isEmpty()) {
            kw = null;
        }
        return systemFeedbackMapper.selectAdminPage(page, query.getStatus(), kw);
    }

    @Override
    @Transactional
    public void process(Long id, FeedbackProcessDTO dto, Long adminUserId) {
        SystemFeedback f = systemFeedbackMapper.selectById(id);
        if (f == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "反馈不存在");
        }
        int st = dto.getStatus();
        if (st != 0 && st != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态无效");
        }
        f.setStatus(st);
        f.setAdminReply(trimToNull(dto.getAdminReply()));
        if (st == 1) {
            f.setProcessedAt(LocalDateTime.now());
            f.setProcessedBy(adminUserId);
        } else {
            f.setProcessedAt(null);
            f.setProcessedBy(null);
        }
        systemFeedbackMapper.updateById(f);
        log.info("管理员处理反馈: adminId={}, feedbackId={}, status={}", adminUserId, id, st);
    }

    @Override
    public FeedbackDashboardVO dashboardStats() {
        long pending = systemFeedbackMapper.selectCount(
                new LambdaQueryWrapper<SystemFeedback>().eq(SystemFeedback::getStatus, 0));
        long total = systemFeedbackMapper.selectCount(new LambdaQueryWrapper<>());
        return new FeedbackDashboardVO(pending, total);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
