package com.netpulse.netpulsefx;

import com.netpulse.netpulsefx.exception.NetworkInterfaceException;
import com.netpulse.netpulsefx.service.NetworkInterfaceService;
import org.pcap4j.core.PcapNetworkInterface;

import java.util.List;

/**
 * 网卡探测模块测试类
 * 用于在控制台测试网卡探测功能
 */
public class NetworkInterfaceTest {
    
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("NetPulse FX - 网卡探测模块测试");
        System.out.println("========================================\n");
        
        NetworkInterfaceService service = new NetworkInterfaceService();
        
        try {
            // 获取所有网络接口
            System.out.println("正在扫描本机网络接口...\n");
            List<PcapNetworkInterface> interfaces = service.getAllInterfaces();
            
            System.out.println("找到 " + interfaces.size() + " 个网络接口：\n");
            
            // 遍历并打印每个网卡的信息
            for (int i = 0; i < interfaces.size(); i++) {
                PcapNetworkInterface nif = interfaces.get(i);
                System.out.println("--- 网卡 #" + (i + 1) + " ---");
                
                // 使用详细信息方法
                String details = service.getInterfaceDetails(nif);
                System.out.println(details);
                
                // 也可以使用结构化信息方法
                NetworkInterfaceService.NetworkInterfaceInfo info = service.getInterfaceInfo(nif);
                System.out.println("（结构化信息: " + info.toString() + "）");
                System.out.println();
            }
            
            System.out.println("========================================");
            System.out.println("测试完成！");
            System.out.println("========================================");
            
        } catch (NetworkInterfaceException e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("发生未预期的错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}


