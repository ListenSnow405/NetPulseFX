package com.netpulse.netpulsefx.service;

import com.netpulse.netpulsefx.model.AIConfig;

/**
 * AI 配置管理器（单例模式）
 * 用于在 MainController 和 HistoryController 之间共享 AI 配置
 * 
 * @author NetPulseFX Team
 */
public class AIConfigManager {
    
    private static AIConfigManager instance;
    private AIService aiService;
    private AIConfig currentConfig;
    
    private AIConfigManager() {
        // 私有构造函数，实现单例模式
    }
    
    /**
     * 获取 AIConfigManager 单例实例
     * 
     * @return AIConfigManager 实例
     */
    public static synchronized AIConfigManager getInstance() {
        if (instance == null) {
            instance = new AIConfigManager();
        }
        return instance;
    }
    
    /**
     * 更新 AI 配置
     * 
     * @param config 新的 AI 配置
     */
    public synchronized void updateConfig(AIConfig config) {
        this.currentConfig = config;
        this.aiService = new AIService(config);
        System.out.println("[AIConfigManager] AI 配置已更新: " + config);
        
        // 自动保存配置到文件
        AIConfigPersistenceService.saveConfig(config);
    }
    
    /**
     * 从文件加载配置
     * 
     * @return true 如果成功加载配置，false 否则
     */
    public synchronized boolean loadConfigFromFile() {
        AIConfig config = AIConfigPersistenceService.loadConfig();
        if (config != null) {
            this.currentConfig = config;
            this.aiService = new AIService(config);
            System.out.println("[AIConfigManager] 已从文件加载配置: " + config);
            return true;
        }
        return false;
    }
    
    /**
     * 获取当前的 AI 服务实例
     * 
     * @return AIService 实例，如果未配置则返回 null
     */
    public synchronized AIService getAIService() {
        if (aiService == null && currentConfig != null) {
            aiService = new AIService(currentConfig);
        }
        return aiService;
    }
    
    /**
     * 获取当前的 AI 配置
     * 
     * @return AIConfig 配置对象，如果未配置则返回 null
     */
    public synchronized AIConfig getConfig() {
        return currentConfig;
    }
    
    /**
     * 检查 AI 服务是否已配置
     * 
     * @return true 如果已配置，false 否则
     */
    public synchronized boolean isConfigured() {
        return currentConfig != null && aiService != null;
    }
}

