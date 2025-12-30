package com.netpulse.netpulsefx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * NetPulse FX 主应用程序入口
 */
public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        // 加载主界面 FXML 文件
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 600, 500);
        stage.setTitle("NetPulse FX - 网络流量监控系统");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}