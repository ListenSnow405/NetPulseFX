# AI 流量分析服务使用指南

## 功能概述

NetPulse FX 集成了 AI 流量分析助手，可以智能分析网络流量数据，识别潜在的网络拥堵、安全风险，并提供优化建议。

## 核心组件

### 1. TrafficData 数据传输对象
位置：`com.netpulse.netpulsefx.model.TrafficData`

用于封装流量监控数据，包含：
- 网络接口名称
- 下行/上行速度（KB/s）
- 捕获时间
- 数据包大小
- 协议类型

### 2. AIService 服务类
位置：`com.netpulse.netpulsefx.service.AIService`

核心方法：`analyzeTraffic(List<TrafficData> history)`

**功能特性：**
- 使用 Java 21 原生 HttpClient 进行异步 API 调用
- 支持多种 AI API 提供商（DeepSeek、OpenAI、Ollama）
- 自动格式化流量数据为 JSON
- 完善的错误处理和超时控制
- 不阻塞 UI 线程

### 3. AIConfig 配置类
位置：`com.netpulse.netpulsefx.model.AIConfig`

用于配置 AI API 的连接信息。

## 使用方法

### 在 Controller 中调用 AI 服务

```java
// 1. 初始化 AI 服务
AIConfig config = AIConfig.defaultOllama();  // 或使用其他提供商
AIService aiService = new AIService(config);

// 2. 准备流量数据
List<TrafficData> trafficDataList = new ArrayList<>();
// ... 从数据库或监控任务中获取数据并转换为 TrafficData 对象

// 3. 异步调用 AI 分析
CompletableFuture<String> analysisFuture = aiService.analyzeTraffic(trafficDataList);

// 4. 处理分析结果
analysisFuture.thenAccept(result -> {
    Platform.runLater(() -> {
        // 在主线程中更新 UI
        textArea.setText(result);
    });
}).exceptionally(throwable -> {
    Platform.runLater(() -> {
        // 处理错误
        textArea.setText("分析失败: " + throwable.getMessage());
    });
    return null;
});
```

### 在 MainController 中的实际使用

参考 `MainController.onAIAnalyzeButtonClick()` 方法：

```java
@FXML
protected void onAIAnalyzeButtonClick() {
    // 1. 从数据库获取历史数据
    List<DatabaseService.TrafficRecord> records = 
        databaseService.queryAllTrafficHistoryAsync().get();
    
    // 2. 转换为 TrafficData 对象
    List<TrafficData> trafficDataList = convertToTrafficData(records);
    
    // 3. 调用 AI 服务分析
    aiService.analyzeTraffic(trafficDataList)
        .thenAccept(result -> {
            Platform.runLater(() -> {
                aiReportTextArea.setText(result);
            });
        })
        .exceptionally(throwable -> {
            Platform.runLater(() -> {
                aiReportTextArea.setText("错误: " + throwable.getMessage());
            });
            return null;
        });
}
```

## 系统提示词

AI 服务使用的系统提示词：

```
你是一个网络安全专家，请根据以下流量数据分析网络状态，识别潜在的拥堵或安全风险（如 DDoS 模拟、大量下载），并给出改进建议。

请从以下角度进行分析：
1. 流量趋势：分析流量变化趋势，是否出现异常波动
2. 网络性能：评估当前网络性能状态，是否存在拥堵
3. 安全风险：识别潜在的网络安全威胁（如异常流量、DDoS 攻击迹象、异常下载等）
4. 优化建议：基于分析结果，提供具体的网络优化建议

请使用中文回答，格式清晰，建议分点列出。
```

## 数据格式

流量数据会被格式化为以下 JSON 格式发送给 AI：

```json
{
  "trafficRecords": [
    {
      "interfaceName": "eth0",
      "downSpeed": 1024.50,
      "upSpeed": 512.30,
      "captureTime": "2024-01-01 12:00:00",
      "packetSize": 1536800,
      "protocol": "TCP"
    }
  ],
  "statistics": {
    "totalRecords": 100,
    "averageDownSpeed": 1024.50,
    "averageUpSpeed": 512.30,
    "maxDownSpeed": 2048.00,
    "maxUpSpeed": 1024.00,
    "timeRange": {
      "start": "2024-01-01 12:00:00",
      "end": "2024-01-01 13:00:00"
    }
  }
}
```

## 错误处理

AI 服务会自动处理以下常见错误：

1. **请求超时**：返回友好的中文提示
2. **API 密钥错误**：提示检查 API 密钥
3. **网络连接失败**：提示检查网络和 API 端点
4. **API 响应格式错误**：显示详细的错误信息

## UI 集成

在主界面中，AI 分析功能已集成：

1. **AI 诊断报告区域**：位于流量图表下方
2. **AI 分析按钮**：点击后触发分析
3. **状态标签**：显示分析进度
4. **报告文本区域**：显示 AI 分析结果

## 配置说明

详细配置方法请参考 `AI_CONFIG.md` 文件。

## 性能优化

- 所有 API 调用都在后台线程执行，不会阻塞 UI
- 设置了合理的超时时间（30秒）
- 使用 CompletableFuture 进行异步处理
- 支持取消操作和资源清理

## 扩展开发

如需扩展 AI 服务功能，可以：

1. 修改系统提示词以调整分析角度
2. 添加更多的数据统计信息
3. 支持批量分析多个时间段的数据
4. 集成更多的 AI 模型提供商





