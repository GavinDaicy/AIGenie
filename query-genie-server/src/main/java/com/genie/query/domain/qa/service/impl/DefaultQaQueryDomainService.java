package com.genie.query.domain.qa.service.impl;

import com.genie.query.domain.qa.service.QaQueryService;
import com.genie.query.domain.qa.service.QaSearchCapability;
import com.genie.query.domain.query.model.QueryResultEntry;
import com.genie.query.domain.query.service.QueryService;
import com.genie.query.domain.vectorstore.ChunkSearchHit;
import com.genie.query.domain.vectorstore.SearchMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * QA 领域策略服务：负责预算、模式升级、去重、rerank 触发与候选过滤。
 */
@Service
public class DefaultQaQueryDomainService implements QaQueryService {
    private static final Logger log = LoggerFactory.getLogger(DefaultQaQueryDomainService.class);
    private static final List<String> CHUNK_ID_KEYS = List.of("chunk_id", "chunkId", "id");
    private static final List<String> CHUNK_INDEX_KEYS = List.of("chunk_index", "chunkIndex");
    private static final List<String> CHUNK_OFFSET_KEYS = List.of("chunk_offset", "offset", "start_offset", "startOffset");
    private static final List<String> TEXT_CONTENT_KEYS = List.of("content", "text", "chunk_content", "chunkContent", "sys_content");
    private static final int DEDUP_TEXT_MAX_LEN = 2000;

    @Value("${app.qa.search.max-es-queries-per-ask:8}")
    private int maxEsQueriesPerAsk;
    @Value("${app.qa.search.short-query-char-threshold:8}")
    private int shortQueryCharThreshold;
    @Value("${app.qa.search.short-query-keyword-first:true}")
    private boolean shortQueryKeywordFirst;
    @Value("${app.qa.search.keyword-upgrade-top1-threshold:7.0}")
    private double keywordUpgradeTop1Threshold;
    @Value("${app.qa.search.keyword-upgrade-min-hits:2}")
    private int keywordUpgradeMinHits;
    @Value("${app.qa.search.rerank-uncertainty-gap-threshold:0.6}")
    private double rerankUncertaintyGapThreshold;
    @Value("${app.qa.search.rerank-gap-compare-k:5}")
    private int rerankGapCompareK;
    @Value("${app.qa.search.rerank-long-query-char-threshold:32}")
    private int rerankLongQueryCharThreshold;
    @Value("${app.qa.search.rerank-prefilter-score-threshold:4.0}")
    private double rerankPrefilterScoreThreshold;
    @Value("${app.qa.search.rerank-prefilter-max-chunks-per-doc:2}")
    private int rerankPrefilterMaxChunksPerDoc;

    private final QueryService queryService;
    private final QaSearchCapability qaSearchCapability;

    public DefaultQaQueryDomainService(QueryService queryService, QaSearchCapability qaSearchCapability) {
        this.queryService = queryService;
        this.qaSearchCapability = qaSearchCapability;
    }

