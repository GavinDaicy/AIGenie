package com.genie.query.domain.fileparser.md;

import com.genie.query.domain.document.model.Document;
import com.genie.query.domain.fileparser.DocumentParser;
import com.genie.query.domain.objectstore.ObjectStore;
import com.genie.query.infrastructure.util.FilePathUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/12
 */
@Service
public class MarkdownDocumentParser implements DocumentParser {

    @Autowired
    private ObjectStore objectStore;

    @Override
    public void parse(Document document) throws IOException {
        objectStore.copyFile(
            FilePathUtils.getFullPath(document.getObjectStorePathOrigin()), 
            FilePathUtils.getFullPath(document.getObjectStorePathParsed()));
    }
}
