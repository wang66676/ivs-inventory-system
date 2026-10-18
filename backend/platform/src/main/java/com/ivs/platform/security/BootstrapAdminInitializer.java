package com.ivs.platform.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** 首次启动时创建引导管理员与各角色的默认权限。 */
@Component
@ConditionalOnProperty(prefix = "ivs.security.bootstrap-admin", name = "enabled", havingValue = "true")
public class BootstrapAdminInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private static final Map<String, List<String>> ROLE_PERMISSIONS = Map.of(
            "ADMIN", List.of(
                    PermissionCodes.IVS_READ, PermissionCodes.IVS_WRITE,
                    PermissionCodes.PRODUCT_MANAGE, PermissionCodes.STOCK_ADJUST, PermissionCodes.STOCK_TRANSFER,
                    PermissionCodes.PRICE_APPROVE,
                    PermissionCodes.RETAIL_WRITE, PermissionCodes.RETAIL_VERIFY,
                    PermissionCodes.POLICY_WRITE, PermissionCodes.POLICY_APPROVE, PermissionCodes.SUBSIDY_POOL_MANAGE,
                    PermissionCodes.POLICY_SELECT, PermissionCodes.PRE_ENTITLEMENT_MANAGE, PermissionCodes.ENTITLEMENT_APPLY,
                    PermissionCodes.CLAIM_SUBMIT, PermissionCodes.CLAIM_CONFIRM,
                    PermissionCodes.FUND_POOL_VIEW, PermissionCodes.FUND_POOL_ADJUST, PermissionCodes.FUND_OFFSET,
                    PermissionCodes.INVOICE_WRITE, PermissionCodes.INVOICE_REDFLUSH,
                    PermissionCodes.FINANCE_READ, PermissionCodes.LEASE_MANAGE,
                    PermissionCodes.PROCESS_READ, PermissionCodes.USER_MANAGE
            ),
            "SALES", List.of(
                    PermissionCodes.IVS_READ, PermissionCodes.IVS_WRITE,
                    PermissionCodes.POLICY_SELECT, PermissionCodes.PRICE_APPROVE
            ),
            "WAREHOUSE", List.of(
                    PermissionCodes.IVS_READ, PermissionCodes.IVS_WRITE,
                    PermissionCodes.STOCK_ADJUST, PermissionCodes.STOCK_TRANSFER, PermissionCodes.FUND_OFFSET
            ),
            "FINANCE", List.of(
                    PermissionCodes.IVS_READ, PermissionCodes.IVS_WRITE,
                    PermissionCodes.INVOICE_WRITE, PermissionCodes.INVOICE_REDFLUSH,
                    PermissionCodes.CLAIM_SUBMIT, PermissionCodes.CLAIM_CONFIRM,
                    PermissionCodes.FUND_POOL_VIEW, PermissionCodes.FUND_POOL_ADJUST,
                    PermissionCodes.PRICE_APPROVE, PermissionCodes.FINANCE_READ, PermissionCodes.PROCESS_READ
            ),
            "POLICY_ADMIN", List.of(
                    PermissionCodes.IVS_READ, PermissionCodes.IVS_WRITE,
                    PermissionCodes.POLICY_WRITE, PermissionCodes.POLICY_APPROVE, PermissionCodes.SUBSIDY_POOL_MANAGE,
                    PermissionCodes.PRE_ENTITLEMENT_MANAGE, PermissionCodes.ENTITLEMENT_APPLY,
                    PermissionCodes.CLAIM_SUBMIT, PermissionCodes.CLAIM_CONFIRM,
                    PermissionCodes.FUND_POOL_VIEW, PermissionCodes.RETAIL_VERIFY,
                    PermissionCodes.FINANCE_READ, PermissionCodes.PROCESS_READ
            )
    );

    private final PlatformUserRepository userRepository;
    private final UserRoleAssignmentRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public BootstrapAdminInitializer(PlatformUserRepository userRepository,
                                     UserRoleAssignmentRepository userRoleRepository,
                                     RolePermissionRepository rolePermissionRepository,
                                     PasswordEncoder passwordEncoder,
                                     @Value("${ivs.security.bootstrap-admin.username:admin}") String username,
                                     @Value("${ivs.security.bootstrap-admin.password:admin123}") String password) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initializeRolePermissions();
        PlatformUser admin = userRepository.findByUsername(username).orElseGet(() -> {
            log.warn("创建引导管理员账号 {}，请在生产环境立即修改默认密码", username);
            return userRepository.save(PlatformUser.create(
                    username, "系统管理员", passwordEncoder.encode(password), DataScope.ALL, null, null, null
            ));
        });
        if (userRoleRepository.findByUserId(admin.getId()).isEmpty()) {
            userRoleRepository.save(UserRoleAssignment.assign(admin.getId(), "ADMIN"));
        }
    }

    private void initializeRolePermissions() {
        ROLE_PERMISSIONS.forEach((role, permissions) -> permissions.forEach(permission -> {
            if (!rolePermissionRepository.existsByRoleCodeAndPermissionCode(role, permission)) {
                rolePermissionRepository.save(RolePermission.grant(role, permission));
            }
        }));
    }
}