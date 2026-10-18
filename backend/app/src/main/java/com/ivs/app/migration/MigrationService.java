package com.ivs.app.migration;

import com.ivs.inventory.stock.StockLedger;
import com.ivs.inventory.stock.StockMoveType;
import com.ivs.inventory.stock.StockService;
import com.ivs.masterdata.CustomerType;
import com.ivs.masterdata.OwnershipType;
import com.ivs.masterdata.SupplierType;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.partner.CustomerRepository;
import com.ivs.masterdata.partner.CustomerService;
import com.ivs.masterdata.partner.SupplierRepository;
import com.ivs.masterdata.partner.SupplierService;
import com.ivs.masterdata.product.ProductCategoryRepository;
import com.ivs.masterdata.product.ProductCategoryService;
import com.ivs.masterdata.product.ProductRepository;
import com.ivs.masterdata.product.ProductService;
import com.ivs.masterdata.product.TrackingMode;
import com.ivs.masterdata.serial.ProductSerialRepository;
import com.ivs.masterdata.serial.ProductSerialService;
import com.ivs.masterdata.warehouse.WarehouseRepository;
import com.ivs.procurement.payable.Payable;
import com.ivs.procurement.payable.PayableRepository;
import com.ivs.policy.fund.RebateFundService;
import com.ivs.policy.fund.api.RebateFundApi;
import com.ivs.sales.settlement.ReceiptAllocationRepository;
import com.ivs.sales.settlement.Receivable;
import com.ivs.sales.settlement.ReceivableRepository;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import com.ivs.shared.security.CurrentActorProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/** 历史数据迁移：校验、导入、对账与回滚。 */
@Service
public class MigrationService {

    private static final String CUSTOMER = "CUSTOMER";
    private static final String SUPPLIER = "SUPPLIER";
    private static final String CATEGORY = "CATEGORY";
    private static final String PRODUCT = "PRODUCT";
    private static final String SERIAL = "SERIAL";
    private static final String STOCK_OPENING = "STOCK_OPENING";
    private static final String RECEIVABLE_OPENING = "RECEIVABLE_OPENING";
    private static final String FUND_OPENING = "FUND_OPENING";
    private static final String PAYABLE_OPENING = "PAYABLE_OPENING";

    private final MigrationBatchRepository batchRepository;
    private final MigrationItemRepository itemRepository;
    private final CustomerService customerService;
    private final SupplierService supplierService;
    private final ProductCategoryService categoryService;
    private final ProductService productService;
    private final ProductSerialService serialService;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductSerialRepository serialRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockService stockService;
    private final ReceivableRepository receivableRepository;
    private final ReceiptAllocationRepository allocationRepository;
    private final RebateFundService fundService;
    private final PayableRepository payableRepository;
    private final CurrentActorProvider actors;
    private final JdbcTemplate jdbcTemplate;

