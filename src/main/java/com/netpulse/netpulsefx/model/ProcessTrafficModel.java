package com.netpulse.netpulsefx.model;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;

/**
 * 进程流量数据模型
 * 
 * <p>用于在 TableView 中显示每个进程的实时流量信息。</p>
 * <p>使用 JavaFX Property 支持自动数据绑定和 UI 更新。</p>
 * 
 * @author NetPulseFX Team
 */
public class ProcessTrafficModel {
    
    /** 进程名称属性 */
    private final StringProperty processName;
    
    /** 进程 ID 属性 */
    private final IntegerProperty pid;
    
    /** 下载速度属性（KB/s） */
    private final DoubleProperty downloadSpeed;
    
    /** 上传速度属性（KB/s） */
    private final DoubleProperty uploadSpeed;
    
    /** 总流量属性（KB/s） */
    private final DoubleProperty totalSpeed;
    
    /**
     * 构造函数
     * 
     * @param processName 进程名称
     * @param pid 进程 ID
     * @param downloadSpeed 下载速度（KB/s）
     * @param uploadSpeed 上传速度（KB/s）
     */
    public ProcessTrafficModel(String processName, Integer pid, double downloadSpeed, double uploadSpeed) {
        this.processName = new SimpleStringProperty(processName != null ? processName : "未知进程");
        this.pid = new SimpleIntegerProperty(pid != null ? pid : 0);
        this.downloadSpeed = new SimpleDoubleProperty(downloadSpeed);
        this.uploadSpeed = new SimpleDoubleProperty(uploadSpeed);
        this.totalSpeed = new SimpleDoubleProperty(downloadSpeed + uploadSpeed);
    }
    
    /**
     * 更新流量数据
     * 
     * @param downloadSpeed 下载速度（KB/s）
     * @param uploadSpeed 上传速度（KB/s）
     */
    public void updateTraffic(double downloadSpeed, double uploadSpeed) {
        this.downloadSpeed.set(downloadSpeed);
        this.uploadSpeed.set(uploadSpeed);
        this.totalSpeed.set(downloadSpeed + uploadSpeed);
    }
    
    /**
     * 累加流量数据（用于汇总多个连接的流量）
     * 
     * @param downloadSpeed 下载速度增量（KB/s）
     * @param uploadSpeed 上传速度增量（KB/s）
     */
    public void addTraffic(double downloadSpeed, double uploadSpeed) {
        this.downloadSpeed.set(this.downloadSpeed.get() + downloadSpeed);
        this.uploadSpeed.set(this.uploadSpeed.get() + uploadSpeed);
        this.totalSpeed.set(this.downloadSpeed.get() + this.uploadSpeed.get());
    }
    
    /**
     * 重置流量数据
     */
    public void resetTraffic() {
        this.downloadSpeed.set(0.0);
        this.uploadSpeed.set(0.0);
        this.totalSpeed.set(0.0);
    }
    
    // Getter 和 Property 方法
    
    public String getProcessName() {
        return processName.get();
    }
    
    public StringProperty processNameProperty() {
        return processName;
    }
    
    public void setProcessName(String processName) {
        this.processName.set(processName);
    }
    
    public int getPid() {
        return pid.get();
    }
    
    public IntegerProperty pidProperty() {
        return pid;
    }
    
    public void setPid(int pid) {
        this.pid.set(pid);
    }
    
    public double getDownloadSpeed() {
        return downloadSpeed.get();
    }
    
    public DoubleProperty downloadSpeedProperty() {
        return downloadSpeed;
    }
    
    public void setDownloadSpeed(double downloadSpeed) {
        this.downloadSpeed.set(downloadSpeed);
        this.totalSpeed.set(this.downloadSpeed.get() + this.uploadSpeed.get());
    }
    
    public double getUploadSpeed() {
        return uploadSpeed.get();
    }
    
    public DoubleProperty uploadSpeedProperty() {
        return uploadSpeed;
    }
    
    public void setUploadSpeed(double uploadSpeed) {
        this.uploadSpeed.set(uploadSpeed);
        this.totalSpeed.set(this.downloadSpeed.get() + this.uploadSpeed.get());
    }
    
    public double getTotalSpeed() {
        return totalSpeed.get();
    }
    
    public DoubleProperty totalSpeedProperty() {
        return totalSpeed;
    }
    
    /**
     * 获取格式化的下载速度字符串（自动选择单位）
     * 
     * @return 格式化后的字符串，如 "1.2 MB/s" 或 "512 KB/s"
     */
    public String getFormattedDownloadSpeed() {
        return formatSpeed(getDownloadSpeed());
    }
    
    /**
     * 获取格式化的上传速度字符串（自动选择单位）
     * 
     * @return 格式化后的字符串，如 "1.2 MB/s" 或 "512 KB/s"
     */
    public String getFormattedUploadSpeed() {
        return formatSpeed(getUploadSpeed());
    }
    
    /**
     * 格式化速度值
     * 
     * @param speedKB 速度（KB/s）
     * @return 格式化后的字符串
     */
    private String formatSpeed(double speedKB) {
        if (speedKB < 0.1) {
            return String.format("%.2f KB/s", speedKB);
        } else if (speedKB < 1024) {
            return String.format("%.2f KB/s", speedKB);
        } else {
            return String.format("%.2f MB/s", speedKB / 1024.0);
        }
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ProcessTrafficModel that = (ProcessTrafficModel) obj;
        return pid.get() == that.pid.get() && 
               processName.get().equals(that.processName.get());
    }
    
    @Override
    public int hashCode() {
        return processName.get().hashCode() * 31 + pid.get();
    }
    
    @Override
    public String toString() {
        return String.format("ProcessTrafficModel{process='%s', pid=%d, down=%.2f KB/s, up=%.2f KB/s}",
                processName.get(), pid.get(), downloadSpeed.get(), uploadSpeed.get());
    }
}


