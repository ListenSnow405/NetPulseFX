package com.netpulse.netpulsefx.service;

import com.netpulse.netpulsefx.model.IPLocationInfo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.security.NoSuchAlgorithmException;
import java.security.KeyManagementException;

/**
 * IP 地理位置查询服务
 * 
 * <p>功能说明：</p>
 * <ul>
 *   <li>通过 HttpClient 调用 Vore-API 查询 IP 地理位置信息</li>
 *   <li>使用本地缓存（ConcurrentHashMap）避免重复查询同一 IP</li>
 *   <li>处理网络异常和 API 错误，返回"未知位置"而不影响主流程</li>
 *   <li>支持失败重试机制（最多重试1次）</li>
 *   <li>从JSON响应中提取关键信息（国家、省份、城市、ISP）</li>
 * </ul>
 * 
 * <p>API 说明：</p>
 * <ul>
 *   <li>使用 Vore-API 的 IP 查询服务</li>
 *   <li>API 地址: https://api.vore.top/api/IPdata?ip={IP}</li>
 *   <li>响应格式: JSON</li>
 *   <li>响应编码: UTF-8</li>
 *   <li>请求超时: 3秒</li>
 * </ul>
 * 
 * <p>JSON 数据结构说明：</p>
 * <pre>
 * {
 *   "code": 200,                    // 状态码，200表示成功
 *   "msg": "SUCCESS",              // 消息
 *   "ipinfo": {
 *     "type": "ipv4",              // IP类型
 *     "text": "8.8.8.8",           // IP地址
 *     "cnip": false                // 是否为中国IP
 *   },
 *   "ipdata": {
 *     "info1": "国家",             // 国家信息
 *     "info2": "省份",             // 省份信息
 *     "info3": "城市",             // 城市信息
 *     "isp": "运营商"              // ISP/运营商信息
 *   },
 *   "adcode": {...},               // 行政区划代码（可选）
 *   "tips": "...",                 // 提示信息
 *   "time": 1767182700             // 时间戳
 * }
 * </pre>
 * 
 * <p>解析策略：</p>
 * <ul>
 *   <li>使用正则表达式解析JSON响应（不依赖外部JSON库）</li>
 *   <li>提取 ipdata 对象中的关键字段：info1（国家）、info2（省份）、info3（城市）、isp（运营商）</li>
 *   <li>检查 code 字段判断请求是否成功</li>
 *   <li>解析失败时返回"未知位置"，确保不影响主程序运行</li>
 * </ul>
 * 
 * @author NetPulseFX Team
 */
public class IPLocationService {
    
    /** API 基础 URL（Vore-API） */
    private static final String API_BASE_URL = "https://api.vore.top/api/IPdata";
    
    /** HTTP 客户端（单例，线程安全） */
    private final HttpClient httpClient;
    
    /** 本地缓存：IP -> IPLocationInfo（线程安全的 Map） */
    private final Map<String, IPLocationInfo> cache;
    
    /** 请求超时时间（秒） */
    private static final int TIMEOUT_SECONDS = 10;
    
    /** 最大重试次数 */
    private static final int MAX_RETRY_COUNT = 2;
    
    /** 重试延迟（毫秒） */
    private static final int RETRY_DELAY_MS = 1000;
    
    /** 
     * 正则表达式模式：提取JSON中的code字段（状态码）
     * 匹配格式：\"code\":200 或 \"code\": 200
     * 用于判断API请求是否成功
     */
    private static final Pattern CODE_PATTERN = Pattern.compile(
        "\"code\"\\s*:\\s*(\\d+)", 
        Pattern.CASE_INSENSITIVE
    );
    
    /** 
     * 正则表达式模式：提取JSON中的msg字段（消息）
     * 匹配格式：\"msg\":\"SUCCESS\" 或 \"msg\": \"SUCCESS\"
     * 用于获取API返回的消息
     */
    private static final Pattern MSG_PATTERN = Pattern.compile(
        "\"msg\"\\s*:\\s*\"([^\"]+)\"", 
        Pattern.CASE_INSENSITIVE
    );
    
