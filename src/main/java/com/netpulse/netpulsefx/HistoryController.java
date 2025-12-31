package com.netpulse.netpulsefx;

import com.netpulse.netpulsefx.model.AIConfig;
import com.netpulse.netpulsefx.model.IPLocationInfo;
import com.netpulse.netpulsefx.service.AIService;
import com.netpulse.netpulsefx.service.DatabaseService;
import com.netpulse.netpulsefx.service.IPLocationService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 历史数据查看窗口控制器
 * 
 * <p>功能说明：</p>
 * <ul>
 *   <li>从 H2 数据库查询流量历史记录</li>
 *   <li>使用 TableView 以表格形式显示历史数据</li>
 *   <li>支持刷新数据功能</li>
 * </ul>
 * 
 * <p>性能考虑：</p>
 * <ul>
 *   <li>数据查询在后台线程执行，不会阻塞 UI</li>
 *   <li>使用 JavaFX 的 TableView 高效显示大量数据</li>
 * </ul>
 */
public class HistoryController {
    
    // ========== FXML 注入的 UI 组件 ==========
    
    /** 历史数据表格 */
    @FXML
    private TableView<TrafficRecordDisplay> historyTable;
    
    /** ID 列 */
    @FXML
    private TableColumn<TrafficRecordDisplay, Long> idColumn;
    
    /** 网卡名称列 */
    @FXML
    private TableColumn<TrafficRecordDisplay, String> ifaceNameColumn;
    
    /** 下行速度列 */
    @FXML
    private TableColumn<TrafficRecordDisplay, Double> downSpeedColumn;
    
    /** 上行速度列 */
    @FXML
    private TableColumn<TrafficRecordDisplay, Double> upSpeedColumn;
    
    /** 源IP地址列 */
    @FXML
    private TableColumn<TrafficRecordDisplay, String> sourceIpColumn;
    
    /** 目标IP地址列 */
    @FXML
    private TableColumn<TrafficRecordDisplay, String> destIpColumn;
    
    /** 进程名称列 */
    @FXML
    private TableColumn<TrafficRecordDisplay, String> processNameColumn;
    
    /** 捕获时间列 */
    @FXML
    private TableColumn<TrafficRecordDisplay, String> captureTimeColumn;
    
    /** 状态标签 */
    @FXML
    private Label statusLabel;
    
    /** 刷新按钮 */
    @FXML
    private Button refreshButton;
    
    /** 关闭按钮 */
    @FXML
    private Button closeButton;
    
    /** IP 输入框 */
    @FXML
    private TextField ipInputField;
    
    /** IP 查询按钮 */
    @FXML
    private Button queryIpButton;
    
    /** IP 归属地信息显示区域 */
    @FXML
    private TextArea ipLocationTextArea;
    
    /** IP 查询状态标签 */
    @FXML
    private Label ipQueryStatusLabel;
    
    /** 主标签页容器 */
    @FXML
    private javafx.scene.control.TabPane mainTabPane;
    
    /** 会话列表表格 */
    @FXML
    private TableView<SessionDisplay> sessionTable;
    
    /** 会话ID列 */
    @FXML
    private TableColumn<SessionDisplay, Integer> sessionIdColumn;
    
    /** 会话网卡名称列 */
    @FXML
    private TableColumn<SessionDisplay, String> sessionIfaceColumn;
    
    /** 会话开始时间列 */
    @FXML
    private TableColumn<SessionDisplay, String> sessionStartTimeColumn;
    
    /** 会话结束时间列 */
    @FXML
    private TableColumn<SessionDisplay, String> sessionEndTimeColumn;
    
    /** 会话持续时间列 */
    @FXML
    private TableColumn<SessionDisplay, Long> sessionDurationColumn;
    
    /** 会话平均下行速度列 */
    @FXML
    private TableColumn<SessionDisplay, Double> sessionAvgDownColumn;
    
    /** 会话平均上行速度列 */
    @FXML
    private TableColumn<SessionDisplay, Double> sessionAvgUpColumn;
    
    /** 会话最大下行速度列 */
    @FXML
    private TableColumn<SessionDisplay, Double> sessionMaxDownColumn;
    
    /** 会话最大上行速度列 */
    @FXML
    private TableColumn<SessionDisplay, Double> sessionMaxUpColumn;
    
    /** 会话记录数列 */
    @FXML
    private TableColumn<SessionDisplay, Integer> sessionRecordCountColumn;
    
    /** 会话详情文本区域 */
    @FXML
    private TextArea sessionDetailTextArea;
    
    /** 查看会话详细记录按钮 */
    @FXML
    private Button viewSessionDetailsButton;
    
    /** 删除会话按钮 */
    @FXML
    private Button deleteSessionButton;
    
    /** AI 诊断按钮 */
    @FXML
    private Button aiDiagnosisButton;
    
