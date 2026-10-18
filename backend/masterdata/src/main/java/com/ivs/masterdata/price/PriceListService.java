package com.ivs.masterdata.price;

import com.ivs.masterdata.CustomerType;
import com.ivs.masterdata.MasterStatus;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.product.Product;
import com.ivs.masterdata.product.ProductService;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 价目表：成交价与最低限价的来源，供 M3 销售取价与低价审批使用。 */
@Service
public class PriceListService {

    private final PriceListRepository priceListRepository;
    private final PriceListLineRepository lineRepository;
    private final ProductService productService;

    public PriceListService(PriceListRepository priceListRepository,
                            PriceListLineRepository lineRepository,
                            ProductService productService) {
        this.priceListRepository = priceListRepository;
        this.lineRepository = lineRepository;
        this.productService = productService;
    }

    @Transactional(readOnly = true)
    public List<MasterdataApi.PriceListView> list() {
        return priceListRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public MasterdataApi.PriceListView get(UUID id) {
        return toView(find(id));
    }

    @Transactional
    public MasterdataApi.PriceListView create(MasterdataApi.SavePriceListCommand command) {
        String code = command.code() == null || command.code().isBlank()
                ? "PL-%06d".formatted(priceListRepository.nextPriceListSequence())
                : command.code().trim().toUpperCase();
        if (priceListRepository.existsByCode(code)) {
            throw new ConflictException("PRICE_LIST_CODE_DUPLICATE", "价目表编码已存在：" + code);
        }
        try {
            PriceList priceList = PriceList.create(code, command.name(), command.customerType(),
                    command.effectiveFrom(), command.effectiveTo(), command.remark());
            return toView(priceListRepository.save(priceList));
        } catch (IllegalArgumentException exception) {
            throw new ConflictException("PRICE_LIST_INVALID", exception.getMessage());
        }
    }

    @Transactional
    public MasterdataApi.PriceListView update(UUID id, MasterdataApi.SavePriceListCommand command) {
        PriceList priceList = find(id);
        try {
            priceList.update(command.name(), command.customerType(), command.effectiveFrom(),
                    command.effectiveTo(), command.remark());
        } catch (IllegalArgumentException exception) {
            throw new ConflictException("PRICE_LIST_INVALID", exception.getMessage());
        }
        return toView(priceListRepository.save(priceList));
    }

    @Transactional
    public MasterdataApi.PriceListView disable(UUID id) {
        PriceList priceList = find(id);
        priceList.disable();
        return toView(priceListRepository.save(priceList));
    }

    @Transactional
    public MasterdataApi.PriceListView enable(UUID id) {
        PriceList priceList = find(id);
        priceList.enable();
        return toView(priceListRepository.save(priceList));
    }

    /** 新增或更新一行（同一价目表内同一商品只有一行）。 */
    @Transactional
    public MasterdataApi.PriceListView saveLine(UUID priceListId, MasterdataApi.SavePriceListLineCommand command) {
        PriceList priceList = find(priceListId);
        Product product = productService.find(command.productId());
        PriceListLine line = lineRepository.findByPriceListIdAndProductId(priceListId, product.getId())
                .orElseGet(() -> PriceListLine.create(priceListId, product.getId(), command.price(),
                        command.minPrice(), command.remark()));
        try {
            line.update(command.price(), command.minPrice(), command.remark());
        } catch (IllegalArgumentException exception) {
            throw new ConflictException("PRICE_LINE_INVALID", exception.getMessage());
        }
        lineRepository.save(line);
        return toView(priceList);
    }

    @Transactional
    public MasterdataApi.PriceListView removeLine(UUID priceListId, UUID lineId) {
        PriceList priceList = find(priceListId);
        PriceListLine line = lineRepository.findById(lineId)
                .orElseThrow(() -> new NotFoundException("价目表明细", lineId));
        if (!line.getPriceListId().equals(priceListId)) {
            throw new ConflictException("PRICE_LINE_MISMATCH", "该明细不属于当前价目表");
        }
        lineRepository.delete(line);
        return toView(priceList);
    }

    /**
     * 取价：优先命中「适用客户类型 + 生效期内」的价目表，未命中回落到商品指导价。
     */
    @Transactional(readOnly = true)
    public MasterdataApi.ProductPriceView lookupPrice(UUID productId, CustomerType customerType, LocalDate date) {
        Product product = productService.find(productId);
        LocalDate target = date == null ? LocalDate.now() : date;
        for (PriceList priceList : priceListRepository.findAllByOrderByCreatedAtDesc()) {
            if (priceList.getStatus() != MasterStatus.ACTIVE || !priceList.covers(target)) {
                continue;
            }
            if (priceList.getCustomerType() != null && priceList.getCustomerType() != customerType) {
                continue;
            }
            var line = lineRepository.findByPriceListIdAndProductId(priceList.getId(), productId);
            if (line.isPresent()) {
                PriceListLine hit = line.get();
                return new MasterdataApi.ProductPriceView(productId, product.getProductNo(), hit.getPrice(),
                        hit.getMinPrice() == null ? product.getMinPrice() : hit.getMinPrice(),
                        "PRICE_LIST:" + priceList.getCode());
            }
        }
        return new MasterdataApi.ProductPriceView(productId, product.getProductNo(), product.getSalePriceGuide(),
                product.getMinPrice(), "PRODUCT_GUIDE");
    }

    @Transactional(readOnly = true)
    public PriceList find(UUID id) {
        return priceListRepository.findById(id).orElseThrow(() -> new NotFoundException("价目表", id));
    }

    private MasterdataApi.PriceListView toView(PriceList priceList) {
        List<MasterdataApi.PriceListLineView> lines = lineRepository
                .findByPriceListIdOrderByCreatedAtAsc(priceList.getId()).stream()
                .map(line -> {
                    Product product = productService.find(line.getProductId());
                    return new MasterdataApi.PriceListLineView(line.getId(), line.getProductId(),
                            product.getProductNo(), product.getName(), line.getPrice(), line.getMinPrice(),
                            line.getRemark());
                })
                .toList();
        return MasterdataApi.PriceListView.from(priceList, lines);
    }
}