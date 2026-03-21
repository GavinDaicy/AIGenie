package com.genie.query.domain.fileparser.web;

import com.genie.query.domain.document.model.DocType;
import com.genie.query.domain.fileparser.web.webpage.WebPageLinkParser;
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
public class WEBLinkParserFactory implements InitializingBean {

    @Autowired
    private ObjectProvider<YuqueLinkParser> yuqueParserProvider;
    @Autowired
    private ObjectProvider<WebPageLinkParser> webpageParserProvider;

    private static ObjectProvider<YuqueLinkParser> staticYuqueParserProvider;
    private static ObjectProvider<WebPageLinkParser> staticWebpageParserProvider;

    @Override
    public void afterPropertiesSet() throws Exception {
        staticYuqueParserProvider = this.yuqueParserProvider;
        staticWebpageParserProvider = this.webpageParserProvider;
    }

    public static WebLinkParser getParser(DocType type) {
        return switch (type) {
            case WEBLINK -> staticWebpageParserProvider.getObject();
            case YUQUELINK -> staticYuqueParserProvider.getObject();
            default -> throw new IllegalArgumentException("不支持的文档类型");
        };
    }
}
