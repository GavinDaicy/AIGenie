package com.genie.query.domain.fileparser.raw;

import com.genie.query.domain.document.model.Document;
import com.genie.query.domain.fileparser.DocumentParser;
import com.genie.query.domain.objectstore.ObjectStore;
import com.genie.query.infrastructure.util.FilePathUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class RawDocumentParser implements DocumentParser {

    @Autowired
    private ObjectStore objectStore;

    @Override
    public void parse(Document document) throws IOException {
        objectStore.copyFile(
            FilePathUtils.getFullPath(document.getObjectStorePathOrigin()), 
            FilePathUtils.getFullPath(document.getObjectStorePathParsed()));
    }
}
