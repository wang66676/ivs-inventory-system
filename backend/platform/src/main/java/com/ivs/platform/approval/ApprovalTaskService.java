package com.ivs.platform.approval;

import com.ivs.platform.security.AuthenticatedUser;
import com.ivs.platform.security.CurrentUserContext;
import com.ivs.shared.error.ConflictException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 审批待办：创建、查询、通过与驳回，并联动站内通知。 */
@Service
public class ApprovalTaskService {
    private final ApprovalTaskRepository repository;
    private final NotificationService notifications;
    private final CurrentUserContext currentUsers;

    public ApprovalTaskService(ApprovalTaskRepository repository, NotificationService notifications,
                               CurrentUserContext currentUsers) {
        this.repository = repository;
        this.notifications = notifications;
        this.currentUsers = currentUsers;
    }

    @Transactional(readOnly = true)
    public List<TaskView> list(ApprovalTaskStatus status) {
        AuthenticatedUser user = currentUsers.require();
        Map<UUID, ApprovalTask> result = new LinkedHashMap<>();
        if (status == null || status == ApprovalTaskStatus.PENDING) {
            repository.findByStatusOrderByCreatedAtDesc(ApprovalTaskStatus.PENDING).stream()
                    .filter(task -> canDecide(task, user))
                    .forEach(task -> result.put(task.getId(), task));
        }
        if (status == null || status != ApprovalTaskStatus.PENDING) {
            List<ApprovalTask> history = status == null
                    ? repository.findByDecisionByIdOrderByDecidedAtDesc(user.userId())
                    : repository.findByStatusOrderByCreatedAtDesc(status).stream()
                        .filter(task -> user.userId().equals(task.getDecisionById())).toList();
            history.forEach(task -> result.put(task.getId(), task));
        }
        return result.values().stream()
                .sorted(Comparator.comparing(ApprovalTask::getCreatedAt).reversed())
                .map(task -> TaskView.from(task, canDecide(task, user)))
                .toList();
    }

    @Transactional
    public TaskView sync(CreateCommand command) {
        AuthenticatedUser user = currentUsers.require();
        ApprovalTask pending = repository
                .findFirstByBusinessTypeAndBusinessIdAndStatusOrderByCreatedAtDesc(
                        command.businessType(), command.businessId(), ApprovalTaskStatus.PENDING)
                .orElse(null);
        if (!command.required()) {
            if (pending != null) repository.save(cancelEntity(pending));
            return null;
        }
        if (command.approvalPermission() == null || command.approvalPermission().isBlank()) {
            throw new ConflictException("APPROVAL_PERMISSION_REQUIRED", "审批任务缺少审批权限");
        }
        ApprovalTask task;
        if (pending == null) {
            String taskNo = "AP-%06d".formatted(repository.nextTaskSequence());
            task = ApprovalTask.create(taskNo, command.taskType(), command.businessType(), command.businessId(),
                    command.businessNo(), command.title(), command.summary(), command.amount(),
                    command.approvalPermission(), user.userId(), user.displayName(), command.payloadJson());
            repository.save(task);
            notifications.notifyPermission("APPROVAL", "新的审批待办", command.title() + "，请及时处理",
                    command.businessType(), command.businessId(), command.approvalPermission());
        } else {
            pending.refresh(command.businessNo(), command.title(), command.summary(), command.amount(),
                    command.approvalPermission(), command.payloadJson());
            task = repository.save(pending);
        }
        return TaskView.from(task, canDecide(task, user));
    }

    @Transactional
    public TaskView resolve(String businessType, UUID businessId, boolean approved, String reason) {
        AuthenticatedUser user = currentUsers.require();
        ApprovalTask task = repository
                .findFirstByBusinessTypeAndBusinessIdAndStatusOrderByCreatedAtDesc(
                        businessType, businessId, ApprovalTaskStatus.PENDING)
                .orElse(null);
        if (task == null) return null;
        if (!canDecide(task, user)) throw new AccessDeniedException("没有该审批任务的审批权限");
        return resolveEntity(task, user, approved, reason);
    }

