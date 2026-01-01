package com.netpulse.netpulsefx.task;

import com.netpulse.netpulsefx.service.ProcessContextService;
import javafx.concurrent.Task;
import org.pcap4j.core.*;
import org.pcap4j.core.BpfProgram.BpfCompileMode;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.IpPacket;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.IpV6Packet;
import org.pcap4j.packet.TcpPacket;
import org.pcap4j.packet.UdpPacket;
import org.pcap4j.packet.namednumber.IpNumber;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;

/**
 * 流量监控任务类
 * 继承自 JavaFX 的 Task，在后台线程中执行数据包捕获
 * 
 * 多线程通信机制：
 * 1. 抓包线程（Task 的 call() 方法）：在后台线程中持续捕获数据包
 * 2. 原子变量（AtomicLong bytesCaptured）：用于线程安全地累加字节数
 * 3. 定时读取线程（Controller 中的 ScheduledExecutorService）：每秒读取并清零计数器
 * 4. UI 更新线程（Platform.runLater）：将数据更新到 JavaFX 图表
 * 
 * 数据流向：
 * 数据包捕获 -> 累加到 AtomicLong -> 定时读取 -> 转换为 KB/s -> 更新图表
 */
public class TrafficMonitorTask extends Task<Void> {
    
    /** 要监控的网络接口 */
    private final PcapNetworkInterface networkInterface;
    
    /** BPF 过滤表达式（可选，如果为 null 则不使用过滤器） */
    private final String bpfFilterExpression;
    
    /** 本地 IP 地址（用于区分上行和下行流量） */
    private final String localIpAddress;
    
    /** 
     * 原子变量：用于线程安全地累加捕获的上行字节数
     * 使用 AtomicLong 确保多线程环境下的数据一致性
     * - 抓包线程：持续累加字节数
     * - 读取线程：读取并清零（使用 getAndSet(0) 原子操作）
     */
    private final AtomicLong bytesUploaded;
    
    /** 
     * 原子变量：用于线程安全地累加捕获的下行字节数
     * 使用 AtomicLong 确保多线程环境下的数据一致性
     * - 抓包线程：持续累加字节数
     * - 读取线程：读取并清零（使用 getAndSet(0) 原子操作）
     */
    private final AtomicLong bytesDownloaded;
    
    /** 
     * IP 地址统计：用于存储最近捕获的数据包的IP地址信息
     * Key: IP地址字符串, Value: 出现次数
     * 使用 ConcurrentHashMap 确保线程安全
     */
    private final Map<String, Integer> ipAddressCounts;
    
    /** 进程流量统计：用于存储每个进程的累计流量（区分上下行）
     * Key: 进程名称, Value: ProcessTrafficStats（包含上行和下行字节数）
     * 使用 ConcurrentHashMap 确保线程安全
     */
    private final Map<String, ProcessTrafficStats> processTrafficBytes;
    
    /** 最近捕获的源IP地址（用于传递给数据库） */
    private volatile String lastSourceIp;
    
    /** 最近捕获的目标IP地址（用于传递给数据库） */
    private volatile String lastDestIp;
    
    /** 最近捕获的本地端口（用于查找进程） */
    private volatile Integer lastLocalPort;
    
    /** 最近捕获的进程名称 */
    private volatile String lastProcessName;
    
    /** 最近捕获的协议类型（TCP, UDP, ICMP, 其他） */
    private volatile String lastProtocol;
    
    /** 协议统计：用于统计每秒内各种协议的数量 */
    private final Map<String, Integer> protocolCounts;
    
    /** 进程上下文服务 */
    private final ProcessContextService processContextService;
    
    /** Pcap 句柄：用于数据包捕获 */
    private PcapHandle pcapHandle;
    
    /** 是否正在运行（用于内部状态跟踪） */
    private volatile boolean monitoringActive;
    
    /**
     * 构造函数
     * 
     * @param networkInterface 要监控的网络接口
     */
    public TrafficMonitorTask(PcapNetworkInterface networkInterface) {
        this(networkInterface, null, null);
    }
    
