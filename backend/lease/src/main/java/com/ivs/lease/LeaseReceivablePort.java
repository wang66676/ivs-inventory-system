package com.ivs.lease;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 租赁月租与销售应收模块之间的端口。 */
public interface LeaseReceivablePort {
    record ReceivableInfo(UUID id, String receivableNo, String status,
                          BigDecimal amount, BigDecimal receivedAmount, BigDecimal outstanding) {
    }

    record DepositApplicationResult(String receiptNo, BigDecimal amount) {
    }

    UUID createLeaseReceivable(UUID customerId, BigDecimal amount, LocalDate dueDate,
                               UUID contractId, String contractNo, int periodNo);
    ReceivableInfo get(UUID receivableId);
    void markOverdue(UUID receivableId, LocalDate today);
    DepositApplicationResult applyDeposit(UUID customerId, List<UUID> receivableIds, BigDecimal amount,
                                          String contractNo);
}
