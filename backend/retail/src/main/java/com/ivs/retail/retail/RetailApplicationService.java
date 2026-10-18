package com.ivs.retail.retail;

import com.ivs.retail.api.RetailApi;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import com.ivs.shared.event.EventEnvelope;
import com.ivs.shared.event.EventPublisher;
import com.ivs.shared.security.CurrentActorProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 实销登记、复核与冲正。
 *
 * <p>实销确认是政策返利的时间与数量基准：确认后发布 {@code RetailConfirmed} 事件，
 * 后续政策引擎据此生成权益（M4-2 下一块）。</p>
 */
@Service
public class RetailApplicationService {

    private final RetailRecordRepository retailRepository;
    private final RetailReversalRepository reversalRepository;
    private final RetailMasterDataPort masterData;
    private final EventPublisher events;
    private final CurrentActorProvider actors;

    public RetailApplicationService(RetailRecordRepository retailRepository,
                                    RetailReversalRepository reversalRepository,
                                    RetailMasterDataPort masterData,
                                    EventPublisher events,
                                    CurrentActorProvider actors) {
        this.retailRepository = retailRepository;
        this.reversalRepository = reversalRepository;
        this.masterData = masterData;
        this.events = events;
        this.actors = actors;
    }

    @Transactional(readOnly = true)
    public List<RetailApi.RetailView> list(RetailStatus status, LocalDate from, LocalDate to) {
        List<RetailRecord> records;
        if (from != null && to != null) {
            records = retailRepository.findByRetailDateBetweenOrderByRetailDateDesc(from, to);
        } else if (status != null) {
            records = retailRepository.findByStatusOrderByCreatedAtDesc(status);
        } else {
            records = retailRepository.findAllByOrderByCreatedAtDesc();
        }
        return records.stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public RetailApi.RetailView get(UUID id) {
        return toView(find(id));
    }

    @Transactional
    public RetailApi.RetailView create(RetailApi.SaveRetailCommand command) {
        RetailMasterDataPort.SerialInfo serial = masterData.requireSerial(command.serialId());
        if (!"SERIAL".equals(serial.trackingMode())) {
            throw new ConflictException("RETAIL_NOT_SERIAL", "该商品按数量管理，不能登记整车实销");
        }
        if (retailRepository.findByActiveSerialKey(serial.id()).isPresent()) {
            throw new ConflictException("RETAIL_DUPLICATE_SERIAL",
                    "序列号「" + serial.serialNo() + "」已有有效实销记录，如需重新登记请先冲正");
        }
        RetailRecord record;
        try {
            record = RetailRecord.create("RT-%06d".formatted(retailRepository.nextRetailSequence()),
                    command.retailDate(), command.invoiceIssuedAt(), command.registerDate(),
                    command.deliveryDate(), serial.id(), serial.serialNo(), serial.productId(),
                    command.customerId(), command.orderId(), command.userName(), command.userMobile(),
                    command.plateNo(), command.salePrice(), command.remark());
        } catch (IllegalArgumentException exception) {
            throw new ConflictException("RETAIL_INVALID", exception.getMessage());
        }
        RetailRecord saved = retailRepository.save(record);
        publish(saved, "RetailRegistered", Map.of("retailNo", saved.getRetailNo(),
                "serialNo", saved.getSerialNo(), "retailDate", saved.getRetailDate().toString()));
        return toView(saved);
    }

    @Transactional
    public RetailApi.RetailView update(UUID id, RetailApi.SaveRetailCommand command) {
        RetailRecord record = find(id);
        try {
            record.update(command.retailDate(), command.invoiceIssuedAt(), command.registerDate(),
                    command.deliveryDate(), command.userName(), command.userMobile(), command.plateNo(),
                    command.salePrice(), command.remark());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ConflictException("RETAIL_UPDATE_INVALID", exception.getMessage());
        }
        return toView(retailRepository.save(record));
    }

    @Transactional
    public RetailApi.RetailView submit(UUID id) {
        RetailRecord record = find(id);
        try {
            record.submit();
        } catch (IllegalStateException exception) {
            throw new ConflictException("RETAIL_STATUS_INVALID", exception.getMessage());
        }
        return toView(retailRepository.save(record));
    }

    /** 复核确认：成为政策兑现基准，只能通过冲正修改。 */
    @Transactional
    public RetailApi.RetailView verify(UUID id) {
        RetailRecord record = find(id);
        try {
            record.verify(currentUserId());
        } catch (IllegalStateException exception) {
            throw new ConflictException("RETAIL_STATUS_INVALID", exception.getMessage());
        }
        RetailRecord saved = retailRepository.save(record);
        Map<String, Object> payload = new LinkedHashMap<>();
        // 事件信封使用 Map.copyOf，null 值会抛异常，因此只放非空字段
        putIfPresent(payload, "retailId", saved.getId());
        putIfPresent(payload, "retailNo", saved.getRetailNo());
        putIfPresent(payload, "serialNo", saved.getSerialNo());
        putIfPresent(payload, "productId", saved.getProductId().toString());
        putIfPresent(payload, "retailDate", saved.getRetailDate().toString());
        putIfPresent(payload, "salePrice", saved.getSalePrice());
        publish(saved, "RetailConfirmed", payload);
        return toView(saved);
    }

    @Transactional
    public RetailApi.RetailView reverse(UUID id, RetailApi.ReverseRetailCommand command) {
        RetailRecord record = find(id);
        if (command == null || command.reason() == null || command.reason().isBlank()) {
            throw new ConflictException("RETAIL_REVERSAL_REASON_REQUIRED", "请填写冲正原因");
        }
        try {
            record.reverse();
        } catch (IllegalStateException exception) {
            throw new ConflictException("RETAIL_STATUS_INVALID", exception.getMessage());
        }
        RetailRecord saved = retailRepository.save(record);
        reversalRepository.save(RetailReversal.create(saved.getId(), command.reason().trim(),
                command.newSerialId(), currentUserId()));
        publish(saved, "RetailReversed", Map.of("retailNo", saved.getRetailNo(),
                "serialNo", saved.getSerialNo(), "reason", command.reason().trim()));
        return toView(saved);
    }

    private RetailRecord find(UUID id) {
        return retailRepository.findById(id).orElseThrow(() -> new NotFoundException("实销记录", id));
    }

    private RetailApi.RetailView toView(RetailRecord record) {
        RetailMasterDataPort.SerialInfo serial = masterData.requireSerial(record.getSerialId());
        String customerName = record.getCustomerId() == null ? null
                : masterData.customerName(record.getCustomerId());
        return RetailApi.RetailView.from(record, serial.productNo(), serial.productName(),
                serial.categoryCode(), customerName);
    }

    private void publish(RetailRecord record, String eventType, Map<String, Object> payload) {
        events.publish(new EventEnvelope(UUID.randomUUID(), eventType, "RetailRecord", record.getId(),
                record.getVersion(), null, UUID.randomUUID(), Instant.now(), payload));
    }

    private void putIfPresent(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
    }

    private UUID currentUserId() {
        return actors.current().map(actor -> actor.userId()).orElse(null);
    }
}