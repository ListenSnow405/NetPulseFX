package com.netpulse.netpulsefx.model;

/**
 * AI 服务配置类
 * 
 * <p>用于存储 AI API 的配置信息，包括：
 * - API 提供商（deepseek、openai、ollama）
 * - API 端点 URL
 * - API 密钥
 * - 模型名称</p>
 * 
 * @author NetPulseFX Team
 */
public class AIConfig {
    
    /** API 提供商：deepseek、openai、ollama */
    private final String provider;
    
    /** API 端点 URL */
    private final String apiEndpoint;
    
    /** API 密钥（Ollama 可能不需要） */
    private final String apiKey;
    
    /** 模型名称 */
    private final String model;
    
    /**
     * 构造函数
     * 
     * @param provider API 提供商
     * @param apiEndpoint API 端点 URL
     * @param apiKey API 密钥
     * @param model 模型名称
     */
    public AIConfig(String provider, String apiEndpoint, String apiKey, String model) {
        this.provider = provider;
        this.apiEndpoint = apiEndpoint;
        this.apiKey = apiKey;
        this.model = model;
    }
    
    /**
     * 创建默认的 DeepSeek 配置
     */
    public static AIConfig defaultDeepSeek() {
        return new AIConfig(
            "deepseek",
            "https://api.deepseek.com/v1/chat/completions",
            "",  // 需要在配置中设置
            "deepseek-chat"
        );
    }
    
    /**
     * 创建默认的 OpenAI 配置
     * 使用最新的 gpt-4o 模型
     */
    public static AIConfig defaultOpenAI() {
        return new AIConfig(
            "openai",
            "https://api.openai.com/v1/chat/completions",
            "",  // 需要在配置中设置
            "gpt-4o"
        );
    }
    
    /**
     * 创建默认的 Ollama 配置（本地）
     */
    public static AIConfig defaultOllama() {
        return new AIConfig(
            "ollama",
            "http://localhost:11434/api/generate",
            "",  // Ollama 通常不需要密钥
            "llama2"  // 或其他本地模型
        );
    }
    
    /**
     * 创建默认的 Gemini 配置
     * 使用最新的 gemini-2.5-flash 模型（根据 Google AI Studio 文档）
     * API 端点格式：https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent
     * 参考：https://ai.google.dev/gemini-api/docs/api-key
     */
    public static AIConfig defaultGemini() {
        return new AIConfig(
            "gemini",
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
            "",  // 需要在配置中设置
            "gemini-2.5-flash"
        );
    }
    
    // Getter 方法
    public String getProvider() {
        return provider;
    }
    
    public String getApiEndpoint() {
        return apiEndpoint;
    }
    
    public String getApiKey() {
        return apiKey;
    }
    
    public String getModel() {
        return model;
    }
    
    @Override
    public String toString() {
        return String.format(
            "AIConfig{provider='%s', endpoint='%s', model='%s'}",
            provider, apiEndpoint, model
        );
    }
}





