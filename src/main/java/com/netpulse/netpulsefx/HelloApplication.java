package com.netpulse.netpulsefx;

import com.netpulse.netpulsefx.service.DatabaseService;
import com.netpulse.netpulsefx.service.SystemCheckService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;

/**
 * NetPulse FX 主应用程序入口
 * 
 * <p>应用程序生命周期管理：</p>
 * <ul>
 *   <li>init(): 初始化数据库连接和表结构</li>
 *   <li>start(): 加载并显示主界面</li>
 *   <li>stop(): 关闭数据库连接，释放资源</li>
 * </ul>
 */
public class HelloApplication extends Application {
    
    /**
     * 数据库服务实例
     */
    private DatabaseService databaseService;
    
    /**
     * 应用程序初始化方法
     * 在 JavaFX Application Thread 启动之前调用，适合执行耗时的初始化操作
     * 
     * <p>初始化内容：</p>
     * <ul>
     *   <li>初始化 H2 数据库连接</li>
     *   <li>创建 traffic_history 表（如果不存在）</li>
     * </ul>
     * 
     * <p>为什么在这里初始化数据库？</p>
     * <p>init() 方法在后台线程中执行，不会阻塞 JavaFX Application Thread 的启动。
     * 这样可以确保数据库初始化完成后再显示 UI。</p>
     */
    @Override
    public void init() {
        try {
            // 获取数据库服务单例并初始化
            databaseService = DatabaseService.getInstance();
            databaseService.initialize();
            System.out.println("[Application] 数据库初始化完成");
        } catch (ClassNotFoundException e) {
            System.err.println("[Application] H2 数据库驱动未找到: " + e.getMessage());
            System.err.println("请确保 pom.xml 中包含 H2 数据库依赖");
            // 注意：这里不能显示 Alert，因为 JavaFX Application Thread 还未启动
            // 错误信息会在控制台输出，启动时会在 UI 中显示
        } catch (SQLException e) {
            System.err.println("[Application] 数据库初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * JavaFX 应用程序启动方法
     * 在 JavaFX Application Thread 中执行
     * 
     * @param stage 主窗口舞台
     * @throws IOException 如果 FXML 文件加载失败
     */
    @Override
    public void start(Stage stage) throws IOException {
        // 系统权限和驱动检查
        SystemCheckService.CheckResult checkResult = SystemCheckService.checkSystemRequirements();
        if (!checkResult.isPassed()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("系统检查警告");
            alert.setHeaderText("系统环境检查失败");
            alert.setContentText(checkResult.getMessage());
            
            // 添加"查看帮助"按钮
            ButtonType helpButton = new ButtonType("查看帮助");
            ButtonType continueButton = new ButtonType("继续运行");
            alert.getButtonTypes().setAll(helpButton, continueButton);
            
            ButtonType result = alert.showAndWait().orElse(continueButton);
            if (result == helpButton) {
                // 打开帮助中心
                openHelpCenter(stage);
            }
        }
        
        // 如果数据库初始化失败，显示错误提示
        if (databaseService == null || databaseService.getConnection() == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("数据库初始化警告");
            alert.setHeaderText("数据库初始化失败");
            alert.setContentText("应用程序将继续运行，但流量历史数据将无法保存。\n" +
                    "请检查控制台输出的错误信息。");
            alert.showAndWait();
        } else {
            // 数据库自动清理（在后台执行）
            databaseService.performAutoCleanup();
        }
        
        // 加载主界面 FXML 文件
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        stage.setTitle("NetPulse FX - 网络流量监控系统");
        stage.setScene(scene);
        
        // 设置窗口默认最大化（全屏显示）
        stage.setMaximized(true);
        
        stage.show();
    }
    
    /**
     * 打开帮助中心窗口
     */
    private void openHelpCenter(Stage parentStage) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("help-view.fxml"));
            Stage helpStage = new Stage();
            helpStage.setTitle("帮助中心 - NetPulse FX");
            helpStage.setScene(new Scene(fxmlLoader.load(), 1000, 700));
            helpStage.setMinWidth(800);
            helpStage.setMinHeight(500);
            helpStage.show();
        } catch (IOException e) {
            System.err.println("无法打开帮助中心: " + e.getMessage());
        }
    }
    
    /**
     * 应用程序停止方法
     * 在应用程序退出前调用，用于清理资源
     * 
     * <p>清理内容：</p>
     * <ul>
     *   <li>关闭数据库连接</li>
     *   <li>关闭数据库操作线程池</li>
     * </ul>
     */
    @Override
    public void stop() {
        // 关闭数据库服务，释放资源
        if (databaseService != null) {
            databaseService.shutdown();
            System.out.println("[Application] 应用程序已退出，资源已释放");
        }
    }

    public static void main(String[] args) {
        launch();
    }
}