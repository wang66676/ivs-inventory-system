package com.ivs.app.orchestration;

import com.ivs.lease.LeaseReceivablePort;
import com.ivs.sales.settlement.Receivable;
import com.ivs.sales.settlement.ReceivableRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** 把租赁月租计划适配为销售模块应收。 */
@Component
public class LeaseReceivableAdapter implements LeaseReceivablePort {
    private final ReceivableRepository receivableRepository;

    public LeaseReceivableAdapter(ReceivableRepository receivableRepository) {
        this.receivableRepository = receivableRepository;
    }

    @Override
    public UUID createLeaseReceivable(UUID customerId, BigDecimal amount, LocalDate dueDate,
                                      UUID contractId, String contractNo, int periodNo) {
        Receivable receivable = Receivable.create("LR-%06d".formatted(receivableRepository.nextReceivableSequence()),
                customerId, null, null, amount, dueDate,
                "电池租赁月租 " + contractNo + " 第 " + periodNo + " 期");
        return receivableRepository.save(receivable).getId();
    }

    @Override
    public ReceivableInfo get(UUID receivableId) {
        Receivable receivable = receivableRepository.findById(receivableId).orElseThrow();
        return new ReceivableInfo(receivable.getId(), receivable.getReceivableNo(), receivable.getStatus().name(),
                receivable.getAmount(), receivable.getReceivedAmount(), receivable.outstanding());
    }
}
