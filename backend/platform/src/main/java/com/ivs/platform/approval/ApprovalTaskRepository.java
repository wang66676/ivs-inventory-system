package com.ivs.platform.approval;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalTaskRepository extends JpaRepository<ApprovalTask, UUID> {
    Optional<ApprovalTask> findFirstByBusinessTypeAndBusinessIdAndStatusOrderByCreatedAtDesc(
            String businessType, UUID businessId, ApprovalTaskStatus status);

    List<ApprovalTask> findByStatusOrderByCreatedAtDesc(ApprovalTaskStatus status);

    List<ApprovalTask> findByDecisionByIdOrderByDecidedAtDesc(UUID decisionById);

    @Query(value = "select nextval('ivs.approval_task_no_seq')", nativeQuery = true)
    long nextTaskSequence();
}
