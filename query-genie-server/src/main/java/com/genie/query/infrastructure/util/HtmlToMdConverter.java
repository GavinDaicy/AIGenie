package com.genie.query.infrastructure.util;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;

public class HtmlToMdConverter {
    public static String convertHtmlToMarkdown(String html) {
        FlexmarkHtmlConverter converter = FlexmarkHtmlConverter.builder().build();
        return converter.convert(html);
    }

    public static void main(String[] args) {
        String html = "<h1>HTML转Markdown测试</h1>\n" +
                "<p>这是一个<strong>示例</strong>文本</p>\n" +
                "<ul>\n" +
                "  <li>列表项1</li>\n" +
                "  <li>列表项2</li>\n" +
                "</ul>\n" +
                "<p>访问<a href=\"https://www.baidu.com\">百度</a></p>";

        String markdown = convertHtmlToMarkdown(html);
        System.out.println("转换结果:\n" + markdown);
    }
}