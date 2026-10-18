package com.ivs.shared.security;

import java.util.Optional;

public interface CurrentActorProvider {
    Optional<CurrentActor> current();
}
