package com.netpulse.netpulsefx.service;

import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.Pcaps;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 系统检查服务
 * 用于检查系统权限和驱动是否满足运行要求
 * 
 * @author NetPulseFX Team
 */
public class SystemCheckService {
    
    /**
     * 检查结果类
     */
    public static class CheckResult {
        private final boolean passed;
        private final String message;
        private final boolean isAdmin;
        private final boolean hasNpcap;
        
        public CheckResult(boolean passed, String message, boolean isAdmin, boolean hasNpcap) {
            this.passed = passed;
            this.message = message;
            this.isAdmin = isAdmin;
            this.hasNpcap = hasNpcap;
        }
        
        public boolean isPassed() {
            return passed;
        }
        
        public String getMessage() {
            return message;
        }
        
        public boolean isAdmin() {
            return isAdmin;
        }
        
        public boolean hasNpcap() {
            return hasNpcap;
        }
    }
    
    /**
     * 检查系统权限和驱动
     * 
     * @return 检查结果
     */
    public static CheckResult checkSystemRequirements() {
        boolean isAdmin = checkAdminPermission();
        boolean hasNpcap = checkNpcapDriver();
        
        if (!isAdmin && !hasNpcap) {
            return new CheckResult(false, 
                "系统检查失败：\n\n" +
                "1. 未检测到管理员权限\n" +
                "2. 未检测到 Npcap 驱动\n\n" +
                "请以管理员身份运行程序，并确保已安装 Npcap 驱动。\n" +
                "详细说明请查看帮助中心。",
                isAdmin, hasNpcap);
        } else if (!isAdmin) {
            return new CheckResult(false,
                "系统检查失败：\n\n" +
                "未检测到管理员权限。\n\n" +
                "请右键点击程序，选择\"以管理员身份运行\"。\n" +
                "详细说明请查看帮助中心。",
                isAdmin, hasNpcap);
        } else if (!hasNpcap) {
            return new CheckResult(false,
                "系统检查失败：\n\n" +
                "未检测到 Npcap 驱动。\n\n" +
                "请访问 https://npcap.com/ 下载并安装 Npcap 驱动。\n" +
                "安装时请勾选\"以兼容模式运行\"选项。\n" +
                "详细说明请查看帮助中心。",
                isAdmin, hasNpcap);
        }
        
        return new CheckResult(true, "系统检查通过", isAdmin, hasNpcap);
    }
    
    /**
     * 检查是否具有管理员权限
     * 
     * @return true 如果具有管理员权限，false 否则
     */
    private static boolean checkAdminPermission() {
        String os = System.getProperty("os.name", "").toLowerCase();
        
        if (os.contains("win")) {
            // Windows 系统：尝试获取系统属性或检查用户组
            try {
                // 方法1：检查是否能够访问系统目录
                File systemDir = new File("C:\\Windows\\System32\\config\\system");
                if (systemDir.exists()) {
                    // 尝试创建临时文件来测试权限
                    try {
                        File testFile = new File("C:\\Windows\\System32\\test_permission.tmp");
                        if (testFile.createNewFile()) {
                            testFile.delete();
                            return true;
                        }
                    } catch (Exception e) {
                        // 无法创建文件，可能没有管理员权限
                    }
                }
                
                // 方法2：检查环境变量
                String userProfile = System.getenv("USERPROFILE");
                if (userProfile != null && userProfile.contains("Administrator")) {
                    return true;
                }
                
                // 方法3：尝试使用 Pcap4j 检测（如果能够获取网卡列表，通常表示有权限）
                try {
                    java.util.List<?> interfaces = Pcaps.findAllDevs();
                    return interfaces != null && !interfaces.isEmpty();
                } catch (Exception e) {
                    // 如果抛出异常，可能是权限问题
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        } else {
            // Linux/macOS 系统：检查是否为 root 用户
            String userName = System.getProperty("user.name");
            return "root".equals(userName);
        }
    }
    
    /**
     * 检查是否安装了 Npcap 驱动
     * 
     * @return true 如果已安装 Npcap，false 否则
     */
    private static boolean checkNpcapDriver() {
        String os = System.getProperty("os.name", "").toLowerCase();
        
        if (os.contains("win")) {
            // Windows 系统：检查 Npcap 安装路径
            String[] npcapPaths = {
                "C:\\Program Files\\Npcap",
                "C:\\Program Files (x86)\\Npcap",
                System.getenv("ProgramFiles") + "\\Npcap",
                System.getenv("ProgramFiles(x86)") + "\\Npcap"
            };
            
            for (String path : npcapPaths) {
                if (path != null && Files.exists(Paths.get(path))) {
                    return true;
                }
            }
            
            // 尝试通过 Pcap4j 检测（如果能够初始化，通常表示驱动已安装）
            try {
                Pcaps.findAllDevs();
                return true;
            } catch (PcapNativeException e) {
                // 如果抛出原生异常，可能是驱动未安装
                return false;
            } catch (Exception e) {
                // 其他异常，保守返回 false
                return false;
            }
        } else {
            // Linux/macOS 系统：检查 libpcap
            try {
                // 尝试查找 libpcap 库文件
                Process process = new ProcessBuilder("which", "libpcap").start();
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    return true;
                }
                
                // 或者尝试通过 Pcap4j 检测
                Pcaps.findAllDevs();
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}

