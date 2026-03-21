package com.genie.query.domain.fileparser;

import com.genie.query.domain.document.model.DocType;
import com.genie.query.domain.fileparser.md.MarkdownDocumentParser;
import com.genie.query.domain.fileparser.raw.RawDocumentParser;
import com.genie.query.domain.fileparser.web.yuque.YuqueLinkParser;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/12
 */
@Component
public class DocumentParserFactory implements InitializingBean {

    @Autowired
    private ObjectProvider<MarkdownDocumentParser> mdParserProvider;
    @Autowired
    private ObjectProvider<YuqueLinkParser> yuqueParserProvider;
    @Autowired
    private ObjectProvider<RawDocumentParser> rawParserProvider;

    private static ObjectProvider<MarkdownDocumentParser> staticMdDocumentParserProvider;
    private static ObjectProvider<YuqueLinkParser> staticYuqueDocumentParserProvider;
    private static ObjectProvider<RawDocumentParser> staticRawParserProvider;

    @Override
    public void afterPropertiesSet() throws Exception {
        staticMdDocumentParserProvider = this.mdParserProvider;
        staticYuqueDocumentParserProvider = this.yuqueParserProvider;
        staticRawParserProvider = this.rawParserProvider;
    }

    public static DocumentParser getParser(DocType type) {
        return switch (type) {
            case MARKDOWN -> staticMdDocumentParserProvider.getObject();
            case WEBLINK -> staticYuqueDocumentParserProvider.getObject();
            case YUQUELINK -> staticYuqueDocumentParserProvider.getObject();
            case TEXT, EXCEL, CSV -> staticRawParserProvider.getObject();
            default -> throw new IllegalArgumentException("不支持的文档类型");
        };
    }
}
