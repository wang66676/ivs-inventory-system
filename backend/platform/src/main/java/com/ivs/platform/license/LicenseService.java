package com.ivs.platform.license;

import com.ivs.shared.error.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 30 天试用、离线激活码校验与授权状态查询。 */
@Service
public class LicenseService {
    private static final String INSTALLATION_ID = "DEFAULT";
    private static final Pattern MODULUS = Pattern.compile("<Modulus>([^<]+)</Modulus>");
    private static final Pattern EXPONENT = Pattern.compile("<Exponent>([^<]+)</Exponent>");
    private static final String PUBLIC_KEY_XML = "<RSAKeyValue><Modulus>uKiZbmno/kuOFM1+5rxSfGTT4M5zNgMkLj6T0Bcmjgq/nxiB7Q5r+EtzXt16uVDq4tUdl8DklQFB6iraKa9nP0qe8ddS6/+fhdyWcogKrlqkP37O8I7lvydRXaiIipKrd6nL/n8EbNUEKPLGpLGYv4oSLq5VixAPaAVNdh0LKP30Wozab9ibzVph7H9x7F3zZus3iJ09VI5JpltyrbpcPJ+SwXtWc0S+r7uys7S3t4p0h3sCfB4OEoPpGdvPF6PX+7eXhKW5/wSlmD8D6IZr/2I5S7uZU6kMBTaBZcuaZUZTFytdy9qE0UV3Tc8i3B4Sbr4VLlwRjmDsQ9CbNTG3bQ==</Modulus><Exponent>AQAB</Exponent></RSAKeyValue>";

    private final SoftwareLicenseRepository repository;
    private final int trialDays;
    private final String contactEmail;
    private volatile PublicKey publicKey;

    public LicenseService(SoftwareLicenseRepository repository,
                          @Value("${ivs.license.trial-days:30}") int trialDays,
                          @Value("${ivs.license.contact-email:691288299@qq.com}") String contactEmail) {
        this.repository = repository;
        this.trialDays = trialDays;
        this.contactEmail = contactEmail;
    }

    @Transactional
    public LicenseStatusView status() {
        SoftwareLicense license = current();
        Instant now = Instant.now();
        String state;
        Instant expiresAt;
        if (license.isLicensed()) {
            state = "LICENSED";
            expiresAt = license.getLicensedUntil();
        } else if (license.isTrialActive()) {
            state = "TRIAL";
            expiresAt = license.getTrialEndsAt();
        } else {
            state = "EXPIRED";
            expiresAt = license.getTrialEndsAt();
        }
        long daysRemaining = Math.max(0, (Duration.between(now, expiresAt).toSeconds() + 86_399L) / 86_400L);
        String message = switch (state) {
            case "LICENSED" -> "软件已注册，授权有效期至 " + expiresAt + "。";
            case "TRIAL" -> "软件当前处于试用期，剩余 " + daysRemaining + " 天。";
            default -> expiredMessage();
        };
        return new LicenseStatusView(license.getInstallationId().toString().toUpperCase(), state, daysRemaining,
                license.getTrialStartedAt(), license.getTrialEndsAt(), license.getLicensedUntil(),
                license.getCustomerName(), contactEmail, message);
    }

    @Transactional
    public LicenseStatusView activate(String activationCode) {
        Activation activation = verify(activationCode);
        SoftwareLicense license = current();
        if (!license.getInstallationId().equals(activation.installationId())) {
            throw new BusinessException("LICENSE_MACHINE_MISMATCH", "注册码与本机安装码不匹配，请联系作者重新生成");
        }
        try {
            license.activate(activation.expiresAt(), activationCode.trim(), activation.customerName());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("LICENSE_CODE_EXPIRED", exception.getMessage());
        }
        repository.save(license);
        return status();
    }

    @Transactional
    public void ensureUsable() {
        LicenseStatusView current = status();
        if ("EXPIRED".equals(current.state())) {
            throw new BusinessException("LICENSE_EXPIRED", current.message());
        }
    }

    @Transactional
    public boolean isUsable() {
        return !"EXPIRED".equals(status().state());
    }

    public String expiredMessage() {
        return "试用期已结束，软件无法登录。如需继续使用，请联系作者：691288299@qq.com。";
    }

    private SoftwareLicense current() {
        return repository.findById(INSTALLATION_ID)
                .orElseGet(() -> repository.save(SoftwareLicense.startTrial(trialDays)));
    }

    private Activation verify(String code) {
        try {
            String value = code == null ? "" : code.trim();
            int separator = value.indexOf('.');
            if (separator <= 0) throw new IllegalArgumentException("注册码格式不正确");
            byte[] payload = Base64.getUrlDecoder().decode(value.substring(0, separator));
            byte[] signature = Base64.getUrlDecoder().decode(value.substring(separator + 1));
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey());
            verifier.update(payload);
            if (!verifier.verify(signature)) throw new IllegalArgumentException("注册码签名无效");
            String text = new String(payload, StandardCharsets.UTF_8);
            String[] fields = text.split("\\|", 4);
            if (fields.length != 4 || !"IVS1".equals(fields[0])) {
                throw new IllegalArgumentException("注册码内容无效");
            }
            String customerName = fields[3].isBlank() ? null
                    : new String(Base64.getUrlDecoder().decode(fields[3]), StandardCharsets.UTF_8);
            return new Activation(java.util.UUID.fromString(fields[1]),
                    Instant.ofEpochSecond(Long.parseLong(fields[2])), customerName);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("LICENSE_CODE_INVALID", "注册码无效，请核对后重试");
        }
    }

    private PublicKey publicKey() throws Exception {
        PublicKey cached = publicKey;
        if (cached != null) return cached;
        synchronized (this) {
            if (publicKey != null) return publicKey;
            Matcher modulus = MODULUS.matcher(PUBLIC_KEY_XML);
            Matcher exponent = EXPONENT.matcher(PUBLIC_KEY_XML);
            if (!modulus.find() || !exponent.find()) throw new IllegalStateException("授权公钥配置错误");
            RSAPublicKeySpec spec = new RSAPublicKeySpec(
                    new BigInteger(1, Base64.getDecoder().decode(modulus.group(1))),
                    new BigInteger(1, Base64.getDecoder().decode(exponent.group(1))));
            publicKey = KeyFactory.getInstance("RSA").generatePublic(spec);
            return publicKey;
        }
    }

    private record Activation(java.util.UUID installationId, Instant expiresAt, String customerName) {
    }

    public record LicenseStatusView(String installationId, String state, long daysRemaining,
                                    Instant trialStartedAt, Instant trialEndsAt, Instant licensedUntil,
                                    String customerName, String contactEmail, String message) {
    }
}
