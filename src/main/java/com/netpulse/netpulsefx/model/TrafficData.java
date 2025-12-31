package com.netpulse.netpulsefx.model;

import java.time.LocalDateTime;

/**
 * 流量数据传输对象
 * 
 * <p>用于封装流量监控数据，传递给 AI 分析服务进行分析。</p>
 * 
 * @author NetPulseFX Team
 */
public class TrafficData {
    
    /** 网络接口名称 */
    private final String interfaceName;
    
    /** 下行速度（KB/s） */
    private final double downSpeed;
    
    /** 上行速度（KB/s） */
    private final double upSpeed;
    
    /** 捕获时间 */
    private final LocalDateTime captureTime;
    
    /** 数据包大小（字节） */
    private final long packetSize;
    
    /** 协议类型（如 TCP、UDP、ICMP 等，可选） */
    private final String protocol;
    
    /**
     * 构造函数
     * 
     * @param interfaceName 网络接口名称
     * @param downSpeed 下行速度（KB/s）
     * @param upSpeed 上行速度（KB/s）
     * @param captureTime 捕获时间
     * @param packetSize 数据包大小（字节）
     * @param protocol 协议类型
     */
    public TrafficData(String interfaceName, double downSpeed, double upSpeed, 
                      LocalDateTime captureTime, long packetSize, String protocol) {
        this.interfaceName = interfaceName;
        this.downSpeed = downSpeed;
        this.upSpeed = upSpeed;
        this.captureTime = captureTime;
        this.packetSize = packetSize;
        this.protocol = protocol != null ? protocol : "UNKNOWN";
    }
    
    /**
     * 简化构造函数（不包含协议信息）
     */
    public TrafficData(String interfaceName, double downSpeed, double upSpeed, 
                      LocalDateTime captureTime, long packetSize) {
        this(interfaceName, downSpeed, upSpeed, captureTime, packetSize, "UNKNOWN");
    }
    
    // Getter 方法
    public String getInterfaceName() {
        return interfaceName;
    }
    
    public double getDownSpeed() {
        return downSpeed;
    }
    
    public double getUpSpeed() {
        return upSpeed;
    }
    
    public LocalDateTime getCaptureTime() {
        return captureTime;
    }
    
    public long getPacketSize() {
        return packetSize;
    }
    
    public String getProtocol() {
        return protocol;
    }
    
    /**
     * 转换为 JSON 格式字符串
     * 
     * @return JSON 字符串
     */
    public String toJson() {
        return String.format(
            """
            {
              "interfaceName": "%s",
              "downSpeed": %.2f,
              "upSpeed": %.2f,
              "captureTime": "%s",
              "packetSize": %d,
              "protocol": "%s"
            }""",
            interfaceName, downSpeed, upSpeed, captureTime, packetSize, protocol
        );
    }
    
    /**
     * 转换为文本格式
     * 
     * @return 文本描述
     */
    public String toText() {
        return String.format(
            "接口: %s | 下行: %.2f KB/s | 上行: %.2f KB/s | 时间: %s | 协议: %s | 包大小: %d 字节",
            interfaceName, downSpeed, upSpeed, captureTime, protocol, packetSize
        );
    }
    
    @Override
    public String toString() {
        return toText();
    }
}




