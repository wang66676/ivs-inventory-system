package com.ivs.platform.security;

import com.ivs.platform.security.api.AssignRolesCommand;
import com.ivs.platform.organization.OrganizationService;
import com.ivs.platform.security.api.CreatePlatformUserCommand;
import com.ivs.platform.security.api.PlatformUserView;
import com.ivs.platform.security.api.ResetPasswordCommand;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserManagementService {
    /** 可分配角色以权限目录为唯一来源，新增角色只需改 PermissionCatalog。 */
    private static final Set<String> ALLOWED_ROLES = Set.copyOf(PermissionCatalog.ROLE_CODES);

    private final PlatformUserRepository userRepository;
    private final UserRoleAssignmentRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrganizationService organizationService;
    private final UserSessionRepository sessionRepository;

    public UserManagementService(PlatformUserRepository userRepository,
                                 UserRoleAssignmentRepository userRoleRepository,
                                 PasswordEncoder passwordEncoder,
                                 OrganizationService organizationService,
                                 UserSessionRepository sessionRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.organizationService = organizationService;
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public PlatformUserView create(CreatePlatformUserCommand command) {
        String username = command.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("USERNAME_DUPLICATE", "用户名已存在");
        }
        validateRoles(command.roles());
        PlatformUser user = userRepository.save(PlatformUser.create(
                username,
                command.displayName().trim(),
                passwordEncoder.encode(command.password()),
                command.dataScope() == null ? DataScope.SELF : command.dataScope(),
                command.companyId(), command.storeId(), command.teamId()
        ));
        replaceRoles(user.getId(), command.roles());
        return PlatformUserView.from(user, command.roles());
    }

    @Transactional(readOnly = true)
    public List<PlatformUserView> list() {
        return userRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(user -> PlatformUserView.from(user, roles(user.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlatformUserView get(UUID userId) {
        PlatformUser user = find(userId);
        return PlatformUserView.from(user, roles(userId));
    }

    @Transactional
    public PlatformUserView assignRoles(UUID userId, AssignRolesCommand command) {
        validateRoles(command.roles());
        PlatformUser user = find(userId);
        replaceRoles(userId, command.roles());
        return PlatformUserView.from(user, command.roles());
    }

    @Transactional
    public PlatformUserView disable(UUID userId) {
        PlatformUser user = find(userId);
        if ("admin".equals(user.getUsername())) {
            throw new ConflictException("ADMIN_DISABLE_FORBIDDEN", "不能停用引导管理员账号");
        }
        user.disable();
        userRepository.saveAndFlush(user);
        return PlatformUserView.from(user, roles(userId));
    }

    @Transactional
    public PlatformUserView enable(UUID userId) {
        PlatformUser user = find(userId);
        user.enable();
        userRepository.saveAndFlush(user);
        return PlatformUserView.from(user, roles(userId));
    }

    /** 管理员为用户重置密码：重置后该用户全部会话失效，必须用新密码重新登录。 */
    @Transactional
    public PlatformUserView resetPassword(UUID userId, ResetPasswordCommand command) {
        PlatformUser user = find(userId);
        if (command.password().equalsIgnoreCase(user.getUsername())) {
            throw new ConflictException("PASSWORD_TOO_WEAK", "密码不能与用户名相同");
        }
        user.changePassword(passwordEncoder.encode(command.password()));
        userRepository.saveAndFlush(user);
        Instant now = Instant.now();
        sessionRepository.findByUserIdAndRevokedAtIsNull(userId).forEach(session -> session.revoke(now));
        sessionRepository.flush();
        return PlatformUserView.from(user, roles(userId));
    }

    private PlatformUser find(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("用户", userId));
    }

    private Set<String> roles(UUID userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(UserRoleAssignment::getRoleCode)
                .collect(Collectors.toUnmodifiableSet());
    }

    private void replaceRoles(UUID userId, Set<String> roles) {
        userRoleRepository.deleteByUserId(userId);
        userRoleRepository.flush();
        roles.forEach(role -> userRoleRepository.save(UserRoleAssignment.assign(userId, role)));
    }

    private void validateRoles(Set<String> roles) {
        if (roles == null || roles.isEmpty() || !ALLOWED_ROLES.containsAll(roles)) {
            throw new ConflictException("INVALID_ROLE", "包含不支持的角色");
        }
    }
}





