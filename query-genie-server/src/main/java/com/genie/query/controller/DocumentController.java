package com.genie.query.controller;

import com.genie.query.application.DocumentApplication;
import com.genie.query.domain.document.model.DocCategory;
import com.genie.query.domain.document.model.Document;
import com.genie.query.domain.document.model.dto.RemoteDocumentDTO;
import com.genie.query.domain.document.model.dto.UploadDocumentDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 文档管理控制器
 *
 * @author daicy
 * @date 2026/1/7
 */
@RestController
@RequestMapping("/doc")
public class DocumentController {

    @Autowired
    private DocumentApplication documentApplication;

    /**
     * 查询文档列表
     */
    @GetMapping("/list")
    public List<Document> queryDocumentList(@RequestParam(required = false) String knowledgeCode) {
        return documentApplication.queryDocumentList(knowledgeCode);
    }

    /**
     * 添加本地文档（multipart 上传，供前端使用）
     */
    @PostMapping("/upload")
    public Document uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("knowledgeCode") String knowledgeCode,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "category", required = false) DocCategory category) {
        UploadDocumentDTO dto = new UploadDocumentDTO();
        dto.setKnowledgeCode(knowledgeCode);
        dto.setName(name != null && !name.isEmpty() ? name : file.getOriginalFilename());
        dto.setDescription(description);
        dto.setCategory(category != null ? category : DocCategory.UNSTRUCTURED);
        dto.setFile(file);
        return documentApplication.addLocalDocument(dto);
    }

    /**
     * 添加远程文档，支持语雀、网页链接（直接归属指定知识库，需传 knowledgeCode）
     */
    @PostMapping("/addRemote")
    public Document addRemoteDocument(@RequestBody RemoteDocumentDTO document) throws IOException, InterruptedException {
        return documentApplication.addRemoteDocument(document);
    }

    /**
     * 更新文档
     */
    @PutMapping("/update")
    public Document updateDocument(@RequestBody Document document) {
        return documentApplication.updateDocument(document);
    }

    /**
     * 根据文档 id 查询该文档所有分块数据（不含向量字段）
     */
    @GetMapping("/{documentId}/chunks")
    public List<Map<String, Object>> listChunksByDocumentId(@PathVariable String documentId) throws IOException {
        return documentApplication.listChunksByDocumentId(documentId);
    }

    /**
     * 删除文档
     * @throws IOException 
     */
    @DeleteMapping("/delete/{documentId}")
    public void deleteDocument(@PathVariable String documentId) throws IOException {
        documentApplication.deleteDocument(documentId);
    }
}
