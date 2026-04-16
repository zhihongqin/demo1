package org.example.demo1.dto;

import lombok.Data;

@Data
public class CrawlJobQueryDTO {

    /** COURTLISTENER / JAPAN_COURTS，不传则全部 */
    private String crawlType;

    /** 0-运行中 1-成功 2-失败，不传则全部 */
    private Integer status;

    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
