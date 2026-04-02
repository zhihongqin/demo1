package org.example.demo1.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.ResultCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * FastGPT 智能问答专用客户端
 *
 * 与 FastGptClient 的区别：
 *  - 不传 model 字段：FastGPT 应用接口通过 API Key 识别应用，无需指定底层模型
 *  - 不传 system 消息：FastGPT 应用的提示词在控制台内配置，外部传入会导致 514 unAuthApiKey
 *  - 支持 chatId 多轮对话
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FastGptQaClient {

    @Value("${fastgpt.qa-base-url}")
    private String baseUrl;

    @Value("${fastgpt.timeout:60}")
    private int timeout;

    private final ObjectMapper objectMapper;

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /**
     * 调用 FastGPT 应用接口（单轮）
     *
     * @param apiKey  FastGPT 应用 API Key（以 fastgpt- 开头）
     * @param content 用户输入内容
     * @return AI 回答文本
     */
    public String chat(String apiKey, String content) {
        return chat(apiKey, content, null);
    }

    /**
     * 调用 FastGPT 应用接口（多轮，传入相同 chatId 保持上下文）
     *
     * @param apiKey  FastGPT 应用 API Key（以 fastgpt- 开头）
     * @param content 用户输入内容
     * @param chatId  会话 ID；为 null 时退化为单轮对话
     * @return AI 回答文本
     */
    public String chat(String apiKey, String content, String chatId) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("stream", false);

            if (chatId != null && !chatId.isBlank()) {
                requestBody.put("chatId", chatId);
            }

            ArrayNode messages = objectMapper.createArrayNode();
            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", content);
            messages.add(userMsg);
            requestBody.set("messages", messages);

            String url = baseUrl + "/v1/chat/completions";
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(objectMapper.writeValueAsString(requestBody), JSON))
                    .build();

            log.debug("FastGPT QA 请求: url={}, chatId={}", url, chatId);

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "unknown";
                    log.error("FastGPT QA 请求失败: status={}, body={}", response.code(), errorBody);
                    throw new BusinessException(ResultCode.CHAT_FAIL,
                            "智能问答请求失败，状态码: " + response.code());
                }

                String responseBody = response.body().string();
                log.debug("FastGPT QA 响应: {}", responseBody);

                JsonNode jsonNode = objectMapper.readTree(responseBody);
                String answer = jsonNode
                        .path("choices")
                        .path(0)
                        .path("message")
                        .path("content")
                        .asText();

                if (answer == null || answer.isBlank()) {
                    log.warn("FastGPT QA 返回空内容, chatId={}, body={}", chatId, responseBody);
                    throw new BusinessException(ResultCode.CHAT_FAIL, "AI 返回内容为空，请重试");
                }

                return answer;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("FastGPT QA 请求IO异常", e);
            throw new BusinessException(ResultCode.CHAT_FAIL, "智能问答服务连接异常: " + e.getMessage());
        }
    }
}
