package com.netpulse.netpulsefx.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 进程上下文服务
 * 
 * <p>功能说明：</p>
 * <ul>
 *   <li>通过 netstat 命令获取网络连接与进程的映射关系</li>
 *   <li>使用 ProcessHandle 获取进程详细信息</li>
 *   <li>后台定期更新连接-PID 映射表</li>
 *   <li>提供快速查询方法，根据数据包的本地 IP 和端口查找对应进程</li>
 * </ul>
 * 
 * <p>性能优化：</p>
 * <ul>
 *   <li>使用 ConcurrentHashMap 确保线程安全</li>
 *   <li>后台线程每 2-3 秒更新一次映射表，避免频繁执行系统命令</li>
 *   <li>查询失败时返回"未知进程"，不阻塞抓包流程</li>
 * </ul>
 * 
 * <p>平台支持：</p>
 * <p>当前实现针对 Windows 平台，使用 netstat -ano 命令。</p>
 * 
 * @author NetPulseFX Team
 */
public class ProcessContextService {
    
    /** 单例实例 */
    private static ProcessContextService instance;
    
    /** 连接-PID 映射表：LocalAddress:Port -> PID（线程安全） */
    private final ConcurrentHashMap<String, Integer> connectionPidMap;
    
    /** PID-进程信息映射表：PID -> ProcessInfo（线程安全） */
    private final ConcurrentHashMap<Integer, ProcessInfo> pidProcessMap;
    
    /** 后台更新任务执行器 */
    private ScheduledExecutorService scheduler;
    
    /** 更新任务的 Future */
    private ScheduledFuture<?> updateTaskFuture;
    
    /** 更新间隔（秒） */
    private static final int UPDATE_INTERVAL_SECONDS = 3;
    
    /** netstat 输出解析模式（Windows） */
    // 示例格式：
    // TCP    0.0.0.0:80             0.0.0.0:0              LISTENING       1234
    // TCP    192.168.1.100:54321   8.8.8.8:443           ESTABLISHED     5678
    // UDP    0.0.0.0:53             *:*                                   1234
    private static final Pattern NETSTAT_PATTERN = Pattern.compile(
        "\\s+(TCP|UDP)\\s+" +                           // 协议类型
        "([\\d\\.\\[\\]]+|\\*):(\\d+|\\*)\\s+" +        // 本地地址:端口
        "([\\d\\.\\[\\]]+|\\*):(\\d+|\\*)\\s*" +        // 远程地址:端口
        "(LISTENING|ESTABLISHED|TIME_WAIT|CLOSE_WAIT|SYN_SENT|CLOSED|CLOSE_WAIT|FIN_WAIT_1|FIN_WAIT_2|LAST_ACK|SYN_RCVD)?\\s*" + // 状态（可选）
        "(\\d+)"                                         // PID
    );
    
