package com.ivs.app.orchestration;

import com.ivs.inventory.stock.StockMoveType;
import com.ivs.inventory.stock.StockService;
import com.ivs.lease.LeaseMasterDataPort;
import com.ivs.masterdata.OwnershipType;
import com.ivs.masterdata.SerialStatus;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.partner.CustomerService;
import com.ivs.masterdata.serial.ProductSerial;
import com.ivs.masterdata.serial.ProductSerialRepository;
import com.ivs.masterdata.serial.ProductSerialService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** 租赁资产端口：租赁出库、归还入库、序列号状态和所有权联动。 */
@Component
public class LeaseMasterDataAdapter implements LeaseMasterDataPort {
    private final ProductSerialService serialService;
    private final ProductSerialRepository serialRepository;
    private final CustomerService customerService;
    private final StockService stockService;

    public LeaseMasterDataAdapter(ProductSerialService serialService,
                                  ProductSerialRepository serialRepository,
                                  CustomerService customerService,
                                  StockService stockService) {
        this.serialService = serialService;
        this.serialRepository = serialRepository;
        this.customerService = customerService;
        this.stockService = stockService;
    }

    @Override
    public SerialInfo requireSerial(UUID serialId) {
        MasterdataApi.SerialView serial = serialService.get(serialId);
        return new SerialInfo(serial.id(), serial.productId(), serial.serialNo(), serial.productName(),
                serial.categoryCode(), serial.categoryName(), serial.status().name(), serial.warehouseId(),
                serial.batchNo(), serial.purchasePrice());
    }

    @Override
    public CustomerInfo requireCustomer(UUID customerId) {
        return new CustomerInfo(customerId, customerService.find(customerId).getName());
    }

    @Override
    public void activateAsset(SerialInfo serial, UUID contractId, String contractNo, LocalDate startDate) {
        stockService.issue(serial.productId(), serial.id(), serial.warehouseId(), serial.batchNo(),
                BigDecimal.ONE, StockMoveType.LEASE_OUT, "LEASE_CONTRACT", contractId, contractNo,
                "电池转入租赁资产池");
        moveSerial(serial.id(), SerialStatus.IN_LEASE);
        serialService.changeOwnership(serial.id(), OwnershipType.LEASED);
        ProductSerial entity = serialRepository.findById(serial.id()).orElseThrow();
        entity.bindLeaseContract(contractId);
        serialRepository.save(entity);
    }

    @Override
    public void endLeaseAsset(SerialInfo serial, UUID contractId, String contractNo) {
        stockService.receive(serial.productId(), serial.id(), serial.warehouseId(), serial.batchNo(),
                BigDecimal.ONE, serial.purchasePrice() == null ? BigDecimal.ZERO : serial.purchasePrice(), false,
                StockMoveType.LEASE_RETURN, "LEASE_CONTRACT", contractId, contractNo, "租赁电池归还入库");
        moveSerial(serial.id(), SerialStatus.IN_STOCK);
        serialService.changeOwnership(serial.id(), OwnershipType.OWNED);
        ProductSerial entity = serialRepository.findById(serial.id()).orElseThrow();
        entity.clearLeaseContract();
        serialRepository.save(entity);
    }

    @Override
    public void retireAsset(SerialInfo serial, UUID disposalId, String disposalNo, boolean scrap, String reason) {
        StockMoveType moveType = scrap ? StockMoveType.SCRAP : StockMoveType.LEASE_RETIRE;
        stockService.issue(serial.productId(), serial.id(), serial.warehouseId(), serial.batchNo(),
                BigDecimal.ONE, moveType, "LEASE_DISPOSAL", disposalId, disposalNo, reason);
        moveSerial(serial.id(), scrap ? SerialStatus.SCRAPPED : SerialStatus.SECOND_LIFE);
    }

    private void moveSerial(UUID serialId, SerialStatus target) {
        for (SerialStatus step : serialService.statusPath(serialId, target)) {
            serialService.moveStatus(serialId, step);
        }
    }
}
