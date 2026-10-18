package com.ivs.app.orchestration;

import com.ivs.inventory.stock.StockMoveType;
import com.ivs.inventory.stock.StockService;
import com.ivs.masterdata.SerialStatus;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.product.ProductService;
import com.ivs.masterdata.serial.ProductSerial;
import com.ivs.masterdata.serial.ProductSerialRepository;
import com.ivs.masterdata.serial.ProductSerialService;
import com.ivs.procurement.returns.PurchaseReturnStockPort;
import com.ivs.shared.error.ConflictException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** 采购退货出库适配：数量商品按数量出库，序列号商品逐台返还厂家并推进序列号状态。 */
@Component
public class PurchaseReturnStockAdapter implements PurchaseReturnStockPort {

    private static final String BIZ_TYPE = "PURCHASE_RETURN";

    private final StockService stockService;
    private final ProductService productService;
    private final ProductSerialRepository serialRepository;
    private final ProductSerialService serialService;

    public PurchaseReturnStockAdapter(StockService stockService,
                                      ProductService productService,
                                      ProductSerialRepository serialRepository,
                                      ProductSerialService serialService) {
        this.stockService = stockService;
        this.productService = productService;
        this.serialRepository = serialRepository;
        this.serialService = serialService;
    }

    @Override
    public void postReturn(Posting posting) {
        for (PostingLine line : posting.lines()) {
            MasterdataApi.ProductView product = productService.get(line.productId());
            if ("SERIAL".equals(product.trackingMode().name())) {
                if (line.serialNos() == null || line.serialNos().isEmpty()
                        || BigDecimal.valueOf(line.serialNos().size()).compareTo(line.quantity()) != 0) {
                    throw new ConflictException("PURCHASE_RETURN_SERIAL_REQUIRED",
                            "序列号商品退货必须逐台填写序列号，且数量一致");
                }
                for (String serialNo : line.serialNos()) {
                    ProductSerial serial = serialRepository.findBySerialNo(serialNo)
                            .orElseThrow(() -> new ConflictException("SERIAL_NOT_FOUND",
                                    "序列号「" + serialNo + "」不存在"));
                    if (!serial.getProductId().equals(product.id())) {
                        throw new ConflictException("SERIAL_PRODUCT_MISMATCH", "序列号与退货商品不一致");
                    }
                    if (serial.getStatus() != SerialStatus.IN_STOCK) {
                        throw new ConflictException("SERIAL_STATUS_INVALID", "只有已在库序列号可以退货");
                    }
                    stockService.issueAvailable(product.id(), serial.getId(), posting.warehouseId(), null,
                            BigDecimal.ONE, StockMoveType.PURCHASE_RETURN_OUT, BIZ_TYPE,
                            posting.returnId(), posting.returnNo(), "采购退货");
                    for (SerialStatus step : serialService.statusPath(serial.getId(), SerialStatus.RETURNED)) {
                        serialService.moveStatus(serial.getId(), step);
                    }
                }
            } else {
                stockService.issueAvailable(product.id(), null, posting.warehouseId(), line.batchNo(),
                        line.quantity(), StockMoveType.PURCHASE_RETURN_OUT, BIZ_TYPE,
                        posting.returnId(), posting.returnNo(), "采购退货");
            }
        }
    }
}
