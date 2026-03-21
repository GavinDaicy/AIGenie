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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按 Markdown 标题/章节进行感知切块的实现。
 * <p>
 * 解析解析后的 Markdown 文本中的 H1~H6 标题，按章节聚合内容，再在章节内按长度与重叠做二次切分，
 * 并在 Chunk 中补充章节相关元信息（section_title/section_path/heading_level 等）。
 *
 * @author daicy
 * @date 2026/3/9
 */
@Component
public class MarkdownHeadingDocumentSplitter implements DocumentSplitter {

    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int MAX_CHUNK_SIZE = 1000;
    private static final int DEFAULT_CHUNK_OVERLAP = 100;

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");

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

        String contentFieldKey = resolveContentFieldKey(userFields);
        List<Section> sections = parseSections(fullText, doc.getName());

        List<Chunk> chunks = new ArrayList<>();
        int order = 0;
        for (Section section : sections) {
            if (StringUtils.isBlank(section.text)) {
                continue;
            }
            List<String> pieces = splitByLengthWithOverlap(section.text, maxChunkSize, overlap);
            for (String piece : pieces) {
                if (StringUtils.isBlank(piece)) {
                    continue;
                }
                Chunk c = new Chunk();
                c.setOrder(order++);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put(contentFieldKey, piece);
                enrichMetadata(doc, data, section);
                c.setData(data);
                chunks.add(c);
            }
        }
        return chunks;
    }

    /**
     * 使用 juniversalchardet 自动探测编码，优先 UTF-8，失败时回退到 GBK（兼容 GB2312）。
     */
    private static String readWithAutoDetect(InputStream in) {
        try {
            byte[] bytes = in.readAllBytes();

            UniversalDetector detector = new UniversalDetector(null);
            detector.handleData(bytes, 0, bytes.length);
            detector.dataEnd();
            String detected = detector.getDetectedCharset();

            Charset charset;
            if (detected != null) {
                try {
                    charset = Charset.forName(detected);
                } catch (Exception ignore) {
                    charset = StandardCharsets.UTF_8;
                }
            } else {
                String utf8 = new String(bytes, StandardCharsets.UTF_8);
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
     * 解析 Markdown 文本中的标题层级，生成章节列表。
     * 若文档开头存在无标题内容，则归入一个“文档级”章节。
     */
    private static List<Section> parseSections(String text, String docName) {
        List<Section> sections = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");
        String[] titlesByLevel = new String[7];

        Section current = null;
        // 先为整篇文档准备一个默认章节，承接可能位于标题前的内容
        if (StringUtils.isNotBlank(docName)) {
            current = new Section();
            current.title = docName;
            current.level = 1;
            current.path = docName;
            current.content = new StringBuilder();
            sections.add(current);
            titlesByLevel[1] = docName;
        }

        for (String line : lines) {
            Matcher m = HEADING_PATTERN.matcher(line);
            if (m.matches()) {
                String hashes = m.group(1);
                String title = m.group(2).trim();
                int level = hashes.length();
                if (level < 1) {
                    level = 1;
                } else if (level > 6) {
                    level = 6;
                }
                titlesByLevel[level] = title;
                for (int i = level + 1; i < titlesByLevel.length; i++) {
                    titlesByLevel[i] = null;
                }
                StringBuilder pathBuilder = new StringBuilder();
                for (int i = 1; i <= level; i++) {
                    if (StringUtils.isNotBlank(titlesByLevel[i])) {
                        if (!pathBuilder.isEmpty()) {
                            pathBuilder.append(" > ");
                        }
                        pathBuilder.append(titlesByLevel[i]);
                    }
                }
                Section sec = new Section();
                sec.title = title;
                sec.level = level;
                sec.path = pathBuilder.toString();
                sec.content = new StringBuilder();
                sections.add(sec);
                current = sec;
            } else {
                if (current == null) {
                    current = new Section();
                    current.title = StringUtils.defaultIfBlank(docName, "文档");
                    current.level = 1;
                    current.path = current.title;
                    current.content = new StringBuilder();
                    sections.add(current);
                }
                if (!current.content.isEmpty()) {
                    current.content.append('\n');
                }
                current.content.append(line);
            }
        }

        // 将 StringBuilder 转为字符串
        for (Section sec : sections) {
            if (sec.content == null) {
                sec.content = new StringBuilder();
            }
            sec.text = sec.content.toString().trim();
        }
        // 过滤掉完全空的章节
        return sections.stream()
                .filter(s -> StringUtils.isNotBlank(s.text))
                .peek(s -> s.content = null)
                .toList();
    }

    /**
     * 简单的按长度 + 重叠切分，实现滑动窗口效果。
     */
    private static List<String> splitByLengthWithOverlap(String text, int maxChunkSize, int overlap) {
        if (StringUtils.isBlank(text)) {
            return List.of();
        }
        if (text.length() <= maxChunkSize) {
            return List.of(text.trim());
        }
        List<String> list = new ArrayList<>();
        int step = Math.max(1, maxChunkSize - overlap);
        for (int i = 0; i < text.length(); i += step) {
            int end = Math.min(i + maxChunkSize, text.length());
            String sub = text.substring(i, end).trim();
            if (!sub.isEmpty()) {
                list.add(sub);
            }
            if (end >= text.length()) {
                break;
            }
        }
        return list;
    }

    /**
     * 尝试优先选取语义字段作为主内容字段，找不到则退回第一个字段。
     */
    private static String resolveContentFieldKey(List<KLField> userFields) {
        for (KLField field : userFields) {
            if (Boolean.TRUE.equals(field.getSemanticSearchable())) {
                return field.getFieldKey();
            }
        }
        return userFields.get(0).getFieldKey();
    }

    /**
     * 为 Chunk 补充文档与章节元信息。
     */
    private static void enrichMetadata(Document doc, Map<String, Object> data, Section section) {
        if (doc != null) {
            if (StringUtils.isNotBlank(doc.getName())) {
                data.put("doc_title", doc.getName());
            }
            if (doc.getType() != null) {
                data.put("doc_type", doc.getType().getValue());
            }
            if (StringUtils.isNotBlank(doc.getOriginUri())) {
                data.put("source_url", doc.getOriginUri());
            }
        }
        if (section != null) {
            if (StringUtils.isNotBlank(section.title)) {
                data.put("section_title", section.title);
            }
            if (StringUtils.isNotBlank(section.path)) {
                data.put("section_path", section.path);
            }
            data.put("heading_level", section.level);
            data.put("chunk_level", "SECTION");
        }
    }

    private static final class Section {
        String title;
        int level;
        String path;
        StringBuilder content;
        String text;
    }
}

