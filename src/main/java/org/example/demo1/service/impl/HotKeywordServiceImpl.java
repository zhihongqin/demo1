package org.example.demo1.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.ResultCode;
import org.example.demo1.dto.HotKeywordCreateDTO;
import org.example.demo1.dto.HotKeywordQueryDTO;
import org.example.demo1.dto.HotKeywordUpdateDTO;
import org.example.demo1.entity.HotKeyword;
import org.example.demo1.mapper.HotKeywordMapper;
import org.example.demo1.mapper.SearchHistoryMapper;
import org.example.demo1.service.HotKeywordService;
import org.example.demo1.vo.HotKeywordVO;
import org.example.demo1.vo.KeywordCountVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotKeywordServiceImpl extends ServiceImpl<HotKeywordMapper, HotKeyword> implements HotKeywordService {

    private static final int STAT_DAYS = 30;
    private static final int STAT_TOP_LIMIT = 50;
    private static final int MIN_COUNT_TO_SYNC = 2;

    private final SearchHistoryMapper searchHistoryMapper;

    @Override
    public List<String> listEnabledKeywords(int limit) {
        List<HotKeyword> list = list(
                new LambdaQueryWrapper<HotKeyword>()
                        .eq(HotKeyword::getIsEnabled, 1)
                        .orderByDesc(HotKeyword::getIsPinned)
                        .orderByDesc(HotKeyword::getSortOrder)
                        .orderByDesc(HotKeyword::getSearchCount)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 50))));
        return list.stream().map(HotKeyword::getKeyword).collect(Collectors.toList());
    }

    @Override
    public IPage<HotKeywordVO> pageForAdmin(HotKeywordQueryDTO dto) {
        int pageNum = dto.getPageNum() == null || dto.getPageNum() < 1 ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null ? 10 : Math.min(Math.max(dto.getPageSize(), 1), 100);

        LambdaQueryWrapper<HotKeyword> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(dto.getKeyword())) {
            w.like(HotKeyword::getKeyword, dto.getKeyword().trim());
        }
        if (dto.getIsEnabled() != null) {
            w.eq(HotKeyword::getIsEnabled, dto.getIsEnabled() == 1 ? 1 : 0);
        }
        if (dto.getIsPinned() != null) {
            w.eq(HotKeyword::getIsPinned, dto.getIsPinned() == 1 ? 1 : 0);
        }
        if (dto.getOrigin() != null) {
            w.eq(HotKeyword::getOrigin, dto.getOrigin() == 1 ? 1 : 0);
        }
        w.orderByDesc(HotKeyword::getSortOrder)
                .orderByDesc(HotKeyword::getSearchCount)
                .orderByAsc(HotKeyword::getId);

        Page<HotKeyword> page = new Page<>(pageNum, pageSize);
        IPage<HotKeyword> entityPage = page(page, w);
        return entityPage.convert(this::toVo);
    }

    @Override
    @Transactional
    public Long createByAdmin(HotKeywordCreateDTO dto) {
        String kw = dto.getKeyword().trim();
        if (kw.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "关键词不能为空");
        }
        long exists = count(new LambdaQueryWrapper<HotKeyword>().eq(HotKeyword::getKeyword, kw));
        if (exists > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该关键词已存在");
        }
        HotKeyword e = new HotKeyword();
        e.setKeyword(kw);
        e.setSearchCount(0);
        e.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        e.setIsPinned(dto.getIsPinned() != null && dto.getIsPinned() == 1 ? 1 : 0);
        e.setIsEnabled(dto.getIsEnabled() == null || dto.getIsEnabled() == 1 ? 1 : 0);
        e.setOrigin(1);
        save(e);
        return e.getId();
    }

    @Override
    @Transactional
    public void updateByAdmin(Long id, HotKeywordUpdateDTO dto) {
        HotKeyword e = getById(id);
        if (e == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "记录不存在");
        }
        if (dto.getSortOrder() != null) {
            e.setSortOrder(dto.getSortOrder());
        }
        if (dto.getIsPinned() != null) {
            e.setIsPinned(dto.getIsPinned() == 1 ? 1 : 0);
        }
        if (dto.getIsEnabled() != null) {
            e.setIsEnabled(dto.getIsEnabled() == 1 ? 1 : 0);
        }
        updateById(e);
    }

    @Override
    @Transactional
    public void deleteByAdmin(Long id) {
        if (!removeById(id)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "记录不存在");
        }
    }

    @Override
    @Transactional
    public void deleteBatchByAdmin(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择要删除的记录");
        }
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择要删除的记录");
        }
        removeByIds(distinct);
    }

    @Override
    @Transactional
    public void refreshFromSearchHistory() {
        LocalDateTime since = LocalDateTime.now().minusDays(STAT_DAYS);
        List<KeywordCountVO> stats = searchHistoryMapper.aggregateKeywordCounts(since, STAT_TOP_LIMIT);

        Set<String> inStats = new HashSet<>();
        for (KeywordCountVO row : stats) {
            if (row.getCnt() == null || row.getCnt() < MIN_COUNT_TO_SYNC) {
                continue;
            }
            String kw = row.getKeyword() == null ? "" : row.getKeyword().trim();
            if (kw.isEmpty()) {
                continue;
            }
            inStats.add(kw);

            HotKeyword existing = getOne(new LambdaQueryWrapper<HotKeyword>().eq(HotKeyword::getKeyword, kw));
            if (existing == null) {
                HotKeyword n = new HotKeyword();
                n.setKeyword(kw);
                n.setSearchCount(row.getCnt());
                n.setSortOrder(0);
                n.setIsPinned(0);
                n.setIsEnabled(1);
                n.setOrigin(0);
                save(n);
            } else {
                existing.setSearchCount(row.getCnt());
                if (existing.getIsPinned() == null || existing.getIsPinned() == 0) {
                    existing.setIsEnabled(1);
                }
                updateById(existing);
            }
        }

        List<HotKeyword> autoRows = list(new LambdaQueryWrapper<HotKeyword>().eq(HotKeyword::getOrigin, 0));
        for (HotKeyword hk : autoRows) {
            if (hk.getIsPinned() != null && hk.getIsPinned() == 1) {
                continue;
            }
            if (!inStats.contains(hk.getKeyword())) {
                hk.setIsEnabled(0);
                hk.setSearchCount(0);
                updateById(hk);
            }
        }

        log.info("热门词同步完成: 统计窗口={}天, 纳入词数={}", STAT_DAYS, inStats.size());
    }

    private HotKeywordVO toVo(HotKeyword e) {
        HotKeywordVO vo = new HotKeywordVO();
        BeanUtils.copyProperties(e, vo);
        return vo;
    }
}
