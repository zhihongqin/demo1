package org.example.demo1.common.result;

import lombok.Getter;

@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    PARAM_ERROR(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权，请先登录"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // 用户相关
    USER_NOT_EXIST(1001, "用户不存在"),
    USER_ALREADY_EXIST(1002, "用户已存在"),
    TOKEN_INVALID(1003, "Token无效或已过期"),
    WECHAT_LOGIN_FAIL(1004, "微信登录失败"),
    USERNAME_OR_PASSWORD_ERROR(1005, "账号或密码错误"),
    OLD_PASSWORD_ERROR(1006, "旧密码错误"),

    // 案例相关
    CASE_NOT_EXIST(2001, "案例不存在"),
    CASE_ALREADY_EXIST(2002, "案例已存在"),
    CASE_AI_NOT_READY(2003, "案例尚未完成AI处理，无法同步至知识库"),

    // AI Agent相关
    AGENT_CALL_FAIL(3001, "AI服务调用失败"),
    TRANSLATION_FAIL(3002, "翻译失败"),
    SUMMARY_FAIL(3003, "摘要提取失败"),
    SCORE_FAIL(3004, "重要性评分失败"),
    ENRICH_FAIL(3005, "案例字段提取失败"),
    FASTGPT_SYNC_BUSY(3006, "知识库同步进行中，请稍后再试"),

    // 智能问答相关
    CHAT_FAIL(4001, "智能问答服务调用失败"),
    CHAT_SESSION_NOT_EXIST(4002, "会话不存在或无权访问");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
