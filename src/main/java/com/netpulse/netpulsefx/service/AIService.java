package com.netpulse.netpulsefx.service;

import com.netpulse.netpulsefx.model.TrafficData;
import com.netpulse.netpulsefx.model.AIConfig;
import com.netpulse.netpulsefx.service.DatabaseService.MonitoringSession;
import com.netpulse.netpulsefx.service.DatabaseService.TrafficRecord;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * AI 流量分析服务
 * 
 * <p>使用 Java 21 原生的 HttpClient 异步调用大模型 API（DeepSeek、OpenAI、Ollama）
 * 对流量数据进行智能分析，识别网络状态、潜在风险和优化建议。</p>
 * 
 * <p><strong>支持的 API 提供商：</strong></p>
 * <ul>
 *   <li><strong>DeepSeek：</strong> https://api.deepseek.com/v1/chat/completions</li>
 *   <li><strong>OpenAI：</strong> https://api.openai.com/v1/chat/completions</li>
 *   <li><strong>Ollama（本地）：</strong> http://localhost:11434/api/generate</li>
 * </ul>
 * 
 * <p><strong>性能优化：</strong></p>
 * <ul>
 *   <li>所有 API 调用都是异步的，使用 CompletableFuture 处理，不会阻塞 UI 线程</li>
 *   <li>设置了合理的超时时间（30秒），避免长时间等待</li>
 *   <li>完善的错误处理机制，返回友好的中文错误提示</li>
 * </ul>
 * 
 * @author NetPulseFX Team
 */
public class AIService {
    
    /**
     * 系统提示词（通用流量分析）
     * 定义 AI 的角色和分析要求
     */
    private static final String SYSTEM_PROMPT = """
        你是一个网络安全专家，请根据以下流量数据分析网络状态，识别潜在的拥堵或安全风险（如 DDoS 模拟、大量下载），并给出改进建议。
        
        请从以下角度进行分析：
        1. 流量趋势：分析流量变化趋势，是否出现异常波动
        2. 网络性能：评估当前网络性能状态，是否存在拥堵
        3. 安全风险：识别潜在的网络安全威胁（如异常流量、DDoS 攻击迹象、异常下载等）
        4. 优化建议：基于分析结果，提供具体的网络优化建议
        
        请使用中文回答，格式清晰，建议分点列出。
        """;
    
    /**
     * 系统提示词（会话深度分析）
     * 用于对监控会话进行专业的网络行为分析
     * 
     * <p><strong>重要约束：</strong></p>
     * <ul>
     *   <li>严禁输出任何开场白、结束语或过渡性废话</li>
     *   <li>直接以 Markdown 格式的标题开始输出分析报告</li>
     *   <li>只输出报告的正文内容，禁止包含任何对话式的回复</li>
     * </ul>
     */
    private static final String SESSION_ANALYSIS_SYSTEM_PROMPT = """
        你是一个网络审计专家，专门负责分析网络监控会话数据。
        
        **重要输出要求：**
        1. 严禁输出任何开场白（如"好的，作为一名专家..."、"根据您提供的数据..."等）
        2. 严禁输出任何结束语（如"希望以上分析对您有帮助"、"如有疑问请随时咨询"等）
        3. 严禁输出任何过渡性废话或客套话
        4. 直接以 Markdown 格式的标题开始输出分析报告
        5. 只输出报告的正文内容，禁止包含任何对话式的回复
        
        **报告格式要求：**
        请根据提供的会话概况信息（时长、网卡、均速）和明细采样数据（异常点、IP 归属、关联进程），生成一份专业的网络行为分析报告。
        
        报告必须使用 Markdown 格式，直接以以下结构开始（不要有任何前言）：
        
        ## 1. 流量趋势评估
        - 分析整体流量变化趋势（上行/下行）
        - 识别流量峰值和低谷时段
        - 评估流量模式的稳定性
        
        ## 2. 潜在风险识别
        - **异常后台上传**：检查是否存在异常的上传行为（如数据泄露迹象）
        - **异常下载**：识别异常的大量下载行为
        - **异常连接**：分析 IP 归属地，识别可疑的远程连接
        - **进程关联**：分析哪些进程产生了异常流量
        
        ## 3. 优化建议
        - 网络性能优化建议
        - 安全防护建议
        - 资源使用优化建议
        
        **再次强调：直接以 "## 1. 流量趋势评估" 开始输出，不要有任何开场白或客套话。**
        """;
    
