package com.ivs.platform.security.api;

import com.ivs.platform.security.AuthenticatedUser;
import com.ivs.platform.security.AuthenticationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/auth")
public class AuthController {
    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginCommand command) {
        return authenticationService.login(command);
    }

    @GetMapping("/me")
    public CurrentUserView me(@AuthenticationPrincipal AuthenticatedUser user) {
        return CurrentUserView.from(user);
    }

    @PostMapping("/change-password")
    public void changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                               @Valid @RequestBody ChangePasswordCommand command) {
        authenticationService.changePassword(user, command);
    }

    @PostMapping("/logout")
    public void logout(@AuthenticationPrincipal AuthenticatedUser user) {
        authenticationService.logout(user.sessionId());
    }
}
