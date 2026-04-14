package org.example.demo1.vo;

import lombok.Data;

@Data
public class FileUploadVO {

    /** COS 公网可访问地址 */
    private String url;

    /** 原始文件名（展示用） */
    private String fileName;
}