    /**
     * 构造函数（带 BPF 过滤器）
     * 
     * @param networkInterface 要监控的网络接口
     * @param bpfFilterExpression BPF 过滤表达式（可选，如果为 null 则不使用过滤器）
     */
    public TrafficMonitorTask(PcapNetworkInterface networkInterface, String bpfFilterExpression) {
        this(networkInterface, bpfFilterExpression, null);
    }
    
    /**
     * 构造函数（带 BPF 过滤器和本地 IP 地址）
     * 
     * @param networkInterface 要监控的网络接口
     * @param bpfFilterExpression BPF 过滤表达式（可选，如果为 null 则不使用过滤器）
     * @param localIpAddress 本地 IP 地址（用于区分上行和下行流量）
     */
    public TrafficMonitorTask(PcapNetworkInterface networkInterface, String bpfFilterExpression, String localIpAddress) {
        this.networkInterface = networkInterface;
        this.bpfFilterExpression = (bpfFilterExpression != null && !bpfFilterExpression.trim().isEmpty()) 
                ? bpfFilterExpression.trim() : null;
        this.localIpAddress = localIpAddress;
        this.bytesUploaded = new AtomicLong(0);
        this.bytesDownloaded = new AtomicLong(0);
        this.ipAddressCounts = new ConcurrentHashMap<>();
        this.processTrafficBytes = new ConcurrentHashMap<>();
        this.lastSourceIp = null;
        this.lastDestIp = null;
        this.lastLocalPort = null;
        this.lastProcessName = null;
        this.lastProtocol = null;
        this.protocolCounts = new ConcurrentHashMap<>();
        this.processContextService = ProcessContextService.getInstance();
        this.monitoringActive = false;
    }
    
    /**
     * Task 的核心方法，在后台线程中执行
     * 启动 Pcap4j 的抓包循环，持续捕获数据包
     * 
     * @return null（因为这是一个无返回值的任务）
     * @throws Exception 如果抓包过程中发生错误
     */
    @Override
    protected Void call() throws Exception {
        monitoringActive = true;
        
        try {
            // 打开网络接口，准备捕获数据包
            // 参数说明：
            // - 65536：快照长度（snapshot length），捕获每个数据包的最大字节数
            // - PcapNetworkInterface.PromiscuousMode.PROMISCUOUS：混杂模式，捕获所有经过网卡的数据包
            // - 10：超时时间（毫秒）
            pcapHandle = networkInterface.openLive(
                    65536,
                    PcapNetworkInterface.PromiscuousMode.PROMISCUOUS,
                    10
            );
            
            // 如果指定了 BPF 过滤表达式，则设置过滤器
            // 注意：此操作在内核空间完成过滤，可大幅降低 JVM 的 GC 压力
            // 因为不符合过滤条件的数据包在内核层就被丢弃，不会传递给 Java 应用
            if (bpfFilterExpression != null) {
                try {
                    pcapHandle.setFilter(bpfFilterExpression, BpfCompileMode.OPTIMIZE);
                    updateMessage("正在监控网卡：" + networkInterface.getDescription() + 
                            " (BPF过滤: " + bpfFilterExpression + ")");
                } catch (Exception e) {
                    // 如果设置过滤器失败，抛出异常（这种情况应该已经在 UI 校验阶段被发现）
                    throw new Exception("设置 BPF 过滤器失败：\n" + e.getMessage(), e);
                }
            } else {
                updateMessage("正在监控网卡：" + networkInterface.getDescription());
            }
            
            // 创建数据包监听器，处理捕获到的每个数据包
            PacketListener listener = new PacketListener() {
                @Override
                public void gotPacket(Packet packet) {
                    // 检查任务是否被取消
                    if (isCancelled() || !monitoringActive) {
                        return;
                    }
                    
                    // 获取数据包的长度（字节数）
                    int packetLength = packet.length();
                    
                    // 识别协议类型并统计
                    String protocol = identifyProtocol(packet);
                    if (protocol != null) {
                        // 统计协议数量
                        protocolCounts.merge(protocol, 1, Integer::sum);
                    }
                    
                    // 尝试提取IP地址信息并区分上下行流量
                    extractIpAddresses(packet);
                    
                    // 根据数据包方向累加到对应的计数器
                    classifyAndCountTraffic(packet, packetLength);
                }
            };
            
            // 启动抓包循环
            // loop() 方法会阻塞，直到捕获到指定数量的数据包或发生错误
            // 使用 Integer.MAX_VALUE 表示持续捕获，直到任务被取消
            pcapHandle.loop(Integer.MAX_VALUE, listener);
            
        } catch (PcapNativeException e) {
            // 处理原生库异常（通常是权限问题）
            throw new Exception("无法打开网卡进行监控：\n" + e.getMessage(), e);
        } catch (InterruptedException e) {
            // 任务被中断（用户停止监控）
            // 这是正常情况，不需要抛出异常
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // 处理其他异常
            throw new Exception("监控过程中发生错误：\n" + e.getMessage(), e);
        } finally {
            // 确保资源被正确释放
            cleanup();
        }
        
        return null;
    }
    
