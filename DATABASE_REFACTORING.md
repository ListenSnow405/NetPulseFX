# 数据库重构说明文档

## 概述

NetPulse FX 的数据库模块已重构为"监控会话-详细记录"的两层嵌套结构，提供了更清晰的数据组织和更强大的查询能力。

## 架构设计

### 表结构

#### 1. monitoring_sessions（监控会话表）

记录每次监控的概况信息：

| 字段名 | 类型 | 说明 |
|--------|------|------|
| session_id | BIGINT | 主键，自动递增 |
| iface_name | VARCHAR(255) | 网络接口名称 |
| start_time | TIMESTAMP | 监控开始时间 |
| end_time | TIMESTAMP | 监控结束时间（可为null） |
| duration_seconds | BIGINT | 持续时间（秒） |
| avg_down_speed | DOUBLE | 平均下行速度（KB/s） |
| avg_up_speed | DOUBLE | 平均上行速度（KB/s） |
| max_down_speed | DOUBLE | 最大下行速度（KB/s） |
| max_up_speed | DOUBLE | 最大上行速度（KB/s） |
| total_down_bytes | BIGINT | 总下行字节数 |
| total_up_bytes | BIGINT | 总上行字节数 |
| record_count | INT | 记录数量 |

#### 2. traffic_records（流量明细记录表）

记录每秒的详细流量数据：

| 字段名 | 类型 | 说明 |
|--------|------|------|
| record_id | BIGINT | 主键，自动递增 |
| session_id | BIGINT | 外键，关联到 monitoring_sessions |
| down_speed | DOUBLE | 下行速度（KB/s） |
| up_speed | DOUBLE | 上行速度（KB/s） |
| source_ip | VARCHAR(45) | 源IP地址 |
| dest_ip | VARCHAR(45) | 目标IP地址 |
| process_name | VARCHAR(255) | 进程名称 |
| record_time | TIMESTAMP | 记录时间 |

## API 使用说明

### 1. 开始新会话

```java
DatabaseService dbService = DatabaseService.getInstance();

// 异步创建新会话
CompletableFuture<Integer> sessionFuture = dbService.startNewSession("eth0");

sessionFuture.thenAccept(sessionId -> {
    System.out.println("新会话已创建，session_id: " + sessionId);
    // 保存 sessionId，用于后续的 saveDetailRecord 和 endSession 调用
});
```

### 2. 保存明细记录

```java
// 在抓包循环中每秒调用
int sessionId = ...; // 从 startNewSession 获取

dbService.saveDetailRecord(
    sessionId,           // 会话 ID
    1024.5,              // 下行速度（KB/s）
    512.3,               // 上行速度（KB/s）
    "192.168.1.100",     // 源IP地址（可为null）
    "8.8.8.8",           // 目标IP地址（可为null）
    "chrome.exe"         // 进程名称（可为null）
).exceptionally(e -> {
    System.err.println("保存明细记录失败: " + e.getMessage());
    return null;
});
```

### 3. 结束会话

```java
int sessionId = ...; // 从 startNewSession 获取

// 监控停止时调用，自动计算统计信息
dbService.endSession(sessionId)
    .thenRun(() -> {
        System.out.println("会话已结束，统计信息已更新");
    })
    .exceptionally(e -> {
        System.err.println("结束会话失败: " + e.getMessage());
        return null;
    });
```

### 4. 查询所有会话

```java
// 获取所有历史会话列表
dbService.getAllSessions()
    .thenAccept(sessions -> {
        for (DatabaseService.MonitoringSession session : sessions) {
            System.out.println("会话 ID: " + session.getSessionId());
            System.out.println("网卡: " + session.getIfaceName());
            System.out.println("开始时间: " + session.getStartTime());
            System.out.println("持续时间: " + session.getDurationSeconds() + " 秒");
            System.out.println("平均下行速度: " + session.getAvgDownSpeed() + " KB/s");
            System.out.println("记录数: " + session.getRecordCount());
        }
    });
```

