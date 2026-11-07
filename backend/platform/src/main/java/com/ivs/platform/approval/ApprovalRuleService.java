package com.ivs.platform.approval;

import com.ivs.platform.security.PermissionCatalog;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import com.ivs.shared.security.CurrentActorProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 审批规则中心：维护矩阵并为业务端口提供统一判定。 */
@Service
public class ApprovalRuleService {
    private static final Map<String, String> FIXED_PERMISSIONS = Map.of(
            "SALES_LOW_PRICE", "IVS_PRICE_APPROVE",
            "STOCK_ADJUSTMENT", "IVS_STOCK_ADJUST",
            "POLICY_PUBLISH", "IVS_POLICY_APPROVE",
            "SUBSIDY_POOL_ADJUSTMENT", "IVS_SUBSIDY_POOL_MANAGE",
            "FUND_POOL_ADJUSTMENT", "IVS_FUND_POOL_ADJUST",
            "REFUND_APPROVAL", "IVS_REFUND_MANAGE"
    );

    private final ApprovalRuleRepository repository;
    private final CurrentActorProvider actors;

    public ApprovalRuleService(ApprovalRuleRepository repository, CurrentActorProvider actors) {
        this.repository = repository;
        this.actors = actors;
    }

    @Transactional(readOnly = true)
    public List<RuleView> list() {
        return repository.findAllByOrderBySortOrderAscRuleCodeAsc().stream().map(RuleView::from).toList();
    }

    @Transactional
    public RuleView update(String ruleCode, UpdateCommand command) {
        ApprovalRule rule = find(ruleCode);
        ApprovalMetricType metricType;
        try {
            metricType = ApprovalMetricType.valueOf(command.metricType());
        } catch (RuntimeException exception) {
            throw new ConflictException("APPROVAL_METRIC_INVALID", "审批触发方式不合法");
        }
        BigDecimal threshold = command.thresholdValue() == null ? BigDecimal.ZERO : command.thresholdValue();
        if (threshold.signum() < 0) {
            throw new ConflictException("APPROVAL_THRESHOLD_INVALID", "审批阈值不能小于 0");
        }
        if (metricType == ApprovalMetricType.DISCOUNT_PERCENT
                && threshold.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ConflictException("APPROVAL_THRESHOLD_INVALID", "折让比例阈值必须在 0-100 之间");
        }
        if (metricType == ApprovalMetricType.ALWAYS || metricType == ApprovalMetricType.NONE) {
            threshold = BigDecimal.ZERO;
        }
        String permission = command.approvalPermission() == null ? "" : command.approvalPermission().trim();
        if (!PermissionCatalog.allCodes().contains(permission)) {
            throw new ConflictException("APPROVAL_PERMISSION_INVALID", "审批权限码不存在");
        }
        String fixedPermission = FIXED_PERMISSIONS.get(ruleCode);
        if (fixedPermission != null && !fixedPermission.equals(permission)) {
            throw new ConflictException("APPROVAL_PERMISSION_FIXED", "该审批规则必须使用指定权限：" + fixedPermission);
        }
        rule.update(Boolean.TRUE.equals(command.enabled()), metricType,
                threshold.setScale(4, RoundingMode.HALF_UP), permission,
                trimToNull(command.description()), currentUserId());
        return RuleView.from(repository.save(rule));
    }

    @Transactional(readOnly = true)
    public Decision evaluate(String ruleCode, BigDecimal amount, BigDecimal baseAmount) {
        ApprovalRule rule = repository.findById(ruleCode).orElse(null);
        if (rule == null || !rule.isEnabled() || rule.getMetricType() == ApprovalMetricType.NONE) {
            return new Decision(false, "规则未启用", null, BigDecimal.ZERO);
        }
        if (rule.getMetricType() == ApprovalMetricType.ALWAYS) {
            return new Decision(true, "该业务始终需要审批", rule.getApprovalPermission(), BigDecimal.ZERO);
        }
        BigDecimal actual = amount == null ? BigDecimal.ZERO : amount.max(BigDecimal.ZERO);
        if (rule.getMetricType() == ApprovalMetricType.AMOUNT) {
            boolean required = actual.signum() > 0 && actual.compareTo(rule.getThresholdValue()) >= 0;
            return new Decision(required, required ? "调整金额达到审批阈值" : "调整金额未达到审批阈值",
                    rule.getApprovalPermission(), actual);
        }
        if (baseAmount == null || baseAmount.signum() <= 0 || actual.signum() <= 0) {
            return new Decision(false, "未发生折让", rule.getApprovalPermission(), BigDecimal.ZERO);
        }
        BigDecimal percent = actual.multiply(BigDecimal.valueOf(100))
                .divide(baseAmount, 4, RoundingMode.HALF_UP);
        boolean required = percent.compareTo(rule.getThresholdValue()) >= 0;
        return new Decision(required, required ? "折让比例达到审批阈值" : "折让比例未达到审批阈值",
                rule.getApprovalPermission(), percent);
    }

    private ApprovalRule find(String ruleCode) {
        return repository.findById(ruleCode)
                .orElseThrow(() -> new NotFoundException("审批规则", UUID.nameUUIDFromBytes(ruleCode.getBytes())));
    }

    private UUID currentUserId() {
        return actors.current().map(actor -> actor.userId()).orElse(null);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record UpdateCommand(Boolean enabled, String metricType, BigDecimal thresholdValue,
                                String approvalPermission, String description) {
    }

    public record Decision(boolean required, String reason, String approvalPermission, BigDecimal measuredValue) {
    }

    public record RuleView(String ruleCode, String ruleName, String scenario, boolean enabled,
                           String metricType, String metricLabel, BigDecimal thresholdValue,
                           String approvalPermission, String description, Instant updatedAt) {
        static RuleView from(ApprovalRule rule) {
            return new RuleView(rule.getRuleCode(), rule.getRuleName(), rule.getScenario(), rule.isEnabled(),
                    rule.getMetricType().name(), rule.getMetricType().label(), rule.getThresholdValue(),
                    rule.getApprovalPermission(), rule.getDescription(), rule.getUpdatedAt());
        }
    }
}
