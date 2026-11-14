package com.ivs.app;

import com.ivs.billing.purchase.PurchaseInvoice;
import com.ivs.billing.purchase.PurchaseInvoicePdfParser;
import com.ivs.billing.purchase.PurchaseInvoicePdfService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockMultipartFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** 进项电子发票 PDF 文字层字段识别。 */
@SpringBootTest
@ActiveProfiles("test")
class PurchaseInvoicePdfParserIntegrationTest {

    @Autowired
    private PurchaseInvoicePdfParser parser;

    @Autowired
    private PurchaseInvoicePdfService pdfService;

    @Test
    void 可保存上传PDF并返回文字提取结果() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText("Invoice No: 26112000000012345678 Total: 820000.00");
                stream.endText();
            }
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            document.save(output);
            pdf = output.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile("file", "invoice.pdf", "application/pdf", pdf);
        var result = pdfService.extract(file);
        assertThat(result.storageKey()).endsWith(".pdf");
        assertThat(result.fileName()).isEqualTo("invoice.pdf");
        assertThat(result.textExtracted()).isTrue();
    }

    @Test
    void 根据票面文字识别发票号码开票时间金额和VIN() {
        String text = """
                电子发票（增值税专用发票）
                购买方信息
                名称：天津容大汽车销售有限公司
                纳税人识别号：91120101TEST123456
                销售方信息
                名称：某整车厂
                纳税人识别号：91110000SELLER12345
                发票号码：26112000000012345678
                开票日期：2026年11月14日 14:35
                税率 13%
                税额 ¥94336.28
                价税合计（小写） ¥820000.00
                备注：采购订单编号：CG2026111401
                车辆识别代号/车架号码：LZZ1CLWB0NA123456
                """;
        PurchaseInvoicePdfParser.ParsedInvoice parsed = parser.parseText(text);
        assertThat(parsed.invoiceNo()).isEqualTo("26112000000012345678");
        assertThat(parsed.invoiceType()).isEqualTo("电子发票（增值税专用发票）");
        assertThat(parsed.type()).isEqualTo("BLUE");
        assertThat(parsed.amount()).isEqualByComparingTo("820000.00");
        assertThat(parsed.taxAmount()).isEqualByComparingTo("94336.28");
        assertThat(parsed.taxRate()).isEqualByComparingTo(new BigDecimal("0.1300"));
        assertThat(parsed.issuedAt()).isEqualTo(LocalDateTime.of(2026, 11, 14, 14, 35)
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant());
        assertThat(parsed.sellerName()).contains("某整车厂");
        assertThat(parsed.orderNoCandidates()).contains("CG2026111401");
        assertThat(parsed.vinCandidates()).contains("LZZ1CLWB0NA123456");
        assertThat(parsed.textExtracted()).isTrue();
        assertThat(parsed.confidence()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void 根据机动车电子发票版式识别全部关键字段() {
        String text = """
                肆拾万圆整
                ¥46017.70¥353982.30合 计
                91440300791738553L统一社会信用代码/纳税人识别号:91120116300584378Q统一社会信用代码/纳税人识别号:
                购
                买
                方
                信
                息
                名称:天津容大汽车贸易有限公司
                销
                售
                方
                信
                息
                名称:比亚迪汽车工业有限公司
                项目名称 车辆识别代号/车架号码 单 位 数 量 单 价 金 额 税率/征收率 税 额
                *机动车*比亚迪牌纯电动 LPEKMDBD2TD000093 辆 1 353982.3 353982.30 13% 46017.70
                运输车
                价税合计（大写） （小写）¥400000.00
                开票人:周青
                机动车 电子发票（增值税专用发票）
                2026年10月28日开票日期：
                26957000000273010157发票号码：
                """;
        PurchaseInvoicePdfParser.ParsedInvoice parsed = parser.parseText(text);
        assertThat(parsed.invoiceNo()).isEqualTo("26957000000273010157");
        assertThat(parsed.invoiceType()).isEqualTo("电子发票（增值税专用发票）");
        assertThat(parsed.amount()).isEqualByComparingTo("400000.00");
        assertThat(parsed.taxAmount()).isEqualByComparingTo("46017.70");
        assertThat(parsed.taxRate()).isEqualByComparingTo(new BigDecimal("0.1300"));
        assertThat(parsed.issuedAt()).isEqualTo(LocalDateTime.of(2026, 10, 28, 0, 0)
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant());
        assertThat(parsed.buyerName()).isEqualTo("天津容大汽车贸易有限公司");
        assertThat(parsed.sellerName()).isEqualTo("比亚迪汽车工业有限公司");
        assertThat(parsed.vinCandidates()).containsExactly("LPEKMDBD2TD000093");
    }

    @Test
    void 蓝字发票登记保存发票类型() {
        PurchaseInvoice invoice = PurchaseInvoice.registerBlue("PINV-B-000001", java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(), "LZZ1CLWB0NA123456", new BigDecimal("820000.00"),
                new BigDecimal("0.13"), "电子发票（增值税专用发票）", "26112000000012345678",
                java.time.Instant.now(), null);
        assertThat(invoice.getInvoiceType()).isEqualTo("电子发票（增值税专用发票）");
        assertThat(invoice.getExternalInvoiceNo()).isEqualTo("26112000000012345678");
    }
}
