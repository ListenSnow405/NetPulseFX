package com.netpulse.netpulsefx;

import com.netpulse.netpulsefx.service.DatabaseService;
import com.netpulse.netpulsefx.service.ExportService;
import com.netpulse.netpulsefx.util.MarkdownToHtmlConverter;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;

/**
 * AI 分析报告独立窗口
 * 
 * <p>功能说明：</p>
 * <ul>
 *   <li>独立的 Stage 窗口，使用 BorderPane 布局</li>
 *   <li>中心区域使用 WebView 渲染 Markdown 格式的 AI 报告</li>
 *   <li>工具栏提供"导出 PDF"和"复制全文"功能</li>
 *   <li>标题栏动态显示模型名称和时间戳</li>
 * </ul>
 * 
 * @author NetPulseFX Team
 */
public class AIReportStage {
    
    /** 主窗口 Stage */
    private final Stage stage;
    
    /** 内容 WebView */
    private final WebView webView;
    
    /** 工具栏容器 */
    private final HBox toolbar;
    
    /** 导出 PDF 按钮 */
    private final Button exportPdfButton;
    
    /** 复制全文按钮 */
    private final Button copyTextButton;
    
    /** 当前显示的 Markdown 内容 */
    private String currentMarkdownContent;
    
    /** 当前会话对象（用于 PDF 导出） */
    private DatabaseService.MonitoringSession currentSession;
    
    /** 当前模型名称 */
    private String currentModelName;
    
    /** 时间格式化器 */
    private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 构造函数
     * 创建并初始化 AI 报告窗口
     */
    public AIReportStage() {
        // 创建主窗口
        stage = new Stage();
        stage.setTitle("AI 诊断报告");
        stage.setMinWidth(800);
        stage.setMinHeight(600);
        
        // 创建 BorderPane 布局
        BorderPane root = new BorderPane();
        
        // 创建工具栏
        toolbar = new HBox(10);
        toolbar.setPadding(new Insets(10));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle("-fx-background-color: #f5f5f5;");
        
        // 创建按钮并设置样式
        exportPdfButton = new Button("导出 PDF");
        exportPdfButton.setPrefWidth(120);
        exportPdfButton.setPrefHeight(35);
        exportPdfButton.setStyle(
            "-fx-background-color: #4CAF50; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 13px; " +
            "-fx-font-weight: bold; " +
            "-fx-background-radius: 5px; " +
            "-fx-border-radius: 5px; " +
            "-fx-cursor: hand;"
        );
        exportPdfButton.setOnAction(e -> onExportPdfClick());
        // 添加悬停效果
        exportPdfButton.setOnMouseEntered(e -> exportPdfButton.setStyle(
            "-fx-background-color: #45a049; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 13px; " +
            "-fx-font-weight: bold; " +
            "-fx-background-radius: 5px; " +
            "-fx-border-radius: 5px; " +
            "-fx-cursor: hand;"
        ));
        exportPdfButton.setOnMouseExited(e -> exportPdfButton.setStyle(
            "-fx-background-color: #4CAF50; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 13px; " +
            "-fx-font-weight: bold; " +
            "-fx-background-radius: 5px; " +
            "-fx-border-radius: 5px; " +
            "-fx-cursor: hand;"
        ));
        
        copyTextButton = new Button("复制全文");
        copyTextButton.setPrefWidth(120);
        copyTextButton.setPrefHeight(35);
        copyTextButton.setStyle(
            "-fx-background-color: #2196F3; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 13px; " +
            "-fx-font-weight: bold; " +
            "-fx-background-radius: 5px; " +
            "-fx-border-radius: 5px; " +
            "-fx-cursor: hand;"
        );
        copyTextButton.setOnAction(e -> onCopyTextClick());
        // 添加悬停效果
        copyTextButton.setOnMouseEntered(e -> copyTextButton.setStyle(
            "-fx-background-color: #1976D2; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 13px; " +
            "-fx-font-weight: bold; " +
            "-fx-background-radius: 5px; " +
            "-fx-border-radius: 5px; " +
            "-fx-cursor: hand;"
        ));
        copyTextButton.setOnMouseExited(e -> copyTextButton.setStyle(
            "-fx-background-color: #2196F3; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 13px; " +
            "-fx-font-weight: bold; " +
            "-fx-background-radius: 5px; " +
            "-fx-border-radius: 5px; " +
            "-fx-cursor: hand;"
        ));
        
        toolbar.getChildren().addAll(exportPdfButton, copyTextButton);
        
        // 创建 WebView
        webView = new WebView();
        
        // 设置布局
        root.setTop(toolbar);
        root.setCenter(webView);
        
        // 创建场景
        Scene scene = new Scene(root, 1000, 700);
        stage.setScene(scene);
    }
    
