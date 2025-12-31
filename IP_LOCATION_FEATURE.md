# IP 归属地查询功能使用说明

## 功能概述

为 NetPulse FX 项目新增了远程 IP 归属地查询功能，支持通过在线 API 查询指定 IP 的地理位置信息（国家、城市、ISP 等）。

## 实现组件

### 1. IPLocationInfo 数据模型
**文件位置**: `src/main/java/com/netpulse/netpulsefx/model/IPLocationInfo.java`

**功能**:
- 存储 IP 地理位置信息（国家、城市、ISP、地区、国家代码等）
- 提供格式化的地理位置描述方法
- 处理查询失败的情况

**主要方法**:
- `getFormattedLocation()`: 获取完整的地理位置描述
- `getShortLocation()`: 获取简短的地理位置描述（仅国家和城市）

### 2. IPLocationService 服务类
**文件位置**: `src/main/java/com/netpulse/netpulsefx/service/IPLocationService.java`

**功能**:
- 通过 HttpClient 调用 ip-api.com API 查询 IP 地理位置
- 使用 ConcurrentHashMap 实现本地缓存，同一 IP 在程序运行期间只查询一次
- 处理网络异常、API 频率限制等错误情况
- 支持同步和异步查询

**主要方法**:
- `queryLocation(String ip)`: 同步查询 IP 地理位置
- `queryLocationAsync(String ip)`: 异步查询 IP 地理位置（返回 CompletableFuture）
- `clearCache()`: 清空缓存
- `getCacheSize()`: 获取缓存大小
- `isCached(String ip)`: 检查 IP 是否已缓存

**API 说明**:
- 使用 ip-api.com 的免费 API
- API 地址: `http://ip-api.com/json/{ip}`
- 免费版本限制: 每分钟 45 次请求
- 响应格式: JSON

**缓存机制**:
- 使用 `ConcurrentHashMap` 实现线程安全的本地缓存
- 缓存键: IP 地址（字符串）
- 缓存值: IPLocationInfo 对象
- 缓存生命周期: 程序运行期间（程序重启后缓存清空）

**异常处理**:
- 网络连接异常: 返回"未知位置"，不影响主流程
- API 频率限制: 返回"未知位置"，不影响主流程
- 请求超时: 返回"未知位置"，不影响主流程
- 无效 IP 地址: 返回"未知位置"，并缓存失败结果避免重复查询

### 3. UI 集成（HistoryController）
**文件位置**: 
- `src/main/java/com/netpulse/netpulsefx/HistoryController.java`
- `src/main/resources/com/netpulse/netpulsefx/history-view.fxml`

**功能**:
- 在历史数据查看窗口中添加了 IP 归属地查询侧边栏
- 支持手动输入 IP 地址进行查询
- 支持点击表格行时自动尝试提取并查询 IP（如果数据中包含 IP 信息）

**UI 组件**:
- IP 输入框: 用于输入要查询的 IP 地址
- 查询按钮: 触发 IP 地理位置查询
- 信息显示区域: 显示查询结果（国家、城市、ISP 等）
- 状态标签: 显示查询状态和简短位置信息

## 使用方法

### 方法 1: 在历史数据窗口中查询

1. 打开"查看历史数据"窗口
2. 在右侧侧边栏的 IP 输入框中输入要查询的 IP 地址（例如: `8.8.8.8`）
3. 点击"查询"按钮
4. 查询结果将显示在下方文本区域中

### 方法 2: 在代码中使用服务类

```java
// 获取服务实例（单例模式）
IPLocationService service = IPLocationService.getInstance();

// 同步查询
IPLocationInfo location = service.queryLocation("8.8.8.8");
if (location.isSuccess()) {
    System.out.println("国家: " + location.getCountry());
    System.out.println("城市: " + location.getCity());
    System.out.println("ISP: " + location.getIsp());
    System.out.println("完整位置: " + location.getFormattedLocation());
}

// 异步查询（推荐，不阻塞 UI 线程）
service.queryLocationAsync("8.8.8.8")
    .thenAccept(locationInfo -> {
        // 在主线程中更新 UI
        Platform.runLater(() -> {
            // 更新 UI 组件
        });
    });
```

## 技术特点

