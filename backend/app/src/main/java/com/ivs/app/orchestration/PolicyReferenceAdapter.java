package com.ivs.app.orchestration;

import com.ivs.masterdata.partner.Supplier;
import com.ivs.masterdata.partner.SupplierRepository;
import com.ivs.masterdata.product.Product;
import com.ivs.masterdata.product.ProductRepository;
import com.ivs.policy.reference.PolicyReferencePort;
import com.ivs.retail.retail.RetailRecord;
import com.ivs.retail.retail.RetailRecordRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 政策报表/申报单所需的主数据与实销只读信息适配（只读仓储，避免嵌套事务被标记回滚）。 */
@Component
public class PolicyReferenceAdapter implements PolicyReferencePort {

    private final SupplierRepository supplierRepository;
    private final RetailRecordRepository retailRepository;
    private final ProductRepository productRepository;

    public PolicyReferenceAdapter(SupplierRepository supplierRepository,
                                  RetailRecordRepository retailRepository,
                                  ProductRepository productRepository) {
        this.supplierRepository = supplierRepository;
        this.retailRepository = retailRepository;
        this.productRepository = productRepository;
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
        return new RetailInfo(record.getRetailNo(), record.getSerialNo(),
                product == null ? null : product.getProductNo(), product == null ? null : product.getName(),
                record.getSalePrice(), record.getRetailDate(), null);
    }
}
