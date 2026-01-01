package com.netpulse.netpulsefx;

import com.netpulse.netpulsefx.util.MarkdownToHtmlConverter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.web.WebView;
import javafx.scene.web.WebEngine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.ResourceBundle;

/**
 * 帮助中心窗口控制器
 * 
 * <p>功能说明：</p>
 * <ul>
 *   <li>自动扫描项目根目录下的 Markdown 文档</li>
 *   <li>在左侧列表显示文档标题（美化后的文件名）</li>
 *   <li>点击列表项时，在右侧 WebView 中显示文档内容</li>
 *   <li>使用 flexmark 库将 Markdown 转换为 HTML 并渲染</li>
 * </ul>
 * 
 * @author NetPulseFX Team
 */
public class HelpController implements Initializable {
    
    /** 主分割面板 */
    @FXML
    private SplitPane mainSplitPane;
    
    /** 文档列表视图 */
    @FXML
    private ListView<String> documentListView;
    
    /** 文档内容 WebView */
    @FXML
    private WebView contentWebView;
    
    /** 文档标题标签 */
    @FXML
    private Label documentTitleLabel;
    
    /** WebView 的引擎，用于加载 HTML 内容 */
    private WebEngine webEngine;
    
    /** 文档文件名列表（与 documentListView 的索引对应） */
    private List<String> documentFileNames;
    
