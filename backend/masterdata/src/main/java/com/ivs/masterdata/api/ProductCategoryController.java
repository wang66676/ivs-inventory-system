package com.ivs.masterdata.api;

import com.ivs.masterdata.product.ProductCategoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** 产品大项与属性模板：新增产品大项属于配置动作，不需要改代码。 */
@RestController
@RequestMapping("/api/ivs/product-categories")
public class ProductCategoryController {

    private final ProductCategoryService service;

    public ProductCategoryController(ProductCategoryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('IVS_READ')")
    public List<MasterdataApi.CategoryView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('IVS_READ')")
    public MasterdataApi.CategoryView get(@PathVariable("id") UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('IVS_PRODUCT_MANAGE')")
    public ResponseEntity<MasterdataApi.CategoryView> create(
            @Valid @RequestBody MasterdataApi.SaveCategoryCommand command) {
        MasterdataApi.CategoryView view = service.create(command);
        return ResponseEntity.created(URI.create("/api/ivs/product-categories/" + view.id())).body(view);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('IVS_PRODUCT_MANAGE')")
    public MasterdataApi.CategoryView update(@PathVariable("id") UUID id,
                                             @Valid @RequestBody MasterdataApi.SaveCategoryCommand command) {
        return service.update(id, command);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('IVS_PRODUCT_MANAGE')")
    public void delete(@PathVariable("id") UUID id) {
        service.delete(id);
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('IVS_PRODUCT_MANAGE')")
    public MasterdataApi.CategoryView disable(@PathVariable("id") UUID id) {
        return service.disable(id);
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("hasAuthority('IVS_PRODUCT_MANAGE')")
    public MasterdataApi.CategoryView enable(@PathVariable("id") UUID id) {
        return service.enable(id);
    }

    @PostMapping("/{id}/attr-defs")
    @PreAuthorize("hasAuthority('IVS_PRODUCT_MANAGE')")
    public ResponseEntity<MasterdataApi.AttrDefView> createAttrDef(
            @PathVariable("id") UUID id, @Valid @RequestBody MasterdataApi.SaveAttrDefCommand command) {
        MasterdataApi.AttrDefView view = service.createAttrDef(id, command);
        return ResponseEntity.created(URI.create("/api/ivs/product-categories/" + id + "/attr-defs/" + view.id()))
                .body(view);
    }

    @PutMapping("/attr-defs/{attrDefId}")
    @PreAuthorize("hasAuthority('IVS_PRODUCT_MANAGE')")
    public MasterdataApi.AttrDefView updateAttrDef(@PathVariable("attrDefId") UUID attrDefId,
                                                   @Valid @RequestBody MasterdataApi.SaveAttrDefCommand command) {
        return service.updateAttrDef(attrDefId, command);
    }

    @DeleteMapping("/attr-defs/{attrDefId}")
    @PreAuthorize("hasAuthority('IVS_PRODUCT_MANAGE')")
    public void deleteAttrDef(@PathVariable("attrDefId") UUID attrDefId) {
        service.deleteAttrDef(attrDefId);
    }
}
