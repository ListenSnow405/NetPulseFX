package com.netpulse.netpulsefx.service;

import com.netpulse.netpulsefx.exception.NetworkInterfaceException;
import org.pcap4j.core.PcapAddress;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.Pcaps;
import org.pcap4j.util.LinkLayerAddress;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * 网卡探测服务类
 * 负责获取和管理本机的网络接口信息
 * 
 * 功能包括：
 * - 获取所有可用的网络接口
 * - 获取指定网络接口的详细信息（名称、描述、IP地址、MAC地址等）
 * - 异常处理和错误提示
 */
public class NetworkInterfaceService {
    
    /**
     * 获取本机所有可用的网络接口列表
     * 
     * @return 网络接口列表，如果未找到任何接口则返回空列表
     * @throws NetworkInterfaceException 当无法获取网卡列表时抛出
     *                                   常见原因：未安装 Npcap（Windows）或权限不足
     */
    public List<PcapNetworkInterface> getAllInterfaces() throws NetworkInterfaceException {
        try {
            // 使用 Pcap4j 获取所有网络接口
            List<PcapNetworkInterface> interfaces = Pcaps.findAllDevs();
            
            // 检查是否找到任何网卡
            if (interfaces == null || interfaces.isEmpty()) {
                throw new NetworkInterfaceException(
                    "未找到任何可用的网络接口。\n" +
                    "可能的原因：\n" +
                    "1. Windows 系统：请确保已安装 Npcap（https://nmap.org/npcap/）\n" +
                    "2. Linux/Mac 系统：请确保有足够的权限运行程序（可能需要 sudo）\n" +
                    "3. 系统中确实没有任何网络接口"
                );
            }
            
            return new ArrayList<>(interfaces);
            
        } catch (PcapNativeException e) {
            // 处理 Pcap4j 原生库异常（通常是权限问题或库未安装）
            String errorMessage = "获取网络接口列表时发生原生库错误：\n" + e.getMessage() + "\n\n" +
                    "可能的原因：\n" +
                    "1. Windows 系统：请确保已安装 Npcap（https://nmap.org/npcap/）\n" +
                    "2. Linux/Mac 系统：请确保有足够的权限运行程序（可能需要 sudo）\n" +
                    "3. 系统架构不匹配（32位/64位）";
            throw new NetworkInterfaceException(errorMessage, e);
        } catch (Exception e) {
            // 处理 Pcap4j 可能抛出的其他异常
            if (e instanceof NetworkInterfaceException) {
                throw e;
            }
            
            // 处理其他异常（如权限问题、库加载失败等）
            String errorMessage = "获取网络接口列表时发生错误：\n" + e.getMessage();
            
            // 检查是否是常见的权限或库问题
            if (e.getMessage() != null && e.getMessage().contains("native")) {
                errorMessage += "\n\n提示：可能是 Pcap4j 原生库加载失败，请检查：\n" +
                               "1. 是否正确安装了 Npcap（Windows）或 libpcap（Linux/Mac）\n" +
                               "2. 系统架构是否匹配（32位/64位）";
            }
            
            throw new NetworkInterfaceException(errorMessage, e);
        }
    }
    
    /**
     * 获取指定网络接口的详细信息
     * 
     * @param nif 网络接口对象
     * @return 包含网卡详细信息的字符串，格式为：
     *         名称: xxx
     *         描述: xxx
     *         IP地址: xxx
     *         MAC地址: xxx
     */
    public String getInterfaceDetails(PcapNetworkInterface nif) {
        if (nif == null) {
            return "网络接口对象为空";
        }
        
        StringBuilder details = new StringBuilder();
        
        // 获取网卡名称
        String name = nif.getName();
        details.append("名称: ").append(name != null ? name : "未知").append("\n");
        
        // 获取网卡描述
        String description = nif.getDescription();
        details.append("描述: ").append(description != null ? description : "无描述").append("\n");
        
        // 获取 IP 地址列表
        List<PcapAddress> addresses = nif.getAddresses();
        if (addresses != null && !addresses.isEmpty()) {
            details.append("IP地址: ");
            boolean first = true;
            for (PcapAddress addr : addresses) {
                InetAddress inetAddr = addr.getAddress();
                if (inetAddr != null) {
                    if (!first) {
                        details.append(", ");
                    }
                    details.append(inetAddr.getHostAddress());
                    first = false;
                }
            }
            if (first) {
                details.append("无");
            }
            details.append("\n");
        } else {
            details.append("IP地址: 无\n");
        }
        
        // 获取 MAC 地址（链路层地址）
        // 注意：Pcap4j 获取 MAC 地址的方式可能因平台而异
        try {
            List<LinkLayerAddress> linkLayerAddresses = nif.getLinkLayerAddresses();
            if (linkLayerAddresses != null && !linkLayerAddresses.isEmpty()) {
                details.append("MAC地址: ");
                boolean first = true;
                for (LinkLayerAddress addr : linkLayerAddresses) {
                    if (!first) {
                        details.append(", ");
                    }
                    byte[] addrBytes = addr.getAddress();
                    if (addrBytes != null && addrBytes.length > 0) {
                        for (int i = 0; i < addrBytes.length; i++) {
                            if (i > 0) {
                                details.append(":");
                            }
                            // 将字节转换为十六进制字符串，确保两位数字
                            details.append(String.format("%02X", addrBytes[i] & 0xFF));
                        }
                        first = false;
                    }
                }
                if (first) {
                    details.append("无法获取");
                }
                details.append("\n");
            } else {
                details.append("MAC地址: 无法获取（可能需要打开网卡句柄后才能获取）\n");
            }
        } catch (Exception e) {
            details.append("MAC地址: 获取失败 (").append(e.getMessage()).append(")\n");
        }
        
        return details.toString();
    }
    
