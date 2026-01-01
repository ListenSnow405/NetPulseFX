package com.netpulse.netpulsefx.service;

import com.netpulse.netpulsefx.model.AIConfig;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Properties;

/**
 * AI 配置持久化服务
 * 用于将 API 配置保存到本地文件，并在应用启动时自动加载
 * 
 * <p>配置文件位置：</p>
 * <ul>
 *   <li>Windows: %APPDATA%\NetPulseFX\ai_config.properties</li>
 *   <li>Linux/Mac: ~/.netpulsefx/ai_config.properties</li>
 * </ul>
 * 
 * <p>安全说明：</p>
 * <ul>
 *   <li>API Key 使用 Base64 编码存储（非加密，仅避免明文）</li>
 *   <li>配置文件存储在用户目录，避免被其他用户访问</li>
 * </ul>
 * 
 * @author NetPulseFX Team
 */
public class AIConfigPersistenceService {
    
    /** 配置文件名 */
    private static final String CONFIG_FILE_NAME = "ai_config.properties";
    
    /** 配置目录名 */
    private static final String CONFIG_DIR_NAME = "NetPulseFX";
    
    /** 配置文件的完整路径 */
    private static Path configFilePath;
    
    static {
        // 初始化配置文件路径
        String userHome = System.getProperty("user.home");
        String os = System.getProperty("os.name", "").toLowerCase();
        
        Path configDir;
        if (os.contains("win")) {
            // Windows: 使用 AppData\Roaming
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isEmpty()) {
                configDir = Paths.get(appData, CONFIG_DIR_NAME);
            } else {
                configDir = Paths.get(userHome, CONFIG_DIR_NAME);
            }
        } else {
            // Linux/Mac: 使用 ~/.netpulsefx
            configDir = Paths.get(userHome, ".netpulsefx");
        }
        
        // 确保配置目录存在
        try {
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }
        } catch (IOException e) {
            System.err.println("[AIConfigPersistenceService] 无法创建配置目录: " + e.getMessage());
            // 如果无法创建目录，使用当前工作目录
            configDir = Paths.get(".");
        }
        
        configFilePath = configDir.resolve(CONFIG_FILE_NAME);
        System.out.println("[AIConfigPersistenceService] 配置文件路径: " + configFilePath.toAbsolutePath());
    }
    
    /**
     * 保存 AI 配置到文件
     * 
     * @param config AI 配置对象
     * @return true 如果保存成功，false 否则
     */
    public static boolean saveConfig(AIConfig config) {
        if (config == null) {
            System.err.println("[AIConfigPersistenceService] 配置对象为空，无法保存");
            return false;
        }
        
        try {
            Properties props = new Properties();
            
            // 保存配置信息
            props.setProperty("provider", config.getProvider() != null ? config.getProvider() : "");
            props.setProperty("apiEndpoint", config.getApiEndpoint() != null ? config.getApiEndpoint() : "");
            props.setProperty("model", config.getModel() != null ? config.getModel() : "");
            
            // API Key 使用 Base64 编码（非加密，仅避免明文）
            String apiKey = config.getApiKey();
            if (apiKey != null && !apiKey.isEmpty()) {
                String encodedKey = Base64.getEncoder().encodeToString(apiKey.getBytes(StandardCharsets.UTF_8));
                props.setProperty("apiKey", encodedKey);
            } else {
                props.setProperty("apiKey", "");
            }
            
            // 保存到文件
            try (FileOutputStream fos = new FileOutputStream(configFilePath.toFile());
                 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                props.store(writer, "NetPulse FX AI Configuration\n" +
                    "This file contains your AI API configuration.\n" +
                    "Please keep this file secure and do not share it with others.");
            }
            
            System.out.println("[AIConfigPersistenceService] 配置已保存到: " + configFilePath.toAbsolutePath());
            return true;
            
        } catch (IOException e) {
            System.err.println("[AIConfigPersistenceService] 保存配置失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 从文件加载 AI 配置
     * 
     * @return AIConfig 配置对象，如果加载失败或文件不存在则返回 null
     */
    public static AIConfig loadConfig() {
        if (!Files.exists(configFilePath)) {
            System.out.println("[AIConfigPersistenceService] 配置文件不存在: " + configFilePath.toAbsolutePath());
            return null;
        }
        
        try {
            Properties props = new Properties();
            
            // 从文件加载
            try (FileInputStream fis = new FileInputStream(configFilePath.toFile());
                 InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
            
            // 读取配置信息
            String provider = props.getProperty("provider", "");
            String apiEndpoint = props.getProperty("apiEndpoint", "");
            String model = props.getProperty("model", "");
            String encodedKey = props.getProperty("apiKey", "");
            
            // 如果配置为空，返回 null
            if (provider.isEmpty() && apiEndpoint.isEmpty() && model.isEmpty()) {
                System.out.println("[AIConfigPersistenceService] 配置文件为空");
                return null;
            }
            
            // 解码 API Key
            String apiKey = "";
            if (encodedKey != null && !encodedKey.isEmpty()) {
                try {
                    byte[] decodedBytes = Base64.getDecoder().decode(encodedKey);
                    apiKey = new String(decodedBytes, StandardCharsets.UTF_8);
                } catch (IllegalArgumentException e) {
                    System.err.println("[AIConfigPersistenceService] API Key 解码失败，使用空字符串: " + e.getMessage());
                    apiKey = "";
                }
            }
            
            // 创建配置对象
            AIConfig config = new AIConfig(provider, apiEndpoint, apiKey, model);
            System.out.println("[AIConfigPersistenceService] 配置已加载: provider=" + provider + ", model=" + model);
            return config;
            
        } catch (IOException e) {
            System.err.println("[AIConfigPersistenceService] 加载配置失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 删除保存的配置文件
     * 
     * @return true 如果删除成功，false 否则
     */
    public static boolean deleteConfig() {
        try {
            if (Files.exists(configFilePath)) {
                Files.delete(configFilePath);
                System.out.println("[AIConfigPersistenceService] 配置文件已删除: " + configFilePath.toAbsolutePath());
                return true;
            }
            return false;
        } catch (IOException e) {
            System.err.println("[AIConfigPersistenceService] 删除配置文件失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 获取配置文件路径（用于调试）
     * 
     * @return 配置文件路径
     */
    public static Path getConfigFilePath() {
        return configFilePath;
    }
}

