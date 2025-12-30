module com.netpulse.netpulsefx {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    
    // JDBC API（用于数据库操作）
    requires java.sql;
    
    // Pcap4j 库依赖（自动模块，基于 artifactId）
    requires org.pcap4j.core;
    // 注意：packetfactory-static 暂时注释，因为自动模块名解析问题
    // 对于基本的网卡探测功能，core 模块已经足够
    // requires org.pcap4j.packetfactory.static_;
    
    // SLF4J 日志实现：解决 SLF4J 警告
    requires org.slf4j.simple;
    
    // H2 Database：嵌入式数据库，用于存储流量历史数据
    // 迁移说明：从 SQLite 迁移到 H2，以解决 SLF4J 依赖冲突问题
    requires com.h2database;

    opens com.netpulse.netpulsefx to javafx.fxml;
    exports com.netpulse.netpulsefx;
    exports com.netpulse.netpulsefx.service;
    exports com.netpulse.netpulsefx.exception;
}