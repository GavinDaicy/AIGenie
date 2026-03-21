package com.genie.query.domain.etlpipeline.spliter.impl;

import com.genie.query.domain.document.model.Document;
import com.genie.query.domain.document.model.DocType;
import com.genie.query.domain.etlpipeline.model.Chunk;
import com.genie.query.domain.etlpipeline.spliter.DocumentSplitter;
import com.genie.query.domain.knowledge.model.KLField;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 按行切块策略实现：适用于 Excel/CSV。
 * - 支持表头自动识别与字段映射；
 * - 引入 sys_content 字段，用于聚合非结构化列与异常行内容，保证数据不丢且可全文/向量检索。
 */
@Component
public class RowBasedDocumentSplitter implements DocumentSplitter {

    private static final int HEADER_SCAN_LIMIT = 20;
    private static final double DEFAULT_DATA_RATIO_THRESHOLD = 0.3d;
    private static final String SYS_CONTENT_FIELD_KEY = "sys_content";
    private static final String CELL_JOIN_SEPARATOR = "  ";

    @Override
    public List<Chunk> split(Document doc, InputStream parsedInput, List<KLField> userFields) {
        if (userFields == null || userFields.isEmpty()) {
            return List.of();
        }
        DocType type = doc.getType();
        if (type == DocType.CSV) {
            return splitCsv(doc, parsedInput, userFields);
        }
        if (type == DocType.EXCEL) {
            return splitExcel(doc, parsedInput, userFields, doc.getName());
        }
        return List.of();
    }

    private List<Chunk> splitCsv(Document doc, InputStream input, List<KLField> userFields) {
        try {
            byte[] bytes = input.readAllBytes();
            Charset charset = detectCharset(bytes);
            try (CSVParser parser = CSVParser.parse(
                    new ByteArrayInputStream(bytes),
                    charset,
                    CSVFormat.DEFAULT)) {
                List<TableRow> rows = buildRowsFromCsv(parser);
                return buildChunksFromTable(doc, rows, userFields);
            }
        } catch (IOException e) {
            throw new RuntimeException("解析 CSV 失败", e);
        }
    }

    private List<Chunk> splitExcel(Document doc, InputStream input, List<KLField> userFields, String fileName) {
        try (Workbook workbook = createWorkbook(input, fileName)) {
            List<Chunk> allChunks = new ArrayList<>();
            int order = 0;
            int sheetCount = workbook.getNumberOfSheets();
            for (int i = 0; i < sheetCount; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                if (sheet == null) {
                    continue;
                }
                // 合并单元格预处理：将合并区域左上角的值扩展到被合并的每一个格子
                List<List<String>> expandedGrid = expandMergedCellsToGrid(sheet);
                List<TableRow> rows = buildRowsFromExpandedGrid(expandedGrid);
                List<Chunk> sheetChunks = buildChunksFromTable(doc, rows, userFields);
                for (Chunk chunk : sheetChunks) {
                    chunk.setOrder(order++);
                    allChunks.add(chunk);
                }
            }
            return allChunks;
        } catch (IOException e) {
            throw new RuntimeException("解析 Excel 失败", e);
        }
    }

    private static Workbook createWorkbook(InputStream input, String fileName) throws IOException {
        if (fileName != null && fileName.toLowerCase().endsWith(".xls")) {
            return new HSSFWorkbook(input);
        }
        // xlsx 文件可能包含无效的 relationship Target URI（如 WPS 云盘路径中含 http:），
        // 会导致 POI 解析时 URISyntaxException。先做 sanitize 再解析，避免报错并保证解析成功。
        // return new XSSFWorkbook(input);
        byte[] bytes = input.readAllBytes();
        byte[] toUse = bytes;
        try {
            toUse = sanitizeXlsxRelationshipUris(bytes);
        } catch (Exception e) {
            // sanitize 失败时回退到原始内容，让 POI 自行处理（可能仅打 ERROR 日志）
        }
        return new XSSFWorkbook(new ByteArrayInputStream(toUse));
    }

