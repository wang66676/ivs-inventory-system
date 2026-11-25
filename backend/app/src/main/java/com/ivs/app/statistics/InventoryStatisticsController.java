package com.ivs.app.statistics;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/ivs/statistics")
public class InventoryStatisticsController {
    private final InventoryStatisticsService service;

    public InventoryStatisticsController(InventoryStatisticsService service) {
        this.service = service;
    }

    @GetMapping("/inventory")
    @PreAuthorize("hasAuthority('IVS_READ')")
    public InventoryStatisticsService.StatisticsView inventory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "ALL") String businessScope,
            @RequestParam(required = false) String warehouseType) {
        return service.query(from, to, businessScope, warehouseType);
    }
}