    @Transactional(readOnly = true)
    public TaskView getPendingForCurrentUser(UUID id) {
        AuthenticatedUser user = currentUsers.require();
        ApprovalTask task = repository.findById(id)
                .orElseThrow(() -> new com.ivs.shared.error.NotFoundException("审批任务", id));
        if (task.getStatus() != ApprovalTaskStatus.PENDING) {
            throw new ConflictException("APPROVAL_TASK_PROCESSED", "该审批任务已处理");
        }
        if (!canDecide(task, user)) throw new AccessDeniedException("没有该审批任务的审批权限");
        return TaskView.from(task, true);
    }

    @Transactional
    public TaskView resolveById(UUID id, boolean approved, String reason) {
        AuthenticatedUser user = currentUsers.require();
        ApprovalTask task = repository.findById(id)
                .orElseThrow(() -> new com.ivs.shared.error.NotFoundException("审批任务", id));
        if (task.getStatus() != ApprovalTaskStatus.PENDING) {
            throw new ConflictException("APPROVAL_TASK_PROCESSED", "该审批任务已处理");
        }
        if (!canDecide(task, user)) throw new AccessDeniedException("没有该审批任务的审批权限");
        return resolveEntity(task, user, approved, reason);
    }

    private TaskView resolveEntity(ApprovalTask task, AuthenticatedUser user, boolean approved, String reason) {
        String note = reason == null || reason.isBlank() ? null : reason.trim();
        if (!approved && note == null) {
            throw new ConflictException("APPROVAL_REASON_REQUIRED", "驳回时必须填写原因");
        }
        task.decide(approved, user.userId(), user.displayName(), note);
        repository.save(task);
        String result = approved ? "已通过" : "已驳回";
        notifications.notifyUser("APPROVAL_RESULT", "审批结果：" + result,
                task.getTitle() + " " + result + (note == null ? "" : "；原因：" + note),
                task.getBusinessType(), task.getBusinessId(), task.getApplicantId());
        return TaskView.from(task, false);
    }

    @Transactional
    public void cancel(String businessType, UUID businessId) {
        repository.findFirstByBusinessTypeAndBusinessIdAndStatusOrderByCreatedAtDesc(
                        businessType, businessId, ApprovalTaskStatus.PENDING)
                .ifPresent(task -> repository.save(cancelEntity(task)));
    }

    private ApprovalTask cancelEntity(ApprovalTask task) {
        task.cancel();
        return task;
    }

    private boolean canDecide(ApprovalTask task, AuthenticatedUser user) {
        return task.getStatus() == ApprovalTaskStatus.PENDING
                && (user.roles().contains("ADMIN") || user.permissions().contains(task.getApprovalPermission()));
    }

    public record CreateCommand(String taskType, String businessType, UUID businessId, String businessNo,
                                String title, String summary, BigDecimal amount, String approvalPermission,
                                boolean required, String payloadJson) {
        public CreateCommand(String taskType, String businessType, UUID businessId, String businessNo,
                             String title, String summary, BigDecimal amount, String approvalPermission,
                             boolean required) {
            this(taskType, businessType, businessId, businessNo, title, summary, amount,
                    approvalPermission, required, null);
        }
    }

    public record TaskView(UUID id, String taskNo, String taskType, String businessType, UUID businessId,
                           String businessNo, String title, String summary, BigDecimal amount,
                           String approvalPermission, ApprovalTaskStatus status, String applicantName,
                           String decisionByName, String decisionReason, Instant decidedAt,
                           Instant createdAt, boolean canDecide, String payloadJson) {
        static TaskView from(ApprovalTask task, boolean canDecide) {
            return new TaskView(task.getId(), task.getTaskNo(), task.getTaskType(), task.getBusinessType(),
                    task.getBusinessId(), task.getBusinessNo(), task.getTitle(), task.getSummary(), task.getAmount(),
                    task.getApprovalPermission(), task.getStatus(), task.getApplicantName(), task.getDecisionByName(),
                    task.getDecisionReason(), task.getDecidedAt(), task.getCreatedAt(), canDecide,
                    task.getPayloadJson());
        }
    }
}