    /**
     * 显示报告窗口
     * 
     * @param markdownContent Markdown 格式的报告内容
     * @param modelName 模型名称
     * @param session 会话对象（可选，用于 PDF 导出）
     */
    public void showReport(String markdownContent, String modelName, DatabaseService.MonitoringSession session) {
        this.currentMarkdownContent = markdownContent;
        this.currentModelName = modelName != null ? modelName : "未知模型";
        this.currentSession = session;
        
        // 更新标题栏
        String timestamp = TIMESTAMP_FORMAT.format(new Date());
        stage.setTitle(String.format("AI 诊断报告 - %s - %s", currentModelName, timestamp));
        
        // 转换 Markdown 为 HTML 并加载到 WebView
        String htmlContent = MarkdownToHtmlConverter.convertToHtml(markdownContent);
        webView.getEngine().loadContent(htmlContent);
        
        // 显示窗口（非模态）
        stage.show();
        stage.toFront(); // 将窗口置于前台
    }
    
    /**
     * 导出 PDF 按钮点击事件
     */
    private void onExportPdfClick() {
        if (currentMarkdownContent == null || currentMarkdownContent.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "导出失败", "当前没有可导出的报告内容。");
            return;
        }
        
        if (currentSession == null) {
            showAlert(Alert.AlertType.WARNING, "导出失败", "缺少会话信息，无法导出 PDF。");
            return;
        }
        
        // 打开文件选择对话框
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("导出 AI 诊断报告为 PDF");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PDF 文件", "*.pdf")
        );
        
        // 设置默认文件名
        String defaultFileName = String.format("AI诊断报告_%s_%s.pdf",
            currentModelName.replaceAll("[^a-zA-Z0-9]", "_"),
            new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date())
        );
        fileChooser.setInitialFileName(defaultFileName);
        
        File file = fileChooser.showSaveDialog(stage);
        if (file == null) {
            return; // 用户取消了选择
        }
        
        // 在后台线程执行导出任务
        Task<Void> exportTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("正在导出 PDF...");
                ExportService.exportAIReportToPDF(currentSession, currentMarkdownContent, file);
                return null;
            }
        };
        
        // 显示进度指示器
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setProgress(-1); // 不确定进度
        
        StackPane progressPane = new StackPane(progressIndicator);
        progressPane.setPrefSize(200, 200);
        progressPane.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5);");
        
        Stage progressStage = new Stage();
        progressStage.initOwner(stage);
        progressStage.setScene(new Scene(progressPane));
        progressStage.setTitle("导出中...");
        progressStage.setWidth(200);
        progressStage.setHeight(200);
        progressStage.centerOnScreen();
        progressStage.show();
        
        // 任务完成回调
        exportTask.setOnSucceeded(e -> {
            progressStage.close();
            showAlert(Alert.AlertType.INFORMATION, "导出成功", 
                String.format("PDF 文件已成功导出到：\n%s", file.getAbsolutePath()));
        });
        
        // 任务失败回调
        exportTask.setOnFailed(e -> {
            progressStage.close();
            Throwable exception = exportTask.getException();
            String errorMessage = exception != null ? exception.getMessage() : "未知错误";
            showAlert(Alert.AlertType.ERROR, "导出失败", 
                String.format("导出 PDF 时发生错误：\n%s", errorMessage));
            if (exception != null) {
                exception.printStackTrace();
            }
        });
        
        // 启动导出任务
        Thread exportThread = new Thread(exportTask);
        exportThread.setDaemon(true);
        exportThread.start();
    }
    
    /**
     * 复制全文按钮点击事件
     */
    private void onCopyTextClick() {
        if (currentMarkdownContent == null || currentMarkdownContent.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "复制失败", "当前没有可复制的内容。");
            return;
        }
        
        // 将 Markdown 内容复制到剪贴板
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(currentMarkdownContent);
        clipboard.setContent(content);
        
        showAlert(Alert.AlertType.INFORMATION, "复制成功", "报告内容已复制到剪贴板。");
    }
    
    /**
     * 显示提示对话框
     */
    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(alertType);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    /**
     * 获取窗口 Stage（用于外部控制）
     */
    public Stage getStage() {
        return stage;
    }
    
    /**
     * 关闭窗口
     */
    public void close() {
        stage.close();
    }
}

