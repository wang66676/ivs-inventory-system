package com.ivs.platform.security.api;

import com.ivs.platform.security.UserManagementService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

@RestController
@RequestMapping("/api/platform/users")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class UserManagementController {
    private final UserManagementService userManagementService;

    public UserManagementController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @PostMapping
    public ResponseEntity<PlatformUserView> create(@Valid @RequestBody CreatePlatformUserCommand command) {
        PlatformUserView user = userManagementService.create(command);
        return ResponseEntity.created(URI.create("/api/platform/users/" + user.id())).body(user);
    }

    @GetMapping
    public List<PlatformUserView> list() {
        return userManagementService.list();
    }

    @GetMapping("/{userId}")
    public PlatformUserView get(@PathVariable("userId") UUID userId) {
        return userManagementService.get(userId);
    }

    @PutMapping("/{userId}/roles")
    public PlatformUserView assignRoles(@PathVariable("userId") UUID userId,
                                        @Valid @RequestBody AssignRolesCommand command) {
        return userManagementService.assignRoles(userId, command);
    }

    @PostMapping("/{userId}/reset-password")
    public PlatformUserView resetPassword(@PathVariable("userId") UUID userId,
                                          @Valid @RequestBody ResetPasswordCommand command) {
        return userManagementService.resetPassword(userId, command);
    }

    @PostMapping("/{userId}/enable")
    public PlatformUserView enable(@PathVariable("userId") UUID userId) {
        return userManagementService.enable(userId);
    }

    @PostMapping("/{userId}/disable")
    public PlatformUserView disable(@PathVariable("userId") UUID userId) {
        return userManagementService.disable(userId);
    }
}
