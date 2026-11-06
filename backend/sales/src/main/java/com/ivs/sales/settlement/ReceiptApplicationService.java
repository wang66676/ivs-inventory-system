package com.ivs.sales.settlement;

import com.ivs.sales.order.SalesMasterDataPort;
import com.ivs.sales.order.SalesOrderRepository;
import com.ivs.sales.order.SalesOrder;
import com.ivs.sales.order.SalesOrderApplicationService;
import com.ivs.sales.order.api.SalesApi;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 应收与收款核销。 */
@Service
public class ReceiptApplicationService {

    private final ReceivableRepository receivableRepository;
    private final ReceiptRepository receiptRepository;
    private final ReceiptAllocationRepository allocationRepository;
    private final SalesMasterDataPort masterData;
    private final SalesOrderRepository orderRepository;
    private final SalesOrderApplicationService orderService;
    private final InstallmentPlanService installmentPlanService;

    public ReceiptApplicationService(ReceivableRepository receivableRepository,
                                     ReceiptRepository receiptRepository,
                                     ReceiptAllocationRepository allocationRepository,
                                     SalesMasterDataPort masterData,
                                     SalesOrderRepository orderRepository,
                                     SalesOrderApplicationService orderService,
                                     InstallmentPlanService installmentPlanService) {
        this.receivableRepository = receivableRepository;
        this.receiptRepository = receiptRepository;
        this.allocationRepository = allocationRepository;
        this.masterData = masterData;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.installmentPlanService = installmentPlanService;
    }

    @Transactional(readOnly = true)
    public List<SalesApi.ReceivableView> listReceivables(UUID customerId) {
        List<Receivable> receivables = customerId == null
                ? receivableRepository.findAllByOrderByCreatedAtDesc()
                : receivableRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
        return receivables.stream().map(this::toView).toList();
    }

