# NetPulse FX - 代码审计与功能架构报告

> **项目版本**: v1.0-SNAPSHOT  
> **审计日期**: 2024年  
> **审计范围**: 完整代码库

---

## 目录

1. [代码结构树](#1-代码结构树)
2. [代码量统计](#2-代码量统计-loc-audit)
3. [核心功能映射](#3-核心功能映射-functional-mapping)
4. [技术栈盘点](#4-技术栈盘点)
5. [工程亮点统计](#5-工程亮点统计)

---

## 1. 代码结构树

```
NetPulseFX/
│
├── src/main/
│   ├── java/com/netpulse/netpulsefx/
│   │   │
│   │   ├── [应用程序入口层]
│   │   │   ├── HelloApplication.java          # JavaFX 应用入口，生命周期管理
│   │   │   ├── MainWrapper.java               # 主程序包装器，环境预检与错误处理
│   │   │   └── HelloController.java           # 示例控制器（保留）
│   │   │
│   │   ├── controller/                        # [UI 交互层]
│   │   │   ├── MainController.java            # 主监控界面控制器（约 3000+ 行）
│   │   │   ├── HistoryController.java         # 历史数据查看窗口控制器（约 3100+ 行）
│   │   │   ├── HelpController.java            # 帮助中心窗口控制器（约 570+ 行）
│   │   │   └── AIReportStage.java             # AI 报告独立窗口（约 320+ 行）
│   │   │
│   │   ├── service/                           # [业务逻辑层]
│   │   │   ├── DatabaseService.java           # 数据库服务（H2 持久化，约 2100+ 行）
│   │   │   ├── AIService.java                 # AI 流量分析服务（多模型适配，约 1430+ 行）
│   │   │   ├── IPLocationService.java         # IP 归属地查询服务（约 810+ 行）
│   │   │   ├── NetworkInterfaceService.java   # 网卡探测服务（约 275+ 行）
│   │   │   ├── ProcessContextService.java     # 进程上下文服务（netstat 映射）
│   │   │   ├── ExportService.java             # 数据导出服务（Excel/PDF）
│   │   │   ├── AIConfigManager.java           # AI 配置管理器（GUI 配置）
│   │   │   ├── AIConfigPersistenceService.java # AI 配置持久化服务
│   │   │   ├── SystemCheckService.java        # 系统环境检查服务
│   │   │   └── TrafficRecordQueryBuilder.java # 流量记录查询构建器
│   │   │
│   │   ├── task/                              # [异步任务层]
│   │   │   └── TrafficMonitorTask.java        # 流量监控任务（抓包内核，约 850+ 行）
│   │   │
│   │   ├── model/                             # [数据模型层]
│   │   │   ├── TrafficData.java               # 流量数据模型
│   │   │   ├── ProcessTrafficModel.java       # 进程流量模型（JavaFX Property）
│   │   │   ├── IPLocationInfo.java            # IP 地理位置信息模型
│   │   │   ├── AIConfig.java                  # AI 配置模型
│   │   │   └── AIModel.java                   # AI 模型定义（预定义模型列表）
│   │   │
│   │   ├── util/                              # [工具类层]
│   │   │   └── MarkdownToHtmlConverter.java   # Markdown 转 HTML 转换器
│   │   │
│   │   ├── exception/                         # [异常处理层]
│   │   │   └── NetworkInterfaceException.java # 网络接口异常
│   │   │
│   │   └── NetworkInterfaceTest.java          # 网卡探测测试类
│   │
│   └── resources/com/netpulse/netpulsefx/
│       │
│       ├── [UI 布局文件]
│       │   ├── main-view.fxml                 # 主监控界面布局
│       │   ├── history-view.fxml              # 历史数据窗口布局
│       │   ├── help-view.fxml                 # 帮助中心窗口布局
│       │   └── hello-view.fxml                # 示例视图（保留）
│       │
│       └── [样式文件]
│           ├── main-view.css                  # 主界面样式
│           └── history-view.css               # 历史窗口样式
│
├── [项目根目录 Markdown 文档]
│   ├── README.md                              # 项目使用指南
│   ├── AI_CONFIG.md                           # AI 服务配置说明
│   ├── AI_SERVICE_USAGE.md                    # AI 流量分析服务使用指南
│   ├── PROMPT_ENGINEERING.md                  # AI 提示词工程文档
│   ├── IP_LOCATION_FEATURE.md                 # IP 归属地查询功能说明
│   └── PROCESS_TRAFFIC_MONITOR.md             # 进程流量监控面板功能说明
│
└── pom.xml                                    # Maven 构建配置
```

### 核心包职责说明

| 包路径 | 职责描述 | 核心类数量 |
|--------|----------|-----------|
| **controller/** | UI 交互层，处理用户界面事件和业务逻辑编排 | 4 |
| **service/** | 业务逻辑层，提供核心业务功能服务 | 10 |
| **task/** | 异步任务层，后台执行耗时操作（抓包、数据处理） | 1 |
| **model/** | 数据模型层，定义数据结构和业务实体 | 5 |
| **util/** | 工具类层，提供通用工具方法 | 1 |
| **exception/** | 异常处理层，自定义异常类型 | 1 |

---

## 2. 代码量统计 (LOC Audit)

### 2.1 总体代码量

| 文件类型 | 文件数量 | 预估总行数 | 说明 |
|---------|---------|-----------|------|
| **Java 源码** | 26 | ~12,500+ | 核心业务逻辑代码 |
| **FXML 布局** | 4 | ~1,800+ | UI 界面定义 |
| **CSS 样式** | 2 | ~800+ | 界面样式定义 |
| **Markdown 文档** | 6 | ~3,500+ | 项目文档（集成到帮助中心） |
| **总计** | 38 | ~18,600+ | 项目总代码量 |

### 2.2 核心 Java 包代码量统计

| 包路径 | 文件数 | 预估代码行数 | 占比 | 主要职责 |
|--------|--------|-------------|------|----------|
| **controller/** | 4 | ~7,000+ | 56% | UI 控制器（MainController + HistoryController 占主要） |
| **service/** | 10 | ~5,800+ | 46% | 业务服务（DatabaseService + AIService 占主要） |
| **task/** | 1 | ~850+ | 7% | 抓包任务（TrafficMonitorTask） |
| **model/** | 5 | ~600+ | 5% | 数据模型 |
| **util/** | 1 | ~150+ | 1% | 工具类 |
| **exception/** | 1 | ~30+ | <1% | 异常类 |
| **其他** | 4 | ~1,000+ | 8% | 应用入口、测试类等 |

> **注**: 代码行数统计基于文件扫描和代码审查，实际数值可能因代码格式和注释密度有所差异。

### 2.3 核心类代码量（Top 10）

| 类名 | 包路径 | 预估行数 | 复杂度 | 功能摘要 |
|------|--------|---------|--------|----------|
| HistoryController | controller | ~3,100+ | 高 | 历史数据管理、AI 诊断、数据导出 |
| MainController | controller | ~3,000+ | 高 | 实时监控、流量图表、进程流量面板 |
| DatabaseService | service | ~2,100+ | 中 | H2 数据库操作、表结构管理、数据查询 |
| AIService | service | ~1,430+ | 中 | 多模型 AI 接口适配、流量分析 |
| IPLocationService | service | ~810+ | 低 | IP 归属地查询、缓存管理 |
| TrafficMonitorTask | task | ~850+ | 中 | 数据包捕获、流量统计、进程关联 |
| HelpController | controller | ~570+ | 低 | 帮助文档加载与渲染 |
| AIReportStage | controller | ~320+ | 低 | AI 报告独立窗口 |
| NetworkInterfaceService | service | ~275+ | 低 | 网卡探测与信息获取 |
| ProcessContextService | service | ~200+ | 低 | 进程上下文映射 |

---

## 3. 核心功能映射 (Functional Mapping)

### 3.1 应用程序入口层

| 类名 | 功能摘要 | 关键方法 |
|------|----------|----------|
| **HelloApplication** | JavaFX 应用入口，管理应用程序生命周期（init/start/stop） | `init()` - 初始化数据库<br>`start()` - 加载主界面<br>`stop()` - 清理资源 |
| **MainWrapper** | 主程序包装器，执行环境预检（wpcap.dll 检测）和启动错误处理 | `checkEnvironment()` - 环境检查<br>`handleApplicationStartupError()` - 错误处理 |

### 3.2 UI 交互层 (Controller)

| 类名 | 功能摘要 | 关键方法 |
|------|----------|----------|
| **MainController** | 主监控界面控制器，管理实时流量监控、图表展示、进程流量面板、BPF 过滤、AI 配置 | `startMonitoring()` - 启动监控<br>`updateTrafficChart()` - 更新图表<br>`updateProcessTrafficTable()` - 更新进程流量<br>`onAIConfigClick()` - AI 配置 |
| **HistoryController** | 历史数据查看窗口控制器，管理会话列表、详细记录、AI 诊断、数据导出、IP 归属地查询 | `loadSessionList()` - 加载会话<br>`onAIDiagnosisClick()` - AI 诊断<br>`onExportExcelClick()` - 导出 Excel<br>`queryIPLocation()` - IP 查询 |
| **HelpController** | 帮助中心窗口控制器，自动扫描 Markdown 文档并在 WebView 中渲染 | `initializeDocumentList()` - 扫描文档<br>`loadDocument()` - 加载文档内容 |
| **AIReportStage** | AI 分析报告独立窗口，提供报告查看、PDF 导出、全文复制功能 | `show()` - 显示窗口<br>`exportToPDF()` - 导出 PDF |

### 3.3 业务逻辑层 (Service)

| 类名 | 功能摘要 | 关键方法 |
|------|----------|----------|
| **DatabaseService** | H2 数据库服务，管理表结构、数据持久化、查询构建、自动清理 | `initialize()` - 初始化数据库<br>`saveTrafficRecord()` - 保存流量记录<br>`getAllSessions()` - 查询所有会话<br>`performAutoCleanup()` - 自动清理 |
| **AIService** | AI 流量分析服务，适配 DeepSeek/OpenAI/Ollama/Gemini 四种模型接口，处理会话诊断逻辑 | `analyzeTraffic()` - 分析流量数据<br>`analyzeSession()` - 分析监控会话<br>`buildPrompt()` - 构建提示词 |
| **IPLocationService** | IP 归属地查询服务，调用 Vore-API 查询地理位置，实现缓存和重试机制 | `queryLocation()` - 查询 IP 位置<br>`queryLocationAsync()` - 异步查询<br>`removeFromCache()` - 清除缓存 |
| **NetworkInterfaceService** | 网卡探测服务，获取系统网络接口信息（名称、IP、MAC） | `getAllInterfaces()` - 获取所有网卡<br>`getInterfaceInfo()` - 获取网卡详情<br>`getValidIPv4Address()` - 获取有效 IP |
| **ProcessContextService** | 进程上下文服务，通过 netstat 命令建立 IP:Port 到 PID 的映射关系 | `buildProcessContext()` - 构建进程上下文<br>`findProcessByIpPort()` - 查找进程 |
| **ExportService** | 数据导出服务，支持 Excel（Apache POI）和 PDF（iText 7）格式导出 | `exportToExcel()` - 导出 Excel<br>`exportToPDF()` - 导出 PDF |
| **AIConfigManager** | AI 配置管理器，提供 GUI 配置对话框，支持模型选择和 API Key 输入 | `showConfigDialog()` - 显示配置对话框<br>`testConnection()` - 测试连接 |
| **AIConfigPersistenceService** | AI 配置持久化服务，将配置保存到 Properties 文件 | `saveConfig()` - 保存配置<br>`loadConfig()` - 加载配置 |
| **SystemCheckService** | 系统环境检查服务，检测 Npcap 驱动和系统权限 | `checkSystemRequirements()` - 检查系统要求 |
| **TrafficRecordQueryBuilder** | 流量记录查询构建器，支持动态 SQL 查询条件构建 | `buildQuery()` - 构建查询 SQL |

### 3.4 异步任务层 (Task)

| 类名 | 功能摘要 | 关键方法 |
|------|----------|----------|
| **TrafficMonitorTask** | 流量监控任务，使用 Pcap4j 进行数据包捕获，统计上下行流量并关联进程 | `call()` - 执行抓包任务<br>`processPacket()` - 处理数据包<br>`getAndResetBytes()` - 获取并重置流量<br>`getAndClearProcessTraffic()` - 获取进程流量 |

### 3.5 数据模型层 (Model)

| 类名 | 功能摘要 | 关键属性 |
|------|----------|----------|
| **TrafficData** | 流量数据传输对象，封装流量监控数据 | `interfaceName`, `downSpeed`, `upSpeed`, `captureTime`, `packetSize`, `protocol` |
| **ProcessTrafficModel** | 进程流量模型，使用 JavaFX Property 支持数据绑定 | `processName`, `pid`, `downloadSpeed`, `uploadSpeed`, `totalSpeed` |
| **IPLocationInfo** | IP 地理位置信息模型，存储查询结果 | `ip`, `country`, `city`, `isp`, `region`, `countryCode` |
| **AIConfig** | AI 配置模型，存储 API 连接信息 | `provider`, `apiEndpoint`, `apiKey`, `model` |
| **AIModel** | AI 模型定义，预定义模型列表和端点映射 | 静态模型列表 |

### 3.6 工具类层 (Util)

| 类名 | 功能摘要 | 关键方法 |
|------|----------|----------|
| **MarkdownToHtmlConverter** | Markdown 转 HTML 转换器，使用 Flexmark 库渲染 Markdown | `convertToHtml()` - 转换为 HTML |

### 3.7 异常处理层 (Exception)

| 类名 | 功能摘要 | 用途 |
|------|----------|------|
| **NetworkInterfaceException** | 网络接口异常，封装网卡探测错误 | 网卡列表获取失败时抛出 |

---

## 4. 技术栈盘点

### 4.1 核心运行时环境

| 技术 | 版本 | 用途 | 备注 |
|------|------|------|------|
| **Java** | 21 | 核心开发语言 | 使用 Java 21 新特性（文本块、模式匹配等） |
| **JavaFX** | 21 | UI 框架 | 跨平台桌面应用开发 |
| **Maven** | 3.x | 构建工具 | 项目依赖管理和打包 |

### 4.2 网络抓包与协议处理

| 技术 | 版本 | 用途 | 备注 |
|------|------|------|------|
| **Pcap4j** | 1.8.2 | 数据包捕获库 | 底层依赖 Npcap/WinPcap (Windows) 或 libpcap (Linux/Mac) |
| **Pcap4j PacketFactory** | 1.8.2 | 数据包解析工厂 | 静态包工厂，提升性能 |

### 4.3 数据持久化

| 技术 | 版本 | 用途 | 备注 |
|------|------|------|------|
| **H2 Database** | 2.2.224 | 嵌入式数据库 | 文件数据库，无需独立服务 |

### 4.4 AI 与网络服务集成

| 技术 | 版本 | 用途 | 备注 |
|------|------|------|------|
| **Java HTTP Client** | 内置 (Java 21) | AI API 调用 | 异步 HTTP 请求 |
| **Vore-API** | 外部服务 | IP 归属地查询 | `https://api.vore.top/api/IPdata` |
| **DeepSeek API** | 外部服务 | AI 模型 | `https://api.deepseek.com/v1/chat/completions` |
| **OpenAI API** | 外部服务 | AI 模型 | `https://api.openai.com/v1/chat/completions` |
| **Google Gemini API** | 外部服务 | AI 模型 | `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent` |
| **Ollama** | 本地服务 | 本地 AI 模型 | `http://localhost:11434/api/generate` |

### 4.5 文档处理与渲染

| 技术 | 版本 | 用途 | 备注 |
|------|------|------|------|
| **Flexmark** | 0.64.8 | Markdown 转 HTML | 帮助文档渲染 |

### 4.6 数据导出

| 技术 | 版本 | 用途 | 备注 |
|------|------|------|------|
| **Apache POI** | 5.2.5 | Excel 导出 | `poi-ooxml` |
| **iText 7** | 8.0.2 | PDF 导出 | 模块化依赖（kernel, layout, io） |

### 4.7 UI 组件库

| 技术 | 版本 | 用途 | 备注 |
|------|------|------|------|
| **ControlsFX** | 11.2.1 | 增强 UI 组件 | JavaFX 扩展控件 |
| **FormsFX** | 11.6.0 | 表单构建 | 表单生成框架 |

### 4.8 日志框架

| 技术 | 版本 | 用途 | 备注 |
|------|------|------|------|
| **SLF4J API** | 2.0.12 | 日志门面 | 统一日志接口 |
| **SLF4J Simple** | 2.0.12 | 日志实现 | 简单日志实现 |

### 4.9 测试框架

| 技术 | 版本 | 用途 | 备注 |
|------|------|------|------|
| **JUnit Jupiter** | 5.10.2 | 单元测试 | API 和 Engine |

---

## 5. 工程亮点统计

### 5.1 API 接口集成

| 类别 | 数量 | 详情 |
|------|------|------|
| **AI 模型 API** | 4 | DeepSeek、OpenAI、Google Gemini、Ollama（本地） |
| **第三方服务 API** | 1 | Vore-API（IP 归属地查询） |
| **总计** | **5** | 支持多种 AI 提供商，提供灵活的 AI 分析能力 |

**API 端点详情**:

1. **DeepSeek**: `https://api.deepseek.com/v1/chat/completions`
2. **OpenAI**: `https://api.openai.com/v1/chat/completions`
3. **Google Gemini**: `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent`
4. **Ollama**: `http://localhost:11434/api/generate` (本地服务)
5. **Vore-API**: `https://api.vore.top/api/IPdata?ip={IP}`

### 5.2 数据库表结构

| 表名 | 用途 | 主要字段 | 关系 |
|------|------|----------|------|
| **monitoring_sessions** | 监控会话表 | `session_id`, `iface_name`, `start_time`, `end_time`, `duration_seconds`, `avg_down_speed`, `avg_up_speed`, `max_down_speed`, `max_up_speed`, `total_down_bytes`, `total_up_bytes`, `record_count` | 主表 |
| **traffic_records** | 流量记录表 | `record_id`, `session_id`, `down_speed`, `up_speed`, `source_ip`, `dest_ip`, `process_name`, `protocol`, `record_time` | 外键关联 sessions |
| **总计** | **2** | 主从表结构，支持会话级别的流量分析 | ON DELETE CASCADE |

**表结构特点**:
- 支持级联删除（CASCADE）
- 自动时间戳记录
- 支持 IPv4/IPv6 地址存储（VARCHAR(45)）
- 自动增量主键（AUTO_INCREMENT）

### 5.3 集成文档系统

| 文档类型 | 数量 | 文档列表 | 集成方式 |
|----------|------|----------|----------|
| **Markdown 文档** | 6 | README.md<br>AI_CONFIG.md<br>AI_SERVICE_USAGE.md<br>PROMPT_ENGINEERING.md<br>IP_LOCATION_FEATURE.md<br>PROCESS_TRAFFIC_MONITOR.md | Maven Resources 插件<br>打包到 JAR 类路径<br>帮助中心动态加载 |
| **总计** | **6** | 完整的项目文档体系 | WebView + Flexmark 渲染 |

**文档系统特点**:
- 文档自动打包到 JAR 文件
- 帮助中心自动扫描并显示
- 支持 Markdown 富文本渲染
- 中文文档，完整的功能说明

### 5.4 UI 界面数量

| 界面类型 | 数量 | 说明 |
|----------|------|------|
| **主窗口** | 1 | MainController - 实时监控主界面 |
| **历史数据窗口** | 1 | HistoryController - 会话列表与详细记录 |
| **帮助中心窗口** | 1 | HelpController - 文档查看 |
| **AI 报告窗口** | 1 | AIReportStage - 独立报告查看窗口 |
| **配置对话框** | 1 | AIConfigManager - AI 配置对话框 |
| **总计** | **5** | 完整的用户界面体系 |

### 5.5 核心功能模块统计

| 功能模块 | 实现方式 | 核心类 |
|----------|----------|--------|
| **实时流量监控** | Pcap4j 抓包 + 图表展示 | TrafficMonitorTask, MainController |
| **进程流量追踪** | netstat 映射 + 实时统计 | ProcessContextService, MainController |
| **历史数据管理** | H2 数据库 + 表格展示 | DatabaseService, HistoryController |
| **AI 智能分析** | 多模型 API 适配 | AIService, AIConfigManager |
| **IP 归属地查询** | Vore-API + 缓存机制 | IPLocationService |
| **数据导出** | POI + iText 7 | ExportService |
| **BPF 过滤** | Pcap4j BPF 引擎 | TrafficMonitorTask |
| **帮助文档系统** | Flexmark + WebView | HelpController, MarkdownToHtmlConverter |
| **总计** | **8** | 完整的功能模块体系 |

---

## 6. 架构特点总结

### 6.1 设计模式应用

- **单例模式**: DatabaseService, IPLocationService, SystemCheckService
- **工厂模式**: AIConfig（默认配置工厂方法）
- **观察者模式**: JavaFX Property 数据绑定
- **策略模式**: 多 AI 模型适配（DeepSeek/OpenAI/Gemini/Ollama）
- **建造者模式**: TrafficRecordQueryBuilder

### 6.2 线程安全设计

- **ConcurrentHashMap**: 缓存和进程流量映射（线程安全）
- **AtomicLong**: 流量字节数统计（原子操作）
- **Platform.runLater()**: UI 更新线程安全
- **Synchronized**: 关键代码段同步保护

### 6.3 性能优化亮点

- **异步任务**: 所有耗时操作（抓包、AI 分析、数据库查询）均异步执行
- **本地缓存**: IP 归属地查询结果缓存，避免重复 API 调用
- **BPF 内核过滤**: 过滤下沉至内核态，降低 CPU 开销
- **数据批量更新**: 进程流量每秒汇总一次，避免频繁 UI 更新
- **数据库连接池**: H2 连接复用，提升性能

### 6.4 用户体验优化

- **实时反馈**: 进度条、状态标签、Tooltip 提示
- **错误处理**: 友好的错误提示和重试机制
- **文档集成**: 内置帮助中心，无需外部文档
- **多格式导出**: 支持 Excel 和 PDF 导出
- **AI 配置可视化**: GUI 配置对话框，无需手动编辑配置文件

---

## 附录

### A. 项目依赖统计

- **总依赖数量**: 15+ 个 Maven 依赖
- **核心依赖**: JavaFX 21, Pcap4j 1.8.2, H2 2.2.224
- **文档处理**: Flexmark 0.64.8
- **数据导出**: Apache POI 5.2.5, iText 7 8.0.2

### B. 代码质量指标

- **代码覆盖率**: 核心功能已实现
- **异常处理**: 完善的异常捕获和用户提示
- **代码注释**: 关键方法包含 JavaDoc 注释
- **命名规范**: 遵循 Java 命名约定

### C. 部署与打包

- **打包方式**: Maven Shade Plugin（Fat JAR）
- **主类**: `com.netpulse.netpulsefx.MainWrapper`
- **资源打包**: Markdown 文档自动包含
- **运行时要求**: Java 21+, Npcap/WinPcap 驱动

---

**报告生成完成** | NetPulse FX 代码审计团队