    @Override
    public List<QueryResultEntry> searchWithQueries(List<String> queries, List<String> knowledgeCodes, SearchMode mode, int size,
                                                    int searchSize, boolean enableRerank, Boolean useTimeDecay) throws IOException {
        if (queries == null || queries.isEmpty()) {
            return List.of();
        }
        List<String> normalizedQueries = queries.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(q -> !q.isBlank())
                .toList();
        if (normalizedQueries.isEmpty()) {
            return List.of();
        }

        int budget = Math.max(1, maxEsQueriesPerAsk);
        int consumed = 0;
        boolean budgetClipped = false;

        String mainQuery = normalizedQueries.get(0);
        SearchMode mainMode = decideMainMode(mainQuery, mode);

        List<ChunkSearchHit> allHits = new ArrayList<>();
        int mainCost = estimateEsCost(mainMode);
        if (consumed + mainCost > budget) {
            budgetClipped = true;
            return List.of();
        }
        List<ChunkSearchHit> mainHits = qaSearchCapability.searchHits(mainQuery, mainMode, searchSize, knowledgeCodes, useTimeDecay);
        allHits.addAll(mainHits);
        consumed += mainCost;

        if (shouldUpgradeToHybrid(mainQuery, mode, mainMode, mainHits, searchSize)) {
            int hybridCost = estimateEsCost(SearchMode.HYBRID);
            if (consumed + hybridCost <= budget) {
                List<ChunkSearchHit> upgradedHits = qaSearchCapability.searchHits(mainQuery, SearchMode.HYBRID, searchSize, knowledgeCodes, useTimeDecay);
                allHits.addAll(upgradedHits);
                consumed += hybridCost;
                mainMode = SearchMode.HYBRID;
            } else {
                budgetClipped = true;
            }
        }

        for (int i = 1; i < normalizedQueries.size(); i++) {
            String expandedQuery = normalizedQueries.get(i);
            SearchMode expandedMode = decideExpandedMode(mode);
            int cost = estimateEsCost(expandedMode);
            if (consumed + cost > budget) {
                budgetClipped = true;
                break;
            }
            allHits.addAll(qaSearchCapability.searchHits(expandedQuery, expandedMode, searchSize, knowledgeCodes, useTimeDecay));
            consumed += cost;
        }
        if (allHits.isEmpty()) {
            return List.of();
        }

        if (log.isInfoEnabled()) {
            log.info("qa_search_budget mainMode={} requestedMode={} rewriteCount={} consumedCost={} budget={} clipped={}",
                    mainMode, mode, normalizedQueries.size(), consumed, budget, budgetClipped);
        }

        List<ChunkSearchHit> deduped = deduplicateHits(allHits);
        if (enableRerank && qaSearchCapability.supportsRerank() && !deduped.isEmpty() && size > 0
                && shouldRunRerank(mainQuery, deduped)) {
            List<ChunkSearchHit> candidates = prefilterRerankCandidates(deduped);
            deduped = qaSearchCapability.rerank(mainQuery, candidates, size);
        } else if (size > 0) {
            deduped = deduped.stream()
                    .sorted(Comparator.comparingDouble(ChunkSearchHit::getScore).reversed())
                    .limit(size)
                    .toList();
        }
        return queryService.buildEntriesFromHits(deduped, enableRerank);
    }

    private SearchMode decideMainMode(String mainQuery, SearchMode requestedMode) {
        if (requestedMode != SearchMode.HYBRID) {
            return requestedMode;
        }
        if (!shortQueryKeywordFirst) {
            return SearchMode.HYBRID;
        }
        if (isShortBusinessLikeQuery(mainQuery)) {
            return SearchMode.KEYWORD;
        }
        return SearchMode.HYBRID;
    }

    private SearchMode decideExpandedMode(SearchMode requestedMode) {
        return requestedMode == SearchMode.VECTOR ? SearchMode.VECTOR : SearchMode.KEYWORD;
    }

    private boolean shouldUpgradeToHybrid(String mainQuery, SearchMode requestedMode, SearchMode mainMode,
                                          List<ChunkSearchHit> mainHits, int searchSize) {
        if (requestedMode != SearchMode.HYBRID || mainMode != SearchMode.KEYWORD) {
            return false;
        }
        if (!isShortBusinessLikeQuery(mainQuery)) {
            return false;
        }
        if (mainHits == null || mainHits.isEmpty()) {
            return true;
        }
        int minHits = Math.max(1, Math.min(searchSize, keywordUpgradeMinHits));
        if (mainHits.size() < minHits) {
            return true;
        }
        double top1 = mainHits.stream().mapToDouble(ChunkSearchHit::getScore).max().orElse(0D);
        return top1 < keywordUpgradeTop1Threshold;
    }

    private int estimateEsCost(SearchMode mode) {
        return mode == SearchMode.HYBRID ? 2 : 1;
    }

    private boolean isShortBusinessLikeQuery(String query) {
        if (query == null) {
            return false;
        }
        String normalized = query.trim().replaceAll("\\s+", "");
        if (normalized.isEmpty()) {
            return false;
        }
        if (normalized.length() > Math.max(1, shortQueryCharThreshold)) {
            return false;
        }
        return normalized.chars().allMatch(c ->
                Character.isLetterOrDigit(c) || Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN || c == '_' || c == '-');
    }

    private List<ChunkSearchHit> deduplicateHits(List<ChunkSearchHit> hits) {
        Map<String, ChunkSearchHit> dedup = new LinkedHashMap<>();
        for (ChunkSearchHit hit : hits) {
            String key = buildDedupKey(hit);
            dedup.merge(key, hit, (existing, candidate) ->
                    candidate.getScore() > existing.getScore() ? candidate : existing);
        }
        return new ArrayList<>(dedup.values());
    }

