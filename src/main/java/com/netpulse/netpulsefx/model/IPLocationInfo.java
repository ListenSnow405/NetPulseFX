package com.netpulse.netpulsefx.model;

/**
 * IP 地理位置信息数据模型
 * 
 * <p>用于存储从 IP 归属地查询 API 获取的地理位置信息。</p>
 * 
 * @author NetPulseFX Team
 */
public class IPLocationInfo {
    
    /** IP 地址 */
    private final String ip;
    
    /** 国家 */
    private final String country;
    
    /** 城市 */
    private final String city;
    
    /** ISP（互联网服务提供商） */
    private final String isp;
    
    /** 地区/省份 */
    private final String region;
    
    /** 国家代码 */
    private final String countryCode;
    
    /** 是否查询成功 */
    private final boolean success;
    
    /** 错误消息（如果查询失败） */
    private final String errorMessage;
    
    /**
     * 成功查询的构造函数
     * 
     * @param ip IP 地址
     * @param country 国家
     * @param city 城市
     * @param isp ISP
     * @param region 地区/省份
     * @param countryCode 国家代码
     */
    public IPLocationInfo(String ip, String country, String city, String isp, 
                         String region, String countryCode) {
        this.ip = ip;
        this.country = country != null ? country : "未知";
        this.city = city != null ? city : "未知";
        this.isp = isp != null ? isp : "未知";
        this.region = region != null ? region : "未知";
        this.countryCode = countryCode != null ? countryCode : "未知";
        this.success = true;
        this.errorMessage = null;
    }
    
    /**
     * 查询失败的构造函数
     * 
     * @param ip IP 地址
     * @param errorMessage 错误消息
     */
    public IPLocationInfo(String ip, String errorMessage) {
        this.ip = ip;
        this.country = "未知位置";
        this.city = "未知位置";
        this.isp = "未知";
        this.region = "未知";
        this.countryCode = "未知";
        this.success = false;
        this.errorMessage = errorMessage;
    }
    
    /**
     * 创建"未知位置"的默认实例
     * 
     * @param ip IP 地址
     * @return IPLocationInfo 实例
     */
    public static IPLocationInfo unknown(String ip) {
        return new IPLocationInfo(ip, "无法查询地理位置信息");
    }
    
    // Getter 方法
    public String getIp() {
        return ip;
    }
    
    public String getCountry() {
        return country;
    }
    
    public String getCity() {
        return city;
    }
    
    public String getIsp() {
        return isp;
    }
    
    public String getRegion() {
        return region;
    }
    
    public String getCountryCode() {
        return countryCode;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * 获取格式化的地理位置描述
     * 
     * @return 格式化的字符串，如 "中国, 北京, 中国电信"
     */
    public String getFormattedLocation() {
        if (!success) {
            return "未知位置";
        }
        
        StringBuilder sb = new StringBuilder();
        if (!country.equals("未知")) {
            sb.append(country);
        }
        if (!region.equals("未知") && !region.equals(country)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(region);
        }
        if (!city.equals("未知") && !city.equals(region)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(city);
        }
        if (!isp.equals("未知")) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(isp);
        }
        
        return sb.length() > 0 ? sb.toString() : "未知位置";
    }
    
    /**
     * 获取简短的地理位置描述（仅国家和城市）
     * 
     * @return 格式化的字符串，如 "中国, 北京"
     */
    public String getShortLocation() {
        if (!success) {
            return "未知位置";
        }
        
        StringBuilder sb = new StringBuilder();
        if (!country.equals("未知")) {
            sb.append(country);
        }
        if (!city.equals("未知") && !city.equals(country)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(city);
        }
        
        return sb.length() > 0 ? sb.toString() : "未知位置";
    }
    
    @Override
    public String toString() {
        if (!success) {
            return String.format("IP: %s - %s", ip, errorMessage != null ? errorMessage : "未知位置");
        }
        return String.format("IP: %s | 位置: %s | ISP: %s", ip, getFormattedLocation(), isp);
    }
}


