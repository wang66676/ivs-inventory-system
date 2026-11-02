package com.ivs.masterdata.product;

import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 商品（车型 / SKU）：编码留空时自动生成 PRD-000001。 */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryService categoryService;
    private final ProductAttrDefRepository attrDefRepository;
    private final ProductCategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository,
                          ProductCategoryService categoryService,
                          ProductAttrDefRepository attrDefRepository,
                          ProductCategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryService = categoryService;
        this.attrDefRepository = attrDefRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<MasterdataApi.ProductView> list(UUID categoryId, String keyword) {
        List<Product> products;
        if (keyword != null && !keyword.isBlank()) {
            products = productRepository.search("%" + keyword.trim().toLowerCase() + "%");
        } else if (categoryId != null) {
            products = productRepository.findByCategoryIdOrderByCreatedAtDesc(categoryId);
        } else {
            products = productRepository.findAllByOrderByCreatedAtDesc();
        }
        return products.stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public MasterdataApi.ProductView get(UUID id) {
        return toView(find(id));
    }

    @Transactional
    public MasterdataApi.ProductView create(MasterdataApi.SaveProductCommand command) {
        ProductCategory category = categoryService.requireActive(command.categoryId());
        String name = resolveProductName(command.name(), command.series(), command.model(), category.getName());
        String productNo = command.productNo() == null || command.productNo().isBlank()
                ? "PRD-%06d".formatted(productRepository.nextProductSequence())
                : command.productNo().trim().toUpperCase();
        if (productRepository.existsByProductNo(productNo)) {
            throw new ConflictException("PRODUCT_NO_DUPLICATE", "商品编码已存在：" + productNo);
        }
        Product product = Product.create(productNo, name, category.getId(), command.unit(),
                command.brand(), command.series(), command.model(), command.configCode(),
                command.taxRate(), command.minPrice(), command.salePriceGuide(), command.purchasePriceRef(),
                command.specJson(), command.remark());
        return toView(productRepository.save(product));
    }

    @Transactional
    public MasterdataApi.ProductBatchImportResult batchImport(List<MasterdataApi.ProductImportRow> rows) {
        if (rows == null || rows.isEmpty()) return new MasterdataApi.ProductBatchImportResult(0, 0, List.of(), List.of());
        List<String> errors = new ArrayList<>();
        List<MasterdataApi.ProductView> created = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            MasterdataApi.ProductImportRow row = rows.get(i);
            if (row == null) {
                errors.add("第 " + (i + 1) + " 行：数据为空");
                continue;
            }
            try {
                if (row.categoryCode() == null || row.categoryCode().isBlank()) {
                    throw new IllegalArgumentException("商品不能为空");
                }
                ProductCategory category = categoryRepository.findByCode(row.categoryCode().trim().toUpperCase())
                        .orElseGet(() -> categoryRepository.findAllByOrderBySortOrderAscCodeAsc().stream()
                                .filter(item -> item.getName().equals(row.categoryCode().trim())).findFirst()
                                .orElseThrow(() -> new IllegalArgumentException("商品不存在：" + row.categoryCode())));
                if (category.getStatus() != com.ivs.masterdata.MasterStatus.ACTIVE) {
                    throw new IllegalArgumentException("商品已停用：" + row.categoryCode());
                }
                String name = resolveProductName(null, row.series(), row.model(), category.getName());
                MasterdataApi.ProductView view = create(new MasterdataApi.SaveProductCommand(
                        null, name, category.getId(), row.unit() == null || row.unit().isBlank() ? category.getUnit() : row.unit(),
                        null, row.series(), row.model(), null,
                        normalizeTaxRate(row.taxRate() == null ? category.getDefaultTaxRate() : row.taxRate()),
                        BigDecimal.ZERO, null, null, null, null));
                created.add(view);
            } catch (RuntimeException exception) {
                errors.add("第 " + (i + 1) + " 行：" + exception.getMessage());
            }
        }
        return new MasterdataApi.ProductBatchImportResult(created.size(), errors.size(), errors, created);
    }
    private BigDecimal normalizeTaxRate(BigDecimal rate) {
        if (rate == null) return null;
        return rate.compareTo(BigDecimal.ONE) > 0 ? rate.divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP) : rate;
    }

    @Transactional
    public MasterdataApi.ProductView update(UUID id, MasterdataApi.SaveProductCommand command) {
        Product product = find(id);
        String name = resolveProductName(command.name(), command.series(), command.model(), product.getName());
        product.update(name, command.unit(), command.brand(), command.series(), command.model(),
                command.configCode(), command.taxRate(), command.minPrice(), command.salePriceGuide(),
                command.purchasePriceRef(), command.specJson(), command.remark());
        return toView(productRepository.save(product));
    }

    @Transactional
    public MasterdataApi.ProductView disable(UUID id) {
        Product product = find(id);
        product.disable();
        return toView(productRepository.save(product));
    }

    @Transactional
    public MasterdataApi.ProductView enable(UUID id) {
        Product product = find(id);
        product.enable();
        return toView(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public Product find(UUID id) {
        return productRepository.findById(id).orElseThrow(() -> new NotFoundException("商品", id));
    }

    private MasterdataApi.ProductView toView(Product product) {
        ProductCategory category = categoryService.find(product.getCategoryId());
        List<ProductAttrDef> attrDefs = attrDefRepository
                .findByCategoryIdOrderBySortOrderAscCodeAsc(product.getCategoryId());
        return MasterdataApi.ProductView.from(product, category, attrDefs);
    }

    private String resolveProductName(String requestedName, String series, String model, String fallback) {
        if (requestedName != null && !requestedName.isBlank()) {
            return requestedName.trim();
        }
        String seriesText = series == null ? "" : series.trim();
        String modelText = model == null ? "" : model.trim();
        String combined = (seriesText + " " + modelText).trim();
        return combined.isEmpty() ? fallback : combined;
    }
}