    /**
     * 登记收款并按到期日 FIFO 核销应收；超过未收总额的部分直接拒绝（避免预收挂账）。
     */
    @Transactional
    public SalesApi.ReceiptView createReceipt(SalesApi.CreateReceiptCommand command) {
        SalesMasterDataPort.CustomerInfo customer = masterData.requireCustomer(command.customerId());
        List<Receivable> outstanding = receivableRepository.findOutstandingForUpdate(customer.id());
        if (command.receivableIds() != null && !command.receivableIds().isEmpty()) {
            var selected = new java.util.HashSet<>(command.receivableIds());
            outstanding = outstanding.stream().filter(item -> selected.contains(item.getId())).toList();
            if (outstanding.size() != selected.size()) {
                throw new ConflictException("RECEIVABLE_NOT_FOUND", "选中的分期应收不存在、已结清或不属于当前客户");
            }
        }
        BigDecimal totalOutstanding = outstanding.stream().map(Receivable::outstanding)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal amount = command.amount() == null ? BigDecimal.ZERO : command.amount();
        if (totalOutstanding.signum() == 0) {
            throw new ConflictException("NO_OUTSTANDING_RECEIVABLE", "该客户当前没有未结清应收");
        }
        if (amount.compareTo(totalOutstanding) > 0) {
            throw new ConflictException("RECEIPT_EXCEEDS_OUTSTANDING", "收款金额超过未收总额："
                    + totalOutstanding.toPlainString());
        }
        Receipt receipt;
        try {
            receipt = receiptRepository.save(Receipt.create(
                    "RCPT-%06d".formatted(receiptRepository.nextReceiptSequence()),
                    customer.id(), amount, command.method(), command.remark()));
        } catch (IllegalArgumentException exception) {
            throw new ConflictException("RECEIPT_INVALID", exception.getMessage());
        }
        BigDecimal remaining = amount;
        List<SalesApi.ReceiptAllocationView> allocations = new ArrayList<>();
        for (Receivable receivable : outstanding) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal applied = remaining.min(receivable.outstanding());
            try {
                receivable.allocate(applied);
            } catch (IllegalStateException exception) {
                throw new ConflictException("RECEIPT_ALLOCATION_INVALID", exception.getMessage());
            }
            receivableRepository.save(receivable);
            allocationRepository.save(ReceiptAllocation.create(receipt.getId(), receivable.getId(), applied));
            installmentPlanService.refreshForReceivable(receivable.getId());
            allocations.add(new SalesApi.ReceiptAllocationView(receivable.getId(), receivable.getReceivableNo(), applied));
            remaining = remaining.subtract(applied);
        }
        // 款货两清：该订单应收全部结清则关闭订单
        for (SalesApi.ReceiptAllocationView allocation : allocations) {
            Receivable settled = receivableRepository.findById(allocation.receivableId()).orElse(null);
            if (settled != null && settled.getOrderId() != null && settled.settled()
                    && receivableRepository.findByOrderId(settled.getOrderId()).stream().allMatch(Receivable::settled)) {
                orderService.closeIfSettled(settled.getOrderId());
            }
        }
        return SalesApi.ReceiptView.from(receipt, customer.name(), allocations);
    }

    @Transactional(readOnly = true)
    public List<SalesApi.ReceiptView> listReceipts() {
        return receiptRepository.findAllByOrderByReceivedAtDesc().stream()
                .map(receipt -> SalesApi.ReceiptView.from(receipt,
                        customerName(receipt.getCustomerId()),
                        allocationRepository.findByReceiptId(receipt.getId()).stream()
                                .map(allocation -> new SalesApi.ReceiptAllocationView(allocation.getReceivableId(),
                                        receivableRepository.findById(allocation.getReceivableId())
                                                .map(Receivable::getReceivableNo).orElse(null),
                                        allocation.getAmount()))
                                .toList()))
                .toList();
    }

    /** 应收账龄分档：未结清部分按到期日与今天的天数差分档。 */
    @Transactional(readOnly = true)
    public List<SalesApi.AgingBucketView> aging() {
        List<Receivable> receivables = receivableRepository.findAllByOrderByCreatedAtDesc();
        LocalDate today = LocalDate.now();
        int[] counts = new int[4];
        BigDecimal[] amounts = new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        for (Receivable receivable : receivables) {
            BigDecimal outstanding = receivable.outstanding();
            if (outstanding.signum() <= 0) {
                continue;
            }
            long overdueDays = receivable.getDueDate() == null ? 0
                    : java.time.temporal.ChronoUnit.DAYS.between(receivable.getDueDate(), today);
            int index = overdueDays <= 0 ? 0 : overdueDays <= 30 ? 1 : overdueDays <= 90 ? 2 : 3;
            counts[index]++;
            amounts[index] = amounts[index].add(outstanding);
        }
        String[] labels = {"未到期", "逾期 1-30 天", "逾期 31-90 天", "逾期 90 天以上"};
        List<SalesApi.AgingBucketView> buckets = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            buckets.add(new SalesApi.AgingBucketView(labels[i], counts[i], amounts[i]));
        }
        return buckets;
    }

    @Transactional(readOnly = true)
    public Receivable findReceivable(UUID id) {
        return receivableRepository.findById(id).orElseThrow(() -> new NotFoundException("应收", id));
    }

    private SalesApi.ReceivableView toView(Receivable receivable) {
        String orderNo = receivable.getOrderId() == null ? null
                : orderNoOf(receivable.getOrderId());
        return SalesApi.ReceivableView.from(receivable, customerName(receivable.getCustomerId()), orderNo);
    }

    private String customerName(UUID customerId) {
        return masterData.requireCustomer(customerId).name();
    }

    /** 结算与订单同属销售模块，直接复用订单仓储取单号。 */
    private String orderNoOf(UUID orderId) {
        return orderRepository.findById(orderId).map(SalesOrder::getOrderNo).orElse(null);
    }
}