    private String buildDedupKey(ChunkSearchHit hit) {
        String knowledgeCode = safeString(hit.getKnowledgeCode());
        String docId = safeString(hit.getDocId());
        Map<String, Object> chunkContent = hit.getChunkContent();

        String chunkId = findFirstNonBlank(chunkContent, CHUNK_ID_KEYS);
        if (!chunkId.isBlank()) {
            return knowledgeCode + "|" + docId + "|chunkId:" + chunkId;
        }

        String chunkIndex = findFirstNonBlank(chunkContent, CHUNK_INDEX_KEYS);
        String chunkOffset = findFirstNonBlank(chunkContent, CHUNK_OFFSET_KEYS);
        if (!chunkIndex.isBlank() || !chunkOffset.isBlank()) {
            return knowledgeCode + "|" + docId + "|chunkPos:" + chunkIndex + "|" + chunkOffset;
        }

        String normalizedText = normalizeText(findPrimaryText(chunkContent));
        if (!normalizedText.isBlank()) {
            return knowledgeCode + "|" + docId + "|textHash:" + sha256(normalizedText);
        }

        String normalizedStableMap = normalizeText(stableSerialize(chunkContent));
        return knowledgeCode + "|" + docId + "|mapHash:" + sha256(normalizedStableMap);
    }

    private String findPrimaryText(Map<String, Object> chunkContent) {
        String preferred = findFirstNonBlank(chunkContent, TEXT_CONTENT_KEYS);
        if (!preferred.isBlank()) {
            return preferred;
        }
        if (chunkContent == null || chunkContent.isEmpty()) {
            return "";
        }
        return chunkContent.values().stream()
                .filter(Objects::nonNull)
                .map(v -> Objects.toString(v, ""))
                .collect(Collectors.joining(" "));
    }

    private String findFirstNonBlank(Map<String, Object> data, List<String> keys) {
        if (data == null || data.isEmpty()) {
            return "";
        }
        for (String key : keys) {
            Object value = data.get(key);
            if (value == null) {
                continue;
            }
            String text = value.toString().trim();
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private String normalizeText(String input) {
        if (input == null) {
            return "";
        }
        String normalized = input.trim().replaceAll("\\s+", " ");
        if (normalized.length() > DEDUP_TEXT_MAX_LEN) {
            return normalized.substring(0, DEDUP_TEXT_MAX_LEN);
        }
        return normalized;
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String stableSerialize(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            map.forEach((k, v) -> sorted.put(Objects.toString(k, ""), v));
            return sorted.entrySet().stream()
                    .map(e -> e.getKey() + ":" + stableSerialize(e.getValue()))
                    .collect(Collectors.joining(",", "{", "}"));
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(this::stableSerialize)
                    .collect(Collectors.joining(",", "[", "]"));
        }
        return Objects.toString(value, "");
    }

    private boolean shouldRunRerank(String query, List<ChunkSearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return false;
        }
        int normalizedLen = normalizeQuery(query).length();
        if (normalizedLen >= Math.max(1, rerankLongQueryCharThreshold)) {
            return true;
        }
        List<ChunkSearchHit> sorted = hits.stream()
                .sorted(Comparator.comparingDouble(ChunkSearchHit::getScore).reversed())
                .toList();
        double top1 = sorted.get(0).getScore();
        int compareK = Math.max(2, Math.min(rerankGapCompareK, sorted.size()));
        double topK = sorted.get(compareK - 1).getScore();
        double gap = top1 - topK;
        return gap <= rerankUncertaintyGapThreshold;
    }

    private List<ChunkSearchHit> prefilterRerankCandidates(List<ChunkSearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        int maxChunksPerDoc = Math.max(1, rerankPrefilterMaxChunksPerDoc);
        double scoreThreshold = rerankPrefilterScoreThreshold;
        Map<String, Integer> docCounter = new LinkedHashMap<>();
        List<ChunkSearchHit> sorted = hits.stream()
                .sorted(Comparator.comparingDouble(ChunkSearchHit::getScore).reversed())
                .toList();
        List<ChunkSearchHit> kept = new ArrayList<>(sorted.size());
        for (ChunkSearchHit hit : sorted) {
            if (hit.getScore() < scoreThreshold) {
                continue;
            }
            String docKey = buildDocLimitKey(hit);
            int keptCount = docCounter.getOrDefault(docKey, 0);
            if (keptCount >= maxChunksPerDoc) {
                continue;
            }
            kept.add(hit);
            docCounter.put(docKey, keptCount + 1);
        }
        return kept.isEmpty() ? sorted : kept;
    }

    private String buildDocLimitKey(ChunkSearchHit hit) {
        String knowledgeCode = safeString(hit.getKnowledgeCode());
        String docId = safeString(hit.getDocId());
        return knowledgeCode + "|" + docId;
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.trim().replaceAll("\\s+", "");
    }
}
