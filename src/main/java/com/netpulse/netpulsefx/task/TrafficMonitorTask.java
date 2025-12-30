package com.netpulse.netpulsefx.task;

import javafx.concurrent.Task;
import org.pcap4j.core.*;
import org.pcap4j.packet.Packet;

import java.util.concurrent.atomic.AtomicLong;

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
    
    /** 
     * 原子变量：用于线程安全地累加捕获的字节数
     * 使用 AtomicLong 确保多线程环境下的数据一致性
     * - 抓包线程：持续累加字节数
     * - 读取线程：读取并清零（使用 getAndSet(0) 原子操作）
     */
    private final AtomicLong bytesCaptured;
    
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
        this.networkInterface = networkInterface;
        this.bytesCaptured = new AtomicLong(0);
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
            
            // 更新任务状态：开始捕获
            updateMessage("正在监控网卡：" + networkInterface.getDescription());
            
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
                    
                    // 使用原子操作累加字节数
                    // addAndGet() 是原子操作，确保线程安全
                    bytesCaptured.addAndGet(packetLength);
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
     * 获取当前累计的字节数并清零
     * 这是一个原子操作，确保线程安全
     * 
     * @return 累计的字节数（清零前）
     */
    public long getAndResetBytes() {
        // getAndSet(0) 是原子操作：
        // 1. 返回当前值
        // 2. 将值设置为 0
        // 这确保了读取和清零操作的原子性，避免数据丢失
        return bytesCaptured.getAndSet(0);
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
}

