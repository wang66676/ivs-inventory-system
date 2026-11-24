package com.ivs.app.deletion;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeletionRequestRepository extends JpaRepository<DeletionRequest, UUID> {
    List<DeletionRequest> findAllByOrderByRequestedAtDesc();
    List<DeletionRequest> findByStatusOrderByRequestedAtDesc(DeletionRequestStatus status);
    List<DeletionRequest> findByRequesterIdOrderByRequestedAtDesc(UUID requesterId);
    List<DeletionRequest> findByRequesterIdAndStatusOrderByRequestedAtDesc(UUID requesterId, DeletionRequestStatus status);
    boolean existsByBusinessTypeAndBusinessIdAndStatus(DeletionBusinessType type, UUID businessId,
                                                       DeletionRequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DeletionRequest r where r.id = :id")
    Optional<DeletionRequest> findByIdForUpdate(@Param("id") UUID id);
}
