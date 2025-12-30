package com.netpulse.netpulsefx.exception;

/**
 * 网卡探测相关的自定义异常类
 * 用于处理网卡发现失败、权限不足等情况
 */
public class NetworkInterfaceException extends Exception {
    
    /**
     * 构造方法：使用指定的错误消息创建异常
     * 
     * @param message 错误消息
     */
    public NetworkInterfaceException(String message) {
        super(message);
    }
    
    /**
     * 构造方法：使用指定的错误消息和原因创建异常
     * 
     * @param message 错误消息
     * @param cause 导致此异常的原因
     */
    public NetworkInterfaceException(String message, Throwable cause) {
        super(message, cause);
    }
}


