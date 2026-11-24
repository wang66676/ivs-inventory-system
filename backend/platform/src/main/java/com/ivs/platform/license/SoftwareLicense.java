package com.ivs.platform.license;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** 本安装实例的试用期与离线授权记录。 */
@Entity
@Table(name = "software_license", schema = "ivs")
public class SoftwareLicense {
    private static final String SINGLETON_ID = "DEFAULT";

    @Id
    @Column(length = 20)
    private String id;

    @Column(name = "installation_id", nullable = false, unique = true)
    private UUID installationId;

    @Column(name = "trial_started_at", nullable = false)
    private Instant trialStartedAt;

    @Column(name = "trial_ends_at", nullable = false)
    private Instant trialEndsAt;

    @Column(name = "licensed_until")
    private Instant licensedUntil;

    @Column(name = "activation_code", columnDefinition = "text")
    private String activationCode;

    @Column(name = "customer_name", length = 200)
    private String customerName;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected SoftwareLicense() {
    }

    public static SoftwareLicense startTrial(int trialDays) {
        Instant now = Instant.now();
        SoftwareLicense value = new SoftwareLicense();
        value.id = SINGLETON_ID;
        value.installationId = UUID.randomUUID();
        value.trialStartedAt = now;
        value.trialEndsAt = now.plusSeconds(Math.max(1, trialDays) * 86_400L);
        value.createdAt = now;
        value.updatedAt = now;
        return value;
    }

    public void activate(Instant until, String code, String customer) {
        if (until == null || until.isBefore(Instant.now())) {
            throw new IllegalArgumentException("授权到期时间必须晚于当前时间");
        }
        this.licensedUntil = until;
        this.activationCode = code;
        this.customerName = customer == null || customer.isBlank() ? null : customer.trim();
        this.activatedAt = Instant.now();
        touch();
    }

    public boolean isLicensed() {
        return licensedUntil != null && licensedUntil.isAfter(Instant.now());
    }

    public boolean isTrialActive() {
        return !trialEndsAt.isBefore(Instant.now());
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        touch();
    }

    public String getId() { return id; }
    public UUID getInstallationId() { return installationId; }
    public Instant getTrialStartedAt() { return trialStartedAt; }
    public Instant getTrialEndsAt() { return trialEndsAt; }
    public Instant getLicensedUntil() { return licensedUntil; }
    public String getActivationCode() { return activationCode; }
    public String getCustomerName() { return customerName; }
    public Instant getActivatedAt() { return activatedAt; }
}
