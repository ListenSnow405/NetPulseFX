package com.netpulse.netpulsefx;

import com.netpulse.netpulsefx.exception.NetworkInterfaceException;
import com.netpulse.netpulsefx.model.AIConfig;
import com.netpulse.netpulsefx.model.ProcessTrafficModel;
import com.netpulse.netpulsefx.model.TrafficData;
import com.netpulse.netpulsefx.service.AIService;
import com.netpulse.netpulsefx.service.DatabaseService;
import com.netpulse.netpulsefx.service.NetworkInterfaceService;
import com.netpulse.netpulsefx.service.ProcessContextService;
import com.netpulse.netpulsefx.task.TrafficMonitorTask;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Region;
import javafx.animation.TranslateTransition;
import javafx.util.Duration;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.event.ActionEvent;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Service;
import javafx.concurrent.WorkerStateEvent;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.stage.Stage;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapAddress;
import org.pcap4j.core.BpfProgram.BpfCompileMode;

import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 主界面控制器类
 * 负责处理网卡列表的显示和用户交互
 * 
 * 数据流向：
 * NetworkInterfaceService (Service层) 
 *   -> getAllInterfaces() 返回 List<PcapNetworkInterface>
 *   -> getInterfaceInfo() 转换为 NetworkInterfaceInfo
 *   -> ObservableList<String> (View层数据源)
 *   -> ListView<String> (UI组件显示)
 */
public class MainController {
    
    // ========== FXML 注入的 UI 组件 ==========
    
    /** 标题标签 */
    @FXML
    private Label titleLabel;
    
    /** 网卡下拉框：显示所有可用的网络接口 */
    @FXML
    private ComboBox<PcapNetworkInterface> networkInterfaceComboBox;
    
    /** BPF 过滤表达式输入框 */
    @FXML
    private TextField bpfFilterTextField;
    
    /** BPF 预设菜单按钮 */
    @FXML
    private MenuButton bpfPresetMenuButton;
    
    /** 帮助按钮 */
    @FXML
    private Button helpButton;
    
    /** 查看历史数据按钮 */
    @FXML
    private Button viewHistoryButton;
    
    /** 刷新网卡按钮 */
    @FXML
    private Button refreshNICButton;
    
    /** 开始监控按钮 */
    @FXML
    private Button startMonitorButton;
    
    /** 停止监控按钮 */
    @FXML
    private Button stopMonitorButton;
    
    /** 实时流量波形图 */
    @FXML
    private LineChart<String, Number> trafficChart;
    
    /** 进程流量表格 */
    @FXML
    private TableView<ProcessTrafficModel> processTrafficTable;
    
    /** 进程名称列 */
    @FXML
    private TableColumn<ProcessTrafficModel, String> processNameColumn;
    
    /** 进程 PID 列 */
    @FXML
    private TableColumn<ProcessTrafficModel, Integer> processPidColumn;
    
    /** 下载速度列 */
    @FXML
    private TableColumn<ProcessTrafficModel, Double> processDownloadSpeedColumn;
    
    /** 上传速度列 */
    @FXML
    private TableColumn<ProcessTrafficModel, Double> processUploadSpeedColumn;
    
    /** 概览看板：实时下行速度标签 */
    @FXML
    private Label realtimeDownLabel;
    
    /** 概览看板：实时上行速度标签 */
    @FXML
    private Label realtimeUpLabel;
    
    /** 概览看板：峰值速度标签 */
    @FXML
    private Label peakSpeedLabel;
    
    /** 概览看板：总流量标签 */
    @FXML
    private Label totalTrafficLabel;
    
    /** 状态栏：运行时间标签 */
    @FXML
    private Label uptimeLabel;
    
    /** 状态栏：AI 状态标签 */
    @FXML
    private Label aiStatusLabel;
    
    /** 状态栏：数据库状态标签 */
    @FXML
    private Label dbStatusLabel;
    
    // ========== 数据模型 ==========
    
    /** 
     * 网卡列表的数据源
     * 使用 ObservableList 实现数据与视图的自动绑定
     * 当列表数据发生变化时，ComboBox 会自动更新显示
     */
    private ObservableList<PcapNetworkInterface> networkInterfaceList;
    
    /** 网卡服务对象：负责获取网卡信息 */
    private NetworkInterfaceService networkInterfaceService;
    
    /** 数据库服务对象：负责流量数据的持久化存储 */
    private DatabaseService databaseService;
    
    /** 进程上下文服务：负责识别数据包对应的进程 */
    private ProcessContextService processContextService;
    
    /** AI 服务对象：负责流量数据的智能分析 */
    private AIService aiService;
    
    /** AI 提供商名称（用于状态显示） */
    private String aiProviderName;
    
    /** 存储实际的网卡对象列表，用于后续的监控操作 */
    private List<PcapNetworkInterface> pcapNetworkInterfaces;
    
    /** 当前正在监控的网卡名称（用于数据库记录） */
    private String currentMonitoringInterfaceName;
    
    /** 当前监控会话 ID */
    private Integer currentSessionId;
    
    /** 进程流量数据锁（用于线程安全） */
    private final Object processTrafficLock = new Object();
    
    // ========== 流量监控相关 ==========
    
    /** 流量监控任务：在后台线程中执行数据包捕获 */
    private TrafficMonitorTask trafficMonitorTask;
    
    /** 
     * 定时任务执行器：用于定时读取流量数据并更新图表
     * 每秒执行一次，读取累计的字节数并更新到图表
     */
    private ScheduledExecutorService scheduler;
    
    /** 定时任务的 Future，用于取消任务 */
    private ScheduledFuture<?> updateTaskFuture;
    
    /** 图表数据系列：存储流量数据点 */
    private XYChart.Series<String, Number> trafficSeries;
    
    /** 下行速度数据系列 */
    private XYChart.Series<String, Number> downSpeedSeries;
    
    /** 上行速度数据系列 */
    private XYChart.Series<String, Number> upSpeedSeries;
    
    /** 时间格式化器：用于生成时间标签 */
    private SimpleDateFormat timeFormatter;
    
    /** 最大数据点数量：保留最近60个数据点（1分钟的数据） */
    private static final int MAX_DATA_POINTS = 60;
    
    // ========== 进程流量监控相关 ==========
    
    /** 进程流量数据源 */
    private ObservableList<ProcessTrafficModel> processTrafficData;
    
    /** 进程流量汇总映射：进程名 -> ProcessTrafficModel（用于快速查找和更新） */
    private java.util.Map<String, ProcessTrafficModel> processTrafficMap;
    
    /** 进程流量更新任务的 Future */
    private ScheduledFuture<?> processTrafficUpdateFuture;
    
    /** 高流量阈值（KB/s），超过此值将显示为高流量 */
    private static final double HIGH_TRAFFIC_THRESHOLD = 1024.0; // 1 MB/s
    
    /** 进程流量临时统计：进程名 -> 流量数据（用于每秒汇总） */
    private java.util.Map<String, ProcessTrafficData> processTrafficTempMap;
    
    // ========== 概览看板数据 ==========
    
    /** 峰值速度（KB/s） */
    private double peakSpeed = 0.0;
    
    /** 总流量（MB） */
    private double totalTrafficMB = 0.0;
    
    /** 应用启动时间 */
    private long appStartTime;
    
    /** 运行时间更新任务的 Future */
    private ScheduledFuture<?> uptimeUpdateFuture;
    
    // ========== 初始化方法 ==========
    
    /**
     * FXML 加载后自动调用的初始化方法
     * 在 Controller 实例化后，FXML 加载器会自动调用此方法
     */
    @FXML
    public void initialize() {
        // 初始化数据源：创建空的 ObservableList
        networkInterfaceList = FXCollections.observableArrayList();
        
        // 将数据源绑定到 ComboBox
        networkInterfaceComboBox.setItems(networkInterfaceList);
        
        // 设置下拉列表的最大显示行数（约等于 400px 高度，每行约 50px）
        networkInterfaceComboBox.setVisibleRowCount(8);
        
        // 初始化服务对象
        networkInterfaceService = new NetworkInterfaceService();
        
        // 初始化数据库服务（用于保存流量历史数据）
        databaseService = DatabaseService.getInstance();
        
        // 初始化进程上下文服务
        processContextService = ProcessContextService.getInstance();
        processContextService.start(); // 启动后台更新任务
        
        // 初始化 AI 服务
        initializeAIService();
        
        // 设置标题为微软雅黑并加粗
        if (titleLabel != null) {
            titleLabel.setStyle("-fx-font-family: 'Microsoft YaHei', '微软雅黑', sans-serif; -fx-font-weight: bold;");
        }
        
        // 初始化流量监控相关组件
        initializeTrafficMonitoring();
        
        // 初始化进程流量监控
        initializeProcessTrafficMonitoring();
        
        // 设置自定义的 CellFactory 和 ButtonCell，用于格式化显示网卡信息
        setupComboBoxCellFactory();
        
        // 初始化概览看板
        initializeOverviewCards();
        
        // 初始化状态栏
        initializeStatusBar();
        
        // 初始化 BPF 过滤输入框
        initializeBpfFilterInput();
        
        // 初始化时自动刷新网卡列表
        refreshNICs();
    }
    
