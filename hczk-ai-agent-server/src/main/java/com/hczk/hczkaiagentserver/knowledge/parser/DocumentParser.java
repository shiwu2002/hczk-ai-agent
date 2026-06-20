package com.hczk.hczkaiagentserver.knowledge.parser;

import com.hczk.hczkaiagentserver.knowledge.config.OcrProperties;
import com.hczk.hczkaiagentserver.knowledge.ocr.OcrService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class DocumentParser {

    private static final List<String> SUPPORTED_EXTENSIONS = List.of(
            ".txt", ".pdf", ".docx", ".xlsx"
    );

    /** 章节标题正则：匹配"第X章"、"X. 标题"、"X、标题"、"Chapter X" 等 */
    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
            "^\\s*(?:第[一二三四五六七八九十百千零\\d]+[章节篇部]|" +
            "Chapter\\s+\\d+|" +
            "[\\d]+[.、]\\s*[\\u4e00-\\u9fa5\\w]+|" +
            "[一二三四五六七八九十]+[、.]\\s*[\\u4e00-\\u9fa5\\w]+)\\s*$",
            Pattern.MULTILINE
    );

    /** 表格行正则（Markdown 表格或 | 分隔） */
    private static final Pattern TABLE_ROW_PATTERN = Pattern.compile("^\\s*\\|.*\\|\\s*$");

    private final OcrService ocrService;
    private final OcrProperties ocrProperties;

    public DocumentParser(OcrService ocrService, OcrProperties ocrProperties) {
        this.ocrService = ocrService;
        this.ocrProperties = ocrProperties;
    }

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
     * 解析上传的文档文件，提取纯文本内容（兼容旧接口）
     */
    public ParsedDocument parse(MultipartFile file) throws IOException {
        ParsedDocumentWithPages doc = parseWithPages(file);
        // 合并所有页文本为纯文本（兼容旧调用方）
        StringBuilder sb = new StringBuilder();
        for (ParsedPage page : doc.pages()) {
            sb.append(page.text()).append("\n\n");
        }
        return new ParsedDocument(doc.filename(), doc.fileType(), sb.toString(), null);
    }

    /**
     * 解析文档，保留页码、章节、上下文信息（v11 新增）
     * 这是多模态溯源入库的核心入口
     */
    public ParsedDocumentWithPages parseWithPages(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        String fileType = getFileType(filename);
        List<ParsedPage> pages;

        try (InputStream is = file.getInputStream()) {
            pages = switch (fileType) {
                case "txt" -> parseTxtWithPages(is);
                case "pdf" -> parsePdfWithPages(file.getBytes(), filename);
                case "docx" -> parseDocxWithPages(is);
                case "xlsx" -> parseXlsxWithPages(is);
                default -> throw new IllegalArgumentException("不支持的文件类型: " + fileType);
            };
        }

        return new ParsedDocumentWithPages(filename, fileType, pages);
    }

    /**
     * 解析 Excel 文件为 QA 对列表（docType=qa 时使用）
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
                    if (row == null) continue;

                    String[] cells = new String[Math.max(row.getLastCellNum(), 2)];
                    boolean hasContent = false;

                    for (int c = 0; c < cells.length; c++) {
                        Cell cell = row.getCell(c);
                        cells[c] = cell != null ? getCellValue(cell) : "";
                        if (!cells[c].isBlank()) hasContent = true;
                    }

                    if (!hasContent) continue;

                    if (firstRow) {
                        firstRow = false;
                        String firstCell = cells[0].toLowerCase();
                        if (firstCell.contains("问题") || firstCell.contains("question")
                                || firstCell.contains("序号") || firstCell.contains("no")
                                || firstCell.contains("id")) {
                            continue;
                        }
                    }

                    qaPairs.add(cells);
                }
            }

            return new ParsedDocument(filename, "xlsx", null, qaPairs);
        }
    }

    // ===== 逐页解析方法 =====

    private List<ParsedPage> parseTxtWithPages(InputStream is) throws IOException {
        String text = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        // TXT 无页码概念，按章节切分为"页"
        return splitByChapters(text, "txt");
    }

    /**
     * PDF 逐页解析：先尝试文本提取，若为扫描件则走 OCR
     */
    private List<ParsedPage> parsePdfWithPages(byte[] data, String filename) throws IOException {
        List<ParsedPage> pages = new ArrayList<>();

        try (PDDocument doc = Loader.loadPDF(data)) {
            int pageCount = doc.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            // 第一遍：提取所有页文本，判断是否为扫描件
            List<String> pageTexts = new ArrayList<>();
            int totalChars = 0;
            for (int i = 0; i < pageCount; i++) {
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);
                String pageText = stripper.getText(doc).trim();
                pageTexts.add(pageText);
                totalChars += pageText.length();
            }

            boolean needOcr = ocrService.isEnabled()
                    && pageCount > 0
                    && (totalChars / pageCount) < ocrProperties.getScannedDetectCharsPerPage();

            if (needOcr) {
                log.info("检测到扫描件 PDF（平均 {}/页 < 阈值 {}），启用 OCR: {}",
                        pageCount > 0 ? totalChars / pageCount : 0,
                        ocrProperties.getScannedDetectCharsPerPage(), filename);
            }

            // 第二遍：构建 ParsedPage
            PDFRenderer renderer = null;
            if (needOcr) {
                renderer = new PDFRenderer(doc);
            }

            String currentChapter = "";
            for (int i = 0; i < pageCount; i++) {
                String pageText = pageTexts.get(i);

                // 扫描件走 OCR
                if (needOcr) {
                    try {
                        BufferedImage image = renderer.renderImageWithDPI(i, ocrProperties.getRenderDpi());
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(image, "png", baos);
                        pageText = ocrService.ocrImage(baos.toByteArray());
                        log.info("PDF 第 {} 页 OCR 完成，识别 {} 字符", i + 1, pageText.length());
                    } catch (Exception e) {
                        log.warn("PDF 第 {} 页 OCR 失败: {}", i + 1, e.getMessage());
                        pageText = pageTexts.get(i); // 回退到文本提取结果
                    }
                }

                // 检测章节标题
                String detectedChapter = detectChapter(pageText);
                if (detectedChapter != null) {
                    currentChapter = detectedChapter;
                }

                // 检测表格
                boolean hasTable = detectTable(pageText);

                pages.add(new ParsedPage(
                        i + 1,               // pageNumber (1-based)
                        pageText,
                        currentChapter,
                        hasTable,
                        false,                // isTableContinuation（后续合并时设置）
                        filename
                ));
            }
        }

        // 后处理：标记跨页表格的延续页
        markCrossPageTables(pages);

        return pages;
    }

    private List<ParsedPage> parseDocxWithPages(InputStream is) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(is)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text).append("\n");
                }
            }
            // DOCX 表格保留为 Markdown 格式
            for (org.apache.poi.xwpf.usermodel.XWPFTable table : doc.getTables()) {
                sb.append("\n");
                boolean firstRow = true;
                for (org.apache.poi.xwpf.usermodel.XWPFTableRow row : table.getRows()) {
                    List<String> cells = new ArrayList<>();
                    for (org.apache.poi.xwpf.usermodel.XWPFTableCell cell : row.getTableCells()) {
                        String cellText = cell.getText();
                        cells.add(cellText != null ? cellText.trim() : "");
                    }
                    sb.append("| ").append(String.join(" | ", cells)).append(" |\n");
                    if (firstRow) {
                        // 添加 Markdown 表格分隔行
                        sb.append("|");
                        for (int c = 0; c < cells.size(); c++) sb.append(" --- |");
                        sb.append("\n");
                        firstRow = false;
                    }
                }
                sb.append("\n");
            }
            // DOCX 无页码，按章节切分
            return splitByChapters(sb.toString(), "docx");
        }
    }

    private List<ParsedPage> parseXlsxWithPages(InputStream is) throws IOException {
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
            return splitByChapters(sb.toString(), "xlsx");
        }
    }

    // ===== 章节检测与跨页表格处理 =====

    /**
     * 检测文本中的章节标题
     * 返回检测到的章节标题，未检测到返回 null
     */
    private String detectChapter(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher matcher = CHAPTER_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group().trim();
        }
        return null;
    }

    /**
     * 检测页面是否包含表格（基于 Markdown 表格格式或 | 分隔）
     */
    private boolean detectTable(String text) {
        if (text == null) return false;
        String[] lines = text.split("\n");
        int tableLineCount = 0;
        for (String line : lines) {
            if (TABLE_ROW_PATTERN.matcher(line).matches()) {
                tableLineCount++;
            }
        }
        return tableLineCount >= 2; // 至少两行才算表格
    }

    /**
     * 标记跨页表格的延续页
     * 规则：如果上一页以表格结尾，且当前页以表格开头，则当前页标记为表格延续
     */
    private void markCrossPageTables(List<ParsedPage> pages) {
        for (int i = 1; i < pages.size(); i++) {
            ParsedPage prev = pages.get(i - 1);
            ParsedPage curr = pages.get(i);

            // 上一页以表格行结尾，当前页以表格行开头 → 跨页表格
            boolean prevEndsWithTable = endsWithTableRow(prev.text());
            boolean currStartsWithTable = startsWithTableRow(curr.text());

            if (prevEndsWithTable && currStartsWithTable) {
                pages.set(i, new ParsedPage(
                        curr.pageNumber(),
                        curr.text(),
                        curr.chapter(),
                        curr.hasTable(),
                        true,  // isTableContinuation
                        curr.sourceFilename()
                ));
                log.debug("检测到跨页表格: 第 {} 页延续第 {} 页的表格", curr.pageNumber(), prev.pageNumber());
            }
        }
    }

    private boolean endsWithTableRow(String text) {
        if (text == null || text.isBlank()) return false;
        String[] lines = text.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            return TABLE_ROW_PATTERN.matcher(line).matches();
        }
        return false;
    }

    private boolean startsWithTableRow(String text) {
        if (text == null || text.isBlank()) return false;
        String[] lines = text.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            return TABLE_ROW_PATTERN.matcher(trimmed).matches();
        }
        return false;
    }

    /**
     * 按章节将文本切分为"页"（用于 TXT/DOCX/XLSX 等无页码的文档）
     */
    private List<ParsedPage> splitByChapters(String text, String sourceFilename) {
        List<ParsedPage> pages = new ArrayList<>();
        if (text == null || text.isBlank()) return pages;

        String[] lines = text.split("\n");
        StringBuilder currentSection = new StringBuilder();
        String currentChapter = "前言";
        int sectionIndex = 0;

        for (String line : lines) {
            String detectedChapter = detectChapter(line);
            if (detectedChapter != null && currentSection.length() > 0) {
                // 保存当前章节
                pages.add(new ParsedPage(
                        sectionIndex + 1,
                        currentSection.toString().trim(),
                        currentChapter,
                        detectTable(currentSection.toString()),
                        false,
                        sourceFilename
                ));
                sectionIndex++;
                currentSection = new StringBuilder();
                currentChapter = detectedChapter;
            }
            currentSection.append(line).append("\n");
        }

        // 最后一节
        if (currentSection.length() > 0) {
            pages.add(new ParsedPage(
                    sectionIndex + 1,
                    currentSection.toString().trim(),
                    currentChapter,
                    detectTable(currentSection.toString()),
                    false,
                    sourceFilename
            ));
        }

        return pages;
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

    // ===== 数据结构 =====

    /**
     * 旧版解析结果（兼容）
     */
    public record ParsedDocument(String filename, String fileType, String text, List<String[]> qaPairs) {}

    /**
     * v11 新增：带页码和章节信息的解析结果
     */
    public record ParsedDocumentWithPages(String filename, String fileType, List<ParsedPage> pages) {}

    /**
     * v11 新增：单页解析结果
     *
     * @param pageNumber         页码（PDF 为实际页码，TXT/DOCX 为章节序号）
     * @param text               页面文本内容
     * @param chapter            所属章节标题
     * @param hasTable           是否包含表格
     * @param isTableContinuation 是否为上一页表格的延续（跨页表格）
     * @param sourceFilename     源文件名
     */
    public record ParsedPage(
            int pageNumber,
            String text,
            String chapter,
            boolean hasTable,
            boolean isTableContinuation,
            String sourceFilename
    ) {}
}
