package com.ivs.platform.security;

import com.ivs.shared.security.CurrentActor;
import com.ivs.shared.security.CurrentActorProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SecurityCurrentActorProvider implements CurrentActorProvider {
    @Override
    public Optional<CurrentActor> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUser user) {
            return Optional.of(new CurrentActor(user.userId(), user.username(), user.dataScope().name(), user.companyId(), user.storeId(), user.teamId()));
        }
        return Optional.empty();
    }
}

