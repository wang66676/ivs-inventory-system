package com.ivs.platform.security;

import com.ivs.shared.security.CurrentActor;
import com.ivs.shared.security.CurrentActorProvider;
import com.ivs.shared.security.DataScopeFilter;
import com.ivs.shared.security.DataScopeResolver;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DefaultDataScopeResolver implements DataScopeResolver {
    private final CurrentActorProvider currentActorProvider;
    private final PlatformUserRepository userRepository;

    public DefaultDataScopeResolver(CurrentActorProvider currentActorProvider,
                                    PlatformUserRepository userRepository) {
        this.currentActorProvider = currentActorProvider;
        this.userRepository = userRepository;
    }

    @Override
    public DataScopeFilter resolve() {
        CurrentActor actor = currentActorProvider.current().orElse(null);
        if (actor == null || "ALL".equals(actor.dataScope())) return DataScopeFilter.all();
        if ("SELF".equals(actor.dataScope())) return DataScopeFilter.owners(Set.of(actor.userId()));
        if ("TEAM".equals(actor.dataScope()) && actor.teamId() != null) {
            return DataScopeFilter.owners(ownerIds(userRepository.findByTeamId(actor.teamId())));
        }
        if ("STORE".equals(actor.dataScope()) && actor.storeId() != null) {
            return DataScopeFilter.owners(ownerIds(userRepository.findByStoreId(actor.storeId())));
        }
        if ("COMPANY".equals(actor.dataScope()) && actor.companyId() != null) {
            return DataScopeFilter.owners(ownerIds(userRepository.findByCompanyId(actor.companyId())));
        }
        return DataScopeFilter.owners(Set.of(actor.userId()));
    }

    private Set<java.util.UUID> ownerIds(java.util.List<PlatformUser> users) {
        return users.stream().filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .map(PlatformUser::getId).collect(Collectors.toUnmodifiableSet());
    }
}
