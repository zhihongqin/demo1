package org.example.demo1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatAskDTO {

    /** 用户提问内容 */
    @NotBlank(message = "问题不能为空")
    @Size(max = 1000, message = "问题长度不能超过1000字")
    private String question;

    /**
     * 会话ID（小程序端生成，格式：chat_时间戳，例如 "chat_1711234567890"）
     * 同一 chatId 的请求在 FastGPT 侧关联为同一会话，实现多轮记忆
     * 为空时由后端自动生成，退化为新建会话
     */
    @Size(max = 64, message = "会话ID过长")
    @Pattern(regexp = "^[a-zA-Z0-9_\\-]*$", message = "会话ID格式不合法")
    private String chatId;

    /** 附件 COS 公网 URL（须为本系统配置的存储桶域名下地址） */
    @Size(max = 768, message = "附件地址过长")
    private String fileUrl;

    /** 附件原始文件名 */
    @Size(max = 255, message = "附件文件名过长")
    private String fileName;
}
