package com.netpulse.netpulsefx.service;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 数据库服务类 - 重构版本
 * 
 * <h2>架构设计：两层嵌套结构</h2>
 * 
 * <p><strong>表结构：</strong></p>
 * <ul>
 *   <li><strong>monitoring_sessions</strong>: 监控会话表
 *       <ul>
 *         <li>记录每次监控的概况信息（开始/结束时间、网卡名、平均速率等）</li>
 *         <li>主键：session_id</li>
 *       </ul>
 *   </li>
 *   <li><strong>traffic_records</strong>: 流量明细记录表
 *       <ul>
 *         <li>记录每秒的详细流量数据</li>
 *         <li>外键：session_id（关联到 monitoring_sessions）</li>
 *       </ul>
 *   </li>
 * </ul>
 * 
 * <p><strong>业务逻辑：</strong></p>
 * <ol>
 *   <li><strong>startNewSession()</strong>: 监控开始时创建新会话，返回 session_id</li>
 *   <li><strong>saveDetailRecord()</strong>: 抓包循环中每秒调用，保存明细记录</li>
 *   <li><strong>endSession()</strong>: 监控停止时更新会话统计信息</li>
 *   <li><strong>getAllSessions()</strong>: 查询所有历史会话</li>
 *   <li><strong>getRecordsBySession()</strong>: 查询某个会话的所有明细记录</li>
 * </ol>
 * 
 * <p><strong>事务处理：</strong></p>
 * <p>所有写操作都使用事务，确保数据一致性。如果操作失败，自动回滚。</p>
 * 
 * <h2>迁移说明：从 SQLite 迁移到 H2 Database</h2>
 * 
 * <p><strong>迁移原因：</strong></p>
 * <ul>
 *   <li><strong>SLF4J 依赖冲突解决：</strong>
 *       项目使用 org.slf4j.simple 作为日志实现，但 SQLite 的 JDBC 驱动（如 org.xerial:sqlite-jdbc）
 *       内部依赖了 slf4j-api，在某些场景下可能产生依赖冲突。H2 Database 作为纯 Java 实现的嵌入式数据库，
 *       对日志框架的依赖更加灵活，可以完美兼容项目现有的 SLF4J 配置。</li>
 *   <li><strong>纯 Java 实现：</strong>H2 是纯 Java 实现的数据库，无需额外的本地库，部署更加简单。</li>
 *   <li><strong>性能优化：</strong>H2 在嵌入式模式下性能优异，特别适合本项目的单机流量监控场景。</li>
 *   <li><strong>功能完整性：</strong>H2 支持标准的 SQL 语法和 JDBC API，迁移成本低，代码改动小。</li>
 * </ul>
 * 
 * <p><strong>连接模式：</strong></p>
 * <p>使用 H2 的嵌入式文件模式（Embedded Mode），数据库文件存储在项目根目录下的 netpulse_db.mv.db。
 * 这种模式下，数据库与应用进程绑定，适合单用户、单进程的应用场景。</p>
 * 
 * <p><strong>性能考虑：</strong></p>
 * <ul>
 *   <li>所有数据库操作都在独立的线程池中异步执行，确保不会阻塞 JavaFX UI 线程</li>
 *   <li>使用 PreparedStatement 提高 SQL 执行效率并防止 SQL 注入</li>
 *   <li>使用事务确保数据一致性</li>
 *   <li>批量插入支持（未来可扩展）</li>
 * </ul>
 * 
 * @author NetPulseFX Team
 * @version 3.0 - Session-Based Architecture
 */
public class DatabaseService {
    
    /**
     * H2 数据库连接字符串
     * 
     * 格式说明：jdbc:h2:./netpulse_db
     * - jdbc:h2: 表示使用 H2 数据库的 JDBC 驱动
     * - ./netpulse_db: 表示数据库文件路径（相对于程序运行目录）
     *   H2 会自动创建 netpulse_db.mv.db 文件（MVStore 存储引擎）
     * 
     * 连接模式：嵌入式文件模式（Embedded File Mode）
     * - 优点：数据持久化到文件，程序重启后数据仍然保留
     * - 适用：单进程应用，数据存储在本地文件系统
     */
    private static final String DB_URL = "jdbc:h2:./netpulse_db";
    
    /**
     * H2 数据库驱动类名
     * H2 2.x 版本的驱动类名为 org.h2.Driver
     */
    private static final String DB_DRIVER = "org.h2.Driver";
    
    /**
     * 数据库用户名（H2 嵌入式模式可以不设置，这里使用空字符串）
     */
    private static final String DB_USER = "";
    
    /**
     * 数据库密码（H2 嵌入式模式可以不设置，这里使用空字符串）
     */
    private static final String DB_PASSWORD = "";
    
    /**
     * 监控会话表名
     */
    private static final String TABLE_MONITORING_SESSIONS = "monitoring_sessions";
    
    /**
     * 流量明细记录表名
     */
    private static final String TABLE_TRAFFIC_RECORDS = "traffic_records";
    
    /**
     * 旧表名（用于兼容性检查）
     */
    private static final String TABLE_TRAFFIC_HISTORY = "traffic_history";
    
    /**
     * 数据库连接对象
     * 使用单例模式，保持连接复用，提高性能
     */
    private Connection connection;
    
    /**
     * 线程池：用于执行异步数据库操作
     * 
     * 为什么需要线程池？
     * - JavaFX UI 线程必须保持响应，不能执行耗时操作
     * - 数据库操作（特别是磁盘 I/O）可能阻塞线程
     * - 使用独立的线程池可以确保 UI 不会被阻塞
     * 
     * 线程池配置：
     * - 核心线程数：2（足够处理并发写入请求）
     * - 最大线程数：5（防止线程过多导致系统资源耗尽）
     * - 守护线程：true（主程序退出时自动终止）
     */
    private final ExecutorService executorService;
    
    /**
     * 单例实例
     */
    private static DatabaseService instance;
    
    /**
     * 私有构造函数，实现单例模式
     */
    private DatabaseService() {
        this.executorService = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "DatabaseService-Thread");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * 获取 DatabaseService 单例实例
     * 
     * @return DatabaseService 实例
     */
    public static synchronized DatabaseService getInstance() {
        if (instance == null) {
            instance = new DatabaseService();
        }
        return instance;
    }
    
    /**
     * 初始化数据库连接并创建表结构
     * 
     * <p>此方法在程序启动时调用，确保数据库和表结构已经准备好。
     * 如果表已存在，CREATE TABLE IF NOT EXISTS 语句不会报错。</p>
     * 
     * <p>执行流程：</p>
     * <ol>
     *   <li>加载 H2 JDBC 驱动</li>
     *   <li>建立数据库连接</li>
     *   <li>创建 monitoring_sessions 表（如果不存在）</li>
     *   <li>创建 traffic_records 表（如果不存在）</li>
     * </ol>
     * 
     * @throws SQLException 如果数据库连接或表创建失败
     * @throws ClassNotFoundException 如果 H2 驱动类未找到
     */
    public void initialize() throws SQLException, ClassNotFoundException {
        // 加载 H2 JDBC 驱动
        // 注意：在 Java 6+ 中，DriverManager 会自动加载驱动，但显式加载更可靠
        Class.forName(DB_DRIVER);
        
        // 建立数据库连接
        // 如果数据库文件不存在，H2 会自动创建
        connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
        
        // 创建新表结构
        createMonitoringSessionsTable();
        createTrafficRecordsTable();
        
        System.out.println("[DatabaseService] 数据库初始化成功: " + DB_URL);
    }
    