    /** HTTP 客户端实例（单例） */
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))  // 连接超时：10秒
            .build();
    
    /** 请求超时时间：30秒 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    
    /** 时间格式化器 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /** AI 配置对象 */
    private final AIConfig config;
    
    /**
     * 构造函数
     * 
     * @param config AI 配置（API 端点、密钥等）
     */
    public AIService(AIConfig config) {
        this.config = config;
    }
    
    /**
     * 分析流量数据（核心方法）
     * 
     * <p>将流量数据格式化为 JSON 或文本，调用 AI API 进行分析，
     * 返回 AI 的分析报告。</p>
     * 
     * <p><strong>执行流程：</strong></p>
     * <ol>
     *   <li>将流量数据列表格式化为 JSON 字符串</li>
     *   <li>构建包含系统提示词和用户数据的完整请求</li>
     *   <li>使用 HttpClient 异步发送 HTTP 请求</li>
     *   <li>解析 API 响应，提取 AI 分析结果</li>
     *   <li>处理各种异常情况，返回友好的错误信息</li>
     * </ol>
     * 
     * @param history 流量历史数据列表
     * @return CompletableFuture<String> 异步返回 AI 分析报告
     */
    public CompletableFuture<String> analyzeTraffic(List<TrafficData> history) {
        // 检查配置
        if (config == null || config.getApiEndpoint() == null || config.getApiEndpoint().isEmpty()) {
            return CompletableFuture.completedFuture(
                "错误：AI 服务未配置。请在配置文件中设置 API 端点和密钥。"
            );
        }
        
        if (history == null || history.isEmpty()) {
            return CompletableFuture.completedFuture(
                "提示：没有流量数据可供分析。请先进行流量监控，收集一些数据后再尝试分析。"
            );
        }
        
        // 在后台线程中执行 API 调用
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 输出调试信息
                System.out.println("[AIService] 开始调用 AI API:");
                System.out.println("  Provider: " + config.getProvider());
                System.out.println("  Endpoint: " + config.getApiEndpoint());
                System.out.println("  Model: " + config.getModel());
                System.out.println("  API Key: " + (config.getApiKey() != null && !config.getApiKey().isEmpty() 
                    ? "已设置（长度: " + config.getApiKey().length() + "）" : "未设置"));
                
                // 格式化流量数据
                String trafficDataJson = formatTrafficDataToJson(history);
                
                // 构建用户提示词
                String userPrompt = buildUserPrompt(trafficDataJson);
                
                // 根据 API 提供商构建请求体
                String requestBody = buildRequestBody(userPrompt);
                
                // 输出请求体预览（只显示前 500 字符，避免日志过长）
                System.out.println("[AIService] 请求体预览: " + 
                    (requestBody.length() > 500 ? requestBody.substring(0, 500) + "..." : requestBody));
                
                // 构建 HTTP 请求
                HttpRequest request = buildHttpRequest(requestBody);
                
                System.out.println("[AIService] 正在发送 HTTP 请求...");
                
                // 发送异步请求并获取响应
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                System.out.println("[AIService] 收到响应: HTTP " + response.statusCode());
                
                // 解析响应
                return parseResponse(response);
                
            } catch (java.net.http.HttpTimeoutException e) {
                System.err.println("[AIService] 请求超时: " + e.getMessage());
                return "错误：请求超时（30秒）。\n" +
                       "可能的原因：\n" +
                       "1. API 服务器响应时间过长\n" +
                       "2. 网络连接较慢\n" +
                       "3. API 端点地址不正确\n\n" +
                       "请检查网络连接或稍后重试。";
                
            } catch (java.net.ConnectException e) {
                System.err.println("[AIService] 连接失败: " + e.getMessage());
                System.err.println("  Endpoint: " + config.getApiEndpoint());
                return "错误：无法连接到 API 服务器。\n\n" +
                       "详细信息：\n" +
                       "- 端点: " + config.getApiEndpoint() + "\n" +
                       "- 错误: " + e.getMessage() + "\n\n" +
                       "请检查：\n" +
                       "1. API 端点地址是否正确（当前: " + config.getApiEndpoint() + "）\n" +
                       "2. 网络连接是否正常（能否访问外网）\n" +
                       "3. 防火墙是否阻止了连接\n" +
                       "4. 如果是 DeepSeek/OpenAI，请确认 API 端点可以正常访问";
                
            } catch (javax.net.ssl.SSLException e) {
                System.err.println("[AIService] SSL 错误: " + e.getMessage());
                return "错误：SSL/TLS 连接失败。\n" +
                       "可能的原因：\n" +
                       "1. 证书验证失败\n" +
                       "2. 代理服务器配置问题\n\n" +
                       "错误详情: " + e.getMessage();
                
            } catch (java.net.UnknownHostException e) {
                System.err.println("[AIService] 域名解析失败: " + e.getMessage());
                return "错误：无法解析 API 服务器地址。\n\n" +
                       "详细信息：\n" +
                       "- 端点: " + config.getApiEndpoint() + "\n" +
                       "- 错误: " + e.getMessage() + "\n\n" +
                       "请检查：\n" +
                       "1. API 端点地址是否正确\n" +
                       "2. DNS 设置是否正确\n" +
                       "3. 网络连接是否正常";
                
            } catch (java.io.IOException e) {
                System.err.println("[AIService] IO 错误: " + e.getMessage());
                e.printStackTrace();
                return "错误：网络通信失败。\n\n" +
                       "详细信息：\n" +
                       "- 端点: " + config.getApiEndpoint() + "\n" +
                       "- 错误: " + e.getClass().getSimpleName() + ": " + e.getMessage() + "\n\n" +
                       "请检查网络连接和 API 配置。";
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("[AIService] 请求被中断");
                return "错误：请求被中断。";
                
            } catch (Exception e) {
                System.err.println("[AIService] 未预期的异常: " + e.getClass().getName());
                e.printStackTrace();
                return "错误：分析过程中发生异常。\n\n" +
                       "异常类型: " + e.getClass().getSimpleName() + "\n" +
                       "错误消息: " + e.getMessage() + "\n\n" +
                       "请检查：\n" +
                       "1. API 配置是否正确（端点、密钥、模型）\n" +
                       "2. API 密钥是否有效\n" +
                       "3. 网络连接是否正常";
            }
        });
    }
    
    /**
     * 将流量数据列表格式化为 JSON 字符串
     * 
     * @param history 流量历史数据
     * @return JSON 格式的字符串
     */
    private String formatTrafficDataToJson(List<TrafficData> history) {
        // 构建 JSON 数组
        String jsonArray = history.stream()
                .map(data -> String.format(
                    """
                    {
                      "interfaceName": "%s",
                      "downSpeed": %.2f,
                      "upSpeed": %.2f,
                      "captureTime": "%s",
                      "packetSize": %d,
                      "protocol": "%s"
                    }""",
                    data.getInterfaceName(),
                    data.getDownSpeed(),
                    data.getUpSpeed(),
                    data.getCaptureTime().format(TIME_FORMATTER),
                    data.getPacketSize(),
                    data.getProtocol()
                ))
                .collect(Collectors.joining(",\n    ", "[\n    ", "\n  ]"));
        
        // 添加统计信息
        double avgDownSpeed = history.stream().mapToDouble(TrafficData::getDownSpeed).average().orElse(0);
        double avgUpSpeed = history.stream().mapToDouble(TrafficData::getUpSpeed).average().orElse(0);
        double maxDownSpeed = history.stream().mapToDouble(TrafficData::getDownSpeed).max().orElse(0);
        double maxUpSpeed = history.stream().mapToDouble(TrafficData::getUpSpeed).max().orElse(0);
        
        return String.format("""
            {
              "trafficRecords": %s,
              "statistics": {
                "totalRecords": %d,
                "averageDownSpeed": %.2f,
                "averageUpSpeed": %.2f,
                "maxDownSpeed": %.2f,
                "maxUpSpeed": %.2f,
                "timeRange": {
                  "start": "%s",
                  "end": "%s"
                }
              }
            }""",
            jsonArray,
            history.size(),
            avgDownSpeed,
            avgUpSpeed,
            maxDownSpeed,
            maxUpSpeed,
            history.get(history.size() - 1).getCaptureTime().format(TIME_FORMATTER),
            history.get(0).getCaptureTime().format(TIME_FORMATTER)
        );
    }
    
    /**
     * 构建用户提示词
     * 
     * @param trafficDataJson 格式化的流量数据 JSON
     * @return 用户提示词
     */
    private String buildUserPrompt(String trafficDataJson) {
        return String.format(
            "请分析以下网络流量数据：\n\n%s\n\n请提供详细的分析报告。",
            trafficDataJson
        );
    }
    
    /**
     * 根据 API 提供商构建请求体
     * 
     * @param userPrompt 用户提示词
     * @return JSON 格式的请求体
     */
    private String buildRequestBody(String userPrompt) {
        String provider = config.getProvider();
        
        if ("ollama".equalsIgnoreCase(provider)) {
            // Ollama API 格式
            return String.format(
                """
                {
                  "model": "%s",
                  "prompt": "%s",
                  "stream": false
                }""",
                config.getModel(),
                escapeJsonString(buildOllamaPrompt(userPrompt))
            );
        } else {
            // DeepSeek / OpenAI API 格式（Chat Completions API）
            return String.format(
                """
                {
                  "model": "%s",
                  "messages": [
                    {
                      "role": "system",
                      "content": "%s"
                    },
                    {
                      "role": "user",
                      "content": "%s"
                    }
                  ],
                  "temperature": 0.7,
                  "max_tokens": 2000
                }""",
                config.getModel(),
                escapeJsonString(SYSTEM_PROMPT),
                escapeJsonString(userPrompt)
            );
        }
    }
    
    /**
     * 构建 Ollama 提示词（将系统提示和用户提示合并）
     */
    private String buildOllamaPrompt(String userPrompt) {
        return SYSTEM_PROMPT + "\n\n" + userPrompt;
    }
    
    /**
     * 转义 JSON 字符串中的特殊字符
     */
    private String escapeJsonString(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    /**
     * 构建 HTTP 请求
     * 
     * @param requestBody 请求体 JSON
     * @return HttpRequest 对象
     */
    private HttpRequest buildHttpRequest(String requestBody) {
        // 清理端点 URL（去除可能的引号和空白字符）
        String endpoint = config.getApiEndpoint().trim();
        // 去除首尾引号（如果存在）
        if ((endpoint.startsWith("\"") && endpoint.endsWith("\"")) ||
            (endpoint.startsWith("'") && endpoint.endsWith("'"))) {
            endpoint = endpoint.substring(1, endpoint.length() - 1).trim();
        }
        
        // 验证并创建 URI
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                String.format("无效的 API 端点地址: '%s'。请检查环境变量 AI_API_ENDPOINT 是否正确设置（不应包含引号）。", 
                    config.getApiEndpoint()), e);
        }
        
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));
        
        // 添加认证头（如果有 API 密钥）
        if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
            if ("ollama".equalsIgnoreCase(config.getProvider())) {
                // Ollama 通常不需要 API 密钥，但有些配置可能需要
                // 这里可以根据实际情况调整
            } else {
                // DeepSeek / OpenAI 使用 Bearer Token
                builder.header("Authorization", "Bearer " + config.getApiKey());
            }
        }
        
        return builder.build();
    }
    
    /**
     * 解析 API 响应
     * 
     * @param response HTTP 响应
     * @return AI 分析结果
     * @throws Exception 如果响应格式不正确或包含错误
     */
    private String parseResponse(HttpResponse<String> response) throws Exception {
        int statusCode = response.statusCode();
        String responseBody = response.body();
        
        System.out.println("[AIService] 响应状态码: " + statusCode);
        System.out.println("[AIService] 响应体长度: " + (responseBody != null ? responseBody.length() : 0) + " 字符");
        
        // 检查 HTTP 状态码
        if (statusCode == 401) {
            System.err.println("[AIService] 认证失败: " + responseBody);
            return "错误：API 密钥无效或未设置（HTTP 401）。\n\n" +
                   "请检查：\n" +
                   "1. 环境变量 AI_API_KEY 是否已正确设置\n" +
                   "2. API 密钥是否有效\n" +
                   "3. API 密钥是否已过期\n\n" +
                   "响应详情: " + (responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody);
        } else if (statusCode == 403) {
            System.err.println("[AIService] 访问被拒绝: " + responseBody);
            return "错误：API 访问被拒绝（HTTP 403）。\n\n" +
                   "可能的原因：\n" +
                   "1. API 密钥没有访问权限\n" +
                   "2. 账户余额不足\n" +
                   "3. 模型访问权限受限\n\n" +
                   "响应详情: " + (responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody);
        } else if (statusCode == 429) {
            System.err.println("[AIService] 请求频率过高: " + responseBody);
            return "错误：API 请求频率过高（HTTP 429）。\n\n" +
                   "请稍后重试，或检查 API 使用限额。";
        } else if (statusCode >= 400) {
            System.err.println("[AIService] API 错误响应: " + responseBody);
            return String.format("错误：API 请求失败（HTTP %d）。\n\n响应内容：\n%s", 
                statusCode, 
                responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody);
        }
        
        // 解析 JSON 响应
        String provider = config.getProvider();
        if ("ollama".equalsIgnoreCase(provider)) {
            // Ollama 响应格式：{"response": "..."}
            return extractOllamaResponse(responseBody);
        } else {
            // DeepSeek / OpenAI 响应格式：{"choices": [{"message": {"content": "..."}}]}
            return extractChatCompletionResponse(responseBody);
        }
    }
    
    /**
     * 提取 Ollama API 响应内容
     */
    private String extractOllamaResponse(String responseBody) {
        try {
            // 简单的 JSON 解析（实际项目中可以使用 Jackson 或 Gson）
            int responseStart = responseBody.indexOf("\"response\":\"") + 12;
            int responseEnd = responseBody.lastIndexOf("\"");
            if (responseStart > 11 && responseEnd > responseStart) {
                String content = responseBody.substring(responseStart, responseEnd);
                // 处理转义字符
                return content.replace("\\n", "\n")
                             .replace("\\\"", "\"")
                             .replace("\\\\", "\\");
            }
            return "错误：无法解析 Ollama API 响应。响应内容：\n" + responseBody;
        } catch (Exception e) {
            return "错误：解析 Ollama API 响应时发生异常：\n" + e.getMessage();
        }
    }
    
    /**
     * 提取 Chat Completions API 响应内容（DeepSeek / OpenAI）
     */
    private String extractChatCompletionResponse(String responseBody) {
        try {
            // 查找 choices[0].message.content
            int choicesStart = responseBody.indexOf("\"choices\":");
            if (choicesStart == -1) {
                return "错误：响应格式不正确。响应内容：\n" + responseBody;
            }
            
            int contentStart = responseBody.indexOf("\"content\":\"", choicesStart) + 11;
            if (contentStart == 10) {
                return "错误：响应中未找到 content 字段。响应内容：\n" + responseBody;
            }
            
            // 查找 content 字符串的结束位置（考虑转义）
            int contentEnd = contentStart;
            boolean escaped = false;
            for (int i = contentStart; i < responseBody.length(); i++) {
                char c = responseBody.charAt(i);
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (c == '\\') {
                    escaped = true;
                    continue;
                }
                if (c == '"') {
                    // 检查是否是字符串结束（下一个字符应该是 , 或 }）
                    if (i + 1 < responseBody.length()) {
                        char next = responseBody.charAt(i + 1);
                        if (next == ',' || next == '}') {
                            contentEnd = i;
                            break;
                        }
                    }
                }
            }
            
            if (contentEnd <= contentStart) {
                return "错误：无法定位响应内容的结束位置。响应内容：\n" + responseBody;
            }
            
            String content = responseBody.substring(contentStart, contentEnd);
            // 处理转义字符
            return content.replace("\\n", "\n")
                         .replace("\\\"", "\"")
                         .replace("\\\\", "\\")
                         .replace("\\r", "\r")
                         .replace("\\t", "\t");
            
        } catch (Exception e) {
            return "错误：解析 API 响应时发生异常：\n" + e.getMessage() + 
                   "\n\n响应内容：\n" + responseBody;
        }
    }
    
    /**
     * 生成会话分析报告
     * 
     * <p>对指定的监控会话进行深度分析，包括流量趋势评估、潜在风险识别和优化建议。</p>
     * 
     * <p>执行流程：</p>
     * <ol>
     *   <li>验证输入参数（会话和记录列表）</li>
     *   <li>对记录进行降采样处理（如果记录数过多）</li>
     *   <li>构建会话概况信息</li>
     *   <li>格式化采样数据</li>
     *   <li>调用 AI API 进行分析</li>
     *   <li>返回 Markdown 格式的分析报告</li>
     * </ol>
     * 
     * @param session 监控会话对象
     * @param records 该会话的所有流量明细记录
     * @return CompletableFuture<String> 异步返回 Markdown 格式的分析报告
     */
    public CompletableFuture<String> generateSessionReport(MonitoringSession session, List<TrafficRecord> records) {
        // 检查配置
        if (config == null || config.getApiEndpoint() == null || config.getApiEndpoint().isEmpty()) {
            return CompletableFuture.completedFuture(
                "**错误：AI 服务未配置**\n\n请在配置文件中设置 API 端点和密钥。"
            );
        }
        
        // 验证输入参数
        if (session == null) {
            return CompletableFuture.completedFuture(
                "**错误：会话对象为空**\n\n无法生成分析报告。"
            );
        }
        
        if (records == null || records.isEmpty()) {
            return CompletableFuture.completedFuture(
                "**提示：没有流量数据可供分析**\n\n该会话没有记录任何流量明细数据，无法进行分析。"
            );
        }
        
        // 在后台线程中执行分析
        return CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("[AIService] 开始生成会话分析报告:");
                System.out.println("  Session ID: " + session.getSessionId());
                System.out.println("  Records count: " + records.size());
                
                // 对记录进行降采样（如果记录数过多）
                List<TrafficRecord> sampledRecords = downsampleRecords(records, 100);
                System.out.println("  Sampled records count: " + sampledRecords.size());
                
                // 构建会话概况信息
                String sessionOverview = buildSessionOverview(session, records);
                
                // 格式化采样数据
                String sampledDataJson = formatSampledRecordsToJson(sampledRecords);
                
                // 构建用户提示词
                String userPrompt = buildSessionAnalysisPrompt(sessionOverview, sampledDataJson, records.size(), sampledRecords.size());
                
                // 根据 API 提供商构建请求体（使用会话分析专用的 System Prompt）
                String requestBody = buildRequestBodyWithSystemPrompt(userPrompt, SESSION_ANALYSIS_SYSTEM_PROMPT);
                
                // 构建 HTTP 请求
                HttpRequest request = buildHttpRequest(requestBody);
                
                System.out.println("[AIService] 正在发送会话分析请求...");
                
                // 发送异步请求并获取响应
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                System.out.println("[AIService] 收到响应: HTTP " + response.statusCode());
                
                // 解析响应
                return parseResponse(response);
                
            } catch (Exception e) {
                System.err.println("[AIService] 生成会话分析报告时发生异常: " + e.getClass().getName());
                e.printStackTrace();
                return "**错误：分析过程中发生异常**\n\n" +
                       "异常类型: " + e.getClass().getSimpleName() + "\n" +
                       "错误消息: " + e.getMessage() + "\n\n" +
                       "请检查：\n" +
                       "1. API 配置是否正确（端点、密钥、模型）\n" +
                       "2. API 密钥是否有效\n" +
                       "3. 网络连接是否正常";
            }
        });
    }
    
    /**
     * 数据降采样：从大量记录中抽取关键数据点
     * 
     * <p>降采样策略：</p>
     * <ul>
     *   <li>保留前 10% 和后 10% 的记录（时间序列的首尾）</li>
     *   <li>识别流量峰值点（上行和下行速度最大的前 10 个点）</li>
     *   <li>识别流量低谷点（速度最小的前 10 个点）</li>
     *   <li>均匀采样中间部分的数据</li>
     * </ul>
     * 
     * @param records 原始记录列表（应该按时间排序）
     * @param maxSamples 最大采样数量
     * @return 降采样后的记录列表
     */
    private List<TrafficRecord> downsampleRecords(List<TrafficRecord> records, int maxSamples) {
        if (records.size() <= maxSamples) {
            return new ArrayList<>(records); // 如果记录数不超过最大值，直接返回
        }
        
        List<TrafficRecord> sampled = new ArrayList<>();
        
        // 1. 保留前 10% 和后 10% 的记录
        int headCount = Math.max(1, records.size() / 10);
        int tailCount = Math.max(1, records.size() / 10);
        
        sampled.addAll(records.subList(0, headCount)); // 前 10%
        sampled.addAll(records.subList(records.size() - tailCount, records.size())); // 后 10%
        
        // 2. 识别峰值点（下行速度最大的前 10 个点）
        List<TrafficRecord> sortedByDownSpeed = new ArrayList<>(records);
        sortedByDownSpeed.sort(Comparator.comparingDouble(TrafficRecord::getDownSpeed).reversed());
        for (int i = 0; i < Math.min(10, sortedByDownSpeed.size()); i++) {
            TrafficRecord record = sortedByDownSpeed.get(i);
            if (!sampled.contains(record)) {
                sampled.add(record);
            }
        }
        
        // 3. 识别峰值点（上行速度最大的前 10 个点）
        List<TrafficRecord> sortedByUpSpeed = new ArrayList<>(records);
        sortedByUpSpeed.sort(Comparator.comparingDouble(TrafficRecord::getUpSpeed).reversed());
        for (int i = 0; i < Math.min(10, sortedByUpSpeed.size()); i++) {
            TrafficRecord record = sortedByUpSpeed.get(i);
            if (!sampled.contains(record)) {
                sampled.add(record);
            }
        }
        
        // 4. 识别低谷点（总速度最小的前 10 个点）
        List<TrafficRecord> sortedByLowSpeed = new ArrayList<>(records);
        sortedByLowSpeed.sort(Comparator.comparingDouble(r -> r.getDownSpeed() + r.getUpSpeed()));
        for (int i = 0; i < Math.min(10, sortedByLowSpeed.size()); i++) {
            TrafficRecord record = sortedByLowSpeed.get(i);
            if (!sampled.contains(record)) {
                sampled.add(record);
            }
        }
        
        // 5. 如果采样数量仍不足，均匀采样中间部分的数据
        if (sampled.size() < maxSamples) {
            int middleStart = headCount;
            int middleEnd = records.size() - tailCount;
            int middleSize = middleEnd - middleStart;
            int needed = maxSamples - sampled.size();
            
            if (middleSize > 0) {
                int step = Math.max(1, middleSize / needed);
                for (int i = middleStart; i < middleEnd && sampled.size() < maxSamples; i += step) {
                    TrafficRecord record = records.get(i);
                    if (!sampled.contains(record)) {
                        sampled.add(record);
                    }
                }
            }
        }
        
        // 6. 按时间重新排序
        sampled.sort(Comparator.comparing(TrafficRecord::getRecordTime));
        
        // 7. 如果仍然超过最大值，截取前 maxSamples 个
        if (sampled.size() > maxSamples) {
            sampled = sampled.subList(0, maxSamples);
        }
        
        return sampled;
    }
    
    /**
     * 构建会话概况信息
     * 
     * @param session 监控会话
     * @param records 记录列表（用于计算额外统计信息）
     * @return 格式化的会话概况字符串
     */
    private String buildSessionOverview(MonitoringSession session, List<TrafficRecord> records) {
        StringBuilder overview = new StringBuilder();
        
        overview.append("**会话概况**\n\n");
        overview.append("- **会话 ID**: ").append(session.getSessionId()).append("\n");
        overview.append("- **网卡名称**: ").append(session.getIfaceName()).append("\n");
        overview.append("- **开始时间**: ").append(formatTimestamp(session.getStartTime())).append("\n");
        
        if (session.getEndTime() != null) {
            overview.append("- **结束时间**: ").append(formatTimestamp(session.getEndTime())).append("\n");
            overview.append("- **持续时间**: ").append(session.getDurationSeconds()).append(" 秒 (")
                    .append(String.format("%.1f", session.getDurationSeconds() / 60.0)).append(" 分钟)\n");
        } else {
            overview.append("- **状态**: 进行中\n");
        }
        
        overview.append("- **记录总数**: ").append(session.getRecordCount()).append(" 条\n");
        overview.append("- **平均下行速度**: ").append(String.format("%.2f", session.getAvgDownSpeed())).append(" KB/s\n");
        overview.append("- **平均上行速度**: ").append(String.format("%.2f", session.getAvgUpSpeed())).append(" KB/s\n");
        overview.append("- **最大下行速度**: ").append(String.format("%.2f", session.getMaxDownSpeed())).append(" KB/s\n");
        overview.append("- **最大上行速度**: ").append(String.format("%.2f", session.getMaxUpSpeed())).append(" KB/s\n");
        
        // 计算总流量
        long totalDownBytes = session.getTotalDownBytes();
        long totalUpBytes = session.getTotalUpBytes();
        overview.append("- **总下行流量**: ").append(formatBytes(totalDownBytes)).append("\n");
        overview.append("- **总上行流量**: ").append(formatBytes(totalUpBytes)).append("\n");
        
        // 统计唯一 IP 地址和进程数量
        long uniqueSourceIps = records.stream()
                .map(TrafficRecord::getSourceIp)
                .filter(ip -> ip != null && !ip.trim().isEmpty())
                .distinct()
                .count();
        
        long uniqueDestIps = records.stream()
                .map(TrafficRecord::getDestIp)
                .filter(ip -> ip != null && !ip.trim().isEmpty())
                .distinct()
                .count();
        
        long uniqueProcesses = records.stream()
                .map(TrafficRecord::getProcessName)
                .filter(p -> p != null && !p.trim().isEmpty())
                .distinct()
                .count();
        
        overview.append("- **唯一源IP数**: ").append(uniqueSourceIps).append("\n");
        overview.append("- **唯一目标IP数**: ").append(uniqueDestIps).append("\n");
        overview.append("- **关联进程数**: ").append(uniqueProcesses).append("\n");
        
        return overview.toString();
    }
    
    /**
     * 格式化时间戳
     */
    private String formatTimestamp(java.sql.Timestamp timestamp) {
        if (timestamp == null) {
            return "未知";
        }
        return timestamp.toLocalDateTime().format(TIME_FORMATTER);
    }
    
    /**
     * 格式化字节数（转换为 KB/MB/GB）
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    /**
     * 格式化采样记录为 JSON
     * 
     * @param sampledRecords 采样后的记录列表
     * @return JSON 格式的字符串
     */
    private String formatSampledRecordsToJson(List<TrafficRecord> sampledRecords) {
        String jsonArray = sampledRecords.stream()
                .map(record -> String.format(
                    """
                    {
                      "downSpeed": %.2f,
                      "upSpeed": %.2f,
                      "sourceIp": "%s",
                      "destIp": "%s",
                      "processName": "%s",
                      "recordTime": "%s"
                    }""",
                    record.getDownSpeed(),
                    record.getUpSpeed(),
                    escapeJsonValue(record.getSourceIp()),
                    escapeJsonValue(record.getDestIp()),
                    escapeJsonValue(record.getProcessName()),
                    formatTimestamp(record.getRecordTime())
                ))
                .collect(Collectors.joining(",\n    ", "[\n    ", "\n  ]"));
        
        return String.format("""
            {
              "sampledRecords": %s,
              "sampleCount": %d
            }""",
            jsonArray,
            sampledRecords.size()
        );
    }
    
    /**
     * 转义 JSON 值中的特殊字符
     */
    private String escapeJsonValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
    
    /**
     * 构建会话分析的用户提示词
     */
    private String buildSessionAnalysisPrompt(String sessionOverview, String sampledDataJson, int totalRecords, int sampledRecords) {
        return String.format(
            """
            请分析以下监控会话的网络行为数据。
            
            %s
            
            **明细采样数据**（共 %d 条记录，已采样 %d 条关键数据点）：
            
            %s
            
            请生成一份详细的网络行为分析报告，包含流量趋势评估、潜在风险识别和优化建议。
            """,
            sessionOverview,
            totalRecords,
            sampledRecords,
            sampledDataJson
        );
    }
    
    /**
     * 使用指定的 System Prompt 构建请求体
     */
    private String buildRequestBodyWithSystemPrompt(String userPrompt, String systemPrompt) {
        String provider = config.getProvider();
        
        if ("ollama".equalsIgnoreCase(provider)) {
            // Ollama API 格式
            return String.format(
                """
                {
                  "model": "%s",
                  "prompt": "%s",
                  "stream": false
                }""",
                config.getModel(),
                escapeJsonString(buildOllamaPromptWithSystemPrompt(userPrompt, systemPrompt))
            );
        } else {
            // DeepSeek / OpenAI API 格式（Chat Completions API）
            return String.format(
                """
                {
                  "model": "%s",
                  "messages": [
                    {
                      "role": "system",
                      "content": "%s"
                    },
                    {
                      "role": "user",
                      "content": "%s"
                    }
                  ],
                  "temperature": 0.7,
                  "max_tokens": 3000
                }""",
                config.getModel(),
                escapeJsonString(systemPrompt),
                escapeJsonString(userPrompt)
            );
        }
    }
    
    /**
     * 构建 Ollama 提示词（将系统提示和用户提示合并）
     */
    private String buildOllamaPromptWithSystemPrompt(String userPrompt, String systemPrompt) {
        return systemPrompt + "\n\n" + userPrompt;
    }
}

