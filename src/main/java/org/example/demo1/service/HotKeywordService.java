package org.example.demo1.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.example.demo1.dto.HotKeywordCreateDTO;
import org.example.demo1.dto.HotKeywordQueryDTO;
import org.example.demo1.dto.HotKeywordUpdateDTO;
import org.example.demo1.vo.HotKeywordVO;

import java.util.List;

public interface HotKeywordService {

    /** 小程序/公开：启用的热词关键词列表 */
    List<String> listEnabledKeywords(int limit);

    /** 管理员分页条件查询 */
    IPage<HotKeywordVO> pageForAdmin(HotKeywordQueryDTO dto);

    Long createByAdmin(HotKeywordCreateDTO dto);

    void updateByAdmin(Long id, HotKeywordUpdateDTO dto);

    void deleteByAdmin(Long id);

    void deleteBatchByAdmin(List<Long> ids);

    /** 从 search_history 聚合更新（定时任务 + 管理员手动触发） */
    void refreshFromSearchHistory();
}
