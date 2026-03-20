package org.example.demo1.dto;

import lombok.Data;

@Data
public class HotKeywordQueryDTO {

    /** 关键词模糊匹配 */
    private String keyword;

    /** 是否启用：1 启用 0 禁用，不传则不限 */
    private Integer isEnabled;

    /** 是否置顶：1 是 0 否，不传则不限 */
    private Integer isPinned;

    /** 来源：0 统计同步 1 手动，不传则不限 */
    private Integer origin;

    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