    /**
     * 清理 xlsx（ZIP 格式）中 relationship 文件里可能导致 URISyntaxException 的 Target 属性。
     * 原因：部分 Excel（如从 WPS 云盘保存）会在内部 metadata 中嵌入原始路径，路径若含
     * "http:" 等非法 URI 字符，POI 解析时会报错。将无效 Target 替换为 "#" 后不影响表格数据解析。
     */
    private static byte[] sanitizeXlsxRelationshipUris(byte[] xlsxBytes) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(xlsxBytes));
             ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry;
            byte[] buf = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                zos.putNextEntry(new ZipEntry(entry.getName()));
                if (entry.getName().endsWith(".rels")) {
                    byte[] relsContent = zis.readAllBytes();
                    String xml = new String(relsContent, StandardCharsets.UTF_8);
                    String sanitized = sanitizeRelationshipTargets(xml);
                    zos.write(sanitized.getBytes(StandardCharsets.UTF_8));
                } else {
                    int n;
                    while ((n = zis.read(buf)) > 0) {
                        zos.write(buf, 0, n);
                    }
                }
                zos.closeEntry();
            }
            zos.finish();
            return baos.toByteArray();
        }
    }

    private static final Pattern TARGET_ATTR = Pattern.compile("Target=\"([^\"]*)\"");

    private static String sanitizeRelationshipTargets(String xml) {
        Matcher m = TARGET_ATTR.matcher(xml);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String target = m.group(1);
            String replacement;
            try {
                new URI(target);
                replacement = m.group(0);
            } catch (URISyntaxException e) {
                replacement = "Target=\"#\"";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 使用 juniversalchardet 自动探测编码，优先 UTF-8，探测/解码失败时回退到 GBK（兼容 GB2312）。
     */
    private static Charset detectCharset(byte[] bytes) {
        // 先用探测器尝试识别编码
        UniversalDetector detector = new UniversalDetector(null);
        detector.handleData(bytes, 0, bytes.length);
        detector.dataEnd();
        String detected = detector.getDetectedCharset();

        if (detected != null) {
            try {
                return Charset.forName(detected);
            } catch (Exception ignore) {
                // 探测到但不被支持时，回退到 UTF-8
                return StandardCharsets.UTF_8;
            }
        }

        // 探测不到时优先按 UTF-8 试一下
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        // 如果包含替换字符 �，说明可能不是 UTF-8，改用 GBK（向下兼容 GB2312）
        if (utf8.indexOf('\uFFFD') >= 0) {
            return Charset.forName("GBK");
        }
        return StandardCharsets.UTF_8;
    }

    private static List<TableRow> buildRowsFromCsv(CSVParser parser) throws IOException {
        List<TableRow> rows = new ArrayList<>();
        int rowIndex = 0;
        for (CSVRecord record : parser) {
            List<String> cells = new ArrayList<>(record.size());
            for (int i = 0; i < record.size(); i++) {
                cells.add(normalizeCell(record.get(i)));
            }
            rows.add(new TableRow(rowIndex++, cells));
        }
        return rows;
    }

    /**
     * 合并单元格预处理：将合并区域左上角的值扩展到被合并的每一个格子，输出完整的二维网格。
     */
    private static List<List<String>> expandMergedCellsToGrid(Sheet sheet) {
        List<List<String>> grid = new ArrayList<>();
        if (sheet == null) {
            return grid;
        }
        int lastRowNum = sheet.getLastRowNum();
        int maxCol = 0;
        for (int r = 0; r <= lastRowNum; r++) {
            Row row = sheet.getRow(r);
            if (row != null && row.getLastCellNum() > maxCol) {
                maxCol = row.getLastCellNum();
            }
        }
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            if (region.getLastColumn() + 1 > maxCol) {
                maxCol = region.getLastColumn() + 1;
            }
        }
        int lastRow = lastRowNum;
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            if (region.getLastRow() > lastRow) {
                lastRow = region.getLastRow();
            }
        }
        for (int r = 0; r <= lastRow; r++) {
            List<String> cells = new ArrayList<>();
            for (int c = 0; c < maxCol; c++) {
                cells.add(normalizeCell(getCellValueWithMergedSupport(sheet, r, c)));
            }
            grid.add(cells);
        }
        return grid;
    }

    /**
     * 获取单元格值，若在合并区域内则从左上角单元格取值。
     */
    private static String getCellValueWithMergedSupport(Sheet sheet, int rowIndex, int colIndex) {
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            if (region.isInRange(rowIndex, colIndex)) {
                Row firstRow = sheet.getRow(region.getFirstRow());
                if (firstRow == null) {
                    return "";
                }
                Cell firstCell = firstRow.getCell(region.getFirstColumn());
                return getCellString(firstCell);
            }
        }
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(colIndex);
        return getCellString(cell);
    }

    /**
     * 从已展开合并单元格的二维网格构建 TableRow 列表。
     */
    private static List<TableRow> buildRowsFromExpandedGrid(List<List<String>> grid) {
        List<TableRow> rows = new ArrayList<>();
        for (int r = 0; r < grid.size(); r++) {
            rows.add(new TableRow(r, new ArrayList<>(grid.get(r))));
        }
        return rows;
    }

    private static String normalizeCell(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String getCellString(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                yield switch (cell.getCachedFormulaResultType()) {
                    case STRING -> cell.getStringCellValue();
                    case NUMERIC -> String.valueOf(cell.getNumericCellValue());
                    case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                    default -> cell.getCellFormula();
                };
            }
            default -> "";
        };
    }

    private List<Chunk> buildChunksFromTable(Document doc, List<TableRow> rows, List<KLField> userFields) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        KLField sysContentField = findSysContentField(userFields);
        HeaderInfo headerInfo = detectHeader(rows, userFields);
        List<Chunk> chunks = new ArrayList<>();
        if (headerInfo == null) {
            buildChunksWithoutHeader(doc, rows, sysContentField, chunks);
        } else {
            buildChunksWithHeader(doc, rows, headerInfo, sysContentField, chunks);
        }
        return chunks;
    }

    private void buildChunksWithoutHeader(Document doc,
                                          List<TableRow> rows,
                                          KLField sysContentField,
                                          List<Chunk> chunks) {
        int order = 0;
        for (TableRow row : rows) {
            if (row.isEmpty()) {
                continue;
            }
            Map<String, Object> data = new LinkedHashMap<>();
            String sysContent = joinRowCells(row.cells());
            if (sysContentField != null) {
                data.put(SYS_CONTENT_FIELD_KEY, sysContent);
            }
            enrichMetadata(doc, data, row.rowIndex());
            Chunk c = new Chunk();
            c.setOrder(order++);
            c.setData(data);
            chunks.add(c);
        }
    }

    private void buildChunksWithHeader(Document doc,
                                       List<TableRow> rows,
                                       HeaderInfo headerInfo,
                                       KLField sysContentField,
                                       List<Chunk> chunks) {
        int order = 0;
        for (TableRow row : rows) {
            if (row.rowIndex() < headerInfo.headerRowIndex()) {
                order = addHeaderlessRowChunk(doc, row, sysContentField, chunks, order);
                continue;
            }
            if (row.rowIndex() == headerInfo.headerRowIndex()) {
                continue;
            }
            if (row.isEmpty()) {
                continue;
            }
            if (isWeakDataRow(row, headerInfo)) {
                order = addHeaderlessRowChunk(doc, row, sysContentField, chunks, order);
                continue;
            }
            order = addStructuredRowChunk(doc, row, headerInfo, sysContentField, chunks, order);
        }
    }

    private static KLField findSysContentField(List<KLField> userFields) {
        if (userFields == null) {
            return null;
        }
        return userFields.stream()
                .filter(f -> SYS_CONTENT_FIELD_KEY.equals(f.getFieldKey()))
                .findFirst()
                .orElse(null);
    }

    private static HeaderInfo detectHeader(List<TableRow> rows, List<KLField> userFields) {
        List<KLField> candidates = userFields.stream()
                .filter(f -> !SYS_CONTENT_FIELD_KEY.equals(f.getFieldKey()))
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        HeaderInfo best = null;
        int limit = Math.min(HEADER_SCAN_LIMIT, rows.size());
        for (int i = 0; i < limit; i++) {
            TableRow row = rows.get(i);
            if (row.isEffectivelyEmpty()) {
                continue;
            }
            HeaderInfo current = matchHeaderRow(row, candidates);
            if (current == null) {
                continue;
            }
            if (best == null || current.matchedCount() > best.matchedCount()) {
                best = current;
            }
        }
        if (best == null) {
            return null;
        }
        int candidateFieldCount = candidates.size();
        int threshold = Math.max(1, (int) Math.ceil(candidateFieldCount * 0.3d));
        if (best.matchedCount() < threshold) {
            return null;
        }
        return best;
    }

    private static HeaderInfo matchHeaderRow(TableRow row, List<KLField> candidates) {
        List<String> cells = row.cells();
        if (cells == null || cells.isEmpty()) {
            return null;
        }
        Map<Integer, String> colToField = new LinkedHashMap<>();
        int matched = 0;
        for (int i = 0; i < cells.size(); i++) {
            String cell = cells.get(i);
            if (cell == null) {
                continue;
            }
            String normalized = normalizeHeader(cell);
            KLField matchedField = findFieldForHeader(normalized, candidates);
            if (matchedField != null) {
                colToField.put(i, matchedField.getFieldKey());
                matched++;
            }
        }
        if (matched == 0) {
            return null;
        }
        // 记录下整行表头内容，后续在数据行里可将表头文本一起写入字段值，丰富召回信息
        return new HeaderInfo(row.rowIndex(), colToField, matched, row.cells());
    }

    private static KLField findFieldForHeader(String normalized, List<KLField> candidates) {
        for (KLField field : candidates) {
            if (headerMatchesField(normalized, field)) {
                return field;
            }
        }
        return null;
    }

    private static boolean headerMatchesField(String normalized, KLField field) {
        String keyNorm = normalizeHeader(field.getFieldKey());
        if (normalized.equals(keyNorm)) {
            return true;
        }
        if (StringUtils.isNotBlank(field.getName())) {
            String nameNorm = normalizeHeader(field.getName());
            return normalized.equals(nameNorm);
        }
        return false;
    }

    private static String normalizeHeader(String raw) {
        if (raw == null) {
            return "";
        }
        // 这里改为替换掉所有的空格和制表符
        raw = raw.replaceAll("[ \\t]", "");
        String lower = raw.trim().toLowerCase();
        if (lower.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if (Character.isLetterOrDigit(ch) || Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static boolean isWeakDataRow(TableRow row, HeaderInfo headerInfo) {
        if (headerInfo.mappedColumns().isEmpty()) {
            return true;
        }
        int nonEmptyOnMapped = 0;
        for (Integer col : headerInfo.mappedColumns().keySet()) {
            String value = row.getCell(col);
            if (value != null && !value.isEmpty()) {
                nonEmptyOnMapped++;
            }
        }
        double ratio = nonEmptyOnMapped * 1.0d / headerInfo.mappedColumns().size();
        return ratio < DEFAULT_DATA_RATIO_THRESHOLD;
    }

    private static int addHeaderlessRowChunk(Document doc,
                                             TableRow row,
                                             KLField sysContentField,
                                             List<Chunk> chunks,
                                             int order) {
        String sysContent = joinRowCells(row.cells());
        if (StringUtils.isBlank(sysContent)) {
            return order;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        if (sysContentField != null) {
            data.put(SYS_CONTENT_FIELD_KEY, sysContent);
        }
        enrichMetadata(doc, data, row.rowIndex());
        Chunk c = new Chunk();
        c.setOrder(order++);
        c.setData(data);
        chunks.add(c);
        return order;
    }

    private static int addStructuredRowChunk(Document doc,
                                             TableRow row,
                                             HeaderInfo headerInfo,
                                             KLField sysContentField,
                                             List<Chunk> chunks,
                                             int order) {
        List<String> cells = row.cells();
        Map<String, Object> data = new LinkedHashMap<>();
        List<String> sysContentParts = new ArrayList<>();
        List<String> headerCells = headerInfo.headerCells();
        for (int col = 0; col < cells.size(); col++) {
            String value = cells.get(col);
            String fieldKey = headerInfo.mappedColumns().get(col);
            if (fieldKey != null) {
                if (value != null) {
                    String enrichedValue = buildHeaderEnrichedValue(value, headerCells, col);
                    data.put(fieldKey, enrichedValue);
                }
            } else if (value != null) {
                sysContentParts.add(value);
            }
        }
        String sysContent = sysContentParts.isEmpty() ? null : String.join(CELL_JOIN_SEPARATOR, sysContentParts);
        if (sysContentField != null && StringUtils.isNotBlank(sysContent)) {
            data.put(SYS_CONTENT_FIELD_KEY, sysContent);
        }
        if (data.isEmpty()) {
            return order;
        }
        enrichMetadata(doc, data, row.rowIndex());
        Chunk c = new Chunk();
        c.setOrder(order++);
        c.setData(data);
        chunks.add(c);
        return order;
    }

    private static String joinRowCells(List<String> cells) {
        if (cells == null || cells.isEmpty()) {
            return null;
        }
        List<String> nonEmpty = new ArrayList<>();
        for (String cell : cells) {
            if (cell != null && !cell.isEmpty()) {
                nonEmpty.add(cell);
            }
        }
        if (nonEmpty.isEmpty()) {
            return null;
        }
        return String.join(CELL_JOIN_SEPARATOR, nonEmpty);
    }

    /**
     * 将表头文本与单元格值拼接，形成更丰富的字段内容。
     */
    private static String buildHeaderEnrichedValue(String value, List<String> headerCells, int colIndex) {
        if (value == null) {
            return null;
        }
        String headerText = null;
        if (headerCells != null && colIndex >= 0 && colIndex < headerCells.size()) {
            headerText = headerCells.get(colIndex);
        }
        if (headerText != null && StringUtils.isNotBlank(headerText) && !headerText.equals(value)) {
            return headerText + CELL_JOIN_SEPARATOR + value;
        }
        return value;
    }

    /**
     * 为表格行切块补充通用元信息。
     */
    private static void enrichMetadata(Document doc, Map<String, Object> data, int rowIndex) {
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
        data.put("chunk_level", "ROW");
        data.put("row_index", rowIndex);
    }

    private record TableRow(int rowIndex, List<String> cells) {

        boolean isEmpty() {
            if (cells == null || cells.isEmpty()) {
                return true;
            }
            for (String cell : cells) {
                if (cell != null && !cell.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        boolean isEffectivelyEmpty() {
            return isEmpty();
        }

        String getCell(int colIndex) {
            if (cells == null || colIndex < 0 || colIndex >= cells.size()) {
                return null;
            }
            return cells.get(colIndex);
        }
    }

    private record HeaderInfo(int headerRowIndex,
                              Map<Integer, String> mappedColumns,
                              int matchedCount,
                              List<String> headerCells) {
    }
}
