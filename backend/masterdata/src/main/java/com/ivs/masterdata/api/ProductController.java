package com.ivs.masterdata.api;

import com.ivs.masterdata.product.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** 商品（车型 / SKU）。 */
@RestController
@RequestMapping("/api/ivs/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('IVS_READ')")
    public List<MasterdataApi.ProductView> list(@RequestParam(required = false) UUID categoryId,
                                                @RequestParam(required = false) String keyword) {
        return service.list(categoryId, keyword);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('IVS_READ')")
    public MasterdataApi.ProductView get(@PathVariable("id") UUID id) {
        return service.get(id);
    }

    @PostMapping("/batch-import")
    @PreAuthorize("hasAuthority('IVS_PRODUCT_MANAGE')")
    public MasterdataApi.ProductBatchImportResult batchImport(
            @RequestBody List<MasterdataApi.ProductImportRow> rows) {
        return service.batchImport(rows);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('IVS_READ')")
    public ResponseEntity<byte[]> export() {
        StringBuilder csv = new StringBuilder("商品,车系,产品名称,型号,单位,税率\n");
        for (MasterdataApi.ProductView row : service.list(null, null)) {
            csv.append(csvCell(row.categoryName())).append(',')
                    .append(csvCell(row.series())).append(',')
                    .append(csvCell(row.name())).append(',')
                    .append(csvCell(row.model())).append(',')
                    .append(csvCell(row.unit())).append(',')
                    .append(row.taxRate() == null ? "" : row.taxRate().stripTrailingZeros().toPlainString())
                    .append('\n');
        }
        byte[] bytes = ("\ufeff" + csv).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=products.csv").contentType(MediaType.parseMediaType("text/csv;charset=utf-8"))
                .body(bytes);
    }

    private String csvCell(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\"";
    }
    @PostMapping
    @PreAuthorize("hasAuthority('IVS_PRODUCT_MANAGE')")
    public ResponseEntity<MasterdataApi.ProductView> create(@Valid @RequestBody MasterdataApi.SaveProductCommand command) {
        MasterdataApi.ProductView view = service.create(command);
        return ResponseEntity.created(URI.create("/api/ivs/products/" + view.id())).body(view);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('IVS_PRODUCT_MANAGE')")
    public MasterdataApi.ProductView update(@PathVariable("id") UUID id,
                                            @Valid @RequestBody MasterdataApi.SaveProductCommand command) {
        return service.update(id, command);
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('IVS_PRODUCT_MANAGE')")
    public MasterdataApi.ProductView disable(@PathVariable("id") UUID id) {
        return service.disable(id);
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("hasAuthority('IVS_PRODUCT_MANAGE')")
    public MasterdataApi.ProductView enable(@PathVariable("id") UUID id) {
        return service.enable(id);
    }
}