    /**
     * 私有构造函数（单例模式）
     */
    private ProcessContextService() {
        this.connectionPidMap = new ConcurrentHashMap<>();
        this.pidProcessMap = new ConcurrentHashMap<>();
        
        // 创建单线程调度器
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ProcessContextService-UpdateThread");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * 获取服务实例（单例模式）
     * 
     * @return ProcessContextService 实例
     */
    public static synchronized ProcessContextService getInstance() {
        if (instance == null) {
            instance = new ProcessContextService();
        }
        return instance;
    }
    
    /**
     * 启动后台更新任务
     * 每 2-3 秒执行一次 netstat 命令并更新映射表
     */
    public void start() {
        if (updateTaskFuture != null && !updateTaskFuture.isCancelled()) {
            // 已经在运行
            return;
        }
        
        // 立即执行一次更新
        updateConnectionMap();
        
        // 启动定期更新任务
        updateTaskFuture = scheduler.scheduleAtFixedRate(
            this::updateConnectionMap,
            UPDATE_INTERVAL_SECONDS,
            UPDATE_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        
        System.out.println("[ProcessContextService] 后台更新任务已启动，更新间隔: " + 
            UPDATE_INTERVAL_SECONDS + " 秒");
    }
    
    /**
     * 停止后台更新任务
     */
    public void stop() {
        if (updateTaskFuture != null) {
            updateTaskFuture.cancel(false);
            updateTaskFuture = null;
        }
        
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        System.out.println("[ProcessContextService] 后台更新任务已停止");
    }
    
    /**
     * 更新连接-PID 映射表
     * 执行 netstat -ano 命令并解析输出
     */
    private void updateConnectionMap() {
        try {
            // 检查操作系统
            String os = System.getProperty("os.name").toLowerCase();
            if (!os.contains("windows")) {
                System.err.println("[ProcessContextService] 当前仅支持 Windows 系统");
                return;
            }
            
            // 执行 netstat -ano 命令
            Process process = new ProcessBuilder("netstat", "-ano")
                .redirectErrorStream(true)
                .start();
            
            // 解析输出
            Map<String, Integer> newMap = new HashMap<>();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "GBK"))) {
                
                String line;
                boolean headerSkipped = false;
                
                while ((line = reader.readLine()) != null) {
                    // 跳过表头
                    if (!headerSkipped) {
                        if (line.contains("本地地址") || line.contains("Local Address")) {
                            headerSkipped = true;
                        }
                        continue;
                    }
                    
                    // 解析每一行
                    Matcher matcher = NETSTAT_PATTERN.matcher(line);
                    if (matcher.find()) {
                        try {
                            String protocol = matcher.group(1);
                            String localAddress = matcher.group(2);
                            String localPort = matcher.group(3);
                            
                            // 获取 PID（最后一组）
                            int groupCount = matcher.groupCount();
                            String pidStr = matcher.group(groupCount);
                            
                            // 跳过通配符端口
                            if ("*".equals(localPort)) {
                                continue;
                            }
                            
                            int pid = Integer.parseInt(pidStr.trim());
                            
                            // 构建映射键：LocalAddress:Port
                            String key = normalizeAddress(localAddress) + ":" + localPort;
                            newMap.put(key, pid);
                            
                            // 同时更新 PID-进程信息映射（如果还没有）
                            if (!pidProcessMap.containsKey(pid)) {
                                updateProcessInfo(pid);
                            }
                        } catch (NumberFormatException e) {
                            // 忽略无效的 PID
                        } catch (Exception e) {
                            // 忽略解析错误
                        }
                    }
                }
            }
            
            // 更新映射表（原子操作）
            connectionPidMap.clear();
            connectionPidMap.putAll(newMap);
            
            System.out.println("[ProcessContextService] 已更新连接映射表，共 " + 
                connectionPidMap.size() + " 个连接");
            
            // 等待进程结束
            process.waitFor();
            
        } catch (Exception e) {
            System.err.println("[ProcessContextService] 更新连接映射表失败: " + e.getMessage());
            // 不抛出异常，避免影响主流程
        }
    }
    
    /**
     * 规范化地址格式
     * 将 [::] 和 * 转换为标准格式
     * 
     * @param address 原始地址
     * @return 规范化后的地址
     */
    private String normalizeAddress(String address) {
        if (address == null) {
            return "0.0.0.0";
        }
        
        address = address.trim();
        
        // IPv6 通配符
        if (address.equals("[::]") || address.equals("*")) {
            return "0.0.0.0";
        }
        
        // IPv6 地址（保留方括号）
        if (address.startsWith("[") && address.endsWith("]")) {
            return address;
        }
        
        return address;
    }
    
    /**
     * 更新进程信息
     * 使用 ProcessHandle 获取进程详细信息
     * 
     * @param pid 进程 ID
     */
    private void updateProcessInfo(int pid) {
        try {
            Optional<ProcessHandle> handleOpt = ProcessHandle.of(pid);
            
            if (handleOpt.isPresent()) {
                ProcessHandle handle = handleOpt.get();
                
                String processName = "未知进程";
                String processPath = "";
                
                // 获取进程信息
                Optional<String> infoOpt = handle.info().command();
                if (infoOpt.isPresent()) {
                    processPath = infoOpt.get();
                    // 从路径中提取进程名
                    String[] parts = processPath.replace("\\", "/").split("/");
                    if (parts.length > 0) {
                        processName = parts[parts.length - 1];
                    }
                } else {
                    // 如果无法获取命令，尝试使用描述
                    handle.info().commandLine().ifPresent(cmd -> {
                        String[] parts = cmd.split("\\s+");
                        if (parts.length > 0) {
                            String cmdPath = parts[0];
                            String[] pathParts = cmdPath.replace("\\", "/").split("/");
                            if (pathParts.length > 0) {
                                pidProcessMap.put(pid, new ProcessInfo(
                                    pathParts[pathParts.length - 1], cmdPath));
                            }
                        }
                    });
                    return; // 已通过 commandLine 更新
                }
                
                // 存储进程信息
                pidProcessMap.put(pid, new ProcessInfo(processName, processPath));
                
            } else {
                // 进程不存在或已结束
                pidProcessMap.put(pid, new ProcessInfo("进程已结束", ""));
            }
            
        } catch (Exception e) {
            // 获取进程信息失败，使用默认值
            pidProcessMap.put(pid, new ProcessInfo("无法获取进程信息", ""));
            System.err.println("[ProcessContextService] 获取进程信息失败 (PID: " + 
                pid + "): " + e.getMessage());
        }
    }
    
    /**
     * 根据数据包的本地 IP 和端口查找对应进程
     * 
     * <p>此方法设计为快速查询，不执行系统命令，直接从内存映射表查找。</p>
     * 
     * @param localIp 本地 IP 地址
     * @param localPort 本地端口
     * @return 进程名称，如果找不到则返回"未知进程"
     */
    public String findProcessByPacket(String localIp, int localPort) {
        if (localIp == null || localPort <= 0) {
            return "未知进程";
        }
        
        // 规范化 IP 地址
        String normalizedIp = normalizeAddress(localIp);
        
        // 构建查询键
        String key = normalizedIp + ":" + localPort;
        
        // 查找 PID
        Integer pid = connectionPidMap.get(key);
        
        if (pid == null) {
            // 尝试使用 0.0.0.0 作为通配符（监听所有接口）
            if (!normalizedIp.equals("0.0.0.0")) {
                pid = connectionPidMap.get("0.0.0.0:" + localPort);
            }
        }
        
        if (pid == null) {
            return "未知进程";
        }
        
        // 查找进程信息
        ProcessInfo processInfo = pidProcessMap.get(pid);
        
        if (processInfo == null) {
            // 如果映射表中没有，尝试实时获取（不阻塞）
            updateProcessInfo(pid);
            processInfo = pidProcessMap.get(pid);
        }
        
        if (processInfo == null) {
            return "未知进程";
        }
        
        return processInfo.getProcessName();
    }
    
    /**
     * 获取进程完整路径
     * 
     * @param localIp 本地 IP 地址
     * @param localPort 本地端口
     * @return 进程完整路径，如果找不到则返回空字符串
     */
    public String getProcessPath(String localIp, int localPort) {
        if (localIp == null || localPort <= 0) {
            return "";
        }
        
        String normalizedIp = normalizeAddress(localIp);
        String key = normalizedIp + ":" + localPort;
        Integer pid = connectionPidMap.get(key);
        
        if (pid == null && !normalizedIp.equals("0.0.0.0")) {
            pid = connectionPidMap.get("0.0.0.0:" + localPort);
        }
        
        if (pid == null) {
            return "";
        }
        
        ProcessInfo processInfo = pidProcessMap.get(pid);
        if (processInfo == null) {
            updateProcessInfo(pid);
            processInfo = pidProcessMap.get(pid);
        }
        
        return processInfo != null ? processInfo.getProcessPath() : "";
    }
    
    /**
     * 获取映射表统计信息（用于调试）
     * 
     * @return 统计信息字符串
     */
    public String getStatistics() {
        return String.format(
            "连接映射: %d 个, 进程映射: %d 个",
            connectionPidMap.size(),
            pidProcessMap.size()
        );
    }
    
    /**
     * 清空所有映射表
     */
    public void clear() {
        connectionPidMap.clear();
        pidProcessMap.clear();
    }
    
    /**
     * 进程信息数据类
     */
    private static class ProcessInfo {
        private final String processName;
        private final String processPath;
        
        public ProcessInfo(String processName, String processPath) {
            this.processName = processName != null ? processName : "未知进程";
            this.processPath = processPath != null ? processPath : "";
        }
        
        public String getProcessName() {
            return processName;
        }
        
        public String getProcessPath() {
            return processPath;
        }
    }
}

