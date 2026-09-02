package io.github.sandbox.admin.common.util;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 轻量导出工具（T-0045/T-0043）：CSV 与 SpreadsheetML（Excel 可直接打开的 XML 表格）。
 *
 * <p>不引入第三方依赖（工程硬约束：不额外扩展依赖面）：
 * CSV 带 UTF-8 BOM（Excel 双击不乱码）；SpreadsheetML 2003 为 W3C 提交、
 * Microsoft Excel / WPS 原生支持的表格 XML，可直接以 .xls 打开。</p>
 */
public final class ExportUtil {

    private ExportUtil() {
    }

    /** 生成 CSV 字节（UTF-8 BOM + RFC4180 转义） */
    public static byte[] toCsv(List<String> headers, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append((char) 0xFEFF); // UTF-8 BOM，Excel 双击打开不乱码
        appendCsvRow(sb, headers.stream().map(h -> (Object) h).toList());
        for (List<Object> row : rows) {
            appendCsvRow(sb, row);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** 生成 SpreadsheetML（Excel 兼容 XML）字节 */
    public static byte[] toExcelXml(String sheetName, List<String> headers, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<?mso-application progid=\"Excel.Sheet\"?>");
        sb.append("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"")
                .append(" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">");
        sb.append("<Worksheet ss:Name=\"").append(escapeXml(abbreviate(sheetName, 30))).append("\"><Table>");
        sb.append("<Row>");
        for (String h : headers) {
            sb.append("<Cell><Data ss:Type=\"String\">").append(escapeXml(h)).append("</Data></Cell>");
        }
        sb.append("</Row>");
        for (List<Object> row : rows) {
            sb.append("<Row>");
            for (Object cell : row) {
                if (cell == null) {
                    sb.append("<Cell><Data ss:Type=\"String\"></Data></Cell>");
                } else if (cell instanceof Number n) {
                    sb.append("<Cell><Data ss:Type=\"Number\">").append(n).append("</Data></Cell>");
                } else {
                    // 换行/控制字符在 XML 文本节点中合法保留（Excel 单元格内多行显示）
                    sb.append("<Cell><Data ss:Type=\"String\">")
                            .append(escapeXml(stripIllegalXml(String.valueOf(cell))))
                            .append("</Data></Cell>");
                }
            }
            sb.append("</Row>");
        }
        sb.append("</Table></Worksheet></Workbook>");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendCsvRow(StringBuilder sb, List<Object> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            Object c = cells.get(i);
            if (c == null) {
                continue;
            }
            String s = String.valueOf(c);
            boolean needQuote = s.indexOf(',') >= 0 || s.indexOf('"') >= 0
                    || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
            if (needQuote) {
                sb.append('"').append(s.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(s);
            }
        }
        sb.append("\r\n");
    }

    /**
     * XML 实体转义。注意：实体串以拼接方式构造，避免源码中字面 & 序列被
     * 部分工具链（编辑器/管道）按 HTML 实体解码而破坏源文件。
     */
    private static String escapeXml(String s) {
        if (s == null) {
            return "";
        }
        String amp = "&" + "amp;";
        String lt = "&" + "lt;";
        String gt = "&" + "gt;";
        String quot = "&" + "quot;";
        String apos = "&" + "apos;";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '&' -> sb.append(amp);
                case '<' -> sb.append(lt);
                case '>' -> sb.append(gt);
                case '"' -> sb.append(quot);
                case '\'' -> sb.append(apos);
                default -> sb.append(ch);
            }
        }
        return sb.toString();
    }

    /** 去除 XML 1.0 非法控制字符（保留 \t \n \r） */
    private static String stripIllegalXml(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\t' || ch == '\n' || ch == '\r' || ch >= 0x20) {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static String abbreviate(String s, int max) {
        if (s == null || s.isBlank()) {
            return "export";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