    /**
     * 根据数据包的方向分类并累加流量
     * 如果源 IP 等于本地 IP，则为上行流量（上传）
     * 如果目标 IP 等于本地 IP，则为下行流量（下载）
     * 
     * @param packet 数据包对象
     * @param packetLength 数据包长度（字节）
     */
    private void classifyAndCountTraffic(Packet packet, int packetLength) {
        // 如果没有设置本地 IP，无法区分方向，跳过
        if (localIpAddress == null || localIpAddress.isEmpty()) {
            // 如果没有本地 IP，将流量平均分配到上下行（向后兼容）
            bytesUploaded.addAndGet(packetLength / 2);
            bytesDownloaded.addAndGet(packetLength / 2);
            return;
        }
        
        try {
            // 尝试获取 IP 数据包（可能是 IPv4 或 IPv6）
            IpPacket ipPacket = packet.get(IpPacket.class);
            
            if (ipPacket != null) {
                // 获取源 IP 和目标 IP
                String sourceIp = ipPacket.getHeader().getSrcAddr().getHostAddress();
                String destIp = ipPacket.getHeader().getDstAddr().getHostAddress();
                
                // 判断方向并累加
                if (localIpAddress.equals(sourceIp)) {
                    // 源 IP 是本地 IP，这是上行流量（上传）
                    bytesUploaded.addAndGet(packetLength);
                } else if (localIpAddress.equals(destIp)) {
                    // 目标 IP 是本地 IP，这是下行流量（下载）
                    bytesDownloaded.addAndGet(packetLength);
                } else {
                    // 如果源 IP 和目标 IP 都不是本地 IP，可能是路由包或其他情况
                    // 这种情况下，我们无法确定方向，可以选择忽略或平均分配
                    // 这里选择忽略，因为这种情况比较少见
                }
            } else {
                // 不是 IP 数据包（可能是 ARP、ICMP 等），无法判断方向
                // 将流量平均分配到上下行
                bytesUploaded.addAndGet(packetLength / 2);
                bytesDownloaded.addAndGet(packetLength / 2);
            }
        } catch (Exception e) {
            // 解析失败，将流量平均分配到上下行
            bytesUploaded.addAndGet(packetLength / 2);
            bytesDownloaded.addAndGet(packetLength / 2);
        }
    }
    
    /**
     * 获取当前累计的上行和下行字节数并清零
     * 这是一个原子操作，确保线程安全
     * 
     * @return 包含上行和下行字节数的数组，[0] 为上行字节数，[1] 为下行字节数
     */
    public long[] getAndResetBytes() {
        // getAndSet(0) 是原子操作：
        // 1. 返回当前值
        // 2. 将值设置为 0
        // 这确保了读取和清零操作的原子性，避免数据丢失
        long uploaded = bytesUploaded.getAndSet(0);
        long downloaded = bytesDownloaded.getAndSet(0);
        return new long[]{uploaded, downloaded};
    }
    
