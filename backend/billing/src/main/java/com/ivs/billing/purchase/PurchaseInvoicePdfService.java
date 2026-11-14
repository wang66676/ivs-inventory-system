package com.ivs.billing.purchase;

import com.ivs.billing.purchase.api.PurchaseInvoiceApi;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.regex.Pattern;

/** 进项电子发票 PDF 本地存储与自动解析。 */
@Service
public class PurchaseInvoicePdfService {

    private static final long MAX_FILE_SIZE = 20L * 1024L * 1024L;
    private static final Pattern STORAGE_KEY = Pattern.compile("[0-9a-fA-F-]{36}\\.pdf");

    private final PurchaseInvoicePdfParser parser;
    private final Path storageRoot;

    public PurchaseInvoicePdfService(PurchaseInvoicePdfParser parser,
                                     @Value("${ivs.invoice.storage-dir:./data/invoice-files}") String storageDir) {
        this.parser = parser;
        this.storageRoot = Paths.get(storageDir).toAbsolutePath().normalize();
    }

    public PurchaseInvoiceApi.PdfExtractionView extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ConflictException("INVOICE_PDF_REQUIRED", "请选择电子发票 PDF 文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ConflictException("INVOICE_PDF_TOO_LARGE", "电子发票 PDF 不能超过 20MB");
        }
        String originalName = file.getOriginalFilename() == null ? "invoice.pdf" : file.getOriginalFilename();
        String fileName = Paths.get(originalName).getFileName().toString();
        if (!fileName.toLowerCase().endsWith(".pdf")) {
            throw new ConflictException("INVOICE_PDF_TYPE_INVALID", "只支持上传 PDF 格式的电子发票");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length < 5 || !new String(bytes, 0, 5).startsWith("%PDF-")) {
                throw new ConflictException("INVOICE_PDF_CONTENT_INVALID", "文件不是有效的 PDF");
            }
            Files.createDirectories(storageRoot);
            String storageKey = UUID.randomUUID() + ".pdf";
            Files.write(storageRoot.resolve(storageKey), bytes);
            PurchaseInvoicePdfParser.ParsedInvoice parsed = parser.parse(bytes);
            return new PurchaseInvoiceApi.PdfExtractionView(storageKey, fileName, parsed.rawText(),
                    parsed.invoiceNo(), parsed.type(), parsed.amount(), parsed.taxAmount(), parsed.taxRate(),
                    parsed.issuedAt(), parsed.sellerName(), parsed.sellerTaxNo(), parsed.buyerName(),
                    parsed.buyerTaxNo(), parsed.vinCandidates(), parsed.orderNoCandidates(), parsed.confidence(),
                    parsed.textExtracted(), parsed.warning());
        } catch (IOException exception) {
            throw new ConflictException("INVOICE_PDF_PARSE_FAILED", "PDF 读取或解析失败：" + exception.getMessage());
        }
    }

    public String validatedKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || !STORAGE_KEY.matcher(storageKey).matches()) {
            throw new ConflictException("INVOICE_PDF_KEY_INVALID", "电子发票附件标识无效");
        }
        return storageKey;
    }

    public Resource resource(String storageKey) {
        Path path = storageRoot.resolve(validatedKey(storageKey)).normalize();
        if (!path.startsWith(storageRoot) || !Files.isRegularFile(path)) {
            throw new NotFoundException("电子发票附件", storageKey);
        }
        return new FileSystemResource(path);
    }
}
