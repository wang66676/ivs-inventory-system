package com.ivs.retail.api;

import com.ivs.retail.retail.RetailRecord;
import com.ivs.retail.retail.RetailStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** 实销模块对外契约。 */
public final class RetailApi {

    private RetailApi() {
    }

    public record RetailView(
            UUID id, String retailNo, LocalDate retailDate, Instant invoiceIssuedAt, LocalDate registerDate,
            LocalDate deliveryDate, UUID serialId, String serialNo, UUID productId, String productNo,
            String productName, String categoryCode, UUID customerId, String customerName, UUID orderId,
            String userName, String userMobile, String plateNo, BigDecimal salePrice, RetailStatus status,
            Instant verifiedAt, String remark, Instant createdAt
    ) {
        public static RetailView from(RetailRecord record, String productNo, String productName, String categoryCode,
                                      String customerName) {
            return new RetailView(record.getId(), record.getRetailNo(), record.getRetailDate(),
                    record.getInvoiceIssuedAt(), record.getRegisterDate(), record.getDeliveryDate(),
                    record.getSerialId(), record.getSerialNo(), record.getProductId(), productNo, productName,
                    categoryCode, record.getCustomerId(), customerName, record.getOrderId(), record.getUserName(),
                    record.getUserMobile(), record.getPlateNo(), record.getSalePrice(), record.getStatus(),
                    record.getVerifiedAt(), record.getRemark(), record.getCreatedAt());
        }
    }

    /** 实销登记：车辆使用人、手机号、终端发票开票时间为必填；车牌照号选填。 */
    public record SaveRetailCommand(
            LocalDate retailDate, Instant invoiceIssuedAt, LocalDate registerDate, LocalDate deliveryDate,
            UUID serialId, UUID customerId, UUID orderId, String userName, String userMobile,
            String plateNo, BigDecimal salePrice, String remark
    ) {
    }

    public record ReverseRetailCommand(String reason, UUID newSerialId) {
    }
}