    /**
     * 停止监控
     * 安全地关闭抓包句柄，释放资源
     */
    public void stop() {
        monitoringActive = false;
        
        // 如果 pcapHandle 存在，关闭它
        // 这会中断 loop() 方法，使其抛出 InterruptedException
        if (pcapHandle != null && pcapHandle.isOpen()) {
            try {
                pcapHandle.breakLoop(); // 中断抓包循环
                pcapHandle.close();     // 关闭句柄
            } catch (Exception e) {
                // 记录错误但不抛出异常
                System.err.println("关闭抓包句柄时发生错误：" + e.getMessage());
            }
        }
    }
    
    /**
     * 清理资源
     * 在任务结束时调用，确保所有资源都被正确释放
     */
    private void cleanup() {
        monitoringActive = false;
        
        if (pcapHandle != null && pcapHandle.isOpen()) {
            try {
                pcapHandle.close();
            } catch (Exception e) {
                System.err.println("清理资源时发生错误：" + e.getMessage());
            }
        }
    }
    
    /**
     * 检查监控是否处于活动状态
     * 
     * @return true 如果监控处于活动状态
     */
    public boolean isMonitoringActive() {
        return monitoringActive;
    }
    
    /**
     * 识别数据包的协议类型
     * 
     * <p>使用 Pcap4j 的 contains 方法识别 TCP、UDP 协议，通过 IP 协议类型识别 ICMP。</p>
     * 
     * @param packet 数据包对象
     * @return 协议类型（TCP, UDP, ICMP, 其他），如果未识别则返回 null
     */
    private String identifyProtocol(Packet packet) {
        try {
            // 尝试获取 IP 数据包
            IpPacket ipPacket = packet.get(IpPacket.class);
            if (ipPacket == null) {
                // 不是 IP 数据包，标记为"其他"
                lastProtocol = "其他";
                return "其他";
            }
            
            // 检查是否为 TCP 数据包
            if (packet.contains(TcpPacket.class)) {
                lastProtocol = "TCP";
                return "TCP";
            }
            
            // 检查是否为 UDP 数据包
            if (packet.contains(UdpPacket.class)) {
                lastProtocol = "UDP";
                return "UDP";
            }
            
            // 检查是否为 ICMP 数据包（通过 IP 协议类型判断）
            // IPv4: ICMP 协议号为 1
            // IPv6: ICMPv6 下一报头类型为 58
            IpV4Packet ipV4Packet = packet.get(IpV4Packet.class);
            if (ipV4Packet != null) {
                // IPv4 数据包
                IpNumber protocol = ipV4Packet.getHeader().getProtocol();
                if (protocol == IpNumber.ICMPV4) {
                    lastProtocol = "ICMP";
                    return "ICMP";
                }
            } else {
                // 尝试 IPv6
                IpV6Packet ipV6Packet = packet.get(IpV6Packet.class);
                if (ipV6Packet != null) {
                    // IPv6 数据包
                    IpNumber nextHeader = ipV6Packet.getHeader().getNextHeader();
                    if (nextHeader == IpNumber.ICMPV6) {
                        lastProtocol = "ICMP";
                        return "ICMP";
                    }
                }
            }
            
            // 其他协议类型
            lastProtocol = "其他";
            return "其他";
        } catch (Exception e) {
            // 协议识别失败，标记为"其他"
            lastProtocol = "其他";
            return "其他";
        }
    }
    
