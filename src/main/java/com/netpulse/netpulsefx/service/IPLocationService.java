package com.netpulse.netpulsefx.service;

import com.netpulse.netpulsefx.model.IPLocationInfo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IP 地理位置查询服务
 * 
 * <p>功能说明：</p>
 * <ul>
 *   <li>通过 HttpClient 调用在线 API（ip-api.com）查询 IP 地理位置</li>
 *   <li>使用本地缓存（ConcurrentHashMap）避免重复查询同一 IP</li>
 *   <li>处理网络异常和 API 频率限制，返回"未知位置"而不影响主流程</li>
 * </ul>
 * 
 * <p>API 说明：</p>
 * <ul>
 *   <li>使用 ip-api.com 的免费 API</li>
 *   <li>免费版本限制：每分钟 45 次请求</li>
 *   <li>响应格式：JSON</li>
 * </ul>
 * 
 * @author NetPulseFX Team
 */
public class IPLocationService {
    
    /** API 基础 URL */
    private static final String API_BASE_URL = "http://ip-api.com/json/";
    
    /** HTTP 客户端（单例，线程安全） */
    private final HttpClient httpClient;
    
    /** 本地缓存：IP -> IPLocationInfo（线程安全的 Map） */
    private final Map<String, IPLocationInfo> cache;
    
    /** 请求超时时间（秒） */
    private static final int TIMEOUT_SECONDS = 5;
    
    /** JSON 字段解析模式（分别匹配每个字段，不依赖顺序） */
    private static final Pattern COUNTRY_PATTERN = Pattern.compile("\"country\":\"([^\"]*)\"");
    private static final Pattern REGION_PATTERN = Pattern.compile("\"regionName\":\"([^\"]*)\"");
    private static final Pattern CITY_PATTERN = Pattern.compile("\"city\":\"([^\"]*)\"");
    private static final Pattern ISP_PATTERN = Pattern.compile("\"isp\":\"([^\"]*)\"");
    private static final Pattern COUNTRY_CODE_PATTERN = Pattern.compile("\"countryCode\":\"([^\"]*)\"");
    private static final Pattern STATUS_PATTERN = Pattern.compile("\"status\":\"([^\"]*)\"");
    
    /** 单例实例 */
    private static IPLocationService instance;
    
    /**
     * 私有构造函数（单例模式）
     */
    private IPLocationService() {
        // 创建 HTTP 客户端，设置超时时间
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
        
        // 使用 ConcurrentHashMap 确保线程安全
        this.cache = new ConcurrentHashMap<>();
    }
    
    /**
     * 获取服务实例（单例模式）
     * 
     * @return IPLocationService 实例
     */
    public static synchronized IPLocationService getInstance() {
        if (instance == null) {
            instance = new IPLocationService();
        }
        return instance;
    }
    
    /**
     * 查询 IP 地理位置信息
     * 
     * <p>执行流程：</p>
     * <ol>
     *   <li>检查缓存，如果已存在则直接返回</li>
     *   <li>调用 API 查询 IP 地理位置</li>
     *   <li>解析 JSON 响应</li>
     *   <li>将结果存入缓存</li>
     *   <li>返回地理位置信息</li>
     * </ol>
     * 
     * <p>异常处理：</p>
     * <ul>
     *   <li>网络异常：返回"未知位置"</li>
     *   <li>API 频率限制：返回"未知位置"</li>
     *   <li>解析错误：返回"未知位置"</li>
     * </ul>
     * 
     * @param ip IP 地址（IPv4 或 IPv6）
     * @return IPLocationInfo 对象，包含地理位置信息
     */
    public IPLocationInfo queryLocation(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            System.out.println("[IPLocationService] IP地址为空，无法查询");
            return IPLocationInfo.unknown(ip != null ? ip : "null");
        }
        
        // 规范化 IP 地址（去除空白字符）
        ip = ip.trim();
        
        // 检查缓存
        IPLocationInfo cached = cache.get(ip);
        if (cached != null) {
            System.out.println("[IPLocationService] 从缓存获取IP: " + ip);
            return cached;
        }
        
        // 验证 IP 地址格式（简单验证）
        if (!isValidIP(ip)) {
            String reason = isPrivateIP(ip) ? "私有IP地址无法查询地理位置" : "IP地址格式无效";
            System.out.println("[IPLocationService] " + reason + ": " + ip);
            IPLocationInfo unknown = new IPLocationInfo(ip, reason);
            cache.put(ip, unknown); // 缓存失败结果，避免重复查询无效 IP
            return unknown;
        }
        
        System.out.println("[IPLocationService] 开始查询IP地理位置: " + ip);
        
        try {
            // 构建 API 请求 URL
            String apiUrl = API_BASE_URL + ip;
            URI uri = URI.create(apiUrl);
            
            // 创建 HTTP 请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .GET()
                    .build();
            
            // 发送请求并获取响应
            HttpResponse<String> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofString());
            
            // 检查 HTTP 状态码
            if (response.statusCode() != 200) {
                // API 返回错误（可能是频率限制）
                IPLocationInfo unknown = IPLocationInfo.unknown(ip);
                cache.put(ip, unknown);
                return unknown;
            }
            
            // 解析 JSON 响应
            IPLocationInfo locationInfo = parseJsonResponse(ip, response.body());
            
            // 存入缓存
            cache.put(ip, locationInfo);
            
