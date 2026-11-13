package com.ivs.app.orchestration;

import com.ivs.inventory.stock.StockMoveType;
import com.ivs.inventory.stock.StockService;
import com.ivs.masterdata.SerialStatus;
import com.ivs.masterdata.SerialStatusTemplate;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.product.ProductCategory;
import com.ivs.masterdata.product.ProductCategoryService;
import com.ivs.masterdata.product.ProductService;
import com.ivs.masterdata.serial.ProductSerial;
import com.ivs.masterdata.serial.ProductSerialRepository;
import com.ivs.masterdata.serial.ProductSerialService;
import com.ivs.procurement.purchase.InventoryReceiptPort;
import com.ivs.shared.error.ConflictException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 收货入库端口的实现：把采购收货落到库存台账与余额。
 *
 * <p>序列号商品：校验档案存在 → 按产品大项的状态机模板推进到「在库」→ 回写仓库与采购价 → 按台入库；
 * 数量商品：按数量与移动加权平均入库。</p>
 */
@Component
public class InventoryReceiptAdapter implements InventoryReceiptPort {

    private static final String BIZ_TYPE = "PURCHASE_RECEIPT";

    private final StockService stockService;
    private final ProductService productService;
    private final ProductCategoryService categoryService;
    private final ProductSerialRepository serialRepository;
    private final ProductSerialService serialService;

    public InventoryReceiptAdapter(StockService stockService,
                                   ProductService productService,
                                   ProductCategoryService categoryService,
                                   ProductSerialRepository serialRepository,
                                   ProductSerialService serialService) {
        this.stockService = stockService;
        this.productService = productService;
        this.categoryService = categoryService;
        this.serialRepository = serialRepository;
        this.serialService = serialService;
    }

    @Override
    public void postReceipt(ReceiptPosting posting) {
        for (ReceiptPostingLine line : posting.lines()) {
            MasterdataApi.ProductView product = productService.get(line.productId());
            UUID warehouseId = line.warehouseId() == null ? posting.warehouseId() : line.warehouseId();
            if (product.trackingMode().name().equals("SERIAL")) {
                postSerialLines(posting, line, product, warehouseId);
            } else {
                stockService.receive(product.id(), null, warehouseId, line.batchNo(),
                        line.quantity(), line.unitPrice(), true, StockMoveType.PURCHASE_IN,
                        BIZ_TYPE, posting.receiptId(), posting.receiptNo(), "采购按数量收货");
            }
        }
    }

    @Override
    public void reverseReceipt(ReceiptPosting posting) {
        for (ReceiptPostingLine line : posting.lines()) {
            MasterdataApi.ProductView product = productService.get(line.productId());
            UUID warehouseId = line.warehouseId() == null ? posting.warehouseId() : line.warehouseId();
            if (product.trackingMode().name().equals("SERIAL")) {
                for (String serialNo : line.serialNos()) {
                    ProductSerial serial = serialRepository.findBySerialNo(serialNo)
                            .orElseThrow(() -> new ConflictException("SERIAL_NOT_FOUND", "VIN/SN 档案不存在：" + serialNo));
                    if (serial.getStatus() != SerialStatus.IN_STOCK) {
                        throw new ConflictException("RECEIPT_RETURN_SERIAL_IN_USE",
                                "VIN/SN「" + serialNo + "」当前状态为 " + serial.getStatus() + "，不能退回收货");
                    }
                    UUID currentWarehouseId = serial.getWarehouseId() == null ? warehouseId : serial.getWarehouseId();
                    stockService.issue(product.id(), serial.getId(), currentWarehouseId, null, BigDecimal.ONE,
                            StockMoveType.PURCHASE_RECEIPT_REVERSAL, BIZ_TYPE, posting.receiptId(),
                            posting.receiptNo(), "采购收货退回冲销");
                    serialService.markReceiptReturned(serial.getId());
                }
            } else {
                stockService.issue(product.id(), null, warehouseId, line.batchNo(), line.quantity(),
                        StockMoveType.PURCHASE_RECEIPT_REVERSAL, BIZ_TYPE, posting.receiptId(),
                        posting.receiptNo(), "采购收货退回冲销");
            }
        }
    }

