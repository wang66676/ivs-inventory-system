package com.ivs.app.orchestration;

import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.partner.CustomerService;
import com.ivs.masterdata.product.ProductService;
import com.ivs.masterdata.serial.ProductSerial;
import com.ivs.masterdata.serial.ProductSerialRepository;
import com.ivs.retail.retail.RetailMasterDataPort;
import com.ivs.shared.error.NotFoundException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 实销侧主数据适配器。 */
@Component
public class RetailMasterDataAdapter implements RetailMasterDataPort {

    private final ProductSerialRepository serialRepository;
    private final ProductService productService;
    private final CustomerService customerService;

    public RetailMasterDataAdapter(ProductSerialRepository serialRepository,
                                   ProductService productService,
                                   CustomerService customerService) {
        this.serialRepository = serialRepository;
        this.productService = productService;
        this.customerService = customerService;
    }

    @Override
    public SerialInfo requireSerial(UUID serialId) {
        ProductSerial serial = serialRepository.findById(serialId)
                .orElseThrow(() -> new NotFoundException("序列号档案", serialId));
        MasterdataApi.ProductView product = productService.get(serial.getProductId());
        return new SerialInfo(serial.getId(), serial.getSerialNo(), serial.getProductId(),
                product.productNo(), product.name(), product.categoryCode(),
                product.trackingMode().name(), serial.getStatus().name());
    }

    @Override
    public String customerName(UUID customerId) {
        return customerService.find(customerId).getName();
    }
}