package org.example.demo1.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 案例爬取任务记录
 */
@Data
@TableName("crawl_job_record")
public class CrawlJobRecord {

    public static final String TYPE_COURTLISTENER = "COURTLISTENER";
    public static final String TYPE_JAPAN_COURTS = "JAPAN_COURTS";

    /** 运行中 */
    public static final int STATUS_RUNNING = 0;
    /** 已成功结束 */
    public static final int STATUS_SUCCESS = 1;
    /** 已失败结束 */
    public static final int STATUS_FAILED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String crawlType;

    private String paramsJson;

    private Integer status;

    private Integer savedCount;

    private String errorMessage;

    private Long startedBy;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
