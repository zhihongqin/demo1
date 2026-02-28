package org.example.demo1.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.ResultCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class WechatUtil {

    @Value("${wechat.miniapp.app-id}")
    private String appId;

    @Value("${wechat.miniapp.app-secret}")
    private String appSecret;

    private final ObjectMapper objectMapper;
    private final OkHttpClient okHttpClient = new OkHttpClient();

    private static final String CODE2SESSION_URL =
            "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code";

    /**
     * 微信 code 换取 openid 和 session_key
     *
     * @param code 小程序登录时获取的 code
     * @return WxSession 包含 openid 和 session_key
     */
    public WxSession code2Session(String code) {
        String url = String.format(CODE2SESSION_URL, appId, appSecret, code);
        log.debug("调用微信code2session接口: url={}", url);

        Request request = new Request.Builder().url(url).get().build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new BusinessException(ResultCode.WECHAT_LOGIN_FAIL,
                        "微信接口请求失败，状态码: " + response.code());
            }

            String body = response.body().string();
            log.debug("微信code2session响应: {}", body);

            JsonNode jsonNode = objectMapper.readTree(body);

            if (jsonNode.has("errcode") && jsonNode.get("errcode").asInt() != 0) {
                String errMsg = jsonNode.path("errmsg").asText();
                log.error("微信登录失败: errcode={}, errmsg={}", jsonNode.get("errcode").asInt(), errMsg);
                throw new BusinessException(ResultCode.WECHAT_LOGIN_FAIL, "微信登录失败: " + errMsg);
            }

            WxSession session = new WxSession();
            session.setOpenid(jsonNode.path("openid").asText());
            session.setSessionKey(jsonNode.path("session_key").asText());
            session.setUnionid(jsonNode.path("unionid").asText(null));

            return session;
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("调用微信接口异常", e);
            throw new BusinessException(ResultCode.WECHAT_LOGIN_FAIL, "网络异常，微信登录失败");
        }
    }

    public static class WxSession {
        private String openid;
        private String sessionKey;
        private String unionid;

        public String getOpenid() { return openid; }
        public void setOpenid(String openid) { this.openid = openid; }
        public String getSessionKey() { return sessionKey; }
        public void setSessionKey(String sessionKey) { this.sessionKey = sessionKey; }
        public String getUnionid() { return unionid; }
        public void setUnionid(String unionid) { this.unionid = unionid; }
    }
}
