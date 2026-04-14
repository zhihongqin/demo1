package org.example.demo1.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.example.demo1.dto.FeedbackProcessDTO;
import org.example.demo1.dto.FeedbackQueryDTO;
import org.example.demo1.dto.FeedbackSubmitDTO;
import org.example.demo1.vo.FeedbackAdminVO;
import org.example.demo1.vo.FeedbackDashboardVO;

public interface FeedbackService {

    Long submit(Long userId, FeedbackSubmitDTO dto);

    IPage<FeedbackAdminVO> pageForAdmin(FeedbackQueryDTO query);

    void process(Long id, FeedbackProcessDTO dto, Long adminUserId);

    /** 仪表盘：未处理数量与总数 */
    FeedbackDashboardVO dashboardStats();
}
