package com.genie.query.domain.vectorstore;

import com.genie.query.domain.knowledge.model.KLField;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 向量数据库抽象接口，只做纯粹的向量库增删改查，不依赖领域模型（如 Chunk、IndexChunk）。
 * 文档以通用 Map 表示：字段名 -> 值（标量或 float[] 向量），具体实现由 infrastructure 提供。
 *
 * @author daicy
 * @date 2026/1/9
 */
public interface VectorStore {

    /**
     * 创建向量库（索引）。
     */
    void createVectorDB(String dbName, List<KLField> klFieldList) throws IOException;

    /**
     * 删除向量库。
     */
    void deleteVectorDB(String code) throws IOException;

    /**
     * 批量写入文档。每个 map 表示一条文档：key 为字段名，value 为标量或 float[]（向量字段约定以 "_vector" 结尾）。
     *
     * @param knowledgeCode 知识库编码（对应向量库/索引名）
     * @param docId         源文档 id，用于按文档删除
     * @param documents     文档列表，每条为字段名 -> 值
     */
    void batchAddDocuments(String knowledgeCode, String docId, List<Map<String, Object>> documents) throws IOException;

    /**
     * 按源文档 id 删除该文档在知识库下的所有文档（用于重算/覆盖）。
     */
    void deleteByDocId(String knowledgeCode, String docId) throws IOException;

    /**
     * 按源文档 id 查询该文档在知识库下的所有分块（返回标量字段，不含向量字段）。
     *
     * @param knowledgeCode 知识库编码（对应索引名）
     * @param docId         源文档 id
     * @return 分块列表，每条为字段名 -> 值（不含 *_vector_system）
     */
    List<Map<String, Object>> listChunksByDocId(String knowledgeCode, String docId) throws IOException;

    /**
     * 多知识库检索：支持关键字、向量、混合三种方式。
     *
     * @param param 检索参数（知识库列表、关键字/向量、各索引的字段配置、条数）
     * @return 命中列表，按相关性排序；每条包含知识库编码、docId、分数、分块内容
     */
    List<ChunkSearchHit> search(VectorSearchParam param) throws IOException;
}