### 1. 线程安全
- 使用 `ConcurrentHashMap` 实现线程安全的缓存
- HTTP 客户端是线程安全的
- 单例模式确保服务实例的唯一性

### 2. 性能优化
- 本地缓存避免重复查询同一 IP
- 异步查询不阻塞 UI 线程
- 请求超时设置（5 秒）避免长时间等待

### 3. 健壮性
- 完善的异常处理机制
- 网络异常不影响主抓包流程
- API 频率限制时返回"未知位置"而不抛出异常

### 4. 可扩展性
- 单例模式便于全局使用
- 可以轻松替换为其他 IP 查询 API
- 支持添加更多地理位置信息字段

## 扩展建议

### 1. 集成到数据包捕获流程
如果需要在数据包捕获时自动查询 IP 归属地，可以：

1. 在 `TrafficMonitorTask` 中提取数据包的源 IP 和目标 IP
2. 调用 `IPLocationService` 查询地理位置
3. 将地理位置信息存储到数据库或显示在 UI 中

示例代码：
```java
// 在 TrafficMonitorTask 中
PacketListener listener = new PacketListener() {
    @Override
    public void gotPacket(Packet packet) {
        // 提取 IP 地址（需要解析数据包）
        String sourceIp = extractSourceIp(packet);
        String destIp = extractDestIp(packet);
        
        // 异步查询地理位置（不阻塞抓包线程）
        IPLocationService.getInstance()
            .queryLocationAsync(sourceIp)
            .thenAccept(location -> {
                // 处理地理位置信息
            });
    }
};
```

### 2. 在 TableView 中显示 IP 归属地
可以在表格中添加 IP 归属地列：

1. 扩展数据模型，添加 IP 地址和地理位置字段
2. 在 TableView 中添加"地理位置"列
3. 使用 Tooltip 显示详细信息

### 3. 使用其他 IP 查询 API
如果需要使用其他 API（如 ipapi.co、ipinfo.io 等），只需修改 `IPLocationService` 中的：
- `API_BASE_URL` 常量
- `parseJsonResponse()` 方法中的 JSON 解析逻辑

### 4. 添加 JSON 解析库
当前使用正则表达式解析 JSON，对于复杂场景建议使用专门的 JSON 库：

在 `pom.xml` 中添加依赖：
```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.15.2</version>
</dependency>
```

然后使用 Jackson 解析 JSON：
```java
ObjectMapper mapper = new ObjectMapper();
JsonNode jsonNode = mapper.readTree(jsonResponse);
String country = jsonNode.get("country").asText();
```

## 注意事项

1. **API 频率限制**: ip-api.com 免费版本限制每分钟 45 次请求，超过限制会返回错误。建议：
   - 使用缓存机制减少 API 调用
   - 对于大量 IP 查询，考虑使用付费 API 或自建服务

2. **网络依赖**: 功能需要网络连接才能工作。如果网络不可用，所有查询将返回"未知位置"。

3. **隐私考虑**: IP 归属地查询会向第三方 API 发送 IP 地址，请注意隐私保护。

4. **缓存管理**: 当前缓存在程序运行期间一直保留。如果需要定期清理缓存，可以添加定时任务或 LRU 缓存机制。

## 测试建议

1. **基本功能测试**:
   - 查询有效的公网 IP（如 `8.8.8.8`）
   - 查询无效的 IP 地址
   - 测试网络断开时的行为

2. **缓存测试**:
   - 查询同一 IP 两次，验证第二次从缓存读取
   - 验证缓存大小和内容

3. **异常处理测试**:
   - 模拟网络超时
   - 模拟 API 频率限制
   - 测试无效的 JSON 响应

4. **UI 测试**:
   - 测试输入框输入和查询按钮
   - 测试异步查询时的 UI 响应
   - 测试错误信息的显示

## 总结

IP 归属地查询功能已成功集成到 NetPulse FX 项目中，提供了：
- ✅ 完整的服务类实现（IPLocationService）
- ✅ 数据模型（IPLocationInfo）
- ✅ UI 集成（历史数据窗口侧边栏）
- ✅ 缓存机制（避免重复查询）
- ✅ 异常处理（不影响主流程）
- ✅ 异步查询支持（不阻塞 UI）

功能已准备就绪，可以在项目中使用！


