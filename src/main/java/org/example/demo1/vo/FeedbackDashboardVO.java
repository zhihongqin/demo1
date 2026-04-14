package org.example.demo1.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackDashboardVO {

    /** 未处理（status=0）条数 */
    private long pendingCount;

    /** 反馈总条数 */
    private long totalCount;
}
