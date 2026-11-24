package com.ivs.app.deletion;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ivs/delete-requests")
public class DeletionRequestController {
    private final DeletionRequestService service;

    public DeletionRequestController(DeletionRequestService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<DeletionRequestService.DeletionRequestView> list(
            @RequestParam(required = false) DeletionRequestStatus status) {
        return service.list(status);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('IVS_WRITE')")
    public DeletionRequestService.DeletionRequestView create(
            @RequestBody DeletionRequestService.CreateCommand command) {
        return service.create(command);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public DeletionRequestService.DeletionRequestView approve(@PathVariable("id") UUID id,
                                                               @RequestBody(required = false) Map<String, String> body) {
        return service.approve(id, body == null ? null : body.get("remark"));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public DeletionRequestService.DeletionRequestView reject(@PathVariable("id") UUID id,
                                                              @RequestBody Map<String, String> body) {
        return service.reject(id, body == null ? null : body.get("remark"));
    }
}
