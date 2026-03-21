package com.genie.query.domain.fileparser.web;

import java.io.IOException;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/12
 */
public interface WebLinkParser {
    String parse(String originUri) throws IOException, InterruptedException;
}
