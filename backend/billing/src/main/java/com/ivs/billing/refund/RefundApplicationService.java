package com.ivs.billing.refund;

import com.ivs.billing.invoice.BillingReferencePort;
import com.ivs.billing.refund.api.RefundApi;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import com.ivs.shared.security.CurrentActorProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RefundApplicationService {
    private final RefundOrderRepository repository;
    private final BillingReferencePort references;
    private final RefundApprovalPort approval;
    private final CurrentActorProvider actors;

    public RefundApplicationService(RefundOrderRepository repository, BillingReferencePort references,
                                    RefundApprovalPort approval, CurrentActorProvider actors) {
        this.repository = repository;
        this.references = references;
        this.approval = approval;
        this.actors = actors;
    }

    @Transactional(readOnly = true)
    public List<RefundApi.RefundView> list() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public RefundApi.RefundView get(UUID id) {
        return toView(find(id));
    }

    @Transactional
    public RefundApi.RefundView create(RefundApi.CreateRefundCommand command) {
        String customerName = references.customerName(command.customerId());
        RefundOrder refund;
        try {
            refund = RefundOrder.create("REF-%06d".formatted(repository.nextRefundSequence()),
                    command.customerId(), command.amount(), command.method(), command.sourceType(),
                    command.sourceNo(), command.reason(), currentUserId());
        } catch (IllegalArgumentException exception) {
            throw new ConflictException("REFUND_INVALID", exception.getMessage());
        }
        RefundOrder saved = repository.save(refund);
        RefundApprovalPort.Decision decision = approval.evaluate(saved.getAmount());
        if (decision.required()) {
            approval.syncPending(saved.getId(), saved.getRefundNo(), customerName, saved.getAmount(), decision);
        } else {
            saved.approve(currentUserId());
            repository.save(saved);
        }
        return toView(saved);
    }

    @Transactional
    public RefundApi.RefundView approve(UUID id) {
        RefundOrder refund = find(id);
        try {
            refund.approve(currentUserId());
        } catch (IllegalStateException exception) {
            throw new ConflictException("REFUND_STATUS_INVALID", exception.getMessage());
        }
        return toView(repository.save(refund));
    }

    @Transactional
    public RefundApi.RefundView reject(UUID id) {
        RefundOrder refund = find(id);
        try {
            refund.reject();
        } catch (IllegalStateException exception) {
            throw new ConflictException("REFUND_STATUS_INVALID", exception.getMessage());
        }
        return toView(repository.save(refund));
    }

    @Transactional
    public RefundApi.RefundView execute(UUID id, RefundApi.ExecuteRefundCommand command) {
        RefundOrder refund = find(id);
        try {
            refund.execute(command.success(), command.failureReason(), command.remark());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ConflictException("REFUND_EXECUTION_INVALID", exception.getMessage());
        }
        return toView(repository.save(refund));
    }

    @Transactional
    public RefundApi.RefundView manualComplete(UUID id, RefundApi.ManualCompleteCommand command) {
        RefundOrder refund = find(id);
        try {
            refund.manualComplete(command == null ? null : command.remark());
        } catch (IllegalStateException exception) {
            throw new ConflictException("REFUND_STATUS_INVALID", exception.getMessage());
        }
        return toView(repository.save(refund));
    }

    @Transactional
    public RefundApi.RefundView cancel(UUID id) {
        RefundOrder refund = find(id);
        try {
            refund.cancel();
        } catch (IllegalStateException exception) {
            throw new ConflictException("REFUND_STATUS_INVALID", exception.getMessage());
        }
        RefundOrder saved = repository.save(refund);
        approval.cancel(id);
        return toView(saved);
    }

    private RefundOrder find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("退款单", id));
    }

    private RefundApi.RefundView toView(RefundOrder refund) {
        return RefundApi.RefundView.from(refund, references.customerName(refund.getCustomerId()));
    }

    private UUID currentUserId() {
        return actors.current().map(actor -> actor.userId()).orElse(null);
    }
}
