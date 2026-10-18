package com.ivs.inventory.transfer;

import com.ivs.inventory.stock.StockBalance;
import com.ivs.inventory.stock.StockLedger;
import com.ivs.inventory.stock.StockMoveType;
import com.ivs.inventory.stock.StockService;
import com.ivs.inventory.transfer.api.StockTransferApi;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import com.ivs.shared.security.CurrentActorProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/** 库存调拨：调出仓审核发出、在途跟踪、调入仓签收与差异登记。 */
@Service
public class StockTransferApplicationService {

    private static final String BIZ_TYPE = "STOCK_TRANSFER";

    private final StockTransferRepository transferRepository;
    private final StockTransferLineRepository lineRepository;
    private final StockService stockService;
    private final CurrentActorProvider actors;

    public StockTransferApplicationService(StockTransferRepository transferRepository,
                                           StockTransferLineRepository lineRepository,
                                           StockService stockService,
                                           CurrentActorProvider actors) {
        this.transferRepository = transferRepository;
        this.lineRepository = lineRepository;
        this.stockService = stockService;
        this.actors = actors;
    }

    @Transactional(readOnly = true)
    public List<StockTransferApi.TransferView> list(UUID warehouseId, StockTransferStatus status) {
        List<StockTransfer> rows = warehouseId != null
                ? transferRepository.findByFromWarehouseIdOrToWarehouseIdOrderByCreatedAtDesc(warehouseId, warehouseId)
                : status != null ? transferRepository.findByStatusOrderByCreatedAtDesc(status)
                : transferRepository.findAllByOrderByCreatedAtDesc();
        return rows.stream().filter(row -> status == null || row.getStatus() == status)
                .map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public StockTransferApi.TransferView get(UUID id) {
        return toView(find(id));
    }

    @Transactional
    public StockTransferApi.TransferView create(StockTransferApi.CreateTransferCommand command) {
        if (command == null || command.fromWarehouseId() == null || command.toWarehouseId() == null) {
            throw new ConflictException("TRANSFER_WAREHOUSE_REQUIRED", "请选择调出仓与调入仓");
        }
        if (command.fromWarehouseId().equals(command.toWarehouseId())) {
            throw new ConflictException("TRANSFER_WAREHOUSE_SAME", "调出仓与调入仓不能相同");
        }
        StockTransfer transfer = StockTransfer.create(
                "TR-%06d".formatted(transferRepository.nextTransferSequence()), command.fromWarehouseId(),
                command.toWarehouseId(), currentUserId(), command.remark());
        transferRepository.save(transfer);
        saveLines(transfer, command.lines());
        transferRepository.save(transfer);
        return toView(transfer);
    }

    @Transactional
    public StockTransferApi.TransferView update(UUID id, StockTransferApi.UpdateTransferCommand command) {
        StockTransfer transfer = find(id);
        if (transfer.getStatus() != StockTransferStatus.DRAFT) {
            throw new ConflictException("TRANSFER_STATUS_INVALID", "只有草稿调拨单可以修改");
        }
        lineRepository.deleteByTransferId(id);
        saveLines(transfer, command == null ? null : command.lines());
        transferRepository.save(transfer);
        return toView(transfer);
    }

    @Transactional
    public StockTransferApi.TransferView approve(UUID id) {
        StockTransfer transfer = find(id);
        try {
            transfer.approve(currentUserId());
        } catch (IllegalStateException exception) {
            throw new ConflictException("TRANSFER_STATUS_INVALID", exception.getMessage());
        }
        return toView(transferRepository.save(transfer));
    }

    /** 审核后发出：从调出仓扣减可用库存，写入 TRANSFER_OUT 台账，调拨单进入在途。 */
    @Transactional
    public StockTransferApi.TransferView dispatch(UUID id) {
        StockTransfer transfer = find(id);
        if (transfer.getStatus() != StockTransferStatus.APPROVED) {
            throw new ConflictException("TRANSFER_STATUS_INVALID", "只有已审核调拨单可以发出");
        }
        List<StockTransferLine> lines = lineRepository.findByTransferIdOrderByCreatedAtAsc(id);
        for (StockTransferLine line : lines) {
            StockLedger ledger = stockService.issueAvailable(line.getProductId(), null, transfer.getFromWarehouseId(),
                    line.getBatchNo(), line.getQuantity(), StockMoveType.TRANSFER_OUT, BIZ_TYPE,
                    transfer.getId(), transfer.getTransferNo(), "调拨发出");
            line.dispatchedAtCost(ledger.getUnitCost());
            lineRepository.save(line);
        }
        transfer.dispatch(currentUserId());
        return toView(transferRepository.save(transfer));
    }

    /** 调入仓签收：按实际签收数量入库，短缺必须登记原因。 */
    @Transactional
    public StockTransferApi.TransferView receive(UUID id, StockTransferApi.ReceiveTransferCommand command) {
        StockTransfer transfer = find(id);
        if (transfer.getStatus() != StockTransferStatus.IN_TRANSIT) {
            throw new ConflictException("TRANSFER_STATUS_INVALID", "只有在途调拨单可以签收");
        }
        List<StockTransferLine> lines = lineRepository.findByTransferIdOrderByCreatedAtAsc(id);
        if (command == null || command.lines() == null || command.lines().size() != lines.size()) {
            throw new ConflictException("TRANSFER_RECEIVE_LINES_REQUIRED", "请逐行填写签收数量");
        }
        Map<UUID, StockTransferApi.ReceiveLineCommand> commands = new HashMap<>();
        for (StockTransferApi.ReceiveLineCommand line : command.lines()) {
            if (line.lineId() == null) {
                throw new ConflictException("TRANSFER_RECEIVE_INVALID", "缺少调拨明细标识");
            }
            commands.put(line.lineId(), line);
        }
        for (StockTransferLine line : lines) {
            StockTransferApi.ReceiveLineCommand lineCommand = commands.get(line.getId());
            if (lineCommand == null) {
                throw new ConflictException("TRANSFER_RECEIVE_INVALID", "请填写所有调拨明细的签收数量");
            }
            try {
                line.receive(lineCommand.receivedQuantity(), lineCommand.differenceReason());
            } catch (IllegalArgumentException exception) {
                throw new ConflictException("TRANSFER_RECEIVE_INVALID", exception.getMessage());
            }
            if (line.getReceivedQuantity().signum() > 0) {
                stockService.receive(line.getProductId(), null, transfer.getToWarehouseId(), line.getBatchNo(),
                        line.getReceivedQuantity(), line.getUnitCost(), true, StockMoveType.TRANSFER_IN,
                        BIZ_TYPE, transfer.getId(), transfer.getTransferNo(), "调拨签收");
            }
            lineRepository.save(line);
        }
        transfer.receive(currentUserId(), lines);
        return toView(transferRepository.save(transfer));
    }

    @Transactional
    public StockTransferApi.TransferView cancel(UUID id) {
        StockTransfer transfer = find(id);
        try {
            transfer.cancel();
        } catch (IllegalStateException exception) {
            throw new ConflictException("TRANSFER_STATUS_INVALID", exception.getMessage());
        }
        return toView(transferRepository.save(transfer));
    }

    private void saveLines(StockTransfer transfer, List<StockTransferApi.SaveLineCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            throw new ConflictException("TRANSFER_LINES_REQUIRED", "调拨单至少需要一条明细");
        }
        Set<String> keys = new HashSet<>();
        List<StockTransferLine> lines = new ArrayList<>();
        for (StockTransferApi.SaveLineCommand command : commands) {
            if (command == null || command.productId() == null) {
                throw new ConflictException("TRANSFER_PRODUCT_REQUIRED", "调拨明细必须选择商品");
            }
            String batch = normalizeBatch(command.batchNo());
            if (!keys.add(command.productId() + "|" + batch)) {
                throw new ConflictException("TRANSFER_LINE_DUPLICATE", "同一商品批次不能重复调拨");
            }
            StockBalance balance = stockService.balance(command.productId(), transfer.getFromWarehouseId(), batch);
            BigDecimal cost = command.unitCost() == null ? balance.getUnitCost() : command.unitCost();
            StockTransferLine line;
            try {
                line = StockTransferLine.create(transfer.getId(), command.productId(), batch,
                        command.quantity(), cost);
            } catch (IllegalArgumentException exception) {
                throw new ConflictException("TRANSFER_LINE_INVALID", exception.getMessage());
            }
            lines.add(lineRepository.save(line));
        }
        transfer.summarize(lines);
    }

    private StockTransferApi.TransferView toView(StockTransfer transfer) {
        List<StockTransferApi.LineView> lines = lineRepository.findByTransferIdOrderByCreatedAtAsc(transfer.getId())
                .stream().map(StockTransferApi.LineView::from).toList();
        return StockTransferApi.TransferView.from(transfer, lines);
    }

    private StockTransfer find(UUID id) {
        return transferRepository.findById(id).orElseThrow(() -> new NotFoundException("库存调拨单", id));
    }

    private String normalizeBatch(String batchNo) {
        return batchNo == null || batchNo.isBlank() ? StockBalance.NO_BATCH : batchNo.trim();
    }

    private UUID currentUserId() {
        return actors.current().map(actor -> actor.userId()).orElse(null);
    }
}
