package com.genie.query.domain.fileparser.web.yuque;

import com.genie.query.domain.document.model.Document;
import com.genie.query.domain.fileparser.DocumentParser;
import com.genie.query.domain.fileparser.web.WebLinkParser;
import com.genie.query.domain.objectstore.ObjectStore;
import com.genie.query.infrastructure.remote.YuqueClient;
import com.genie.query.infrastructure.util.HtmlToMdConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/12
 */

/**
 * {
 *     "name": "企业数据综合检索方案设计 V2",
 *     "description": "企业数据综合检索方案设计 V2",
 *     "docType": "yuque",
 *     "category": "UNSTRUCTURED",
 *     "url": "https://www.yuque.com/api/v2/repos/42271794/docs/249972430"
 * }
 */
@Service
public class YuqueLinkParser implements WebLinkParser, DocumentParser {

    @Autowired
    private YuqueClient yuqueClient;
    @Autowired
    private ObjectStore objectStore;

    @Override
    public String parse(String originUri) {
        return yuqueClient.getDocContent(originUri);
    }

    @Override
    public void parse(Document document) throws IOException {
        InputStream inputStream = objectStore.downloadFile(document.getObjectStorePathOrigin());
        String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        inputStream.close();
        String md = HtmlToMdConverter.convertHtmlToMarkdown(content);
        objectStore.saveFile(document.getObjectStorePathParsed(), md);
    }
}