    @Override
    public void updateCertificateApplicationDate(List<String> serialNos, java.time.LocalDate certificateApplicationDate) {
        serialService.updateCertificateApplicationDate(serialNos, certificateApplicationDate);
    }

    @Override
    public void relocateSerial(String serialNo, UUID productId, UUID fromWarehouseId, UUID toWarehouseId,
                               UUID receiptId, String receiptNo) {
        ProductSerial serial = serialRepository.findBySerialNo(serialNo)
                .orElseThrow(() -> new ConflictException("SERIAL_NOT_FOUND", "VIN/SN 档案不存在：" + serialNo));
        if (!serial.getProductId().equals(productId)) {
            throw new ConflictException("SERIAL_PRODUCT_MISMATCH", "VIN/SN 所属商品与收货明细不一致");
        }
        BigDecimal cost = serial.getPurchasePrice() == null ? BigDecimal.ZERO : serial.getPurchasePrice();
        stockService.issue(productId, serial.getId(), fromWarehouseId, null, BigDecimal.ONE,
                StockMoveType.TRANSFER_OUT, "RECEIPT_WAREHOUSE_CHANGE", receiptId, receiptNo, "收货仓库调整转出");
        stockService.receive(productId, serial.getId(), toWarehouseId, null, BigDecimal.ONE, cost, false,
                StockMoveType.TRANSFER_IN, "RECEIPT_WAREHOUSE_CHANGE", receiptId, receiptNo, "收货仓库调整转入");
        serialService.applyInbound(serial.getId(), toWarehouseId, null, null, null);
    }

    private void postSerialLines(ReceiptPosting posting, ReceiptPostingLine line, MasterdataApi.ProductView product,
                                 UUID warehouseId) {
        ProductCategory category = categoryService.find(product.categoryId());
        SerialStatusTemplate template = category.statusTemplate();
        for (String serialNo : line.serialNos()) {
            ProductSerial serial = serialRepository.findBySerialNo(serialNo).orElse(null);
            if (serial == null) {
                // VIN 不预先建档：采购收货时录入 VIN、单车采购价和申请合格证日期，系统自动建立一物一档。
                serialService.create(new MasterdataApi.SaveSerialCommand(serialNo, product.id(),
                        warehouseId, null, null, line.unitPrice(), java.time.Instant.now(),
                        line.certificateApplicationDate(), null, null,
                        com.ivs.masterdata.OwnershipType.OWNED, null, null));
                serial = serialRepository.findBySerialNo(serialNo)
                        .orElseThrow(() -> new ConflictException("SERIAL_CREATE_FAILED", "VIN 自动建档失败：" + serialNo));
            } else {
                if (!serial.getProductId().equals(product.id())) {
                    throw new ConflictException("SERIAL_PRODUCT_MISMATCH",
                            "序列号「" + serialNo + "」所属商品与采购明细不一致");
                }
                if (serial.getStatus() == SerialStatus.IN_STOCK || serial.getStatus() == SerialStatus.LOCKED) {
                    throw new ConflictException("SERIAL_ALREADY_IN_STOCK", "序列号「" + serialNo + "」已在库");
                }
                for (SerialStatus step : template.pathTo(serial.getStatus(), SerialStatus.IN_STOCK)) {
                    serialService.moveStatus(serial.getId(), step);
                }
            }
            serialService.applyInbound(serial.getId(), warehouseId, line.unitPrice(), null,
                    line.certificateApplicationDate());
            stockService.receive(product.id(), serial.getId(), warehouseId, null,
                    BigDecimal.ONE, line.unitPrice(), false, StockMoveType.PURCHASE_IN,
                    BIZ_TYPE, posting.receiptId(), posting.receiptNo(), "采购按序列号收货");
        }
    }
}
