package com.ivs.app.deletion;

import com.ivs.platform.security.AuthenticatedUser;
import com.ivs.platform.security.CurrentUserContext;
import com.ivs.policy.policy.PolicyApplicationService;
import com.ivs.sales.order.SalesOrderApplicationService;
import com.ivs.shared.error.BusinessException;
import com.ivs.shared.error.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 删除审批：管理员直接删除；其他用户只能申请，审批通过后由 app 层执行真实删除。 */
@Service
public class DeletionRequestService {
    private final DeletionRequestRepository repository;
    private final JdbcTemplate jdbc;
    private final CurrentUserContext currentUser;
    private final SalesOrderApplicationService salesOrders;
    private final PolicyApplicationService policies;

    public DeletionRequestService(DeletionRequestRepository repository, JdbcTemplate jdbc,
                                  CurrentUserContext currentUser, SalesOrderApplicationService salesOrders,
                                  PolicyApplicationService policies) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.salesOrders = salesOrders;
        this.policies = policies;
    }

    @Transactional
    public DeletionRequestView create(CreateCommand command) {
        AuthenticatedUser user = currentUser.require();
        if (command == null || command.businessType() == null || command.businessId() == null) {
            throw new BusinessException("DELETE_REQUEST_INVALID", "缺少待删除数据的类型或编号");
        }
        Target target = resolve(command.businessType(), command.businessId());
        if (!isAdmin(user) && !user.userId().equals(target.ownerId())) {
            throw new BusinessException("DELETE_REQUEST_FORBIDDEN", "只能申请删除自己登记的数据");
        }
        if (repository.existsByBusinessTypeAndBusinessIdAndStatus(
                command.businessType(), command.businessId(), DeletionRequestStatus.PENDING)) {
            throw new BusinessException("DELETE_REQUEST_DUPLICATE", "该数据已有待审批的删除申请");
        }
        DeletionRequest request = DeletionRequest.create(command.businessType(), command.businessId(),
                target.businessNo(), command.reason(), user.userId(), displayName(user));
        return DeletionRequestView.from(repository.save(request));
    }

    @Transactional(readOnly = true)
    public List<DeletionRequestView> list(DeletionRequestStatus status) {
        AuthenticatedUser user = currentUser.require();
        List<DeletionRequest> rows;
        if (isAdmin(user)) {
            rows = status == null ? repository.findAllByOrderByRequestedAtDesc()
                    : repository.findByStatusOrderByRequestedAtDesc(status);
        } else {
            rows = status == null ? repository.findByRequesterIdOrderByRequestedAtDesc(user.userId())
                    : repository.findByRequesterIdAndStatusOrderByRequestedAtDesc(user.userId(), status);
        }
        return rows.stream().map(DeletionRequestView::from).toList();
    }

    @Transactional
    public DeletionRequestView approve(UUID id, String remark) {
        AuthenticatedUser reviewer = requireAdmin();
        DeletionRequest request = find(id);
        if (request.getStatus() != DeletionRequestStatus.PENDING) {
            throw new BusinessException("DELETE_REQUEST_STATUS_INVALID", "该删除申请已经处理，不能重复操作");
        }
        executeDelete(request);
        request.approve(reviewer.userId(), displayName(reviewer), remark);
        return DeletionRequestView.from(repository.save(request));
    }

    @Transactional
    public DeletionRequestView reject(UUID id, String remark) {
        AuthenticatedUser reviewer = requireAdmin();
        DeletionRequest request = find(id);
        try {
            request.reject(reviewer.userId(), displayName(reviewer), remark);
        } catch (IllegalStateException exception) {
            throw new BusinessException("DELETE_REQUEST_STATUS_INVALID", exception.getMessage());
        }
        return DeletionRequestView.from(repository.save(request));
    }

    private void executeDelete(DeletionRequest request) {
        try {
            if (request.getBusinessType() == DeletionBusinessType.SALES_ORDER) {
                salesOrders.deleteCancelled(request.getBusinessId());
            } else if (request.getBusinessType() == DeletionBusinessType.PRE_ENTITLEMENT) {
                policies.deletePre(request.getBusinessId());
            } else {
                throw new BusinessException("DELETE_REQUEST_TYPE_UNSUPPORTED", "不支持的删除数据类型");
            }
        } catch (IllegalStateException exception) {
            throw new BusinessException("DELETE_REQUEST_EXECUTION_FAILED", exception.getMessage());
        }
    }

    private Target resolve(DeletionBusinessType type, UUID id) {
        try {
            if (type == DeletionBusinessType.SALES_ORDER) {
                return jdbc.queryForObject("""
                        select coalesce(external_order_no, order_no) as business_no, owner_id, status
                          from ivs.sales_order where id = ?
                        """, (rs, n) -> {
                    if (!"CANCELLED".equals(rs.getString("status"))) {
                        throw new BusinessException("DELETE_REQUEST_STATUS_INVALID", "只有已取消销售订单可以申请删除");
                    }
                    return new Target(rs.getString("business_no"), rs.getObject("owner_id", UUID.class));
                }, id);
            }
            if (type == DeletionBusinessType.PRE_ENTITLEMENT) {
                return jdbc.queryForObject("""
                        select pre.pre_no as business_no, po.owner_id
                          from ivs.policy_pre_entitlement pre
                          join ivs.purchase_order po on po.id = pre.order_id
                         where pre.id = ?
                        """, (rs, n) -> new Target(rs.getString("business_no"),
                        rs.getObject("owner_id", UUID.class)), id);
            }
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new NotFoundException("待删除数据", id);
        }
        throw new BusinessException("DELETE_REQUEST_TYPE_UNSUPPORTED", "不支持的删除数据类型");
    }

    private DeletionRequest find(UUID id) {
        return repository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("删除申请", id));
    }

    private AuthenticatedUser requireAdmin() {
        AuthenticatedUser user = currentUser.require();
        if (!isAdmin(user)) throw new BusinessException("DELETE_REQUEST_FORBIDDEN", "只有管理员可以审批删除");
        return user;
    }

    private boolean isAdmin(AuthenticatedUser user) {
        return user.roles().contains("ADMIN");
    }

    private String displayName(AuthenticatedUser user) {
        return user.displayName() == null || user.displayName().isBlank() ? user.username() : user.displayName();
    }

    private record Target(String businessNo, UUID ownerId) {
    }

    public record CreateCommand(DeletionBusinessType businessType, UUID businessId, String reason) {
    }

    public record DeletionRequestView(UUID id, DeletionBusinessType businessType, String businessTypeLabel,
                                      UUID businessId, String businessNo, String moduleName, String reason,
                                      UUID requesterId, String requesterName, DeletionRequestStatus status,
                                      String reviewerName, String reviewRemark, Instant requestedAt,
                                      Instant reviewedAt) {
        static DeletionRequestView from(DeletionRequest request) {
            return new DeletionRequestView(request.getId(), request.getBusinessType(),
                    request.getBusinessType().label(), request.getBusinessId(), request.getBusinessNo(),
                    request.getModuleName(), request.getReason(), request.getRequesterId(),
                    request.getRequesterName(), request.getStatus(), request.getReviewerName(),
                    request.getReviewRemark(), request.getRequestedAt(), request.getReviewedAt());
        }
    }
}
