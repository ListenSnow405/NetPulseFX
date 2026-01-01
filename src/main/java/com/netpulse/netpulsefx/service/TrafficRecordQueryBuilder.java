package com.netpulse.netpulsefx.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 流量记录查询构建器
 * 
 * <p>功能说明：</p>
 * <ul>
 *   <li>根据用户选择的过滤条件动态构建 SQL WHERE 子句</li>
 *   <li>使用 PreparedStatement 占位符防止 SQL 注入攻击</li>
 *   <li>支持多维度组合过滤：协议、应用、流量阈值</li>
 * </ul>
 * 
 * <p>安全考虑：</p>
 * <ul>
 *   <li>所有用户输入都通过 PreparedStatement 的参数绑定，不使用字符串拼接</li>
 *   <li>WHERE 子句的条件通过安全的字符串构建，只包含列名和操作符</li>
 *   <li>参数值通过 setXXX 方法设置，确保类型安全</li>
 * </ul>
 * 
 * <p>使用示例：</p>
 * <pre>{@code
 * QueryBuilder builder = new QueryBuilder();
 * builder.setProtocols(List.of("TCP", "UDP"));
 * builder.setProcessName("chrome.exe");
 * builder.setMinDownSpeed(100.0);
 * 
 * QueryResult result = builder.build();
 * String sql = result.getSql();
 * List<Object> params = result.getParameters();
 * }</pre>
 */
public class TrafficRecordQueryBuilder {
    
    /** 表名常量 */
    private static final String TABLE_TRAFFIC_RECORDS = "traffic_records";
    private static final String TABLE_MONITORING_SESSIONS = "monitoring_sessions";
    
    /** 协议过滤：TCP, UDP, 其他 */
    private List<String> selectedProtocols;
    
    /** 应用过滤：进程名称 */
    private String processName;
    
    /** 流量阈值：最小下载速度（KB/s） */
    private Double minDownSpeed;
    
    /** 会话ID过滤（可选） */
    private Integer sessionId;
    
    /**
     * 构造函数
     */
    public TrafficRecordQueryBuilder() {
        this.selectedProtocols = new ArrayList<>();
        this.processName = null;
        this.minDownSpeed = null;
        this.sessionId = null;
    }
    
    /**
     * 设置协议过滤
     * 
     * @param protocols 协议列表（"TCP", "UDP", "其他"），如果为空或null则不过滤
     */
    public void setProtocols(List<String> protocols) {
        if (protocols != null && !protocols.isEmpty()) {
            this.selectedProtocols = new ArrayList<>(protocols);
        } else {
            this.selectedProtocols.clear();
        }
    }
    
    /**
     * 设置应用过滤
     * 
     * @param processName 进程名称，如果为null或空字符串则不过滤
     */
    public void setProcessName(String processName) {
        if (processName != null && !processName.trim().isEmpty()) {
            this.processName = processName.trim();
        } else {
            this.processName = null;
        }
    }
    
    /**
     * 设置流量阈值过滤
     * 
     * @param minDownSpeed 最小下载速度（KB/s），如果为null则不过滤
     */
    public void setMinDownSpeed(Double minDownSpeed) {
        this.minDownSpeed = minDownSpeed;
    }
    
    /**
     * 设置会话ID过滤
     * 
     * @param sessionId 会话ID，如果为null则不过滤
     */
    public void setSessionId(Integer sessionId) {
        this.sessionId = sessionId;
    }
    
    /**
     * 构建查询 SQL 和参数
     * 
     * @return QueryResult 包含 SQL 语句和参数列表
     */
    public QueryResult build() {
        StringBuilder sql = new StringBuilder();
        List<Object> parameters = new ArrayList<>();
        
        // 基础 SELECT 语句
        sql.append("SELECT tr.record_id, tr.session_id, tr.down_speed, tr.up_speed, ");
        sql.append("tr.source_ip, tr.dest_ip, tr.process_name, tr.protocol, tr.record_time, ");
        sql.append("ms.iface_name ");
        sql.append("FROM ").append(TABLE_TRAFFIC_RECORDS).append(" tr ");
        sql.append("INNER JOIN ").append(TABLE_MONITORING_SESSIONS).append(" ms ");
        sql.append("ON tr.session_id = ms.session_id ");
        
        // 构建 WHERE 子句
        List<String> conditions = new ArrayList<>();
        
        // 会话ID过滤
        if (sessionId != null) {
            conditions.add("tr.session_id = ?");
            parameters.add(sessionId);
        }
        
        // 协议过滤（使用真实的 protocol 字段）
        if (!selectedProtocols.isEmpty()) {
            List<String> protocolConditions = new ArrayList<>();
            
            for (String protocol : selectedProtocols) {
                if ("TCP".equalsIgnoreCase(protocol)) {
                    protocolConditions.add("tr.protocol = ?");
                    parameters.add("TCP");
                } else if ("UDP".equalsIgnoreCase(protocol)) {
                    protocolConditions.add("tr.protocol = ?");
                    parameters.add("UDP");
                } else if ("ICMP".equalsIgnoreCase(protocol)) {
                    protocolConditions.add("tr.protocol = ?");
                    parameters.add("ICMP");
                } else if ("其他".equals(protocol)) {
                    // 其他协议：既不是 TCP、UDP，也不是 ICMP，或者协议字段为 NULL
                    protocolConditions.add("(tr.protocol IS NULL OR (tr.protocol != ? AND tr.protocol != ? AND tr.protocol != ?))");
                    parameters.add("TCP");
                    parameters.add("UDP");
                    parameters.add("ICMP");
                }
            }
            
            if (!protocolConditions.isEmpty()) {
                // 使用 OR 连接多个协议条件
                conditions.add("(" + String.join(" OR ", protocolConditions) + ")");
            }
        }
        
        // 应用过滤（进程名）
        if (processName != null) {
            conditions.add("tr.process_name = ?");
            parameters.add(processName);
        }
        
        // 流量阈值过滤
        if (minDownSpeed != null && minDownSpeed > 0) {
            conditions.add("tr.down_speed >= ?");
            parameters.add(minDownSpeed);
        }
        
        // 添加 WHERE 子句
        if (!conditions.isEmpty()) {
            sql.append("WHERE ");
            sql.append(String.join(" AND ", conditions));
        }
        
        // 排序
        sql.append(" ORDER BY tr.record_time DESC");
        
        return new QueryResult(sql.toString(), parameters);
    }
    
    /**
     * 查询结果类
     * 包含构建好的 SQL 语句和参数列表
     */
    public static class QueryResult {
        private final String sql;
        private final List<Object> parameters;
        
        public QueryResult(String sql, List<Object> parameters) {
            this.sql = sql;
            this.parameters = new ArrayList<>(parameters);
        }
        
        public String getSql() {
            return sql;
        }
        
        public List<Object> getParameters() {
            return new ArrayList<>(parameters);
        }
    }
}