    /**
     * 初始化方法
     * 在 FXML 加载后自动调用
     * 
     * @param location FXML 文件位置
     * @param resources 资源包
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 初始化 WebEngine
        webEngine = contentWebView.getEngine();
        
        // 配置 SplitPane：设置左侧面板固定宽度，分割条不可拖拽
        configureSplitPane();
        
        // 初始化文档列表（从类路径读取）
        initializeDocumentList();
        
        // 设置列表选择监听器
        documentListView.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> {
                if (newValue != null) {
                    loadDocument(newValue);
                }
            }
        );
    }
    
    /**
     * 配置 SplitPane
     * 设置左侧面板固定宽度，确保文档标题完整显示
     * 
     * <p>说明：</p>
     * <ul>
     *   <li>左侧面板已通过 FXML 设置为固定宽度（280px）</li>
     *   <li>由于 minWidth = maxWidth = prefWidth，SplitPane 会自动保持这个宽度</li>
     *   <li>分割条实际上无法移动，因为左侧面板宽度是固定的</li>
     * </ul>
     */
    private void configureSplitPane() {
        if (mainSplitPane == null) {
            return;
        }
        
        // 设置 ListView 的单元格工厂，使用 Label 支持文本换行，确保长标题完整显示
        documentListView.setCellFactory(listView -> new javafx.scene.control.ListCell<String>() {
            private final javafx.scene.control.Label label = new javafx.scene.control.Label();
            
            {
                // 配置 Label 支持文本换行
                label.setWrapText(true);
                label.setStyle("-fx-font-size: 13px; -fx-font-family: 'Microsoft YaHei', '微软雅黑', sans-serif;");
                label.setMaxWidth(Double.MAX_VALUE);
                // 设置内边距，提升可读性
                label.setPadding(new javafx.geometry.Insets(8, 10, 8, 10));
            }
            
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    label.setText(item);
                    setGraphic(label);
                    setText(null);
                }
            }
        });
    }
    
    /**
     * 初始化文档列表
     * 从类路径扫描所有 .md 文件，并添加到列表中
     * 
     * <p>实现说明：</p>
     * <ul>
     *   <li>使用类路径资源扫描，支持 Fat JAR 模式</li>
     *   <li>通过 ClassLoader 获取所有类路径中的 .md 文件</li>
     *   <li>排除不需要显示的文档</li>
     *   <li>按预定义顺序排序</li>
     * </ul>
     */
    private void initializeDocumentList() {
        // 创建后台任务扫描文档
        Task<List<DocumentInfo>> scanTask = new Task<List<DocumentInfo>>() {
            @Override
            protected List<DocumentInfo> call() throws Exception {
                List<DocumentInfo> documents = new ArrayList<>();
                
                // 从类路径扫描所有 .md 文件
                // 使用 ClassLoader 获取资源，支持 Fat JAR 模式
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                if (classLoader == null) {
                    classLoader = HelpController.class.getClassLoader();
                }
                
                // 尝试获取类路径根目录下的所有资源
                // 注意：在 JAR 文件中，无法直接列出目录内容，所以我们需要使用预定义的文档列表
                // 然后逐个检查资源是否存在
                
                // 使用预定义的文档列表（从 DOCUMENT_ORDER 获取）
                for (String fileName : DOCUMENT_ORDER) {
                    // 尝试多种资源路径格式
                    String[] possiblePaths = {
                        "/" + fileName,           // 类路径根目录
                        fileName,                 // 相对路径
                        "META-INF/resources/" + fileName  // META-INF 资源目录
                    };
                    
                    boolean found = false;
                    for (String resourcePath : possiblePaths) {
                        InputStream resourceStream = classLoader.getResourceAsStream(resourcePath);
                        if (resourceStream != null) {
                            // 资源存在，添加到列表
                            resourceStream.close(); // 立即关闭，稍后会重新打开读取
                            String displayName = beautifyFileName(fileName);
                            documents.add(new DocumentInfo(displayName, fileName));
                            System.out.println("[HelpController] 找到文档资源: " + resourcePath + " (文件: " + fileName + ")");
                            found = true;
                            break;
                        }
                    }
                    
                    if (!found) {
                        System.out.println("[HelpController] 未找到文档资源: " + fileName);
                        // 尝试使用当前类所在的包路径查找（调试用）
                        for (String resourcePath : possiblePaths) {
                            URL resourceUrl = classLoader.getResource(resourcePath);
                            if (resourceUrl != null) {
                                System.out.println("[HelpController] 资源 URL: " + resourceUrl);
                                break;
                            }
                        }
                    }
                }
                
                // 如果预定义列表中没有文档，尝试扫描所有可能的文档
                if (documents.isEmpty()) {
                    System.out.println("[HelpController] 预定义列表中没有找到文档，尝试扫描常见文档...");
                    // 尝试常见的文档名称
                    String[] commonDocs = {
                        "README.md", "AI_CONFIG.md", "AI_SERVICE_USAGE.md",
                        "IP_LOCATION_FEATURE.md", "PROCESS_TRAFFIC_MONITOR.md"
                    };
                    
                    for (String fileName : commonDocs) {
                        if (EXCLUDED_DOCUMENTS.contains(fileName)) {
                            continue;
                        }
                        
                        // 尝试多种路径格式
                        String[] possiblePaths = {
                            "/" + fileName,           // 类路径根目录
                            fileName,                 // 相对路径
                            "META-INF/resources/" + fileName  // META-INF 资源目录
                        };
                        
                        boolean found = false;
                        for (String resourcePath : possiblePaths) {
                            InputStream resourceStream = classLoader.getResourceAsStream(resourcePath);
                            if (resourceStream != null) {
                                resourceStream.close();
                                String displayName = beautifyFileName(fileName);
                                documents.add(new DocumentInfo(displayName, fileName));
                                System.out.println("[HelpController] 找到文档资源: " + resourcePath + " (文件: " + fileName + ")");
                                found = true;
                                break;
                            }
                        }
                        
                        if (!found) {
                            System.out.println("[HelpController] 未找到常见文档: " + fileName);
                        }
                    }
                }
                
                // 按预定义的顺序排序（README.md 始终在第一位）
                documents.sort((a, b) -> {
                    int indexA = DOCUMENT_ORDER.indexOf(a.fileName);
                    int indexB = DOCUMENT_ORDER.indexOf(b.fileName);
                    
                    // 如果都在顺序列表中，按列表顺序排序
                    if (indexA != -1 && indexB != -1) {
                        return Integer.compare(indexA, indexB);
                    }
                    // 如果只有一个在列表中，在列表中的排在前面
                    if (indexA != -1) return -1;
                    if (indexB != -1) return 1;
                    // 如果都不在列表中，按文件名排序
                    return a.displayName.compareToIgnoreCase(b.displayName);
                });
                
                return documents;
            }
        };
        
        // 任务成功完成后的回调
        scanTask.setOnSucceeded(event -> {
            List<DocumentInfo> documents = scanTask.getValue();
            documentFileNames = new ArrayList<>();
            ObservableList<String> displayNames = FXCollections.observableArrayList();
            
            for (DocumentInfo doc : documents) {
                displayNames.add(doc.displayName);
                documentFileNames.add(doc.fileName);
            }
            
            documentListView.setItems(displayNames);
            
            // 如果有文档，默认选择第一个
            if (!displayNames.isEmpty()) {
                documentListView.getSelectionModel().select(0);
            } else {
                // 如果没有找到文档，显示提示信息
                Platform.runLater(() -> {
                    documentTitleLabel.setText("未找到文档");
                    webEngine.loadContent(
                        "<html><body><p style='color: orange;'>未找到任何文档资源。请确保 Markdown 文档已正确打包到 JAR 文件中。</p></body></html>",
                        "text/html"
                    );
                });
            }
        });
        
        // 任务失败后的回调
        scanTask.setOnFailed(event -> {
            Throwable exception = scanTask.getException();
            System.err.println("[HelpController] 扫描文档失败: " + 
                (exception != null ? exception.getMessage() : "未知错误"));
            if (exception != null) {
                exception.printStackTrace();
            }
            
            // 显示错误信息
            Platform.runLater(() -> {
                documentTitleLabel.setText("扫描文档失败");
                webEngine.loadContent(
                    "<html><body><p style='color: red;'>无法扫描文档资源，请检查类路径配置。</p><p>错误信息: " +
                    (exception != null ? exception.getMessage() : "未知错误") + "</p></body></html>",
                    "text/html"
                );
            });
        });
        
        // 在后台线程执行扫描任务
        Thread scanThread = new Thread(scanTask);
        scanThread.setDaemon(true);
        scanThread.start();
    }
    
    /**
     * 需要排除的文档文件名列表（不在帮助中心显示）
     */
    private static final java.util.Set<String> EXCLUDED_DOCUMENTS = new java.util.HashSet<>();
    
    static {
        // 初始化排除列表
        EXCLUDED_DOCUMENTS.add("CODE_STATISTICS.md");        // 代码统计
        EXCLUDED_DOCUMENTS.add("DATABASE_REFACTORING.md");  // 数据库重构
        EXCLUDED_DOCUMENTS.add("MODULE_FIX_SUMMARY.md");    // 模块修复总结
        EXCLUDED_DOCUMENTS.add("MODULE_FIX.md");            // 模块修复
        EXCLUDED_DOCUMENTS.add("TROUBLESHOOTING.md");       // 故障排除
    }
    
    /**
     * 文档文件名到中文名称的映射表
     * 根据文档实际标题进行映射，确保显示名称准确
     */
    private static final java.util.Map<String, String> DOCUMENT_NAME_MAP = new java.util.HashMap<>();
    
    /**
     * 文档显示顺序列表（按此顺序排序，README.md 始终在第一位）
     */
    private static final java.util.List<String> DOCUMENT_ORDER = new java.util.ArrayList<>();
    
    static {
        // 初始化文档名称映射表（仅包含需要在帮助中心显示的文档）
        // 映射关系基于文档的实际标题
        DOCUMENT_NAME_MAP.put("README.md", "NetPulse FX - 使用指南");
        DOCUMENT_NAME_MAP.put("AI_CONFIG.md", "AI 服务配置说明");
        DOCUMENT_NAME_MAP.put("AI_SERVICE_USAGE.md", "AI 流量分析服务使用指南");
        DOCUMENT_NAME_MAP.put("PROMPT_ENGINEERING.md", "AI 提示词工程文档");
        DOCUMENT_NAME_MAP.put("IP_LOCATION_FEATURE.md", "IP 归属地查询功能使用说明");
        DOCUMENT_NAME_MAP.put("PROCESS_TRAFFIC_MONITOR.md", "进程流量监控面板功能说明");
        
        // 初始化文档显示顺序（README.md 始终在第一位）
        DOCUMENT_ORDER.add("README.md");
        DOCUMENT_ORDER.add("AI_CONFIG.md");
        DOCUMENT_ORDER.add("AI_SERVICE_USAGE.md");
        DOCUMENT_ORDER.add("PROMPT_ENGINEERING.md");
        DOCUMENT_ORDER.add("IP_LOCATION_FEATURE.md");
        DOCUMENT_ORDER.add("PROCESS_TRAFFIC_MONITOR.md");
    }
    
    /**
     * 美化文件名
     * 将文件名转换为中文显示名称
     * 
     * <p>转换规则：</p>
     * <ul>
     *   <li>优先使用预定义的中文名称映射</li>
     *   <li>如果没有映射，则使用默认转换规则（去掉扩展名、下划线转空格、首字母大写）</li>
     * </ul>
     * 
     * @param fileName 原始文件名（例如：AI_CONFIG.md）
     * @return 中文显示名称（例如：AI 配置指南）
     */
    private String beautifyFileName(String fileName) {
        // 首先检查是否有预定义的中文名称
        String chineseName = DOCUMENT_NAME_MAP.get(fileName);
        if (chineseName != null) {
            return chineseName;
        }
        
        // 如果没有预定义名称，使用默认转换规则
        // 去掉文件扩展名
        String nameWithoutExt = fileName.replaceAll("\\.md$", "");
        
        // 将下划线和连字符替换为空格
        String withSpaces = nameWithoutExt.replaceAll("[_-]", " ");
        
        // 将每个单词的首字母大写
        String[] words = withSpaces.split("\\s+");
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < words.length; i++) {
            if (words[i].isEmpty()) {
                continue;
            }
            
            String word = words[i];
            
            // 如果单词是全大写且长度 <= 3，可能是缩写词（如 AI、IP、API），保持原样
            if (word.length() <= 3 && word.equals(word.toUpperCase())) {
                // 保持缩写词原样
            } else {
                // 首字母大写，其余小写
                word = word.substring(0, 1).toUpperCase() + 
                       (word.length() > 1 ? word.substring(1).toLowerCase() : "");
            }
            
            if (i > 0) {
                result.append(" ");
            }
            result.append(word);
        }
        
        return result.toString();
    }
    
    /**
     * 加载文档内容
     * 从类路径读取选中的文档文件，转换为 HTML 并在 WebView 中显示
     * 
     * <p>实现说明：</p>
     * <ul>
     *   <li>使用类路径资源读取，支持 Fat JAR 模式</li>
     *   <li>通过 InputStream 读取资源内容</li>
     *   <li>使用 BufferedReader 和 UTF-8 编码转换为字符串</li>
     *   <li>完善的错误处理，确保资源缺失时不会崩溃</li>
     * </ul>
     * 
     * @param displayName 文档的显示名称（用于查找对应的文件名）
     */
    private void loadDocument(String displayName) {
        // 查找对应的文件名
        int index = documentListView.getItems().indexOf(displayName);
        if (index < 0 || index >= documentFileNames.size()) {
            // 显示错误信息
            documentTitleLabel.setText("文档未找到");
            webEngine.loadContent(
                "<html><body><p style='color: red;'>无法找到对应的文档文件。</p></body></html>",
                "text/html"
            );
            return;
        }
        
        String fileName = documentFileNames.get(index);
        
        // 更新标题
        documentTitleLabel.setText(displayName);
        
        // 创建后台任务读取文档
        Task<String> loadTask = new Task<String>() {
            @Override
            protected String call() throws Exception {
                // 从类路径读取资源
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                if (classLoader == null) {
                    classLoader = HelpController.class.getClassLoader();
                }
                
                // 尝试多种资源路径格式
                String[] possiblePaths = {
                    "/" + fileName,           // 类路径根目录
                    fileName,                 // 相对路径
                    "META-INF/resources/" + fileName  // META-INF 资源目录
                };
                
                InputStream resourceStream = null;
                String usedPath = null;
                
                // 尝试每个可能的路径
                for (String resourcePath : possiblePaths) {
                    resourceStream = classLoader.getResourceAsStream(resourcePath);
                    if (resourceStream != null) {
                        usedPath = resourcePath;
                        System.out.println("[HelpController] 成功找到文档资源: " + resourcePath + " (文件: " + fileName + ")");
                        break;
                    }
                }
                
                if (resourceStream == null) {
                    // 所有路径都失败，返回详细错误信息
                    StringBuilder errorMsg = new StringBuilder("文档资源未找到: " + fileName + "\n");
                    errorMsg.append("已尝试的路径：\n");
                    for (String path : possiblePaths) {
                        errorMsg.append("  - ").append(path).append("\n");
                    }
                    errorMsg.append("请检查资源路径是否正确，确保 Markdown 文档已正确打包到 JAR 文件中。");
                    throw new IOException(errorMsg.toString());
                }
                
                // 使用 BufferedReader 和 UTF-8 编码读取内容
                // try-with-resources 会自动关闭流，无需在 finally 中手动关闭
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resourceStream, StandardCharsets.UTF_8))) {
                    
                    StringBuilder content = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line).append("\n");
                    }
                    
                    String markdown = content.toString();
                    
                    if (markdown.trim().isEmpty()) {
                        throw new IOException("文档内容为空: " + fileName);
                    }
                    
                    System.out.println("[HelpController] 成功读取文档: " + fileName + " (路径: " + usedPath + ", 大小: " + markdown.length() + " 字符)");
                    
                    // 转换为 HTML
                    return MarkdownToHtmlConverter.convertToHtml(markdown);
                }
            }
        };
        
        // 任务成功完成后的回调
        loadTask.setOnSucceeded(event -> {
            String html = loadTask.getValue();
            webEngine.loadContent(html, "text/html");
        });
        
        // 任务失败后的回调
        loadTask.setOnFailed(event -> {
            Throwable exception = loadTask.getException();
            String errorMessage = exception != null ? exception.getMessage() : "未知错误";
            
            System.err.println("[HelpController] 加载文档失败: " + errorMessage);
            if (exception != null) {
                exception.printStackTrace();
            }
            
            // 显示友好的错误信息
            String errorHtml = String.format(
                "<html><body style='font-family: \"Microsoft YaHei\", sans-serif; padding: 20px;'>" +
                "<h2 style='color: #dc3545;'>文档加载失败</h2>" +
                "<p style='color: #666;'>无法加载文档：<strong>%s</strong></p>" +
                "<p style='color: #999; font-size: 12px;'>错误详情：%s</p>" +
                "<hr style='margin: 20px 0; border: none; border-top: 1px solid #ddd;'>" +
                "<p style='color: #666; font-size: 12px;'>" +
                "可能的原因：<br>" +
                "1. 文档资源未正确打包到 JAR 文件中<br>" +
                "2. 资源路径配置不正确<br>" +
                "3. 文件编码问题<br>" +
                "4. 类路径配置错误" +
                "</p>" +
                "</body></html>",
                displayName,
                errorMessage
            );
            webEngine.loadContent(errorHtml, "text/html");
        });
        
        // 在后台线程执行加载任务
        Thread loadThread = new Thread(loadTask);
        loadThread.setDaemon(true);
        loadThread.start();
    }
    
    /**
     * 文档信息内部类
     * 用于存储文档的显示名称和文件名（类路径中的资源名称）
     */
    private static class DocumentInfo {
        final String displayName;
        final String fileName;
        
        DocumentInfo(String displayName, String fileName) {
            this.displayName = displayName;
            this.fileName = fileName;
        }
    }
}