    /**
     * 从数据包中提取IP地址信息
     * 
     * <p>支持 IPv4 和 IPv6 数据包。</p>
     * 
     * @param packet 数据包对象
     */
    private void extractIpAddresses(Packet packet) {
        try {
            // 尝试获取 IP 数据包（可能是 IPv4 或 IPv6）
            IpPacket ipPacket = packet.get(IpPacket.class);
            
            if (ipPacket != null) {
                // 获取源IP地址
                String sourceIp = ipPacket.getHeader().getSrcAddr().getHostAddress();
                // 获取目标IP地址
                String destIp = ipPacket.getHeader().getDstAddr().getHostAddress();
                
                // 只保存公网IP地址（过滤掉本地/私有IP）
                // 优先保存公网IP，如果都是私有IP则保存第一个
                if (isPublicIP(sourceIp)) {
                    lastSourceIp = sourceIp;
                } else if (lastSourceIp == null) {
                    lastSourceIp = sourceIp; // 如果没有公网IP，至少保存一个
                }
                
                if (isPublicIP(destIp)) {
                    lastDestIp = destIp;
                } else if (lastDestIp == null) {
                    lastDestIp = destIp; // 如果没有公网IP，至少保存一个
                }
                
                // 统计IP地址出现次数（可选，用于分析）
                ipAddressCounts.merge(sourceIp, 1, Integer::sum);
                ipAddressCounts.merge(destIp, 1, Integer::sum);
                
                // 提取端口信息并查找进程，同时统计进程流量
                extractPortAndProcess(ipPacket, sourceIp, packet.length());
            }
        } catch (Exception e) {
            // 如果数据包不是IP数据包或解析失败，忽略错误
            // 这很常见，因为可能捕获到ARP、ICMP等其他类型的包
            // 不记录错误，避免日志过多
        }
    }
    
    /**
     * 提取端口信息并查找对应进程，同时根据数据包方向统计进程流量
     * 
     * @param ipPacket IP 数据包
     * @param sourceIp 源IP地址
     * @param packetSize 数据包大小（字节）
     */
    private void extractPortAndProcess(IpPacket ipPacket, String sourceIp, int packetSize) {
        try {
            String processName = null;
            int localPort = 0;
            
            // 获取目标 IP 地址，用于判断数据包方向
            String destIp = ipPacket.getHeader().getDstAddr().getHostAddress();
            
            // 判断数据包方向
            boolean isUpload = false;  // 是否为上行流量
            boolean isDownload = false; // 是否为下行流量
            
            if (localIpAddress != null && !localIpAddress.isEmpty()) {
                if (localIpAddress.equals(sourceIp)) {
                    // 源 IP 是本地 IP，这是上行流量（上传）
                    isUpload = true;
                } else if (localIpAddress.equals(destIp)) {
                    // 目标 IP 是本地 IP，这是下行流量（下载）
                    isDownload = true;
                }
            }
            
            // 尝试获取 TCP 数据包
            TcpPacket tcpPacket = ipPacket.get(TcpPacket.class);
            if (tcpPacket != null) {
                int srcPort = tcpPacket.getHeader().getSrcPort().valueAsInt();
                // 判断是否为本地端口（源IP是本地地址）
                if (!isPublicIP(sourceIp) || sourceIp.startsWith("192.168.") || 
                    sourceIp.startsWith("10.") || sourceIp.startsWith("172.")) {
                    localPort = srcPort;
                    // 查找进程
                    processName = processContextService.findProcessByPacket(sourceIp, srcPort);
                } else {
                    // 如果源 IP 不是本地，尝试通过目标 IP 和目标端口查找进程
                    int dstPort = tcpPacket.getHeader().getDstPort().valueAsInt();
                    if (localIpAddress != null && localIpAddress.equals(destIp)) {
                        localPort = dstPort;
                        processName = processContextService.findProcessByPacket(destIp, dstPort);
                    }
                }
            } else {
                // 尝试获取 UDP 数据包
                UdpPacket udpPacket = ipPacket.get(UdpPacket.class);
                if (udpPacket != null) {
                    int srcPort = udpPacket.getHeader().getSrcPort().valueAsInt();
                    // 判断是否为本地端口
                    if (!isPublicIP(sourceIp) || sourceIp.startsWith("192.168.") || 
                        sourceIp.startsWith("10.") || sourceIp.startsWith("172.")) {
                        localPort = srcPort;
                        // 查找进程
                        processName = processContextService.findProcessByPacket(sourceIp, srcPort);
                    } else {
                        // 如果源 IP 不是本地，尝试通过目标 IP 和目标端口查找进程
                        int dstPort = udpPacket.getHeader().getDstPort().valueAsInt();
                        if (localIpAddress != null && localIpAddress.equals(destIp)) {
                            localPort = dstPort;
                            processName = processContextService.findProcessByPacket(destIp, dstPort);
                        }
                    }
                }
            }
            
            // 更新最近捕获的进程信息
            if (processName != null && !processName.equals("未知进程")) {
                lastProcessName = processName;
                lastLocalPort = localPort;
                
                // 根据数据包方向累加进程流量统计
                ProcessTrafficStats stats = processTrafficBytes.computeIfAbsent(
                    processName, 
                    k -> new ProcessTrafficStats()
                );
                
                if (isUpload) {
                    stats.addUpload(packetSize);
                } else if (isDownload) {
                    stats.addDownload(packetSize);
                } else {
                    // 如果无法确定方向，平均分配
                    stats.addUpload(packetSize / 2);
                    stats.addDownload(packetSize / 2);
                }
            }
        } catch (Exception e) {
            // 端口提取失败，忽略错误
        }
    }
    
