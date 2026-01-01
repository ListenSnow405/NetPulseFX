package com.netpulse.netpulsefx.model;

/**
 * AI 模型配置枚举
 * 统一管理所有支持的 AI 模型信息，包括显示名称、API 端点、类别等
 * 
 * @author NetPulseFX Team
 */
public enum AIModel {
    // ========== DeepSeek 系列 ==========
    DEEPSEEK_CHAT("DeepSeek Chat", "deepseek-chat", "deepseek",
            "https://api.deepseek.com/v1/chat/completions",
            "标准", "DeepSeek Chat 是标准对话模型"),
    
    DEEPSEEK_V3("DeepSeek V3", "deepseek-v3", "deepseek",
            "https://api.deepseek.com/v1/chat/completions",
            "旗舰", "DeepSeek V3 是最新的深度思考模型，推理能力强大"),
    
    DEEPSEEK_R1("DeepSeek R1", "deepseek-r1", "deepseek",
            "https://api.deepseek.com/v1/chat/completions",
            "推理", "DeepSeek R1 是专门优化的推理模型"),
    
    // ========== Google Gemini 系列 ==========
    GEMINI_2_5_PRO("Gemini 2.5 Pro", "gemini-2.5-pro", "gemini",
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro:generateContent",
            "旗舰", "Google Gemini 2.5 Pro 是 Google 最新的旗舰模型，提供强大的推理和分析能力"),
    
    GEMINI_2_5_FLASH("Gemini 2.5 Flash", "gemini-2.5-flash", "gemini",
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
            "极速", "Google Gemini 2.5 Flash 是快速响应版本，适合实时分析"),
    
    // ========== OpenAI 系列 ==========
    GPT_4O("GPT-4o", "gpt-4o", "openai",
            "https://api.openai.com/v1/chat/completions",
            "旗舰", "OpenAI GPT-4o 是最新的多模态模型，性能卓越"),
    
    GPT_4O_MINI("GPT-4o-mini", "gpt-4o-mini", "openai",
            "https://api.openai.com/v1/chat/completions",
            "极速", "OpenAI GPT-4o-mini 是快速响应版本，成本更低"),
    
    // ========== Ollama 本地模型 ==========
    LLAMA4("Llama 4", "llama4", "ollama",
            "http://localhost:11434/api/generate",
            "本地", "Llama 4 是 Meta 最新的开源模型，需要本地部署"),
    
    // ========== 自定义模型 ==========
    CUSTOM("自定义模型", "custom", "custom",
            "",
            "自定义", "使用自定义 API 端点和模型名称");
    
    private final String displayName;      // 显示名称（带类别标签）
    private final String modelId;          // 模型 ID（用于 API 调用）
    private final String provider;         // 提供商
    private final String defaultEndpoint;  // 默认 API 端点
    private final String category;         // 类别标签（旗舰、极速、推理等）
    private final String description;      // 模型描述
    
    AIModel(String displayName, String modelId, String provider,
            String defaultEndpoint, String category, String description) {
        this.displayName = displayName;
        this.modelId = modelId;
        this.provider = provider;
        this.defaultEndpoint = defaultEndpoint;
        this.category = category;
        this.description = description;
    }
    
    /**
     * 获取带类别标签的显示名称
     * 格式：[类别] 模型名称
     */
    public String getDisplayName() {
        return "[" + category + "] " + displayName;
    }
    
    /**
     * 获取不带标签的显示名称
     */
    public String getSimpleDisplayName() {
        return displayName;
    }
    
    /**
     * 获取模型 ID（用于 API 调用）
     */
    public String getModelId() {
        return modelId;
    }
    
    /**
     * 获取提供商
     */
    public String getProvider() {
        return provider;
    }
    
    /**
     * 获取默认 API 端点
     */
    public String getDefaultEndpoint() {
        return defaultEndpoint;
    }
    
    /**
     * 获取类别标签
     */
    public String getCategory() {
        return category;
    }
    
    /**
     * 获取模型描述
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 根据模型 ID 查找对应的枚举值
     * 
     * @param modelId 模型 ID
     * @return AIModel 枚举值，如果未找到则返回 null
     */
    public static AIModel findByModelId(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return null;
        }
        
        for (AIModel model : values()) {
            if (model.getModelId().equalsIgnoreCase(modelId)) {
                return model;
            }
        }
        
        return null;
    }
    
    /**
     * 根据显示名称查找对应的枚举值
     * 支持带或不带类别标签的格式
     * 
     * @param displayName 显示名称
     * @return AIModel 枚举值，如果未找到则返回 null
     */
    public static AIModel findByDisplayName(String displayName) {
        if (displayName == null || displayName.isEmpty()) {
            return null;
        }
        
        // 移除类别标签（如果存在）
        String cleanName = displayName.replaceFirst("^\\[.*?\\]\\s*", "");
        
        for (AIModel model : values()) {
            if (model.getDisplayName().equals(displayName) ||
                model.getSimpleDisplayName().equals(displayName) ||
                model.getSimpleDisplayName().equals(cleanName)) {
                return model;
            }
        }
        
        return null;
    }
    
    /**
     * 获取所有非自定义模型（用于 UI 显示）
     * 
     * @return AIModel 数组
     */
    public static AIModel[] getAvailableModels() {
        AIModel[] all = values();
        AIModel[] available = new AIModel[all.length - 1]; // 排除 CUSTOM
        int index = 0;
        for (AIModel model : all) {
            if (model != CUSTOM) {
                available[index++] = model;
            }
        }
        return available;
    }
    
    /**
     * 获取指定提供商的所有模型
     * 
     * @param provider 提供商名称
     * @return AIModel 数组
     */
    public static AIModel[] getModelsByProvider(String provider) {
        if (provider == null || provider.isEmpty()) {
            return new AIModel[0];
        }
        
        java.util.List<AIModel> models = new java.util.ArrayList<>();
        for (AIModel model : values()) {
            if (model.getProvider().equalsIgnoreCase(provider)) {
                models.add(model);
            }
        }
        return models.toArray(new AIModel[0]);
    }
}

