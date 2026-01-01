package com.netpulse.netpulsefx;

import java.io.File;
import java.lang.reflect.Method;

/**
 * 主程序包装器 - 环境隔离式预检
 * 
 * <p>功能说明：</p>
 * <ul>
 *   <li>在启动主应用前进行环境检测（wpcap.dll 检测）</li>
 *   <li>捕获类加载错误（NoClassDefFoundError、UnsatisfiedLinkError）</li>
 *   <li>使用 Swing JOptionPane 显示错误提示（不依赖 JavaFX 场景图）</li>
 *   <li>智能分析异常原因，明确指出驱动问题</li>
 * </ul>
 * 
 * <p>设计原则：</p>
 * <ul>
 *   <li>彻底去耦合：不导入任何 Pcap4j 相关类</li>
 *   <li>硬核检测：直接检测系统文件是否存在</li>
 *   <li>错误隔离：捕获所有可能的启动错误</li>
 *   <li>明确提示：分析异常原因，明确指出驱动未安装问题</li>
 * </ul>
 * 
 * @author NetPulseFX Team
 */
public class MainWrapper {
    
    /**
     * 主入口方法
     * 
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 步骤1：环境预检（Pre-check）- 在执行 App.main(args) 之前
        if (!checkEnvironment()) {
            // 环境检测失败，显示特定错误弹窗并退出
            showEnvironmentCheckFailedDialog();
            System.exit(0);
        }
        
        // 步骤2：尝试启动主应用，捕获所有可能的错误
        try {
            // 使用反射调用 HelloApplication.main，避免在编译时依赖
            Class<?> appClass = Class.forName("com.netpulse.netpulsefx.HelloApplication");
            Method mainMethod = appClass.getMethod("main", String[].class);
            mainMethod.invoke(null, (Object) args);
        } catch (Throwable t) {
            // 捕获所有可能的错误（包括 Error 和 Exception）
            handleApplicationStartupError(t);
        }
    }
    
    /**
     * 环境检查方法（Pre-check）
     * 在执行 App.main(args) 之前进行纯文件检测
     * 
     * <p>检测逻辑：</p>
     * <ul>
     *   <li>如果是 Windows 系统，检查 C:\Windows\System32\wpcap.dll 是否存在</li>
     *   <li>如果不存在，直接返回 false</li>
     * </ul>
     * 
     * @return 如果环境检查通过返回 true，否则返回 false
     */
    private static boolean checkEnvironment() {
        // 只在 Windows 系统下进行检测
        if (!isWindows()) {
            return true; // 非 Windows 系统，跳过检测
        }
        
        // 检查 C:\Windows\System32\wpcap.dll
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot == null || systemRoot.isEmpty()) {
            systemRoot = "C:\\Windows"; // 默认值
        }
        
        String wpcapPath = systemRoot + "\\System32\\wpcap.dll";
        File wpcapFile = new File(wpcapPath);
        