    /**
     * 检查是否为公网IP地址
     * 
     * @param ip IP地址
     * @return true 如果是公网IP
     */
    private boolean isPublicIP(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        
        // 回环地址
        if (ip.equals("127.0.0.1") || ip.startsWith("127.")) {
            return false;
        }
        
        // 本地链路地址
        if (ip.startsWith("169.254.")) {
            return false;
        }
        
        // 私有地址范围
        // 10.0.0.0 - 10.255.255.255
        if (ip.startsWith("10.")) {
            return false;
        }
        
        // 172.16.0.0 - 172.31.255.255
        if (ip.matches("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) {
            return false;
        }
        
        // 192.168.0.0 - 192.168.255.255
        if (ip.startsWith("192.168.")) {
            return false;
        }
        
        // IPv6 本地地址
        if (ip.startsWith("::1") || ip.startsWith("fe80:")) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 获取最近捕获的源IP地址
     * 
     * @return 源IP地址，如果未捕获到IP数据包则返回null
     */
    public String getLastSourceIp() {
        return lastSourceIp;
    }
    
    /**
     * 获取最近捕获的目标IP地址
     * 
     * @return 目标IP地址，如果未捕获到IP数据包则返回null
     */
    public String getLastDestIp() {
        return lastDestIp;
    }
    
    /**
     * 获取并清空最近捕获的IP地址（原子操作）
     * 
     * <p>用于在读取IP地址后清空，避免重复使用旧数据。</p>
     * 
     * @return 包含源IP和目标IP的数组，[0]为源IP，[1]为目标IP
     */
    public String[] getAndClearLastIps() {
        String[] result = new String[]{lastSourceIp, lastDestIp};
        lastSourceIp = null;
        lastDestIp = null;
        return result;
    }
    
    /**
     * 获取并清空最近捕获的进程信息（原子操作）
     * 
     * @return 进程名称，如果未找到则返回"未知进程"
     */
    public String getAndClearLastProcessName() {
        String result = lastProcessName != null ? lastProcessName : "未知进程";
        lastProcessName = null;
        lastLocalPort = null;
        return result;
    }
    
    /**
     * 获取并清空最近捕获的协议类型（原子操作）
     * 
     * <p>返回每秒内出现次数最多的协议类型。如果所有协议数量相同，则按优先级返回：TCP > UDP > ICMP > 其他</p>
     * 
     * @return 协议类型（TCP, UDP, ICMP, 其他），如果未识别则返回 "其他"
     */
    public String getAndClearLastProtocol() {
        // 获取每秒内出现次数最多的协议
        String mostCommonProtocol = getMostCommonProtocol();
        
        // 调试输出
        if (mostCommonProtocol == null) {
            System.out.println("[TrafficMonitorTask] 警告：协议类型为 null，使用默认值'其他'");
            mostCommonProtocol = "其他";
        } else {
            System.out.println("[TrafficMonitorTask] 获取协议类型: " + mostCommonProtocol + 
                ", 统计: " + protocolCounts);
        }
        
        // 清空统计
        protocolCounts.clear();
        lastProtocol = null;
        
        return mostCommonProtocol;
    }
    
    /**
     * 获取每秒内出现次数最多的协议类型
     * 
     * @return 协议类型（TCP, UDP, ICMP, 其他），如果未识别则返回 null
     */
    private String getMostCommonProtocol() {
        if (protocolCounts.isEmpty()) {
            // 如果没有统计数据，返回最后识别的协议
            if (lastProtocol == null) {
                System.out.println("[TrafficMonitorTask] 警告：协议统计为空且 lastProtocol 为 null");
            }
            return lastProtocol;
        }
        
        // 找到出现次数最多的协议
        String mostCommon = null;
        int maxCount = 0;
        
        // 协议优先级：TCP > UDP > ICMP > 其他
        String[] priorityOrder = {"TCP", "UDP", "ICMP", "其他"};
        
        for (String protocol : priorityOrder) {
            Integer count = protocolCounts.get(protocol);
            if (count != null && count > maxCount) {
                maxCount = count;
                mostCommon = protocol;
            }
        }
        
        // 如果找到了，返回；否则返回最后识别的协议
        String result = mostCommon != null ? mostCommon : lastProtocol;
        if (result == null) {
            System.out.println("[TrafficMonitorTask] 警告：无法确定协议类型，使用默认值'其他'");
            result = "其他";
        }
        return result;
    }
    
    /**
     * 获取最近捕获的协议类型（不清空）
     * 
     * @return 协议类型（TCP, UDP, ICMP, 其他），如果未识别则返回 null
     */
    public String getLastProtocol() {
        return lastProtocol;
    }
    
    /**
     * 获取IP地址统计信息（用于调试或分析）
     * 
     * @return IP地址出现次数的映射（只读副本）
     */
    public Map<String, Integer> getIpAddressCounts() {
        return new ConcurrentHashMap<>(ipAddressCounts);
    }
    
    /**
     * 清空IP地址统计
     */
    public void clearIpAddressCounts() {
        ipAddressCounts.clear();
    }
    
    /**
     * 获取并清空进程流量统计（原子操作）
     * 
     * <p>用于在读取进程流量后清空，避免重复使用旧数据。</p>
     * 
     * @return 进程流量映射：进程名 -> ProcessTrafficStats（包含上行和下行字节数）
     */
    public Map<String, ProcessTrafficStats> getAndClearProcessTraffic() {
        Map<String, ProcessTrafficStats> result = new ConcurrentHashMap<>();
        // 创建副本并清空原映射
        for (Map.Entry<String, ProcessTrafficStats> entry : processTrafficBytes.entrySet()) {
            result.put(entry.getKey(), new ProcessTrafficStats(entry.getValue()));
        }
        processTrafficBytes.clear();
        return result;
    }
    
    /**
     * 获取进程流量统计（只读）
     * 
     * @return 进程流量映射的副本
     */
    public Map<String, ProcessTrafficStats> getProcessTraffic() {
        Map<String, ProcessTrafficStats> result = new ConcurrentHashMap<>();
        for (Map.Entry<String, ProcessTrafficStats> entry : processTrafficBytes.entrySet()) {
            result.put(entry.getKey(), new ProcessTrafficStats(entry.getValue()));
        }
        return result;
    }
    
    /**
     * 进程流量统计数据结构
     * 用于存储每个进程的上行和下行流量
     */
    public static class ProcessTrafficStats {
        private long uploadBytes;
        private long downloadBytes;
        
        public ProcessTrafficStats() {
            this.uploadBytes = 0;
            this.downloadBytes = 0;
        }
        
        public ProcessTrafficStats(ProcessTrafficStats other) {
            this.uploadBytes = other.uploadBytes;
            this.downloadBytes = other.downloadBytes;
        }
        
        public void addUpload(long bytes) {
            this.uploadBytes += bytes;
        }
        
        public void addDownload(long bytes) {
            this.downloadBytes += bytes;
        }
        
        public long getUploadBytes() {
            return uploadBytes;
        }
        
        public long getDownloadBytes() {
            return downloadBytes;
        }
        
        public long getTotalBytes() {
            return uploadBytes + downloadBytes;
        }
    }
}

