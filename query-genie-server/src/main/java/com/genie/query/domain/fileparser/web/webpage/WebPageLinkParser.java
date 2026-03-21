package com.genie.query.domain.fileparser.web.webpage;

import com.genie.query.domain.document.model.Document;
import com.genie.query.domain.fileparser.DocumentParser;
import com.genie.query.domain.fileparser.web.WebLinkParser;
import com.genie.query.domain.objectstore.ObjectStore;
import com.genie.query.infrastructure.util.HtmlToMdConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/15
 */
@Service
public class WebPageLinkParser implements WebLinkParser, DocumentParser {

    @Autowired
    private ObjectStore objectStore;

    private final HttpClient httpClient;

    public WebPageLinkParser() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String parse(String originUri) throws IOException, InterruptedException {
        return downloadToString(originUri);
    }

    @Override
    public void parse(Document document) throws IOException {
        InputStream inputStream = objectStore.downloadFile(document.getObjectStorePathOrigin());
        String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        inputStream.close();
        String md = HtmlToMdConverter.convertHtmlToMarkdown(content);
        objectStore.saveFile(document.getObjectStorePathParsed(), md);
    }

    /**
     * 下载网页内容到字符串
     */
    public String downloadToString(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // 检查响应状态码
        if (response.statusCode() != 200) {
            throw new IOException("HTTP请求失败，状态码: " + response.statusCode());
        }

        return response.body();
    }

    /**
     * 下载网页内容到字符串（指定编码）
     */
    public String downloadToString(String url, Charset charset) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP请求失败，状态码: " + response.statusCode());
        }

        return new String(response.body(), charset);
    }

    /**
     * 下载网页内容到文件
     */
    public void downloadToFile(String url, String filePath) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<Path> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofFile(Path.of(filePath))
        );

        if (response.statusCode() != 200) {
            Files.deleteIfExists(Path.of(filePath));
            throw new IOException("HTTP请求失败，状态码: " + response.statusCode());
        }

        System.out.println("网页已下载到: " + filePath);
    }

    /**
     * 下载并保存网页内容，支持重试机制
     */
    public String downloadWithRetry(String url, int maxRetries) throws IOException, InterruptedException {
        IOException lastException = null;

        for (int i = 0; i < maxRetries; i++) {
            try {
                return downloadToString(url);
            } catch (IOException e) {
                lastException = e;
                System.err.println("第 " + (i + 1) + " 次尝试失败: " + e.getMessage());

                if (i < maxRetries - 1) {
                    // 等待一段时间后重试
                    Thread.sleep(1000L * (i + 1)); // 指数退避
                }
            }
        }

        throw new IOException("下载失败，已重试 " + maxRetries + " 次", lastException);
    }
}
