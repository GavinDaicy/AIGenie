package com.genie.query.infrastructure.remote;

import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.util.Objects;
import com.alibaba.fastjson.JSONObject;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/12
 */
@Service
public class YuqueClient {

    @Autowired
    private OkHttpClient okHttpClient;

    @Value("${yuque.auth.token:}")
    private String authToken;

//    public static void main(String[] args) {
//        String content = new YuqueClient(new OkHttpClient()).getDocContent("https://www.yuque.com/api/v2/repos/42271794/docs/249972430");
//        System.out.println( content);
//        System.out.println(HtmlToMdConverter.convertHtmlToMarkdown( content));
//    }

    /**
     * 获取语雀文档内容
     * urlValue: https://www.yuque.com/api/v2/repos/42271794/docs/249972430
     * @return 文档内容
     */
    public String getDocContent(String urlValue) {
        // 构建请求URL
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(urlValue))
                .newBuilder()
                .build();

        requireAuthToken();

        // 构建请求
        Request request = new Request.Builder()
                .url(url)
                .header("X-Auth-Token", authToken)
                .header("Accept", "*/*")
                .header("Host", "www.yuque.com")
                .header("Connection", "keep-alive")
                .get()
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code: " + response + ", message: " +
                        (response.body() != null ? response.body().string() : ""));
            }

            ResponseBody responseBody = response.body();
            if (responseBody != null) {
                String responseStr = responseBody.string();
                JSONObject jsonObject = JSONObject.parseObject(responseStr);
                if (jsonObject.containsKey("data") && jsonObject.getJSONObject("data").containsKey("body_draft")) {
                    return jsonObject.getJSONObject("data").getString("body_draft");
                }
            }
            return null;
        } catch (IOException e) {
            throw new RuntimeException("请求语雀API失败: " + e.getMessage(), e);
        }
    }

    /**
     * 通用方法：获取语雀文档
     * @param repoId 仓库ID
     * @param docId 文档ID
     * @return 文档内容
     */
    public String getDocContent(String repoId, String docId) {
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse("https://www.yuque.com/api/v2/repos/" + repoId + "/docs/" + docId))
                .newBuilder()
                .addQueryParameter("raw", "1")
                .addQueryParameter("format", "markdown")
                .build();

        requireAuthToken();

        Request request = new Request.Builder()
                .url(url)
                .header("X-Auth-Token", authToken)
                .header("Accept", "*/*")
                .header("Host", "www.yuque.com")
                .header("Connection", "keep-alive")
                .get()
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code: " + response + ", message: " +
                        (response.body() != null ? response.body().string() : ""));
            }

            ResponseBody responseBody = response.body();
            if (responseBody != null) {
                return responseBody.string();
            }
            return null;
        } catch (IOException e) {
            throw new RuntimeException("请求语雀API失败: " + e.getMessage(), e);
        }
    }

    private void requireAuthToken() {
        if (authToken == null || authToken.isBlank()) {
            throw new IllegalStateException(
                    "未配置语雀 API Token：请设置配置项 yuque.auth.token 或环境变量 YUQUE_AUTH_TOKEN");
        }
    }
}
