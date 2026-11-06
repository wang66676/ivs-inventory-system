package com.ivs.masterdata.serial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivs.masterdata.SerialStatus;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.product.Product;
import com.ivs.masterdata.product.ProductAttrDef;
import com.ivs.masterdata.product.ProductCategory;
import com.ivs.masterdata.product.ProductCategoryService;
import com.ivs.masterdata.product.ProductService;
import com.ivs.masterdata.product.TrackingMode;
import com.ivs.masterdata.warehouse.Warehouse;
import com.ivs.masterdata.warehouse.WarehouseRepository;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import com.ivs.shared.event.EventEnvelope;
import com.ivs.shared.event.EventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 序列号档案：整车 VIN 与动力电池 SN 一物一档。
 *
 * <p>建档只负责身份、属性与状态机；库存数量与台账由 M2 库存模块承担。</p>
 */
@Service
public class ProductSerialService {

    private final ProductSerialRepository serialRepository;
    private final ProductService productService;
    private final ProductCategoryService categoryService;
    private final WarehouseRepository warehouseRepository;
    private final ObjectMapper objectMapper;
    private final EventPublisher events;

    public ProductSerialService(ProductSerialRepository serialRepository,
                                ProductService productService,
                                ProductCategoryService categoryService,
                                WarehouseRepository warehouseRepository,
                                ObjectMapper objectMapper,
                                EventPublisher events) {
        this.serialRepository = serialRepository;
        this.productService = productService;
        this.categoryService = categoryService;
        this.warehouseRepository = warehouseRepository;
        this.objectMapper = objectMapper;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<MasterdataApi.SerialView> list(String keyword, SerialStatus status, UUID warehouseId) {
        List<ProductSerial> serials;
        if (keyword != null && !keyword.isBlank()) {
            serials = serialRepository.search("%" + keyword.trim().toLowerCase() + "%");
        } else if (warehouseId != null && status != null) {
            serials = serialRepository.findByWarehouseIdAndStatusOrderByCreatedAtDesc(warehouseId, status);
        } else if (status != null) {
            serials = serialRepository.findByStatusOrderByCreatedAtDesc(status);
        } else {
            serials = serialRepository.findAllByOrderByCreatedAtDesc();
        }
        return serials.stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public MasterdataApi.SerialView get(UUID id) {
        return toView(find(id));
    }

    @Transactional
    public MasterdataApi.SerialView create(MasterdataApi.SaveSerialCommand command) {
        Product product = productService.find(command.productId());
        ProductCategory category = categoryService.find(product.getCategoryId());
        if (category.getTrackingMode() != TrackingMode.SERIAL) {
            throw new ConflictException("SERIAL_NOT_TRACKED",
                    "产品大项「" + category.getName() + "」按数量管理，不建立序列号档案");
        }
        String serialNo = command.serialNo() == null ? "" : command.serialNo().trim().toUpperCase();
        if (serialNo.isEmpty()) {
            throw new ConflictException("SERIAL_NO_REQUIRED", "序列号（VIN / 电池 SN）不能为空");
        }
        if (serialRepository.existsBySerialNo(serialNo)) {
            throw new ConflictException("SERIAL_NO_DUPLICATE", "序列号已存在：" + serialNo);
        }
        if (command.warehouseId() != null) {
            requireWarehouse(command.warehouseId());
        }
        validateAttrs(category, command.attrsJson());
        ProductSerial serial = ProductSerial.create(serialNo, product.getId(), command.warehouseId(),
                command.locationCode(), command.batchNo(), command.purchasePrice(), command.inboundAt(),
                command.certificateApplicationDate(), command.warrantyStart(), command.warrantyEnd(),
                command.ownershipType(), command.attrsJson(), command.remark());
        ProductSerial saved = serialRepository.save(serial);
        publish(saved, "SerialRegistered", Map.of("serialNo", saved.getSerialNo(), "productNo", product.getProductNo()));
        return toView(saved);
    }

    /**
     * 批量导入：先整体校验（校验不通过的单条不影响其它单条），通过后统一落库。
     * 校验失败的行在返回结果中逐条给出原因。
     */
    @Transactional
    public MasterdataApi.BatchImportResult importBatch(List<MasterdataApi.SaveSerialCommand> commands) {
        List<MasterdataApi.SerialView> created = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<ProductSerial> pending = new ArrayList<>();
        java.util.Set<String> seenSerialNos = new java.util.HashSet<>();
        if (commands != null) {
            for (MasterdataApi.SaveSerialCommand command : commands) {
                try {
                    Product product = productService.find(command.productId());
                    ProductCategory category = categoryService.find(product.getCategoryId());
                    if (category.getTrackingMode() != TrackingMode.SERIAL) {
                        throw new IllegalStateException("产品大项按数量管理，不建立序列号档案");
                    }
                    String serialNo = command.serialNo() == null ? "" : command.serialNo().trim().toUpperCase();
                    if (serialNo.isEmpty()) {
                        throw new IllegalStateException("序列号不能为空");
                    }
                    if (!seenSerialNos.add(serialNo)) {
                        throw new IllegalStateException("序列号在本次导入中重复");
                    }
                    if (serialRepository.existsBySerialNo(serialNo)) {
                        throw new IllegalStateException("序列号已存在");
                    }
                    validateAttrs(category, command.attrsJson());
                    pending.add(ProductSerial.create(serialNo, product.getId(), command.warehouseId(),
                            command.locationCode(), command.batchNo(), command.purchasePrice(),
                            command.inboundAt(), command.certificateApplicationDate(), command.warrantyStart(),
                            command.warrantyEnd(), command.ownershipType(), command.attrsJson(), command.remark()));
                } catch (RuntimeException exception) {
                    errors.add((command.serialNo() == null ? "(空序列号)" : command.serialNo()) + "：" + exception.getMessage());
                }
            }
        }
        for (ProductSerial serial : pending) {
            ProductSerial saved = serialRepository.save(serial);
            created.add(toView(saved));
            publish(saved, "SerialRegistered", Map.of("serialNo", saved.getSerialNo()));
        }
        return new MasterdataApi.BatchImportResult(created.size(), errors.size(), errors, created);
    }

    @Transactional
    public MasterdataApi.SerialView update(UUID id, MasterdataApi.SaveSerialCommand command) {
        ProductSerial serial = find(id);
        if (command.warehouseId() != null) {
            requireWarehouse(command.warehouseId());
        }
        serial.update(command.warehouseId(), command.locationCode(), command.batchNo(), command.purchasePrice(),
                command.inboundAt(), command.certificateApplicationDate(), command.warrantyStart(),
                command.warrantyEnd(), command.ownershipType(), command.attrsJson(), command.remark());
        return toView(serialRepository.save(serial));
    }

    /** 收货入库时回写仓库、采购价与入库时间（不改状态，状态由 moveStatus 按模板流转）。 */
    @Transactional
    public void applyInbound(UUID id, UUID warehouseId, java.math.BigDecimal purchasePrice, Instant inboundAt) {
        applyInbound(id, warehouseId, purchasePrice, inboundAt, null);
    }

    public void applyInbound(UUID id, UUID warehouseId, java.math.BigDecimal purchasePrice, Instant inboundAt,
                             java.time.LocalDate certificateApplicationDate) {
        ProductSerial serial = find(id);
        serial.applyInbound(warehouseId, purchasePrice, inboundAt, certificateApplicationDate);
        serialRepository.save(serial);
    }

    /** 收货后补录或调整申请合格证日期，按 VIN/SN 批量同步一物一档。 */
    @Transactional
    public void updateCertificateApplicationDate(List<String> serialNos, java.time.LocalDate certificateApplicationDate) {
        if (serialNos == null || serialNos.isEmpty()) {
            return;
        }
        for (String serialNo : serialNos) {
            if (serialNo == null || serialNo.isBlank()) {
                continue;
            }
            ProductSerial serial = serialRepository.findBySerialNo(serialNo.trim().toUpperCase())
                    .orElseThrow(() -> new NotFoundException("序列号档案", serialNo));
            serial.updateCertificateApplicationDate(certificateApplicationDate);
            serialRepository.save(serial);
        }
    }

    /** 分期以租代售出库时标记为租赁转自有，全部结清后再转为自有。 */
    @Transactional
    public void changeOwnership(UUID id, com.ivs.masterdata.OwnershipType ownershipType) {
        ProductSerial serial = find(id);
        serial.changeOwnership(ownershipType);
        ProductSerial saved = serialRepository.save(serial);
        publish(saved, "SerialOwnershipChanged", Map.of("serialNo", saved.getSerialNo(),
                "ownershipType", saved.getOwnershipType().name()));
    }

    /** 按状态机模板求合法流转路径，供收货等场景逐步推进状态。 */
    @Transactional(readOnly = true)
    public java.util.List<SerialStatus> statusPath(UUID id, SerialStatus target) {
        ProductSerial serial = find(id);
        Product product = productService.find(serial.getProductId());
        ProductCategory category = categoryService.find(product.getCategoryId());
        return category.statusTemplate().pathTo(serial.getStatus(), target);
    }

    @Transactional
    public MasterdataApi.SerialView moveStatus(UUID id, SerialStatus targetStatus) {
        ProductSerial serial = find(id);
        Product product = productService.find(serial.getProductId());
        ProductCategory category = categoryService.find(product.getCategoryId());
        SerialStatus from = serial.getStatus();
        try {
            serial.moveTo(targetStatus, category.statusTemplate());
        } catch (IllegalStateException exception) {
            throw new ConflictException("SERIAL_STATUS_INVALID", exception.getMessage());
        }
        ProductSerial saved = serialRepository.save(serial);
        publish(saved, "SerialStatusChanged", Map.of(
                "serialNo", saved.getSerialNo(), "from", from.name(), "to", saved.getStatus().name()));
        return toView(saved);
    }

    @Transactional(readOnly = true)
    public ProductSerial find(UUID id) {
        return serialRepository.findById(id).orElseThrow(() -> new NotFoundException("序列号档案", id));
    }

    private void requireWarehouse(UUID warehouseId) {
        warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new NotFoundException("仓库", warehouseId));
    }

    /** 必填属性校验：按产品大项的属性模板检查扩展字段。 */
    private void validateAttrs(ProductCategory category, String attrsJson) {
        List<ProductAttrDef> defs = categoryService.attrDefs(category.getId());
        List<String> required = defs.stream().filter(ProductAttrDef::isRequired).map(ProductAttrDef::getCode).toList();
        if (required.isEmpty()) {
            return;
        }
        Map<String, Object> attrs = new LinkedHashMap<>();
        if (attrsJson != null && !attrsJson.isBlank()) {
            try {
                attrs = objectMapper.readValue(attrsJson, new com.fasterxml.jackson.core.type.TypeReference<>() {
                });
            } catch (Exception exception) {
                throw new ConflictException("SERIAL_ATTRS_INVALID", "扩展属性必须是合法的 JSON 对象");
            }
        }
        for (String code : required) {
            Object value = attrs.get(code);
            if (value == null || String.valueOf(value).isBlank()) {
                throw new ConflictException("SERIAL_ATTR_REQUIRED", "缺少必填扩展属性：" + code);
            }
        }
    }

    private void publish(ProductSerial serial, String eventType, Map<String, Object> payload) {
        events.publish(new EventEnvelope(UUID.randomUUID(), eventType, "ProductSerial", serial.getId(),
                serial.getVersion(), null, UUID.randomUUID(), Instant.now(), payload));
    }

    private MasterdataApi.SerialView toView(ProductSerial serial) {
        Product product = productService.find(serial.getProductId());
        ProductCategory category = categoryService.find(product.getCategoryId());
        String warehouseName = serial.getWarehouseId() == null ? null
                : warehouseRepository.findById(serial.getWarehouseId()).map(Warehouse::getName).orElse(null);
        return MasterdataApi.SerialView.from(serial, product, category, warehouseName);
    }
}