    /**
     * 执行数据库自动清理
     * 如果 traffic_records 表记录数超过 5000 条，自动清理最早的 10% 记录
     * 
     * <p>执行流程：</p>
     * <ol>
     *   <li>检查 traffic_records 表的记录总数</li>
     *   <li>如果超过 5000 条，计算需要删除的记录数（10%）</li>
     *   <li>删除最早的记录（按 record_time 排序）</li>
     *   <li>压缩数据库文件</li>
     * </ol>
     * 
     * <p>注意：此方法在后台线程执行，不会阻塞 UI</p>
     */
    public void performAutoCleanup() {
        executorService.submit(() -> {
            try {
                // 检查记录总数
                String countSQL = "SELECT COUNT(*) FROM " + TABLE_TRAFFIC_RECORDS;
                int totalRecords = 0;
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery(countSQL)) {
                    if (rs.next()) {
                        totalRecords = rs.getInt(1);
                    }
                }
                
                System.out.println("[DatabaseService] 当前记录数: " + totalRecords);
                
                // 如果记录数超过 5000，执行清理
                if (totalRecords > 5000) {
                    int recordsToDelete = (int) Math.ceil(totalRecords * 0.1); // 删除 10%
                    System.out.println("[DatabaseService] 开始自动清理，将删除 " + recordsToDelete + " 条记录");
                    
                    // 使用事务确保数据一致性
                    connection.setAutoCommit(false);
                    try {
                        // 删除最早的记录
                        String deleteSQL = """
                            DELETE FROM %s 
                            WHERE record_id IN (
                                SELECT record_id FROM (
                                    SELECT record_id FROM %s 
                                    ORDER BY record_time ASC 
                                    LIMIT %d
                                )
                            )
                            """.formatted(TABLE_TRAFFIC_RECORDS, TABLE_TRAFFIC_RECORDS, recordsToDelete);
                        
                        try (Statement stmt = connection.createStatement()) {
                            int deletedCount = stmt.executeUpdate(deleteSQL);
                            connection.commit();
                            System.out.println("[DatabaseService] 自动清理完成，已删除 " + deletedCount + " 条记录");
                            
                            // 压缩数据库
                            try (Statement compressStmt = connection.createStatement()) {
                                compressStmt.execute("CHECKPOINT DEFRAG");
                                System.out.println("[DatabaseService] 数据库压缩完成");
                            }
                        }
                    } catch (SQLException e) {
                        connection.rollback();
                        System.err.println("[DatabaseService] 自动清理失败: " + e.getMessage());
                        e.printStackTrace();
                    } finally {
                        connection.setAutoCommit(true);
                    }
                } else {
                    System.out.println("[DatabaseService] 记录数未超过阈值，无需清理");
                }
            } catch (SQLException e) {
                System.err.println("[DatabaseService] 自动清理检查失败: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * 获取数据库记录总数（用于状态显示）
     * 
     * @return 记录总数，如果查询失败返回 -1
     */
    public int getRecordCount() {
        try {
            String countSQL = "SELECT COUNT(*) FROM " + TABLE_TRAFFIC_RECORDS;
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(countSQL)) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseService] 获取记录数失败: " + e.getMessage());
        }
        return -1;
    }
    
    /**
     * 创建监控会话表
     * 
     * <p>表结构说明：</p>
     * <ul>
     *   <li><strong>session_id</strong>: BIGINT AUTO_INCREMENT PRIMARY KEY - 主键，自动递增</li>
     *   <li><strong>iface_name</strong>: VARCHAR(255) NOT NULL - 网络接口名称</li>
     *   <li><strong>start_time</strong>: TIMESTAMP NOT NULL - 监控开始时间</li>
     *   <li><strong>end_time</strong>: TIMESTAMP - 监控结束时间（可为null，表示正在监控）</li>
     *   <li><strong>duration_seconds</strong>: BIGINT - 持续时间（秒）</li>
     *   <li><strong>avg_down_speed</strong>: DOUBLE - 平均下行速度（KB/s）</li>
     *   <li><strong>avg_up_speed</strong>: DOUBLE - 平均上行速度（KB/s）</li>
     *   <li><strong>max_down_speed</strong>: DOUBLE - 最大下行速度（KB/s）</li>
     *   <li><strong>max_up_speed</strong>: DOUBLE - 最大上行速度（KB/s）</li>
     *   <li><strong>total_down_bytes</strong>: BIGINT - 总下行字节数</li>
     *   <li><strong>total_up_bytes</strong>: BIGINT - 总上行字节数</li>
     *   <li><strong>record_count</strong>: INT - 记录数量</li>
     * </ul>
     * 
     * @throws SQLException 如果表创建失败
     */
    private void createMonitoringSessionsTable() throws SQLException {
        String checkTableSQL = """
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES 
            WHERE TABLE_NAME = '%s'
            """.formatted(TABLE_MONITORING_SESSIONS.toUpperCase());
        
        boolean tableExists = false;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(checkTableSQL)) {
            if (rs.next() && rs.getInt(1) > 0) {
                tableExists = true;
            }
        }
        
        if (!tableExists) {
            String createTableSQL = """
                CREATE TABLE %s (
                    session_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    iface_name VARCHAR(255) NOT NULL,
                    start_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    end_time TIMESTAMP,
                    duration_seconds BIGINT,
                    avg_down_speed DOUBLE,
                    avg_up_speed DOUBLE,
                    max_down_speed DOUBLE,
                    max_up_speed DOUBLE,
                    total_down_bytes BIGINT,
                    total_up_bytes BIGINT,
                    record_count INT DEFAULT 0
                )
                """.formatted(TABLE_MONITORING_SESSIONS);
            
            try (Statement statement = connection.createStatement()) {
                statement.execute(createTableSQL);
                System.out.println("[DatabaseService] 表创建成功: " + TABLE_MONITORING_SESSIONS);
            }
        }
    }
    
    /**
     * 创建流量明细记录表
     * 
     * <p>表结构说明：</p>
     * <ul>
     *   <li><strong>record_id</strong>: BIGINT AUTO_INCREMENT PRIMARY KEY - 主键，自动递增</li>
     *   <li><strong>session_id</strong>: BIGINT NOT NULL - 外键，关联到 monitoring_sessions</li>
     *   <li><strong>down_speed</strong>: DOUBLE NOT NULL - 下行速度（KB/s）</li>
     *   <li><strong>up_speed</strong>: DOUBLE NOT NULL - 上行速度（KB/s）</li>
     *   <li><strong>source_ip</strong>: VARCHAR(45) - 源IP地址</li>
     *   <li><strong>dest_ip</strong>: VARCHAR(45) - 目标IP地址</li>
     *   <li><strong>process_name</strong>: VARCHAR(255) - 进程名称</li>
     *   <li><strong>protocol</strong>: VARCHAR(10) - 协议类型（TCP, UDP, ICMP, 其他）</li>
     *   <li><strong>record_time</strong>: TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP - 记录时间</li>
     * </ul>
     * 
     * @throws SQLException 如果表创建失败
     */
    private void createTrafficRecordsTable() throws SQLException {
        String checkTableSQL = """
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES 
            WHERE TABLE_NAME = '%s'
            """.formatted(TABLE_TRAFFIC_RECORDS.toUpperCase());
        
        boolean tableExists = false;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(checkTableSQL)) {
            if (rs.next() && rs.getInt(1) > 0) {
                tableExists = true;
            }
        }
        
        if (!tableExists) {
            String createTableSQL = """
                CREATE TABLE %s (
                    record_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    session_id BIGINT NOT NULL,
                    down_speed DOUBLE NOT NULL,
                    up_speed DOUBLE NOT NULL,
                    source_ip VARCHAR(45),
                    dest_ip VARCHAR(45),
                    process_name VARCHAR(255),
                    record_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (session_id) REFERENCES %s(session_id) ON DELETE CASCADE
                )
                """.formatted(TABLE_TRAFFIC_RECORDS, TABLE_MONITORING_SESSIONS);
            
            try (Statement statement = connection.createStatement()) {
                statement.execute(createTableSQL);
                System.out.println("[DatabaseService] 表创建成功: " + TABLE_TRAFFIC_RECORDS);
            }
        } else {
            // 表已存在，检查并添加新字段（如果不存在）
            addColumnIfNotExists(TABLE_TRAFFIC_RECORDS, "source_ip", "VARCHAR(45)");
            addColumnIfNotExists(TABLE_TRAFFIC_RECORDS, "dest_ip", "VARCHAR(45)");
            addColumnIfNotExists(TABLE_TRAFFIC_RECORDS, "process_name", "VARCHAR(255)");
            addColumnIfNotExists(TABLE_TRAFFIC_RECORDS, "protocol", "VARCHAR(10)");
        }
    }
    
