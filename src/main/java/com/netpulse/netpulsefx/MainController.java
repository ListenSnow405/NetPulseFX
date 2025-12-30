package com.netpulse.netpulsefx;

import com.netpulse.netpulsefx.exception.NetworkInterfaceException;
import com.netpulse.netpulsefx.service.NetworkInterfaceService;
import com.netpulse.netpulsefx.task.TrafficMonitorTask;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import org.pcap4j.core.PcapNetworkInterface;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
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
    
    /** 网卡列表视图：显示所有可用的网络接口 */
    @FXML
    private ListView<String> networkInterfaceListView;
    
    /** 刷新网卡按钮 */
    @FXML
    private Button refreshButton;
    
    /** 开始监控按钮 */
    @FXML
    private Button startMonitorButton;
    
    /** 停止监控按钮 */
    @FXML
    private Button stopMonitorButton;
    
    /** 实时流量波形图 */
    @FXML
    private LineChart<String, Number> trafficChart;
    
    // ========== 数据模型 ==========
    
    /** 
     * 网卡列表的数据源
     * 使用 ObservableList 实现数据与视图的自动绑定
     * 当列表数据发生变化时，ListView 会自动更新显示
     */
    private ObservableList<String> networkInterfaceList;
    
    /** 网卡服务对象：负责获取网卡信息 */
    private NetworkInterfaceService networkInterfaceService;
    
    /** 存储实际的网卡对象列表，用于后续的监控操作 */
    private List<PcapNetworkInterface> pcapNetworkInterfaces;
    
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
    
    /** 时间格式化器：用于生成时间标签 */
    private SimpleDateFormat timeFormatter;
    
    /** 最大数据点数量：保留最近60个数据点（1分钟的数据） */
    private static final int MAX_DATA_POINTS = 60;
    
    // ========== 初始化方法 ==========
    
    /**
     * FXML 加载后自动调用的初始化方法
     * 在 Controller 实例化后，FXML 加载器会自动调用此方法
     */
    @FXML
    public void initialize() {
        // 初始化数据源：创建空的 ObservableList
        networkInterfaceList = FXCollections.observableArrayList();
        
        // 将数据源绑定到 ListView
        networkInterfaceListView.setItems(networkInterfaceList);
        
        // 初始化服务对象
        networkInterfaceService = new NetworkInterfaceService();
        
        // 初始化流量监控相关组件
        initializeTrafficMonitoring();
        
        // 设置自定义的 CellFactory，用于格式化显示网卡信息
        // 这样可以在 ListView 中显示更友好的网卡信息格式
        networkInterfaceListView.setCellFactory(param -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    // 直接显示格式化后的网卡信息字符串
                    setText(item);
                    // 设置样式，使显示更清晰
                    setStyle("-fx-font-size: 12px; -fx-padding: 5px;");
                }
            }
        });
        
        // 初始化时自动刷新网卡列表
        refreshNICs();
    }
    
    /**
     * 初始化流量监控相关组件
     * 设置图表数据系列和时间格式化器
     */
    private void initializeTrafficMonitoring() {
        // 创建图表数据系列
        trafficSeries = new XYChart.Series<>();
        trafficSeries.setName("网络流量");
        
        // 将数据系列添加到图表
        trafficChart.getData().add(trafficSeries);
        
        // 初始化时间格式化器（格式：HH:mm:ss）
        timeFormatter = new SimpleDateFormat("HH:mm:ss");
        
        // 创建定时任务执行器（单线程）
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TrafficUpdateThread");
            t.setDaemon(true); // 设置为守护线程
            return t;
        });
    }
    
    // ========== 事件处理方法 ==========
    
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
        // 获取当前选中的网卡项
        String selectedItem = networkInterfaceListView.getSelectionModel().getSelectedItem();
        
        if (selectedItem == null) {
            // 如果没有选中任何网卡，显示提示信息
            showAlert(Alert.AlertType.WARNING, "未选择网卡", 
                    "请先选择一个网卡后再开始监控！");
            return;
        }
        
        // 如果已经在监控，先停止当前监控
        if (trafficMonitorTask != null && trafficMonitorTask.isMonitoringActive()) {
            stopMonitoring();
        }
        
        // 根据选中的索引找到对应的 PcapNetworkInterface 对象
        int selectedIndex = networkInterfaceListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0 && selectedIndex < pcapNetworkInterfaces.size()) {
            PcapNetworkInterface selectedInterface = pcapNetworkInterfaces.get(selectedIndex);
            
            try {
                // 清空图表数据
                trafficSeries.getData().clear();
                
                // 创建流量监控任务
                trafficMonitorTask = new TrafficMonitorTask(selectedInterface);
                
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
                // 每秒执行一次，读取流量数据并更新图表
                updateTaskFuture = scheduler.scheduleAtFixedRate(
                        this::updateTrafficChart,  // 要执行的任务
                        1,                         // 初始延迟（秒）
                        1,                         // 执行间隔（秒）
                        TimeUnit.SECONDS
                );
                
                // 更新UI状态
                startMonitorButton.setDisable(true);
                stopMonitorButton.setDisable(false);
                refreshButton.setDisable(true);
                networkInterfaceListView.setDisable(true);
                
                titleLabel.setText("正在监控：" + selectedInterface.getDescription());
                
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
        
        // 恢复UI状态
        Platform.runLater(this::resetMonitorButtons);
    }
    
    /**
     * 重置监控按钮状态
     */
    private void resetMonitorButtons() {
        startMonitorButton.setDisable(false);
        stopMonitorButton.setDisable(true);
        refreshButton.setDisable(false);
        networkInterfaceListView.setDisable(false);
        titleLabel.setText("请选择监控网卡");
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
     */
    private void updateTrafficChart() {
        if (trafficMonitorTask == null || !trafficMonitorTask.isMonitoringActive()) {
            return;
        }
        
        try {
            // 读取累计的字节数并清零（原子操作）
            long bytes = trafficMonitorTask.getAndResetBytes();
            
            // 转换为 KB/s（因为每秒执行一次，所以直接除以1024即可）
            double kbPerSecond = bytes / 1024.0;
            
            // 生成当前时间标签
            String timeLabel = timeFormatter.format(new Date());
            
            // 使用 Platform.runLater() 确保 UI 更新在主线程执行
            Platform.runLater(() -> {
                // 添加新的数据点
                trafficSeries.getData().add(new XYChart.Data<>(timeLabel, kbPerSecond));
                
                // 性能优化：限制数据点数量，只保留最近60个点
                // 这样可以防止图表数据点过多导致卡顿
                if (trafficSeries.getData().size() > MAX_DATA_POINTS) {
                    // 移除最旧的数据点（第一个）
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
        refreshButton.setDisable(true);
        startMonitorButton.setDisable(true);
        
        // 清空当前列表，显示加载状态
        networkInterfaceList.clear();
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
                        
                        // 遍历网卡列表，格式化并添加到数据源
                        for (PcapNetworkInterface nif : interfaces) {
                            // 使用服务层的方法获取格式化的网卡信息
                            NetworkInterfaceService.NetworkInterfaceInfo info = 
                                    networkInterfaceService.getInterfaceInfo(nif);
                            
                            // 格式化显示字符串：显示描述、IP地址和MAC地址
                            // 格式：描述 (IP: xxx, MAC: xxx)
                            String displayText = String.format(
                                    "%s (IP: %s, MAC: %s)",
                                    info.getDescription(),
                                    info.getIpAddress(),
                                    info.getMacAddress()
                            );
                            
                            // 添加到 ObservableList，ListView 会自动更新
                            networkInterfaceList.add(displayText);
                        }
                        
                        // 更新标题，显示找到的网卡数量
                        titleLabel.setText(String.format("请选择监控网卡（找到 %d 个）", 
                                networkInterfaceList.size()));
                        
                        // 恢复按钮状态
                        refreshButton.setDisable(false);
                        startMonitorButton.setDisable(false);
                    });
                    
                } catch (Exception e) {
                    // 处理任务执行过程中的异常
                    Platform.runLater(() -> {
                        showAlert(Alert.AlertType.ERROR, "刷新失败", 
                                "刷新网卡列表时发生错误：\n" + e.getMessage());
                        titleLabel.setText("请选择监控网卡");
                        refreshButton.setDisable(false);
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
                    refreshButton.setDisable(false);
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
     * 清理资源
     * 在 Controller 销毁时调用，确保所有线程和资源都被正确释放
     */
    public void cleanup() {
        // 停止监控
        stopMonitoring();
        
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

