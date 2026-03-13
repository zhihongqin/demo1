package org.example.demo1.crawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * CourtListener REST API v4 客户端
 * 文档：https://www.courtlistener.com/api/rest/v4/
 */
@Slf4j
@Component
public class CourtListenerClient {

    @Value("${courtlistener.base-url}")
    private String baseUrl;

    @Value("${courtlistener.api-token}")
    private String apiToken;

    private final ObjectMapper objectMapper;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();

    public CourtListenerClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 搜索 opinions（法律意见书/判决书）
     *
     * @param query   搜索关键词
     * @param nextUrl 翻页 URL（第一页传 null）
     * @return API 返回的 JSON，包含 results 列表和 next 翻页链接
     */
    public JsonNode searchOpinions(String query, String nextUrl) {
        String url = nextUrl != null ? nextUrl
                : baseUrl + "/search/?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                  + "&type=o&format=json";
        return doGet(url);
    }

    /**
     * 通过 clusterId 获取 opinion 全文
     * 仅返回 plain_text 不为空的案例正文，否则返回空字符串（调用方应将其丢弃）
     *
     * @param clusterId 案例 cluster ID
     * @return plain_text 内容，若为空则返回 ""
     */
    public String fetchOpinionText(String clusterId) {
        String url = baseUrl + "/opinions/?cluster=" + clusterId + "&format=json";
        JsonNode resp = doGet(url);
        if (resp == null) return "";

        for (JsonNode op : resp.path("results")) {
            String plainText = op.path("plain_text").asText("").trim();
            if (!plainText.isBlank()) {
                return plainText;
            }
        }
        return "";
    }

    private JsonNode doGet(String url) {
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Token " + apiToken)
                .addHeader("Accept", "application/json")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("CourtListener API 请求失败: url={}, status={}", url, response.code());
                return null;
            }
            String body = response.body() != null ? response.body().string() : "";
            return objectMapper.readTree(body);
        } catch (IOException e) {
            log.error("CourtListener API IO异常: url={}, error={}", url, e.getMessage());
            return null;
        }
    }
}