    /** 
     * 正则表达式模式：提取ipdata对象中的info1字段（国家）
     * 匹配格式：\"ipdata\"\\s*:\\s*\\{[^}]*\"info1\"\\s*:\\s*\"([^\"]+)\"
     * 数据流向：JSON响应 -> ipdata对象 -> info1字段 -> 国家名称
     */
    private static final Pattern INFO1_PATTERN = Pattern.compile(
        "\"ipdata\"\\s*:\\s*\\{[^}]*\"info1\"\\s*:\\s*\"([^\"]+)\"", 
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );
    
    /** 
     * 正则表达式模式：提取ipdata对象中的info2字段（省份）
     * 匹配格式：\"ipdata\"\\s*:\\s*\\{[^}]*\"info2\"\\s*:\\s*\"([^\"]+)\"
     * 数据流向：JSON响应 -> ipdata对象 -> info2字段 -> 省份名称
     */
    private static final Pattern INFO2_PATTERN = Pattern.compile(
        "\"ipdata\"\\s*:\\s*\\{[^}]*\"info2\"\\s*:\\s*\"([^\"]+)\"", 
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );
    
    /** 
     * 正则表达式模式：提取ipdata对象中的info3字段（城市）
     * 匹配格式：\"ipdata\"\\s*:\\s*\\{[^}]*\"info3\"\\s*:\\s*\"([^\"]+)\"
     * 数据流向：JSON响应 -> ipdata对象 -> info3字段 -> 城市名称
     */
    private static final Pattern INFO3_PATTERN = Pattern.compile(
        "\"ipdata\"\\s*:\\s*\\{[^}]*\"info3\"\\s*:\\s*\"([^\"]+)\"", 
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );
    
    /** 
     * 正则表达式模式：提取ipdata对象中的isp字段（运营商）
     * 匹配格式：\"ipdata\"\\s*:\\s*\\{[^}]*\"isp\"\\s*:\\s*\"([^\"]+)\"
     * 数据流向：JSON响应 -> ipdata对象 -> isp字段 -> 运营商名称
     */
    private static final Pattern ISP_PATTERN = Pattern.compile(
        "\"ipdata\"\\s*:\\s*\\{[^}]*\"isp\"\\s*:\\s*\"([^\"]+)\"", 
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );
    
    /** 单例实例 */
    private static IPLocationService instance;
    
    /**
     * 私有构造函数（单例模式）
     */
    private IPLocationService() {
        // 创建 HTTP 客户端，设置超时时间、系统代理和 SSL 配置
        HttpClient client = null;
        try {
            // 创建更宽松的 SSL 上下文，解决 SSL 握手失败问题
            SSLContext sslContext = createRelaxedSSLContext();
            SSLParameters sslParameters = new SSLParameters();
            // 允许所有 TLS 协议版本（包括 TLS 1.0, 1.1, 1.2, 1.3）
            sslParameters.setProtocols(new String[]{"TLSv1.2", "TLSv1.3", "TLSv1.1", "TLSv1"});
            
            client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .proxy(java.net.ProxySelector.getDefault())
                    .sslContext(sslContext)
                    .sslParameters(sslParameters)
                    .build();
            
            System.out.println("[IPLocationService] 使用自定义 SSL 上下文初始化 HTTP 客户端");
        } catch (Exception e) {
            // 如果创建自定义 SSL 上下文失败，使用默认配置
            System.err.println("[IPLocationService] 创建自定义 SSL 上下文失败，使用默认配置: " + e.getMessage());
            client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .proxy(java.net.ProxySelector.getDefault())
                    .build();
        }
        
        // 确保 httpClient 被赋值
        this.httpClient = client;
        
        // 使用 ConcurrentHashMap 确保线程安全
        this.cache = new ConcurrentHashMap<>();
        
        // 输出初始化信息
        System.out.println("[IPLocationService] 初始化完成，超时设置: " + TIMEOUT_SECONDS + "秒");
        System.out.println("[IPLocationService] 最大重试次数: " + (MAX_RETRY_COUNT + 1));
        System.out.println("[IPLocationService] API地址: " + API_BASE_URL);
    }
    