    /**
     * 获取指定网络接口的详细信息（返回结构化对象）
     * 此方法返回的数据结构便于后续转换为 JavaFX ObservableList
     * 
     * @param nif 网络接口对象
     * @return 包含网卡详细信息的 NetworkInterfaceInfo 对象
     */
    public NetworkInterfaceInfo getInterfaceInfo(PcapNetworkInterface nif) {
        if (nif == null) {
            return new NetworkInterfaceInfo("未知", "无", "无", "无");
        }
        
        // 获取名称
        String name = nif.getName() != null ? nif.getName() : "未知";
        
        // 获取描述
        String description = nif.getDescription() != null ? nif.getDescription() : "无描述";
        
        // 获取 IP 地址
        StringBuilder ipAddresses = new StringBuilder();
        List<PcapAddress> addresses = nif.getAddresses();
        if (addresses != null && !addresses.isEmpty()) {
            boolean first = true;
            for (PcapAddress addr : addresses) {
                InetAddress inetAddr = addr.getAddress();
                if (inetAddr != null) {
                    if (!first) {
                        ipAddresses.append(", ");
                    }
                    ipAddresses.append(inetAddr.getHostAddress());
                    first = false;
                }
            }
            if (first) {
                ipAddresses.append("无");
            }
        } else {
            ipAddresses.append("无");
        }
        
        // 获取 MAC 地址
        StringBuilder macAddress = new StringBuilder();
        try {
            List<LinkLayerAddress> linkLayerAddresses = nif.getLinkLayerAddresses();
            if (linkLayerAddresses != null && !linkLayerAddresses.isEmpty()) {
                boolean first = true;
                for (LinkLayerAddress addr : linkLayerAddresses) {
                    if (!first) {
                        macAddress.append(", ");
                    }
                    byte[] addrBytes = addr.getAddress();
                    if (addrBytes != null && addrBytes.length > 0) {
                        for (int i = 0; i < addrBytes.length; i++) {
                            if (i > 0) {
                                macAddress.append(":");
                            }
                            macAddress.append(String.format("%02X", addrBytes[i] & 0xFF));
                        }
                        first = false;
                    }
                }
                if (first) {
                    macAddress.append("无法获取");
                }
            } else {
                macAddress.append("无法获取");
            }
        } catch (Exception e) {
            macAddress.append("获取失败");
        }
        
        return new NetworkInterfaceInfo(name, description, ipAddresses.toString(), macAddress.toString());
    }
    
    /**
     * 网络接口信息数据类
     * 用于存储网卡的详细信息，便于 JavaFX 绑定和显示
     */
    public static class NetworkInterfaceInfo {
        private final String name;
        private final String description;
        private final String ipAddress;
        private final String macAddress;
        
        public NetworkInterfaceInfo(String name, String description, String ipAddress, String macAddress) {
            this.name = name;
            this.description = description;
            this.ipAddress = ipAddress;
            this.macAddress = macAddress;
        }
        
        public String getName() {
            return name;
        }
        
        public String getDescription() {
            return description;
        }
        
        public String getIpAddress() {
            return ipAddress;
        }
        
        public String getMacAddress() {
            return macAddress;
        }
        
        @Override
        public String toString() {
            return String.format("名称: %s, IP: %s, MAC: %s", name, ipAddress, macAddress);
        }
    }
}

