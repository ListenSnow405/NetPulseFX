module com.netpulse.netpulsefx {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    
    // JDBC API（用于数据库操作）
    requires java.sql;
    
    // HTTP Client（用于 AI API 调用）
    requires java.net.http;
    
    // Pcap4j 库依赖（自动模块，基于 artifactId）
    requires org.pcap4j.core;
    // packetfactory-static 用于解析数据包（提取IP地址）
    // 注意：由于 'static' 是 Java 关键字，不能直接用作模块名
    // 如果 pcap4j-packetfactory-static 不是模块化的，将通过类路径访问
    // 如果需要，可以尝试使用引号或检查实际的模块名
    
    // SLF4J 日志实现：解决 SLF4J 警告
    requires org.slf4j.simple;
    
    // H2 Database：嵌入式数据库，用于存储流量历史数据
    // 迁移说明：从 SQLite 迁移到 H2，以解决 SLF4J 依赖冲突问题
    requires com.h2database;

    opens com.netpulse.netpulsefx to javafx.fxml;
    exports com.netpulse.netpulsefx;
    exports com.netpulse.netpulsefx.service;
    exports com.netpulse.netpulsefx.exception;
    exports com.netpulse.netpulsefx.model;
}