            return locationInfo;
            
        } catch (java.net.http.HttpTimeoutException e) {
            // 请求超时
            System.err.println("[IPLocationService] 查询 IP " + ip + " 超时: " + e.getMessage());
            IPLocationInfo unknown = IPLocationInfo.unknown(ip);
            cache.put(ip, unknown);
            return unknown;
            
        } catch (java.net.ConnectException e) {
            // 网络连接异常
            System.err.println("[IPLocationService] 无法连接到 API 服务器: " + e.getMessage());
            IPLocationInfo unknown = IPLocationInfo.unknown(ip);
            cache.put(ip, unknown);
            return unknown;
            
        } catch (Exception e) {
            // 其他异常（包括解析错误、IO 异常等）
            System.err.println("[IPLocationService] 查询 IP " + ip + " 时发生错误: " + e.getMessage());
            IPLocationInfo unknown = IPLocationInfo.unknown(ip);
            cache.put(ip, unknown);
            return unknown;
        }
    }
    
    /**
     * 异步查询 IP 地理位置信息
     * 
     * <p>在后台线程中执行查询，不阻塞调用线程。</p>
     * 
     * @param ip IP 地址
     * @return CompletableFuture<IPLocationInfo> 异步结果
     */
    public java.util.concurrent.CompletableFuture<IPLocationInfo> queryLocationAsync(String ip) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> queryLocation(ip));
    }
    
    /**
     * 解析 JSON 响应
     * 
     * <p>使用正则表达式分别解析每个 JSON 字段，不依赖字段顺序。</p>
     * <p>注意：对于复杂的 JSON，建议使用专门的 JSON 库（如 Jackson 或 Gson）。</p>
     * 
     * @param ip IP 地址
     * @param jsonResponse JSON 响应字符串
     * @return IPLocationInfo 对象
     */
    private IPLocationInfo parseJsonResponse(String ip, String jsonResponse) {
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            return IPLocationInfo.unknown(ip);
        }
        
        try {
            // 首先检查状态字段
            Matcher statusMatcher = STATUS_PATTERN.matcher(jsonResponse);
            if (statusMatcher.find()) {
                String status = statusMatcher.group(1);
                if (!"success".equals(status)) {
                    // API 返回失败（可能是无效 IP 或频率限制）
                    return IPLocationInfo.unknown(ip);
                }
            } else {
                // 如果没有找到 status 字段，检查是否有 fail 状态
                if (jsonResponse.contains("\"status\":\"fail\"")) {
                    return IPLocationInfo.unknown(ip);
                }
            }
            
            // 分别解析各个字段（不依赖顺序）
            String country = extractJsonField(jsonResponse, COUNTRY_PATTERN);
            String region = extractJsonField(jsonResponse, REGION_PATTERN);
            String city = extractJsonField(jsonResponse, CITY_PATTERN);
            String isp = extractJsonField(jsonResponse, ISP_PATTERN);
            String countryCode = extractJsonField(jsonResponse, COUNTRY_CODE_PATTERN);
            
            // 创建成功的地理位置信息对象
            return new IPLocationInfo(ip, country, city, isp, region, countryCode);
            
        } catch (Exception e) {
            System.err.println("[IPLocationService] 解析 JSON 响应失败: " + e.getMessage());
            return IPLocationInfo.unknown(ip);
        }
    }
    
    /**
     * 从 JSON 字符串中提取指定字段的值
     * 
     * @param json JSON 字符串
     * @param pattern 匹配模式
     * @return 字段值，如果未找到则返回 null
     */
    private String extractJsonField(String json, Pattern pattern) {
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    /**
     * 验证 IP 地址格式（简单验证）
     * 
     * @param ip IP 地址字符串
     * @return true 如果格式看起来有效
     */
    private boolean isValidIP(String ip) {
        if (ip == null || ip.isEmpty() || ip.trim().isEmpty()) {
            return false;
        }
        
        ip = ip.trim();
        
        // IPv4 格式验证（简单检查）
        if (ip.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
            // 进一步验证是否为本地/私有IP
            if (isPrivateIP(ip)) {
                System.out.println("[IPLocationService] 跳过私有IP地址: " + ip);
                return false; // 私有IP无法查询地理位置
            }
            return true;
        }
        
        // IPv6 格式验证（简单检查，包含冒号）
        if (ip.contains(":") && ip.matches("^[0-9a-fA-F:]+$")) {
            // IPv6 本地地址检查
            if (ip.startsWith("::1") || ip.startsWith("fe80:")) {
                System.out.println("[IPLocationService] 跳过IPv6本地地址: " + ip);
                return false;
            }
            return true;
        }
        
        return false;
    }
    
    /**
     * 检查是否为私有IP地址（无法查询地理位置）
     * 
     * @param ip IPv4 地址
     * @return true 如果是私有IP
     */
    private boolean isPrivateIP(String ip) {
        if (ip == null || ip.isEmpty()) {
            return true;
        }
        
        // 回环地址
        if (ip.equals("127.0.0.1") || ip.startsWith("127.")) {
            return true;
        }
        
        // 本地链路地址
        if (ip.startsWith("169.254.")) {
            return true;
        }
        
        // 私有地址范围
        // 10.0.0.0 - 10.255.255.255
        if (ip.startsWith("10.")) {
            return true;
        }
        
        // 172.16.0.0 - 172.31.255.255
        if (ip.matches("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) {
            return true;
        }
        
        // 192.168.0.0 - 192.168.255.255
        if (ip.startsWith("192.168.")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 清空缓存
     * 用于测试或需要强制重新查询的场景
     */
    public void clearCache() {
        cache.clear();
    }
    
    /**
     * 获取缓存大小
     * 
     * @return 缓存中 IP 数量
     */
    public int getCacheSize() {
        return cache.size();
    }
    
    /**
     * 检查缓存中是否包含指定 IP
     * 
     * @param ip IP 地址
     * @return true 如果缓存中存在
     */
    public boolean isCached(String ip) {
        return cache.containsKey(ip);
    }
}