        if (wpcapFile.exists() && wpcapFile.isFile()) {
            System.out.println("[MainWrapper] 环境检查通过：找到 wpcap.dll: " + wpcapPath);
            return true;
        } else {
            System.err.println("[MainWrapper] 环境检查失败：未找到 wpcap.dll: " + wpcapPath);
            return false;
        }
    }
    
    /**
     * 检测是否为 Windows 系统
     * 
     * @return 如果是 Windows 系统返回 true，否则返回 false
     */
    private static boolean isWindows() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        return osName.contains("windows");
    }
    
    /**
     * 显示环境检查失败对话框
     * 使用 javax.swing.JOptionPane（最稳妥的跨平台弹窗，不依赖 JavaFX 运行时）
     */
    private static void showEnvironmentCheckFailedDialog() {
        try {
            javax.swing.JOptionPane.showMessageDialog(
                null,
                "未在系统中检测到 Npcap 驱动。请安装 Npcap 驱动后再运行程序。\n\n" +
                "官方下载地址：https://npcap.com/\n\n" +
                "您可以参考项目根目录下的 README.md 获取安装指引。",
                "系统环境检查失败",
                javax.swing.JOptionPane.ERROR_MESSAGE
            );
        } catch (Exception e) {
            // 如果 Swing 也失败，输出到控制台
            System.err.println("========================================");
            System.err.println("系统环境检查失败");
            System.err.println("========================================");
            System.err.println("未在系统中检测到 Npcap 驱动。请安装 Npcap 驱动后再运行程序。");
            System.err.println("官方下载地址：https://npcap.com/");
            System.err.println("您可以参考项目根目录下的 README.md 获取安装指引。");
            System.err.println("========================================");
            e.printStackTrace();
        }
    }
    
    /**
     * 处理应用程序启动错误
     * 完善异常捕获，分析异常原因，明确指出驱动问题
     * 
     * @param t 捕获的异常或错误
     */
    private static void handleApplicationStartupError(Throwable t) {
        // 打印完整的堆栈信息到控制台
        System.err.println("========================================");
        System.err.println("[MainWrapper] 应用程序启动失败");
        System.err.println("========================================");
        System.err.println("异常类型: " + t.getClass().getName());
        System.err.println("异常消息: " + (t.getMessage() != null ? t.getMessage() : "null"));
        System.err.println("完整堆栈信息:");
        t.printStackTrace();
        System.err.println("========================================");
        
        // 分析 t.getCause()，检查是否包含驱动相关问题
        Throwable cause = t.getCause();
        if (cause != null) {
            System.err.println("根本原因 (Cause):");
            System.err.println("  类型: " + cause.getClass().getName());
            System.err.println("  消息: " + (cause.getMessage() != null ? cause.getMessage() : "null"));
            cause.printStackTrace();
            System.err.println("========================================");
        }
        
        // 检查是否是驱动相关问题
        boolean isDriverIssue = isDriverRelatedError(t, cause);
        
        if (isDriverIssue) {
            // 如果是驱动问题，显示特定的驱动错误弹窗
            showDriverErrorDialog();
        } else {
            // 其他错误，显示通用错误弹窗
            showGenericErrorDialog(t);
        }
    }
    
    /**
     * 判断是否是驱动相关的错误
     * 检查异常或原因中是否包含 UnsatisfiedLinkError 或 pcap 关键字
     * 
     * @param throwable 主异常
     * @param cause 根本原因
     * @return 如果是驱动相关问题返回 true，否则返回 false
     */
    private static boolean isDriverRelatedError(Throwable throwable, Throwable cause) {
        // 检查主异常
        if (throwable instanceof UnsatisfiedLinkError) {
            return true;
        }
        
        // 检查异常消息中是否包含 pcap 关键字（不区分大小写）
        String message = throwable.getMessage();
        if (message != null && message.toLowerCase().contains("pcap")) {
            return true;
        }
        
        // 检查异常类名中是否包含 pcap 关键字（不区分大小写）
        String className = throwable.getClass().getName();
        if (className.toLowerCase().contains("pcap")) {
            return true;
        }
        
        // 检查根本原因
        if (cause != null) {
            if (cause instanceof UnsatisfiedLinkError) {
                return true;
            }
            
            String causeMessage = cause.getMessage();
            if (causeMessage != null && causeMessage.toLowerCase().contains("pcap")) {
                return true;
            }
            
            String causeClassName = cause.getClass().getName();
            if (causeClassName.toLowerCase().contains("pcap")) {
                return true;
            }
        }
        
        // 检查堆栈跟踪中是否包含 pcap 相关类
        StackTraceElement[] stackTrace = throwable.getStackTrace();
        if (stackTrace != null) {
            for (StackTraceElement element : stackTrace) {
                String classNameInStack = element.getClassName();
                if (classNameInStack != null && classNameInStack.toLowerCase().contains("pcap")) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 显示驱动错误对话框
     * 明确指出驱动未安装问题
     */
    private static void showDriverErrorDialog() {
        try {
            javax.swing.JOptionPane.showMessageDialog(
                null,
                "检测到驱动相关问题。\n\n" +
                "应用程序无法加载 Npcap 驱动，请确保：\n" +
                "1. 已安装 Npcap 驱动（推荐）或 WinPcap 驱动\n" +
                "2. 驱动已正确安装并配置\n" +
                "3. 已重启计算机（如果刚安装驱动）\n\n" +
                "您可以参考项目根目录下的 README.md 获取安装指引。\n\n" +
                "详细错误信息请查看控制台输出。",
                "驱动加载失败",
                javax.swing.JOptionPane.ERROR_MESSAGE
            );
        } catch (Exception e) {
            // 如果 Swing 也失败，输出到控制台
            System.err.println("========================================");
            System.err.println("驱动加载失败");
            System.err.println("========================================");
            System.err.println("检测到驱动相关问题。");
            System.err.println("应用程序无法加载 Npcap 驱动，请确保：");
            System.err.println("1. 已安装 Npcap 驱动（推荐）或 WinPcap 驱动");
            System.err.println("2. 驱动已正确安装并配置");
            System.err.println("3. 已重启计算机（如果刚安装驱动）");
            System.err.println("您可以参考项目根目录下的 README.md 获取安装指引。");
            System.err.println("========================================");
            e.printStackTrace();
        }
    }
    
    /**
     * 显示通用错误对话框
     * 用于非驱动相关的其他错误
     * 
     * @param throwable 捕获的异常或错误
     */
    private static void showGenericErrorDialog(Throwable throwable) {
        try {
            String errorMessage = "应用程序启动时发生错误。\n\n";
            errorMessage += "错误类型: " + throwable.getClass().getSimpleName() + "\n";
            if (throwable.getMessage() != null) {
                errorMessage += "错误消息: " + throwable.getMessage() + "\n";
            }
            errorMessage += "\n详细错误信息请查看控制台输出。";
            
            javax.swing.JOptionPane.showMessageDialog(
                null,
                errorMessage,
                "应用程序启动失败",
                javax.swing.JOptionPane.ERROR_MESSAGE
            );
        } catch (Exception e) {
            // 如果 Swing 也失败，输出到控制台
            System.err.println("========================================");
            System.err.println("应用程序启动失败");
            System.err.println("========================================");
            System.err.println("错误类型: " + throwable.getClass().getSimpleName());
            if (throwable.getMessage() != null) {
                System.err.println("错误消息: " + throwable.getMessage());
            }
            System.err.println("详细错误信息请查看控制台输出。");
            System.err.println("========================================");
            e.printStackTrace();
        }
    }
}
