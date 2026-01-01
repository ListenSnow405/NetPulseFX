package com.netpulse.netpulsefx;

import com.netpulse.netpulsefx.model.AIConfig;
import com.netpulse.netpulsefx.model.IPLocationInfo;
import com.netpulse.netpulsefx.service.AIService;
import com.netpulse.netpulsefx.service.DatabaseService;
import com.netpulse.netpulsefx.service.ExportService;
import com.netpulse.netpulsefx.service.IPLocationService;
import com.netpulse.netpulsefx.service.TrafficRecordQueryBuilder;
import com.netpulse.netpulsefx.util.MarkdownToHtmlConverter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
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
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Slider;
import javafx.scene.layout.GridPane;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Region;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

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
    
    /** 协议列 */
    @FXML
    private TableColumn<TrafficRecordDisplay, String> protocolColumn;
    
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
    
    /** IP 归属地信息显示容器 */
    @FXML
    private VBox ipLocationContainer;
    
    /** IP 归属地信息显示区域（结构化Label容器） */
    @FXML
    private VBox ipLocationInfoBox;
    
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
    
    /** 会话详情容器 */
    @FXML
    private VBox sessionDetailContainer;
    
    /** 会话详情占位符标签 */
    @FXML
    private Label sessionDetailPlaceholder;
    
    /** 会话信息网格 */
    @FXML
    private GridPane sessionInfoGrid;
    
    /** 会话速度网格 */
    @FXML
    private GridPane sessionSpeedGrid;
    
    /** 会话ID标签 */
    @FXML
    private Label sessionIdLabel;
    
    /** 会话网卡名称标签 */
    @FXML
    private Label sessionIfaceLabel;
    
    /** 会话开始时间标签 */
    @FXML
    private Label sessionStartTimeLabel;
    
    /** 会话结束时间标签 */
    @FXML
    private Label sessionEndTimeLabel;
    
    /** 会话持续时间标签 */
    @FXML
    private Label sessionDurationLabel;
    
    /** 会话记录数标签 */
    @FXML
    private Label sessionRecordCountLabel;
    
    /** 会话平均下行速度标签 */
    @FXML
    private Label sessionAvgDownLabel;
    
    /** 会话平均上行速度标签 */
    @FXML
    private Label sessionAvgUpLabel;
    
    /** 会话最大下行速度标签 */
    @FXML
    private Label sessionMaxDownLabel;
    
    /** 会话最大上行速度标签 */
    @FXML
    private Label sessionMaxUpLabel;
    
    // ========== 记录详情 UI 组件 ==========
    
    /** 记录详情容器 */
    @FXML
    private VBox recordDetailContainer;
    
    /** 记录信息网格 */
    @FXML
    private GridPane recordInfoGrid;
    
    /** 记录速度网格 */
    @FXML
    private GridPane recordSpeedGrid;
    
    /** 记录详情占位符 */
    @FXML
    private Label recordDetailPlaceholder;
    
    /** 记录ID标签 */
    @FXML
    private Label recordIdLabel;
    
    
    /** 记录捕获时间标签 */
    @FXML
    private Label recordCaptureTimeLabel;
    
    /** 记录协议类型标签 */
    @FXML
    private Label recordProtocolLabel;
    
    /** 记录源IP地址标签 */
    @FXML
    private Label recordSourceIpLabel;
    
    /** 记录目标IP地址标签 */
    @FXML
    private Label recordDestIpLabel;
    
    /** 记录进程名称标签 */
    @FXML
    private Label recordProcessNameLabel;
    
    /** 记录下行速度标签 */
    @FXML
    private Label recordDownSpeedLabel;
    
    /** 记录上行速度标签 */
    @FXML
    private Label recordUpSpeedLabel;
    
    /** 删除会话按钮 */
    @FXML
    private Button deleteSessionButton;
    
    /** AI 诊断按钮 */
    @FXML
    private Button aiDiagnosisButton;
    
    /** AI 诊断结果容器 */
    @FXML
    private VBox aiDiagnosisContainer;
    
    /** AI 诊断结果 WebView（用于显示 Markdown 格式的报告） */
    @FXML
    private javafx.scene.web.WebView aiDiagnosisWebView;
    
    /** AI 诊断进度指示器 */
    @FXML
    private ProgressIndicator aiDiagnosisProgress;
    
    /** AI 诊断取消按钮 */
    @FXML
    private Button cancelAIDiagnosisButton;
    
    /** 导出 Excel 按钮 */
    @FXML
    private Button exportExcelButton;
    
    /** 导出 PDF 按钮 */
    @FXML
    private Button exportPDFButton;
    
    // ========== 过滤器 UI 组件 ==========
    
    /** 协议过滤：TCP 复选框 */
    @FXML
    private CheckBox protocolTcpCheckBox;
    
    /** 协议过滤：UDP 复选框 */
    @FXML
    private CheckBox protocolUdpCheckBox;
    
    /** 协议过滤：其他 复选框 */
    @FXML
    private CheckBox protocolOtherCheckBox;
    
    /** 应用过滤：进程名称下拉框 */
    @FXML
    private ComboBox<String> processNameComboBox;
    
    /** 流量阈值：最小下载速度输入框 */
    @FXML
    private TextField minDownSpeedTextField;
    
    /** 流量阈值：最小下载速度滑块 */
    @FXML
    private Slider minDownSpeedSlider;
    
    /** 当前正在执行的 AI 诊断任务（用于取消功能） */
    private Task<String> currentDiagnosisTask;
    
    /** 当前会话的 AI 诊断报告内容（Markdown 格式） */
    private String currentAIDiagnosisReport;
    
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
    
    /** 当前正在查看的会话ID（如果正在查看详细记录） */
    private Integer currentViewingSessionId;
    
    /** 当前正在加载的会话ID（用于防抖处理，取消之前的任务） */
    private final AtomicReference<Integer> currentLoadingSessionId = new AtomicReference<>(null);
    
    /** 时间格式化器 */
    private final SimpleDateFormat timeFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    /** 防抖刷新任务的 Future（用于取消之前的任务） */
    private ScheduledFuture<?> debounceRefreshFuture;
    
    /** 防抖刷新任务执行器 */
    private final ScheduledExecutorService debounceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "FilterDebounceThread");
        t.setDaemon(true);
        return t;
    });
    
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
        downSpeedColumn.setCellValueFactory(new PropertyValueFactory<>("downSpeed"));
        upSpeedColumn.setCellValueFactory(new PropertyValueFactory<>("upSpeed"));
        sourceIpColumn.setCellValueFactory(new PropertyValueFactory<>("sourceIp"));
        destIpColumn.setCellValueFactory(new PropertyValueFactory<>("destIp"));
        processNameColumn.setCellValueFactory(new PropertyValueFactory<>("processName"));
        if (protocolColumn != null) {
            protocolColumn.setCellValueFactory(new PropertyValueFactory<>("protocol"));
        }
        captureTimeColumn.setCellValueFactory(new PropertyValueFactory<>("captureTime"));
        
        // 设置下行速度列的进度条显示
        downSpeedColumn.setCellFactory(column -> new SpeedProgressBarTableCell(true));
        
        // 设置上行速度列的进度条显示
        upSpeedColumn.setCellFactory(column -> new SpeedProgressBarTableCell(false));
        
        // 设置协议列的彩色标签显示
        if (protocolColumn != null) {
            protocolColumn.setCellFactory(column -> new ProtocolLabelTableCell());
        }
        
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
        
        // 初始化取消按钮（默认隐藏）
        if (cancelAIDiagnosisButton != null) {
            cancelAIDiagnosisButton.setVisible(false);
            cancelAIDiagnosisButton.setManaged(false);
        }
        
        // 初始化 IP 归属地显示区域（使用结构化Label）
        if (ipLocationInfoBox != null) {
            displayIpLocationPlaceholder();
        }
        
        // 初始化会话详情显示区域（默认显示占位符）
        if (sessionDetailPlaceholder != null) {
            sessionDetailPlaceholder.setVisible(true);
            sessionDetailPlaceholder.setManaged(true);
        }
        if (sessionInfoGrid != null) {
            sessionInfoGrid.setVisible(false);
            sessionInfoGrid.setManaged(false);
        }
        if (sessionSpeedGrid != null) {
            sessionSpeedGrid.setVisible(false);
            sessionSpeedGrid.setManaged(false);
        }
        
        // 初始化记录详情显示区域（默认显示占位符）
        if (recordDetailPlaceholder != null) {
            recordDetailPlaceholder.setVisible(true);
            recordDetailPlaceholder.setManaged(true);
        }
        if (recordInfoGrid != null) {
            recordInfoGrid.setVisible(false);
            recordInfoGrid.setManaged(false);
        }
        if (recordSpeedGrid != null) {
            recordSpeedGrid.setVisible(false);
            recordSpeedGrid.setManaged(false);
        }
        
        // 设置详细记录表格行选择监听器
        historyTable.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> {
                if (newValue != null) {
                    onTableRowSelected(newValue);
                } else {
                    clearRecordDetail();
                }
            }
        );
        
        // 设置会话列表表格为多选模式
        sessionTable.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
        
        // ========== 核心优化：选中会话即自动加载详细记录 ==========
        // 为会话列表的 selectedItemProperty 注册 ChangeListener
        // 当用户选中某一行时，自动加载并显示对应的明细记录
        sessionTable.getSelectionModel().selectedItemProperty().addListener(
            new ChangeListener<SessionDisplay>() {
                @Override
                public void changed(ObservableValue<? extends SessionDisplay> observable, 
                                  SessionDisplay oldValue, SessionDisplay newValue) {
                    // 当选中项发生变化时触发
                    if (newValue != null) {
                        // 有选中项：自动加载详细记录
                        onSessionSelectedAutoLoad(newValue);
                    } else {
                        // 没有选中项：清空详细记录表格
                        Platform.runLater(() -> {
                            historyData.clear();
                            currentViewingSessionId = null;
                            // 如果当前在详细记录标签页，显示提示信息
                            if (mainTabPane.getSelectionModel().getSelectedIndex() == 1) {
                                statusLabel.setText("请选择一个会话，详细记录将自动加载（切换到详细记录标签页查看）");
                            }
                        });
                    }
                }
            }
        );
        
        // 保留原有的多选监听器（用于更新删除按钮状态和会话详情文本）
        sessionTable.getSelectionModel().getSelectedItems().addListener(
            (ListChangeListener.Change<? extends SessionDisplay> change) -> {
                // 当选择发生变化时更新 UI
                updateDeleteButtonState();
                
                // 如果有选中项，显示第一个选中项的详情（数据卡片形式）
                if (!sessionTable.getSelectionModel().getSelectedItems().isEmpty()) {
                    SessionDisplay firstSelected = sessionTable.getSelectionModel().getSelectedItems().get(0);
                    onSessionRowSelected(firstSelected);
                } else {
                    clearSessionDetail();
                }
            }
        );
        
        // 初始化删除按钮状态（默认禁用）
        deleteSessionButton.setDisable(true);
        
        // 初始化过滤器
        initializeFilters();
        
        // 初始化时加载会话列表数据
        loadSessionList();
    }
    
    /**
     * 初始化过滤器组件
     * 设置监听器和初始状态
     */
    private void initializeFilters() {
        // 加载进程名列表到下拉框
        loadProcessNameList();
        
        // 设置协议复选框的监听器
        if (protocolTcpCheckBox != null) {
            protocolTcpCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> onFilterChanged());
        }
        if (protocolUdpCheckBox != null) {
            protocolUdpCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> onFilterChanged());
        }
        if (protocolOtherCheckBox != null) {
            protocolOtherCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> onFilterChanged());
        }
        
        // 设置应用下拉框的监听器
        if (processNameComboBox != null) {
            processNameComboBox.valueProperty().addListener((obs, oldVal, newVal) -> onFilterChanged());
        }
        
        // 设置流量阈值输入框的监听器
        if (minDownSpeedTextField != null) {
            minDownSpeedTextField.textProperty().addListener((obs, oldVal, newVal) -> {
                // 同步到滑块
                try {
                    if (newVal != null && !newVal.trim().isEmpty()) {
                        double value = Double.parseDouble(newVal.trim());
                        if (minDownSpeedSlider != null && value >= minDownSpeedSlider.getMin() && 
                            value <= minDownSpeedSlider.getMax()) {
                            minDownSpeedSlider.setValue(value);
                        }
                    }
                } catch (NumberFormatException e) {
                    // 忽略无效输入
                }
                onFilterChanged();
            });
        }
        
        // 设置流量阈值滑块的监听器
        if (minDownSpeedSlider != null) {
            minDownSpeedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                // 同步到输入框
                if (minDownSpeedTextField != null) {
                    minDownSpeedTextField.setText(String.format("%.1f", newVal.doubleValue()));
                }
                onFilterChanged();
            });
        }
    }
    
    /**
     * 从数据库加载进程名列表
     */
    private void loadProcessNameList() {
        if (processNameComboBox == null) {
            return;
        }
        
        databaseService.getDistinctProcessNames()
            .thenAccept(processNames -> {
                Platform.runLater(() -> {
                    processNameComboBox.getItems().clear();
                    processNameComboBox.getItems().add("全部"); // 添加"全部"选项表示不过滤
                    processNameComboBox.getItems().addAll(processNames);
                });
            })
            .exceptionally(e -> {
                Platform.runLater(() -> {
                    System.err.println("[HistoryController] 加载进程名列表失败: " + e.getMessage());
                });
                return null;
            });
    }
    
    /**
     * 过滤器变化事件处理（防抖刷新）
     * 当用户调整过滤器参数时，延迟 300ms 后自动刷新显示结果
     */
    private void onFilterChanged() {
        // 取消之前的刷新任务
        if (debounceRefreshFuture != null && !debounceRefreshFuture.isCancelled()) {
            debounceRefreshFuture.cancel(false);
        }
        
        // 创建新的延迟刷新任务（300ms 防抖）
        debounceRefreshFuture = debounceExecutor.schedule(() -> {
            Platform.runLater(() -> {
                // 只在详细记录标签页时刷新
                if (mainTabPane.getSelectionModel().getSelectedIndex() == 1) {
                    applyFiltersAndRefresh();
                }
            });
        }, 300, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 应用过滤器并刷新表格
     */
    private void applyFiltersAndRefresh() {
        // 如果有当前查看的会话，使用会话过滤；否则加载所有记录
        if (currentViewingSessionId != null) {
            loadSessionDetailsWithFilter(currentViewingSessionId);
        } else {
            loadHistoryDataWithFilter();
        }
    }
    
    /**
     * 清除过滤按钮点击事件
     */
    @FXML
    protected void onClearFiltersClick() {
        // 清除所有过滤器
        if (protocolTcpCheckBox != null) protocolTcpCheckBox.setSelected(false);
        if (protocolUdpCheckBox != null) protocolUdpCheckBox.setSelected(false);
        if (protocolOtherCheckBox != null) protocolOtherCheckBox.setSelected(false);
        if (processNameComboBox != null) processNameComboBox.setValue("全部");
        if (minDownSpeedTextField != null) minDownSpeedTextField.setText("");
        if (minDownSpeedSlider != null) minDownSpeedSlider.setValue(0);
        
        // 立即刷新（不需要防抖）
        applyFiltersAndRefresh();
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
            // 如果有当前查看的会话，加载该会话的记录；否则加载所有记录
            if (currentViewingSessionId != null) {
                loadSessionDetails(currentViewingSessionId);
            } else {
                loadHistoryData();
            }
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
        
        // 每次使用前，尝试从全局配置管理器获取最新的 AI 服务
        com.netpulse.netpulsefx.service.AIConfigManager configManager = 
            com.netpulse.netpulsefx.service.AIConfigManager.getInstance();
        if (configManager.isConfigured()) {
            aiService = configManager.getAIService();
        }
        
        // 检查 AI 服务是否可用
        if (aiService == null) {
            showAlert(Alert.AlertType.WARNING, "AI 服务未配置", 
                    "AI 服务未初始化。\n\n请先在主界面配置 API 参数，或检查环境变量配置：\n" +
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
        // 禁用按钮，显示进度指示器和取消按钮
        aiDiagnosisButton.setDisable(true);
        if (aiDiagnosisContainer != null) {
            aiDiagnosisContainer.setVisible(true);
            aiDiagnosisContainer.setManaged(true);
        }
        if (aiDiagnosisProgress != null) {
            aiDiagnosisProgress.setVisible(true);
        }
        if (cancelAIDiagnosisButton != null) {
            cancelAIDiagnosisButton.setVisible(true);
            cancelAIDiagnosisButton.setManaged(true);
            cancelAIDiagnosisButton.setDisable(false);
        }
        if (aiDiagnosisWebView != null) {
            // 在 WebView 中显示加载提示
            loadAIDiagnosisToWebView("## 正在加载...\n\n正在加载会话数据并生成 AI 诊断报告，请稍候...");
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
        
        // 保存任务引用，以便可以取消
        currentDiagnosisTask = diagnosisTask;
        
        // 监听任务取消事件
        diagnosisTask.setOnCancelled(e -> {
            Platform.runLater(() -> {
                if (aiDiagnosisWebView != null) {
                    loadAIDiagnosisToWebView("## 诊断已取消\n\nAI 诊断已被用户取消。");
                }
                if (aiDiagnosisProgress != null) {
                    aiDiagnosisProgress.setVisible(false);
                }
                if (cancelAIDiagnosisButton != null) {
                    cancelAIDiagnosisButton.setVisible(false);
                    cancelAIDiagnosisButton.setManaged(false);
                }
                aiDiagnosisButton.setDisable(false);
                statusLabel.setText("AI 诊断已取消");
                currentDiagnosisTask = null;
            });
        });
        
        // 任务成功完成
        diagnosisTask.setOnSucceeded(e -> {
            try {
                String markdownReport = diagnosisTask.getValue();
                // 保存原始 Markdown 内容，用于 PDF 导出
                currentAIDiagnosisReport = markdownReport;
                
                Platform.runLater(() -> {
                    if (aiDiagnosisWebView != null) {
                        // 将 Markdown 转换为 HTML 并在 WebView 中显示
                        loadAIDiagnosisToWebView(markdownReport);
                    }
                    if (aiDiagnosisProgress != null) {
                        aiDiagnosisProgress.setVisible(false);
                    }
                    if (cancelAIDiagnosisButton != null) {
                        cancelAIDiagnosisButton.setVisible(false);
                        cancelAIDiagnosisButton.setManaged(false);
                    }
                    aiDiagnosisButton.setDisable(false);
                    statusLabel.setText("AI 诊断完成");
                    currentDiagnosisTask = null;
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    handleAIDiagnosisError("生成诊断报告失败: " + ex.getMessage(), ex);
                });
            }
        });
        
        // 任务失败
        diagnosisTask.setOnFailed(e -> {
            // 检查任务是否被取消
            if (diagnosisTask.isCancelled()) {
                return; // 如果已取消，不处理失败事件
            }
            
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
     * 将 AI 诊断结果加载到 WebView 中
     * 
     * <p>此方法将 Markdown 格式的 AI 诊断结果转换为 HTML 并加载到 WebView 中显示。</p>
     * 
     * @param markdownContent Markdown 格式的 AI 诊断结果
     */
    private void loadAIDiagnosisToWebView(String markdownContent) {
        if (aiDiagnosisWebView != null && markdownContent != null) {
            String htmlContent = MarkdownToHtmlConverter.convertToHtml(markdownContent);
            aiDiagnosisWebView.getEngine().loadContent(htmlContent);
        }
    }
    
    /**
     * 处理 AI 诊断错误
     */
    private void handleAIDiagnosisError(String message, Throwable exception) {
        if (aiDiagnosisWebView != null) {
            // 在 WebView 中显示错误信息（使用 Markdown 格式）
            String errorMarkdown = "## 错误\n\n**" + message + "**";
            loadAIDiagnosisToWebView(errorMarkdown);
        }
        if (aiDiagnosisProgress != null) {
            aiDiagnosisProgress.setVisible(false);
        }
        if (cancelAIDiagnosisButton != null) {
            cancelAIDiagnosisButton.setVisible(false);
            cancelAIDiagnosisButton.setManaged(false);
        }
        aiDiagnosisButton.setDisable(false);
        statusLabel.setText("AI 诊断失败");
        currentDiagnosisTask = null;
        
        // 显示错误对话框
        showAlert(Alert.AlertType.ERROR, "AI 诊断失败", message);
        
        if (exception != null) {
            exception.printStackTrace();
        }
    }
    
    /**
     * 导出 Excel 按钮点击事件
     */
    @FXML
    protected void onExportExcelClick() {
        SessionDisplay selectedSession = sessionTable.getSelectionModel().getSelectedItem();
        if (selectedSession == null) {
            showAlert(Alert.AlertType.WARNING, "未选择会话", "请先选择一个监控会话进行导出。");
            return;
        }
        
        // 异步查询会话信息以获取网卡名称
        Task<String> prepareFileNameTask = new Task<String>() {
            @Override
            protected String call() throws Exception {
                // 查询会话信息
                List<DatabaseService.MonitoringSession> sessions = databaseService.getAllSessions().get();
                DatabaseService.MonitoringSession session = sessions.stream()
                        .filter(s -> s.getSessionId() == selectedSession.getSessionId())
                        .findFirst()
                        .orElse(null);
                
                if (session == null) {
                    throw new Exception("会话不存在: session_id=" + selectedSession.getSessionId());
                }
                
                // 生成文件名：NetPulse_Data_[网卡名]_Session[ID]_[yyyyMMdd_HHmm].xlsx
                String ifaceName = session.getIfaceName() != null ? session.getIfaceName() : "Unknown";
                // 过滤网卡名称中的非法字符（如 \ 或 /）
                ifaceName = sanitizeFileName(ifaceName);
                
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date());
                String fileName = String.format("NetPulse_Data_%s_Session%d_%s.xlsx", 
                    ifaceName, session.getSessionId(), timestamp);
                
                return fileName;
            }
        };
        
        // 文件名准备完成后，显示文件选择对话框
        prepareFileNameTask.setOnSucceeded(e -> {
            String defaultFileName = prepareFileNameTask.getValue();
            
            // 在状态栏显示准备信息
            if (statusLabel != null) {
                statusLabel.setText("已准备好导出文件：" + defaultFileName);
            }
            
            // 使用 FileChooser 选择保存路径
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("导出 Excel 文件");
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel 文件", "*.xlsx")
            );
            fileChooser.setInitialFileName(defaultFileName);
            
            Stage stage = (Stage) exportExcelButton.getScene().getWindow();
            File file = fileChooser.showSaveDialog(stage);
            
            if (file == null) {
                return; // 用户取消了选择
            }
            
            // 执行导出
            executeExcelExport(selectedSession, file);
        });
        
        prepareFileNameTask.setOnFailed(e -> {
            Throwable exception = prepareFileNameTask.getException();
            String errorMsg = exception != null ? exception.getMessage() : "未知错误";
            showAlert(Alert.AlertType.ERROR, "准备导出失败", 
                "准备导出文件时发生错误：\n" + errorMsg);
            if (statusLabel != null) {
                statusLabel.setText("准备导出失败: " + errorMsg);
            }
        });
        
        // 启动准备任务
        Thread prepareThread = new Thread(prepareFileNameTask);
        prepareThread.setDaemon(true);
        prepareThread.start();
        
        if (statusLabel != null) {
            statusLabel.setText("正在准备导出文件...");
        }
    }
    
    /**
     * 执行 Excel 导出
     */
    private void executeExcelExport(SessionDisplay selectedSession, File file) {
        // 异步执行导出任务
        Task<Void> exportTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("正在查询会话数据...");
                
                // 查询会话信息
                List<DatabaseService.MonitoringSession> sessions = databaseService.getAllSessions().get();
                DatabaseService.MonitoringSession session = sessions.stream()
                        .filter(s -> s.getSessionId() == selectedSession.getSessionId())
                        .findFirst()
                        .orElse(null);
                
                if (session == null) {
                    throw new Exception("会话不存在: session_id=" + selectedSession.getSessionId());
                }
                
                updateMessage("正在查询流量明细记录...");
                
                // 查询该会话的所有记录
                List<DatabaseService.TrafficRecord> records = databaseService.getRecordsBySession(
                        selectedSession.getSessionId()).get();
                
                updateMessage("正在导出 Excel 文件...");
                
                // 执行导出
                ExportService.exportSessionToExcel(session, records, file);
                
                return null;
            }
        };
        
        // 任务成功完成
        exportTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                showAlert(Alert.AlertType.INFORMATION, "导出成功", 
                    "Excel 文件已成功导出到：\n" + file.getAbsolutePath());
                statusLabel.setText("Excel 导出完成");
            });
        });
        
        // 任务失败
        exportTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                Throwable exception = exportTask.getException();
                String errorMsg = exception != null ? exception.getMessage() : "未知错误";
                statusLabel.setText("Excel 导出失败: " + errorMsg);
                showAlert(Alert.AlertType.ERROR, "导出失败", 
                    "导出 Excel 文件时发生错误：\n" + errorMsg);
                
                if (exception != null) {
                    exception.printStackTrace();
                }
            });
        });
        
        // 启动任务
        Thread exportThread = new Thread(exportTask);
        exportThread.setDaemon(true);
        exportThread.start();
        
        statusLabel.setText("正在导出 Excel 文件...");
    }
    
    /**
     * 导出 PDF 按钮点击事件
     */
    @FXML
    protected void onExportPDFClick() {
        SessionDisplay selectedSession = sessionTable.getSelectionModel().getSelectedItem();
        if (selectedSession == null) {
            showAlert(Alert.AlertType.WARNING, "未选择会话", "请先选择一个监控会话进行导出。");
            return;
        }
        
        // 检查是否有 AI 诊断结果
        if (currentAIDiagnosisReport == null || currentAIDiagnosisReport.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "无 AI 诊断结果", 
                "请先执行 AI 诊断，生成分析报告后再导出 PDF。");
            return;
        }
        
        String aiReportContent = currentAIDiagnosisReport;
        
        // 生成文件名：NetPulse_Report_[AI模型名]_[yyyyMMdd_HHmm].pdf
        String modelName = "未知模型";
        // 从全局配置管理器获取 AI 模型名称
        com.netpulse.netpulsefx.service.AIConfigManager configManager = 
            com.netpulse.netpulsefx.service.AIConfigManager.getInstance();
        if (configManager.isConfigured()) {
            com.netpulse.netpulsefx.model.AIConfig config = configManager.getConfig();
            if (config != null && config.getModel() != null) {
                modelName = config.getModel();
            }
        } else if (aiService != null && aiService.getConfig() != null) {
            modelName = aiService.getConfig().getModel();
        }
        
        // 智能识别 Gemini 2.5 模型，统一显示为 "Gemini_2.5"
        String modelLower = modelName.toLowerCase();
        if (modelLower.contains("gemini-2.5") || modelLower.contains("gemini 2.5")) {
            // 提取版本信息（flash 或 pro）
            if (modelLower.contains("pro")) {
                modelName = "Gemini_2.5_Pro";
            } else {
                modelName = "Gemini_2.5_Flash";
            }
        }
        
        // 清理模型名称中的特殊字符，确保文件名合法
        modelName = sanitizeFileName(modelName);
        
        // 时间戳格式：yyyyMMdd_HHmm（精确到分钟）
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date());
        String defaultFileName = String.format("NetPulse_Report_%s_%s.pdf", modelName, timestamp);
        
        // 在状态栏显示准备信息
        if (statusLabel != null) {
            statusLabel.setText("已准备好导出文件：" + defaultFileName);
        }
        
        // 使用 FileChooser 选择保存路径
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("导出 PDF 文件");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PDF 文件", "*.pdf")
        );
        fileChooser.setInitialFileName(defaultFileName);
        
        Stage stage = (Stage) exportPDFButton.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);
        
        if (file == null) {
            return; // 用户取消了选择
        }
        
        // 执行导出
        executePDFExport(selectedSession, aiReportContent, file);
    }
    
    /**
     * 执行 PDF 导出
     */
    private void executePDFExport(SessionDisplay selectedSession, String aiReportContent, File file) {
        // 异步执行导出任务
        Task<Void> exportTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("正在查询会话数据...");
                
                // 查询会话信息
                List<DatabaseService.MonitoringSession> sessions = databaseService.getAllSessions().get();
                DatabaseService.MonitoringSession session = sessions.stream()
                        .filter(s -> s.getSessionId() == selectedSession.getSessionId())
                        .findFirst()
                        .orElse(null);
                
                if (session == null) {
                    throw new Exception("会话不存在: session_id=" + selectedSession.getSessionId());
                }
                
                updateMessage("正在导出 PDF 文件...");
                
                // 执行导出
                ExportService.exportAIReportToPDF(session, aiReportContent, file);
                
                return null;
            }
        };
        
        // 任务成功完成
        exportTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                showAlert(Alert.AlertType.INFORMATION, "导出成功", 
                    "PDF 文件已成功导出到：\n" + file.getAbsolutePath());
                statusLabel.setText("PDF 导出完成");
            });
        });
        
        // 任务失败
        exportTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                Throwable exception = exportTask.getException();
                String errorMsg = exception != null ? exception.getMessage() : "未知错误";
                statusLabel.setText("PDF 导出失败: " + errorMsg);
                showAlert(Alert.AlertType.ERROR, "导出失败", 
                    "导出 PDF 文件时发生错误：\n" + errorMsg);
                
                if (exception != null) {
                    exception.printStackTrace();
                }
            });
        });
        
        // 启动任务
        Thread exportThread = new Thread(exportTask);
        exportThread.setDaemon(true);
        exportThread.start();
        
        statusLabel.setText("正在导出 PDF 文件...");
    }
    
    /**
     * 取消 AI 诊断按钮点击事件
     * 
     * <p>功能说明：</p>
     * <ul>
     *   <li>取消当前正在执行的 AI 诊断任务</li>
     *   <li>停止后台线程的执行</li>
     *   <li>更新 UI 状态，恢复按钮可用性</li>
     *   <li>显示取消提示信息</li>
     * </ul>
     */
    @FXML
    protected void onCancelAIDiagnosisClick() {
        if (currentDiagnosisTask != null && !currentDiagnosisTask.isDone()) {
            // 取消任务
            boolean cancelled = currentDiagnosisTask.cancel(true);
            
            if (cancelled) {
                System.out.println("[HistoryController] AI 诊断任务已取消");
                statusLabel.setText("正在取消 AI 诊断...");
                
                // 禁用取消按钮，防止重复点击
                if (cancelAIDiagnosisButton != null) {
                    cancelAIDiagnosisButton.setDisable(true);
                }
            } else {
                // 如果无法取消（可能已经完成或失败），显示提示
                showAlert(Alert.AlertType.INFORMATION, "无法取消", 
                    "AI 诊断任务已经完成或失败，无法取消。");
            }
        } else {
            // 没有正在执行的任务
            showAlert(Alert.AlertType.INFORMATION, "无任务可取消", 
                "当前没有正在执行的 AI 诊断任务。");
        }
    }
    
    /**
     * 初始化 AI 服务
     * 优先从保存的配置文件加载，然后使用全局配置管理器中的配置，最后从环境变量读取
     */
    private void initializeAIService() {
        try {
            // 首先尝试从文件加载配置
            com.netpulse.netpulsefx.service.AIConfigManager configManager = 
                com.netpulse.netpulsefx.service.AIConfigManager.getInstance();
            
            if (configManager.loadConfigFromFile()) {
                // 成功从文件加载配置
                aiService = configManager.getAIService();
                System.out.println("[HistoryController] AI 服务已初始化（从配置文件加载）");
                return;
            }
            
            // 如果文件加载失败，检查全局配置管理器是否有配置
            if (configManager.isConfigured()) {
                aiService = configManager.getAIService();
                System.out.println("[HistoryController] AI 服务已初始化（使用全局配置管理器）");
                return;
            }
            
            // 如果全局配置管理器中没有配置，则从环境变量读取
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
                
                AIConfig config = new AIConfig(finalProvider, apiEndpoint, finalApiKey, finalModel);
                aiService = new AIService(config);
                // 同时更新全局配置管理器
                configManager.updateConfig(config);
                
                System.out.println("[HistoryController] AI 服务已初始化（环境变量配置）");
            } else {
                // 环境变量未设置，根据 provider 使用默认配置
                String finalProvider = (apiProvider != null && !apiProvider.isEmpty()) 
                    ? apiProvider : "deepseek";
                
                AIConfig config;
                if ("deepseek".equalsIgnoreCase(finalProvider)) {
                    config = AIConfig.defaultDeepSeek();
                    String finalApiKey = (apiKey != null && !apiKey.isEmpty()) ? apiKey : "";
                    String finalModel = (model != null && !model.isEmpty()) ? model : config.getModel();
                    config = new AIConfig(
                        config.getProvider(),
                        config.getApiEndpoint(),
                        finalApiKey,
                        finalModel
                    );
                } else if ("openai".equalsIgnoreCase(finalProvider)) {
                    config = AIConfig.defaultOpenAI();
                    String finalApiKey = (apiKey != null && !apiKey.isEmpty()) ? apiKey : "";
                    String finalModel = (model != null && !model.isEmpty()) ? model : config.getModel();
                    config = new AIConfig(
                        config.getProvider(),
                        config.getApiEndpoint(),
                        finalApiKey,
                        finalModel
                    );
                } else if ("gemini".equalsIgnoreCase(finalProvider)) {
                    config = AIConfig.defaultGemini();
                    String finalApiKey = (apiKey != null && !apiKey.isEmpty()) ? apiKey : "";
                    String finalModel = (model != null && !model.isEmpty()) ? model : config.getModel();
                    config = new AIConfig(
                        config.getProvider(),
                        config.getApiEndpoint(),
                        finalApiKey,
                        finalModel
                    );
                } else {
                    // 默认使用 Ollama（本地）
                    config = AIConfig.defaultOllama();
                    String finalModel = (model != null && !model.isEmpty()) ? model : "llama2";
                    config = new AIConfig(
                        config.getProvider(),
                        config.getApiEndpoint(),
                        config.getApiKey(),
                        finalModel
                    );
                }
                
                aiService = new AIService(config);
                // 同时更新全局配置管理器
                configManager.updateConfig(config);
                
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
     * 删除会话按钮点击事件（支持单个和批量删除）
     */
    @FXML
    protected void onDeleteSessionClick() {
        ObservableList<SessionDisplay> selectedSessions = sessionTable.getSelectionModel().getSelectedItems();
        
        if (selectedSessions.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "未选择会话", "请先选择一个或多个要删除的监控会话。\n\n提示：按住 Ctrl 键可多选。");
            return;
        }
        
        int selectedCount = selectedSessions.size();
        boolean isBatchDelete = selectedCount > 1;
        
        // 构建确认对话框内容
        StringBuilder content = new StringBuilder();
        if (isBatchDelete) {
            content.append(String.format("确定要删除 %d 个监控会话吗？\n\n", selectedCount));
            
            // 计算总记录数
            int totalRecords = selectedSessions.stream()
                    .mapToInt(SessionDisplay::getRecordCount)
                    .sum();
            
            content.append(String.format("总共将删除 %d 条明细记录。\n\n", totalRecords));
            
            // 列出前 5 个会话的信息（避免对话框过长）
            content.append("选中的会话：\n");
            int displayCount = Math.min(5, selectedCount);
            for (int i = 0; i < displayCount; i++) {
                SessionDisplay session = selectedSessions.get(i);
                content.append(String.format("  - 会话 #%d (%s, %d 条记录)\n",
                    session.getSessionId(),
                    session.getIfaceName(),
                    session.getRecordCount()));
            }
            if (selectedCount > 5) {
                content.append(String.format("  ... 还有 %d 个会话\n", selectedCount - 5));
            }
        } else {
            SessionDisplay selectedSession = selectedSessions.get(0);
            content.append(String.format(
                "确定要删除会话 #%d 吗？\n\n" +
                "网卡: %s\n" +
                "开始时间: %s\n" +
                "记录数: %d 条\n",
                selectedSession.getSessionId(),
                selectedSession.getIfaceName(),
                selectedSession.getStartTime(),
                selectedSession.getRecordCount()
            ));
        }
        
        content.append("\n注意：删除会话将同时删除所有关联的明细记录，此操作不可恢复！");
        
        // 显示确认对话框
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("确认删除");
        confirmAlert.setHeaderText(isBatchDelete ? "批量删除监控会话" : "删除监控会话");
        confirmAlert.setContentText(content.toString());
        
        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                if (isBatchDelete) {
                    deleteSessions(selectedSessions);
                } else {
                    deleteSession(selectedSessions.get(0).getSessionId());
                }
            }
        });
    }
    
    /**
     * 更新删除按钮的状态和文本
     */
    private void updateDeleteButtonState() {
        int selectedCount = sessionTable.getSelectionModel().getSelectedItems().size();
        if (selectedCount == 0) {
            deleteSessionButton.setDisable(true);
            deleteSessionButton.setText("删除会话");
        } else if (selectedCount == 1) {
            deleteSessionButton.setDisable(false);
            deleteSessionButton.setText("删除会话");
        } else {
            deleteSessionButton.setDisable(false);
            deleteSessionButton.setText(String.format("批量删除 (%d)", selectedCount));
        }
    }
    
    /**
     * 批量删除指定的监控会话
     * 
     * @param selectedSessions 选中的会话列表
     */
    private void deleteSessions(ObservableList<SessionDisplay> selectedSessions) {
        // 提取会话 ID 列表
        List<Integer> sessionIds = selectedSessions.stream()
                .map(SessionDisplay::getSessionId)
                .collect(java.util.stream.Collectors.toList());
        
        int totalCount = sessionIds.size();
        
        // 禁用删除按钮，防止重复点击
        deleteSessionButton.setDisable(true);
        statusLabel.setText(String.format("正在删除 %d 个会话...", totalCount));
        
        databaseService.deleteSessions(sessionIds)
            .thenAccept(deletedCount -> {
                Platform.runLater(() -> {
                    if (deletedCount > 0) {
                        // 删除成功，从列表中移除已删除的会话
                        sessionData.removeAll(selectedSessions);
                        statusLabel.setText(String.format("成功删除 %d 个会话", deletedCount));
                        
                        // 清空会话详情显示
                        clearSessionDetail();
                        
                        // 如果当前查看的是已删除会话的详细记录，清空详细记录表格
                        if (mainTabPane.getSelectionModel().getSelectedIndex() == 1) {
                            historyData.clear();
                        }
                        
                        // 清除选择
                        sessionTable.getSelectionModel().clearSelection();
                    } else {
                        statusLabel.setText("删除失败：没有会话被删除");
                        showAlert(Alert.AlertType.ERROR, "删除失败", 
                                "没有会话被删除，请检查会话是否仍然存在。");
                    }
                    
                    // 恢复删除按钮状态
                    updateDeleteButtonState();
                });
            })
            .exceptionally(e -> {
                Platform.runLater(() -> {
                    statusLabel.setText("批量删除失败: " + e.getMessage());
                    showAlert(Alert.AlertType.ERROR, "批量删除失败", 
                            "删除会话时发生错误：\n" + e.getMessage());
                    
                    // 恢复删除按钮状态
                    updateDeleteButtonState();
                });
                return null;
            });
    }
    
    /**
     * 删除指定的监控会话（单个删除）
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
                        clearSessionDetail();
                        
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
                    
                    // 恢复删除按钮状态
                    updateDeleteButtonState();
                });
            })
            .exceptionally(e -> {
                Platform.runLater(() -> {
                    statusLabel.setText("删除失败: " + e.getMessage());
                    showAlert(Alert.AlertType.ERROR, "删除失败", 
                            "删除会话时发生错误：\n" + e.getMessage());
                    
                    // 恢复删除按钮状态
                    updateDeleteButtonState();
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
            displayIpLocationPlaceholder();
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
        // 显示记录详情
        loadRecordDetails(selectedRecord);
        
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
            displayIpLocationPlaceholder();
            ipQueryStatusLabel.setText("该记录没有IP地址信息");
        }
    }
    
    /**
     * 加载并显示记录详情
     * 
     * @param record 选中的记录
     */
    private void loadRecordDetails(TrafficRecordDisplay record) {
        if (record == null) {
            clearRecordDetail();
            return;
        }
        
        // 隐藏占位符，显示详情网格
        if (recordDetailPlaceholder != null) {
            recordDetailPlaceholder.setVisible(false);
            recordDetailPlaceholder.setManaged(false);
        }
        if (recordInfoGrid != null) {
            recordInfoGrid.setVisible(true);
            recordInfoGrid.setManaged(true);
        }
        if (recordSpeedGrid != null) {
            recordSpeedGrid.setVisible(true);
            recordSpeedGrid.setManaged(true);
        }
        
        // 填充基本信息
        if (recordIdLabel != null) {
            recordIdLabel.setText(record.getId() != null ? record.getId().toString() : "--");
        }
        if (recordCaptureTimeLabel != null) {
            recordCaptureTimeLabel.setText(record.getCaptureTime() != null && !record.getCaptureTime().isEmpty() 
                ? record.getCaptureTime() : "--");
        }
        if (recordProtocolLabel != null) {
            String protocol = record.getProtocol();
            if (protocol != null && !protocol.isEmpty()) {
                recordProtocolLabel.setText(protocol);
                // 根据协议类型设置颜色（与表格中的协议标签样式一致）
                switch (protocol.toUpperCase()) {
                    case "TCP":
                        recordProtocolLabel.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; " +
                            "-fx-padding: 2px 8px; -fx-background-radius: 3px; -fx-font-size: 12px;");
                        break;
                    case "UDP":
                        recordProtocolLabel.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; " +
                            "-fx-padding: 2px 8px; -fx-background-radius: 3px; -fx-font-size: 12px;");
                        break;
                    default:
                        recordProtocolLabel.setStyle("-fx-background-color: #757575; -fx-text-fill: white; " +
                            "-fx-padding: 2px 8px; -fx-background-radius: 3px; -fx-font-size: 12px;");
                        break;
                }
            } else {
                recordProtocolLabel.setText("--");
                recordProtocolLabel.setStyle("");
            }
        }
        if (recordSourceIpLabel != null) {
            recordSourceIpLabel.setText(record.getSourceIp() != null && !record.getSourceIp().isEmpty() 
                ? record.getSourceIp() : "--");
        }
        if (recordDestIpLabel != null) {
            recordDestIpLabel.setText(record.getDestIp() != null && !record.getDestIp().isEmpty() 
                ? record.getDestIp() : "--");
        }
        if (recordProcessNameLabel != null) {
            recordProcessNameLabel.setText(record.getProcessName() != null && !record.getProcessName().isEmpty() 
                ? record.getProcessName() : "--");
        }
        
        // 填充速度信息
        if (recordDownSpeedLabel != null) {
            if (record.getDownSpeed() != null) {
                recordDownSpeedLabel.setText(String.format("%.2f", record.getDownSpeed()));
            } else {
                recordDownSpeedLabel.setText("--");
            }
        }
        if (recordUpSpeedLabel != null) {
            if (record.getUpSpeed() != null) {
                recordUpSpeedLabel.setText(String.format("%.2f", record.getUpSpeed()));
            } else {
                recordUpSpeedLabel.setText("--");
            }
        }
    }
    
    /**
     * 清空记录详情显示
     */
    private void clearRecordDetail() {
        // 显示占位符，隐藏详情网格
        if (recordDetailPlaceholder != null) {
            recordDetailPlaceholder.setVisible(true);
            recordDetailPlaceholder.setManaged(true);
        }
        if (recordInfoGrid != null) {
            recordInfoGrid.setVisible(false);
            recordInfoGrid.setManaged(false);
        }
        if (recordSpeedGrid != null) {
            recordSpeedGrid.setVisible(false);
            recordSpeedGrid.setManaged(false);
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
        displayIpLocationLoading();
        
        // 使用异步查询，避免阻塞 UI 线程
        ipLocationService.queryLocationAsync(ip)
            .thenAccept(locationInfo -> {
                // 在主线程中更新 UI
                Platform.runLater(() -> {
                    if (locationInfo.isSuccess()) {
                        // 查询成功，使用结构化Label显示信息
                        displayIpLocationInfo(locationInfo);
                        ipQueryStatusLabel.setText("查询完成 - " + locationInfo.getShortLocation());
                    } else {
                        // 查询失败
                        String errorMsg = locationInfo.getErrorMessage();
                        displayIpLocationError(errorMsg);
                        ipQueryStatusLabel.setText("查询失败 - " + (errorMsg != null ? errorMsg : "未知原因"));
                    }
                    
                    // 恢复按钮状态
                    queryIpButton.setDisable(false);
                });
            })
            .exceptionally(throwable -> {
                // 处理异常
                Platform.runLater(() -> {
                    displayIpLocationError("查询过程中发生异常: " + 
                        (throwable.getMessage() != null ? throwable.getMessage() : "未知错误"));
                    ipQueryStatusLabel.setText("查询异常");
                    queryIpButton.setDisable(false);
                });
                return null;
            });
    }
    
    /**
     * 显示IP归属地占位符信息
     */
    private void displayIpLocationPlaceholder() {
        if (ipLocationInfoBox == null) return;
        
        ipLocationInfoBox.getChildren().clear();
        
        Label placeholder = new Label("IP 归属地信息将显示在这里...\n\n提示：\n1. 在输入框中输入 IP 地址并点击\"查询\"按钮\n2. 或点击表格中的任意行，系统将尝试从该行数据中提取 IP 地址");
        placeholder.setWrapText(true);
        placeholder.setStyle("-fx-font-size: 12px; -fx-text-fill: #757575; -fx-padding: 10px;");
        ipLocationInfoBox.getChildren().add(placeholder);
    }
    
    /**
     * 显示IP归属地加载状态
     */
    private void displayIpLocationLoading() {
        if (ipLocationInfoBox == null) return;
        
        ipLocationInfoBox.getChildren().clear();
        
        Label loadingLabel = new Label("正在查询，请稍候...");
        loadingLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #0078D7; -fx-font-weight: 500; -fx-padding: 10px;");
        ipLocationInfoBox.getChildren().add(loadingLabel);
    }
    
    /**
     * 显示IP归属地信息（结构化Label）
     */
    private void displayIpLocationInfo(IPLocationInfo locationInfo) {
        if (ipLocationInfoBox == null) return;
        
        ipLocationInfoBox.getChildren().clear();
        
        // IP 地址
        addInfoLabel("IP 地址", locationInfo.getIp(), true);
        
        // 国家
        if (locationInfo.getCountry() != null && !locationInfo.getCountry().isEmpty() && !locationInfo.getCountry().equals("未知")) {
            addInfoLabel("国家", locationInfo.getCountry(), false);
        }
        
        // 省份/州
        if (locationInfo.getRegion() != null && !locationInfo.getRegion().isEmpty() && !locationInfo.getRegion().equals("未知")) {
            addInfoLabel("省份/州", locationInfo.getRegion(), false);
        }
        
        // 城市
        if (locationInfo.getCity() != null && !locationInfo.getCity().isEmpty() && !locationInfo.getCity().equals("未知")) {
            addInfoLabel("城市", locationInfo.getCity(), false);
        }
        
        // ISP
        if (locationInfo.getIsp() != null && !locationInfo.getIsp().isEmpty() && !locationInfo.getIsp().equals("未知")) {
            addInfoLabel("ISP", locationInfo.getIsp(), false);
        }
        
        // 国家代码
        if (locationInfo.getCountryCode() != null && !locationInfo.getCountryCode().isEmpty() && !locationInfo.getCountryCode().equals("未知")) {
            addInfoLabel("国家代码", locationInfo.getCountryCode(), false);
        }
        
        // ASN
        if (locationInfo.getAsn() != null && !locationInfo.getAsn().isEmpty() && !locationInfo.getAsn().equals("未知")) {
            addInfoLabel("ASN", locationInfo.getAsn(), false);
        }
        
        // 网络类型
        if (locationInfo.getNetworkType() != null && !locationInfo.getNetworkType().isEmpty() && !locationInfo.getNetworkType().equals("未知")) {
            addInfoLabel("网络类型", locationInfo.getNetworkType(), false);
        }
        
        // 风险评分
        if (locationInfo.getRiskScore() != null) {
            addInfoLabel("风险评分", String.valueOf(locationInfo.getRiskScore()), false);
        }
    }
    
    /**
     * 添加信息标签到显示区域
     */
    private void addInfoLabel(String label, String value, boolean isFirst) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 0, 6, 0));
        
        Label keyLabel = new Label(label + ":");
        keyLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #212121; -fx-min-width: 80px;");
        
        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #0078D7; -fx-wrap-text: true;");
        valueLabel.setWrapText(true);
        
        row.getChildren().addAll(keyLabel, valueLabel);
        ipLocationInfoBox.getChildren().add(row);
    }
    
    /**
     * 显示IP归属地错误信息
     */
    private void displayIpLocationError(String errorMsg) {
        if (ipLocationInfoBox == null) return;
        
        ipLocationInfoBox.getChildren().clear();
        
        Label errorLabel = new Label("查询失败\n\n" + errorMsg);
        errorLabel.setWrapText(true);
        errorLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #DC3545; -fx-padding: 10px;");
        ipLocationInfoBox.getChildren().add(errorLabel);
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
        // 记录当前查看的会话ID
        currentViewingSessionId = sessionId;
        
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
                        
                        // ID从1开始重新编号
                        int displayId = 1;
                        for (DatabaseService.TrafficRecord record : records) {
                            TrafficRecordDisplay display = new TrafficRecordDisplay(
                                (long) displayId,  // 使用重新编号的ID
                                record.getIfaceName(),
                                record.getDownSpeed(),
                                record.getUpSpeed(),
                                record.getSourceIp(),
                                record.getDestIp(),
                                record.getProcessName(),
                                record.getProtocol(),
                                formatTimestamp(record.getCaptureTime())
                            );
                            historyData.add(display);
                            displayId++;
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
     * 会话行选择事件处理（更新会话详情数据卡片）
     * 此方法用于在右侧会话详情区域显示会话的基本信息
     * 
     * @param selectedSession 选中的会话对象
     */
    private void onSessionRowSelected(SessionDisplay selectedSession) {
        if (selectedSession == null) {
            clearSessionDetail();
            return;
        }
        
        // 隐藏占位符，显示数据卡片
        if (sessionDetailPlaceholder != null) {
            sessionDetailPlaceholder.setVisible(false);
            sessionDetailPlaceholder.setManaged(false);
        }
        if (sessionInfoGrid != null) {
            sessionInfoGrid.setVisible(true);
            sessionInfoGrid.setManaged(true);
        }
        if (sessionSpeedGrid != null) {
            sessionSpeedGrid.setVisible(true);
            sessionSpeedGrid.setManaged(true);
        }
        
        // 填充基本信息卡片
        if (sessionIdLabel != null) {
            sessionIdLabel.setText(String.valueOf(selectedSession.getSessionId()));
        }
        if (sessionIfaceLabel != null) {
            sessionIfaceLabel.setText(selectedSession.getIfaceName() != null ? selectedSession.getIfaceName() : "--");
        }
        if (sessionStartTimeLabel != null) {
            sessionStartTimeLabel.setText(selectedSession.getStartTime() != null ? selectedSession.getStartTime() : "--");
        }
        if (sessionEndTimeLabel != null) {
            String endTime = selectedSession.getEndTime() != null && !selectedSession.getEndTime().isEmpty() 
                ? selectedSession.getEndTime() : "进行中";
            sessionEndTimeLabel.setText(endTime);
        }
        if (sessionDurationLabel != null) {
            sessionDurationLabel.setText(String.valueOf(selectedSession.getDurationSeconds()));
        }
        if (sessionRecordCountLabel != null) {
            sessionRecordCountLabel.setText(String.valueOf(selectedSession.getRecordCount()));
        }
        
        // 填充速度指标卡片
        if (sessionAvgDownLabel != null) {
            sessionAvgDownLabel.setText(String.format("%.2f", selectedSession.getAvgDownSpeed()));
        }
        if (sessionAvgUpLabel != null) {
            sessionAvgUpLabel.setText(String.format("%.2f", selectedSession.getAvgUpSpeed()));
        }
        if (sessionMaxDownLabel != null) {
            sessionMaxDownLabel.setText(String.format("%.2f", selectedSession.getMaxDownSpeed()));
        }
        if (sessionMaxUpLabel != null) {
            sessionMaxUpLabel.setText(String.format("%.2f", selectedSession.getMaxUpSpeed()));
        }
    }
    
    /**
     * 清空会话详情显示区域
     */
    private void clearSessionDetail() {
        // 显示占位符，隐藏数据卡片
        if (sessionDetailPlaceholder != null) {
            sessionDetailPlaceholder.setVisible(true);
            sessionDetailPlaceholder.setManaged(true);
        }
        if (sessionInfoGrid != null) {
            sessionInfoGrid.setVisible(false);
            sessionInfoGrid.setManaged(false);
        }
        if (sessionSpeedGrid != null) {
            sessionSpeedGrid.setVisible(false);
            sessionSpeedGrid.setManaged(false);
        }
        
        // 清空所有标签
        if (sessionIdLabel != null) sessionIdLabel.setText("--");
        if (sessionIfaceLabel != null) sessionIfaceLabel.setText("--");
        if (sessionStartTimeLabel != null) sessionStartTimeLabel.setText("--");
        if (sessionEndTimeLabel != null) sessionEndTimeLabel.setText("--");
        if (sessionDurationLabel != null) sessionDurationLabel.setText("--");
        if (sessionRecordCountLabel != null) sessionRecordCountLabel.setText("--");
        if (sessionAvgDownLabel != null) sessionAvgDownLabel.setText("--");
        if (sessionAvgUpLabel != null) sessionAvgUpLabel.setText("--");
        if (sessionMaxDownLabel != null) sessionMaxDownLabel.setText("--");
        if (sessionMaxUpLabel != null) sessionMaxUpLabel.setText("--");
    }
    
    /**
     * 会话选中时自动加载详细记录（核心优化方法）
     * 
     * <p>功能说明：</p>
     * <ul>
     *   <li>当用户在会话列表 TableView 中选中某一行时，自动触发此方法</li>
     *   <li>异步加载该会话的所有明细记录，并更新到详细记录表格中</li>
     *   <li>实现防抖处理：如果用户快速连续选择多行，只显示最后一次选中的结果</li>
     *   <li>注意：不会自动切换标签页，用户需要手动切换到"详细记录"标签页查看</li>
     * </ul>
     * 
     * <p>执行流程：</p>
     * <ol>
     *   <li>取消之前的加载任务（防抖处理）</li>
     *   <li>获取选中会话的 sessionId</li>
     *   <li>创建异步任务调用 DatabaseService.getRecordsBySession()</li>
     *   <li>使用 Platform.runLater() 更新 UI（详细记录表格）</li>
     *   <li>数据加载完成后，用户可切换到"详细记录"标签页查看</li>
     * </ol>
     * 
     * @param selectedSession 选中的会话对象
     */
    private void onSessionSelectedAutoLoad(SessionDisplay selectedSession) {
        if (selectedSession == null) {
            return;
        }
        
        int sessionId = selectedSession.getSessionId();
        
        // ========== 防抖处理：标记之前的加载任务为已取消 ==========
        // 如果用户快速连续点击多行，确保只显示最后一次选中的结果
        Integer previousSessionId = currentLoadingSessionId.getAndSet(sessionId);
        if (previousSessionId != null && previousSessionId != sessionId) {
            // 标记之前的任务为已取消（通过比较sessionId，让之前的任务忽略结果）
            System.out.println("[HistoryController] 取消之前的加载任务（会话 #" + previousSessionId + "），加载新的会话: " + sessionId);
        }
        
        // ========== 显示加载状态 ==========
        Platform.runLater(() -> {
            // 在详细记录表格中显示加载提示
            historyData.clear();
            statusLabel.setText(String.format("正在加载会话 #%d 的详细记录...", sessionId));
            
            // 注意：不再自动切换到详细记录标签页，用户需要手动切换查看
        });
        
        // ========== 异步加载详细记录 ==========
        // 使用 CompletableFuture 异步调用数据库服务
        // 使用 sessionId 作为唯一标识符进行防抖检查，避免变量初始化问题
        final int finalSessionId = sessionId; // 用于lambda表达式的final变量
        databaseService.getRecordsBySession(sessionId)
            .thenAccept(records -> {
                // 在主线程中更新 UI
                Platform.runLater(() -> {
                    // 检查任务是否已被新的任务替换（防抖处理）
                    // 通过比较 sessionId 来判断，而不是比较 CompletableFuture 对象
                    if (currentLoadingSessionId.get() != finalSessionId) {
                        // 此任务已被新任务替换，忽略结果
                        System.out.println("[HistoryController] 忽略已取消的加载任务结果: sessionId=" + finalSessionId);
                        return;
                    }
                    
                    // 清空旧数据
                    historyData.clear();
                    
                    // 转换并添加数据，ID从1开始重新编号
                    int displayId = 1;
                    for (DatabaseService.TrafficRecord record : records) {
                        String protocol = record.getProtocol();
                        // 调试输出
                        if (protocol == null || protocol.isEmpty()) {
                            System.out.println("[HistoryController] 警告：记录协议为空，记录ID: " + record.getRecordId() + 
                                ", 会话ID: " + record.getSessionId());
                        }
                        
                        TrafficRecordDisplay display = new TrafficRecordDisplay(
                            (long) displayId,  // 使用重新编号的ID
                            record.getIfaceName(),
                            record.getDownSpeed(),
                            record.getUpSpeed(),
                            record.getSourceIp(),
                            record.getDestIp(),
                            record.getProcessName(),
                            protocol != null ? protocol : "其他",  // 如果协议为空，使用默认值
                            formatTimestamp(record.getRecordTime())
                        );
                        historyData.add(display);
                        displayId++;
                    }
                    
                    // 更新状态标签
                    statusLabel.setText(String.format("会话 #%d 共 %d 条记录", finalSessionId, records.size()));
                    
                    // 记录当前查看的会话ID
                    currentViewingSessionId = finalSessionId;
                    
                    System.out.println("[HistoryController] 成功加载会话 #" + finalSessionId + " 的详细记录，共 " + records.size() + " 条");
                });
            })
            .exceptionally(throwable -> {
                // 处理加载失败的情况
                Platform.runLater(() -> {
                    // 检查任务是否已被新的任务替换（防抖处理）
                    // 通过比较 sessionId 来判断，而不是比较 CompletableFuture 对象
                    if (currentLoadingSessionId.get() != finalSessionId) {
                        // 此任务已被新任务替换，忽略错误
                        return; // Platform.runLater 接受 Runnable，不能返回值
                    }
                    
                    String errorMsg = throwable.getMessage() != null ? throwable.getMessage() : "未知错误";
                    statusLabel.setText("加载详细记录失败: " + errorMsg);
                    historyData.clear();
                    
                    // 显示错误提示
                    showAlert(Alert.AlertType.ERROR, "加载失败", 
                        String.format("无法加载会话 #%d 的详细记录：\n%s", finalSessionId, errorMsg));
                    
                    System.err.println("[HistoryController] 加载会话 #" + finalSessionId + " 的详细记录失败: " + errorMsg);
                    throwable.printStackTrace();
                });
                return null; // exceptionally 需要返回 Void 类型
            });
        
        // 注意：currentLoadingSessionId 已在方法开始时设置，用于防抖检查
    }
    
    /**
     * 从数据库加载历史数据（所有会话的记录）
     * 
     * <p>执行流程：</p>
     * <ol>
     *   <li>在后台线程中异步查询数据库</li>
     *   <li>将查询结果转换为显示对象</li>
     *   <li>在主线程中更新表格数据</li>
     * </ol>
     */
    private void loadHistoryData() {
        // 清空当前查看的会话ID（因为加载所有记录）
        currentViewingSessionId = null;
        
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
                        
                        // 转换并添加数据，ID从1开始重新编号
                        int displayId = 1;
                        for (DatabaseService.TrafficRecord record : records) {
                            TrafficRecordDisplay display = new TrafficRecordDisplay(
                                (long) displayId,  // 使用重新编号的ID
                                record.getIfaceName(),
                                record.getDownSpeed(),
                                record.getUpSpeed(),
                                record.getSourceIp(),
                                record.getDestIp(),
                                record.getProcessName(),
                                record.getProtocol(),
                                formatTimestamp(record.getCaptureTime())
                            );
                            historyData.add(display);
                            displayId++;
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
     * 使用过滤器加载历史数据（所有会话的记录）
     * 
     * <p>此方法根据用户选择的过滤条件查询并显示流量记录。</p>
     */
    private void loadHistoryDataWithFilter() {
        // 清空当前查看的会话ID（因为加载所有记录）
        currentViewingSessionId = null;
        
        // 禁用刷新按钮，防止重复点击
        refreshButton.setDisable(true);
        statusLabel.setText("正在加载数据...");
        
        // 构建查询条件
        TrafficRecordQueryBuilder queryBuilder = new TrafficRecordQueryBuilder();
        
        // 收集选中的协议
        List<String> selectedProtocols = new ArrayList<>();
        if (protocolTcpCheckBox != null && protocolTcpCheckBox.isSelected()) {
            selectedProtocols.add("TCP");
        }
        if (protocolUdpCheckBox != null && protocolUdpCheckBox.isSelected()) {
            selectedProtocols.add("UDP");
        }
        if (protocolOtherCheckBox != null && protocolOtherCheckBox.isSelected()) {
            selectedProtocols.add("其他");
        }
        queryBuilder.setProtocols(selectedProtocols);
        
        // 设置应用过滤
        if (processNameComboBox != null && processNameComboBox.getValue() != null) {
            String selectedProcess = processNameComboBox.getValue();
            if (!"全部".equals(selectedProcess)) {
                queryBuilder.setProcessName(selectedProcess);
            }
        }
        
        // 设置流量阈值过滤
        if (minDownSpeedTextField != null && minDownSpeedTextField.getText() != null) {
            try {
                String text = minDownSpeedTextField.getText().trim();
                if (!text.isEmpty()) {
                    double minSpeed = Double.parseDouble(text);
                    if (minSpeed > 0) {
                        queryBuilder.setMinDownSpeed(minSpeed);
                    }
                }
            } catch (NumberFormatException e) {
                // 忽略无效输入
            }
        }
        
        // 创建后台任务查询数据
        Task<List<DatabaseService.TrafficRecord>> queryTask = new Task<>() {
            @Override
            protected List<DatabaseService.TrafficRecord> call() throws Exception {
                // 在后台线程中使用过滤器查询历史记录
                return databaseService.queryRecordsWithFilter(queryBuilder).get();
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
                        
                        // 转换并添加数据，ID从1开始重新编号
                        int displayId = 1;
                        for (DatabaseService.TrafficRecord record : records) {
                            TrafficRecordDisplay display = new TrafficRecordDisplay(
                                (long) displayId,  // 使用重新编号的ID
                                record.getIfaceName(),
                                record.getDownSpeed(),
                                record.getUpSpeed(),
                                record.getSourceIp(),
                                record.getDestIp(),
                                record.getProcessName(),
                                record.getProtocol(),
                                formatTimestamp(record.getRecordTime())
                            );
                            historyData.add(display);
                            displayId++;
                        }
                        
                        // 更新状态标签
                        statusLabel.setText(String.format("共加载 %d 条记录（已应用过滤）", records.size()));
                        
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
     * 使用过滤器加载指定会话的详细记录
     * 
     * @param sessionId 会话 ID
     */
    private void loadSessionDetailsWithFilter(int sessionId) {
        // 记录当前查看的会话ID
        currentViewingSessionId = sessionId;
        
        refreshButton.setDisable(true);
        statusLabel.setText("正在加载会话详细记录...");
        
        // 构建查询条件
        TrafficRecordQueryBuilder queryBuilder = new TrafficRecordQueryBuilder();
        queryBuilder.setSessionId(sessionId);
        
        // 收集选中的协议
        List<String> selectedProtocols = new ArrayList<>();
        if (protocolTcpCheckBox != null && protocolTcpCheckBox.isSelected()) {
            selectedProtocols.add("TCP");
        }
        if (protocolUdpCheckBox != null && protocolUdpCheckBox.isSelected()) {
            selectedProtocols.add("UDP");
        }
        if (protocolOtherCheckBox != null && protocolOtherCheckBox.isSelected()) {
            selectedProtocols.add("其他");
        }
        queryBuilder.setProtocols(selectedProtocols);
        
        // 设置应用过滤
        if (processNameComboBox != null && processNameComboBox.getValue() != null) {
            String selectedProcess = processNameComboBox.getValue();
            if (!"全部".equals(selectedProcess)) {
                queryBuilder.setProcessName(selectedProcess);
            }
        }
        
        // 设置流量阈值过滤
        if (minDownSpeedTextField != null && minDownSpeedTextField.getText() != null) {
            try {
                String text = minDownSpeedTextField.getText().trim();
                if (!text.isEmpty()) {
                    double minSpeed = Double.parseDouble(text);
                    if (minSpeed > 0) {
                        queryBuilder.setMinDownSpeed(minSpeed);
                    }
                }
            } catch (NumberFormatException e) {
                // 忽略无效输入
            }
        }
        
        Task<List<DatabaseService.TrafficRecord>> queryTask = new Task<>() {
            @Override
            protected List<DatabaseService.TrafficRecord> call() throws Exception {
                return databaseService.queryRecordsWithFilter(queryBuilder).get();
            }
            
            @Override
            protected void succeeded() {
                try {
                    List<DatabaseService.TrafficRecord> records = getValue();
                    
                    Platform.runLater(() -> {
                        historyData.clear();
                        
                        // ID从1开始重新编号
                        int displayId = 1;
                        for (DatabaseService.TrafficRecord record : records) {
                            TrafficRecordDisplay display = new TrafficRecordDisplay(
                                (long) displayId,  // 使用重新编号的ID
                                record.getIfaceName(),
                                record.getDownSpeed(),
                                record.getUpSpeed(),
                                record.getSourceIp(),
                                record.getDestIp(),
                                record.getProcessName(),
                                record.getProtocol(),
                                formatTimestamp(record.getRecordTime())
                            );
                            historyData.add(display);
                            displayId++;
                        }
                        
                        statusLabel.setText(String.format("会话 #%d 共 %d 条记录（已应用过滤）", sessionId, records.size()));
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
        private final String protocol;
        private final String captureTime;
        
        public TrafficRecordDisplay(Long id, String ifaceName, Double downSpeed, 
                                   Double upSpeed, String sourceIp, String destIp, 
                                   String processName, String protocol, String captureTime) {
            this.id = id;
            this.ifaceName = ifaceName;
            this.downSpeed = downSpeed;
            this.upSpeed = upSpeed;
            this.sourceIp = sourceIp;
            this.destIp = destIp;
            this.processName = processName;
            this.protocol = protocol;
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
        public String getProtocol() { 
            String result = protocol != null ? protocol : "";
            // 调试输出
            if (result.isEmpty()) {
                System.out.println("[TrafficRecordDisplay] 警告：协议字段为空，记录ID: " + id);
            }
            return result;
        }
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
    
    /**
     * 过滤文件名中的非法字符
     * 移除 Windows/Linux 文件系统中不允许的字符：< > : " / \ | ? *
     * 
     * @param fileName 原始文件名
     * @return 清理后的文件名
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "Unknown";
        }
        
        // 移除或替换非法字符
        String sanitized = fileName
            .replaceAll("[<>:\"/\\\\|?*]", "_")  // 替换所有非法字符为下划线
            .replaceAll("\\s+", "_")            // 将多个连续空格替换为单个下划线
            .replaceAll("_{2,}", "_")           // 将多个连续下划线替换为单个下划线
            .replaceAll("^_+|_+$", "");         // 移除首尾下划线
        
        // 如果清理后为空，使用默认值
        if (sanitized.isEmpty()) {
            sanitized = "Unknown";
        }
        
        // 限制文件名长度（Windows 路径限制）
        if (sanitized.length() > 100) {
            sanitized = sanitized.substring(0, 100);
        }
        
        return sanitized;
    }
    
    /**
     * 速度进度条表格单元格
     * 用于在单元格背景显示动态进度条，表示流量速度
     */
    private static class SpeedProgressBarTableCell extends javafx.scene.control.TableCell<TrafficRecordDisplay, Double> {
        private final javafx.scene.layout.StackPane stackPane;
        private final javafx.scene.layout.Region progressBar;
        private final javafx.scene.control.Label valueLabel;
        private final boolean isDownload;
        private static final double MAX_SPEED = 10000.0; // 最大速度阈值（KB/s）
        
        public SpeedProgressBarTableCell(boolean isDownload) {
            this.isDownload = isDownload;
            
            // 创建进度条背景
            progressBar = new Region();
            progressBar.setMaxHeight(Double.MAX_VALUE);
            progressBar.setMaxWidth(Double.MAX_VALUE);
            
            // 创建数值标签
            valueLabel = new Label();
            valueLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #212121; -fx-font-weight: 500;");
            valueLabel.setAlignment(Pos.CENTER_LEFT);
            
            // 创建堆叠面板
            stackPane = new StackPane();
            stackPane.setAlignment(Pos.CENTER_LEFT);
            stackPane.setPadding(new Insets(4, 8, 4, 8));
            stackPane.getChildren().addAll(progressBar, valueLabel);
        }
        
        @Override
        protected void updateItem(Double item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
            } else {
                // 计算进度（基于最大速度）
                double progress = Math.min(item / MAX_SPEED, 1.0);
                
                // 设置进度条宽度
                progressBar.prefWidthProperty().bind(stackPane.widthProperty().multiply(progress));
                
                // 设置文本
                valueLabel.setText(String.format("%.2f", item));
                
                // 根据速度大小调整进度条颜色（科技蓝渐变）
                if (item > MAX_SPEED * 0.7) {
                    // 高流量：深蓝色
                    progressBar.setStyle("-fx-background-color: linear-gradient(to right, #004578, #0078D7); " +
                                        "-fx-background-radius: 3px;");
                } else if (item > MAX_SPEED * 0.4) {
                    // 中流量：蓝色
                    progressBar.setStyle("-fx-background-color: linear-gradient(to right, #0078D7, #40A5F5); " +
                                        "-fx-background-radius: 3px;");
                } else {
                    // 低流量：浅蓝色
                    progressBar.setStyle("-fx-background-color: linear-gradient(to right, #40A5F5, #B3D9F2); " +
                                        "-fx-background-radius: 3px;");
                }
                
                setGraphic(stackPane);
                setText(null);
            }
        }
    }
    
    /**
     * 协议标签表格单元格
     * 根据协议类型显示不同颜色的标签
     */
    private static class ProtocolLabelTableCell extends javafx.scene.control.TableCell<TrafficRecordDisplay, String> {
        private final Label protocolLabel;
        
        public ProtocolLabelTableCell() {
            protocolLabel = new Label();
            protocolLabel.setAlignment(Pos.CENTER);
            protocolLabel.setPadding(new Insets(4, 12, 4, 12));
            protocolLabel.setStyle("-fx-background-radius: 12px; -fx-font-size: 11px; " +
                                  "-fx-font-weight: bold; -fx-text-fill: white;");
        }
        
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null || item.isEmpty()) {
                setGraphic(null);
                setText(null);
            } else {
                protocolLabel.setText(item);
                
                // 根据协议类型设置不同的颜色
                String protocol = item.toUpperCase();
                if ("TCP".equals(protocol)) {
                    // TCP：科技蓝
                    protocolLabel.setStyle("-fx-background-color: #0078D7; " +
                                          "-fx-background-radius: 12px; -fx-font-size: 11px; " +
                                          "-fx-font-weight: bold; -fx-text-fill: white;");
                } else if ("UDP".equals(protocol)) {
                    // UDP：活力橙
                    protocolLabel.setStyle("-fx-background-color: #F57C00; " +
                                          "-fx-background-radius: 12px; -fx-font-size: 11px; " +
                                          "-fx-font-weight: bold; -fx-text-fill: white;");
                } else if ("ICMP".equals(protocol)) {
                    // ICMP：绿色
                    protocolLabel.setStyle("-fx-background-color: #28A745; " +
                                          "-fx-background-radius: 12px; -fx-font-size: 11px; " +
                                          "-fx-font-weight: bold; -fx-text-fill: white;");
                } else {
                    // 其他：灰色
                    protocolLabel.setStyle("-fx-background-color: #6C757D; " +
                                          "-fx-background-radius: 12px; -fx-font-size: 11px; " +
                                          "-fx-font-weight: bold; -fx-text-fill: white;");
                }
                
                setGraphic(protocolLabel);
                setText(null);
            }
        }
    }
}

