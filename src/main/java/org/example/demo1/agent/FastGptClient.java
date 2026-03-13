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
 * FastGPT API 调用客户端
 * 通过 OpenAI 兼容接口调用 FastGPT 的各个应用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FastGptClient {

    @Value("${fastgpt.base-url}")
    private String baseUrl;

    @Value("${fastgpt.model}")
    private String model;

    @Value("${fastgpt.timeout:60}")
    private int timeout;

    private final ObjectMapper objectMapper;

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /**
     * 调用 FastGPT Chat 接口
     *
     * @param apiKey  应用的 API Key
     * @param prompt  系统提示词
     * @param content 用户输入内容
     * @return AI 返回的文本内容
     */
    public String chat(String apiKey, String prompt, String content) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("stream", false);

            ArrayNode messages = objectMapper.createArrayNode();

            if (prompt != null && !prompt.isBlank()) {
                ObjectNode systemMsg = objectMapper.createObjectNode();
                systemMsg.put("role", "system");
                systemMsg.put("content", prompt);
                messages.add(systemMsg);
            }

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

            log.debug("FastGPT 请求: url={}", url);

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "unknown";
                    log.error("FastGPT 请求失败: status={}, body={}", response.code(), errorBody);
                    throw new BusinessException(ResultCode.AGENT_CALL_FAIL,
                            "FastGPT 请求失败，状态码: " + response.code());
                }

                String responseBody = response.body().string();
                log.debug("FastGPT 响应: {}", responseBody);

                JsonNode jsonNode = objectMapper.readTree(responseBody);
                return jsonNode
                        .path("choices")
                        .path(0)
                        .path("message")
                        .path("content")
                        .asText();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("FastGPT 请求IO异常", e);
            throw new BusinessException(ResultCode.AGENT_CALL_FAIL, "AI服务连接异常: " + e.getMessage());
        }
    }
}
