package com.ivs.billing.purchase;

import com.ivs.billing.purchase.api.PurchaseInvoiceApi;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import com.ivs.shared.event.EventEnvelope;
import com.ivs.shared.event.EventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 进项（厂家）红字发票登记：登记后自动把金额写入返利资金池，
 * 形成「厂家红字 → 返利入池 → 入库单抵扣车款」的闭环。
 */
@Service
public class PurchaseInvoiceApplicationService {

    private final PurchaseInvoiceRepository repository;
    private final RebateCreditPort rebateCreditPort;
    private final ClaimInvoicePort claimInvoicePort;
    private final EventPublisher events;

    public PurchaseInvoiceApplicationService(PurchaseInvoiceRepository repository,
                                             RebateCreditPort rebateCreditPort,
                                             ClaimInvoicePort claimInvoicePort,
                                             EventPublisher events) {
        this.repository = repository;
        this.rebateCreditPort = rebateCreditPort;
        this.claimInvoicePort = claimInvoicePort;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<PurchaseInvoiceApi.PurchaseInvoiceView> list(UUID supplierId) {
        List<PurchaseInvoice> rows = supplierId == null ? repository.findAllByOrderByCreatedAtDesc()
                : repository.findBySupplierIdOrderByCreatedAtDesc(supplierId);
        return rows.stream().map(PurchaseInvoiceApi.PurchaseInvoiceView::from).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseInvoiceApi.PurchaseInvoiceView get(UUID id) {
        return repository.findById(id).map(PurchaseInvoiceApi.PurchaseInvoiceView::from)
                .orElseThrow(() -> new NotFoundException("进项发票", id));
    }

    /** 登记厂家红字发票，并自动入池（同一事务：入池失败则发票登记回滚）。 */
    @Transactional
    public PurchaseInvoiceApi.PurchaseInvoiceView registerRed(
            PurchaseInvoiceApi.RegisterRedInvoiceCommand command) {
        if (command.supplierId() == null) {
            throw new ConflictException("SUPPLIER_REQUIRED", "请指定开票厂家");
        }
        if (command.claimId() != null) {
            claimInvoicePort.validateForInvoice(command.claimId(), command.supplierId(), command.amount());
        }
        PurchaseInvoice invoice;
        try {
            invoice = PurchaseInvoice.registerRed(
                    "PINV-R-%06d".formatted(repository.nextInvoiceSequence()), command.supplierId(),
                    command.amount(), command.taxRate(), command.originalInvoiceNo(),
                    command.externalInvoiceNo(), command.claimId(), command.remark());
        } catch (IllegalArgumentException exception) {
            throw new ConflictException("PURCHASE_INVOICE_INVALID", exception.getMessage());
        }
        PurchaseInvoice saved = repository.save(invoice);
        if (saved.getClaimId() != null) {
            claimInvoicePort.settleFromInvoice(saved.getClaimId(), saved.getId(), saved.getInvoiceNo());
        }
        rebateCreditPort.credit(saved.getSupplierId(), saved.getAmount(), "RED_INVOICE", saved.getInvoiceNo(),
                saved.getId(), "厂家红字发票入池");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("invoiceNo", saved.getInvoiceNo());
        payload.put("supplierId", saved.getSupplierId().toString());
        payload.put("amount", saved.getAmount());
        payload.put("taxAmount", saved.getTaxAmount());
        events.publish(new EventEnvelope(UUID.randomUUID(), "PurchaseInvoiceRegistered", "PurchaseInvoice",
                saved.getId(), saved.getVersion(), null, UUID.randomUUID(), Instant.now(), payload));
        return PurchaseInvoiceApi.PurchaseInvoiceView.from(saved);
    }

    /** 登记厂家蓝字进项发票，用于与供应商应付匹配。 */
    @Transactional
    public PurchaseInvoiceApi.PurchaseInvoiceView registerBlue(
            PurchaseInvoiceApi.RegisterBlueInvoiceCommand command) {
        if (command.supplierId() == null) {
            throw new ConflictException("SUPPLIER_REQUIRED", "请指定开票厂家");
        }
        PurchaseInvoice invoice;
        try {
            invoice = PurchaseInvoice.registerBlue(
                    "PINV-B-%06d".formatted(repository.nextInvoiceSequence()), command.supplierId(),
                    command.amount(), command.taxRate(), command.externalInvoiceNo(),
                    command.issuedAt(), command.remark());
        } catch (IllegalArgumentException exception) {
            throw new ConflictException("PURCHASE_INVOICE_INVALID", exception.getMessage());
        }
        PurchaseInvoice saved = repository.save(invoice);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("invoiceNo", saved.getInvoiceNo());
        payload.put("supplierId", saved.getSupplierId().toString());
        payload.put("amount", saved.getAmount());
        payload.put("taxAmount", saved.getTaxAmount());
        events.publish(new EventEnvelope(UUID.randomUUID(), "PurchaseInvoiceRegistered", "PurchaseInvoice",
                saved.getId(), saved.getVersion(), null, UUID.randomUUID(), Instant.now(), payload));
        return PurchaseInvoiceApi.PurchaseInvoiceView.from(saved);
    }
}
