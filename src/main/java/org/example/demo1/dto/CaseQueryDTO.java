package org.example.demo1.dto;

import lombok.Data;

@Data
public class CaseQueryDTO {

    /** 搜索关键词 */
    private String keyword;

    /** 案件类型：1-民事，2-刑事，3-行政，4-商事 */
    private Integer caseType;

    /** 所属国家/地区 */
    private String country;

    /** 排序字段：importance_score/view_count/judgment_date/created_at */
    private String orderBy = "created_at";

    /** 排序方向：asc/desc */
    private String orderDir = "desc";

    /** 页码 */
    private Integer pageNum = 1;

    /** 每页大小 */
    private Integer pageSize = 10;
}
