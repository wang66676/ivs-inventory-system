package com.ivs.inventory.stocktake;

import com.ivs.inventory.stock.StockBalance;
import com.ivs.inventory.stock.StockBalanceRepository;
import com.ivs.inventory.stock.StockService;
import com.ivs.inventory.stocktake.api.StocktakeApi;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import com.ivs.shared.security.CurrentActorProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** 库存盘点：账面快照 → 实盘录入 → 差异过账。 */
@Service
public class StocktakeApplicationService {

    private final StocktakeRepository stocktakeRepository;
    private final StocktakeLineRepository lineRepository;
    private final StockBalanceRepository balanceRepository;
    private final StockService stockService;
    private final StocktakeApprovalPort approval;
    private final CurrentActorProvider actors;

    public StocktakeApplicationService(StocktakeRepository stocktakeRepository,
                                       StocktakeLineRepository lineRepository,
                                       StockBalanceRepository balanceRepository,
                                       StockService stockService,
                                       StocktakeApprovalPort approval,
                                       CurrentActorProvider actors) {
        this.stocktakeRepository = stocktakeRepository;
        this.lineRepository = lineRepository;
        this.balanceRepository = balanceRepository;
        this.stockService = stockService;
        this.approval = approval;
        this.actors = actors;
    }

    @Transactional(readOnly = true)
    public List<StocktakeApi.StocktakeView> list(UUID warehouseId, StocktakeStatus status) {
        List<Stocktake> rows = warehouseId != null ? stocktakeRepository.findByWarehouseIdOrderByCreatedAtDesc(warehouseId)
                : status != null ? stocktakeRepository.findByStatusOrderByCreatedAtDesc(status)
                : stocktakeRepository.findAllByOrderByCreatedAtDesc();
        return rows.stream()
                .filter(row -> status == null || row.getStatus() == status)
                .map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public StocktakeApi.StocktakeView get(UUID id) {
        return toView(find(id));
    }

    @Transactional
    public StocktakeApi.StocktakeView create(StocktakeApi.CreateStocktakeCommand command) {
        if (command == null || command.warehouseId() == null) {
            throw new ConflictException("STOCKTAKE_WAREHOUSE_REQUIRED", "请选择盘点仓库");
        }
        Stocktake stocktake = Stocktake.create(
                "ST-%06d".formatted(stocktakeRepository.nextStocktakeSequence()), command.warehouseId(),
                currentUserId(), command.remark());
        stocktakeRepository.save(stocktake);
        List<StocktakeApi.SaveLineCommand> commands = command.lines();
        if (commands == null || commands.isEmpty()) {
            commands = currentBalanceLines(command.warehouseId());
        }
        saveLines(stocktake, commands, Map.of());
        stocktakeRepository.save(stocktake);
        return toView(stocktake);
    }

    @Transactional
    public StocktakeApi.StocktakeView update(UUID id, StocktakeApi.UpdateStocktakeCommand command) {
        Stocktake stocktake = find(id);
        requireDraft(stocktake);
        if (command == null || command.lines() == null) {
            throw new ConflictException("STOCKTAKE_LINES_REQUIRED", "请填写盘点明细");
        }
        Map<String, StocktakeLine> existing = new HashMap<>();
        for (StocktakeLine line : lineRepository.findByStocktakeIdOrderByCreatedAtAsc(id)) {
            existing.put(key(line.getProductId(), line.getBatchNo()), line);
        }
        lineRepository.deleteByStocktakeId(id);
        saveLines(stocktake, command.lines(), existing);
        stocktakeRepository.save(stocktake);
        return toView(stocktake);
    }

    @Transactional
    public StocktakeApi.StocktakeView post(UUID id) {
        Stocktake stocktake = find(id);
        requireDraft(stocktake);
        List<StocktakeLine> lines = lineRepository.findByStocktakeIdOrderByCreatedAtAsc(id);
        validatePostable(stocktake, lines);
        BigDecimal differenceAmount = lines.stream()
                .map(line -> line.getDifferenceQty().abs().multiply(line.getUnitCost()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        StocktakeApprovalPort.AdjustmentDecision decision = approval.evaluate(differenceAmount);
        if (decision.required()) {
            stocktake.markPendingApproval();
            Stocktake saved = stocktakeRepository.save(stocktake);
            approval.syncPending(saved.getId(), saved.getStocktakeNo(), differenceAmount, decision);
            return toView(saved);
        }
        approval.cancel(id);
        return executePost(stocktake, lines);
    }

    /** 审批通过后执行实际过账；只允许待审批状态调用。 */
    @Transactional
    public StocktakeApi.StocktakeView postApproved(UUID id) {
        Stocktake stocktake = find(id);
        if (stocktake.getStatus() != StocktakeStatus.PENDING_APPROVAL) {
            throw new ConflictException("STOCKTAKE_STATUS_INVALID", "盘点单不在待审批状态");
        }
        List<StocktakeLine> lines = lineRepository.findByStocktakeIdOrderByCreatedAtAsc(id);
        validatePostable(stocktake, lines);
        return executePost(stocktake, lines);
    }

    /** 审批驳回后回到草稿，申请人可修改后重新提交。 */
    @Transactional
    public StocktakeApi.StocktakeView rejectApproval(UUID id) {
        Stocktake stocktake = find(id);
        try {
            stocktake.rejectApproval();
        } catch (IllegalStateException exception) {
            throw new ConflictException("STOCKTAKE_STATUS_INVALID", exception.getMessage());
        }
        return toView(stocktakeRepository.save(stocktake));
    }

    private void validatePostable(Stocktake stocktake, List<StocktakeLine> lines) {
        if (lines.isEmpty()) {
            throw new ConflictException("STOCKTAKE_LINE_EMPTY", "盘点单没有明细，不能过账");
        }
        for (StocktakeLine line : lines) {
            line.validateReason();
            StockBalance current = stockService.balance(line.getProductId(), stocktake.getWarehouseId(),
                    line.getBatchNo());
            if (current.getOnHandQty().compareTo(line.getBookQty()) != 0) {
                throw new ConflictException("STOCKTAKE_BALANCE_CHANGED",
                        "商品 " + line.getProductId() + " 批次 " + line.getBatchNo()
                                + " 的库存已变化（账面快照 " + line.getBookQty().toPlainString()
                                + "，当前 " + current.getOnHandQty().toPlainString() + "），请重新生成盘点单");
            }
        }
    }

    private StocktakeApi.StocktakeView executePost(Stocktake stocktake, List<StocktakeLine> lines) {
        for (StocktakeLine line : lines) {
            if (line.getDifferenceQty().signum() != 0) {
                stockService.adjustTo(line.getProductId(), stocktake.getWarehouseId(), line.getBatchNo(),
                        line.getActualQty(), line.getUnitCost(), "STOCKTAKE", stocktake.getId(),
                        stocktake.getStocktakeNo(), line.getDifferenceReason());
            }
        }
        stocktake.post(currentUserId());
        return toView(stocktakeRepository.save(stocktake));
    }

    @Transactional
    public StocktakeApi.StocktakeView cancel(UUID id) {
        Stocktake stocktake = find(id);
        try {
            stocktake.cancel();
        } catch (IllegalStateException exception) {
            throw new ConflictException("STOCKTAKE_STATUS_INVALID", exception.getMessage());
        }
        Stocktake saved = stocktakeRepository.save(stocktake);
        approval.cancel(saved.getId());
        return toView(saved);
    }

    private void saveLines(Stocktake stocktake, List<StocktakeApi.SaveLineCommand> commands,
                           Map<String, StocktakeLine> existing) {
        if (commands == null) commands = List.of();
        Set<String> keys = new HashSet<>();
        List<StocktakeLine> saved = new ArrayList<>();
        for (StocktakeApi.SaveLineCommand command : commands) {
            if (command == null || command.productId() == null) {
                throw new ConflictException("STOCKTAKE_PRODUCT_REQUIRED", "盘点明细必须选择商品");
            }
            String batch = normalizeBatch(command.batchNo());
            String key = key(command.productId(), batch);
            if (!keys.add(key)) {
                throw new ConflictException("STOCKTAKE_LINE_DUPLICATE", "同一商品批次不能重复盘点");
            }
            StocktakeLine old = existing.get(key);
            BigDecimal bookQty;
            BigDecimal unitCost;
            if (old != null) {
                bookQty = old.getBookQty();
                unitCost = command.unitCost() == null ? old.getUnitCost() : command.unitCost();
            } else {
                StockBalance balance = stockService.balance(command.productId(), stocktake.getWarehouseId(), batch);
                bookQty = balance.getOnHandQty();
                unitCost = command.unitCost() == null ? balance.getUnitCost() : command.unitCost();
            }
            StocktakeLine line;
            try {
                line = StocktakeLine.create(stocktake.getId(), command.productId(), batch, bookQty,
                        command.actualQty(), unitCost, command.differenceReason());
            } catch (IllegalArgumentException exception) {
                throw new ConflictException("STOCKTAKE_LINE_INVALID", exception.getMessage());
            }
            saved.add(lineRepository.save(line));
        }
        stocktake.summarize(saved);
    }

    private List<StocktakeApi.SaveLineCommand> currentBalanceLines(UUID warehouseId) {
        return balanceRepository.findByWarehouseIdOrderByProductIdAsc(warehouseId).stream()
                .filter(balance -> balance.getOnHandQty().signum() != 0 || balance.getLockedQty().signum() != 0)
                .map(balance -> new StocktakeApi.SaveLineCommand(balance.getProductId(), balance.getBatchNo(),
                        balance.getOnHandQty(), balance.getUnitCost(), null))
                .toList();
    }

    private StocktakeApi.StocktakeView toView(Stocktake stocktake) {
        List<StocktakeApi.LineView> lines = lineRepository.findByStocktakeIdOrderByCreatedAtAsc(stocktake.getId())
                .stream().map(StocktakeApi.LineView::from).toList();
        return StocktakeApi.StocktakeView.from(stocktake, lines);
    }

    private Stocktake find(UUID id) {
        return stocktakeRepository.findById(id).orElseThrow(() -> new NotFoundException("库存盘点单", id));
    }

    private void requireDraft(Stocktake stocktake) {
        if (stocktake.getStatus() != StocktakeStatus.DRAFT) {
            throw new ConflictException("STOCKTAKE_STATUS_INVALID", "只有草稿盘点单可以修改或过账");
        }
    }

    private String key(UUID productId, String batchNo) {
        return productId + "|" + normalizeBatch(batchNo);
    }

    private String normalizeBatch(String batchNo) {
        return batchNo == null || batchNo.isBlank() ? StockBalance.NO_BATCH : batchNo.trim();
    }

    private UUID currentUserId() {
        return actors.current().map(actor -> actor.userId()).orElse(null);
    }
}
