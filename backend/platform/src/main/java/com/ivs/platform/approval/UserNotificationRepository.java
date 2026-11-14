package com.ivs.platform.approval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface UserNotificationRepository extends JpaRepository<UserNotification, UUID> {
    List<UserNotification> findTop200ByOrderByCreatedAtDesc();

    boolean existsByCategoryAndCreatedAtAfter(String category, Instant createdAt);
}
