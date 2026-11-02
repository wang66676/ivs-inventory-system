package com.ivs.app.orchestration;

import com.ivs.masterdata.partner.Supplier;
import com.ivs.masterdata.partner.SupplierRepository;
import com.ivs.masterdata.product.Product;
import com.ivs.masterdata.product.ProductRepository;
import com.ivs.policy.reference.PolicyReferencePort;
import com.ivs.procurement.purchase.PurchaseOrder;
import com.ivs.procurement.purchase.PurchaseOrderLine;
import com.ivs.procurement.purchase.PurchaseOrderLineRepository;
import com.ivs.procurement.purchase.PurchaseOrderRepository;
import com.ivs.procurement.purchase.PurchaseReceiptLine;
import com.ivs.procurement.purchase.PurchaseReceiptLineRepository;
import com.ivs.retail.retail.RetailRecord;
import com.ivs.retail.retail.RetailRecordRepository;
import com.ivs.sales.order.SalesOrderLine;
import com.ivs.sales.order.SalesOrderLineRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 政策报表/申报单所需的主数据与实销只读信息适配（只读仓储，避免嵌套事务被标记回滚）。 */
@Component
public class PolicyReferenceAdapter implements PolicyReferencePort {

    private final SupplierRepository supplierRepository;
    private final RetailRecordRepository retailRepository;
    private final ProductRepository productRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final PurchaseReceiptLineRepository purchaseReceiptLineRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;

    public PolicyReferenceAdapter(SupplierRepository supplierRepository,
                                  RetailRecordRepository retailRepository,
                                  ProductRepository productRepository,
                                  PurchaseOrderRepository purchaseOrderRepository,
                                  PurchaseOrderLineRepository purchaseOrderLineRepository,
                                  PurchaseReceiptLineRepository purchaseReceiptLineRepository,
                                  SalesOrderLineRepository salesOrderLineRepository) {
        this.supplierRepository = supplierRepository;
        this.retailRepository = retailRepository;
        this.productRepository = productRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderLineRepository = purchaseOrderLineRepository;
        this.purchaseReceiptLineRepository = purchaseReceiptLineRepository;
        this.salesOrderLineRepository = salesOrderLineRepository;
    }

    @Override
    public String supplierName(UUID supplierId) {
        if (supplierId == null) return null;
        return supplierRepository.findById(supplierId).map(Supplier::getName).orElse(null);
    }

    @Override
    public RetailInfo retail(UUID retailId) {
        if (retailId == null) return null;
        RetailRecord record = retailRepository.findById(retailId).orElse(null);
        if (record == null) return null;
        Product product = productRepository.findById(record.getProductId()).orElse(null);
        String serialKey = record.getSerialId() == null ? null : record.getSerialId().toString();
        SalesOrderLine orderLine = serialKey == null ? null : salesOrderLineRepository.searchBySerialId(serialKey)
                .stream().filter(line -> line.getOrderId().equals(record.getOrderId()))
                .filter(line -> line.getSerialIds() != null && line.getSerialIds().contains(serialKey))
                .findFirst().orElse(null);
        java.math.BigDecimal salePrice = orderLine == null ? record.getSalePrice() : orderLine.getUnitPrice();
        return new RetailInfo(record.getRetailNo(), record.getSerialNo(),
                product == null ? null : product.getProductNo(), product == null ? null : product.getName(),
                salePrice, record.getRetailDate(), record.getInvoiceIssuedAt(), null);
    }

    @Override
    public PurchaseOrderInfo purchaseOrderBySerialNo(String serialNo) {
        if (serialNo == null || serialNo.isBlank()) return null;
        String normalized = serialNo.trim().toUpperCase();
        for (PurchaseReceiptLine receiptLine : purchaseReceiptLineRepository.searchBySerialNo(normalized)) {
            if (receiptLine.getSerialNos() == null || !receiptLine.getSerialNos().toUpperCase().contains(normalized)) {
                continue;
            }
            if (receiptLine.getOrderLineId() == null) continue;
            PurchaseOrderLine orderLine = purchaseOrderLineRepository.findById(receiptLine.getOrderLineId()).orElse(null);
            if (orderLine == null) continue;
            PurchaseOrder order = purchaseOrderRepository.findById(orderLine.getOrderId()).orElse(null);
            if (order != null) {
                return new PurchaseOrderInfo(order.getId(), order.getOrderNo(), order.getExternalOrderNo(),
                        receiptLine.getUnitPrice());
            }
        }
        return null;
    }
}
