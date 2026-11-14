package com.ivs.billing.purchase;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 标准电子发票 PDF 的文字层提取与票面字段解析。 */
@Component
public class PurchaseInvoicePdfParser {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern INVOICE_NO = Pattern.compile(
            "(?:发票号码|发票号)[:：\\s]*([0-9A-Z]{8,24})", Pattern.CASE_INSENSITIVE);
    private static final Pattern INVOICE_TYPE = Pattern.compile(
            "电\\s*子\\s*发\\s*票\\s*[（(]\\s*([^）)\\r\\n]{2,60})\\s*[）)]");
    private static final Pattern ELECTRONIC_INVOICE_LABEL = Pattern.compile("电\\s*子\\s*发\\s*票");
    private static final Pattern ISSUED_AT = Pattern.compile(
            "(?:开票日期|开票时间)[:：\\s]*((?:\\d{4}[年./-]\\d{1,2}[月./-]\\d{1,2}日?)(?:\\s*\\d{1,2}[:：]\\d{2}(?::\\d{2})?)?)");
    private static final Pattern AMOUNT = Pattern.compile(
            "(?:价税合计(?:\\s*[（(]小写[）)])?|小写)[:：\\s]*[¥￥]?\\s*(-?[0-9,]+(?:\\.[0-9]{1,2})?)");
    private static final Pattern TAX_AMOUNT = Pattern.compile(
            "(?:合计税额|税额)[:：\\s]*[¥￥]?\\s*(-?[0-9,]+(?:\\.[0-9]{1,2})?)");
    private static final Pattern TAX_RATE = Pattern.compile("(?:税率|征收率)[:：\\s]*(\\d+(?:\\.\\d+)?)\\s*%");
    private static final Pattern SELLER_NAME = Pattern.compile(
            "(?:销售方(?:信息)?[\\s\\S]{0,360}?|销售方)名称[:：\\s]*([^\\n\\r]{2,100})");
    private static final Pattern BUYER_NAME = Pattern.compile(
            "购买方(?:信息)?[\\s\\S]{0,160}?名称[:：\\s]*([^\\n\\r]{2,100})");
    private static final Pattern SELLER_TAX_NO = Pattern.compile(
            "销售方(?:信息)?[\\s\\S]{0,220}?纳税人识别号[:：\\s]*([0-9A-Z]{15,20})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BUYER_TAX_NO = Pattern.compile(
            "购买方(?:信息)?[\\s\\S]{0,220}?纳税人识别号[:：\\s]*([0-9A-Z]{15,20})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern VIN = Pattern.compile("(?<![A-Z0-9])([A-HJ-NPR-Z0-9]{17})(?![A-Z0-9])");
    private static final Pattern LABELLED_VIN = Pattern.compile(
            "(?:车辆识别代号(?:\\s*[/／]\\s*车架号(?:码)?)?|车架号(?:码)?|VIN)[:：\\s]*([A-HJ-NPR-Z0-9]{17})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDER_NO = Pattern.compile(
            "(?:采购订单编号|订单编号|订单号)[:：\\s]*([A-Za-z0-9_-]{4,40})");

    public ParsedInvoice parse(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                throw new IOException("PDF 已加密，无法读取");
            }
            String text = new PDFTextStripper().getText(document);
            return parseText(text);
        }
    }

    public ParsedInvoice parseText(String text) {
        String raw = normalize(text);
        String invoiceNo = match(INVOICE_NO, raw);
        String invoiceType = invoiceType(raw);
        String amountText = match(AMOUNT, raw);
        String taxText = match(TAX_AMOUNT, raw);
        String rateText = match(TAX_RATE, raw);
        String issuedText = match(ISSUED_AT, raw);
        BigDecimal amount = decimal(amountText);
        BigDecimal taxAmount = decimal(taxText);
        BigDecimal taxRate = rateText == null ? null : decimal(rateText).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        Instant issuedAt = issuedAt(issuedText);
        String sellerName = cleanName(match(SELLER_NAME, raw));
        String sellerTaxNo = match(SELLER_TAX_NO, raw);
        String buyerName = cleanName(match(BUYER_NAME, raw));
        String buyerTaxNo = match(BUYER_TAX_NO, raw);
        boolean red = raw.contains("红字") || raw.contains("冲红") || (amount != null && amount.signum() < 0);
        List<String> vins = vinCandidates(raw.toUpperCase());
        List<String> orderNos = matches(ORDER_NO, raw);
        int confidence = 0;
        if (invoiceNo != null) confidence++;
        if (invoiceType != null) confidence++;
        if (issuedAt != null) confidence++;
        if (amount != null) confidence++;
        if (taxAmount != null) confidence++;
        if (sellerName != null || sellerTaxNo != null) confidence++;
        if (buyerName != null || buyerTaxNo != null) confidence++;
        boolean extracted = raw.length() >= 20;
        String warning = extracted
                ? (confidence < 3 ? "已提取到文字，但票面字段识别较少，请重点人工核对。" : "已自动识别，请核对后登记。")
                : "PDF 未提取到文字层，可能是扫描图片发票；当前版本需要手工录入，OCR 可作为后续增强。";
        return new ParsedInvoice(raw, invoiceNo, invoiceType, red ? "RED" : "BLUE", amount == null ? null : amount.abs(),
                taxAmount == null ? null : taxAmount.abs(), taxRate, issuedAt, sellerName, sellerTaxNo,
                buyerName, buyerTaxNo, vins, orderNos, confidence, extracted, warning);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("[\\t\\x0B\\f\\r]+", " ");
    }

    private static String match(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static List<String> matches(Pattern pattern, String text) {
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) values.add(matcher.group(1));
        return new ArrayList<>(values);
    }

    private static List<String> vinCandidates(String text) {
        Set<String> values = new LinkedHashSet<>(matches(LABELLED_VIN, text));
        if (values.isEmpty()) values.addAll(matches(VIN, text));
        return new ArrayList<>(values);
    }

    private static String invoiceType(String text) {
        String detail = match(INVOICE_TYPE, text);
        if (detail != null) return "电子发票（" + detail.trim() + "）";
        return ELECTRONIC_INVOICE_LABEL.matcher(text).find() ? "电子发票" : null;
    }

    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.replace(",", "").trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Instant issuedAt(String value) {
        if (value == null || value.isBlank()) return null;
        Matcher matcher = Pattern.compile(
                "(\\d{4})[年./-](\\d{1,2})[月./-](\\d{1,2})日?(?:\\s*(\\d{1,2})[:：](\\d{2})(?::(\\d{2}))?)?")
                .matcher(value);
        if (!matcher.find()) return null;
        LocalDate date = LocalDate.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)));
        LocalTime time = matcher.group(4) == null ? LocalTime.MIDNIGHT
                : LocalTime.of(Integer.parseInt(matcher.group(4)), Integer.parseInt(matcher.group(5)),
                matcher.group(6) == null ? 0 : Integer.parseInt(matcher.group(6)));
        return LocalDateTime.of(date, time).atZone(ZONE).toInstant();
    }

    private static String cleanName(String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("\\s{2,}.*$", "").trim();
        return cleaned.length() > 100 ? cleaned.substring(0, 100) : cleaned;
    }

    public record ParsedInvoice(
            String rawText, String invoiceNo, String invoiceType, String type, BigDecimal amount, BigDecimal taxAmount,
            BigDecimal taxRate, Instant issuedAt, String sellerName, String sellerTaxNo,
            String buyerName, String buyerTaxNo, List<String> vinCandidates,
            List<String> orderNoCandidates, int confidence, boolean textExtracted, String warning
    ) {
    }
}
