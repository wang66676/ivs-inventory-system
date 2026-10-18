package com.ivs.masterdata.api;

import com.ivs.masterdata.CustomerType;
import com.ivs.masterdata.price.PriceListService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ivs/price-lists")
public class PriceListController {

    private final PriceListService service;

    public PriceListController(PriceListService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('IVS_READ')")
    public List<MasterdataApi.PriceListView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('IVS_READ')")
    public MasterdataApi.PriceListView get(@PathVariable("id") UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('IVS_PRODUCT_MANAGE')")
    public ResponseEntity<MasterdataApi.PriceListView> create(@Valid @RequestBody MasterdataApi.SavePriceListCommand command) {
        MasterdataApi.PriceListView view = service.create(command);
        return ResponseEntity.created(URI.create("/api/ivs/price-lists/" + view.id())).body(view);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('IVS_PRODUCT_MANAGE')")
    public MasterdataApi.PriceListView update(@PathVariable("id") UUID id,
                                              @Valid @RequestBody MasterdataApi.SavePriceListCommand command) {
        return service.update(id, command);
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('IVS_PRODUCT_MANAGE')")
    public MasterdataApi.PriceListView disable(@PathVariable("id") UUID id) {
        return service.disable(id);
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("hasAuthority('IVS_PRODUCT_MANAGE')")
    public MasterdataApi.PriceListView enable(@PathVariable("id") UUID id) {
        return service.enable(id);
    }

    @PostMapping("/{id}/lines")
    @PreAuthorize("hasAuthority('IVS_PRODUCT_MANAGE')")
    public MasterdataApi.PriceListView saveLine(@PathVariable("id") UUID id,
                                                @Valid @RequestBody MasterdataApi.SavePriceListLineCommand command) {
        return service.saveLine(id, command);
    }

    @DeleteMapping("/{id}/lines/{lineId}")
    @PreAuthorize("hasAuthority('IVS_PRODUCT_MANAGE')")
    public MasterdataApi.PriceListView removeLine(@PathVariable("id") UUID id, @PathVariable("lineId") UUID lineId) {
        return service.removeLine(id, lineId);
    }

    /** 取价：命中价目表返回价目表价格，否则回落到商品指导价。 */
    @GetMapping("/lookup")
    @PreAuthorize("hasAuthority('IVS_READ')")
    public MasterdataApi.ProductPriceView lookup(@RequestParam UUID productId,
                                                 @RequestParam(required = false) CustomerType customerType,
                                                 @RequestParam(required = false)
                                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.lookupPrice(productId, customerType, date);
    }
}