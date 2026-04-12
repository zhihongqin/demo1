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
     * 调用 FastGPT Chat 接口（单次对话）
     *
     * @param apiKey  应用的 API Key
     * @param prompt  系统提示词
     * @param content 用户输入内容
     * @return AI 返回的文本内容
     */
    public String chat(String apiKey, String prompt, String content) {
        return chat(apiKey, prompt, content, null);
    }

    /**
     * 调用 FastGPT Chat 接口（支持会话记忆）
     * 传入相同的 chatId 可让 FastGPT 关联同一会话，AI 将记住历史对话内容
     *
     * @param apiKey  应用的 API Key
     * @param prompt  系统提示词
     * @param content 用户输入内容
     * @param chatId  会话 ID，同一篇文书的分段请求应使用相同的 chatId；为 null 时退化为单次对话
     * @return AI 返回的文本内容
     */
    public String chat(String apiKey, String prompt, String content, String chatId) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("stream", false);

            if (chatId != null && !chatId.isBlank()) {
                requestBody.put("chatId", chatId);
            }

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

            log.debug("FastGPT 请求: url={}, chatId={}", url, chatId);

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

    /**
     * 携带文件链接的 Chat（适用于日本案例 PDF 翻译）
     * 按 FastGPT 官方格式，将 file_url 节点直接传入 user 消息的 content 数组。
     *
     * <p>消息结构：
     * <pre>
     * {
     *   "role": "user",
     *   "content": [
     *     { "type": "file_url", "name": "case.pdf", "url": "https://..." },
     *     { "type": "text", "text": "请将上述日文裁判文书翻译为中文" }
     *   ]
     * }
     * </pre>
     *
     * @param apiKey      应用的 API Key
     * @param prompt      系统提示词
     * @param fileUrl     PDF 文件的公开访问链接
     * @param fileName    文件名（含扩展名，如 "case.pdf"）
     * @param instruction 对文件的处理指令
     * @param chatId      会话 ID，为 null 时退化为单次对话
     * @return AI 返回的文本内容
     */
    public String chatWithFile(String apiKey, String prompt, String fileUrl,
                               String fileName, String instruction, String chatId) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("stream", false);

            if (chatId != null && !chatId.isBlank()) {
                requestBody.put("chatId", chatId);
            }

            ArrayNode messages = objectMapper.createArrayNode();

            // system 消息
            if (prompt != null && !prompt.isBlank()) {
                ObjectNode systemMsg = objectMapper.createObjectNode();
                systemMsg.put("role", "system");
                systemMsg.put("content", prompt);
                messages.add(systemMsg);
            }

            // user 消息：content 为数组（多模态格式）
            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");

            ArrayNode contentArr = objectMapper.createArrayNode();

            // 文件节点：{ "type": "file_url", "name": "xxx.pdf", "url": "https://..." }
            ObjectNode fileNode = objectMapper.createObjectNode();
            fileNode.put("type", "file_url");
            fileNode.put("name", fileName);
            fileNode.put("url", fileUrl);
            contentArr.add(fileNode);

            // 文字指令节点
            ObjectNode textNode = objectMapper.createObjectNode();
            textNode.put("type", "text");
            textNode.put("text", instruction);
            contentArr.add(textNode);

            userMsg.set("content", contentArr);
            messages.add(userMsg);
            requestBody.set("messages", messages);

            String url = baseUrl + "/v1/chat/completions";
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(objectMapper.writeValueAsString(requestBody), JSON))
                    .build();

            log.debug("FastGPT chatWithFile 请求: url={}, fileUrl={}, chatId={}", url, fileUrl, chatId);

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "unknown";
                    log.error("FastGPT chatWithFile 请求失败: status={}, body={}", response.code(), errorBody);
                    throw new BusinessException(ResultCode.AGENT_CALL_FAIL,
                            "FastGPT 请求失败，状态码: " + response.code());
                }

                String responseBody = response.body().string();
                log.debug("FastGPT chatWithFile 响应: {}", responseBody);

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
            log.error("FastGPT chatWithFile 请求IO异常", e);
            throw new BusinessException(ResultCode.AGENT_CALL_FAIL, "AI服务连接异常: " + e.getMessage());
        }
    }
}
