package com.ivs.sales.settlement;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivs.sales.order.SalesDelivery;
import com.ivs.sales.order.SalesMasterDataPort;
import com.ivs.sales.order.SalesOrder;
import com.ivs.sales.order.SalesOrderLine;
import com.ivs.sales.order.SalesOrderLineRepository;
import com.ivs.sales.order.SalesOrderRepository;
import com.ivs.sales.order.SalesStockPort;
import com.ivs.sales.order.api.SalesApi;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 分期收款计划：生成首付与分期应收，并负责以租代售所有权转移。 */
@Service
public class InstallmentPlanService {

    private final InstallmentPlanRepository planRepository;
    private final InstallmentScheduleRepository scheduleRepository;
    private final ReceivableRepository receivableRepository;
    private final SalesOrderRepository orderRepository;
    private final SalesOrderLineRepository lineRepository;
    private final SalesMasterDataPort masterData;
    private final SalesStockPort stockPort;
    private final ObjectMapper objectMapper;

    public InstallmentPlanService(InstallmentPlanRepository planRepository,
                                  InstallmentScheduleRepository scheduleRepository,
                                  ReceivableRepository receivableRepository,
                                  SalesOrderRepository orderRepository,
                                  SalesOrderLineRepository lineRepository,
                                  SalesMasterDataPort masterData,
                                  SalesStockPort stockPort,
                                  ObjectMapper objectMapper) {
        this.planRepository = planRepository;
        this.scheduleRepository = scheduleRepository;
        this.receivableRepository = receivableRepository;
        this.orderRepository = orderRepository;
        this.lineRepository = lineRepository;
        this.masterData = masterData;
        this.stockPort = stockPort;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InstallmentPlan createForDelivery(SalesOrder order, SalesDelivery delivery, BigDecimal totalAmount,
                                             List<UUID> serialIds) {
        if (planRepository.findByOrderId(order.getId()).isPresent()) {
            throw new ConflictException("INSTALLMENT_PLAN_EXISTS", "该销售订单已存在分期计划");
        }
        InstallmentPlan plan;
        try {
            plan = InstallmentPlan.create("IP-%06d".formatted(planRepository.nextPlanSequence()),
                    order.getId(), order.getCustomerId(), totalAmount, order.getDownPayment(),
                    order.getInstallmentCount(), order.getFirstDueDate());
            plan = planRepository.save(plan);
        } catch (IllegalArgumentException exception) {
            throw new ConflictException("INSTALLMENT_INVALID", exception.getMessage());
        }

        LocalDate deliveryDate = delivery.getDeliveredAt().atZone(ZoneId.systemDefault()).toLocalDate();
        int periodNo = 0;
        if (plan.getDownPayment().signum() > 0) {
            createSchedule(plan, periodNo++, deliveryDate, plan.getDownPayment(), "首付");
        }
        BigDecimal financed = plan.getFinancedAmount();
        BigDecimal regular = financed.divide(BigDecimal.valueOf(plan.getInstallmentCount()), 2, RoundingMode.DOWN);
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 1; i <= plan.getInstallmentCount(); i++) {
            BigDecimal amount = i == plan.getInstallmentCount()
                    ? financed.subtract(allocated)
                    : regular;
            allocated = allocated.add(amount);
            createSchedule(plan, periodNo++, plan.getFirstDueDate().plusMonths(i - 1L), amount, "第 " + i + " 期");
        }
        stockPort.markOwnership(serialIds, true);
        return plan;
    }

    @Transactional
    public void refreshForReceivable(UUID receivableId) {
        InstallmentSchedule schedule = scheduleRepository.findByReceivableId(receivableId).orElse(null);
        if (schedule == null) return;
        InstallmentPlan plan = planRepository.findByIdForUpdate(schedule.getPlanId()).orElse(null);
        if (plan == null || plan.getStatus() == InstallmentPlanStatus.SETTLED) return;
        List<InstallmentSchedule> schedules = scheduleRepository.findByPlanIdOrderByPeriodNoAsc(plan.getId());
        boolean settled = schedules.stream().map(item -> receivableRepository.findById(item.getReceivableId()).orElse(null))
                .allMatch(item -> item != null && item.settled());
        if (settled) {
            plan.settle();
            planRepository.save(plan);
            List<UUID> serialIds = new ArrayList<>();
            for (SalesOrderLine line : lineRepository.findByOrderIdOrderByLineNoAsc(plan.getOrderId())) {
                serialIds.addAll(parseIds(line.getSerialIds()));
            }
            stockPort.markOwnership(serialIds, false);
        }
    }

    @Transactional(readOnly = true)
    public List<SalesApi.InstallmentPlanView> list() {
        return planRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public SalesApi.InstallmentPlanView get(UUID id) {
        return planRepository.findById(id).map(this::toView)
                .orElseThrow(() -> new NotFoundException("分期计划", id));
    }

    private void createSchedule(InstallmentPlan plan, int periodNo, LocalDate dueDate,
                                BigDecimal amount, String remark) {
        if (amount.signum() <= 0) return;
        Receivable receivable = receivableRepository.save(Receivable.create(
                "AR-%06d".formatted(receivableRepository.nextReceivableSequence()), plan.getCustomerId(),
                plan.getOrderId(), null, amount, dueDate, plan.getPlanNo() + " " + remark));
        scheduleRepository.save(InstallmentSchedule.create(plan.getId(), periodNo, receivable.getId(), dueDate, amount));
    }

    private SalesApi.InstallmentPlanView toView(InstallmentPlan plan) {
        String orderNo = orderRepository.findById(plan.getOrderId()).map(SalesOrder::getOrderNo).orElse(null);
        String customerName = masterData.requireCustomer(plan.getCustomerId()).name();
        LocalDate today = LocalDate.now();
        List<SalesApi.InstallmentScheduleView> schedules = new ArrayList<>();
        BigDecimal received = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        BigDecimal overdue = BigDecimal.ZERO;
        LocalDate nextDueDate = null;
        for (InstallmentSchedule item : scheduleRepository.findByPlanIdOrderByPeriodNoAsc(plan.getId())) {
            Receivable receivable = receivableRepository.findById(item.getReceivableId()).orElse(null);
            if (receivable == null) continue;
            BigDecimal remaining = receivable.outstanding();
            long overdueDays = remaining.signum() > 0 && item.getDueDate().isBefore(today)
                    ? ChronoUnit.DAYS.between(item.getDueDate(), today) : 0;
            schedules.add(new SalesApi.InstallmentScheduleView(item.getId(), item.getPeriodNo(), item.getReceivableId(),
                    receivable.getReceivableNo(), item.getDueDate(), receivable.getAmount(),
                    receivable.getReceivedAmount(), remaining, receivable.getStatus(), overdueDays));
            received = received.add(receivable.getReceivedAmount());
            outstanding = outstanding.add(remaining);
            if (overdueDays > 0) overdue = overdue.add(remaining);
            if (remaining.signum() > 0 && (nextDueDate == null || item.getDueDate().isBefore(nextDueDate))) {
                nextDueDate = item.getDueDate();
            }
        }
        return new SalesApi.InstallmentPlanView(plan.getId(), plan.getPlanNo(), plan.getOrderId(), orderNo,
                plan.getCustomerId(), customerName, plan.getTotalAmount(), plan.getDownPayment(),
                plan.getFinancedAmount(), plan.getInstallmentCount(), plan.getFirstDueDate(),
                plan.getStatus().name(), received, outstanding, overdue, nextDueDate, plan.getCreatedAt(), schedules);
    }

    private List<UUID> parseIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (Exception exception) {
            return List.of();
        }
    }
}
