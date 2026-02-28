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

    // 案例相关
    CASE_NOT_EXIST(2001, "案例不存在"),
    CASE_ALREADY_EXIST(2002, "案例已存在"),

    // AI Agent相关
    AGENT_CALL_FAIL(3001, "AI服务调用失败"),
    TRANSLATION_FAIL(3002, "翻译失败"),
    SUMMARY_FAIL(3003, "摘要提取失败"),
    SCORE_FAIL(3004, "重要性评分失败");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
