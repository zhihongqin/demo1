package org.example.demo1.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 腾讯云 COS 配置（问答附件上传）
 */
@Data
@ConfigurationProperties(prefix = "tencent.cos")
public class TencentCosProperties {

    private String secretId = "";
    private String secretKey = "";
    private String region = "ap-guangzhou";
    private String bucket = "";
    /** 对象键前缀，如 chat-files/ */
    private String pathPrefix = "chat-files/";
    /** 公网访问根 URL，不含末尾斜杠，如 https://xxx.cos.ap-guangzhou.myqcloud.com */
    private String publicBaseUrl = "";
}