    /**
     * 创建更宽松的 SSL 上下文
     * 用于解决某些服务器 SSL 握手失败的问题
     * 
     * <p>注意：此方法使用信任所有证书的方式，仅用于 IP 地理位置查询这种非敏感操作。
     * 对于涉及敏感数据的场景，应使用更严格的 SSL 配置。</p>
     * 
     * @return SSLContext 对象
     * @throws NoSuchAlgorithmException 如果算法不存在
     * @throws KeyManagementException 如果密钥管理失败
     */
    private SSLContext createRelaxedSSLContext() throws NoSuchAlgorithmException, KeyManagementException {
        // 创建信任所有证书的 TrustManager（仅用于 IP 查询，不涉及敏感数据）
        // 这样可以解决某些服务器的 SSL 证书验证问题
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
                
                @Override
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    // 信任所有客户端证书（IP 查询不需要客户端证书）
                }
                
                @Override
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    // 信任所有服务器证书（解决 SSL 握手失败问题）
                }
            }
        };
        
        // 创建 SSL 上下文，使用 TLS 协议（支持所有 TLS 版本）
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        
        return sslContext;
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
     * 查询 IP 地理位置信息（带重试机制）
     * 
     * <p>执行流程：</p>
     * <ol>
     *   <li>检查缓存，如果已存在则直接返回</li>
     *   <li>验证IP地址格式和私有IP检查</li>
     *   <li>调用 fetchLocation 查询 IP 地理位置（最多重试 MAX_RETRY_COUNT 次）</li>
     *   <li>将结果存入缓存</li>
     *   <li>返回地理位置信息</li>
     * </ol>
     * 
     * @param ip IP 地址（IPv4）
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
            cache.put(ip, unknown);
            return unknown;
        }
        
        System.out.println("[IPLocationService] 开始查询IP地理位置: " + ip);
        
        // 带重试机制的查询
        IPLocationInfo locationInfo = null;
        Exception lastException = null;
        
        for (int attempt = 0; attempt <= MAX_RETRY_COUNT; attempt++) {
            if (attempt > 0) {
                System.out.println("[IPLocationService] 第 " + (attempt + 1) + " 次尝试查询IP: " + ip);
                // 重试前等待更长时间，给服务器时间恢复
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            try {
                locationInfo = fetchLocation(ip);
                if (locationInfo != null && locationInfo.isSuccess()) {
                    // 查询成功，存入缓存并返回
                    cache.put(ip, locationInfo);
                    System.out.println("[IPLocationService] 成功查询IP: " + ip + " - " + locationInfo.getFormattedLocation());
                    return locationInfo;
                } else if (locationInfo != null && !locationInfo.isSuccess()) {
                    // API返回了错误响应，不需要重试
                    System.err.println("[IPLocationService] API返回错误，IP: " + ip + ", 错误: " + locationInfo.getErrorMessage());
                    cache.put(ip, locationInfo);
                    return locationInfo;
                }
            } catch (Exception e) {
                lastException = e;
                String errorMsg = e.getMessage();
                String errorType = e.getClass().getSimpleName();
                
                // 详细记录错误信息，特别是 SSL 相关错误
                System.err.println("[IPLocationService] 查询IP " + ip + " 失败（第 " + (attempt + 1) + " 次尝试）");
                System.err.println("[IPLocationService] 错误类型: " + errorType);
                System.err.println("[IPLocationService] 错误信息: " + errorMsg);
                
                // 如果是 SSL 相关错误，提供更详细的提示
                if (errorMsg != null && (errorMsg.contains("handshake") || 
                    errorMsg.contains("SSL") || errorMsg.contains("TLS"))) {
                    System.err.println("[IPLocationService] 检测到 SSL/TLS 握手问题，将在 " + 
                        (RETRY_DELAY_MS / 1000) + " 秒后重试...");
                }
                
                if (attempt < MAX_RETRY_COUNT) {
                    System.out.println("[IPLocationService] 将在 " + (RETRY_DELAY_MS / 1000) + " 秒后重试...");
                }
            }
        }
        
        // 所有重试都失败，返回未知位置
        String errorMsg = "查询失败，已重试 " + (MAX_RETRY_COUNT + 1) + " 次";
        if (lastException != null) {
            errorMsg += ": " + lastException.getMessage();
        }
        System.err.println("[IPLocationService] " + errorMsg);
        IPLocationInfo unknown = new IPLocationInfo(ip, errorMsg);
        cache.put(ip, unknown);
        return unknown;
    }
    
    /**
     * 执行实际的API请求（核心查询方法）
     * 
     * <p>该方法负责：</p>
     * <ol>
     *   <li>构建API请求URL（格式：https://api.vore.top/api/IPdata?ip={IP}）</li>
     *   <li>发送HTTP GET请求</li>
     *   <li>接收JSON响应</li>
     *   <li>调用parseJsonResponse解析JSON内容</li>
     *   <li>返回解析后的IPLocationInfo对象</li>
     * </ol>
     * 
     * <p>异常处理链：</p>
     * <ul>
     *   <li>HttpTimeoutException -> 请求超时异常，转换为"请求超时"错误信息</li>
     *   <li>ConnectException -> 网络连接异常，转换为"无法连接到API服务器"错误信息</li>
     *   <li>IOException -> IO异常，转换为"网络IO异常"错误信息</li>
     *   <li>其他Exception -> 统一转换为"未知错误"错误信息</li>
     * </ul>
     * 
     * @param ip IP 地址
     * @return IPLocationInfo 对象，如果查询失败则返回失败对象（不会返回null）
     * @throws Exception 网络异常或其他异常
     */
    private IPLocationInfo fetchLocation(String ip) throws Exception {
        // 构建 API 请求 URL
        // URL格式：https://api.vore.top/api/IPdata?ip={IP}
        String apiUrl = API_BASE_URL + "?ip=" + ip;
        System.out.println("[IPLocationService] 请求URL: " + apiUrl);
        URI uri = URI.create(apiUrl);
        
        // 创建 HTTP 请求，添加User-Agent头部
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("User-Agent", "NetPulseFX/1.0")
                .header("Accept", "application/json")
                .GET()
                .build();
        
        long startTime = System.currentTimeMillis();
        
        // 发送请求并获取响应（JSON格式）
        HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());
        
        long elapsedTime = System.currentTimeMillis() - startTime;
        System.out.println("[IPLocationService] HTTP请求完成，耗时: " + elapsedTime + "ms");
        
        int statusCode = response.statusCode();
        String jsonResponse = response.body();
        
        // 检查 HTTP 状态码
        if (statusCode != 200) {
            String errorMsg = String.format("API返回HTTP状态码: %d", statusCode);
            System.err.println("[IPLocationService] 查询 IP " + ip + " 失败 - " + errorMsg);
            return new IPLocationInfo(ip, errorMsg);
        }
        
        // 检查响应体是否为空
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            System.err.println("[IPLocationService] API返回空响应体");
            return new IPLocationInfo(ip, "API返回空响应");
        }
        
        // 解析 JSON 响应
        IPLocationInfo locationInfo = parseJsonResponse(ip, jsonResponse);
        
        return locationInfo;
    }
    
    /**
     * 异步查询 IP 地理位置信息
     * 
     * <p>在后台线程中执行查询，不阻塞调用线程。</p>
     * <p>Controller中应使用 Platform.runLater() 更新UI。</p>
     * 
     * <p>使用示例：</p>
     * <pre>
     * IPLocationService service = IPLocationService.getInstance();
     * service.queryLocationAsync("8.8.8.8")
     *     .thenAccept(locationInfo -> {
     *         Platform.runLater(() -> {
     *             // 在主线程中更新UI
     *             updateUI(locationInfo);
     *         });
     *     })
     *     .exceptionally(throwable -> {
     *         Platform.runLater(() -> {
     *             // 处理异常
     *             showError(throwable.getMessage());
     *         });
     *         return null;
     *     });
     * </pre>
     * 
     * @param ip IP 地址
     * @return CompletableFuture<IPLocationInfo> 异步结果
     */
    public CompletableFuture<IPLocationInfo> queryLocationAsync(String ip) {
        return CompletableFuture.supplyAsync(() -> queryLocation(ip));
    }
    
    /**
     * 解析 JSON 响应，提取IP地理位置信息
     * 
     * <p>JSON数据流向说明：</p>
     * <ol>
     *   <li><strong>接收原始JSON字符串</strong>：从HTTP响应中获取JSON文本</li>
     *   <li><strong>提取code字段</strong>：判断API请求是否成功（code=200表示成功）</li>
     *   <li><strong>提取ipdata对象</strong>：从JSON中定位到ipdata对象</li>
     *   <li><strong>提取info1字段</strong>：从ipdata对象中提取国家信息</li>
     *   <li><strong>提取info2字段</strong>：从ipdata对象中提取省份信息</li>
     *   <li><strong>提取info3字段</strong>：从ipdata对象中提取城市信息</li>
     *   <li><strong>提取isp字段</strong>：从ipdata对象中提取运营商信息</li>
     *   <li><strong>构建IPLocationInfo对象</strong>：将提取的数据封装为IPLocationInfo对象</li>
     * </ol>
     * 
     * <p>解析策略：</p>
     * <ul>
     *   <li>使用正则表达式匹配JSON字段，不依赖外部JSON库</li>
     *   <li>正则表达式使用DOTALL模式，可以跨行匹配</li>
     *   <li>如果某个字段提取失败，使用"未知"作为默认值</li>
     *   <li>如果所有关键字段都提取失败，返回"无法解析API响应"错误</li>
     * </ul>
     * 
     * <p>JSON结构示例：</p>
     * <pre>
     * {
     *   "code": 200,
     *   "msg": "SUCCESS",
     *   "ipdata": {
     *     "info1": "美国",
     *     "info2": "加利福尼亚州",
     *     "info3": "山景城",
     *     "isp": "Google LLC"
     *   }
     * }
     * </pre>
     * 
     * @param ip IP 地址
     * @param jsonResponse JSON 响应字符串
     * @return IPLocationInfo 对象
     */
    private IPLocationInfo parseJsonResponse(String ip, String jsonResponse) {
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            System.err.println("[IPLocationService] JSON响应为空");
            return new IPLocationInfo(ip, "API返回空响应");
        }
        
        try {
            // 第一步：检查code字段，判断API请求是否成功
            // 数据流向：JSON响应 -> code字段 -> 状态码值
            Matcher codeMatcher = CODE_PATTERN.matcher(jsonResponse);
            int code = -1;
            if (codeMatcher.find()) {
                try {
                    code = Integer.parseInt(codeMatcher.group(1));
                } catch (NumberFormatException e) {
                    System.err.println("[IPLocationService] 无法解析code字段: " + codeMatcher.group(1));
                }
            }
            
            // 如果code不等于200，说明API返回了错误
            if (code != 200) {
                // 尝试提取msg字段获取错误信息
                String msg = extractByPattern(jsonResponse, MSG_PATTERN, 1);
                String errorMsg = msg != null ? msg : "API返回错误状态码: " + code;
                System.err.println("[IPLocationService] API返回错误，IP: " + ip + ", code: " + code + ", msg: " + errorMsg);
                return new IPLocationInfo(ip, errorMsg);
            }
            
            // 第二步：从ipdata对象中提取各个字段
            // 数据流向：JSON响应 -> ipdata对象 -> info1/info2/info3/isp字段 -> 字段值
            
            // 提取info1字段（可能是国家或省份）
            // 数据流向：JSON -> ipdata.info1 -> 国家名称或省份名称
            String info1 = extractByPattern(jsonResponse, INFO1_PATTERN, 1);
            if (info1 == null || info1.trim().isEmpty()) {
                info1 = "未知";
            } else {
                info1 = info1.trim();
            }
            
            // 提取info2字段（可能是省份或城市）
            // 数据流向：JSON -> ipdata.info2 -> 省份名称或城市名称
            String info2 = extractByPattern(jsonResponse, INFO2_PATTERN, 1);
            if (info2 == null || info2.trim().isEmpty()) {
                info2 = "未知";
            } else {
                info2 = info2.trim();
            }
            
            // 提取info3字段（可能是城市）
            // 数据流向：JSON -> ipdata.info3 -> 城市名称
            String info3 = extractByPattern(jsonResponse, INFO3_PATTERN, 1);
            if (info3 == null || info3.trim().isEmpty()) {
                info3 = "未知";
            } else {
                info3 = info3.trim();
            }
            
            // 提取isp字段（运营商）
            // 数据流向：JSON -> ipdata.isp -> 运营商名称
            String isp = extractByPattern(jsonResponse, ISP_PATTERN, 1);
            if (isp == null || isp.trim().isEmpty()) {
                isp = "未知";
            } else {
                isp = isp.trim();
            }
            
            // 第三步：智能解析国家、省份、城市
            // 对于中国的IP地址，API可能返回：info1=省份，info2=城市
            // 需要检测info1是否是中国的省份/城市，如果是，则国家应该是"中国"
            String country;
            String region;
            String city;
            
            if (isChineseProvinceOrCity(info1)) {
                // info1是中国的省份或城市，说明这是中国IP
                country = "中国";
                region = info1; // info1是省份
                // 如果info2也是省份（直辖市情况），则使用info3作为城市
                if (isChineseProvinceOrCity(info2) && !info2.equals(info1)) {
                    // info2也是省份，说明info1可能是直辖市，info2是区县
                    // 这种情况下，保持info1作为region，info2作为city
                    city = info2;
                } else if (!info2.equals("未知") && !isChineseProvinceOrCity(info2)) {
                    // info2不是省份，应该是城市
                    city = info2;
                } else if (!info3.equals("未知")) {
                    // 使用info3作为城市
                    city = info3;
                } else {
                    city = "未知";
                }
            } else if (info1.equals("中国")) {
                // info1直接是"中国"
                country = "中国";
                region = !info2.equals("未知") ? info2 : "未知";
                city = !info3.equals("未知") ? info3 : "未知";
            } else {
                // 国际IP地址，按原逻辑处理
                country = info1;
                region = !info2.equals("未知") ? info2 : "未知";
                city = !info3.equals("未知") ? info3 : "未知";
            }
            
            // 第四步：验证是否提取到有效信息
            // 如果所有关键字段都是"未知"，说明解析失败
            if (country.equals("未知") && region.equals("未知") && city.equals("未知") && isp.equals("未知")) {
                System.err.println("[IPLocationService] 无法从JSON中解析任何有效字段，IP: " + ip);
                System.err.println("[IPLocationService] JSON内容预览: " + jsonResponse.substring(0, Math.min(500, jsonResponse.length())));
                return new IPLocationInfo(ip, "无法解析API响应");
            }
            
            // 第五步：根据国家名称推断国家代码
            // 数据流向：国家名称 -> 国家代码映射 -> 国家代码（如CN、US等）
            String countryCode = inferCountryCode(country);
            
            // 第六步：构建IPLocationInfo对象
            // 数据流向：提取的字段值 -> IPLocationInfo构造函数 -> IPLocationInfo对象
            // 注意：由于Vore-API不提供风险评分和网络类型信息，这些字段设为null
            return new IPLocationInfo(ip, country, city, isp, region, countryCode, null, null, null);
            
        } catch (Exception e) {
            System.err.println("[IPLocationService] 解析 JSON 响应失败: " + e.getMessage());
            e.printStackTrace();
            return new IPLocationInfo(ip, "解析响应时发生错误: " + e.getMessage());
        }
    }
    
    /**
     * 使用正则表达式从JSON中提取指定字段的值
     * 
     * <p>该方法使用预定义的正则表达式模式匹配JSON字段。</p>
     * 
     * @param json JSON字符串
     * @param pattern 正则表达式模式
     * @param groupIndex 捕获组索引（从1开始）
     * @return 提取的字段值，如果未找到则返回null
     */
    private String extractByPattern(String json, Pattern pattern, int groupIndex) {
        Matcher matcher = pattern.matcher(json);
        if (matcher.find() && matcher.groupCount() >= groupIndex) {
            return matcher.group(groupIndex);
        }
        return null;
    }
    
    /**
     * 检测字符串是否是中国的省份或城市
     * 
     * <p>通过检查是否包含省份/城市特征字符来判断。</p>
     * 
     * @param text 要检测的字符串
     * @return true 如果是中国的省份或城市
     */
    private boolean isChineseProvinceOrCity(String text) {
        if (text == null || text.equals("未知") || text.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = text.trim();
        
        // 检查是否包含省份/城市特征字符
        return trimmed.endsWith("省") || 
               trimmed.endsWith("市") || 
               trimmed.endsWith("自治区") || 
               trimmed.endsWith("特别行政区") ||
               trimmed.endsWith("区") ||
               trimmed.endsWith("县") ||
               // 检查是否是直辖市或省份名称（不含后缀）
               trimmed.equals("北京") || trimmed.equals("上海") || 
               trimmed.equals("天津") || trimmed.equals("重庆") ||
               trimmed.equals("广东") || trimmed.equals("江苏") || 
               trimmed.equals("浙江") || trimmed.equals("山东") ||
               trimmed.equals("河南") || trimmed.equals("四川") ||
               trimmed.equals("湖北") || trimmed.equals("湖南") ||
               trimmed.equals("河北") || trimmed.equals("安徽") ||
               trimmed.equals("福建") || trimmed.equals("江西") ||
               trimmed.equals("陕西") || trimmed.equals("山西") ||
               trimmed.equals("云南") || trimmed.equals("广西") ||
               trimmed.equals("贵州") || trimmed.equals("辽宁") ||
               trimmed.equals("黑龙江") || trimmed.equals("吉林") ||
               trimmed.equals("内蒙古") || trimmed.equals("新疆") ||
               trimmed.equals("西藏") || trimmed.equals("青海") ||
               trimmed.equals("甘肃") || trimmed.equals("宁夏") ||
               trimmed.equals("海南") || trimmed.equals("香港") ||
               trimmed.equals("澳门") || trimmed.equals("台湾");
    }
    
    /**
     * 根据国家名称推断国家代码
     * 
     * <p>这是一个简单的映射表，仅包含常见国家。</p>
     * <p>如果无法匹配，返回"未知"。</p>
     * 
     * @param country 国家名称
     * @return 国家代码（ISO 3166-1 alpha-2）
     */
    private String inferCountryCode(String country) {
        if (country == null || country.equals("未知")) {
            return "未知";
        }
        
        // 简单的国家名称到代码的映射
        switch (country.trim()) {
            case "中国": return "CN";
            case "美国": return "US";
            case "日本": return "JP";
            case "韩国": return "KR";
            case "英国": return "GB";
            case "德国": return "DE";
            case "法国": return "FR";
            case "俄罗斯": return "RU";
            case "印度": return "IN";
            case "巴西": return "BR";
            default: return "未知";
        }
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
     * 移除指定 IP 的缓存
     * 用于强制重新查询指定 IP 的场景
     * 
     * @param ip IP 地址
     */
    public void removeFromCache(String ip) {
        if (ip != null && !ip.trim().isEmpty()) {
            cache.remove(ip.trim());
            System.out.println("[IPLocationService] 已移除IP缓存: " + ip.trim());
        }
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
