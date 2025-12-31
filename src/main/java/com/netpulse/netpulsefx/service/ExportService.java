package com.netpulse.netpulsefx.service;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.netpulse.netpulsefx.util.MarkdownToHtmlConverter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 导出服务类
 * 
 * <p>功能说明：</p>
 * <ul>
 *   <li>Excel 导出：将会话数据和明细记录导出为 Excel 文件</li>
 *   <li>PDF 导出：将 AI 分析报告导出为 PDF 文件，支持中文字体</li>
 * </ul>
 * 
 * <p>技术实现：</p>
 * <ul>
 *   <li>Excel：使用 Apache POI 5.2.5</li>
 *   <li>PDF：使用 iText 7.8.0.2，支持中文字体渲染</li>
 * </ul>
 * 
 * @author NetPulseFX Team
 */
public class ExportService {
    
    /** 时间格式化器 */
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 导出会话数据到 Excel 文件
     * 
     * <p>Excel 文件结构：</p>
     * <ul>
     *   <li>第一张表：会话概况（ID、时间、网卡、平均流速等）</li>
     *   <li>第二张表：明细数据（时间、上传/下载速度、归属地、进程名等）</li>
     * </ul>
     * 
     * <p>样式设置：</p>
     * <ul>
     *   <li>表头：加粗、浅灰色背景</li>
     *   <li>列宽：自动调整以适应内容</li>
     * </ul>
     * 
     * @param session 会话实体对象
     * @param records 流量明细记录列表
     * @param file 目标文件
     * @throws IOException 如果文件写入失败
     */
    public static void exportSessionToExcel(
            DatabaseService.MonitoringSession session,
            List<DatabaseService.TrafficRecord> records,
            File file) throws IOException {
        
        // 创建工作簿
        try (Workbook workbook = new XSSFWorkbook()) {
            
            // ========== 创建样式 ==========
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            
            CellStyle numberStyle = workbook.createCellStyle();
            numberStyle.cloneStyleFrom(dataStyle);
            DataFormat numberFormat = workbook.createDataFormat();
            numberStyle.setDataFormat(numberFormat.getFormat("0.00"));
            
            // ========== 第一张表：会话概况 ==========
            Sheet sessionSheet = workbook.createSheet("会话概况");
            
            // 创建表头
            Row headerRow = sessionSheet.createRow(0);
            String[] sessionHeaders = {"项目", "值"};
            for (int i = 0; i < sessionHeaders.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(sessionHeaders[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // 填充会话数据
            int rowNum = 1;
            addSessionDataRow(sessionSheet, rowNum++, "会话 ID", String.valueOf(session.getSessionId()), dataStyle);
            addSessionDataRow(sessionSheet, rowNum++, "网卡名称", session.getIfaceName(), dataStyle);
            addSessionDataRow(sessionSheet, rowNum++, "开始时间", 
                    formatTimestamp(session.getStartTime()), dataStyle);
            addSessionDataRow(sessionSheet, rowNum++, "结束时间", 
                    session.getEndTime() != null ? formatTimestamp(session.getEndTime()) : "进行中", dataStyle);
            addSessionDataRow(sessionSheet, rowNum++, "持续时间", 
                    session.getDurationSeconds() + " 秒", dataStyle);
            addSessionDataRow(sessionSheet, rowNum++, "平均下行速度", 
                    String.format("%.2f KB/s", session.getAvgDownSpeed()), numberStyle);
            addSessionDataRow(sessionSheet, rowNum++, "平均上行速度", 
                    String.format("%.2f KB/s", session.getAvgUpSpeed()), numberStyle);
            addSessionDataRow(sessionSheet, rowNum++, "最大下行速度", 
                    String.format("%.2f KB/s", session.getMaxDownSpeed()), numberStyle);
            addSessionDataRow(sessionSheet, rowNum++, "最大上行速度", 
                    String.format("%.2f KB/s", session.getMaxUpSpeed()), numberStyle);
            addSessionDataRow(sessionSheet, rowNum++, "总下行字节数", 
                    formatBytes(session.getTotalDownBytes()), dataStyle);
            addSessionDataRow(sessionSheet, rowNum++, "总上行字节数", 
                    formatBytes(session.getTotalUpBytes()), dataStyle);
            addSessionDataRow(sessionSheet, rowNum++, "记录数量", 
                    String.valueOf(session.getRecordCount()), dataStyle);
            
            // 自动调整列宽
            sessionSheet.setColumnWidth(0, 20 * 256);  // 20 个字符宽度
            sessionSheet.setColumnWidth(1, 40 * 256);   // 40 个字符宽度
            
            // ========== 第二张表：明细数据 ==========
            Sheet recordsSheet = workbook.createSheet("明细数据");
            
            // 创建表头
            Row recordsHeaderRow = recordsSheet.createRow(0);
            String[] recordHeaders = {"序号", "时间", "下行速度 (KB/s)", "上行速度 (KB/s)", 
                    "源IP", "目标IP", "进程名称"};
            for (int i = 0; i < recordHeaders.length; i++) {
                Cell cell = recordsHeaderRow.createCell(i);
                cell.setCellValue(recordHeaders[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // 填充明细数据
            int recordRowNum = 1;
            int recordIndex = 1;
            for (DatabaseService.TrafficRecord record : records) {
                Row row = recordsSheet.createRow(recordRowNum++);
                
                // 序号
                Cell cell0 = row.createCell(0);
                cell0.setCellValue(recordIndex++);
                cell0.setCellStyle(dataStyle);
                
                // 时间
                Cell cell1 = row.createCell(1);
                cell1.setCellValue(formatTimestamp(record.getRecordTime()));
                cell1.setCellStyle(dataStyle);
                
                // 下行速度
                Cell cell2 = row.createCell(2);
                cell2.setCellValue(record.getDownSpeed());
                cell2.setCellStyle(numberStyle);
                
                // 上行速度
                Cell cell3 = row.createCell(3);
                cell3.setCellValue(record.getUpSpeed());
                cell3.setCellStyle(numberStyle);
                
                // 源IP
                Cell cell4 = row.createCell(4);
                cell4.setCellValue(record.getSourceIp() != null ? record.getSourceIp() : "");
                cell4.setCellStyle(dataStyle);
                
                // 目标IP
                Cell cell5 = row.createCell(5);
                cell5.setCellValue(record.getDestIp() != null ? record.getDestIp() : "");
                cell5.setCellStyle(dataStyle);
                
                // 进程名称
                Cell cell6 = row.createCell(6);
                cell6.setCellValue(record.getProcessName() != null ? record.getProcessName() : "未知进程");
                cell6.setCellStyle(dataStyle);
            }
            
            // 自动调整列宽
            for (int i = 0; i < recordHeaders.length; i++) {
                recordsSheet.autoSizeColumn(i);
                // 设置最小宽度
                int currentWidth = recordsSheet.getColumnWidth(i);
                if (currentWidth < 2000) {
                    recordsSheet.setColumnWidth(i, 2000);
                }
            }
            
            // 冻结表头行
            recordsSheet.createFreezePane(0, 1);
            
            // 写入文件
            try (FileOutputStream outputStream = new FileOutputStream(file)) {
                workbook.write(outputStream);
            }
        }
    }
    
    /**
     * 添加会话数据行
     */
    private static void addSessionDataRow(Sheet sheet, int rowNum, String label, String value, CellStyle style) {
        Row row = sheet.createRow(rowNum);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(style);
        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value);
        valueCell.setCellStyle(style);
    }
    
    /**
     * 格式化字节数
     */
    private static String formatBytes(long bytes) {
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
     * 格式化时间戳
     */
    private static String formatTimestamp(java.sql.Timestamp timestamp) {
        if (timestamp == null) {
            return "";
        }
        return DATE_FORMAT.format(new Date(timestamp.getTime()));
    }
    
    /**
     * 导出 AI 诊断报告到 PDF 文件
     * 
     * <p>PDF 报告结构：</p>
     * <ul>
     *   <li>页眉：项目名称 "NetPulse FX - 网络行为诊断报告"</li>
     *   <li>会话概况：开始时间、持续时间、总流量等关键信息</li>
     *   <li>AI 诊断结果：完整的 Markdown 内容渲染到 PDF</li>
     * </ul>
     * 
     * <p>中文字体支持：</p>
     * <ul>
     *   <li>优先使用系统字体（Windows: 微软雅黑，支持 TTC 字体索引）</li>
     *   <li>如果系统字体不可用，回退到 iText 标准字体（可能不支持中文）</li>
     *   <li>字体自动嵌入，确保跨平台兼容性</li>
     * </ul>
     * 
     * @param session 会话实体对象（包含会话基本信息）
     * @param aiReportContent AI 生成的 Markdown 格式诊断报告内容
     * @param file 目标文件
     * @throws IOException 如果文件写入失败或字体加载失败
     */
    public static void exportAIReportToPDF(
            DatabaseService.MonitoringSession session,
            String aiReportContent,
            File file) throws IOException {
        
        // 创建 PDF 文档
        try (PdfWriter writer = new PdfWriter(file);
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {
            
            // 设置页面边距，确保文本不会溢出
            document.setMargins(50, 50, 50, 50);
            
            // ========== 加载中文字体（增强版） ==========
            PdfFont font = null;
            PdfFont boldFont = null;
            
            // 尝试加载系统字体（支持中文，优先使用 TTC 字体索引）
            String[][] fontConfigs = {
                {"C:/Windows/Fonts/msyh.ttc", "0"},      // Windows 微软雅黑（索引 0）
                {"C:/Windows/Fonts/msyhbd.ttc", "0"},   // Windows 微软雅黑粗体（索引 0）
                {"C:/Windows/Fonts/simsun.ttc", "0"},  // Windows 宋体（索引 0）
                {"/System/Library/Fonts/PingFang.ttc", "0"},  // macOS 苹方
                {"/usr/share/fonts/truetype/wqy/wqy-microhei.ttc", "0"},  // Linux 文泉驿微米黑
            };
            
            for (String[] fontConfig : fontConfigs) {
                String fontPath = fontConfig[0];
                String fontIndex = fontConfig.length > 1 ? fontConfig[1] : "0";
                
                try {
                    if (Files.exists(Paths.get(fontPath))) {
                        // 对于 TTC 字体文件，使用 IDENTITY_H 编码以支持中文
                        // 注意：iText 7 支持通过字体路径和索引加载 TTC 字体
                        String fontSpec = fontPath + "," + fontIndex;
                        font = PdfFontFactory.createFont(fontSpec, 
                                PdfEncodings.IDENTITY_H, 
                                PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                        
                        // 尝试加载粗体字体
                        try {
                            boldFont = PdfFontFactory.createFont(fontSpec, 
                                    PdfEncodings.IDENTITY_H, 
                                    PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                        } catch (Exception e) {
                            // 如果无法加载粗体，使用普通字体作为粗体
                            boldFont = font;
                        }
                        
                        System.out.println("[ExportService] 成功加载字体: " + fontSpec);
                        break;
                    }
                } catch (Exception e) {
                    // 继续尝试下一个字体
                    System.err.println("[ExportService] 无法加载字体 " + fontPath + ": " + e.getMessage());
                }
            }
            
            // 如果系统字体都不可用，使用标准字体（不支持中文，会显示为方块）
            if (font == null) {
                try {
                    font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
                    boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
                    System.out.println("[ExportService] 使用标准字体（可能不支持中文）");
                } catch (Exception e) {
                    throw new IOException("无法加载任何字体: " + e.getMessage(), e);
                }
            }
            
            // ========== 添加页眉 ==========
            Paragraph header = new Paragraph("NetPulse FX - 网络行为诊断报告")
                    .setFont(boldFont != null ? boldFont : font)
                    .setFontSize(20)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(25);
            document.add(header);
            
            // ========== 添加会话概况区 ==========
            Paragraph overviewTitle = new Paragraph("会话概况")
                    .setFont(boldFont != null ? boldFont : font)
                    .setFontSize(16)
                    .setBold()
                    .setMarginTop(15)
                    .setMarginBottom(12);
            document.add(overviewTitle);
            
            // 创建会话概况表格（显示关键信息）
            Table overviewTable = new Table(UnitValue.createPercentArray(new float[]{1, 2}))
                    .useAllAvailableWidth()
                    .setMarginBottom(25);
            
            // 计算总流量（下行 + 上行）
            long totalBytes = session.getTotalDownBytes() + session.getTotalUpBytes();
            String totalTraffic = formatBytes(totalBytes);
            
            // 格式化持续时间
            String duration = formatDuration(session.getDurationSeconds());
            
            // 添加关键信息到表格
            addTableRow(overviewTable, "会话 ID", String.valueOf(session.getSessionId()), font);
            addTableRow(overviewTable, "网卡名称", session.getIfaceName(), font);
            addTableRow(overviewTable, "开始时间", formatTimestamp(session.getStartTime()), font);
            addTableRow(overviewTable, "结束时间", 
                    session.getEndTime() != null ? formatTimestamp(session.getEndTime()) : "进行中", font);
            addTableRow(overviewTable, "持续时间", duration, font);
            addTableRow(overviewTable, "总流量", totalTraffic, font);
            addTableRow(overviewTable, "平均下行速度", String.format("%.2f KB/s", session.getAvgDownSpeed()), font);
            addTableRow(overviewTable, "平均上行速度", String.format("%.2f KB/s", session.getAvgUpSpeed()), font);
            addTableRow(overviewTable, "记录数量", String.valueOf(session.getRecordCount()) + " 条", font);
            
            document.add(overviewTable);
            
            // ========== 添加 AI 诊断结果区 ==========
            Paragraph diagnosisTitle = new Paragraph("AI 诊断结果")
                    .setFont(boldFont != null ? boldFont : font)
                    .setFontSize(16)
                    .setBold()
                    .setMarginTop(20)
                    .setMarginBottom(12);
            document.add(diagnosisTitle);
            
            // 渲染 Markdown 内容到 PDF（支持自动换行）
            renderMarkdownToPDF(document, aiReportContent, font, boldFont);
            
            // 添加页脚
            document.add(new Paragraph("\n\n生成时间: " + DATE_FORMAT.format(new Date()))
                    .setFont(font)
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setMarginTop(30));
        }
    }
    
    /**
     * 格式化持续时间（将秒数转换为更易读的格式）
     */
    private static String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + " 秒";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long remainingSeconds = seconds % 60;
            return String.format("%d 分 %d 秒", minutes, remainingSeconds);
        } else {
            long hours = seconds / 3600;
            long remainingMinutes = (seconds % 3600) / 60;
            long remainingSeconds = seconds % 60;
            return String.format("%d 小时 %d 分 %d 秒", hours, remainingMinutes, remainingSeconds);
        }
    }
    
    /**
     * 将 Markdown 内容渲染到 PDF 文档中（支持自动换行和基本格式）
     * 
     * <p>支持以下 Markdown 格式：</p>
     * <ul>
     *   <li>标题：#, ##, ### 等</li>
     *   <li>段落：普通文本段落</li>
     *   <li>列表：-、*、+ 或数字列表</li>
     *   <li>粗体：**text** 或 __text__</li>
     *   <li>代码块：```code```</li>
     * </ul>
     * 
     * @param document PDF 文档对象
     * @param markdownContent Markdown 格式的文本内容
     * @param font 普通字体
     * @param boldFont 粗体字体
     */
    private static void renderMarkdownToPDF(Document document, String markdownContent, 
                                           PdfFont font, PdfFont boldFont) {
        if (markdownContent == null || markdownContent.trim().isEmpty()) {
            Paragraph emptyPara = new Paragraph("（无诊断内容）")
                    .setFont(font)
                    .setFontSize(12)
                    .setFontColor(com.itextpdf.kernel.colors.ColorConstants.GRAY);
            document.add(emptyPara);
            return;
        }
        
        // 逐行处理，更准确地识别 Markdown 格式
        String[] lines = markdownContent.split("\n");
        StringBuilder currentParagraph = new StringBuilder();
        boolean inCodeBlock = false;
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmedLine = line.trim();
            
            // 检测代码块开始/结束
            if (trimmedLine.startsWith("```")) {
                // 如果当前有段落内容，先输出
                if (currentParagraph.length() > 0) {
                    addParagraphToPDF(document, currentParagraph.toString(), font, boldFont);
                    currentParagraph.setLength(0);
                }
                inCodeBlock = !inCodeBlock;
                continue;
            }
            
            // 如果在代码块中，直接添加原始内容
            if (inCodeBlock) {
                currentParagraph.append(line).append("\n");
                continue;
            }
            
            // 检测标题（行首以 # 开头，且 # 后跟空格）
            if (trimmedLine.startsWith("#") && trimmedLine.length() > 1) {
                // 如果当前有段落内容，先输出
                if (currentParagraph.length() > 0) {
                    addParagraphToPDF(document, currentParagraph.toString(), font, boldFont);
                    currentParagraph.setLength(0);
                }
                
                // 提取标题级别和文本
                int level = 0;
                int j = 0;
                while (j < trimmedLine.length() && trimmedLine.charAt(j) == '#') {
                    level++;
                    j++;
                }
                
                // 确保 # 后跟空格（标准 Markdown 格式）
                if (j < trimmedLine.length() && trimmedLine.charAt(j) == ' ') {
                    String titleText = trimmedLine.substring(j + 1).trim();
                    
                    // 移除格式标记（如粗体）
                    titleText = removeMarkdownFormatting(titleText);
                    
                    // 根据标题级别设置字体大小
                    float titleSize = switch (level) {
                        case 1 -> 18f;
                        case 2 -> 16f;
                        case 3 -> 14f;
                        case 4 -> 13f;
                        default -> 12f;
                    };
                    
                    Paragraph titlePara = new Paragraph(titleText)
                            .setFont(boldFont != null ? boldFont : font)
                            .setFontSize(titleSize)
                            .setBold()
                            .setMarginTop(level == 1 ? 15 : (level == 2 ? 12 : 10))
                            .setMarginBottom(6);
                    document.add(titlePara);
                } else {
                    // 不是标准标题格式，作为普通文本处理
                    currentParagraph.append(line).append("\n");
                }
                continue;
            }
            
            // 检测列表项（行首以 -、*、+ 或数字开头）
            if (trimmedLine.matches("^[-*+]\\s+.*") || trimmedLine.matches("^\\d+\\.\\s+.*")) {
                // 如果当前有段落内容，先输出
                if (currentParagraph.length() > 0) {
                    addParagraphToPDF(document, currentParagraph.toString(), font, boldFont);
                    currentParagraph.setLength(0);
                }
                
                // 移除列表标记
                String listItem = trimmedLine.replaceFirst("^[-*+]\\s+", "")
                        .replaceFirst("^\\d+\\.\\s+", "");
                
                // 移除格式标记
                listItem = removeMarkdownFormatting(listItem);
                
                // 添加列表项（使用缩进和项目符号）
                Paragraph listPara = new Paragraph("• " + listItem)
                        .setFont(font)
                        .setFontSize(12)
                        .setMarginLeft(20)
                        .setMarginBottom(4)
                        .setKeepTogether(false);
                document.add(listPara);
                continue;
            }
            
            // 空行：如果当前有段落内容，输出段落
            if (trimmedLine.isEmpty()) {
                if (currentParagraph.length() > 0) {
                    addParagraphToPDF(document, currentParagraph.toString(), font, boldFont);
                    currentParagraph.setLength(0);
                }
                // 添加小间距
                document.add(new Paragraph(" ").setFont(font).setFontSize(4));
                continue;
            }
            
            // 普通文本行，添加到当前段落
            // 注意：保留原始行的格式，只在段落内添加空格
            if (currentParagraph.length() > 0) {
                currentParagraph.append(" ");
            }
            // 使用 trim() 移除行首尾空白，但保留段落内的格式
            currentParagraph.append(trimmedLine);
        }
        
        // 处理最后一个段落
        if (currentParagraph.length() > 0) {
            addParagraphToPDF(document, currentParagraph.toString(), font, boldFont);
        }
    }
    
    /**
     * 将段落文本添加到 PDF 文档中（支持粗体等格式）
     * 
     * @param document PDF 文档对象
     * @param text 段落文本（可能包含 Markdown 格式标记）
     * @param font 普通字体
     * @param boldFont 粗体字体
     */
    private static void addParagraphToPDF(Document document, String text, 
                                         PdfFont font, PdfFont boldFont) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        
        // 移除基本格式标记，但保留文本结构
        text = removeMarkdownFormatting(text);
        
        // 创建段落，iText 7 会自动处理换行
        Paragraph para = new Paragraph(text)
                .setFont(font)
                .setFontSize(12)
                .setMarginBottom(8)
                .setKeepTogether(false)  // 允许跨页
                .setKeepWithNext(false);  // 不强制与下一段保持在同一页
        
        document.add(para);
    }
    
    /**
     * 移除 Markdown 格式标记，保留文本内容
     * 
     * @param text 包含 Markdown 格式的文本
     * @return 移除格式标记后的纯文本
     */
    private static String removeMarkdownFormatting(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        String result = text;
        
        // 移除粗体标记 **text** 或 __text__
        result = result.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");
        result = result.replaceAll("__([^_]+)__", "$1");
        
        // 移除斜体标记 *text* 或 _text_（注意：避免与粗体冲突）
        result = result.replaceAll("(?<!\\*)\\*([^*]+)\\*(?!\\*)", "$1");
        result = result.replaceAll("(?<!_)_([^_]+)_(?!_)", "$1");
        
        // 移除行内代码标记 `code`
        result = result.replaceAll("`([^`]+)`", "$1");
        
        // 移除链接标记 [text](url)
        result = result.replaceAll("\\[([^\\]]+)\\]\\([^\\)]+\\)", "$1");
        
        return result;
    }
    
    /**
     * 添加表格行
     */
    private static void addTableRow(Table table, String label, String value, PdfFont font) {
        com.itextpdf.layout.element.Cell labelCell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(label).setFont(font).setBold())
                .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setPadding(5);
        com.itextpdf.layout.element.Cell valueCell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(value != null ? value : "").setFont(font))
                .setPadding(5);
        table.addCell(labelCell);
        table.addCell(valueCell);
    }
    
    /**
     * 将 Markdown 转换为纯文本（保留标题结构，移除格式标记）
     * 
     * <p>注意：这是一个简化的实现，只处理基本的 Markdown 语法。
     * 保留标题标记（#）以便后续识别，移除其他格式标记。</p>
     * 
     * @param markdown Markdown 格式的文本
     * @return 处理后的文本内容（保留标题标记）
     */
    private static String convertMarkdownToPlainText(String markdown) {
        if (markdown == null || markdown.trim().isEmpty()) {
            return "";
        }
        
        String text = markdown;
        
        // 移除代码块标记（保留内容）
        text = text.replaceAll("```[\\s\\S]*?```", "");
        
        // 移除行内代码标记（保留内容）
        text = text.replaceAll("`([^`]+)`", "$1");
        
        // 移除粗体标记（保留内容）
        text = text.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");
        text = text.replaceAll("__([^_]+)__", "$1");
        
        // 移除斜体标记（保留内容）
        text = text.replaceAll("\\*([^*]+)\\*", "$1");
        text = text.replaceAll("_([^_]+)_", "$1");
        
        // 移除链接标记（保留链接文本）
        text = text.replaceAll("\\[([^\\]]+)\\]\\([^\\)]+\\)", "$1");
        
        // 移除列表标记（使用 Pattern.MULTILINE 标志支持多行匹配）
        text = java.util.regex.Pattern.compile("^[-*+]\\s+", java.util.regex.Pattern.MULTILINE)
                .matcher(text).replaceAll("");
        text = java.util.regex.Pattern.compile("^\\d+\\.\\s+", java.util.regex.Pattern.MULTILINE)
                .matcher(text).replaceAll("");
        
        // 保留标题标记（#），以便后续识别
        // 标题标记会在 renderMarkdownToPDF 中处理
        
        return text;
    }
}

