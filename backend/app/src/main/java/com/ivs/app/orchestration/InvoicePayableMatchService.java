package com.ivs.app.orchestration;

import com.ivs.app.matching.PayableInvoiceMatch;
import com.ivs.app.matching.PayableInvoiceMatchRepository;
import com.ivs.billing.purchase.PurchaseInvoice;
import com.ivs.billing.purchase.PurchaseInvoiceRepository;
import com.ivs.procurement.payable.Payable;
import com.ivs.procurement.payable.PayableRepository;
import com.ivs.procurement.payable.PayableService;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 蓝字进项发票与供应商应付自动匹配：按到期日优先，跨模块编排放在 app 层。 */
@Service
public class InvoicePayableMatchService {
    private final PurchaseInvoiceRepository invoiceRepository;
    private final PayableRepository payableRepository;
    private final PayableService payableService;
    private final PayableInvoiceMatchRepository matchRepository;

    public InvoicePayableMatchService(PurchaseInvoiceRepository invoiceRepository,
                                      PayableRepository payableRepository,
                                      PayableService payableService,
                                      PayableInvoiceMatchRepository matchRepository) {
        this.invoiceRepository = invoiceRepository;
        this.payableRepository = payableRepository;
        this.payableService = payableService;
        this.matchRepository = matchRepository;
    }

    @Transactional
    public MatchResult autoMatch(UUID invoiceId) {
        PurchaseInvoice invoice = invoiceRepository.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new NotFoundException("进项发票", invoiceId));
        if (!"BLUE".equals(invoice.getType())) {
            throw new ConflictException("INVOICE_MATCH_TYPE_INVALID", "只有蓝字进项发票可以匹配应付");
        }
        BigDecimal remaining = invoice.getUnmatchedAmount();
        List<MatchLine> lines = new ArrayList<>();
        if (remaining.signum() > 0) {
            List<Payable> candidates = payableRepository.findBySupplierIdOrderByDueDateAscCreatedAtAsc(
                    invoice.getSupplierId());
            for (Payable candidate : candidates) {
                if (remaining.signum() <= 0) break;
                Payable payable = payableRepository.findByIdForUpdate(candidate.getId()).orElse(candidate);
                BigDecimal amount = payable.getUninvoicedAmount().min(remaining);
                if (amount.signum() <= 0) continue;
                payableService.matchInvoice(payable.getId(), amount);
                matchRepository.save(PayableInvoiceMatch.create(invoiceId, payable.getId(), amount));
                lines.add(new MatchLine(payable.getId(), payable.getPayableNo(), amount));
                remaining = remaining.subtract(amount);
            }
        }
        if (lines.isEmpty()) {
            throw new ConflictException("INVOICE_MATCH_NONE", "没有可匹配的应付单");
        }
        BigDecimal matchedNow = lines.stream().map(MatchLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        invoice.matchAmount(matchedNow);
        invoiceRepository.save(invoice);
        return new MatchResult(invoiceId, matchedNow, invoice.getMatchedAmount(), invoice.getUnmatchedAmount(), lines);
    }

    @Transactional(readOnly = true)
    public MatchResult matches(UUID invoiceId) {
        PurchaseInvoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new NotFoundException("进项发票", invoiceId));
        List<MatchLine> lines = matchRepository.findByInvoiceIdOrderByCreatedAtAsc(invoiceId).stream()
                .map(match -> new MatchLine(match.getPayableId(),
                        payableRepository.findById(match.getPayableId()).map(Payable::getPayableNo).orElse(null),
                        match.getAmount()))
                .toList();
        BigDecimal matched = lines.stream().map(MatchLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new MatchResult(invoiceId, matched, invoice.getMatchedAmount(), invoice.getUnmatchedAmount(), lines);
    }

    public record MatchLine(UUID payableId, String payableNo, BigDecimal amount) {
    }

    public record MatchResult(UUID invoiceId, BigDecimal matchedNow, BigDecimal matchedAmount,
                              BigDecimal unmatchedAmount, List<MatchLine> lines) {
    }
}
