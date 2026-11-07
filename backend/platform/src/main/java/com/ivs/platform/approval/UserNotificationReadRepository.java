package com.ivs.platform.approval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserNotificationReadRepository extends JpaRepository<UserNotificationRead, UUID> {
    List<UserNotificationRead> findByUserId(UUID userId);

    Optional<UserNotificationRead> findByNotificationIdAndUserId(UUID notificationId, UUID userId);
}
