package com.genie.query.domain.etlpipeline.spliter.impl;

import com.genie.query.domain.document.model.Document;
import com.genie.query.domain.etlpipeline.model.Chunk;
import com.genie.query.domain.etlpipeline.spliter.DocumentSplitter;
import com.genie.query.domain.knowledge.model.KLField;
import org.apache.commons.lang3.StringUtils;
import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 递归切块策略实现：按段落分割后合并为合适大小的块，支持块间重叠（overlap），适用于单内容字段（如解析后的 Markdown/文本）。
 * 重叠可减少边界断句，提升召回与上下文连贯性。
 *
 * @author daicy
 * @date 2026/2/1
 */
@Component
public class RecursiveDocumentSplitter implements DocumentSplitter {

    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int MAX_CHUNK_SIZE = 1000;
    private static final int DEFAULT_CHUNK_OVERLAP = 100;
    private static final String PARAGRAPH_SEP = "\n\n";

    @Value("${app.chunk.size:#{null}}")
    private Integer configuredChunkSize;
    @Value("${app.chunk.max-size:#{null}}")
    private Integer configuredMaxChunkSize;
    @Value("${app.chunk.overlap:#{null}}")
    private Integer configuredOverlap;

    @Override
    public List<Chunk> split(Document doc, InputStream parsedInput, List<KLField> userFields) {
        if (userFields == null || userFields.isEmpty()) {
            return List.of();
        }
        String fullText = readWithAutoDetect(parsedInput);
        if (StringUtils.isBlank(fullText)) {
            return List.of();
        }
        int chunkSize = configuredChunkSize != null ? configuredChunkSize : DEFAULT_CHUNK_SIZE;
        int maxChunkSize = configuredMaxChunkSize != null ? configuredMaxChunkSize : MAX_CHUNK_SIZE;
        int overlap = configuredOverlap != null ? Math.max(0, configuredOverlap) : DEFAULT_CHUNK_OVERLAP;
        overlap = Math.min(overlap, chunkSize);
        // 单内容字段：取唯一语义字段的 fieldKey 作为存储 key
        String contentFieldKey = userFields.get(0).getFieldKey();
        List<String> segments = splitByParagraphThenSize(fullText, chunkSize, maxChunkSize, overlap);
        List<Chunk> chunks = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            Chunk c = new Chunk();
            c.setOrder(i);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put(contentFieldKey, segments.get(i));
            enrichMetadata(doc, data);
            c.setData(data);
            chunks.add(c);
        }
        return chunks;
    }

    /**
     * 使用 juniversalchardet 自动探测编码，优先 UTF-8，失败时回退到 GBK（兼容 GB2312）。
     */
    private static String readWithAutoDetect(InputStream in) {
        try {
            byte[] bytes = in.readAllBytes();

            // 先用探测器尝试识别编码
            UniversalDetector detector = new UniversalDetector(null);
            detector.handleData(bytes, 0, bytes.length);
            detector.dataEnd();
            String detected = detector.getDetectedCharset();

            Charset charset;
            if (detected != null) {
                try {
                    charset = Charset.forName(detected);
                } catch (Exception ignore) {
                    // 探测到但不被支持时，回退到 UTF-8
                    charset = StandardCharsets.UTF_8;
                }
            } else {
                // 没探测出来时优先按 UTF-8 试一下
                String utf8 = new String(bytes, StandardCharsets.UTF_8);
                // 如果包含替换字符 �，说明可能不是 UTF-8，改用 GBK（向下兼容 GB2312）
                if (utf8.indexOf('\uFFFD') >= 0) {
                    charset = Charset.forName("GBK");
                } else {
                    return utf8;
                }
            }
            return new String(bytes, charset);
        } catch (IOException e) {
            throw new RuntimeException("读取解析内容失败", e);
        }
    }

    /**
     * 先按段落切，再按大小合并为 [chunkSize, maxChunkSize] 的块；块间保留 overlap 字符重叠，减少边界断句。
     */
    private static List<String> splitByParagraphThenSize(String text, int chunkSize, int maxChunkSize, int overlap) {
        String[] paragraphs = text.split(PARAGRAPH_SEP);
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String p : paragraphs) {
            String trimmed = p.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (current.length() + trimmed.length() + 2 <= maxChunkSize) {
                if (current.length() > 0) {
                    current.append(PARAGRAPH_SEP);
                }
                current.append(trimmed);
                if (current.length() >= chunkSize) {
                    flushChunkWithOverlap(result, current, overlap);
                }
            } else {
                if (current.length() > 0) {
                    flushChunkWithOverlap(result, current, overlap);
                }
                if (trimmed.length() <= maxChunkSize) {
                    current.append(trimmed);
                } else {
                    List<String> subChunks = splitByLength(trimmed, maxChunkSize, overlap);
                    result.addAll(subChunks);
                    current.setLength(0);
                }
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    /**
     * 为 Chunk 补充通用的文档级元信息。
     */
    private static void enrichMetadata(Document doc, Map<String, Object> data) {
        if (doc == null) {
            return;
        }
        if (doc.getName() != null && !doc.getName().isBlank()) {
            data.put("doc_title", doc.getName());
        }
        if (doc.getType() != null) {
            data.put("doc_type", doc.getType().getValue());
        }
        if (doc.getOriginUri() != null && !doc.getOriginUri().isBlank()) {
            data.put("source_url", doc.getOriginUri());
        }
        data.put("chunk_level", "PARAGRAPH");
    }

    /**
     * 将当前块写出到 result，并将末尾 overlap 字符保留到 current 作为下一块前缀。
     */
    private static void flushChunkWithOverlap(List<String> result, StringBuilder current, int overlap) {
        String flushed = current.toString();
        result.add(flushed);
        current.setLength(0);
        if (overlap > 0 && flushed.length() > overlap) {
            current.append(flushed.substring(flushed.length() - overlap));
        }
    }

    /**
     * 按长度切分长段落，块间保留 overlap 字符重叠。
     */
    private static List<String> splitByLength(String s, int maxLen, int overlap) {
        List<String> list = new ArrayList<>();
        int step = Math.max(1, maxLen - overlap);
        for (int i = 0; i < s.length(); i += step) {
            int end = Math.min(i + maxLen, s.length());
            list.add(s.substring(i, end));
            if (end >= s.length()) {
                break;
            }
        }
        return list;
    }
}
