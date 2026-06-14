package com.hczk.hczkaiagentserver.knowledge.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class DocumentParser {

    private static final List<String> SUPPORTED_EXTENSIONS = List.of(
            ".txt", ".pdf", ".docx", ".xlsx"
    );

    public boolean isSupported(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    public String getFileType(String filename) {
        if (filename == null) return "unknown";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".txt")) return "txt";
        if (lower.endsWith(".pdf")) return "pdf";
        if (lower.endsWith(".docx")) return "docx";
        if (lower.endsWith(".xlsx")) return "xlsx";
        return "unknown";
    }

    /**
     * 解析上传的文档文件，提取纯文本内容
     */
    public ParsedDocument parse(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        String fileType = getFileType(filename);
        String text;

        try (InputStream is = file.getInputStream()) {
            text = switch (fileType) {
                case "txt" -> parseTxt(is);
                case "pdf" -> parsePdf(file.getBytes());
                case "docx" -> parseDocx(is);
                case "xlsx" -> parseXlsx(is);
                default -> throw new IllegalArgumentException("不支持的文件类型: " + fileType);
            };
        }

        return new ParsedDocument(filename, fileType, text, null);
    }

    /**
     * 解析 Excel 文件为 QA 对列表（docType=qa 时使用）
     * 第一列为问题，第二列为答案，后续列追加到答案
     * 第一行如果是表头则跳过
     */
    public ParsedDocument parseXlsxAsQA(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        try (InputStream is = file.getInputStream();
             Workbook wb = new XSSFWorkbook(is)) {

            List<String[]> qaPairs = new ArrayList<>();

            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                boolean firstRow = true;

                for (Row row : sheet) {
                    // 跳过空行
                    if (row == null) continue;

                    String[] cells = new String[Math.max(row.getLastCellNum(), 2)];
                    boolean hasContent = false;

                    for (int c = 0; c < cells.length; c++) {
                        Cell cell = row.getCell(c);
                        cells[c] = cell != null ? getCellValue(cell) : "";
                        if (!cells[c].isBlank()) hasContent = true;
                    }

                    if (!hasContent) continue;

                    // 检测并跳过表头行（第一行包含"问题"/"答案"/"question"/"answer"等关键词）
                    if (firstRow) {
                        firstRow = false;
                        String firstCell = cells[0].toLowerCase();
                        if (firstCell.contains("问题") || firstCell.contains("question")
                                || firstCell.contains("序号") || firstCell.contains("no")
                                || firstCell.contains("id")) {
                            continue; // 跳过表头
                        }
                    }

                    qaPairs.add(cells);
                }
            }

            return new ParsedDocument(filename, "xlsx", null, qaPairs);
        }
    }

    private String parseTxt(InputStream is) throws IOException {
        return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private String parsePdf(byte[] data) throws IOException {
        try (PDDocument doc = Loader.loadPDF(data)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }

    private String parseDocx(InputStream is) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(is)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text).append("\n");
                }
            }
            for (org.apache.poi.xwpf.usermodel.XWPFTable table : doc.getTables()) {
                for (org.apache.poi.xwpf.usermodel.XWPFTableRow row : table.getRows()) {
                    List<String> cells = new ArrayList<>();
                    for (org.apache.poi.xwpf.usermodel.XWPFTableCell cell : row.getTableCells()) {
                        String cellText = cell.getText();
                        if (cellText != null && !cellText.isBlank()) {
                            cells.add(cellText.trim());
                        }
                    }
                    if (!cells.isEmpty()) {
                        sb.append(String.join(" | ", cells)).append("\n");
                    }
                }
            }
            return sb.toString();
        }
    }

    private String parseXlsx(InputStream is) throws IOException {
        try (Workbook wb = new XSSFWorkbook(is)) {
            StringBuilder sb = new StringBuilder();
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                String sheetName = sheet.getSheetName();
                if (sheetName != null && !sheetName.isBlank()) {
                    sb.append("## ").append(sheetName).append("\n\n");
                }
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (Cell cell : row) {
                        String cellValue = getCellValue(cell);
                        if (cellValue != null && !cellValue.isBlank()) {
                            cells.add(cellValue);
                        }
                    }
                    if (!cells.isEmpty()) {
                        sb.append(String.join(" | ", cells)).append("\n");
                    }
                }
                sb.append("\n");
            }
            return sb.toString();
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> "";
        };
    }

    /**
     * 解析结果
     * @param filename 文件名
     * @param fileType 文件类型
     * @param text 提取的纯文本（prose 模式使用）
     * @param qaPairs 提取的 QA 对列表（qa 模式使用，Excel 专用）
     */
    public record ParsedDocument(String filename, String fileType, String text, List<String[]> qaPairs) {}
}
