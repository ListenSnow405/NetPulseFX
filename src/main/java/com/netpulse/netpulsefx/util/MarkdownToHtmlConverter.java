package com.netpulse.netpulsefx.util;

import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

import java.util.Arrays;

/**
 * Markdown 转 HTML 工具类
 * 
 * <p>使用 flexmark-java 库将 Markdown 格式的文本转换为 HTML，
 * 用于在 WebView 中显示富文本内容。</p>
 * 
 * <p>功能说明：</p>
 * <ul>
 *   <li>支持标准 Markdown 语法（标题、列表、粗体、斜体、代码块等）</li>
 *   <li>支持表格、任务列表等扩展语法</li>
 *   <li>支持 Emoji 等特殊符号</li>
 *   <li>自动添加内联 CSS 样式，使 HTML 内容与 JavaFX 主题保持一致</li>
 * </ul>
 * 
 * @author NetPulseFX Team
 */
public class MarkdownToHtmlConverter {
    
    /**
     * 配置 flexmark 解析器选项
     * 显式启用表格扩展，确保表格能够正确解析
     */
    private static final MutableDataSet OPTIONS = new MutableDataSet();
    
    static {
        // 显式启用表格扩展
        OPTIONS.set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create()));
    }
    
    /**
     * Markdown 解析器实例（线程安全，可复用）
     */
    private static final Parser PARSER = Parser.builder(OPTIONS).build();
    
    /**
     * HTML 渲染器实例（线程安全，可复用）
     */
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder(OPTIONS).build();
    
    /**
     * 将 Markdown 文本转换为 HTML
     * 
     * <p>转换后的 HTML 包含完整的内联样式，可以直接在 WebView 中显示。</p>
     * 
     * @param markdown Markdown 格式的文本
     * @return 完整的 HTML 文档（包含样式）
     */
    public static String convertToHtml(String markdown) {
        if (markdown == null || markdown.trim().isEmpty()) {
            return wrapWithHtmlTemplate("");
        }
        
        try {
            // 解析 Markdown 并转换为 HTML
            var document = PARSER.parse(markdown);
            String htmlBody = RENDERER.render(document);
            
            // 调试输出：检查是否包含表格标签
            if (htmlBody.contains("<table") || htmlBody.contains("&lt;table")) {
                System.out.println("[MarkdownToHtmlConverter] 检测到表格标签");
            }
            
            // 包装为完整的 HTML 文档，并添加样式
            return wrapWithHtmlTemplate(htmlBody);
        } catch (Exception e) {
            System.err.println("[MarkdownToHtmlConverter] 转换失败: " + e.getMessage());
            e.printStackTrace();
            // 如果转换失败，返回原始文本（转义 HTML）
            return wrapWithHtmlTemplate("<pre>" + escapeHtml(markdown) + "</pre>");
        }
    }
    
    /**
     * 转义 HTML 特殊字符
     */
    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
    
    /**
     * 将 HTML 内容包装为完整的 HTML 文档，并添加内联 CSS 样式
     * 
     * <p>样式设计说明：</p>
     * <ul>
     *   <li>字体：使用系统默认字体，回退到 "Microsoft YaHei" 和 "Consolas"</li>
     *   <li>颜色：深色文字（#2c3e50）配合浅色背景（#ffffff）</li>
     *   <li>间距：合理的行高和内边距，提升可读性</li>
     *   <li>代码块：使用等宽字体和浅灰色背景</li>
     *   <li>链接：蓝色主题色，悬停时加深</li>
     * </ul>
     * 
     * @param htmlBody HTML 正文内容
     * @return 完整的 HTML 文档
     */
    private static String wrapWithHtmlTemplate(String htmlBody) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Microsoft YaHei", "PingFang SC", "Hiragino Sans GB", "Helvetica Neue", Arial, sans-serif;
                        font-size: 14px;
                        line-height: 1.6;
                        color: #2c3e50;
                        background-color: #ffffff;
                        padding: 20px;
                        margin: 0;
                        word-wrap: break-word;
                        /* 确保 Emoji 和特殊符号正确显示 */
                        font-feature-settings: "liga" 1, "calt" 1;
                        text-rendering: optimizeLegibility;
                    }
                    
                    h1 {
                        font-size: 24px;
                        font-weight: 600;
                        color: #1a1a1a;
                        margin-top: 24px;
                        margin-bottom: 16px;
                        padding-bottom: 8px;
                        border-bottom: 2px solid #e1e4e8;
                    }
                    
                    h2 {
                        font-size: 20px;
                        font-weight: 600;
                        color: #24292e;
                        margin-top: 20px;
                        margin-bottom: 12px;
                        padding-bottom: 6px;
                        border-bottom: 1px solid #e1e4e8;
                    }
                    
                    h3 {
                        font-size: 18px;
                        font-weight: 600;
                        color: #24292e;
                        margin-top: 16px;
                        margin-bottom: 10px;
                    }
                    
                    h4 {
                        font-size: 16px;
                        font-weight: 600;
                        color: #24292e;
                        margin-top: 14px;
                        margin-bottom: 8px;
                    }
                    
                    p {
                        margin-top: 0;
                        margin-bottom: 12px;
                    }
                    
                    ul, ol {
                        margin-top: 0;
                        margin-bottom: 12px;
                        padding-left: 24px;
                    }
                    
                    li {
                        margin-bottom: 6px;
                    }
                    
                    strong {
                        font-weight: 600;
                        color: #1a1a1a;
                    }
                    
                    em {
                        font-style: italic;
                        color: #586069;
                    }
                    
                    code {
                        font-family: "Consolas", "Monaco", "Courier New", monospace;
                        font-size: 13px;
                        background-color: #f6f8fa;
                        padding: 2px 6px;
                        border-radius: 3px;
                        color: #e83e8c;
                    }
                    
                    pre {
                        font-family: "Consolas", "Monaco", "Courier New", monospace;
                        font-size: 13px;
                        background-color: #f6f8fa;
                        padding: 16px;
                        border-radius: 6px;
                        overflow-x: auto;
                        margin-top: 0;
                        margin-bottom: 16px;
                        border: 1px solid #e1e4e8;
                    }
                    
                    pre code {
                        background-color: transparent;
                        padding: 0;
                        color: #24292e;
                    }
                    
                    blockquote {
                        margin: 0;
                        padding: 0 16px;
                        color: #6a737d;
                        border-left: 4px solid #dfe2e5;
                    }
                    
                    a {
                        color: #0366d6;
                        text-decoration: none;
                    }
                    
                    a:hover {
                        color: #0056b3;
                        text-decoration: underline;
                    }
                    
                    table {
                        border-collapse: collapse;
                        border-spacing: 0;
                        width: 100%%;
                        margin-top: 16px;
                        margin-bottom: 16px;
                        display: table;
                    }
                    
                    table thead {
                        display: table-header-group;
                        background-color: #f6f8fa;
                    }
                    
                    table tbody {
                        display: table-row-group;
                    }
                    
                    table tr {
                        display: table-row;
                        border-top: 1px solid #dfe2e5;
                    }
                    
                    table tr:nth-child(even) {
                        background-color: #f6f8fa;
                    }
                    
                    table tr:hover {
                        background-color: #f9fafb;
                    }
                    
                    table th, table td {
                        padding: 8px 12px;
                        border: 1px solid #dfe2e5;
                        display: table-cell;
                    }
                    
                    table th {
                        background-color: #f6f8fa;
                        font-weight: 600;
                        text-align: left;
                    }
                    
                    table td {
                        text-align: left;
                    }
                    
                    hr {
                        height: 1px;
                        background-color: #e1e4e8;
                        border: 0;
                        margin: 24px 0;
                    }
                    
                    /* 引用块样式增强 */
                    blockquote {
                        margin-left: 0;
                        margin-right: 0;
                        margin-top: 16px;
                        margin-bottom: 16px;
                        padding-left: 16px;
                        padding-right: 16px;
                    }
                    
                    /* 确保代码块中的内容可以横向滚动 */
                    pre {
                        white-space: pre;
                        overflow-x: auto;
                    }
                </style>
            </head>
            <body>
                %s
            </body>
            </html>
            """, htmlBody);
    }
}

