package com.ivs.masterdata.api;

import com.ivs.masterdata.CustomerType;
import com.ivs.masterdata.MasterStatus;
import com.ivs.masterdata.OwnershipType;
import com.ivs.masterdata.SerialStatus;
import com.ivs.masterdata.SupplierType;
import com.ivs.masterdata.WarehouseType;
import com.ivs.masterdata.partner.Customer;
import com.ivs.masterdata.partner.Supplier;
import com.ivs.masterdata.price.PriceList;
import com.ivs.masterdata.product.Product;
import com.ivs.masterdata.product.ProductAttrDef;
import com.ivs.masterdata.product.ProductCategory;
import com.ivs.masterdata.product.TrackingMode;
import com.ivs.masterdata.serial.ProductSerial;
import com.ivs.masterdata.warehouse.Warehouse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 主数据模块的对外契约（命令与视图）。
 *
 * <p>按领域集中定义：一个领域的命令与视图放在同一个文件里，便于对照阅读与演进；
 * 领域内部仍保持实体与仓储独立。</p>
 */
public final class MasterdataApi {

    private MasterdataApi() {
    }

    /* ===================== 产品大项 ===================== */

    public record CategoryView(
            UUID id, String code, String name, TrackingMode trackingMode, String unit,
            BigDecimal defaultTaxRate, String statusTemplate, int sortOrder, String remark,
            MasterStatus status, long productCount, List<AttrDefView> attrDefs, Instant createdAt
    ) {
        public static CategoryView from(ProductCategory category, List<ProductAttrDef> attrDefs, long productCount) {
            return new CategoryView(
                    category.getId(), category.getCode(), category.getName(), category.getTrackingMode(),
                    category.getUnit(), category.getDefaultTaxRate(), category.getStatusTemplate(),
                    category.getSortOrder(), category.getRemark(), category.getStatus(), productCount,
                    attrDefs.stream().map(AttrDefView::from).toList(), category.getCreatedAt());
        }
    }

    public record SaveCategoryCommand(
            String code, String name, TrackingMode trackingMode, String unit, BigDecimal defaultTaxRate,
            String statusTemplate, int sortOrder, String remark
    ) {
    }

    public record AttrDefView(
            UUID id, UUID categoryId, String code, String name, String dataType,
            boolean required, String optionsJson, int sortOrder
    ) {
        public static AttrDefView from(ProductAttrDef def) {
            return new AttrDefView(def.getId(), def.getCategoryId(), def.getCode(), def.getName(),
                    def.getDataType(), def.isRequired(), def.getOptionsJson(), def.getSortOrder());
        }
    }

    public record SaveAttrDefCommand(
            String code, String name, String dataType, boolean required, String optionsJson, int sortOrder
    ) {
    }

    /* ===================== 商品 ===================== */

    public record ProductView(
            UUID id, String productNo, String name, UUID categoryId, String categoryCode, String categoryName,
            TrackingMode trackingMode, String unit, String brand, String series, String model, String configCode,
            BigDecimal taxRate, BigDecimal minPrice, BigDecimal salePriceGuide, BigDecimal purchasePriceRef,
            String specJson, String remark, MasterStatus status, List<AttrDefView> attrDefs, Instant createdAt
    ) {
        public static ProductView from(Product product, ProductCategory category, List<ProductAttrDef> attrDefs) {
            return new ProductView(
                    product.getId(), product.getProductNo(), product.getName(), product.getCategoryId(),
                    category.getCode(), category.getName(), category.getTrackingMode(), product.getUnit(),
                    product.getBrand(), product.getSeries(), product.getModel(), product.getConfigCode(),
                    product.getTaxRate(), product.getMinPrice(), product.getSalePriceGuide(),
                    product.getPurchasePriceRef(), product.getSpecJson(), product.getRemark(), product.getStatus(),
                    attrDefs.stream().map(AttrDefView::from).toList(), product.getCreatedAt());
        }
    }

    public record ProductImportRow(String categoryCode, String series, String name, String model,
                                        String unit, BigDecimal taxRate) {
    }

    public record ProductBatchImportResult(int created, int failed, List<String> errors,
                                            List<ProductView> items) {
    }

    public record SaveProductCommand(
            String productNo, String name, UUID categoryId, String unit, String brand, String series,
            String model, String configCode, BigDecimal taxRate, BigDecimal minPrice,
            BigDecimal salePriceGuide, BigDecimal purchasePriceRef, String specJson, String remark
    ) {
    }

    /* ===================== 序列号档案 ===================== */

