package org.example.demo1.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.demo1.entity.BrowseHistory;
import org.example.demo1.vo.BrowseHistoryVO;

@Mapper
public interface BrowseHistoryMapper extends BaseMapper<BrowseHistory> {

    /**
     * 分页查询用户浏览记录，关联案例基本信息
     */
    IPage<BrowseHistoryVO> selectBrowseHistoryPage(Page<BrowseHistoryVO> page,
                                                    @Param("userId") Long userId);
}