    public MigrationService(MigrationBatchRepository batchRepository,
                            MigrationItemRepository itemRepository,
                            CustomerService customerService,
                            SupplierService supplierService,
                            ProductCategoryService categoryService,
                            ProductService productService,
                            ProductSerialService serialService,
                            CustomerRepository customerRepository,
                            SupplierRepository supplierRepository,
                            ProductCategoryRepository categoryRepository,
                            ProductRepository productRepository,
                            ProductSerialRepository serialRepository,
                            WarehouseRepository warehouseRepository,
                            StockService stockService,
                            ReceivableRepository receivableRepository,
                            ReceiptAllocationRepository allocationRepository,
                            RebateFundService fundService,
                            PayableRepository payableRepository,
                            CurrentActorProvider actors,
                            JdbcTemplate jdbcTemplate) {
        this.batchRepository = batchRepository;
        this.itemRepository = itemRepository;
        this.customerService = customerService;
        this.supplierService = supplierService;
        this.categoryService = categoryService;
        this.productService = productService;
        this.serialService = serialService;
        this.customerRepository = customerRepository;
        this.supplierRepository = supplierRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.serialRepository = serialRepository;
        this.warehouseRepository = warehouseRepository;
        this.stockService = stockService;
        this.receivableRepository = receivableRepository;
        this.allocationRepository = allocationRepository;
        this.fundService = fundService;
        this.payableRepository = payableRepository;
        this.actors = actors;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<MigrationApi.BatchView> listBatches() {
        return batchRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(MigrationApi.BatchView::from).toList();
    }

    @Transactional(readOnly = true)
    public MigrationApi.BatchView getBatch(UUID id) {
        return MigrationApi.BatchView.from(findBatch(id));
    }

    @Transactional(readOnly = true)
    public MigrationApi.ValidationView validate(MigrationApi.Payload payload) {
        List<String> errors = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (payload == null) {
            return new MigrationApi.ValidationView(false, List.of("迁移数据不能为空"), counts, money(BigDecimal.ZERO));
        }
        if (blank(payload.name())) errors.add("批次名称不能为空");
        List<MigrationApi.CustomerRow> customers = safe(payload.customers());
        List<MigrationApi.SupplierRow> suppliers = safe(payload.suppliers());
        List<MigrationApi.CategoryRow> categories = safe(payload.categories());
        List<MigrationApi.ProductRow> products = safe(payload.products());
        List<MigrationApi.SerialRow> serials = safe(payload.serials());
        List<MigrationApi.OpeningStockRow> stocks = safe(payload.openingStocks());
        List<MigrationApi.ReceivableRow> receivables = safe(payload.receivables());
        List<MigrationApi.FundOpeningRow> funds = safe(payload.fundOpenings());
        List<MigrationApi.PayableRow> payables = safe(payload.payables());
        counts.put("customers", customers.size());
        counts.put("suppliers", suppliers.size());
        counts.put("categories", categories.size());
        counts.put("products", products.size());
        counts.put("serials", serials.size());
        counts.put("openingStocks", stocks.size());
        counts.put("receivables", receivables.size());
        counts.put("fundOpenings", funds.size());
        counts.put("payables", payables.size());

        Set<String> customerCodes = unique(customers, MigrationApi.CustomerRow::code, "客户编码", errors);
        Set<String> supplierCodes = unique(suppliers, MigrationApi.SupplierRow::code, "供应商编码", errors);
        Set<String> categoryCodes = unique(categories, MigrationApi.CategoryRow::code, "产品大项编码", errors);
        Set<String> productNos = unique(products, MigrationApi.ProductRow::productNo, "商品编码", errors);
        Set<String> serialNos = unique(serials, MigrationApi.SerialRow::serialNo, "序列号", errors);
        Set<String> receivableNos = unique(receivables, MigrationApi.ReceivableRow::receivableNo, "应收单号", errors);
        unique(payables, MigrationApi.PayableRow::payableNo, "应付单号", errors);
        for (var row : customers) {
            if (blank(row.code()) || blank(row.name())) errors.add("客户行缺少编码或名称");
            if (!blank(row.code()) && customerRepository.existsByCode(upper(row.code()))) errors.add("客户编码已存在：" + row.code());
            checkEnum(CustomerType.class, row.customerType(), "客户类型", errors);
        }
        for (var row : suppliers) {
            if (blank(row.code()) || blank(row.name())) errors.add("供应商行缺少编码或名称");
            if (!blank(row.code()) && supplierRepository.existsByCode(upper(row.code()))) errors.add("供应商编码已存在：" + row.code());
            checkEnum(SupplierType.class, row.supplierType(), "供应商类型", errors);
        }
        for (var row : categories) {
            if (blank(row.code()) || blank(row.name())) errors.add("产品大项行缺少编码或名称");
            if (!blank(row.code()) && categoryRepository.existsByCode(upper(row.code()))) errors.add("产品大项编码已存在：" + row.code());
            checkEnum(TrackingMode.class, row.trackingMode(), "追踪方式", errors);
            if (blank(row.statusTemplate())) errors.add("产品大项缺少状态机模板：" + row.code());
        }
        Set<String> categoryRefs = new HashSet<>(categoryCodes);
        categoryRepository.findAllByOrderBySortOrderAscCodeAsc().forEach(c -> categoryRefs.add(c.getCode()));
        for (var row : products) {
            if (blank(row.productNo()) || blank(row.name())) errors.add("商品行缺少编码或名称");
            if (!blank(row.productNo()) && productRepository.existsByProductNo(upper(row.productNo()))) errors.add("商品编码已存在：" + row.productNo());
            if (blank(row.categoryCode()) || !categoryRefs.contains(upper(row.categoryCode()))) errors.add("商品引用的产品大项不存在：" + row.productNo());
        }
        Set<String> productRefs = new HashSet<>(productNos);
        productRepository.findAllByOrderByCreatedAtDesc().forEach(p -> productRefs.add(p.getProductNo()));
        Set<String> warehouseCodes = new HashSet<>();
        warehouseRepository.findAllByOrderByCodeAsc().forEach(w -> warehouseCodes.add(w.getCode()));
        for (var row : serials) {
            if (blank(row.serialNo()) || blank(row.productNo())) errors.add("序列号行缺少序列号或商品编码");
            if (!productRefs.contains(upper(row.productNo()))) errors.add("序列号引用的商品不存在：" + row.serialNo());
            if (!blank(row.warehouseCode()) && !warehouseCodes.contains(upper(row.warehouseCode()))) errors.add("序列号引用的仓库不存在：" + row.warehouseCode());
            if (!blank(row.serialNo()) && serialRepository.existsBySerialNo(upper(row.serialNo()))) errors.add("序列号已存在：" + row.serialNo());
        }
        Set<String> allSerials = new HashSet<>(serialNos);
        serialRepository.findAllByOrderByCreatedAtDesc().forEach(s -> allSerials.add(s.getSerialNo()));
        BigDecimal total = BigDecimal.ZERO;
        for (var row : stocks) {
            if (!productRefs.contains(upper(row.productNo()))) errors.add("期初库存商品不存在：" + row.productNo());
            if (blank(row.warehouseCode()) || !warehouseCodes.contains(upper(row.warehouseCode()))) errors.add("期初库存仓库不存在：" + row.warehouseCode());
            if (nvl(row.quantity()).signum() <= 0) errors.add("期初库存数量必须大于 0：" + row.productNo());
            if (nvl(row.unitCost()).signum() < 0) errors.add("期初单位成本不能为负：" + row.productNo());
            if (!blank(row.serialNo()) && !allSerials.contains(upper(row.serialNo()))) errors.add("期初库存序列号不存在：" + row.serialNo());
            total = total.add(nvl(row.quantity()).multiply(nvl(row.unitCost())));
        }
        Set<String> customerRefs = new HashSet<>(customerCodes);
        customerRepository.findAllByOrderByCreatedAtDesc().forEach(c -> customerRefs.add(c.getCode()));
        for (var row : receivables) {
            if (blank(row.receivableNo())) errors.add("期初应收缺少单号");
            if (!customerRefs.contains(upper(row.customerCode()))) errors.add("期初应收客户不存在：" + row.customerCode());
            if (nvl(row.amount()).signum() <= 0) errors.add("期初应收金额必须大于 0：" + row.receivableNo());
            if (nvl(row.receivedAmount()).compareTo(nvl(row.amount())) > 0) errors.add("期初应收已收金额大于应收金额：" + row.receivableNo());
            if (!blank(row.receivableNo()) && receivableRepository.findByReceivableNo(row.receivableNo()).isPresent()) errors.add("应收单号已存在：" + row.receivableNo());
            total = total.add(nvl(row.amount()).subtract(nvl(row.receivedAmount())).max(BigDecimal.ZERO));
        }
        Set<String> supplierRefs = new HashSet<>(supplierCodes);
        supplierRepository.findAllByOrderByCodeAsc().forEach(s -> supplierRefs.add(s.getCode()));
        for (var row : funds) {
            if (!supplierRefs.contains(upper(row.supplierCode()))) errors.add("资金池期初厂家不存在：" + row.supplierCode());
            if (nvl(row.amount()).signum() <= 0) errors.add("资金池期初金额必须大于 0：" + row.supplierCode());
            total = total.add(nvl(row.amount()));
        }
        for (var row : payables) {
            if (blank(row.payableNo())) errors.add("期初应付缺少单号");
            if (!supplierRefs.contains(upper(row.supplierCode()))) errors.add("期初应付供应商不存在：" + row.supplierCode());
            if (nvl(row.amount()).signum() <= 0) errors.add("期初应付金额必须大于 0：" + row.payableNo());
            if (nvl(row.paidAmount()).compareTo(nvl(row.amount())) > 0) errors.add("期初应付已付金额大于应付金额：" + row.payableNo());
            if (!blank(row.payableNo()) && payableRepository.findByPayableNo(row.payableNo()).isPresent()) errors.add("应付单号已存在：" + row.payableNo());
            total = total.add(nvl(row.amount()).subtract(nvl(row.paidAmount())).max(BigDecimal.ZERO));
        }        return new MigrationApi.ValidationView(errors.isEmpty(), errors, counts, money(total));
    }

    @Transactional
    public MigrationApi.BatchView importBatch(MigrationApi.Payload payload) {
        MigrationApi.ValidationView validation = validate(payload);
        if (!validation.valid()) {
            throw new ConflictException("MIGRATION_VALIDATION_FAILED",
                    "迁移校验失败：" + String.join("；", validation.errors()));
        }
        MigrationBatch batch = MigrationBatch.imported(
                "MIG-%06d".formatted(batchRepository.nextBatchSequence()), payload.name(),
                payload.sourceSystem(), payload.remark(), currentUserId());
        batchRepository.save(batch);
        List<MigrationItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        Map<String, UUID> customerIds = customerMap();
        for (var row : safe(payload.customers())) {
            var saved = customerService.create(new MasterdataApi.SaveCustomerCommand(row.code(), row.name(),
                    enumValue(CustomerType.class, row.customerType()), row.contactName(), row.contactPhone(),
                    row.taxNo(), row.creditLimit(), row.remark()));
            customerIds.put(saved.code(), saved.id());
            items.add(item(batch, CUSTOMER, saved.code(), saved.id(), BigDecimal.ZERO));
        }
        Map<String, UUID> supplierIds = supplierMap();
        for (var row : safe(payload.suppliers())) {
            var saved = supplierService.create(new MasterdataApi.SaveSupplierCommand(row.code(), row.name(),
                    enumValue(SupplierType.class, row.supplierType()), row.contactName(), row.contactPhone(),
                    row.taxNo(), null, row.remark()));
            supplierIds.put(saved.code(), saved.id());
            items.add(item(batch, SUPPLIER, saved.code(), saved.id(), BigDecimal.ZERO));
        }
        Map<String, UUID> categoryIds = categoryMap();
        for (var row : safe(payload.categories())) {
            var saved = categoryService.create(new MasterdataApi.SaveCategoryCommand(row.code(), row.name(),
                    enumValue(TrackingMode.class, row.trackingMode()), row.unit(), row.defaultTaxRate(),
                    row.statusTemplate(), row.sortOrder() == null ? 0 : row.sortOrder(), row.remark()));
            categoryIds.put(saved.code(), saved.id());
            items.add(item(batch, CATEGORY, saved.code(), saved.id(), BigDecimal.ZERO));
        }
        Map<String, UUID> productIds = productMap();
        for (var row : safe(payload.products())) {
            var saved = productService.create(new MasterdataApi.SaveProductCommand(row.productNo(), row.name(),
                    categoryIds.get(upper(row.categoryCode())), row.unit(), row.brand(), row.series(), row.model(),
                    null, row.taxRate(), row.minPrice(), row.salePriceGuide(), row.purchasePriceRef(), null, row.remark()));
            productIds.put(saved.productNo(), saved.id());
            items.add(item(batch, PRODUCT, saved.productNo(), saved.id(), BigDecimal.ZERO));
        }
        Map<String, UUID> warehouseIds = warehouseMap();
        Map<String, UUID> serialIds = serialMap();
        for (var row : safe(payload.serials())) {
            var saved = serialService.create(new MasterdataApi.SaveSerialCommand(row.serialNo(),
                    productIds.get(upper(row.productNo())),
                    blank(row.warehouseCode()) ? null : warehouseIds.get(upper(row.warehouseCode())),
                    null, null, row.purchasePrice(), row.inboundAt() == null ? Instant.now() : row.inboundAt(),
                    null, null, OwnershipType.OWNED, row.attrsJson(), row.remark()));
            serialIds.put(saved.serialNo(), saved.id());
            items.add(item(batch, SERIAL, saved.serialNo(), saved.id(), BigDecimal.ZERO));
        }
        for (var row : safe(payload.openingStocks())) {
            UUID productId = productIds.get(upper(row.productNo()));
            UUID warehouseId = warehouseIds.get(upper(row.warehouseCode()));
            UUID serialId = blank(row.serialNo()) ? null : serialIds.get(upper(row.serialNo()));
            String batchNo = blank(row.batchNo()) ? "-" : row.batchNo();
            StockLedger ledger = stockService.receive(productId, serialId, warehouseId, batchNo,
                    row.quantity(), row.unitCost(), serialId == null, StockMoveType.OPENING,
                    "MIGRATION_OPENING", batch.getId(), batch.getBatchNo(), row.remark());
            BigDecimal amount = money(nvl(row.quantity()).multiply(nvl(row.unitCost())));
            total = total.add(amount);
            items.add(item(batch, STOCK_OPENING, productId + "|" + warehouseId + "|" + batchNo, productId, amount));
        }
        for (var row : safe(payload.receivables())) {
            Receivable receivable;
            try {
                receivable = Receivable.importOpening(row.receivableNo(), customerIds.get(upper(row.customerCode())),
                        row.amount(), row.receivedAmount(), row.dueDate(), row.remark());
            } catch (IllegalArgumentException exception) {
                throw new ConflictException("MIGRATION_RECEIVABLE_INVALID", exception.getMessage());
            }
            receivableRepository.save(receivable);
            BigDecimal amount = money(nvl(row.amount()).subtract(nvl(row.receivedAmount())).max(BigDecimal.ZERO));
            total = total.add(amount);
            items.add(item(batch, RECEIVABLE_OPENING, row.receivableNo(), receivable.getId(), amount));
        }
        for (var row : safe(payload.fundOpenings())) {
            UUID supplierId = supplierIds.get(upper(row.supplierCode()));
            fundService.credit(new RebateFundApi.CreditCommand(supplierId, row.amount(), "OPENING",
                    blank(row.sourceNo()) ? batch.getBatchNo() : row.sourceNo(), batch.getId(), row.remark()));
            total = total.add(money(row.amount()));
            items.add(item(batch, FUND_OPENING, row.supplierCode(), supplierId, money(row.amount())));
        }
        for (var row : safe(payload.payables())) {
            Payable payable;
            try {
                payable = Payable.importOpening(row.payableNo(), supplierIds.get(upper(row.supplierCode())),
                        row.amount(), row.paidAmount(), row.dueDate(), row.remark());
            } catch (IllegalArgumentException exception) {
                throw new ConflictException("MIGRATION_PAYABLE_INVALID", exception.getMessage());
            }
            payableRepository.save(payable);
            BigDecimal amount = money(nvl(row.amount()).subtract(nvl(row.paidAmount())).max(BigDecimal.ZERO));
            total = total.add(amount);
            items.add(item(batch, PAYABLE_OPENING, row.payableNo(), payable.getId(), amount));
        }        itemRepository.saveAll(items);
        batch.summarize(items.size(), total);
        return MigrationApi.BatchView.from(batchRepository.save(batch));
    }

    @Transactional(readOnly = true)
    public MigrationApi.ReconciliationView reconcile(UUID batchId) {
        MigrationBatch batch = findBatch(batchId);
        List<MigrationItem> items = itemRepository.findByBatchIdOrderByCreatedAtAsc(batchId);
        List<String> missing = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (MigrationItem item : items) {
            counts.merge(item.getItemType(), 1L, Long::sum);
            boolean exists = switch (item.getItemType()) {
                case CUSTOMER -> customerRepository.findById(item.getTargetId()).isPresent();
                case SUPPLIER -> supplierRepository.findById(item.getTargetId()).isPresent();
                case CATEGORY -> categoryRepository.findById(item.getTargetId()).isPresent();
                case PRODUCT -> productRepository.findById(item.getTargetId()).isPresent();
                case SERIAL -> serialRepository.findById(item.getTargetId()).isPresent();
                case STOCK_OPENING -> count("select count(*) from ivs.stock_ledger where biz_type='MIGRATION_OPENING' and biz_id=? and product_id=?",
                        batchId, item.getTargetId()) > 0;
                case RECEIVABLE_OPENING -> receivableRepository.findById(item.getTargetId()).isPresent();
                case PAYABLE_OPENING -> payableRepository.findById(item.getTargetId()).isPresent();
                case FUND_OPENING -> count("select count(*) from ivs.rebate_fund_txn where source_type='OPENING' and source_id=?", batchId) > 0;
                default -> false;
            };
            if (!exists) missing.add(item.getItemType() + ":" + item.getSourceKey());
        }
        counts.forEach((key, value) -> messages.add(key + " = " + value));
        return new MigrationApi.ReconciliationView(batch.getId(), batch.getBatchNo(),
                missing.isEmpty() && batch.getStatus() == MigrationStatus.IMPORTED, items.size(), missing, messages);
    }

    @Transactional
    public MigrationApi.BatchView rollback(UUID batchId) {
        MigrationBatch batch = findBatch(batchId);
        if (batch.getStatus() != MigrationStatus.IMPORTED) {
            throw new ConflictException("MIGRATION_STATUS_INVALID", "只有已导入批次可以回滚");
        }
        fundService.reverseOpening(batchId);
        List<MigrationItem> items = itemRepository.findByBatchIdOrderByCreatedAtAsc(batchId);
        for (MigrationItem item : items) {
            if (!RECEIVABLE_OPENING.equals(item.getItemType())) continue;
            if (!allocationRepository.findByReceivableId(item.getTargetId()).isEmpty()) {
                throw new ConflictException("MIGRATION_ROLLBACK_CONFLICT", "应收已有收款核销，不能回滚：" + item.getSourceKey());
            }
            receivableRepository.deleteById(item.getTargetId());
        }
        receivableRepository.flush();
        for (MigrationItem item : items) {
            if (!STOCK_OPENING.equals(item.getItemType())) continue;
            String[] parts = item.getSourceKey().split("\\|", -1);
            UUID productId = UUID.fromString(parts[0]);
            UUID warehouseId = UUID.fromString(parts[1]);
            String batchNo = parts.length > 2 ? parts[2] : "-";
            if (count("select count(*) from ivs.stock_ledger where product_id=? and warehouse_id=? and not (biz_type='MIGRATION_OPENING' and biz_id=?)",
                    productId, warehouseId, batchId) > 0) {
                throw new ConflictException("MIGRATION_ROLLBACK_CONFLICT", "期初库存已有后续业务，不能回滚：" + item.getSourceKey());
            }
            jdbcTemplate.update("delete from ivs.stock_ledger where biz_type='MIGRATION_OPENING' and biz_id=? and product_id=? and warehouse_id=?",
                    batchId, productId, warehouseId);
            jdbcTemplate.update("delete from ivs.stock_balance where product_id=? and warehouse_id=? and batch_no=?",
                    productId, warehouseId, batchNo);
        }
        for (MigrationItem item : items) {
            if (!SERIAL.equals(item.getItemType())) continue;
            if (count("select count(*) from ivs.stock_ledger where serial_id=?", item.getTargetId()) > 0) {
                throw new ConflictException("MIGRATION_ROLLBACK_CONFLICT", "序列号已有库存业务，不能回滚：" + item.getSourceKey());
            }
            serialRepository.deleteById(item.getTargetId());
        }
        serialRepository.flush();
        for (MigrationItem item : items) {
            if (!PRODUCT.equals(item.getItemType())) continue;
            if (productReferences(item.getTargetId()) > 0) {
                throw new ConflictException("MIGRATION_ROLLBACK_CONFLICT", "商品已被业务引用，不能回滚：" + item.getSourceKey());
            }
            productRepository.deleteById(item.getTargetId());
        }
        productRepository.flush();
        for (MigrationItem item : items) {
            if (!CATEGORY.equals(item.getItemType())) continue;
            if (count("select count(*) from ivs.product where category_id=?", item.getTargetId()) > 0) {
                throw new ConflictException("MIGRATION_ROLLBACK_CONFLICT", "产品大项下仍有商品，不能回滚：" + item.getSourceKey());
            }
            categoryRepository.deleteById(item.getTargetId());
        }
        categoryRepository.flush();
        for (MigrationItem item : items) {
            if (!CUSTOMER.equals(item.getItemType())) continue;
            if (customerReferences(item.getTargetId()) > 0) {
                throw new ConflictException("MIGRATION_ROLLBACK_CONFLICT", "客户已被业务引用，不能回滚：" + item.getSourceKey());
            }
            customerRepository.deleteById(item.getTargetId());
        }
        customerRepository.flush();
        for (MigrationItem item : items) {
            if (!PAYABLE_OPENING.equals(item.getItemType())) continue;
            payableRepository.deleteById(item.getTargetId());
        }
        payableRepository.flush();        for (MigrationItem item : items) {
            if (!SUPPLIER.equals(item.getItemType())) continue;
            if (supplierReferences(item.getTargetId()) > 0) {
                throw new ConflictException("MIGRATION_ROLLBACK_CONFLICT", "供应商已被业务引用，不能回滚：" + item.getSourceKey());
            }
            supplierRepository.deleteById(item.getTargetId());
        }
        supplierRepository.flush();
        batch.rollback();
        return MigrationApi.BatchView.from(batchRepository.save(batch));
    }

    private long productReferences(UUID id) {
        return count("select count(*) from ivs.product_serial where product_id=?", id)
                + count("select count(*) from ivs.stock_ledger where product_id=?", id)
                + count("select count(*) from ivs.stock_balance where product_id=?", id)
                + count("select count(*) from ivs.purchase_order_line where product_id=?", id)
                + count("select count(*) from ivs.sales_order_line where product_id=?", id)
                + count("select count(*) from ivs.sales_delivery_line where product_id=?", id)
                + count("select count(*) from ivs.policy_pre_entitlement where product_id=?", id)
                + count("select count(*) from ivs.policy_entitlement where product_id=?", id)
                + count("select count(*) from ivs.sales_return_line where product_id=?", id)
                + count("select count(*) from ivs.purchase_return_line where product_id=?", id);
    }

    private long customerReferences(UUID id) {
        return count("select count(*) from ivs.sales_order where customer_id=?", id)
                + count("select count(*) from ivs.sales_delivery where customer_id=?", id)
                + count("select count(*) from ivs.receivable where customer_id=?", id)
                + count("select count(*) from ivs.receipt where customer_id=?", id)
                + count("select count(*) from ivs.sales_return where customer_id=?", id)
                + count("select count(*) from ivs.retail_record where customer_id=?", id);
    }

    private long supplierReferences(UUID id) {
        return count("select count(*) from ivs.purchase_order where supplier_id=?", id)
                + count("select count(*) from ivs.purchase_receipt where supplier_id=?", id)
                + count("select count(*) from ivs.purchase_invoice where supplier_id=?", id)
                + count("select count(*) from ivs.purchase_return where supplier_id=?", id)
                + count("select count(*) from ivs.promotion_policy where supplier_id=?", id)
                + count("select count(*) from ivs.rebate_fund_supplier_balance where supplier_id=?", id);
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private MigrationItem item(MigrationBatch batch, String type, String key, UUID target, BigDecimal amount) {
        return MigrationItem.create(batch.getId(), type, key, target, amount);
    }

    private MigrationBatch findBatch(UUID id) {
        return batchRepository.findById(id).orElseThrow(() -> new NotFoundException("迁移批次", id));
    }

    private Map<String, UUID> customerMap() {
        Map<String, UUID> map = new HashMap<>();
        customerRepository.findAllByOrderByCreatedAtDesc().forEach(c -> map.put(c.getCode(), c.getId()));
        return map;
    }

    private Map<String, UUID> supplierMap() {
        Map<String, UUID> map = new HashMap<>();
        supplierRepository.findAllByOrderByCodeAsc().forEach(s -> map.put(s.getCode(), s.getId()));
        return map;
    }

    private Map<String, UUID> categoryMap() {
        Map<String, UUID> map = new HashMap<>();
        categoryRepository.findAllByOrderBySortOrderAscCodeAsc().forEach(c -> map.put(c.getCode(), c.getId()));
        return map;
    }

    private Map<String, UUID> productMap() {
        Map<String, UUID> map = new HashMap<>();
        productRepository.findAllByOrderByCreatedAtDesc().forEach(p -> map.put(p.getProductNo(), p.getId()));
        return map;
    }

    private Map<String, UUID> serialMap() {
        Map<String, UUID> map = new HashMap<>();
        serialRepository.findAllByOrderByCreatedAtDesc().forEach(s -> map.put(s.getSerialNo(), s.getId()));
        return map;
    }

    private Map<String, UUID> warehouseMap() {
        Map<String, UUID> map = new HashMap<>();
        warehouseRepository.findAllByOrderByCodeAsc().forEach(w -> map.put(w.getCode(), w.getId()));
        return map;
    }

    private <T> Set<String> unique(List<T> rows, java.util.function.Function<T, String> getter,
                               String label, List<String> errors) {
        Set<String> values = new HashSet<>();
        for (T row : rows) {
            String value = upper(getter.apply(row));
            if (!blank(value) && !values.add(value)) errors.add(label + "在迁移数据中重复：" + value);
        }
        return values;
    }

    private void checkEnum(Class<? extends Enum<?>> type, String value, String label, List<String> errors) {
        if (blank(value)) return;
        try {
            Enum.valueOf((Class) type, value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            errors.add(label + "无效：" + value);
        }
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        return blank(value) ? null : Enum.valueOf(type, value.trim().toUpperCase());
    }

    private String upper(String value) { return value == null ? null : value.trim().toUpperCase(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private BigDecimal nvl(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private BigDecimal money(BigDecimal value) { return nvl(value).setScale(2, RoundingMode.HALF_UP); }
    private <T> List<T> safe(List<T> value) { return value == null ? List.of() : value; }
    private UUID currentUserId() { return actors.current().map(actor -> actor.userId()).orElse(null); }
}










