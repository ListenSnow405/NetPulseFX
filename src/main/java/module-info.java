module com.netpulse.netpulsefx {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    
    // Pcap4j 库依赖（自动模块，基于 artifactId）
    requires org.pcap4j.core;
    // 注意：packetfactory-static 暂时注释，因为自动模块名解析问题
    // 对于基本的网卡探测功能，core 模块已经足够
    // requires org.pcap4j.packetfactory.static_;

    opens com.netpulse.netpulsefx to javafx.fxml;
    exports com.netpulse.netpulsefx;
    exports com.netpulse.netpulsefx.service;
    exports com.netpulse.netpulsefx.exception;
}