    /** AI 诊断结果容器 */
    @FXML
    private VBox aiDiagnosisContainer;
    
    /** AI 诊断结果文本区域 */
    @FXML
    private TextArea aiDiagnosisTextArea;
    
    /** AI 诊断进度指示器 */
    @FXML
    private ProgressIndicator aiDiagnosisProgress;
    
    // ========== 数据模型 ==========
    
    /** 表格数据源 */
    private ObservableList<TrafficRecordDisplay> historyData;
    
    /** 会话列表数据源 */
    private ObservableList<SessionDisplay> sessionData;
    
    /** 数据库服务实例 */
    private DatabaseService databaseService;
    
    /** IP 地理位置查询服务实例 */
    private IPLocationService ipLocationService;
    
    /** AI 服务实例 */
    private AIService aiService;
    
    /** 时间格式化器 */
    private final SimpleDateFormat timeFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    // ========== 初始化方法 ==========
    
    /**
     * FXML 加载后自动调用的初始化方法
     */
    @FXML
    public void initialize() {
        // 初始化数据源
        historyData = FXCollections.observableArrayList();
        historyTable.setItems(historyData);
        
        sessionData = FXCollections.observableArrayList();
        sessionTable.setItems(sessionData);
        
        // 配置详细记录表格列的数据绑定
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        ifaceNameColumn.setCellValueFactory(new PropertyValueFactory<>("ifaceName"));
        downSpeedColumn.setCellValueFactory(new PropertyValueFactory<>("downSpeed"));
        upSpeedColumn.setCellValueFactory(new PropertyValueFactory<>("upSpeed"));
        sourceIpColumn.setCellValueFactory(new PropertyValueFactory<>("sourceIp"));
        destIpColumn.setCellValueFactory(new PropertyValueFactory<>("destIp"));
        processNameColumn.setCellValueFactory(new PropertyValueFactory<>("processName"));
        captureTimeColumn.setCellValueFactory(new PropertyValueFactory<>("captureTime"));
        
        // 设置数字列的格式化显示（保留两位小数）
        downSpeedColumn.setCellFactory(column -> new DecimalTableCell<>(2));
        upSpeedColumn.setCellFactory(column -> new DecimalTableCell<>(2));
        
        // 配置会话列表表格列的数据绑定
        sessionIdColumn.setCellValueFactory(new PropertyValueFactory<>("sessionId"));
        sessionIfaceColumn.setCellValueFactory(new PropertyValueFactory<>("ifaceName"));
        sessionStartTimeColumn.setCellValueFactory(new PropertyValueFactory<>("startTime"));
        sessionEndTimeColumn.setCellValueFactory(new PropertyValueFactory<>("endTime"));
        sessionDurationColumn.setCellValueFactory(new PropertyValueFactory<>("durationSeconds"));
        sessionAvgDownColumn.setCellValueFactory(new PropertyValueFactory<>("avgDownSpeed"));
        sessionAvgUpColumn.setCellValueFactory(new PropertyValueFactory<>("avgUpSpeed"));
        sessionMaxDownColumn.setCellValueFactory(new PropertyValueFactory<>("maxDownSpeed"));
        sessionMaxUpColumn.setCellValueFactory(new PropertyValueFactory<>("maxUpSpeed"));
        sessionRecordCountColumn.setCellValueFactory(new PropertyValueFactory<>("recordCount"));
        
        // 设置会话列表数字列的格式化显示
        sessionAvgDownColumn.setCellFactory(column -> new DecimalTableCell<>(2));
        sessionAvgUpColumn.setCellFactory(column -> new DecimalTableCell<>(2));
        sessionMaxDownColumn.setCellFactory(column -> new DecimalTableCell<>(2));
        sessionMaxUpColumn.setCellFactory(column -> new DecimalTableCell<>(2));
        
        // 获取数据库服务实例
        databaseService = DatabaseService.getInstance();
        
        // 获取 IP 地理位置查询服务实例
        ipLocationService = IPLocationService.getInstance();
        
        // 初始化 AI 服务
        initializeAIService();
        
        // 初始化 AI 诊断区域（默认隐藏）
        if (aiDiagnosisContainer != null) {
            aiDiagnosisContainer.setVisible(false);
            aiDiagnosisContainer.setManaged(false);
        }
        
        // 初始化 IP 归属地显示区域
        ipLocationTextArea.setPromptText("IP 归属地信息将显示在这里...\n\n提示：\n1. 在输入框中输入 IP 地址并点击\"查询\"按钮\n2. 或点击表格中的任意行，系统将尝试从该行数据中提取 IP 地址");
        
        // 初始化会话详情显示区域
        sessionDetailTextArea.setPromptText("选择会话查看详细信息...");
        
        // 设置详细记录表格行选择监听器
        historyTable.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> {
                if (newValue != null) {
                    onTableRowSelected(newValue);
                }
            }
        );
        