    public record SerialView(
            UUID id, String serialNo, UUID productId, String productNo, String productName, String categoryCode,
            SerialStatus status, UUID warehouseId, String warehouseName, String locationCode, String batchNo,
            BigDecimal purchasePrice, Instant inboundAt, Instant warrantyStart, Instant warrantyEnd,
            OwnershipType ownershipType, String attrsJson, String remark,
            List<SerialStatus> allowedNextStatuses, Instant createdAt
    ) {
        public static SerialView from(ProductSerial serial, Product product, ProductCategory category,
                                      String warehouseName) {
            Set<SerialStatus> next = category == null || category.getTrackingMode() == null
                    ? Set.of()
                    : category.statusTemplate().nextStatuses(serial.getStatus());
            return new SerialView(
                    serial.getId(), serial.getSerialNo(), serial.getProductId(),
                    product == null ? null : product.getProductNo(), product == null ? null : product.getName(),
                    category == null ? null : category.getCode(), serial.getStatus(), serial.getWarehouseId(),
                    warehouseName, serial.getLocationCode(), serial.getBatchNo(), serial.getPurchasePrice(),
                    serial.getInboundAt(), serial.getWarrantyStart(), serial.getWarrantyEnd(),
                    serial.getOwnershipType(), serial.getAttrsJson(), serial.getRemark(),
                    List.copyOf(next), serial.getCreatedAt());
        }
    }

    public record SaveSerialCommand(
            String serialNo, UUID productId, UUID warehouseId, String locationCode, String batchNo,
            BigDecimal purchasePrice, Instant inboundAt, Instant warrantyStart, Instant warrantyEnd,
            OwnershipType ownershipType, String attrsJson, String remark
    ) {
    }

    public record MoveSerialStatusCommand(SerialStatus targetStatus) {
    }

    public record BatchImportResult(int created, int failed, List<String> errors, List<SerialView> items) {
    }

    /* ===================== 仓库 ===================== */

    public record WarehouseView(
            UUID id, String code, String name, WarehouseType type, String address,
            UUID ownerOrgId, MasterStatus status, Instant createdAt
    ) {
        public static WarehouseView from(Warehouse warehouse) {
            return new WarehouseView(warehouse.getId(), warehouse.getCode(), warehouse.getName(),
                    warehouse.getType(), warehouse.getAddress(), warehouse.getOwnerOrgId(),
                    warehouse.getStatus(), warehouse.getCreatedAt());
        }
    }

    public record SaveWarehouseCommand(String code, String name, WarehouseType type, String address, UUID ownerOrgId) {
    }

    /* ===================== 供应商 ===================== */

    public record SupplierView(
            UUID id, String code, String name, SupplierType supplierType, String contactName, String contactPhone,
            String taxNo, String paymentTerms, String remark, MasterStatus status, Instant createdAt
    ) {
        public static SupplierView from(Supplier supplier) {
            return new SupplierView(supplier.getId(), supplier.getCode(), supplier.getName(),
                    supplier.getSupplierType(), supplier.getContactName(), supplier.getContactPhone(),
                    supplier.getTaxNo(), supplier.getPaymentTerms(), supplier.getRemark(),
                    supplier.getStatus(), supplier.getCreatedAt());
        }
    }

    public record SaveSupplierCommand(
            String code, String name, SupplierType supplierType, String contactName, String contactPhone,
            String taxNo, String paymentTerms, String remark
    ) {
    }

    /* ===================== 客户 ===================== */

    public record CustomerView(
            UUID id, String code, String name, CustomerType customerType, String contactName, String contactPhone,
            String taxNo, BigDecimal creditLimit, String remark, MasterStatus status, Instant createdAt
    ) {
        public static CustomerView from(Customer customer) {
            return new CustomerView(customer.getId(), customer.getCode(), customer.getName(),
                    customer.getCustomerType(), customer.getContactName(), customer.getContactPhone(),
                    customer.getTaxNo(), customer.getCreditLimit(), customer.getRemark(),
                    customer.getStatus(), customer.getCreatedAt());
        }
    }

    public record SaveCustomerCommand(
            String code, String name, CustomerType customerType, String contactName, String contactPhone,
            String taxNo, BigDecimal creditLimit, String remark
    ) {
    }

    /* ===================== 价目表 ===================== */

    public record PriceListView(
            UUID id, String code, String name, CustomerType customerType, LocalDate effectiveFrom,
            LocalDate effectiveTo, MasterStatus status, String remark, int lineCount,
            List<PriceListLineView> lines, Instant createdAt
    ) {
        public static PriceListView from(PriceList priceList, List<PriceListLineView> lines) {
            return new PriceListView(priceList.getId(), priceList.getCode(), priceList.getName(),
                    priceList.getCustomerType(), priceList.getEffectiveFrom(), priceList.getEffectiveTo(),
                    priceList.getStatus(), priceList.getRemark(), lines.size(), lines, priceList.getCreatedAt());
        }
    }

    public record PriceListLineView(
            UUID id, UUID productId, String productNo, String productName,
            BigDecimal price, BigDecimal minPrice, String remark
    ) {
    }

    public record SavePriceListCommand(
            String code, String name, CustomerType customerType, LocalDate effectiveFrom,
            LocalDate effectiveTo, String remark
    ) {
    }

    public record SavePriceListLineCommand(UUID productId, BigDecimal price, BigDecimal minPrice, String remark) {
    }

    /** 商品取价结果：命中价目表返回价目表价格，否则回落到商品指导价。 */
    public record ProductPriceView(
            UUID productId, String productNo, BigDecimal price, BigDecimal minPrice, String source
    ) {
    }
}
