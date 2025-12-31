# 进程流量监控面板功能说明

## 功能概述

为 NetPulse FX 项目新增了进程流量监控面板，可以实时显示每个进程的网络流量（上传/下载速度）。

## 实现组件

### 1. ProcessTrafficModel 数据模型
**文件位置**: `src/main/java/com/netpulse/netpulsefx/model/ProcessTrafficModel.java`

**功能**:
- 使用 JavaFX Property（SimpleStringProperty、SimpleDoubleProperty 等）支持自动数据绑定
- 提供格式化的速度显示方法（自动选择 KB/s 或 MB/s）
- 支持流量数据的更新和累加

**主要属性**:
- `processName`: 进程名称
- `pid`: 进程 ID
- `downloadSpeed`: 下载速度（KB/s）
- `uploadSpeed`: 上传速度（KB/s）
- `totalSpeed`: 总速度（KB/s）

### 2. UI 布局（main-view.fxml）
**更新内容**:
- 添加了 `TitledPane` 组件，标题为"进程流量详情"
- 使用 `TableView<ProcessTrafficModel>` 展示进程流量数据
- 包含四列：应用、PID、下载速度、上传速度

### 3. MainController 更新
**新增功能**:
- 进程流量数据源：`ObservableList<ProcessTrafficModel>`
- 进程流量汇总映射：`Map<String, ProcessTrafficModel>`
- 临时统计映射：`Map<String, ProcessTrafficData>`（用于每秒汇总）
- 定时更新任务：每秒执行一次，汇总进程流量并更新表格

**核心方法**:
- `initializeProcessTrafficMonitoring()`: 初始化进程流量监控
- `updateProcessTrafficTable()`: 更新进程流量表格（每秒执行）
- `findPidByProcessName()`: 根据进程名查找 PID

### 4. 交互增强

#### 自动排序
- 默认按下载速度从高到低排序
- 用户可以点击列标题进行排序

#### 颜色标识
- **高流量进程**（> 1 MB/s）：浅红色背景（#ffcccc）
- **中等流量进程**（> 512 KB/s）：浅黄色背景（#ffffcc）
- **低流量进程**：默认背景

## 技术特点

### 1. 线程安全
- 使用 `ConcurrentHashMap` 确保多线程安全
- 使用 `synchronized` 保护关键代码段
- 使用 `Platform.runLater()` 确保 UI 更新在主线程执行

### 2. 性能优化
- 后台线程汇总数据，不阻塞抓包流程
- 每秒更新一次，避免频繁 UI 更新
- 使用 JavaFX Property 实现高效的数据绑定

### 3. 数据汇总逻辑
- 在 `updateTrafficChart()` 中累加每个进程的流量
- 每秒汇总一次，更新到表格
- 自动查找进程 PID

## 使用说明

1. **启动监控**：选择网卡并点击"开始监控"
2. **查看进程流量**：在"进程流量详情"面板中查看每个进程的实时流量
3. **排序**：点击列标题可以按该列排序
4. **颜色标识**：高流量进程会显示为红色背景，便于快速识别

## 数据流向

```
数据包捕获 (TrafficMonitorTask)
  ↓
提取 IP 和端口信息
  ↓
查找进程 (ProcessContextService)
  ↓
累加进程流量 (processTrafficTempMap)
  ↓
每秒汇总 (updateProcessTrafficTable)
  ↓
更新表格 (TableView)
```

## 注意事项

1. **进程识别**：依赖 ProcessContextService 的 netstat 映射表，如果进程不在映射表中，可能显示为"未知进程"
2. **流量统计**：当前实现将总流量平均分配给上下行，未来可以改进为分别统计
3. **性能影响**：进程流量监控对系统性能影响很小，每秒只更新一次表格

## 未来改进建议

1. **分别统计上下行**：在 TrafficMonitorTask 中区分上行和下行流量
2. **进程图标**：显示进程图标，提升用户体验
3. **流量历史**：记录每个进程的流量历史，支持查看趋势图
4. **进程过滤**：支持按进程名、PID 等条件过滤
5. **导出功能**：支持导出进程流量数据