        // 设置会话列表表格行选择监听器
        sessionTable.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> {
                if (newValue != null) {
                    onSessionRowSelected(newValue);
                }
                // 更新删除按钮的启用状态
                deleteSessionButton.setDisable(newValue == null);
            }
        );
        
        // 初始化删除按钮状态（默认禁用）
        deleteSessionButton.setDisable(true);
        
        // 初始化时加载会话列表数据
        loadSessionList();
    }
    
    // ========== 事件处理方法 ==========
    
    /**
     * 刷新按钮点击事件
     * 根据当前选中的标签页刷新对应的数据
     */
    @FXML
    protected void onRefreshButtonClick() {
        if (mainTabPane.getSelectionModel().getSelectedIndex() == 0) {
            // 第一个标签页：会话列表
            loadSessionList();
        } else {
            // 第二个标签页：详细记录
            loadHistoryData();
        }
    }
    
    /**
     * 查看会话详细记录按钮点击事件
     */
    @FXML
    protected void onViewSessionDetailsClick() {
        SessionDisplay selectedSession = sessionTable.getSelectionModel().getSelectedItem();
        if (selectedSession != null) {
            // 切换到详细记录标签页
            mainTabPane.getSelectionModel().select(1);
            // 加载该会话的详细记录
            loadSessionDetails(selectedSession.getSessionId());
        }
    }
    
    /**
     * AI 诊断按钮点击事件
     */
    @FXML
    protected void onAIDiagnosisClick() {
        SessionDisplay selectedSession = sessionTable.getSelectionModel().getSelectedItem();
        if (selectedSession == null) {
            showAlert(Alert.AlertType.WARNING, "未选择会话", "请先选择一个监控会话进行 AI 诊断。");
            return;
        }
        
        // 检查 AI 服务是否可用
        if (aiService == null) {
            showAlert(Alert.AlertType.WARNING, "AI 服务未配置", 
                    "AI 服务未初始化。\n\n请检查环境变量配置：\n" +
                    "- AI_API_ENDPOINT\n" +
                    "- AI_API_KEY（如需要）\n" +
                    "- AI_PROVIDER\n" +
                    "- AI_MODEL");
            return;
        }
        
        // 执行 AI 诊断
        performAIDiagnosis(selectedSession.getSessionId());
    }
    
    /**
     * 执行 AI 诊断
     * 
     * @param sessionId 会话 ID
     */
    private void performAIDiagnosis(int sessionId) {
        // 禁用按钮，显示进度指示器
        aiDiagnosisButton.setDisable(true);
        if (aiDiagnosisContainer != null) {
            aiDiagnosisContainer.setVisible(true);
            aiDiagnosisContainer.setManaged(true);
        }
        if (aiDiagnosisProgress != null) {
            aiDiagnosisProgress.setVisible(true);
        }
        if (aiDiagnosisTextArea != null) {
            aiDiagnosisTextArea.setText("正在加载会话数据并生成 AI 诊断报告，请稍候...");
        }
        
        statusLabel.setText("正在执行 AI 诊断...");
        
        // 创建异步任务：先查询会话和记录，然后调用 AI 分析
        Task<String> diagnosisTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                // 第一步：查询会话信息
                updateMessage("正在查询会话信息...");
                List<DatabaseService.MonitoringSession> sessions = databaseService.getAllSessions().get();
                DatabaseService.MonitoringSession session = sessions.stream()
                        .filter(s -> s.getSessionId() == sessionId)
                        .findFirst()
                        .orElse(null);
                
                if (session == null) {
                    throw new Exception("会话不存在: session_id=" + sessionId);
                }
                
                // 第二步：查询该会话的所有记录
                updateMessage("正在查询流量明细记录...");
                List<DatabaseService.TrafficRecord> records = databaseService.getRecordsBySession(sessionId).get();
                
                if (records == null || records.isEmpty()) {
                    throw new Exception("该会话没有流量明细记录，无法进行 AI 诊断。");
                }
                
                // 第三步：调用 AI 服务生成报告
                updateMessage("正在生成 AI 诊断报告...");
                return aiService.generateSessionReport(session, records).get();
            }
        };
        
        // 任务成功完成
        diagnosisTask.setOnSucceeded(e -> {
            try {
                String report = diagnosisTask.getValue();
                Platform.runLater(() -> {
                    if (aiDiagnosisTextArea != null) {
                        aiDiagnosisTextArea.setText(report);
                    }
                    if (aiDiagnosisProgress != null) {
                        aiDiagnosisProgress.setVisible(false);
                    }
                    aiDiagnosisButton.setDisable(false);
                    statusLabel.setText("AI 诊断完成");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    handleAIDiagnosisError("生成诊断报告失败: " + ex.getMessage(), ex);
                });
            }
        });
        
        // 任务失败
        diagnosisTask.setOnFailed(e -> {
            Throwable exception = diagnosisTask.getException();
            Platform.runLater(() -> {
                String errorMsg = exception != null ? exception.getMessage() : "未知错误";
                handleAIDiagnosisError("AI 诊断失败: " + errorMsg, exception);
            });
        });
        
        // 启动任务
        Thread diagnosisThread = new Thread(diagnosisTask);
        diagnosisThread.setDaemon(true);
        diagnosisThread.start();
    }
    
    /**
     * 处理 AI 诊断错误
     */
    private void handleAIDiagnosisError(String message, Throwable exception) {
        if (aiDiagnosisTextArea != null) {
            aiDiagnosisTextArea.setText("**错误**\n\n" + message);
        }
        if (aiDiagnosisProgress != null) {
            aiDiagnosisProgress.setVisible(false);
        }
        aiDiagnosisButton.setDisable(false);
        statusLabel.setText("AI 诊断失败");
        
        // 显示错误对话框
        showAlert(Alert.AlertType.ERROR, "AI 诊断失败", message);
        
        if (exception != null) {
            exception.printStackTrace();
        }
    }
    
    /**
     * 初始化 AI 服务
     * 使用与 MainController 相同的逻辑
     */
    private void initializeAIService() {
        try {
            // 尝试从环境变量读取配置
            String apiProvider = cleanEnvValue(System.getenv("AI_PROVIDER"));
            String apiEndpoint = cleanEnvValue(System.getenv("AI_API_ENDPOINT"));
            String apiKey = cleanEnvValue(System.getenv("AI_API_KEY"));
            String model = cleanEnvValue(System.getenv("AI_MODEL"));
            
            // 如果环境变量已设置，使用环境变量配置
            if (apiEndpoint != null && !apiEndpoint.isEmpty()) {
                String finalProvider = apiProvider != null && !apiProvider.isEmpty() 
                    ? apiProvider : "deepseek";
                String finalModel = model != null && !model.isEmpty() 
                    ? model : (finalProvider.equalsIgnoreCase("deepseek") ? "deepseek-chat" : "gpt-3.5-turbo");
                String finalApiKey = apiKey != null ? apiKey : "";
                
                aiService = new AIService(new AIConfig(finalProvider, apiEndpoint, finalApiKey, finalModel));
                
                System.out.println("[HistoryController] AI 服务已初始化（环境变量配置）");
            } else {
                // 环境变量未设置，根据 provider 使用默认配置
                String finalProvider = (apiProvider != null && !apiProvider.isEmpty()) 
                    ? apiProvider : "ollama";
                
                if ("deepseek".equalsIgnoreCase(finalProvider)) {
                    AIConfig config = AIConfig.defaultDeepSeek();
                    String finalApiKey = (apiKey != null && !apiKey.isEmpty()) ? apiKey : "";
                    String finalModel = (model != null && !model.isEmpty()) ? model : config.getModel();
                    aiService = new AIService(new AIConfig(
                        config.getProvider(),
                        config.getApiEndpoint(),
                        finalApiKey,
                        finalModel
                    ));
                } else if ("openai".equalsIgnoreCase(finalProvider)) {
                    AIConfig config = AIConfig.defaultOpenAI();
                    String finalApiKey = (apiKey != null && !apiKey.isEmpty()) ? apiKey : "";
                    String finalModel = (model != null && !model.isEmpty()) ? model : config.getModel();
                    aiService = new AIService(new AIConfig(
                        config.getProvider(),
                        config.getApiEndpoint(),
                        finalApiKey,
                        finalModel
                    ));
                } else {
                    // 默认使用 Ollama（本地）
                    AIConfig config = AIConfig.defaultOllama();
                    String finalModel = (model != null && !model.isEmpty()) ? model : "llama2";
                    aiService = new AIService(new AIConfig(
                        config.getProvider(),
                        config.getApiEndpoint(),
                        config.getApiKey(),
                        finalModel
                    ));
                }
                
                System.out.println("[HistoryController] AI 服务已初始化（默认配置）");
            }
        } catch (Exception e) {
            System.err.println("[HistoryController] AI 服务初始化失败: " + e.getMessage());
            e.printStackTrace();
            aiService = null;
        }
    }
    
    /**
     * 清理环境变量值（去除引号和空白字符）
     */
    private String cleanEnvValue(String value) {
        if (value == null) {
            return null;
        }
        value = value.trim();
        // 去除首尾的引号（单引号或双引号）
        if ((value.startsWith("\"") && value.endsWith("\"")) ||
            (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        return value.trim();
    }
    
    /**
     * 删除会话按钮点击事件
     */
    @FXML
    protected void onDeleteSessionClick() {
        SessionDisplay selectedSession = sessionTable.getSelectionModel().getSelectedItem();
        if (selectedSession == null) {
            showAlert(Alert.AlertType.WARNING, "未选择会话", "请先选择一个要删除的监控会话。");
            return;
        }
        
        // 显示确认对话框
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("确认删除");
        confirmAlert.setHeaderText("删除监控会话");
        confirmAlert.setContentText(
            String.format(
                "确定要删除会话 #%d 吗？\n\n" +
                "网卡: %s\n" +
                "开始时间: %s\n" +
                "记录数: %d 条\n\n" +
                "注意：删除会话将同时删除所有关联的明细记录，此操作不可恢复！",
                selectedSession.getSessionId(),
                selectedSession.getIfaceName(),
                selectedSession.getStartTime(),
                selectedSession.getRecordCount()
            )
        );
        
        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                deleteSession(selectedSession.getSessionId());
            }
        });
    }
    
    /**
     * 删除指定的监控会话
     * 
     * @param sessionId 会话 ID
     */
    private void deleteSession(int sessionId) {
        // 禁用删除按钮，防止重复点击
        deleteSessionButton.setDisable(true);
        statusLabel.setText("正在删除会话...");
        
        databaseService.deleteSession(sessionId)
            .thenAccept(success -> {
                Platform.runLater(() -> {
                    if (success) {
                        // 删除成功，从列表中移除
                        sessionData.removeIf(session -> session.getSessionId() == sessionId);
                        statusLabel.setText(String.format("会话 #%d 已删除", sessionId));
                        
                        // 清空会话详情显示
                        sessionDetailTextArea.setText("");
                        
                        // 如果当前查看的是该会话的详细记录，清空详细记录表格
                        if (mainTabPane.getSelectionModel().getSelectedIndex() == 1) {
                            historyData.clear();
                            statusLabel.setText(String.format("会话 #%d 已删除，详细记录已清空", sessionId));
                        }
                    } else {
                        statusLabel.setText(String.format("删除失败：会话 #%d 不存在", sessionId));
                        showAlert(Alert.AlertType.ERROR, "删除失败", 
                                String.format("会话 #%d 不存在或已被删除。", sessionId));
                    }
                    
                    // 恢复删除按钮状态（根据当前选择）
                    SessionDisplay selected = sessionTable.getSelectionModel().getSelectedItem();
                    deleteSessionButton.setDisable(selected == null);
                });
            })
            .exceptionally(e -> {
                Platform.runLater(() -> {
                    statusLabel.setText("删除失败: " + e.getMessage());
                    showAlert(Alert.AlertType.ERROR, "删除失败", 
                            "删除会话时发生错误：\n" + e.getMessage());
                    
                    // 恢复删除按钮状态
                    SessionDisplay selected = sessionTable.getSelectionModel().getSelectedItem();
                    deleteSessionButton.setDisable(selected == null);
                });
                return null;
            });
    }
    
    /**
     * 显示提示对话框
     * 
     * @param alertType 对话框类型
     * @param title 标题
     * @param message 消息内容
     */
    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * 关闭按钮点击事件
     * 关闭历史数据查看窗口
     */
    @FXML
    protected void onCloseButtonClick() {
        // 获取当前窗口的 Stage 并关闭
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }
    
    /**
     * IP 查询按钮点击事件
     * 查询输入框中 IP 地址的地理位置信息
     */
    @FXML
    protected void onQueryIpButtonClick() {
        String ip = ipInputField.getText();
        if (ip == null || ip.trim().isEmpty()) {
            ipQueryStatusLabel.setText("请输入 IP 地址");
            ipLocationTextArea.setText("");
            return;
        }
        
        queryIPLocation(ip.trim());
    }
    
    /**
     * 表格行选择事件处理
     * 当用户点击表格行时调用
     * 
     * @param selectedRecord 选中的记录
     */
    private void onTableRowSelected(TrafficRecordDisplay selectedRecord) {
        // 优先使用源IP地址，如果没有则使用目标IP地址
        String ipToQuery = selectedRecord.getSourceIp();
        if (ipToQuery == null || ipToQuery.trim().isEmpty()) {
            ipToQuery = selectedRecord.getDestIp();
        }
        
        // 如果找到IP地址，自动查询归属地
        if (ipToQuery != null && !ipToQuery.trim().isEmpty()) {
            ipInputField.setText(ipToQuery);
            queryIPLocation(ipToQuery);
        } else {
            // 如果没有IP地址，清空输入框和显示区域
            ipInputField.setText("");
            ipLocationTextArea.setText("");
            ipQueryStatusLabel.setText("该记录没有IP地址信息");
        }
    }
    
    /**
     * 查询 IP 地理位置信息
     * 
     * @param ip IP 地址
     */
    private void queryIPLocation(String ip) {
        // 禁用查询按钮，防止重复点击
        queryIpButton.setDisable(true);
        ipQueryStatusLabel.setText("正在查询 IP: " + ip + "...");
        ipLocationTextArea.setText("正在查询，请稍候...");
        
        // 使用异步查询，避免阻塞 UI 线程
        ipLocationService.queryLocationAsync(ip)
            .thenAccept(locationInfo -> {
                // 在主线程中更新 UI
                Platform.runLater(() -> {
                    if (locationInfo.isSuccess()) {
                        // 查询成功，格式化显示信息
                        StringBuilder info = new StringBuilder();
                        info.append("IP 地址: ").append(locationInfo.getIp()).append("\n\n");
                        info.append("地理位置信息:\n");
                        info.append("  国家: ").append(locationInfo.getCountry()).append("\n");
                        info.append("  地区: ").append(locationInfo.getRegion()).append("\n");
                        info.append("  城市: ").append(locationInfo.getCity()).append("\n");
                        info.append("  国家代码: ").append(locationInfo.getCountryCode()).append("\n");
                        info.append("  ISP: ").append(locationInfo.getIsp()).append("\n\n");
                        info.append("完整位置: ").append(locationInfo.getFormattedLocation());
                        
                        ipLocationTextArea.setText(info.toString());
                        ipQueryStatusLabel.setText("查询完成 - " + locationInfo.getShortLocation());
                    } else {
                        // 查询失败
                        String errorMsg = locationInfo.getErrorMessage();
                        String detailedMessage;
                        
                        if (errorMsg != null && errorMsg.contains("私有IP")) {
                            detailedMessage = "无法查询 IP 地理位置信息\n\n" +
                                "原因：这是一个私有/本地 IP 地址\n\n" +
                                "私有 IP 地址包括：\n" +
                                "• 127.0.0.1 (本地回环)\n" +
                                "• 192.168.x.x (局域网)\n" +
                                "• 10.x.x.x (私有网络)\n" +
                                "• 172.16-31.x.x (私有网络)\n\n" +
                                "提示：只有公网 IP 地址才能查询地理位置信息。\n" +
                                "请尝试查询表格中其他记录的公网 IP 地址。";
                        } else {
                            detailedMessage = "无法查询 IP 地理位置信息\n\n" +
                                "可能的原因：\n" +
                                "1. IP 地址格式无效或为空\n" +
                                "2. 这是私有/本地 IP 地址（无法查询地理位置）\n" +
                                "3. 网络连接异常\n" +
                                "4. API 服务暂时不可用\n" +
                                "5. 达到 API 频率限制（每分钟45次）\n\n" +
                                "错误信息: " + (errorMsg != null ? errorMsg : "未知错误");
                        }
                        
                        ipLocationTextArea.setText(detailedMessage);
                        ipQueryStatusLabel.setText("查询失败 - " + (errorMsg != null ? errorMsg : "未知原因"));
                    }
                    
                    // 恢复按钮状态
                    queryIpButton.setDisable(false);
                });
            })
            .exceptionally(throwable -> {
                // 处理异常
                Platform.runLater(() -> {
                    ipLocationTextArea.setText("查询过程中发生异常: " + 
                        (throwable.getMessage() != null ? throwable.getMessage() : "未知错误"));
                    ipQueryStatusLabel.setText("查询异常");
                    queryIpButton.setDisable(false);
                });
                return null;
            });
    }
    
    // ========== 核心业务方法 ==========
    
    /**
     * 从数据库加载会话列表
     */
    private void loadSessionList() {
        refreshButton.setDisable(true);
        statusLabel.setText("正在加载会话列表...");
        
        Task<List<DatabaseService.MonitoringSession>> queryTask = new Task<>() {
            @Override
            protected List<DatabaseService.MonitoringSession> call() throws Exception {
                return databaseService.getAllSessions().get();
            }
            
            @Override
            protected void succeeded() {
                try {
                    List<DatabaseService.MonitoringSession> sessions = getValue();
                    
                    Platform.runLater(() -> {
                        sessionData.clear();
                        
                        for (DatabaseService.MonitoringSession session : sessions) {
                            SessionDisplay display = new SessionDisplay(
                                session.getSessionId(),
                                session.getIfaceName(),
                                formatTimestamp(session.getStartTime()),
                                formatTimestamp(session.getEndTime()),
                                session.getDurationSeconds(),
                                session.getAvgDownSpeed(),
                                session.getAvgUpSpeed(),
                                session.getMaxDownSpeed(),
                                session.getMaxUpSpeed(),
                                session.getRecordCount()
                            );
                            sessionData.add(display);
                        }
                        
                        statusLabel.setText(String.format("共加载 %d 个会话", sessions.size()));
                        refreshButton.setDisable(false);
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        statusLabel.setText("加载会话列表失败: " + e.getMessage());
                        refreshButton.setDisable(false);
                    });
                }
            }
            
            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    Throwable exception = getException();
                    statusLabel.setText("加载会话列表失败: " + 
                        (exception != null ? exception.getMessage() : "未知错误"));
                    refreshButton.setDisable(false);
                });
            }
        };
        
        Thread queryThread = new Thread(queryTask);
        queryThread.setDaemon(true);
        queryThread.start();
    }
    
    /**
     * 从数据库加载指定会话的详细记录
     */
    private void loadSessionDetails(int sessionId) {
        refreshButton.setDisable(true);
        statusLabel.setText("正在加载会话详细记录...");
        
        Task<List<DatabaseService.TrafficRecord>> queryTask = new Task<>() {
            @Override
            protected List<DatabaseService.TrafficRecord> call() throws Exception {
                return databaseService.getRecordsBySession(sessionId).get();
            }
            
            @Override
            protected void succeeded() {
                try {
                    List<DatabaseService.TrafficRecord> records = getValue();
                    
                    Platform.runLater(() -> {
                        historyData.clear();
                        
                        for (DatabaseService.TrafficRecord record : records) {
                            TrafficRecordDisplay display = new TrafficRecordDisplay(
                                record.getId(),
                                record.getIfaceName(),
                                record.getDownSpeed(),
                                record.getUpSpeed(),
                                record.getSourceIp(),
                                record.getDestIp(),
                                record.getProcessName(),
                                formatTimestamp(record.getCaptureTime())
                            );
                            historyData.add(display);
                        }
                        
                        statusLabel.setText(String.format("会话 #%d 共 %d 条记录", sessionId, records.size()));
                        refreshButton.setDisable(false);
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        statusLabel.setText("加载详细记录失败: " + e.getMessage());
                        refreshButton.setDisable(false);
                    });
                }
            }
            
            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    Throwable exception = getException();
                    statusLabel.setText("加载详细记录失败: " + 
                        (exception != null ? exception.getMessage() : "未知错误"));
                    refreshButton.setDisable(false);
                });
            }
        };
        
        Thread queryThread = new Thread(queryTask);
        queryThread.setDaemon(true);
        queryThread.start();
    }
    
    /**
     * 会话行选择事件处理
     */
    private void onSessionRowSelected(SessionDisplay selectedSession) {
        StringBuilder detail = new StringBuilder();
        detail.append("会话 ID: ").append(selectedSession.getSessionId()).append("\n");
        detail.append("网卡名称: ").append(selectedSession.getIfaceName()).append("\n");
        detail.append("开始时间: ").append(selectedSession.getStartTime()).append("\n");
        detail.append("结束时间: ").append(selectedSession.getEndTime() != null && !selectedSession.getEndTime().isEmpty() 
            ? selectedSession.getEndTime() : "进行中").append("\n");
        detail.append("持续时间: ").append(selectedSession.getDurationSeconds()).append(" 秒\n");
        detail.append("平均下行速度: ").append(String.format("%.2f", selectedSession.getAvgDownSpeed())).append(" KB/s\n");
        detail.append("平均上行速度: ").append(String.format("%.2f", selectedSession.getAvgUpSpeed())).append(" KB/s\n");
        detail.append("最大下行速度: ").append(String.format("%.2f", selectedSession.getMaxDownSpeed())).append(" KB/s\n");
        detail.append("最大上行速度: ").append(String.format("%.2f", selectedSession.getMaxUpSpeed())).append(" KB/s\n");
        detail.append("记录数量: ").append(selectedSession.getRecordCount()).append(" 条\n");
        
        sessionDetailTextArea.setText(detail.toString());
    }
    
    /**
     * 从数据库加载历史数据
     * 
     * <p>执行流程：</p>
     * <ol>
     *   <li>在后台线程中异步查询数据库</li>
     *   <li>将查询结果转换为显示对象</li>
     *   <li>在主线程中更新表格数据</li>
     * </ol>
     */
    private void loadHistoryData() {
        // 禁用刷新按钮，防止重复点击
        refreshButton.setDisable(true);
        statusLabel.setText("正在加载数据...");
        
        // 创建后台任务查询数据
        Task<List<DatabaseService.TrafficRecord>> queryTask = new Task<>() {
            @Override
            protected List<DatabaseService.TrafficRecord> call() throws Exception {
                // 在后台线程中查询所有历史记录
                return databaseService.queryAllTrafficHistoryAsync().get();
            }
            
            @Override
            protected void succeeded() {
                // 查询成功后的处理
                try {
                    List<DatabaseService.TrafficRecord> records = getValue();
                    
                    // 在主线程中更新 UI
                    Platform.runLater(() -> {
                        // 清空旧数据
                        historyData.clear();
                        
                        // 转换并添加数据
                        for (DatabaseService.TrafficRecord record : records) {
                            TrafficRecordDisplay display = new TrafficRecordDisplay(
                                record.getId(),
                                record.getIfaceName(),
                                record.getDownSpeed(),
                                record.getUpSpeed(),
                                record.getSourceIp(),
                                record.getDestIp(),
                                record.getProcessName(),
                                formatTimestamp(record.getCaptureTime())
                            );
                            historyData.add(display);
                        }
                        
                        // 更新状态标签
                        statusLabel.setText(String.format("共加载 %d 条记录", records.size()));
                        
                        // 恢复按钮状态
                        refreshButton.setDisable(false);
                    });
                    
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        statusLabel.setText("加载数据失败: " + e.getMessage());
                        refreshButton.setDisable(false);
                    });
                }
            }
            
            @Override
            protected void failed() {
                // 查询失败后的处理
                Platform.runLater(() -> {
                    Throwable exception = getException();
                    statusLabel.setText("加载数据失败: " + 
                        (exception != null ? exception.getMessage() : "未知错误"));
                    refreshButton.setDisable(false);
                });
            }
        };
        
        // 启动后台任务
        Thread queryThread = new Thread(queryTask);
        queryThread.setDaemon(true);
        queryThread.start();
    }
    
    /**
     * 格式化时间戳为字符串
     * 
     * @param timestamp 时间戳
     * @return 格式化后的时间字符串
     */
    private String formatTimestamp(java.sql.Timestamp timestamp) {
        if (timestamp == null) {
            return "";
        }
        return timeFormatter.format(new Date(timestamp.getTime()));
    }
    
    // ========== 内部类 ==========
    
    /**
     * 流量记录显示对象
     * 用于 TableView 的数据绑定
     */
    public static class TrafficRecordDisplay {
        private final Long id;
        private final String ifaceName;
        private final Double downSpeed;
        private final Double upSpeed;
        private final String sourceIp;
        private final String destIp;
        private final String processName;
        private final String captureTime;
        
        public TrafficRecordDisplay(Long id, String ifaceName, Double downSpeed, 
                                   Double upSpeed, String sourceIp, String destIp, 
                                   String processName, String captureTime) {
            this.id = id;
            this.ifaceName = ifaceName;
            this.downSpeed = downSpeed;
            this.upSpeed = upSpeed;
            this.sourceIp = sourceIp;
            this.destIp = destIp;
            this.processName = processName;
            this.captureTime = captureTime;
        }
        
        // Getter 方法（PropertyValueFactory 需要使用这些方法）
        public Long getId() { return id; }
        public String getIfaceName() { return ifaceName; }
        public Double getDownSpeed() { return downSpeed; }
        public Double getUpSpeed() { return upSpeed; }
        public String getSourceIp() { return sourceIp != null ? sourceIp : ""; }
        public String getDestIp() { return destIp != null ? destIp : ""; }
        public String getProcessName() { return processName != null ? processName : "未知进程"; }
        public String getCaptureTime() { return captureTime; }
    }
    
    /**
     * 会话显示对象
     * 用于 TableView 的数据绑定
     */
    public static class SessionDisplay {
        private final Integer sessionId;
        private final String ifaceName;
        private final String startTime;
        private final String endTime;
        private final Long durationSeconds;
        private final Double avgDownSpeed;
        private final Double avgUpSpeed;
        private final Double maxDownSpeed;
        private final Double maxUpSpeed;
        private final Integer recordCount;
        
        public SessionDisplay(Integer sessionId, String ifaceName, String startTime, String endTime,
                            Long durationSeconds, Double avgDownSpeed, Double avgUpSpeed,
                            Double maxDownSpeed, Double maxUpSpeed, Integer recordCount) {
            this.sessionId = sessionId;
            this.ifaceName = ifaceName;
            this.startTime = startTime;
            this.endTime = endTime;
            this.durationSeconds = durationSeconds;
            this.avgDownSpeed = avgDownSpeed;
            this.avgUpSpeed = avgUpSpeed;
            this.maxDownSpeed = maxDownSpeed;
            this.maxUpSpeed = maxUpSpeed;
            this.recordCount = recordCount;
        }
        
        // Getter 方法
        public Integer getSessionId() { return sessionId; }
        public String getIfaceName() { return ifaceName; }
        public String getStartTime() { return startTime; }
        public String getEndTime() { return endTime; }
        public Long getDurationSeconds() { return durationSeconds; }
        public Double getAvgDownSpeed() { return avgDownSpeed; }
        public Double getAvgUpSpeed() { return avgUpSpeed; }
        public Double getMaxDownSpeed() { return maxDownSpeed; }
        public Double getMaxUpSpeed() { return maxUpSpeed; }
        public Integer getRecordCount() { return recordCount; }
    }
    
    /**
     * 小数格式化表格单元格
     * 用于格式化显示带小数的数值（如流量速度）
     */
    private static class DecimalTableCell<T> extends javafx.scene.control.TableCell<T, Double> {
        private final int decimalPlaces;
        
        public DecimalTableCell(int decimalPlaces) {
            this.decimalPlaces = decimalPlaces;
        }
        
        @Override
        protected void updateItem(Double item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null) {
                setText(null);
            } else {
                // 格式化显示，保留指定小数位数
                String format = "%." + decimalPlaces + "f";
                setText(String.format(format, item));
            }
        }
    }
}

