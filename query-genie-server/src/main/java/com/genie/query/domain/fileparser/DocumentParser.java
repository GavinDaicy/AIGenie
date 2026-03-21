package com.genie.query.domain.fileparser;

import com.genie.query.domain.document.model.Document;

import java.io.IOException;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/12
 */
public interface DocumentParser {
    void parse(Document document) throws IOException;
}
