package com.ivs.masterdata.product;

import com.ivs.masterdata.MasterStatus;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 产品大项与其属性模板。
 *
 * <p>主数据是公司级共享数据，不按数据范围过滤；新增产品大项只走本服务，属于配置动作。</p>
 */
@Service
public class ProductCategoryService {

    private final ProductCategoryRepository categoryRepository;
    private final ProductAttrDefRepository attrDefRepository;
    private final ProductRepository productRepository;

    public ProductCategoryService(ProductCategoryRepository categoryRepository,
                                  ProductAttrDefRepository attrDefRepository,
                                  ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.attrDefRepository = attrDefRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<MasterdataApi.CategoryView> list() {
        return categoryRepository.findAllByOrderBySortOrderAscCodeAsc().stream()
                .map(category -> MasterdataApi.CategoryView.from(
                        category, attrDefs(category.getId()), productRepository.countByCategoryId(category.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public MasterdataApi.CategoryView get(UUID id) {
        ProductCategory category = find(id);
        return MasterdataApi.CategoryView.from(category, attrDefs(id), productRepository.countByCategoryId(id));
    }

    @Transactional
    public MasterdataApi.CategoryView create(MasterdataApi.SaveCategoryCommand command) {
        String code = command.code() == null ? "" : command.code().trim().toUpperCase();
        if (code.isEmpty()) {
            throw new ConflictException("CATEGORY_CODE_REQUIRED", "产品大项编码不能为空");
        }
        if (categoryRepository.existsByCode(code)) {
            throw new ConflictException("CATEGORY_CODE_DUPLICATE", "产品大项编码已存在：" + code);
        }
        ProductCategory category = ProductCategory.create(command.code(), command.name(), command.trackingMode(),
                command.unit(), command.defaultTaxRate(), command.statusTemplate(), command.sortOrder(),
                command.remark());
        return MasterdataApi.CategoryView.from(categoryRepository.save(category), List.of(), 0L);
    }

    @Transactional
    public MasterdataApi.CategoryView update(UUID id, MasterdataApi.SaveCategoryCommand command) {
        ProductCategory category = find(id);
        boolean trackingChanged = command.trackingMode() != null && command.trackingMode() != category.getTrackingMode();
        if (trackingChanged && productRepository.countByCategoryId(id) > 0) {
            throw new ConflictException("CATEGORY_TRACKING_LOCKED",
                    "该产品大项下已有商品，不能修改库存追踪方式（数量/序列号）");
        }
        try {
            category.update(command.name(), command.trackingMode(), command.unit(), command.defaultTaxRate(),
                    command.statusTemplate(), command.sortOrder(), command.remark());
        } catch (IllegalArgumentException exception) {
            throw new ConflictException("CATEGORY_INVALID", exception.getMessage());
        }
        return MasterdataApi.CategoryView.from(categoryRepository.save(category), attrDefs(id),
                productRepository.countByCategoryId(id));
    }

    @Transactional
    public void delete(UUID id) {
        ProductCategory category = find(id);
        long productCount = productRepository.countByCategoryId(id);
        if (productCount > 0) {
            throw new ConflictException("CATEGORY_IN_USE",
                    "产品大项下仍有 " + productCount + " 个商品，不能删除；请先迁移或删除商品");
        }
        attrDefRepository.deleteByCategoryId(id);
        categoryRepository.delete(category);
    }
    @Transactional
    public MasterdataApi.CategoryView disable(UUID id) {
        ProductCategory category = find(id);
        category.disable();
        return MasterdataApi.CategoryView.from(categoryRepository.save(category), attrDefs(id),
                productRepository.countByCategoryId(id));
    }

    @Transactional
    public MasterdataApi.CategoryView enable(UUID id) {
        ProductCategory category = find(id);
        category.enable();
        return MasterdataApi.CategoryView.from(categoryRepository.save(category), attrDefs(id),
                productRepository.countByCategoryId(id));
    }

    @Transactional
    public MasterdataApi.AttrDefView createAttrDef(UUID categoryId, MasterdataApi.SaveAttrDefCommand command) {
        ProductCategory category = find(categoryId);
        String code = command.code() == null ? "" : command.code().trim().toUpperCase();
        if (attrDefRepository.existsByCategoryIdAndCode(category.getId(), code)) {
            throw new ConflictException("ATTR_CODE_DUPLICATE", "属性编码已存在：" + code);
        }
        ProductAttrDef def = ProductAttrDef.create(category.getId(), code, command.name(), command.dataType(),
                command.required(), command.optionsJson(), command.sortOrder());
        return MasterdataApi.AttrDefView.from(attrDefRepository.save(def));
    }

    @Transactional
    public MasterdataApi.AttrDefView updateAttrDef(UUID attrDefId, MasterdataApi.SaveAttrDefCommand command) {
        ProductAttrDef def = attrDefRepository.findById(attrDefId)
                .orElseThrow(() -> new NotFoundException("产品属性定义", attrDefId));
        def.update(command.name(), command.dataType(), command.required(), command.optionsJson(), command.sortOrder());
        return MasterdataApi.AttrDefView.from(attrDefRepository.save(def));
    }

    @Transactional
    public void deleteAttrDef(UUID attrDefId) {
        ProductAttrDef def = attrDefRepository.findById(attrDefId)
                .orElseThrow(() -> new NotFoundException("产品属性定义", attrDefId));
        attrDefRepository.delete(def);
    }

    @Transactional(readOnly = true)
    public List<ProductAttrDef> attrDefs(UUID categoryId) {
        return attrDefRepository.findByCategoryIdOrderBySortOrderAscCodeAsc(categoryId);
    }

    @Transactional(readOnly = true)
    public ProductCategory find(UUID id) {
        return categoryRepository.findById(id).orElseThrow(() -> new NotFoundException("产品大项", id));
    }

    @Transactional(readOnly = true)
    public ProductCategory requireActive(UUID id) {
        ProductCategory category = find(id);
        if (category.getStatus() == MasterStatus.DISABLED) {
            throw new ConflictException("CATEGORY_DISABLED", "产品大项已停用：" + category.getName());
        }
        return category;
    }
}
