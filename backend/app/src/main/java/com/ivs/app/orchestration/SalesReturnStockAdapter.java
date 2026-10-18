package com.ivs.app.orchestration;

import com.ivs.inventory.stock.StockMoveType;
import com.ivs.inventory.stock.StockService;
import com.ivs.masterdata.SerialStatus;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.product.ProductService;
import com.ivs.masterdata.serial.ProductSerial;
import com.ivs.masterdata.serial.ProductSerialRepository;
import com.ivs.masterdata.serial.ProductSerialService;
import com.ivs.sales.returns.SalesReturnStockPort;
import com.ivs.shared.error.ConflictException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

/** 销售退货回库适配：数量商品入库，序列号商品恢复在库并写回仓库/成本。 */
@Component
public class SalesReturnStockAdapter implements SalesReturnStockPort {

    private static final String BIZ_TYPE = "SALES_RETURN";

    private final StockService stockService;
    private final ProductService productService;
    private final ProductSerialRepository serialRepository;
    private final ProductSerialService serialService;

    public SalesReturnStockAdapter(StockService stockService,
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
                    throw new ConflictException("SALES_RETURN_SERIAL_REQUIRED",
                            "序列号商品退货必须逐台填写序列号，且数量一致");
                }
                for (String serialNo : line.serialNos()) {
                    ProductSerial serial = serialRepository.findBySerialNo(serialNo)
                            .orElseThrow(() -> new ConflictException("SERIAL_NOT_FOUND",
                                    "序列号「" + serialNo + "」不存在"));
                    if (!serial.getProductId().equals(product.id())) {
                        throw new ConflictException("SERIAL_PRODUCT_MISMATCH", "序列号与退货商品不一致");
                    }
                    if (serial.getStatus() != SerialStatus.DELIVERED && serial.getStatus() != SerialStatus.SOLD) {
                        throw new ConflictException("SERIAL_STATUS_INVALID", "只有已交付序列号可以销售退货");
                    }
                    for (SerialStatus step : serialService.statusPath(serial.getId(), SerialStatus.IN_STOCK)) {
                        serialService.moveStatus(serial.getId(), step);
                    }
                    serialService.applyInbound(serial.getId(), posting.warehouseId(), line.unitCost(), Instant.now());
                    stockService.receive(product.id(), serial.getId(), posting.warehouseId(), null,
                            BigDecimal.ONE, line.unitCost(), false, StockMoveType.SALE_RETURN_IN,
                            BIZ_TYPE, posting.returnId(), posting.returnNo(), "销售退货回库");
                }
            } else {
                stockService.receive(product.id(), null, posting.warehouseId(), line.batchNo(),
                        line.quantity(), line.unitCost(), true, StockMoveType.SALE_RETURN_IN,
                        BIZ_TYPE, posting.returnId(), posting.returnNo(), "销售退货回库");
            }
        }
    }
}