### 5. 查询会话的明细记录

```java
int sessionId = ...; // 从 getAllSessions 获取

// 获取指定会话的所有明细记录，用于重新绘制图表
dbService.getRecordsBySession(sessionId)
    .thenAccept(records -> {
        for (DatabaseService.TrafficRecord record : records) {
            System.out.println("时间: " + record.getRecordTime());
            System.out.println("下行: " + record.getDownSpeed() + " KB/s");
            System.out.println("上行: " + record.getUpSpeed() + " KB/s");
        }
    });
```

## 完整使用示例

```java
public class TrafficMonitor {
    private DatabaseService dbService;
    private int currentSessionId;
    
    public void startMonitoring(String ifaceName) {
        dbService = DatabaseService.getInstance();
        
        // 1. 创建新会话
        dbService.startNewSession(ifaceName)
            .thenAccept(sessionId -> {
                currentSessionId = sessionId;
                System.out.println("监控开始，session_id: " + sessionId);
                
                // 2. 开始抓包循环
                startCaptureLoop();
            });
    }
    
    private void startCaptureLoop() {
        // 每秒执行一次
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            // 获取当前流量数据
            double downSpeed = getCurrentDownSpeed();
            double upSpeed = getCurrentUpSpeed();
            String sourceIp = getCurrentSourceIp();
            String destIp = getCurrentDestIp();
            String processName = getCurrentProcessName();
            
            // 3. 保存明细记录
            dbService.saveDetailRecord(
                currentSessionId, downSpeed, upSpeed,
                sourceIp, destIp, processName
            );
        }, 1, 1, TimeUnit.SECONDS);
    }
    
    public void stopMonitoring() {
        // 4. 结束会话
        if (currentSessionId > 0) {
            dbService.endSession(currentSessionId)
                .thenRun(() -> {
                    System.out.println("监控已停止，会话已结束");
                    currentSessionId = 0;
                });
        }
    }
}
```

## 事务处理

所有写操作（`startNewSession`、`saveDetailRecord`、`endSession`）都使用事务：

- **自动提交关闭**：操作开始时关闭自动提交
- **异常回滚**：如果发生异常，自动回滚所有更改
- **提交确认**：操作成功后手动提交

这确保了数据一致性，即使在高并发情况下也能保证数据的完整性。

## 数据迁移

### 兼容性说明

新版本保留了与旧版本的兼容性：

- 旧表 `traffic_history` 仍然存在（如果之前已创建）
- 新表 `monitoring_sessions` 和 `traffic_records` 会自动创建
- 可以同时使用新旧两套 API（不推荐）

### 迁移建议

如果需要将旧数据迁移到新结构：

1. 查询所有旧记录
2. 按时间分组创建会话
3. 将记录插入到新表结构

（迁移脚本可以根据实际需求编写）

## 性能优化

1. **异步操作**：所有数据库操作都是异步的，不会阻塞 UI 线程
2. **事务批处理**：每个操作使用独立事务，避免长时间锁定
3. **索引优化**：外键自动创建索引，提高查询性能
4. **批量插入**：未来可以扩展为批量插入，进一步提高性能

## 注意事项

1. **session_id 管理**：确保在监控开始时保存 `session_id`，并在整个监控过程中使用同一个 ID
2. **异常处理**：所有异步操作都应该使用 `exceptionally()` 处理异常
3. **资源清理**：在应用退出时调用 `shutdown()` 方法关闭数据库连接
4. **并发安全**：`DatabaseService` 是线程安全的，可以在多个线程中同时使用

## 未来扩展

1. **批量插入**：支持批量插入明细记录，提高性能
2. **数据压缩**：对于历史数据，可以定期压缩归档
3. **数据导出**：支持导出会话数据为 CSV 或 JSON 格式
4. **统计分析**：提供更丰富的统计查询功能