    /**
     * 初始化 BPF 过滤表达式输入框
     * 设置文本变化监听器，进行异步语法校验
     */
    private void initializeBpfFilterInput() {
        if (bpfFilterTextField == null) {
            return;
        }
        
        // 添加文本变化监听器，进行异步校验
        bpfFilterTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            // 如果输入为空，清除错误状态
            if (newValue == null || newValue.trim().isEmpty()) {
                bpfFilterTextField.getStyleClass().remove("error");
                bpfFilterTextField.setTooltip(null);
                return;
            }
            
            // 异步校验 BPF 表达式语法
            validateBpfExpressionAsync(newValue.trim());
        });
    }
    
    /**
     * 异步校验 BPF 表达式语法
     * 使用后台任务进行校验，避免阻塞 UI 线程
     * 
     * @param expression BPF 过滤表达式
     */
    private void validateBpfExpressionAsync(String expression) {
        // 创建一个后台任务来校验表达式
        Task<Boolean> validationTask = new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                try {
                    // 使用 Pcap4j 的 BpfProgram 来编译和校验表达式
                    // 这里我们创建一个临时的 PcapHandle 来测试表达式
                    // 实际上，我们可以直接使用 BpfProgram.compile() 方法
                    PcapNetworkInterface testInterface = networkInterfaceComboBox.getValue();
                    if (testInterface == null) {
                        // 如果没有选中网卡，使用第一个可用网卡进行测试
                        List<PcapNetworkInterface> interfaces = networkInterfaceService.getAllInterfaces();
                        if (interfaces.isEmpty()) {
                            return false;
                        }
                        testInterface = interfaces.get(0);
                    }
                    
                    // 打开一个临时的 PcapHandle 来测试过滤器
                    try (PcapHandle testHandle = testInterface.openLive(
                            65536,
                            PcapNetworkInterface.PromiscuousMode.NONPROMISCUOUS,
                            10)) {
                        // 尝试编译并设置过滤器（仅用于校验）
                        // 注意：此操作在内核空间完成过滤，可大幅降低 JVM 的 GC 压力
                        testHandle.setFilter(expression, BpfCompileMode.OPTIMIZE);
                        return true;
                    }
                } catch (Exception e) {
                    // 捕获语法错误（setFilter 可能抛出各种异常，包括语法错误）
                    updateMessage(e.getMessage());
                    return false;
                }
            }
        };
        
        // 任务完成后的回调
        validationTask.setOnSucceeded(event -> {
            boolean isValid = validationTask.getValue();
            Platform.runLater(() -> {
                if (isValid) {
                    // 语法正确，清除错误样式
                    bpfFilterTextField.getStyleClass().remove("error");
                    bpfFilterTextField.setTooltip(null);
                } else {
                    // 语法错误，显示错误样式和提示
                    String errorMessage = validationTask.getMessage();
                    if (errorMessage == null || errorMessage.isEmpty()) {
                        errorMessage = "BPF 表达式语法错误";
                    }
                    bpfFilterTextField.getStyleClass().add("error");
                    bpfFilterTextField.setTooltip(new Tooltip(errorMessage));
                }
            });
        });
        
        validationTask.setOnFailed(event -> {
            Platform.runLater(() -> {
                // 校验失败，显示错误样式
                bpfFilterTextField.getStyleClass().add("error");
                Throwable exception = validationTask.getException();
                String errorMessage = exception != null ? exception.getMessage() : "校验失败";
                bpfFilterTextField.setTooltip(new Tooltip(errorMessage));
            });
        });
        
        // 在后台线程中执行校验任务
        Thread validationThread = new Thread(validationTask);
        validationThread.setDaemon(true);
        validationThread.start();
    }
    
    /**
     * 初始化概览看板
     */
    private void initializeOverviewCards() {
        if (realtimeDownLabel != null) realtimeDownLabel.setText("0.00");
        if (realtimeUpLabel != null) realtimeUpLabel.setText("0.00");
        if (peakSpeedLabel != null) peakSpeedLabel.setText("0.00");
        if (totalTrafficLabel != null) totalTrafficLabel.setText("0.00");
        
        peakSpeed = 0.0;
        totalTrafficMB = 0.0;
    }
    
    /**
     * 初始化状态栏
     */
    private void initializeStatusBar() {
        appStartTime = System.currentTimeMillis();
        
        // 更新运行时间（每秒更新一次）
        uptimeUpdateFuture = scheduler.scheduleAtFixedRate(
            this::updateUptime,
            1,
            1,
            TimeUnit.SECONDS
        );
        
        // 初始化状态显示
        if (uptimeLabel != null) uptimeLabel.setText("00:00:00");
        if (aiStatusLabel != null) {
            // 检查 AI 服务是否可用
            checkAIStatus();
        }
        if (dbStatusLabel != null) {
            if (databaseService != null && databaseService.getConnection() != null) {
                dbStatusLabel.setText("就绪");
                dbStatusLabel.setStyle("-fx-text-fill: #28a745;");
            } else {
                dbStatusLabel.setText("未连接");
                dbStatusLabel.setStyle("-fx-text-fill: #dc3545;");
            }
        }
    }
    
    /**
     * 更新运行时间显示
     */
    private void updateUptime() {
        long elapsed = System.currentTimeMillis() - appStartTime;
        long seconds = elapsed / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        Platform.runLater(() -> {
            if (uptimeLabel != null) {
                uptimeLabel.setText(String.format("%02d:%02d:%02d", hours, minutes, secs));
            }
        });
    }
    
    /**
     * 初始化 AI 服务
     * 从环境变量读取配置并创建 AIService 实例
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
                aiProviderName = formatProviderName(finalProvider);
                
                System.out.println("[MainController] AI 服务已初始化（环境变量配置）");
            } else {
                // 环境变量未设置，根据 provider 使用默认配置
                String finalProvider = (apiProvider != null && !apiProvider.isEmpty()) 
                    ? apiProvider : "deepseek";
                
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
                    aiProviderName = formatProviderName(config.getProvider());
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
                    aiProviderName = formatProviderName(config.getProvider());
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
                    aiProviderName = formatProviderName(config.getProvider());
                }
                
                System.out.println("[MainController] AI 服务已初始化（默认配置）");
            }
        } catch (Exception e) {
            System.err.println("[MainController] AI 服务初始化失败: " + e.getMessage());
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
     * 格式化提供商名称（用于显示）
     * 
     * @param provider 提供商名称（小写）
     * @return 格式化后的名称（首字母大写）
     */
    private String formatProviderName(String provider) {
        if (provider == null || provider.isEmpty()) {
            return "未知";
        }
        
        String lower = provider.toLowerCase();
        if ("deepseek".equals(lower)) {
            return "DeepSeek";
        } else if ("openai".equals(lower)) {
            return "OpenAI";
        } else if ("ollama".equals(lower)) {
            return "Ollama";
        } else {
            // 首字母大写
            return provider.substring(0, 1).toUpperCase() + provider.substring(1).toLowerCase();
        }
    }
    
    /**
     * 检查 AI 服务状态
     * 如果 AI 服务已配置，则更新状态标签为"已连接{提供商名称}"
     */
    private void checkAIStatus() {
        if (aiStatusLabel == null) {
            return;
        }
        
        // 首先检查 AI 服务是否已初始化
        if (aiService == null) {
            aiStatusLabel.setText("未连接");
            aiStatusLabel.setStyle("-fx-text-fill: #6c757d;");
            return;
        }
        
        // 获取提供商名称
        String providerName = aiProviderName;
        if (providerName == null || providerName.isEmpty()) {
            // 如果未保存提供商名称，尝试从环境变量获取
            String apiProvider = cleanEnvValue(System.getenv("AI_PROVIDER"));
            if (apiProvider == null || apiProvider.isEmpty()) {
                apiProvider = "deepseek";
            }
            providerName = formatProviderName(apiProvider);
        }
        
        // 检查环境变量或配置是否存在
        String apiEndpoint = cleanEnvValue(System.getenv("AI_API_ENDPOINT"));
        if (apiEndpoint == null || apiEndpoint.isEmpty()) {
            // 如果没有设置环境变量，检查是否有默认配置（DeepSeek）
            String apiProvider = cleanEnvValue(System.getenv("AI_PROVIDER"));
            if (apiProvider == null || apiProvider.isEmpty()) {
                apiProvider = "deepseek";
            }
            // 如果有provider，认为已配置（使用默认端点）
            if (apiProvider != null && !apiProvider.isEmpty()) {
                aiStatusLabel.setText("已连接" + providerName);
                aiStatusLabel.setStyle("-fx-text-fill: #28a745;");
                // 异步测试连接（不阻塞UI）
                testAIConnectionAsync(providerName);
            } else {
                aiStatusLabel.setText("未配置");
                aiStatusLabel.setStyle("-fx-text-fill: #6c757d;");
            }
        } else {
            // 环境变量已设置，显示"已连接{提供商名称}"
            aiStatusLabel.setText("已连接" + providerName);
            aiStatusLabel.setStyle("-fx-text-fill: #28a745;");
            // 异步测试连接（不阻塞UI）
            testAIConnectionAsync(providerName);
        }
    }
    
    /**
     * 异步测试 AI 连接
     * 发送一个简单的测试请求来验证连接是否正常
     * 
     * @param providerName 提供商名称（用于状态显示）
     */
    private void testAIConnectionAsync(String providerName) {
        if (aiService == null) {
            return;
        }
        
        // 在后台线程中测试连接
        CompletableFuture.supplyAsync(() -> {
            try {
                // 创建一个简单的测试请求（使用空数据列表）
                // 这会触发配置检查，如果配置无效会返回错误信息
                List<TrafficData> emptyList = new ArrayList<>();
                CompletableFuture<String> result = aiService.analyzeTraffic(emptyList);
                
                // 等待结果（设置超时）
                String response = result.get(5, TimeUnit.SECONDS);
                
                // 如果返回的是错误信息，说明连接可能有问题
                if (response != null && response.startsWith("错误：")) {
                    return false;
                }
                return true;
            } catch (Exception e) {
                // 连接测试失败
                System.err.println("[MainController] AI 连接测试失败: " + e.getMessage());
                return false;
            }
        }).thenAccept(success -> {
            Platform.runLater(() -> {
                if (aiStatusLabel != null) {
                    if (success) {
                        // 连接成功，显示"已连接{提供商名称}"
                        String displayName = providerName != null && !providerName.isEmpty() 
                            ? providerName : "AI";
                        aiStatusLabel.setText("已连接" + displayName);
                        aiStatusLabel.setStyle("-fx-text-fill: #28a745;");
                    } else {
                        // 配置存在但连接失败，显示"连接失败{提供商名称}"
                        String displayName = providerName != null && !providerName.isEmpty() 
                            ? providerName : "AI";
                        aiStatusLabel.setText("连接失败" + displayName);
                        aiStatusLabel.setStyle("-fx-text-fill: #ffc107;");
                    }
                }
            });
        });
    }
    
    /**
     * 设置 ComboBox 的自定义 CellFactory 和 ButtonCell
     * 下拉列表中使用 HBox 布局显示网卡名称、IP 和 MAC 地址
     * 选中后只显示网卡名称
     */
    private void setupComboBoxCellFactory() {
        // 设置下拉列表中的单元格样式（ListCell）
        networkInterfaceComboBox.setCellFactory(param -> new ListCell<PcapNetworkInterface>() {
            @Override
            protected void updateItem(PcapNetworkInterface item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    // 获取网卡信息
                    NetworkInterfaceService.NetworkInterfaceInfo info = 
                            networkInterfaceService.getInterfaceInfo(item);
                    
                    // 创建 HBox 布局：左侧显示网卡名称（加粗），下方显示 IP 和 MAC 地址
                    VBox container = new VBox(4);
                    container.setStyle("-fx-padding: 4px;");
                    
                    // 网卡名称（加粗）
                    Label nameLabel = new Label(info.getDescription());
                    nameLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
                    
                    // IP 和 MAC 地址（小字）
                    HBox detailsBox = new HBox(8);
                    Label ipLabel = new Label("IP: " + info.getIpAddress());
                    ipLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #4a90e2;");
                    Label macLabel = new Label("MAC: " + info.getMacAddress());
                    macLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #6c757d;");
                    detailsBox.getChildren().addAll(ipLabel, macLabel);
                    
                    container.getChildren().addAll(nameLabel, detailsBox);
                    setGraphic(container);
                }
            }
        });
        
        // 设置选中后显示区域的单元格样式（ButtonCell）
        // 只显示网卡名称，避免一行太挤
        networkInterfaceComboBox.setButtonCell(new ListCell<PcapNetworkInterface>() {
            @Override
            protected void updateItem(PcapNetworkInterface item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    // 只显示网卡描述（核心信息）
                    NetworkInterfaceService.NetworkInterfaceInfo info = 
                            networkInterfaceService.getInterfaceInfo(item);
                    setText(info.getDescription());
                    setStyle("-fx-font-size: 13px;");
                }
            }
        });
    }
    
    /**
     * 初始化流量监控相关组件
     * 设置图表数据系列和时间格式化器
     */
    private void initializeTrafficMonitoring() {
        // 创建图表数据系列
        trafficSeries = new XYChart.Series<>();
        trafficSeries.setName("总流量");
        
        // 创建下行和上行速度数据系列
        downSpeedSeries = new XYChart.Series<>();
        downSpeedSeries.setName("下行速度");
        
        upSpeedSeries = new XYChart.Series<>();
        upSpeedSeries.setName("上行速度");
        
        // 将数据系列添加到图表
        trafficChart.getData().addAll(downSpeedSeries, upSpeedSeries, trafficSeries);
        
        // 初始化时间格式化器（格式：HH:mm:ss）
        timeFormatter = new SimpleDateFormat("HH:mm:ss");
        
        // 设置图表样式类（用于CSS面积填充）
        trafficChart.getStyleClass().add("area-chart");
        
        // 为所有数据点添加Tooltip（鼠标悬停显示精确数值）
        addTooltipsToChartData();
        
        // 创建定时任务执行器（单线程）
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TrafficUpdateThread");
            t.setDaemon(true); // 设置为守护线程
            return t;
        });
    }
    
    /**
     * 为图表数据点添加 Tooltip 交互功能
     * 遍历所有数据序列（下行速度、上行速度、总流量），为每个数据点安装 Tooltip
     * 
     * 调用时机：
     * 1. 在 initializeTrafficMonitoring() 中初始化时调用
     * 2. 每次向图表添加新数据点后，会自动通过监听器触发
     */
    private void addTooltipsToChartData() {
        // 为下行速度序列添加监听器
        downSpeedSeries.getData().addListener((javafx.collections.ListChangeListener.Change<? extends XYChart.Data<String, Number>> change) -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (XYChart.Data<String, Number> data : change.getAddedSubList()) {
                        installTooltipForDataPoint(data, "下行速度", true);
                    }
                }
            }
        });
        
        // 为上行速度序列添加监听器
        upSpeedSeries.getData().addListener((javafx.collections.ListChangeListener.Change<? extends XYChart.Data<String, Number>> change) -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (XYChart.Data<String, Number> data : change.getAddedSubList()) {
                        installTooltipForDataPoint(data, "上行速度", false);
                    }
                }
            }
        });
        
        // 为总流量序列添加监听器
        trafficSeries.getData().addListener((javafx.collections.ListChangeListener.Change<? extends XYChart.Data<String, Number>> change) -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (XYChart.Data<String, Number> data : change.getAddedSubList()) {
                        installTooltipForDataPoint(data, "总流量", null);
                    }
                }
            }
        });
    }
    
    /**
     * 为单个数据点安装 Tooltip
     * 
     * @param data 数据点对象
     * @param seriesName 序列名称（用于显示）
     * @param isDownSpeed 是否为下行速度（true=下行，false=上行，null=总流量）
     */
    private void installTooltipForDataPoint(XYChart.Data<String, Number> data, String seriesName, Boolean isDownSpeed) {
        // 创建 Tooltip 实例
        Tooltip tooltip = new Tooltip();
        
        // 获取数据值
        String time = data.getXValue();
        double speed = data.getYValue().doubleValue();
        
        // 根据序列类型设置 Tooltip 文本
        String tooltipText;
        if (isDownSpeed == null) {
            // 总流量：显示总流量、下行和上行（平均分配）
            double downSpeed = speed / 2.0;
            double upSpeed = speed / 2.0;
            tooltipText = String.format(
                "时间：%s\n" +
                "总流量：%.2f KB/s\n" +
                "下行：%.2f KB/s\n" +
                "上行：%.2f KB/s",
                time, speed, downSpeed, upSpeed
            );
        } else if (isDownSpeed) {
            // 下行速度
            tooltipText = String.format(
                "时间：%s\n" +
                "下行速度：%.2f KB/s",
                time, speed
            );
        } else {
            // 上行速度
            tooltipText = String.format(
                "时间：%s\n" +
                "上行速度：%.2f KB/s",
                time, speed
            );
        }
        
        tooltip.setText(tooltipText);
        
        // 设置 Tooltip 样式
        tooltip.setStyle(
            "-fx-font-size: 13px; " +
            "-fx-font-family: 'Segoe UI', 'Microsoft YaHei UI', sans-serif; " +
            "-fx-background-color: rgba(44,62,80,0.95); " +
            "-fx-text-fill: white; " +
            "-fx-padding: 10px 14px; " +
            "-fx-background-radius: 6px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 8, 0, 0, 2);"
        );
        
        // 将 Tooltip 安装到数据点的节点上
        Platform.runLater(() -> {
            if (data.getNode() != null) {
                Tooltip.install(data.getNode(), tooltip);
            } else {
                // 如果节点还未创建，等待节点创建后再安装
                data.nodeProperty().addListener((obs, oldNode, newNode) -> {
                    if (newNode != null) {
                        Tooltip.install(newNode, tooltip);
                    }
                });
            }
        });
    }
    
    /**
     * 初始化进程流量监控
     * 设置表格列绑定、排序和样式
     */
    private void initializeProcessTrafficMonitoring() {
        // 初始化进程流量映射表
        processTrafficMap = new java.util.concurrent.ConcurrentHashMap<>();
        
        // 初始化临时统计映射表
        processTrafficTempMap = new java.util.concurrent.ConcurrentHashMap<>();
        
        // 创建数据源
        processTrafficData = FXCollections.observableArrayList();
        
        // 配置表格列的数据绑定
        processNameColumn.setCellValueFactory(new PropertyValueFactory<>("processName"));
        processPidColumn.setCellValueFactory(new PropertyValueFactory<>("pid"));
        processDownloadSpeedColumn.setCellValueFactory(new PropertyValueFactory<>("downloadSpeed"));
        processUploadSpeedColumn.setCellValueFactory(new PropertyValueFactory<>("uploadSpeed"));
        
        // 设置下载速度列的格式化显示（带进度条）
        processDownloadSpeedColumn.setCellFactory(column -> new ProgressBarTableCell(true));
        
        // 设置上传速度列的格式化显示（带进度条）
        processUploadSpeedColumn.setCellFactory(column -> new ProgressBarTableCell(false));
        
        // 设置列对齐：进程名称左对齐，其他列右对齐
        processNameColumn.setStyle("-fx-alignment: CENTER-LEFT;");
        processPidColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        processDownloadSpeedColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        processUploadSpeedColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        
        // 设置进程名称列的背景颜色（整行）
        processNameColumn.setCellFactory(column -> new TableCell<ProcessTrafficModel, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    ProcessTrafficModel model = getTableView().getItems().get(getIndex());
                    double totalSpeed = model.getTotalSpeed();
                    if (totalSpeed > HIGH_TRAFFIC_THRESHOLD) {
                        setStyle("-fx-background-color: #ffcccc;");
                    } else if (totalSpeed > HIGH_TRAFFIC_THRESHOLD / 2) {
                        setStyle("-fx-background-color: #ffffcc;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        
        // 创建排序列表：按下载速度从高到低排序
        SortedList<ProcessTrafficModel> sortedData = new SortedList<>(processTrafficData);
        sortedData.comparatorProperty().bind(processTrafficTable.comparatorProperty());
        
        // 设置默认排序：按下载速度降序
        processDownloadSpeedColumn.setSortType(TableColumn.SortType.DESCENDING);
        processTrafficTable.getSortOrder().add(processDownloadSpeedColumn);
        
        // 绑定排序后的数据到表格
        processTrafficTable.setItems(sortedData);
    }
    
    // ========== 事件处理方法 ==========
    
    /**
     * 帮助按钮的点击事件处理
     * 打开帮助中心窗口（非模态窗口）
     */
    @FXML
    protected void onHelpButtonClick() {
        try {
            // 加载帮助中心窗口的 FXML 文件
            FXMLLoader fxmlLoader = new FXMLLoader(
                HelloApplication.class.getResource("help-view.fxml")
            );
            
            // 创建新窗口
            Stage helpStage = new Stage();
            helpStage.setTitle("帮助中心 - NetPulse FX");
            helpStage.setScene(new Scene(fxmlLoader.load(), 1000, 700));
            
            // 设置窗口为非模态窗口（允许同时操作主窗口）
            // helpStage.initModality(Modality.NONE); // 默认就是非模态，可省略
            
            // 设置窗口最小尺寸
            helpStage.setMinWidth(800);
            helpStage.setMinHeight(500);
            
            // 显示窗口
            helpStage.show();
            
        } catch (Exception e) {
            // 如果打开窗口失败，显示错误提示
            showAlert(Alert.AlertType.ERROR, "打开帮助中心失败", 
                    "无法打开帮助中心窗口：\n" + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 查看历史数据按钮的点击事件处理
     * 打开历史数据查看窗口
     */
    @FXML
    protected void onViewHistoryButtonClick() {
        try {
            // 加载历史数据窗口的 FXML 文件
            FXMLLoader fxmlLoader = new FXMLLoader(
                HelloApplication.class.getResource("history-view.fxml")
            );
            
            // 创建新窗口
            Stage historyStage = new Stage();
            historyStage.setTitle("流量历史数据");
            historyStage.setScene(new Scene(fxmlLoader.load()));
            
            // 设置窗口默认最大化（全屏显示）
            historyStage.setMaximized(true);
            
            // 设置窗口为模态窗口（可选，这里使用非模态，允许同时查看主窗口）
            // historyStage.initModality(Modality.APPLICATION_MODAL);
            
            // 显示窗口
            historyStage.show();
            
        } catch (Exception e) {
            // 如果打开窗口失败，显示错误提示
            showAlert(Alert.AlertType.ERROR, "打开历史数据窗口失败", 
                    "无法打开历史数据查看窗口：\n" + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 刷新网卡按钮的点击事件处理
     * 重新扫描系统中的所有网卡并更新列表显示
     */
    @FXML
    protected void onRefreshButtonClick() {
        refreshNICs();
    }
    
    /**
     * 开始监控按钮的点击事件处理
     * 获取用户选中的网卡，启动流量监控功能
     * 
     * 多线程通信流程：
     * 1. 创建 TrafficMonitorTask，在后台线程中启动抓包
     * 2. 启动定时任务（ScheduledExecutorService），每秒执行一次
     * 3. 定时任务读取 AtomicLong 中的累计字节数
     * 4. 使用 Platform.runLater() 将数据更新到图表
     */
    @FXML
    protected void onStartMonitorButtonClick() {
        // 获取当前选中的网卡对象
        PcapNetworkInterface selectedInterface = networkInterfaceComboBox.getValue();
        
        if (selectedInterface == null) {
            // 如果没有选中任何网卡，显示警告并添加视觉提示
            showAlert(Alert.AlertType.WARNING, "未选择网卡", 
                    "请先选择一个网卡后再开始监控！");
            
            // 添加红框警告效果
            networkInterfaceComboBox.getStyleClass().add("error");
            
            // 添加震动效果
            shakeComboBox();
            
            // 3秒后移除错误样式
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    Platform.runLater(() -> {
                        networkInterfaceComboBox.getStyleClass().remove("error");
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
            
            return;
        }
        
        // 移除错误样式（如果存在）
        networkInterfaceComboBox.getStyleClass().remove("error");
        
        // 如果已经在监控，先停止当前监控
        if (trafficMonitorTask != null && trafficMonitorTask.isMonitoringActive()) {
            stopMonitoring();
        }
        
        // 直接使用选中的 PcapNetworkInterface 对象
        if (selectedInterface != null) {
            
            try {
                // 保存当前监控的网卡名称（用于数据库记录）
                // 优先使用描述（用户友好的名称），如果描述为空则使用设备名称
                String description = selectedInterface.getDescription();
                if (description != null && !description.trim().isEmpty()) {
                    currentMonitoringInterfaceName = description;
                } else {
                    // 如果描述为空，使用设备名称作为后备
                    currentMonitoringInterfaceName = selectedInterface.getName();
                }
                
                // 创建新的监控会话
                databaseService.startNewSession(currentMonitoringInterfaceName)
                    .thenAccept(sessionId -> {
                        currentSessionId = sessionId;
                        System.out.println("[MainController] 监控会话已创建: session_id=" + sessionId);
                        
                        // 清空图表数据（清空所有序列）
                        Platform.runLater(() -> {
                            downSpeedSeries.getData().clear();
                            upSpeedSeries.getData().clear();
                            trafficSeries.getData().clear();
                        });
                        
                        // 获取 BPF 过滤表达式（如果为空则传递 null）
                        String bpfExpression = null;
                        if (bpfFilterTextField != null) {
                            String text = bpfFilterTextField.getText();
                            if (text != null && !text.trim().isEmpty()) {
                                bpfExpression = text.trim();
                            }
                        }
                        
                        // 检查 BPF 表达式是否有错误样式（表示语法错误）
                        if (bpfExpression != null && bpfFilterTextField.getStyleClass().contains("error")) {
                            Platform.runLater(() -> {
                                showAlert(Alert.AlertType.WARNING, "BPF 表达式错误", 
                                        "BPF 过滤表达式存在语法错误，请修正后再开始监控！");
                                resetMonitorButtons();
                            });
                            return;
                        }
                        
                        // 获取本地 IP 地址（用于区分上行和下行流量）
                        String localIp = networkInterfaceService.getValidIPv4Address(selectedInterface);
                        if (localIp == null || localIp.isEmpty()) {
                            // 如果无法获取本地 IP，显示警告但继续监控（会使用平均分配的方式）
                            System.out.println("[MainController] 警告：无法获取本地 IP 地址，将使用平均分配方式统计流量");
                        } else {
                            System.out.println("[MainController] 本地 IP 地址: " + localIp);
                        }
                        
                        // 创建流量监控任务（传入 BPF 表达式和本地 IP 地址）
                        trafficMonitorTask = new TrafficMonitorTask(selectedInterface, bpfExpression, localIp);
                        
                        // 设置任务失败时的回调
                        trafficMonitorTask.setOnFailed(e -> {
                            Platform.runLater(() -> {
                                Throwable exception = trafficMonitorTask.getException();
                                showAlert(Alert.AlertType.ERROR, "监控失败", 
                                        "启动流量监控时发生错误：\n" + 
                                        (exception != null ? exception.getMessage() : "未知错误"));
                                resetMonitorButtons();
                            });
                        });
                        
                        // 启动监控任务（在后台线程执行）
                        Thread monitorThread = new Thread(trafficMonitorTask);
                        monitorThread.setDaemon(true);
                        monitorThread.start();
                        
                        // 启动定时更新任务
                        Platform.runLater(() -> {
                            // 启动定时更新任务
                            // 每秒执行一次，读取流量数据并更新图表
                            updateTaskFuture = scheduler.scheduleAtFixedRate(
                                    this::updateTrafficChart,  // 要执行的任务
                                    1,                         // 初始延迟（秒）
                                    1,                         // 执行间隔（秒）
                                    TimeUnit.SECONDS
                            );
                            
                            // 启动进程流量更新任务
                            // 每秒执行一次，汇总进程流量并更新表格
                            processTrafficUpdateFuture = scheduler.scheduleAtFixedRate(
                                    this::updateProcessTrafficTable,  // 要执行的任务
                                    1,                                // 初始延迟（秒）
                                    1,                                // 执行间隔（秒）
                                    TimeUnit.SECONDS
                            );
                            
                            // 更新UI状态
                            startMonitorButton.setDisable(true);
                            stopMonitorButton.setDisable(false);
                            refreshNICButton.setDisable(true);
                            networkInterfaceComboBox.setDisable(true);
                            if (bpfFilterTextField != null) {
                                bpfFilterTextField.setDisable(true);
                            }
                            if (bpfPresetMenuButton != null) {
                                bpfPresetMenuButton.setDisable(true);
                            }
                            
                            titleLabel.setText("正在监控：" + selectedInterface.getDescription());
                        });
                    })
                    .exceptionally(e -> {
                        Platform.runLater(() -> {
                            showAlert(Alert.AlertType.ERROR, "创建会话失败", 
                                    "无法创建监控会话：\n" + e.getMessage());
                            resetMonitorButtons();
                        });
                        return null;
                    });
                
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "启动失败", 
                        "无法启动流量监控：\n" + e.getMessage());
                resetMonitorButtons();
            }
        }
    }
    
    /**
     * 停止监控按钮的点击事件处理
     * 安全地停止流量监控，释放所有资源
     */
    @FXML
    protected void onStopMonitorButtonClick() {
        stopMonitoring();
    }
    
    // ========== BPF 预设菜单事件处理方法 ==========
    
    /**
     * BPF 预设：仅限 Web 流量
     * 过滤表达式：tcp port 80 or port 443
     */
    @FXML
    protected void onBpfPresetWebTraffic() {
        applyBpfPreset("tcp port 80 or port 443");
    }
    
    /**
     * BPF 预设：排除本机会话
     * 过滤表达式：not host [本地IP]
     */
    @FXML
    protected void onBpfPresetExcludeLocal() {
        String localIp = getLocalIpAddress();
        if (localIp != null && !localIp.isEmpty()) {
            applyBpfPreset("not host " + localIp);
        } else {
            showAlert(Alert.AlertType.WARNING, "无法获取本地IP", 
                    "无法获取本地IP地址，请手动输入排除本机会话的过滤表达式。");
        }
    }
    
    /**
     * BPF 预设：DNS 监控
     * 过滤表达式：udp port 53
     */
    @FXML
    protected void onBpfPresetDns() {
        applyBpfPreset("udp port 53");
    }
    
    /**
     * BPF 预设：大包屏蔽（只抓小控制包）
     * 过滤表达式：less 128
     */
    @FXML
    protected void onBpfPresetSmallPackets() {
        applyBpfPreset("less 128");
    }
    
    /**
     * 应用 BPF 预设表达式
     * 将表达式填充到输入框并立即触发语法验证
     * 
     * @param expression BPF 过滤表达式
     */
    private void applyBpfPreset(String expression) {
        if (bpfFilterTextField == null) {
            return;
        }
        
        // 填充表达式到输入框
        bpfFilterTextField.setText(expression);
        
        // 触发语法验证（通过模拟文本变化事件）
        // 由于我们已经设置了 textProperty 监听器，直接设置文本会自动触发验证
        // 但为了确保验证立即执行，我们可以手动调用验证方法
        if (expression != null && !expression.trim().isEmpty()) {
            validateBpfExpressionAsync(expression.trim());
        }
    }
    
    /**
     * 获取本地IP地址
     * 优先使用当前选中的网卡IP，如果没有选中则使用第一个可用网卡的IP
     * 
     * @return 本地IP地址字符串，如果无法获取则返回 null
     */
    private String getLocalIpAddress() {
        try {
            // 优先使用当前选中的网卡
            PcapNetworkInterface selectedInterface = networkInterfaceComboBox.getValue();
            if (selectedInterface != null) {
                String ip = getIpFromInterface(selectedInterface);
                if (ip != null && !ip.isEmpty()) {
                    return ip;
                }
            }
            
            // 如果没有选中网卡或选中的网卡没有IP，尝试获取第一个可用网卡的IP
            List<PcapNetworkInterface> interfaces = networkInterfaceService.getAllInterfaces();
            for (PcapNetworkInterface nif : interfaces) {
                String ip = getIpFromInterface(nif);
                if (ip != null && !ip.isEmpty()) {
                    // 排除回环地址
                    if (!ip.equals("127.0.0.1") && !ip.startsWith("127.")) {
                        return ip;
                    }
                }
            }
            
            // 如果所有网卡都没有有效IP，返回 null
            return null;
        } catch (Exception e) {
            System.err.println("[MainController] 获取本地IP地址失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 从网络接口获取IP地址
     * 
     * @param nif 网络接口对象
     * @return IP地址字符串，如果无法获取则返回 null
     */
    private String getIpFromInterface(PcapNetworkInterface nif) {
        if (nif == null) {
            return null;
        }
        
        try {
            List<PcapAddress> addresses = nif.getAddresses();
            if (addresses != null && !addresses.isEmpty()) {
                for (PcapAddress addr : addresses) {
                    InetAddress inetAddr = addr.getAddress();
                    if (inetAddr != null) {
                        String ip = inetAddr.getHostAddress();
                        // 排除 IPv6 地址（以冒号分隔）
                        if (ip != null && !ip.contains(":") && !ip.equals("127.0.0.1")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[MainController] 从网络接口获取IP失败: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 停止监控的核心方法
     * 确保所有线程和资源都被正确释放
     * 
     * 资源释放流程：
     * 1. 取消定时更新任务
     * 2. 停止抓包任务（调用 stop() 方法）
     * 3. 等待任务线程结束
     * 4. 恢复UI状态
     */
    private void stopMonitoring() {
        // 取消定时更新任务
        if (updateTaskFuture != null && !updateTaskFuture.isCancelled()) {
            updateTaskFuture.cancel(false);
            updateTaskFuture = null;
        }
        
        // 取消进程流量更新任务
        if (processTrafficUpdateFuture != null && !processTrafficUpdateFuture.isCancelled()) {
            processTrafficUpdateFuture.cancel(false);
            processTrafficUpdateFuture = null;
        }
        
        // 取消运行时间更新任务
        if (uptimeUpdateFuture != null && !uptimeUpdateFuture.isCancelled()) {
            uptimeUpdateFuture.cancel(false);
            uptimeUpdateFuture = null;
        }
        
        // 停止抓包任务
        if (trafficMonitorTask != null) {
            trafficMonitorTask.stop();  // 关闭 Pcap 句柄，中断抓包循环
            trafficMonitorTask.cancel(); // 取消 Task
            
            // 等待任务线程结束（最多等待2秒）
            try {
                Thread.sleep(100); // 给任务一点时间清理资源
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            trafficMonitorTask = null;
        }
        
        // 结束当前监控会话
        if (currentSessionId != null && databaseService != null) {
            final int sessionIdToEnd = currentSessionId;
            databaseService.endSession(sessionIdToEnd)
                .thenRun(() -> {
                    System.out.println("[MainController] 监控会话已结束: session_id=" + sessionIdToEnd);
                })
                .exceptionally(e -> {
                    System.err.println("[MainController] 结束监控会话失败: " + e.getMessage());
                    return null;
                });
            currentSessionId = null;
        }
        
        // 清空进程流量数据
        Platform.runLater(() -> {
            synchronized (processTrafficLock) {
                processTrafficMap.clear();
                processTrafficData.clear();
            }
        });
        
        // 重置概览看板
        Platform.runLater(() -> {
            initializeOverviewCards();
        });
        
        // 恢复UI状态
        Platform.runLater(this::resetMonitorButtons);
    }
    
    /**
     * 重置监控按钮状态
     */
    private void resetMonitorButtons() {
        startMonitorButton.setDisable(false);
        stopMonitorButton.setDisable(true);
        refreshNICButton.setDisable(false);
        networkInterfaceComboBox.setDisable(false);
        if (bpfFilterTextField != null) {
            bpfFilterTextField.setDisable(false);
        }
        if (bpfPresetMenuButton != null) {
            bpfPresetMenuButton.setDisable(false);
        }
        titleLabel.setText("请选择监控网卡");
        currentMonitoringInterfaceName = null; // 清除当前监控的网卡名称
        currentSessionId = null; // 清除当前会话ID
    }
    
    /**
     * 震动 ComboBox 的动画效果
     * 当用户未选择网卡就点击开始监控时触发
     */
    private void shakeComboBox() {
        TranslateTransition shake = new TranslateTransition(Duration.millis(50), networkInterfaceComboBox);
        shake.setFromX(0);
        shake.setByX(10);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);
        shake.play();
    }
    
    /**
     * 更新流量图表
     * 在定时任务中调用，读取累计的字节数并更新到图表
     * 
     * 多线程通信说明：
     * 1. 此方法在 ScheduledExecutorService 的线程中执行
     * 2. 调用 trafficMonitorTask.getAndResetBytes() 读取并清零原子变量
     * 3. 使用 Platform.runLater() 确保 UI 更新在 JavaFX Application Thread 执行
     * 4. 限制数据点数量，只保留最近60个点（1分钟的数据）
     * 5. 异步保存流量数据到 H2 数据库（不阻塞 UI 线程）
     * 
     * 数据库集成说明：
     * - 流量数据通过 DatabaseService 异步保存到数据库
     * - 使用 CompletableFuture 确保数据库操作不会阻塞 UI 更新
     * - 如果数据库保存失败，只记录错误，不影响图表显示
     */
    private void updateTrafficChart() {
        if (trafficMonitorTask == null || !trafficMonitorTask.isMonitoringActive()) {
            return;
        }
        
        try {
            // 读取累计的上行和下行字节数并清零（原子操作）
            long[] bytes = trafficMonitorTask.getAndResetBytes();
            long uploadedBytes = bytes[0];  // 上行字节数
            long downloadedBytes = bytes[1]; // 下行字节数
            
            // 转换为 KB/s（因为每秒执行一次，所以直接除以1024即可）
            double uploadKbPerSecond = uploadedBytes / 1024.0;   // 上行速度（KB/s）
            double downloadKbPerSecond = downloadedBytes / 1024.0; // 下行速度（KB/s）
            double totalKbPerSecond = uploadKbPerSecond + downloadKbPerSecond; // 总速度（KB/s）
            
            // 生成当前时间标签
            String timeLabel = timeFormatter.format(new Date());
            
            // 获取最近捕获的IP地址信息
            String[] lastIps = trafficMonitorTask.getAndClearLastIps();
            String sourceIp = lastIps[0];
            String destIp = lastIps[1];
            
            // 获取进程名称（用于数据库记录）
            String processName = trafficMonitorTask.getAndClearLastProcessName();
            
            // 获取进程流量统计（从 TrafficMonitorTask 中获取所有进程的累计流量，已区分上下行）
            Map<String, TrafficMonitorTask.ProcessTrafficStats> processTrafficMap = 
                trafficMonitorTask.getAndClearProcessTraffic();
            
            // 汇总进程流量（用于进程流量表格）
            synchronized (processTrafficLock) {
                for (Map.Entry<String, TrafficMonitorTask.ProcessTrafficStats> entry : processTrafficMap.entrySet()) {
                    String procName = entry.getKey();
                    TrafficMonitorTask.ProcessTrafficStats stats = entry.getValue();
                    
                    // 转换为 KB/s（因为每秒执行一次，所以直接除以1024即可）
                    double processUploadKbPerSecond = stats.getUploadBytes() / 1024.0;   // 进程上行速度（KB/s）
                    double processDownloadKbPerSecond = stats.getDownloadBytes() / 1024.0; // 进程下行速度（KB/s）
                    
                    ProcessTrafficData tempData = processTrafficTempMap.computeIfAbsent(
                        procName, 
                        k -> new ProcessTrafficData(procName, 0, 0.0, 0.0)
                    );
                    // 累加流量（已区分上下行）
                    tempData.addTraffic(processDownloadKbPerSecond, processUploadKbPerSecond);
                }
            }
            
            // 调试信息：打印捕获的IP地址和进程信息
            if (sourceIp != null || destIp != null || !processName.equals("未知进程")) {
                System.out.println("[MainController] 捕获到数据 - 源IP: " + 
                    (sourceIp != null ? sourceIp : "无") + 
                    ", 目标IP: " + (destIp != null ? destIp : "无") +
                    ", 进程: " + processName);
            }
            
            // 保存明细记录到当前会话
            if (currentSessionId != null && databaseService != null) {
                // 获取协议类型
                String protocol = trafficMonitorTask.getAndClearLastProtocol();
                
                // 调试输出
                if (protocol == null || protocol.isEmpty()) {
                    System.out.println("[MainController] 警告：协议类型为空，使用默认值'其他'");
                    protocol = "其他";
                } else {
                    System.out.println("[MainController] 保存记录 - 协议: " + protocol + 
                        ", 下行: " + downloadKbPerSecond + " KB/s, 上行: " + uploadKbPerSecond + " KB/s");
                }
                
                databaseService.saveDetailRecord(
                        currentSessionId,
                        downloadKbPerSecond,  // 下行速度（KB/s）
                        uploadKbPerSecond,     // 上行速度（KB/s）
                        sourceIp,             // 源IP地址
                        destIp,               // 目标IP地址
                        processName,          // 进程名称
                        protocol              // 协议类型
                ).exceptionally(e -> {
                    // 数据库保存失败时，只记录错误，不影响 UI 显示
                    System.err.println("[MainController] 保存流量明细记录失败: " + e.getMessage());
                    return null;
                });
            }
            
            // 更新概览看板数据
            double downSpeed = downloadKbPerSecond;  // 下行速度
            double upSpeed = uploadKbPerSecond;     // 上行速度
            
            // 更新峰值速度（使用总速度）
            if (totalKbPerSecond > peakSpeed) {
                peakSpeed = totalKbPerSecond;
            }
            
            // 更新总流量（累加）
            totalTrafficMB += totalKbPerSecond / 1024.0;  // KB/s 转换为 MB
            
            // 使用 Platform.runLater() 确保 UI 更新在主线程执行
            Platform.runLater(() -> {
                // 更新概览看板
                if (realtimeDownLabel != null) {
                    realtimeDownLabel.setText(String.format("%.2f", downSpeed));
                }
                if (realtimeUpLabel != null) {
                    realtimeUpLabel.setText(String.format("%.2f", upSpeed));
                }
                if (peakSpeedLabel != null) {
                    peakSpeedLabel.setText(String.format("%.2f", peakSpeed));
                }
                if (totalTrafficLabel != null) {
                    totalTrafficLabel.setText(String.format("%.2f", totalTrafficMB));
                }
                
                // 添加新的数据点到三个序列
                // 下行速度数据点
                XYChart.Data<String, Number> downData = new XYChart.Data<>(timeLabel, downSpeed);
                downSpeedSeries.getData().add(downData);
                
                // 上行速度数据点
                XYChart.Data<String, Number> upData = new XYChart.Data<>(timeLabel, upSpeed);
                upSpeedSeries.getData().add(upData);
                
                // 总流量数据点
                XYChart.Data<String, Number> totalData = new XYChart.Data<>(timeLabel, totalKbPerSecond);
                trafficSeries.getData().add(totalData);
                
                // Tooltip 会通过监听器自动安装，无需手动安装
                
                // 性能优化：限制数据点数量，只保留最近60个点
                // 这样可以防止图表数据点过多导致卡顿
                if (downSpeedSeries.getData().size() > MAX_DATA_POINTS) {
                    downSpeedSeries.getData().remove(0);
                }
                if (upSpeedSeries.getData().size() > MAX_DATA_POINTS) {
                    upSpeedSeries.getData().remove(0);
                }
                if (trafficSeries.getData().size() > MAX_DATA_POINTS) {
                    trafficSeries.getData().remove(0);
                }
            });
            
        } catch (Exception e) {
            // 如果发生错误，在主线程中显示错误信息
            Platform.runLater(() -> {
                System.err.println("更新流量图表时发生错误：" + e.getMessage());
            });
        }
    }
    
    // ========== 核心业务方法 ==========
    
    /**
     * 刷新网卡列表的核心方法
     * 
     * 数据流向说明：
     * 1. 调用 NetworkInterfaceService.getAllInterfaces() 获取原始网卡数据
     * 2. 遍历网卡列表，使用 getInterfaceInfo() 转换为结构化信息
     * 3. 将结构化信息格式化为字符串，添加到 ObservableList
     * 4. ListView 自动检测到 ObservableList 的变化，更新显示
     * 
     * 线程安全处理：
     * - 使用 Task 在后台线程执行耗时的网卡扫描操作
     * - 使用 Platform.runLater() 确保 UI 更新在主线程（JavaFX Application Thread）执行
     * - 这样可以防止界面卡顿，提升用户体验
     */
    private void refreshNICs() {
        // 禁用按钮，防止重复点击
        refreshNICButton.setDisable(true);
        startMonitorButton.setDisable(true);
        
        // 清空当前列表，显示加载状态
        networkInterfaceList.clear();
        networkInterfaceComboBox.setValue(null); // 清空选中项
        titleLabel.setText("正在扫描网卡...");
        
        // 创建后台任务，在后台线程执行网卡扫描
        Task<List<PcapNetworkInterface>> scanTask = new Task<List<PcapNetworkInterface>>() {
            @Override
            protected List<PcapNetworkInterface> call() throws Exception {
                // 在后台线程中执行：调用服务层获取网卡列表
                // 这是一个可能耗时的操作，特别是当系统中有很多网卡时
                return networkInterfaceService.getAllInterfaces();
            }
            
            @Override
            protected void succeeded() {
                // 任务成功完成后的回调（在主线程执行）
                try {
                    // 获取扫描结果
                    List<PcapNetworkInterface> interfaces = getValue();
                    pcapNetworkInterfaces = interfaces;
                    
                    // 使用 Platform.runLater 确保 UI 更新在主线程执行
                    // 虽然 Task 的回调方法通常已经在主线程，但显式调用更安全
                    Platform.runLater(() -> {
                        // 清空旧数据
                        networkInterfaceList.clear();
                        
                        // 直接将 PcapNetworkInterface 对象添加到列表
                        // ComboBox 会使用自定义的 CellFactory 来显示
                        networkInterfaceList.addAll(interfaces);
                        
                        // 更新标题，显示找到的网卡数量
                        titleLabel.setText(String.format("请选择监控网卡（找到 %d 个）", 
                                networkInterfaceList.size()));
                        
                        // 恢复按钮状态
                        refreshNICButton.setDisable(false);
                        startMonitorButton.setDisable(false);
                    });
                    
                } catch (Exception e) {
                    // 处理任务执行过程中的异常
                    Platform.runLater(() -> {
                        showAlert(Alert.AlertType.ERROR, "刷新失败", 
                                "刷新网卡列表时发生错误：\n" + e.getMessage());
                        titleLabel.setText("请选择监控网卡");
                        refreshNICButton.setDisable(false);
                        startMonitorButton.setDisable(false);
                    });
                }
            }
            
            @Override
            protected void failed() {
                // 任务失败后的回调（在主线程执行）
                Platform.runLater(() -> {
                    Throwable exception = getException();
                    
                    // 根据异常类型显示不同的错误信息
                    String errorMessage;
                    if (exception instanceof NetworkInterfaceException) {
                        errorMessage = exception.getMessage();
                    } else {
                        errorMessage = "扫描网卡时发生未知错误：\n" + 
                                (exception != null ? exception.getMessage() : "未知错误");
                    }
                    
                    showAlert(Alert.AlertType.ERROR, "扫描失败", errorMessage);
                    titleLabel.setText("请选择监控网卡");
                    refreshNICButton.setDisable(false);
                    startMonitorButton.setDisable(false);
                });
            }
        };
        
        // 启动后台任务
        // 使用新的线程执行任务，避免阻塞 JavaFX Application Thread
        Thread taskThread = new Thread(scanTask);
        taskThread.setDaemon(true); // 设置为守护线程，主程序退出时自动结束
        taskThread.start();
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 显示提示对话框的辅助方法
     * 
     * @param alertType 对话框类型（信息、警告、错误等）
     * @param title 对话框标题
     * @param message 对话框内容
     */
    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * 更新进程流量表格
     * 每秒执行一次，汇总进程流量并更新表格显示
     * 
     * <p>执行流程：</p>
     * <ol>
     *   <li>从临时统计映射表中获取所有进程的流量数据</li>
     *   <li>通过 ProcessContextService 查找每个进程的 PID</li>
     *   <li>更新或创建 ProcessTrafficModel</li>
     *   <li>清空临时统计，准备下一秒的统计</li>
     * </ol>
     */
    private void updateProcessTrafficTable() {
        if (trafficMonitorTask == null || !trafficMonitorTask.isMonitoringActive()) {
            return;
        }
        
        try {
            // 在后台线程中汇总数据
            java.util.Map<String, ProcessTrafficData> snapshot = new java.util.HashMap<>();
            
            synchronized (processTrafficLock) {
                snapshot.putAll(processTrafficTempMap);
                // 清空临时统计，准备下一秒的统计
                processTrafficTempMap.clear();
            }
            
            // 获取进程上下文服务，用于获取 PID
            final ProcessContextService processService = processContextService;
            
            // 在主线程中更新 UI
            Platform.runLater(() -> {
                synchronized (processTrafficLock) {
                    // 更新或创建进程流量模型
                    for (ProcessTrafficData tempData : snapshot.values()) {
                        String processName = tempData.getProcessName();
                        
                        // 如果流量为 0，跳过（避免显示无流量的进程）
                        if (tempData.getDownloadSpeed() < 0.01 && tempData.getUploadSpeed() < 0.01) {
                            continue;
                        }
                        
                        ProcessTrafficModel model = processTrafficMap.get(processName);
                        
                        if (model == null) {
                            // 创建新模型
                            // 尝试获取 PID（通过 ProcessContextService 的映射表查找）
                            Integer pid = findPidByProcessName(processName, processService);
                            model = new ProcessTrafficModel(processName, pid, 0.0, 0.0);
                            processTrafficMap.put(processName, model);
                            processTrafficData.add(model);
                        }
                        
                        // 更新流量数据
                        model.updateTraffic(tempData.getDownloadSpeed(), tempData.getUploadSpeed());
                    }
                    
                    // 将流量为 0 的进程设置为 0（保留在表格中，但显示为 0）
                    // 如果需要完全移除，可以添加逻辑：如果流量为 0 且持续一段时间，则移除
                    for (ProcessTrafficModel model : processTrafficMap.values()) {
                        boolean found = false;
                        for (ProcessTrafficData tempData : snapshot.values()) {
                            if (tempData.getProcessName().equals(model.getProcessName())) {
                                found = true;
                                break;
                            }
                        }
                        if (!found && model.getTotalSpeed() > 0) {
                            // 该进程在本秒没有流量，但保留显示（可以逐渐衰减）
                            // 这里选择保留，实际可以根据需求调整
                        }
                    }
                }
            });
            
        } catch (Exception e) {
            System.err.println("[MainController] 更新进程流量表格时发生错误: " + e.getMessage());
        }
    }
    
    /**
     * 根据进程名查找 PID
     * 
     * <p>通过 ProcessContextService 的连接映射表查找对应的 PID。</p>
     * 
     * @param processName 进程名称
     * @param processService 进程上下文服务
     * @return PID，如果找不到则返回 null
     */
    private Integer findPidByProcessName(String processName, ProcessContextService processService) {
        // 这里简化实现：通过进程名查找 PID
        // 实际实现中，可以通过 ProcessContextService 的统计信息或映射表查找
        // 或者通过 Java 的 ProcessHandle API 查找
        
        try {
            // 尝试通过进程名查找 PID（使用 ProcessHandle）
            java.util.Optional<java.lang.ProcessHandle> handleOpt = 
                java.lang.ProcessHandle.allProcesses()
                    .filter(ph -> {
                        try {
                            java.util.Optional<String> cmdOpt = ph.info().command();
                            if (cmdOpt.isPresent()) {
                                String cmd = cmdOpt.get();
                                String[] parts = cmd.replace("\\", "/").split("/");
                                if (parts.length > 0) {
                                    return parts[parts.length - 1].equalsIgnoreCase(processName);
                                }
                            }
                        } catch (Exception e) {
                            // 忽略异常
                        }
                        return false;
                    })
                    .findFirst();
            
            if (handleOpt.isPresent()) {
                return (int) handleOpt.get().pid();
            }
        } catch (Exception e) {
            // 查找失败，返回 null
        }
        
        return null;
    }
    
    /**
     * 带进度条的表格单元格
     * 用于显示流量数值，背景显示进度条效果
     * 根据流量占总带宽的比例动态切换颜色（绿/黄/红）
     */
    private class ProgressBarTableCell extends TableCell<ProcessTrafficModel, Double> {
        private final StackPane stackPane;
        private final Region progressBar;
        private final Label valueLabel;
        private final boolean isDownload;
        
        public ProgressBarTableCell(boolean isDownload) {
            this.isDownload = isDownload;
            
            // 创建进度条背景
            progressBar = new Region();
            progressBar.setStyle("-fx-background-color: linear-gradient(to right, #27AE60, #2ECC71); " +
                                "-fx-background-radius: 4px;");
            progressBar.setMaxHeight(Double.MAX_VALUE);
            progressBar.setMaxWidth(Double.MAX_VALUE);
            
            // 创建数值标签
            valueLabel = new Label();
            valueLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #2C3E50; -fx-font-weight: 600;");
            valueLabel.setAlignment(Pos.CENTER_RIGHT);
            
            // 创建堆叠面板
            stackPane = new StackPane();
            stackPane.setAlignment(Pos.CENTER_RIGHT);
            stackPane.setPadding(new Insets(6, 12, 6, 12));
            stackPane.getChildren().addAll(progressBar, valueLabel);
            
            // 添加样式类用于CSS控制
            stackPane.getStyleClass().add("progress-bar-cell");
        }
        
        @Override
        protected void updateItem(Double item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                stackPane.getStyleClass().removeAll("low-traffic", "medium-traffic", "high-traffic");
            } else {
                ProcessTrafficModel model = getTableView().getItems().get(getIndex());
                if (model == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                
                // 获取当前值和总速度
                double currentValue = isDownload ? model.getDownloadSpeed() : model.getUploadSpeed();
                double totalSpeed = model.getTotalSpeed();
                
                // 计算进度条宽度（基于总速度，最大100%）
                // 使用表格中最大流量作为基准（简化处理，使用固定阈值）
                double maxSpeed = HIGH_TRAFFIC_THRESHOLD * 2; // 2 MB/s 作为最大值
                double progress = Math.min(totalSpeed / maxSpeed, 1.0);
                
                // 设置进度条宽度
                progressBar.prefWidthProperty().bind(stackPane.widthProperty().multiply(progress));
                
                // 设置文本（右对齐）
                valueLabel.setText(isDownload ? model.getFormattedDownloadSpeed() : model.getFormattedUploadSpeed());
                
                // 根据流量占总带宽的比例动态切换颜色
                // 移除旧的样式类
                stackPane.getStyleClass().removeAll("low-traffic", "medium-traffic", "high-traffic");
                
                // 计算流量占比（基于总速度）
                double bandwidthRatio = totalSpeed / maxSpeed;
                
                if (bandwidthRatio > 0.7) {
                    // 高流量（>70%）- 红色
                    stackPane.getStyleClass().add("high-traffic");
                    progressBar.setStyle("-fx-background-color: linear-gradient(to right, #E74C3C, #EC7063); " +
                                        "-fx-background-radius: 4px;");
                } else if (bandwidthRatio > 0.3) {
                    // 中等流量（30%-70%）- 黄色
                    stackPane.getStyleClass().add("medium-traffic");
                    progressBar.setStyle("-fx-background-color: linear-gradient(to right, #F39C12, #F1C40F); " +
                                        "-fx-background-radius: 4px;");
                } else {
                    // 低流量（<30%）- 绿色
                    stackPane.getStyleClass().add("low-traffic");
                    progressBar.setStyle("-fx-background-color: linear-gradient(to right, #27AE60, #2ECC71); " +
                                        "-fx-background-radius: 4px;");
                }
                
                setGraphic(stackPane);
                setText(null);
                setAlignment(Pos.CENTER_RIGHT);
            }
        }
    }
    
    /**
     * 进程流量临时数据类（用于每秒汇总）
     */
    private static class ProcessTrafficData {
        private final String processName;
        private final Integer pid;
        private double downloadSpeed;
        private double uploadSpeed;
        
        public ProcessTrafficData(String processName, Integer pid, double downloadSpeed, double uploadSpeed) {
            this.processName = processName;
            this.pid = pid;
            this.downloadSpeed = downloadSpeed;
            this.uploadSpeed = uploadSpeed;
        }
        
        public void addTraffic(double downSpeed, double upSpeed) {
            this.downloadSpeed += downSpeed;
            this.uploadSpeed += upSpeed;
        }
        
        public String getProcessName() {
            return processName;
        }
        
        public Integer getPid() {
            return pid;
        }
        
        public double getDownloadSpeed() {
            return downloadSpeed;
        }
        
        public double getUploadSpeed() {
            return uploadSpeed;
        }
    }
    
    /**
     * 清理资源
     * 在 Controller 销毁时调用，确保所有线程和资源都被正确释放
     */
    public void cleanup() {
        // 停止监控
        stopMonitoring();
        
        // 停止进程上下文服务
        if (processContextService != null) {
            processContextService.stop();
        }
        
        // 关闭定时任务执行器
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                // 等待任务结束，最多等待5秒
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow(); // 强制关闭
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}