    /**
     * 如果列不存在则添加列（用于数据库迁移）
     * 
     * @param tableName 表名
     * @param columnName 列名
     * @param columnType 列类型
     * @throws SQLException 如果操作失败
     */
    private void addColumnIfNotExists(String tableName, String columnName, String columnType) throws SQLException {
        String checkColumnSQL = """
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
            WHERE TABLE_NAME = '%s' AND COLUMN_NAME = '%s'
            """.formatted(tableName.toUpperCase(), columnName.toUpperCase());
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(checkColumnSQL)) {
            if (rs.next() && rs.getInt(1) == 0) {
                // 列不存在，添加列
                String alterTableSQL = """
                    ALTER TABLE %s ADD COLUMN %s %s
                    """.formatted(tableName, columnName, columnType);
                stmt.execute(alterTableSQL);
                System.out.println("[DatabaseService] 已添加列: " + tableName + "." + columnName);
            }
        }
    }
    
    /**
     * 开始新的监控会话
     * 
     * <p>在监控开始时调用此方法，创建一条新的会话记录。</p>
     * 
     * <p>执行流程：</p>
     * <ol>
     *   <li>开启事务</li>
     *   <li>插入新会话记录</li>
     *   <li>获取生成的 session_id</li>
     *   <li>提交事务</li>
     * </ol>
     * 
     * @param ifaceName 网络接口名称
     * @return CompletableFuture<Integer> 异步返回生成的 session_id
     */
    public CompletableFuture<Integer> startNewSession(String ifaceName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return startNewSessionSync(ifaceName);
            } catch (SQLException e) {
                throw new RuntimeException("创建监控会话失败: " + e.getMessage(), e);
            }
        }, executorService);
    }
    
    /**
     * 同步创建新会话（内部方法）
     * 
     * @param ifaceName 网络接口名称
     * @return 生成的 session_id
     * @throws SQLException 如果数据库操作失败
     */
    private int startNewSessionSync(String ifaceName) throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("数据库连接未初始化或已关闭");
        }
        
        // 开启事务
        connection.setAutoCommit(false);
        
        try {
            String insertSQL = """
                INSERT INTO %s (iface_name, start_time)
                VALUES (?, CURRENT_TIMESTAMP)
                """.formatted(TABLE_MONITORING_SESSIONS);
            
            try (PreparedStatement pstmt = connection.prepareStatement(
                    insertSQL, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, ifaceName);
                pstmt.executeUpdate();
                
                // 获取生成的 session_id
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        int sessionId = rs.getInt(1);
                        connection.commit();
                        System.out.println("[DatabaseService] 创建新会话: session_id=" + sessionId + ", iface=" + ifaceName);
                        return sessionId;
                    } else {
                        connection.rollback();
                        throw new SQLException("无法获取生成的 session_id");
                    }
                }
            }
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }
    
    /**
     * 保存流量明细记录
     * 
     * <p>在抓包循环中每秒调用此方法，保存一条明细记录。</p>
     * 
     * <p>执行流程：</p>
     * <ol>
     *   <li>开启事务</li>
     *   <li>插入明细记录</li>
     *   <li>提交事务</li>
     * </ol>
     * 
     * @param sessionId 会话 ID
     * @param downSpeed 下行速度（KB/s）
     * @param upSpeed 上行速度（KB/s）
     * @param sourceIp 源IP地址（可为null）
     * @param destIp 目标IP地址（可为null）
     * @param processName 进程名称（可为null）
     * @param protocol 协议类型（TCP, UDP, ICMP, 其他，可为null）
     * @return CompletableFuture<Void> 异步操作结果
     */
    public CompletableFuture<Void> saveDetailRecord(int sessionId, double downSpeed, double upSpeed,
                                                   String sourceIp, String destIp, String processName, String protocol) {
        return CompletableFuture.runAsync(() -> {
            try {
                saveDetailRecordSync(sessionId, downSpeed, upSpeed, sourceIp, destIp, processName, protocol);
            } catch (SQLException e) {
                throw new RuntimeException("保存流量明细记录失败: " + e.getMessage(), e);
            }
        }, executorService);
    }
    
    /**
     * 保存流量明细记录（向后兼容方法，不包含协议）
     * 
     * @param sessionId 会话 ID
     * @param downSpeed 下行速度（KB/s）
     * @param upSpeed 上行速度（KB/s）
     * @param sourceIp 源IP地址（可为null）
     * @param destIp 目标IP地址（可为null）
     * @param processName 进程名称（可为null）
     * @return CompletableFuture<Void> 异步操作结果
     */
    public CompletableFuture<Void> saveDetailRecord(int sessionId, double downSpeed, double upSpeed,
                                                   String sourceIp, String destIp, String processName) {
        return saveDetailRecord(sessionId, downSpeed, upSpeed, sourceIp, destIp, processName, null);
    }
    
    /**
     * 同步保存明细记录（内部方法）
     * 
     * @param sessionId 会话 ID
     * @param downSpeed 下行速度（KB/s）
     * @param upSpeed 上行速度（KB/s）
     * @param sourceIp 源IP地址（可为null）
     * @param destIp 目标IP地址（可为null）
     * @param processName 进程名称（可为null）
     * @param protocol 协议类型（TCP, UDP, ICMP, 其他，可为null）
     * @throws SQLException 如果数据库操作失败
     */
    private void saveDetailRecordSync(int sessionId, double downSpeed, double upSpeed,
                                     String sourceIp, String destIp, String processName, String protocol) throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("数据库连接未初始化或已关闭");
        }
        
        // 开启事务
        connection.setAutoCommit(false);
        
        try {
            String insertSQL = """
                INSERT INTO %s (session_id, down_speed, up_speed, source_ip, dest_ip, process_name, protocol, record_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """.formatted(TABLE_TRAFFIC_RECORDS);
            
            try (PreparedStatement pstmt = connection.prepareStatement(insertSQL)) {
                pstmt.setInt(1, sessionId);
                pstmt.setDouble(2, downSpeed);
                pstmt.setDouble(3, upSpeed);
                pstmt.setString(4, sourceIp);
                pstmt.setString(5, destIp);
                pstmt.setString(6, processName);
                pstmt.setString(7, protocol);
                
                pstmt.executeUpdate();
                connection.commit();
            }
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }
    
    /**
     * 结束监控会话
     * 
     * <p>在监控停止时调用此方法，自动计算会话的统计信息并更新会话记录。</p>
     * 
     * <p>执行流程：</p>
     * <ol>
     *   <li>开启事务</li>
     *   <li>查询该会话的所有明细记录</li>
     *   <li>计算统计信息（平均速度、最大速度、总字节数、持续时间等）</li>
     *   <li>更新会话记录</li>
     *   <li>提交事务</li>
     * </ol>
     * 
     * @param sessionId 会话 ID
     * @return CompletableFuture<Void> 异步操作结果
     */
    public CompletableFuture<Void> endSession(int sessionId) {
        return CompletableFuture.runAsync(() -> {
            try {
                endSessionSync(sessionId);
            } catch (SQLException e) {
                throw new RuntimeException("结束监控会话失败: " + e.getMessage(), e);
            }
        }, executorService);
    }
    
    /**
     * 同步结束会话（内部方法）
     * 
     * @param sessionId 会话 ID
     * @throws SQLException 如果数据库操作失败
     */
    private void endSessionSync(int sessionId) throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("数据库连接未初始化或已关闭");
        }
        
        // 开启事务
        connection.setAutoCommit(false);
        
        try {
            // 查询会话的开始时间
            String selectSessionSQL = """
                SELECT start_time FROM %s WHERE session_id = ?
                """.formatted(TABLE_MONITORING_SESSIONS);
            
            Timestamp startTime = null;
            try (PreparedStatement pstmt = connection.prepareStatement(selectSessionSQL)) {
                pstmt.setInt(1, sessionId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        startTime = rs.getTimestamp("start_time");
                    } else {
                        throw new SQLException("会话不存在: session_id=" + sessionId);
                    }
                }
            }
            
            // 查询该会话的所有明细记录，计算统计信息
            String selectRecordsSQL = """
                SELECT down_speed, up_speed, record_time
                FROM %s
                WHERE session_id = ?
                ORDER BY record_time
                """.formatted(TABLE_TRAFFIC_RECORDS);
            
            double totalDownSpeed = 0.0;
            double totalUpSpeed = 0.0;
            double maxDownSpeed = 0.0;
            double maxUpSpeed = 0.0;
            int recordCount = 0;
            Timestamp endTime = null;
            
            try (PreparedStatement pstmt = connection.prepareStatement(selectRecordsSQL)) {
                pstmt.setInt(1, sessionId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        double downSpeed = rs.getDouble("down_speed");
                        double upSpeed = rs.getDouble("up_speed");
                        Timestamp recordTime = rs.getTimestamp("record_time");
                        
                        totalDownSpeed += downSpeed;
                        totalUpSpeed += upSpeed;
                        maxDownSpeed = Math.max(maxDownSpeed, downSpeed);
                        maxUpSpeed = Math.max(maxUpSpeed, upSpeed);
                        recordCount++;
                        
                        // 最后一个记录的时间作为结束时间
                        if (endTime == null || recordTime.after(endTime)) {
                            endTime = recordTime;
                        }
                    }
                }
            }
            
            // 计算平均值
            double avgDownSpeed = recordCount > 0 ? totalDownSpeed / recordCount : 0.0;
            double avgUpSpeed = recordCount > 0 ? totalUpSpeed / recordCount : 0.0;
            
            // 计算持续时间（秒）
            long durationSeconds = 0;
            if (startTime != null && endTime != null) {
                durationSeconds = (endTime.getTime() - startTime.getTime()) / 1000;
            }
            
            // 计算总字节数（假设每秒记录一次，速度单位是 KB/s）
            long totalDownBytes = (long) (totalDownSpeed * 1024);
            long totalUpBytes = (long) (totalUpSpeed * 1024);
            
            // 更新会话记录
            String updateSessionSQL = """
                UPDATE %s
                SET end_time = ?,
                    duration_seconds = ?,
                    avg_down_speed = ?,
                    avg_up_speed = ?,
                    max_down_speed = ?,
                    max_up_speed = ?,
                    total_down_bytes = ?,
                    total_up_bytes = ?,
                    record_count = ?
                WHERE session_id = ?
                """.formatted(TABLE_MONITORING_SESSIONS);
            
            try (PreparedStatement pstmt = connection.prepareStatement(updateSessionSQL)) {
                pstmt.setTimestamp(1, endTime);
                pstmt.setLong(2, durationSeconds);
                pstmt.setDouble(3, avgDownSpeed);
                pstmt.setDouble(4, avgUpSpeed);
                pstmt.setDouble(5, maxDownSpeed);
                pstmt.setDouble(6, maxUpSpeed);
                pstmt.setLong(7, totalDownBytes);
                pstmt.setLong(8, totalUpBytes);
                pstmt.setInt(9, recordCount);
                pstmt.setInt(10, sessionId);
                
                pstmt.executeUpdate();
                connection.commit();
                
                System.out.println("[DatabaseService] 会话结束: session_id=" + sessionId + 
                    ", 持续时间=" + durationSeconds + "秒, 记录数=" + recordCount);
            }
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }
    
    /**
     * 查询所有监控会话
     * 
     * <p>按开始时间倒序排列，最新的会话在前面。</p>
     * 
     * @return CompletableFuture<List<MonitoringSession>> 异步查询结果
     */
    public CompletableFuture<List<MonitoringSession>> getAllSessions() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getAllSessionsSync();
            } catch (SQLException e) {
                throw new RuntimeException("查询监控会话失败: " + e.getMessage(), e);
            }
        }, executorService);
    }
    
    /**
     * 同步查询所有会话（内部方法）
     * 
     * @return 会话列表，按开始时间倒序排列
     * @throws SQLException 如果查询失败
     */
    private List<MonitoringSession> getAllSessionsSync() throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("数据库连接未初始化或已关闭");
        }
        
        String querySQL = """
            SELECT session_id, iface_name, start_time, end_time, duration_seconds,
                   avg_down_speed, avg_up_speed, max_down_speed, max_up_speed,
                   total_down_bytes, total_up_bytes, record_count
            FROM %s
            ORDER BY start_time DESC
            """.formatted(TABLE_MONITORING_SESSIONS);
        
        List<MonitoringSession> sessions = new ArrayList<>();
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(querySQL)) {
            
            while (rs.next()) {
                MonitoringSession session = new MonitoringSession(
                    rs.getInt("session_id"),
                    rs.getString("iface_name"),
                    rs.getTimestamp("start_time"),
                    rs.getTimestamp("end_time"),
                    rs.getLong("duration_seconds"),
                    rs.getDouble("avg_down_speed"),
                    rs.getDouble("avg_up_speed"),
                    rs.getDouble("max_down_speed"),
                    rs.getDouble("max_up_speed"),
                    rs.getLong("total_down_bytes"),
                    rs.getLong("total_up_bytes"),
                    rs.getInt("record_count")
                );
                sessions.add(session);
            }
        }
        
        return sessions;
    }
    
    /**
     * 删除指定的监控会话及其所有明细记录
     * 
     * <p>由于外键约束设置了 ON DELETE CASCADE，删除会话时会自动删除所有关联的明细记录。</p>
     * 
     * <p>执行流程：</p>
     * <ol>
     *   <li>开启事务</li>
     *   <li>删除会话记录（级联删除明细记录）</li>
     *   <li>提交事务</li>
     * </ol>
     * 
     * @param sessionId 会话 ID
     * @return CompletableFuture<Boolean> 异步操作结果，true 表示删除成功，false 表示会话不存在
     */
    public CompletableFuture<Boolean> deleteSession(int sessionId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return deleteSessionSync(sessionId);
            } catch (SQLException e) {
                throw new RuntimeException("删除监控会话失败: " + e.getMessage(), e);
            }
        }, executorService);
    }
    
    /**
     * 同步删除会话（内部方法）
     * 
     * @param sessionId 会话 ID
     * @return true 表示删除成功，false 表示会话不存在
     * @throws SQLException 如果数据库操作失败
     */
    private boolean deleteSessionSync(int sessionId) throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("数据库连接未初始化或已关闭");
        }
        
        // 开启事务
        connection.setAutoCommit(false);
        
        try {
            // 首先检查会话是否存在
            String checkSQL = """
                SELECT COUNT(*) FROM %s WHERE session_id = ?
                """.formatted(TABLE_MONITORING_SESSIONS);
            
            boolean sessionExists = false;
            try (PreparedStatement pstmt = connection.prepareStatement(checkSQL)) {
                pstmt.setInt(1, sessionId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        sessionExists = true;
                    }
                }
            }
            
            if (!sessionExists) {
                connection.rollback();
                return false;
            }
            
            // 删除会话（由于外键约束 ON DELETE CASCADE，会自动删除关联的明细记录）
            String deleteSQL = """
                DELETE FROM %s WHERE session_id = ?
                """.formatted(TABLE_MONITORING_SESSIONS);
            
            try (PreparedStatement pstmt = connection.prepareStatement(deleteSQL)) {
                pstmt.setInt(1, sessionId);
                int rowsAffected = pstmt.executeUpdate();
                
                if (rowsAffected > 0) {
                    System.out.println("[DatabaseService] 会话已删除: session_id=" + sessionId);
                    
                    // 删除后重新分配ID
                    reorderSessionIds();
                    
                    connection.commit();
                    return true;
                } else {
                    connection.commit();
                    return false;
                }
            }
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }
    
    /**
     * 重新分配会话ID，按照开始时间排序，从1开始连续编号
     * 
     * <p>执行流程：</p>
     * <ol>
     *   <li>禁用外键约束</li>
     *   <li>查询所有会话，按开始时间排序</li>
     *   <li>创建旧ID到新ID的映射（1, 2, 3...）</li>
     *   <li>更新 traffic_records 表中的 session_id</li>
     *   <li>删除并重新插入 monitoring_sessions 表（使用新ID）</li>
     *   <li>重新启用外键约束</li>
     *   <li>重置 AUTO_INCREMENT 序列</li>
     * </ol>
     * 
     * @throws SQLException 如果数据库操作失败
     */
    private void reorderSessionIds() throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("数据库连接未初始化或已关闭");
        }
        
        try {
            // 1. 禁用外键约束（H2数据库）
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("SET REFERENTIAL_INTEGRITY FALSE");
            }
            
            // 2. 查询所有会话，按开始时间排序
            String selectSQL = """
                SELECT session_id, iface_name, start_time, end_time, duration_seconds,
                       avg_down_speed, avg_up_speed, max_down_speed, max_up_speed,
                       total_down_bytes, total_up_bytes, record_count
                FROM %s
                ORDER BY start_time ASC
                """.formatted(TABLE_MONITORING_SESSIONS);
            
            List<MonitoringSession> sessions = new ArrayList<>();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(selectSQL)) {
                while (rs.next()) {
                    MonitoringSession session = new MonitoringSession(
                        rs.getInt("session_id"),
                        rs.getString("iface_name"),
                        rs.getTimestamp("start_time"),
                        rs.getTimestamp("end_time"),
                        rs.getLong("duration_seconds"),
                        rs.getDouble("avg_down_speed"),
                        rs.getDouble("avg_up_speed"),
                        rs.getDouble("max_down_speed"),
                        rs.getDouble("max_up_speed"),
                        rs.getLong("total_down_bytes"),
                        rs.getLong("total_up_bytes"),
                        rs.getInt("record_count")
                    );
                    sessions.add(session);
                }
            }
            
            // 如果没有会话，只重置 AUTO_INCREMENT
            if (sessions.isEmpty()) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("ALTER TABLE " + TABLE_MONITORING_SESSIONS + " ALTER COLUMN session_id RESTART WITH 1");
                }
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("SET REFERENTIAL_INTEGRITY TRUE");
                }
                return;
            }
            
            // 3. 创建旧ID到新ID的映射（按顺序：1, 2, 3...）
            Map<Integer, Integer> idMapping = new HashMap<>();
            for (int i = 0; i < sessions.size(); i++) {
                int oldId = sessions.get(i).getSessionId();
                int newId = i + 1;
                if (oldId != newId) {
                    idMapping.put(oldId, newId);
                }
            }
            
            // 如果没有需要更新的ID，直接返回
            if (idMapping.isEmpty()) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("SET REFERENTIAL_INTEGRITY TRUE");
                }
                return;
            }
            
            // 4. 更新 traffic_records 表中的 session_id
            for (Map.Entry<Integer, Integer> entry : idMapping.entrySet()) {
                int oldId = entry.getKey();
                int newId = entry.getValue();
                
                String updateTrafficSQL = """
                    UPDATE %s SET session_id = ? WHERE session_id = ?
                    """.formatted(TABLE_TRAFFIC_RECORDS);
                
                try (PreparedStatement pstmt = connection.prepareStatement(updateTrafficSQL)) {
                    pstmt.setInt(1, newId);
                    pstmt.setInt(2, oldId);
                    pstmt.executeUpdate();
                }
            }
            
            // 5. 删除所有 monitoring_sessions 记录，然后重新插入（使用新ID）
            // 先保存数据到临时列表
            List<MonitoringSession> sessionsToReinsert = new ArrayList<>();
            for (int i = 0; i < sessions.size(); i++) {
                MonitoringSession oldSession = sessions.get(i);
                int newId = i + 1;
                // 创建新会话对象，使用新ID
                MonitoringSession newSession = new MonitoringSession(
                    newId,
                    oldSession.getIfaceName(),
                    oldSession.getStartTime(),
                    oldSession.getEndTime(),
                    oldSession.getDurationSeconds(),
                    oldSession.getAvgDownSpeed(),
                    oldSession.getAvgUpSpeed(),
                    oldSession.getMaxDownSpeed(),
                    oldSession.getMaxUpSpeed(),
                    oldSession.getTotalDownBytes(),
                    oldSession.getTotalUpBytes(),
                    oldSession.getRecordCount()
                );
                sessionsToReinsert.add(newSession);
            }
            
            // 删除所有记录
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DELETE FROM " + TABLE_MONITORING_SESSIONS);
            }
            
            // 重新插入，使用新ID
            String insertSQL = """
                INSERT INTO %s (session_id, iface_name, start_time, end_time, duration_seconds,
                               avg_down_speed, avg_up_speed, max_down_speed, max_up_speed,
                               total_down_bytes, total_up_bytes, record_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(TABLE_MONITORING_SESSIONS);
            
            try (PreparedStatement pstmt = connection.prepareStatement(insertSQL)) {
                for (MonitoringSession session : sessionsToReinsert) {
                    pstmt.setInt(1, session.getSessionId());
                    pstmt.setString(2, session.getIfaceName());
                    pstmt.setTimestamp(3, session.getStartTime());
                    pstmt.setTimestamp(4, session.getEndTime());
                    pstmt.setLong(5, session.getDurationSeconds());
                    pstmt.setDouble(6, session.getAvgDownSpeed());
                    pstmt.setDouble(7, session.getAvgUpSpeed());
                    pstmt.setDouble(8, session.getMaxDownSpeed());
                    pstmt.setDouble(9, session.getMaxUpSpeed());
                    pstmt.setLong(10, session.getTotalDownBytes());
                    pstmt.setLong(11, session.getTotalUpBytes());
                    pstmt.setInt(12, session.getRecordCount());
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }
            
            // 6. 重置 AUTO_INCREMENT 序列
            int nextId = sessions.size() + 1;
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER TABLE " + TABLE_MONITORING_SESSIONS + " ALTER COLUMN session_id RESTART WITH " + nextId);
            }
            
            // 7. 重新启用外键约束
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("SET REFERENTIAL_INTEGRITY TRUE");
            }
            
            System.out.println("[DatabaseService] 会话ID已重新分配，共 " + sessions.size() + " 个会话，ID范围：1-" + sessions.size());
            
        } catch (SQLException e) {
            // 确保重新启用外键约束
            try {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("SET REFERENTIAL_INTEGRITY TRUE");
                }
            } catch (SQLException e2) {
                // 忽略此错误
            }
            throw e;
        }
    }
    
    /**
     * 批量删除指定的监控会话及其所有明细记录
     * 
     * <p>由于外键约束设置了 ON DELETE CASCADE，删除会话时会自动删除所有关联的明细记录。</p>
     * 
     * <p>执行流程：</p>
     * <ol>
     *   <li>开启事务</li>
     *   <li>批量删除会话记录（级联删除明细记录）</li>
     *   <li>提交事务</li>
     * </ol>
     * 
     * @param sessionIds 会话 ID 列表
     * @return CompletableFuture<Integer> 异步操作结果，返回成功删除的会话数量
     */
    public CompletableFuture<Integer> deleteSessions(List<Integer> sessionIds) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return deleteSessionsSync(sessionIds);
            } catch (SQLException e) {
                throw new RuntimeException("批量删除监控会话失败: " + e.getMessage(), e);
            }
        }, executorService);
    }
    
    /**
     * 同步批量删除会话（内部方法）
     * 
     * @param sessionIds 会话 ID 列表
     * @return 成功删除的会话数量
     * @throws SQLException 如果数据库操作失败
     */
    private int deleteSessionsSync(List<Integer> sessionIds) throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("数据库连接未初始化或已关闭");
        }
        
        if (sessionIds == null || sessionIds.isEmpty()) {
            return 0;
        }
        
        // 开启事务
        connection.setAutoCommit(false);
        
        try {
            // 构建批量删除 SQL（使用 IN 子句）
            StringBuilder deleteSQL = new StringBuilder("DELETE FROM ");
            deleteSQL.append(TABLE_MONITORING_SESSIONS);
            deleteSQL.append(" WHERE session_id IN (");
            
            // 构建占位符 (?, ?, ?, ...)
            for (int i = 0; i < sessionIds.size(); i++) {
                if (i > 0) {
                    deleteSQL.append(", ");
                }
                deleteSQL.append("?");
            }
            deleteSQL.append(")");
            
            try (PreparedStatement pstmt = connection.prepareStatement(deleteSQL.toString())) {
                // 设置参数
                for (int i = 0; i < sessionIds.size(); i++) {
                    pstmt.setInt(i + 1, sessionIds.get(i));
                }
                
                int rowsAffected = pstmt.executeUpdate();
                
                if (rowsAffected > 0) {
                    // 删除后重新分配ID
                    reorderSessionIds();
                }
                
                connection.commit();
                
                System.out.println("[DatabaseService] 批量删除会话: 删除 " + rowsAffected + " 个会话");
                return rowsAffected;
            }
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }
    
    /**
     * 查询指定会话的所有明细记录
     * 
     * <p>用于在 UI 中点击某个历史会话时，加载其所有明细数据以重新绘制图表。</p>
     * 
     * @param sessionId 会话 ID
     * @return CompletableFuture<List<TrafficRecord>> 异步查询结果
     */
    public CompletableFuture<List<TrafficRecord>> getRecordsBySession(int sessionId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getRecordsBySessionSync(sessionId);
            } catch (SQLException e) {
                throw new RuntimeException("查询流量明细记录失败: " + e.getMessage(), e);
            }
        }, executorService);
    }
    
    /**
     * 同步查询指定会话的明细记录（内部方法）
     * 
     * @param sessionId 会话 ID
     * @return 明细记录列表，按时间顺序排列
     * @throws SQLException 如果查询失败
     */
    private List<TrafficRecord> getRecordsBySessionSync(int sessionId) throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("数据库连接未初始化或已关闭");
        }
        
        String querySQL = """
            SELECT record_id, session_id, down_speed, up_speed, source_ip, dest_ip, process_name, protocol, record_time
            FROM %s
            WHERE session_id = ?
            ORDER BY record_time ASC
            """.formatted(TABLE_TRAFFIC_RECORDS);
        
        List<TrafficRecord> records = new ArrayList<>();
        
        try (PreparedStatement pstmt = connection.prepareStatement(querySQL)) {
            pstmt.setInt(1, sessionId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    TrafficRecord record = new TrafficRecord(
                        rs.getLong("record_id"),
                        rs.getInt("session_id"),
                        rs.getDouble("down_speed"),
                        rs.getDouble("up_speed"),
                        rs.getString("source_ip"),
                        rs.getString("dest_ip"),
                        rs.getString("process_name"),
                        rs.getString("protocol"),
                        rs.getTimestamp("record_time")
                    );
                    records.add(record);
                }
            }
        }
        
        return records;
    }
    
    // ========== 向后兼容方法 ==========
    
    /**
     * 异步查询所有流量历史记录（向后兼容方法）
     * 
     * <p>此方法从新的 traffic_records 表中查询所有记录，保持与旧 API 的兼容性。</p>
     * <p>按记录时间倒序排列，最新的记录在前面。</p>
     * 
     * @return CompletableFuture<List<TrafficRecord>> 异步查询结果
     */
    public CompletableFuture<List<TrafficRecord>> queryAllTrafficHistoryAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return queryAllTrafficHistorySync();
            } catch (SQLException e) {
                throw new RuntimeException("查询流量历史数据失败: " + e.getMessage(), e);
            }
        }, executorService);
    }
    
    /**
     * 同步查询所有流量历史记录（内部方法）
     * 
     * <p>为了保持向后兼容，此方法会 JOIN 会话表以获取网卡名称。</p>
     * 
     * @return 流量历史记录列表，按时间倒序排列
     * @throws SQLException 如果查询失败
     */
    private List<TrafficRecord> queryAllTrafficHistorySync() throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("数据库连接未初始化或已关闭");
        }
        
        // JOIN 会话表以获取网卡名称，保持向后兼容
        String querySQL = """
            SELECT tr.record_id, tr.session_id, tr.down_speed, tr.up_speed, 
                   tr.source_ip, tr.dest_ip, tr.process_name, tr.protocol, tr.record_time,
                   ms.iface_name
            FROM %s tr
            INNER JOIN %s ms ON tr.session_id = ms.session_id
            ORDER BY tr.record_time DESC
            """.formatted(TABLE_TRAFFIC_RECORDS, TABLE_MONITORING_SESSIONS);
        
        List<TrafficRecord> records = new ArrayList<>();
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(querySQL)) {
            
            while (rs.next()) {
                TrafficRecord record = new TrafficRecordWithIface(
                    rs.getLong("record_id"),
                    rs.getInt("session_id"),
                    rs.getDouble("down_speed"),
                    rs.getDouble("up_speed"),
                    rs.getString("source_ip"),
                    rs.getString("dest_ip"),
                    rs.getString("process_name"),
                    rs.getString("protocol"),
                    rs.getTimestamp("record_time"),
                    rs.getString("iface_name")
                );
                records.add(record);
            }
        }
        
        return records;
    }
    
    /**
     * 异步查询指定网卡的流量历史记录（向后兼容方法）
     * 
     * @param ifaceName 网络接口名称
     * @return CompletableFuture<List<TrafficRecord>> 异步查询结果
     */
    public CompletableFuture<List<TrafficRecord>> queryTrafficHistoryByInterfaceAsync(String ifaceName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return queryTrafficHistoryByInterfaceSync(ifaceName);
            } catch (SQLException e) {
                throw new RuntimeException("查询流量历史数据失败: " + e.getMessage(), e);
            }
        }, executorService);
    }
    
    /**
     * 同步查询指定网卡的流量历史记录（内部方法）
     * 
     * @param ifaceName 网络接口名称
     * @return 流量历史记录列表，按时间倒序排列
     * @throws SQLException 如果查询失败
     */
    private List<TrafficRecord> queryTrafficHistoryByInterfaceSync(String ifaceName) throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("数据库连接未初始化或已关闭");
        }
        
            String querySQL = """
            SELECT tr.record_id, tr.session_id, tr.down_speed, tr.up_speed, 
                   tr.source_ip, tr.dest_ip, tr.process_name, tr.protocol, tr.record_time,
                   ms.iface_name
            FROM %s tr
            INNER JOIN %s ms ON tr.session_id = ms.session_id
            WHERE ms.iface_name = ?
            ORDER BY tr.record_time DESC
            """.formatted(TABLE_TRAFFIC_RECORDS, TABLE_MONITORING_SESSIONS);
        
        List<TrafficRecord> records = new ArrayList<>();
        
        try (PreparedStatement pstmt = connection.prepareStatement(querySQL)) {
            pstmt.setString(1, ifaceName);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    TrafficRecord record = new TrafficRecordWithIface(
                        rs.getLong("record_id"),
                        rs.getInt("session_id"),
                        rs.getDouble("down_speed"),
                        rs.getDouble("up_speed"),
                        rs.getString("source_ip"),
                        rs.getString("dest_ip"),
                        rs.getString("process_name"),
                        rs.getString("protocol"),
                        rs.getTimestamp("record_time"),
                        rs.getString("iface_name")
                    );
                    records.add(record);
                }
            }
        }
        
        return records;
    }
    
    /**
     * 异步查询最近的流量历史记录（向后兼容方法）
     * 
     * @param limit 返回的最大记录数
     * @return CompletableFuture<List<TrafficRecord>> 异步查询结果
     */
    public CompletableFuture<List<TrafficRecord>> queryRecentTrafficHistoryAsync(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return queryRecentTrafficHistorySync(limit);
            } catch (SQLException e) {
                throw new RuntimeException("查询流量历史数据失败: " + e.getMessage(), e);
            }
        }, executorService);
    }
    
    /**
     * 同步查询最近的流量历史记录（内部方法）
     * 
     * @param limit 返回的最大记录数
     * @return 流量历史记录列表，按时间倒序排列
     * @throws SQLException 如果查询失败
     */
    private List<TrafficRecord> queryRecentTrafficHistorySync(int limit) throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("数据库连接未初始化或已关闭");
        }
        
            String querySQL = """
            SELECT tr.record_id, tr.session_id, tr.down_speed, tr.up_speed, 
                   tr.source_ip, tr.dest_ip, tr.process_name, tr.protocol, tr.record_time,
                   ms.iface_name
            FROM %s tr
            INNER JOIN %s ms ON tr.session_id = ms.session_id
            ORDER BY tr.record_time DESC
            LIMIT ?
            """.formatted(TABLE_TRAFFIC_RECORDS, TABLE_MONITORING_SESSIONS);
        
        List<TrafficRecord> records = new ArrayList<>();
        
        try (PreparedStatement pstmt = connection.prepareStatement(querySQL)) {
            pstmt.setInt(1, limit);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    TrafficRecord record = new TrafficRecordWithIface(
                        rs.getLong("record_id"),
                        rs.getInt("session_id"),
                        rs.getDouble("down_speed"),
                        rs.getDouble("up_speed"),
                        rs.getString("source_ip"),
                        rs.getString("dest_ip"),
                        rs.getString("process_name"),
                        rs.getString("protocol"),
                        rs.getTimestamp("record_time"),
                        rs.getString("iface_name")
                    );
                    records.add(record);
                }
            }
        }
        
        return records;
    }
    
    /**
     * 使用 QueryBuilder 异步查询流量记录
     * 
     * <p>此方法使用 TrafficRecordQueryBuilder 构建的 SQL 查询流量记录。</p>
     * 
     * @param queryBuilder 查询构建器
     * @return CompletableFuture<List<TrafficRecord>> 异步查询结果
     */
    public CompletableFuture<List<TrafficRecord>> queryRecordsWithFilter(
            TrafficRecordQueryBuilder queryBuilder) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return queryRecordsWithFilterSync(queryBuilder);
            } catch (SQLException e) {
                throw new RuntimeException("查询流量记录失败: " + e.getMessage(), e);
            }
        }, executorService);
    }
    
    /**
     * 同步查询流量记录（使用 QueryBuilder）
     * 
     * @param queryBuilder 查询构建器
     * @return 流量记录列表
     * @throws SQLException 如果查询失败
     */
    private List<TrafficRecord> queryRecordsWithFilterSync(
            TrafficRecordQueryBuilder queryBuilder) throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("数据库连接未初始化或已关闭");
        }
        
        TrafficRecordQueryBuilder.QueryResult queryResult = queryBuilder.build();
        String sql = queryResult.getSql();
        List<Object> parameters = queryResult.getParameters();
        
        List<TrafficRecord> records = new ArrayList<>();
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            // 设置参数
            for (int i = 0; i < parameters.size(); i++) {
                Object param = parameters.get(i);
                int paramIndex = i + 1;
                
                if (param instanceof String) {
                    pstmt.setString(paramIndex, (String) param);
                } else if (param instanceof Integer) {
                    pstmt.setInt(paramIndex, (Integer) param);
                } else if (param instanceof Double) {
                    pstmt.setDouble(paramIndex, (Double) param);
                } else if (param instanceof Long) {
                    pstmt.setLong(paramIndex, (Long) param);
                } else {
                    pstmt.setObject(paramIndex, param);
                }
            }
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    TrafficRecord record = new TrafficRecordWithIface(
                        rs.getLong("record_id"),
                        rs.getInt("session_id"),
                        rs.getDouble("down_speed"),
                        rs.getDouble("up_speed"),
                        rs.getString("source_ip"),
                        rs.getString("dest_ip"),
                        rs.getString("process_name"),
                        rs.getString("protocol"),
                        rs.getTimestamp("record_time"),
                        rs.getString("iface_name")
                    );
                    records.add(record);
                }
            }
        }
        
        return records;
    }
    
    /**
     * 异步获取所有不重复的进程名称列表
     * 
     * <p>用于填充应用过滤下拉列表。</p>
     * 
     * @return CompletableFuture<List<String>> 异步返回进程名称列表（不包含 null 和空字符串）
     */
    public CompletableFuture<List<String>> getDistinctProcessNames() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getDistinctProcessNamesSync();
            } catch (SQLException e) {
                throw new RuntimeException("查询进程名称列表失败: " + e.getMessage(), e);
            }
        }, executorService);
    }
    
    /**
     * 同步获取所有不重复的进程名称列表（内部方法）
     * 
     * @return 进程名称列表（不包含 null 和空字符串），按字母顺序排序
     * @throws SQLException 如果查询失败
     */
    private List<String> getDistinctProcessNamesSync() throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("数据库连接未初始化或已关闭");
        }
        
        String querySQL = """
            SELECT DISTINCT process_name
            FROM %s
            WHERE process_name IS NOT NULL AND process_name != ''
            ORDER BY process_name ASC
            """.formatted(TABLE_TRAFFIC_RECORDS);
        
        List<String> processNames = new ArrayList<>();
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(querySQL)) {
            
            while (rs.next()) {
                String processName = rs.getString("process_name");
                if (processName != null && !processName.trim().isEmpty()) {
                    processNames.add(processName);
                }
            }
        }
        
        return processNames;
    }
    
    /**
     * 异步保存流量数据到数据库（向后兼容方法）
     * 
     * <p><strong>注意：</strong>此方法是为了保持向后兼容而保留的。
     * 新代码应该使用会话模式：startNewSession() -> saveDetailRecord() -> endSession()</p>
     * 
     * <p>此方法会创建一个临时会话，保存一条记录，然后立即结束会话。
     * 不推荐在高频调用场景下使用。</p>
     * 
     * @param ifaceName 网络接口名称
     * @param downSpeed 下行速度（KB/s）
     * @param upSpeed 上行速度（KB/s）
     * @param sourceIp 源IP地址（可为null）
     * @param destIp 目标IP地址（可为null）
     * @param processName 进程名称（可为null）
     * @return CompletableFuture<Void> 异步操作的结果
     */
    public CompletableFuture<Void> saveTrafficDataAsync(String ifaceName, double downSpeed, double upSpeed, 
                                                       String sourceIp, String destIp, String processName) {
        return CompletableFuture.runAsync(() -> {
            try {
                // 创建临时会话
                int sessionId = startNewSessionSync(ifaceName);
                
                // 保存明细记录（不包含协议，使用默认值 null）
                saveDetailRecordSync(sessionId, downSpeed, upSpeed, sourceIp, destIp, processName, null);
                
                // 立即结束会话
                endSessionSync(sessionId);
            } catch (SQLException e) {
                throw new RuntimeException("保存流量数据失败: " + e.getMessage(), e);
            }
        }, executorService);
    }
    
    /**
     * 异步保存流量数据到数据库（兼容旧版本，不包含进程信息）
     * 
     * @param ifaceName 网络接口名称
     * @param downSpeed 下行速度（KB/s）
     * @param upSpeed 上行速度（KB/s）
     * @param sourceIp 源IP地址（可为null）
     * @param destIp 目标IP地址（可为null）
     * @return CompletableFuture<Void> 异步操作的结果
     */
    public CompletableFuture<Void> saveTrafficDataAsync(String ifaceName, double downSpeed, double upSpeed, 
                                                       String sourceIp, String destIp) {
        return saveTrafficDataAsync(ifaceName, downSpeed, upSpeed, sourceIp, destIp, null);
    }
    
    /**
     * 异步保存流量数据到数据库（兼容旧版本，不包含IP和进程信息）
     * 
     * @param ifaceName 网络接口名称
     * @param downSpeed 下行速度（KB/s）
     * @param upSpeed 上行速度（KB/s）
     * @return CompletableFuture<Void> 异步操作的结果
     */
    public CompletableFuture<Void> saveTrafficDataAsync(String ifaceName, double downSpeed, double upSpeed) {
        return saveTrafficDataAsync(ifaceName, downSpeed, upSpeed, null, null, null);
    }
    
    /**
     * 关闭数据库连接和线程池
     * 
     * <p>在应用程序退出时调用，确保资源被正确释放。</p>
     */
    public void shutdown() {
        // 关闭线程池
        executorService.shutdown();
        
        // 关闭数据库连接
        if (connection != null) {
            try {
                connection.close();
                System.out.println("[DatabaseService] 数据库连接已关闭");
            } catch (SQLException e) {
                System.err.println("[DatabaseService] 关闭数据库连接时发生错误: " + e.getMessage());
            }
        }
    }
    
    /**
     * 获取数据库连接（用于测试或高级操作）
     * 
     * @return 数据库连接对象
     */
    public Connection getConnection() {
        return connection;
    }
    
    // ========== 数据模型类 ==========
    
    /**
     * 监控会话数据模型
     * 
     * 用于表示数据库中的一条监控会话记录
     */
    public static class MonitoringSession {
        private final int sessionId;
        private final String ifaceName;
        private final Timestamp startTime;
        private final Timestamp endTime;
        private final long durationSeconds;
        private final double avgDownSpeed;
        private final double avgUpSpeed;
        private final double maxDownSpeed;
        private final double maxUpSpeed;
        private final long totalDownBytes;
        private final long totalUpBytes;
        private final int recordCount;
        
        public MonitoringSession(int sessionId, String ifaceName, Timestamp startTime, Timestamp endTime,
                                long durationSeconds, double avgDownSpeed, double avgUpSpeed,
                                double maxDownSpeed, double maxUpSpeed,
                                long totalDownBytes, long totalUpBytes, int recordCount) {
            this.sessionId = sessionId;
            this.ifaceName = ifaceName;
            this.startTime = startTime;
            this.endTime = endTime;
            this.durationSeconds = durationSeconds;
            this.avgDownSpeed = avgDownSpeed;
            this.avgUpSpeed = avgUpSpeed;
            this.maxDownSpeed = maxDownSpeed;
            this.maxUpSpeed = maxUpSpeed;
            this.totalDownBytes = totalDownBytes;
            this.totalUpBytes = totalUpBytes;
            this.recordCount = recordCount;
        }
        
        // Getter 方法
        public int getSessionId() { return sessionId; }
        public String getIfaceName() { return ifaceName; }
        public Timestamp getStartTime() { return startTime; }
        public Timestamp getEndTime() { return endTime; }
        public long getDurationSeconds() { return durationSeconds; }
        public double getAvgDownSpeed() { return avgDownSpeed; }
        public double getAvgUpSpeed() { return avgUpSpeed; }
        public double getMaxDownSpeed() { return maxDownSpeed; }
        public double getMaxUpSpeed() { return maxUpSpeed; }
        public long getTotalDownBytes() { return totalDownBytes; }
        public long getTotalUpBytes() { return totalUpBytes; }
        public int getRecordCount() { return recordCount; }
        
        @Override
        public String toString() {
            return String.format("MonitoringSession{sessionId=%d, iface='%s', start=%s, duration=%d秒, records=%d}",
                    sessionId, ifaceName, startTime, durationSeconds, recordCount);
        }
    }
    
    /**
     * 流量明细记录数据模型
     * 
     * 用于表示数据库中的一条流量明细记录
     */
    public static class TrafficRecord {
        private final long recordId;
        private final int sessionId;
        private final double downSpeed;
        private final double upSpeed;
        private final String sourceIp;
        private final String destIp;
        private final String processName;
        private final String protocol;
        private final Timestamp recordTime;
        
        public TrafficRecord(long recordId, int sessionId, double downSpeed, double upSpeed,
                           String sourceIp, String destIp, String processName, String protocol, Timestamp recordTime) {
            this.recordId = recordId;
            this.sessionId = sessionId;
            this.downSpeed = downSpeed;
            this.upSpeed = upSpeed;
            this.sourceIp = sourceIp;
            this.destIp = destIp;
            this.processName = processName;
            this.protocol = protocol;
            this.recordTime = recordTime;
        }
        
        // 向后兼容构造函数（不包含协议）
        public TrafficRecord(long recordId, int sessionId, double downSpeed, double upSpeed,
                           String sourceIp, String destIp, String processName, Timestamp recordTime) {
            this(recordId, sessionId, downSpeed, upSpeed, sourceIp, destIp, processName, null, recordTime);
        }
        
        // Getter 方法
        public long getRecordId() { return recordId; }
        public int getSessionId() { return sessionId; }
        public double getDownSpeed() { return downSpeed; }
        public double getUpSpeed() { return upSpeed; }
        public String getSourceIp() { return sourceIp; }
        public String getDestIp() { return destIp; }
        public String getProcessName() { return processName; }
        public String getProtocol() { return protocol; }
        public Timestamp getRecordTime() { return recordTime; }
        
        // 向后兼容方法
        /** @deprecated 使用 getRecordId() 代替 */
        @Deprecated
        public long getId() { return recordId; }
        
        /** @deprecated 使用 getRecordTime() 代替 */
        @Deprecated
        public Timestamp getCaptureTime() { return recordTime; }
        
        /**
         * 获取网卡名称（需要查询会话表）
         * 注意：此方法会执行数据库查询，性能较低，建议缓存结果
         * 
         * @return 网卡名称，如果查询失败则返回 null
         */
        public String getIfaceName() {
            // 为了保持向后兼容，这里需要查询会话表
            // 但为了性能考虑，建议在查询时 JOIN 会话表获取网卡名称
            // 这里返回 null，调用者应该通过其他方式获取
            return null;
        }
        
        @Override
        public String toString() {
            return String.format("TrafficRecord{recordId=%d, sessionId=%d, down=%.2f KB/s, up=%.2f KB/s, time=%s}",
                    recordId, sessionId, downSpeed, upSpeed, recordTime);
        }
    }
    
    /**
     * 带网卡名称的流量记录（用于向后兼容）
     * 
     * 内部类，用于在查询时包含网卡名称信息
     */
    private static class TrafficRecordWithIface extends TrafficRecord {
        private final String ifaceName;
        
        public TrafficRecordWithIface(long recordId, int sessionId, double downSpeed, double upSpeed,
                                     String sourceIp, String destIp, String processName, String protocol,
                                     Timestamp recordTime, String ifaceName) {
            super(recordId, sessionId, downSpeed, upSpeed, sourceIp, destIp, processName, protocol, recordTime);
            this.ifaceName = ifaceName;
        }
        
        @Override
        public String getIfaceName() {
            return ifaceName;
        }
    }
}
