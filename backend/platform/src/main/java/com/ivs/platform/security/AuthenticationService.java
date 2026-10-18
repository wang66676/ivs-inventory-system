package com.ivs.platform.security;

import com.ivs.platform.audit.PlatformActionAuditor;
import com.ivs.platform.security.api.ChangePasswordCommand;
import com.ivs.platform.security.api.LoginCommand;
import com.ivs.platform.security.api.LoginResponse;
import com.ivs.shared.error.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthenticationService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PlatformUserRepository userRepository;
    private final UserRoleAssignmentRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserSessionRepository sessionRepository;
    private final PlatformActionAuditor auditor;
    private final PasswordEncoder passwordEncoder;
    private final long sessionHours;

    public AuthenticationService(PlatformUserRepository userRepository,
                                 UserRoleAssignmentRepository userRoleRepository,
                                 RolePermissionRepository rolePermissionRepository,
                                 UserSessionRepository sessionRepository,
                                 PlatformActionAuditor auditor,
                                 PasswordEncoder passwordEncoder,
                                 @Value("${ivs.security.session-hours:12}") long sessionHours) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.sessionRepository = sessionRepository;
        this.auditor = auditor;
        this.passwordEncoder = passwordEncoder;
        this.sessionHours = sessionHours;
    }

    @Transactional
    public LoginResponse login(LoginCommand command) {
        String username = command.username().trim();
        PlatformUser user = userRepository.findByUsername(username)
                .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException("AUTH_INVALID_CREDENTIALS", "用户名或密码错误"));
        if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
            throw new BusinessException("AUTH_INVALID_CREDENTIALS", "用户名或密码错误");
        }

        String rawToken = generateToken();
        Instant expiresAt = Instant.now().plus(sessionHours, ChronoUnit.HOURS);
        sessionRepository.save(UserSession.create(hashToken(rawToken), user.getId(), expiresAt));
        auditor.record("AuthLoggedIn", "PlatformUser", user.getId(), java.util.Map.of(
                "username", user.getUsername(), "displayName", user.getDisplayName()));

        Set<String> roles = roles(user.getId());
        Set<String> permissions = permissions(roles);
        return new LoginResponse(
                rawToken,
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getDataScope().name(),
                roles,
                permissions
        );
    }

    @Transactional
    public Optional<AuthenticatedUser> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        UserSession session = sessionRepository.findByTokenHash(hashToken(rawToken)).orElse(null);
        Instant now = Instant.now();
        if (session == null || session.getRevokedAt() != null || session.getExpiresAt().isBefore(now)) {
            return Optional.empty();
        }
        PlatformUser user = userRepository.findById(session.getUserId())
                .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                .orElse(null);
        if (user == null) {
            return Optional.empty();
        }
        session.touch(now);
        Set<String> roles = roles(user.getId());
        return Optional.of(new AuthenticatedUser(
                user.getId(), session.getId(), user.getUsername(), user.getDisplayName(),
                user.getDataScope(),
                user.getCompanyId(), user.getStoreId(), user.getTeamId(),
                roles, permissions(roles)
        ));
    }

    /**
     * 修改自己的登录密码。
     *
     * <p>校验原密码 → 校验新密码强度 → 更新密码摘要 → 注销该用户除当前会话以外的所有会话。</p>
     */
    @Transactional
    public void changePassword(AuthenticatedUser actor, ChangePasswordCommand command) {
        PlatformUser user = userRepository.findById(actor.userId())
                .orElseThrow(() -> new com.ivs.shared.error.NotFoundException("用户", actor.userId()));
        if (!passwordEncoder.matches(command.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException("PASSWORD_CURRENT_MISMATCH", "原密码不正确");
        }
        if (command.newPassword().equals(command.currentPassword())) {
            throw new BusinessException("PASSWORD_UNCHANGED", "新密码不能与原密码相同");
        }
        if (command.newPassword().equalsIgnoreCase(user.getUsername())) {
            throw new BusinessException("PASSWORD_TOO_WEAK", "新密码不能与用户名相同");
        }
        user.changePassword(passwordEncoder.encode(command.newPassword()));
        userRepository.saveAndFlush(user);
        revokeOtherSessions(user.getId(), actor.sessionId());
    }

    /** 注销指定用户除 keepSessionId 之外的全部有效会话（改密后强制其它设备重新登录）。 */
    @Transactional
    public void revokeOtherSessions(UUID userId, UUID keepSessionId) {
        Instant now = Instant.now();
        sessionRepository.findByUserIdAndRevokedAtIsNull(userId).stream()
                .filter(session -> keepSessionId == null || !session.getId().equals(keepSessionId))
                .forEach(session -> session.revoke(now));
        sessionRepository.flush();
    }

    @Transactional
    public void logout(UUID sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> session.revoke(Instant.now()));
    }

    private Set<String> roles(UUID userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(UserRoleAssignment::getRoleCode)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<String> permissions(Set<String> roles) {
        if (roles.isEmpty()) {
            return Set.of();
        }
        return rolePermissionRepository.findByRoleCodeIn(List.copyOf(roles)).stream()
                .map(RolePermission::getPermissionCode)
                .collect(Collectors.toUnmodifiableSet());
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成会话摘要", exception);
        }
    }
}


