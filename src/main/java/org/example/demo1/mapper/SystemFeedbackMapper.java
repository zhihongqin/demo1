package org.example.demo1.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.demo1.entity.SystemFeedback;
import org.example.demo1.vo.FeedbackAdminVO;

@Mapper
public interface SystemFeedbackMapper extends BaseMapper<SystemFeedback> {

    IPage<FeedbackAdminVO> selectAdminPage(Page<FeedbackAdminVO> page,
                                           @Param("status") Integer status,
                                           @Param("keyword") String keyword);
}
