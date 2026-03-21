package com.genie.query.controller;

import com.genie.query.application.KnowledgeApplication;
import com.genie.query.domain.knowledge.model.Knowledge;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库控制器
 *
 * @author daicy
 * @date 2026/1/6
 */
@RestController
@RequestMapping("/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeApplication knowledgeApplication;

    /**
     * 查询知识库列表
     */
    @GetMapping("/list")
    public List<Knowledge> queryKnowledgeList() {
        return knowledgeApplication.queryKnowledgeList();
    }

    /**
     * 创建知识库
     */
    @PostMapping("/create")
    public Knowledge createKnowledge(@RequestBody Knowledge knowledge) {
        return knowledgeApplication.createKnowledge(knowledge);
    }

    /**
     * 更新知识库
     */
    @PutMapping("/updateBaseInfo")
    public Knowledge updateKnowledgeBaseInfo(@RequestBody Knowledge knowledge) {
        return knowledgeApplication.updateKnowledgeBaseInfo(knowledge);
    }

    @PutMapping("publish/{code}")
    public String publishKnowledge(@PathVariable String code) {
        knowledgeApplication.publishKnowledge(code);
        return "发布成功";
    }

    /**
     * 删除知识库
     */
    @DeleteMapping("/delete/{code}")
    public void deleteKnowledge(@PathVariable String code) {
        knowledgeApplication.deleteKnowledge(code);
    }
}
