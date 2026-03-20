package org.example.demo1.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.ResultCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * FastGPT 知识库 OpenAPI：以「本地文件」形式上传文本并触发向量化。
 * <p>
 * 说明：{@code /collection/create/text} 在部分部署（如 SaaS）下会把正文落到对象存储但缺少文件扩展名，
 * 训练阶段按扩展名选择解析器时会得到空字符串，报错：
 * {@code Only support .txt, ... "" is not supported.}
 * 使用 {@code /collection/create/localFile} + 明确的 {@code legal-case-{id}.txt} 文件名可避免该问题。
 *
 * @see <a href="https://doc.fastgpt.io/docs/introduction/development/openapi/dataset">知识库接口</a>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FastGptDatasetClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final MediaType TEXT_PLAIN_UTF8 = MediaType.parse("text/plain; charset=utf-8");

    private final ObjectMapper objectMapper;

    @Value("${fastgpt.base-url}")
    private String baseUrl;

    @Value("${fastgpt.knowledge-timeout:180}")
    private int knowledgeTimeoutSeconds;

    /**
     * 将 UTF-8 文本作为 .txt 文件上传至知识库（chunk + auto 分块）。
     *
     * @param caseId 用于生成稳定 ASCII 文件名，避免中文文件名编码问题
     * @return FastGPT 返回的 collectionId
     */
    public String uploadCaseTextAsLocalFile(String apiKey, String datasetId, long caseId, String text) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(knowledgeTimeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build();

        String url = baseUrl + "/core/dataset/collection/create/localFile";
        try {
            ObjectNode dataJson = objectMapper.createObjectNode();
            dataJson.put("datasetId", datasetId);
            dataJson.putNull("parentId");
            dataJson.put("trainingType", "chunk");
            dataJson.put("chunkSettingMode", "auto");
            dataJson.put("chunkSplitter", "");
            dataJson.put("qaPrompt", "");
            dataJson.set("metadata", objectMapper.createObjectNode());

            String dataStr = objectMapper.writeValueAsString(dataJson);
            byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
            RequestBody fileBody = RequestBody.create(textBytes, TEXT_PLAIN_UTF8);
            String filename = "legal-case-" + caseId + ".txt";

            RequestBody multipartBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("data", dataStr)
                    .addFormDataPart("file", filename, fileBody)
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(multipartBody)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String respBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    log.error("FastGPT 知识库推送 HTTP 失败: status={}, body={}", response.code(), respBody);
                    throw new BusinessException(ResultCode.AGENT_CALL_FAIL,
                            "知识库接口 HTTP " + response.code() + ": " + abbreviate(respBody, 500));
                }
                JsonNode root = objectMapper.readTree(respBody);
                int code = root.path("code").asInt(-1);
                if (code != 200) {
                    String msg = root.path("message").asText(root.path("statusText").asText("未知错误"));
                    log.error("FastGPT 知识库推送业务失败: code={}, message={}, body={}", code, msg, abbreviate(respBody, 800));
                    throw new BusinessException(ResultCode.AGENT_CALL_FAIL, msg);
                }
                String collectionId = root.path("data").path("collectionId").asText(null);
                if (collectionId == null || collectionId.isBlank()) {
                    throw new BusinessException(ResultCode.AGENT_CALL_FAIL, "知识库返回缺少 collectionId");
                }
                return collectionId;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("FastGPT 知识库请求 IO 异常", e);
            throw new BusinessException(ResultCode.AGENT_CALL_FAIL, "知识库连接异常: " + e.getMessage());
        }
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace("\n", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }
}
