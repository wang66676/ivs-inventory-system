package com.ivs.platform.license;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivs.shared.error.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 30 天试用、离线授权文件校验与旧版注册码兼容。 */
@Service
public class LicenseService {
    private static final String INSTALLATION_ID = "DEFAULT";
    private static final String LICENSE_FILE_NAME = "ivs-license.lic";
    private static final long MAX_LICENSE_FILE_BYTES = 200 * 1024L;
    private static final Pattern MODULUS = Pattern.compile("<Modulus>([^<]+)</Modulus>");
    private static final Pattern EXPONENT = Pattern.compile("<Exponent>([^<]+)</Exponent>");
    private static final String PUBLIC_KEY_XML = "<RSAKeyValue><Modulus>uKiZbmno/kuOFM1+5rxSfGTT4M5zNgMkLj6T0Bcmjgq/nxiB7Q5r+EtzXt16uVDq4tUdl8DklQFB6iraKa9nP0qe8ddS6/+fhdyWcogKrlqkP37O8I7lvydRXaiIipKrd6nL/n8EbNUEKPLGpLGYv4oSLq5VixAPaAVNdh0LKP30Wozab9ibzVph7H9x7F3zZus3iJ09VI5JpltyrbpcPJ+SwXtWc0S+r7uys7S3t4p0h3sCfB4OEoPpGdvPF6PX+7eXhKW5/wSlmD8D6IZr/2I5S7uZU6kMBTaBZcuaZUZTFytdy9qE0UV3Tc8i3B4Sbr4VLlwRjmDsQ9CbNTG3bQ==</Modulus><Exponent>AQAB</Exponent></RSAKeyValue>";

    private final SoftwareLicenseRepository repository;
    private final int trialDays;
    private final String contactEmail;
    private final ObjectMapper objectMapper;
    private final Path licenseFile;
    private volatile PublicKey publicKey;

    public LicenseService(SoftwareLicenseRepository repository,
                          @Value("${ivs.license.trial-days:30}") int trialDays,
                          @Value("${ivs.license.contact-email:691288299@qq.com}") String contactEmail,
                          @Value("${ivs.license.file:}") String configuredLicenseFile,
                          ObjectMapper objectMapper) {
        this.repository = repository;
        this.trialDays = trialDays;
        this.contactEmail = contactEmail;
        this.objectMapper = objectMapper;
        this.licenseFile = configuredLicenseFile == null || configuredLicenseFile.isBlank()
                ? Path.of(System.getProperty("user.dir", "."), LICENSE_FILE_NAME)
                : Path.of(configuredLicenseFile.trim());
    }

    @Transactional
    public LicenseStatusView status() {
        SoftwareLicense license = current();
        LocalDate today = LocalDate.now();
        Instant now = Instant.now();
        LicenseFileResult fileResult = readLicenseFile(license.getInstallationId(), today);

        if (fileResult.verifiedLicense() != null && fileResult.state().equals("VALID")) {
            VerifiedLicense verified = fileResult.verifiedLicense();
            Instant expiresAt = verified.expiresAt();
            return new LicenseStatusView(license.getInstallationId().toString().toUpperCase(), "LICENSED",
                    daysRemaining(now, expiresAt), license.getTrialStartedAt(), license.getTrialEndsAt(),
                    expiresAt, verified.customerName(), verified.username(), contactEmail,
                    "软件已授权，有效期至 " + expiresAt.atZone(ZoneId.systemDefault()).toLocalDate()
                            + "；授权用户：" + verified.username() + "。",
                    true, fileResult.state(), licenseFile.getFileName().toString());
        }

        boolean legacyDatabaseLicense = license.isLicensed()
                && (license.getActivationCode() == null || !license.getActivationCode().startsWith("FILE:"));
        if (legacyDatabaseLicense) {
            Instant expiresAt = license.getLicensedUntil();
            return new LicenseStatusView(license.getInstallationId().toString().toUpperCase(), "LICENSED",
                    daysRemaining(now, expiresAt), license.getTrialStartedAt(), license.getTrialEndsAt(),
                    expiresAt, license.getCustomerName(), license.getCustomerName(), contactEmail,
                    "软件已授权，授权有效期至 " + expiresAt + "。", Files.exists(licenseFile),
                    fileResult.state(), licenseFile.getFileName().toString());
        }

        if (license.isTrialActive()) {
            String suffix = fileResult.state().equals("INVALID") || fileResult.state().equals("MACHINE_MISMATCH")
                    ? " 当前授权文件无效，已继续使用试用期。"
                    : "";
            return new LicenseStatusView(license.getInstallationId().toString().toUpperCase(), "TRIAL",
                    daysRemaining(now, license.getTrialEndsAt()), license.getTrialStartedAt(),
                    license.getTrialEndsAt(), null, null, null, contactEmail,
                    "软件当前处于试用期，剩余 " + daysRemaining(now, license.getTrialEndsAt())
                            + " 天。如需长期使用，请联系作者：" + contactEmail + "。" + suffix,
                    Files.exists(licenseFile), fileResult.state(), licenseFile.getFileName().toString());
        }

        String message = fileResult.message() == null || fileResult.message().isBlank()
                ? expiredMessage()
                : "试用期已结束，当前授权文件不可用：" + fileResult.message() + "。如需继续使用，请联系作者：" + contactEmail + "。";
        return new LicenseStatusView(license.getInstallationId().toString().toUpperCase(), "EXPIRED", 0,
                license.getTrialStartedAt(), license.getTrialEndsAt(), null, null, null, contactEmail,
                message, Files.exists(licenseFile), fileResult.state(), licenseFile.getFileName().toString());
    }

    /** 兼容旧版文本框注册码；新版客户统一使用 ivs-license.lic。 */
    @Transactional
    public LicenseStatusView activate(String activationCode) {
        Activation activation = verifyLegacyCode(activationCode);
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
    public LicenseStatusView importLicense(byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new BusinessException("LICENSE_FILE_REQUIRED", "请选择授权文件");
        }
        if (fileBytes.length > MAX_LICENSE_FILE_BYTES) {
            throw new BusinessException("LICENSE_FILE_TOO_LARGE", "授权文件超过 200KB，无法导入");
        }
        SoftwareLicense license = current();
        VerifiedLicense verified = verifyLicenseFile(fileBytes, license.getInstallationId(), LocalDate.now());
        try {
            Files.createDirectories(licenseFile.toAbsolutePath().getParent());
            Path temporary = licenseFile.resolveSibling(LICENSE_FILE_NAME + ".tmp");
            Files.write(temporary, fileBytes);
            try {
                Files.move(temporary, licenseFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, licenseFile, StandardCopyOption.REPLACE_EXISTING);
            }
            license.activate(verified.expiresAt(), "FILE:" + sha256(fileBytes), verified.customerName());
            repository.save(license);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("LICENSE_FILE_WRITE_FAILED", "授权文件保存失败：" + exception.getMessage());
        }
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
        return "试用期已结束，软件无法登录。如需继续使用，请联系作者：" + contactEmail + "。";
    }

    private long daysRemaining(Instant now, Instant expiresAt) {
        return Math.max(0, (Duration.between(now, expiresAt).toSeconds() + 86_399L) / 86_400L);
    }

    private SoftwareLicense current() {
        return repository.findById(INSTALLATION_ID)
                .orElseGet(() -> repository.save(SoftwareLicense.startTrial(trialDays)));
    }

    private LicenseFileResult readLicenseFile(UUID installationId, LocalDate today) {
        if (!Files.exists(licenseFile)) {
            return new LicenseFileResult("MISSING", "未找到授权文件", null);
        }
        try {
            byte[] bytes = Files.readAllBytes(licenseFile);
            return readLicenseFile(bytes, installationId, today);
        } catch (Exception exception) {
            return new LicenseFileResult("INVALID", "授权文件无法读取", null);
        }
    }

    private LicenseFileResult readLicenseFile(byte[] bytes, UUID installationId, LocalDate today) {
        try {
            VerifiedLicense verified = verifyLicenseFile(bytes, installationId, today);
            return new LicenseFileResult("VALID", null, verified);
        } catch (LicenseFileException exception) {
            return new LicenseFileResult(exception.state, exception.getMessage(), null);
        } catch (Exception exception) {
            return new LicenseFileResult("INVALID", "授权文件格式无效", null);
        }
    }

    private VerifiedLicense verifyLicenseFile(byte[] fileBytes, UUID installationId, LocalDate today) {
        try {
            JsonNode envelope = objectMapper.readTree(fileBytes);
            String payloadValue = envelope.path("payload").asText();
            String signatureValue = envelope.path("signature").asText();
            if (payloadValue.isBlank() || signatureValue.isBlank()) {
                throw new LicenseFileException("INVALID", "授权文件缺少签名数据");
            }
            byte[] payload = Base64.getUrlDecoder().decode(payloadValue);
            byte[] signature = Base64.getUrlDecoder().decode(signatureValue);
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey());
            verifier.update(payload);
            if (!verifier.verify(signature)) {
                throw new LicenseFileException("INVALID", "授权文件签名无效");
            }
            JsonNode data = objectMapper.readTree(payload);
            if (!"IVS-LICENSE-V2".equals(data.path("format").asText())) {
                throw new LicenseFileException("INVALID", "授权文件版本不受支持");
            }
            UUID softwareCode;
            LocalDate validFrom;
            LocalDate validUntil;
            try {
                softwareCode = UUID.fromString(data.path("softwareCode").asText().trim());
                validFrom = LocalDate.parse(data.path("validFrom").asText());
                validUntil = LocalDate.parse(data.path("validUntil").asText());
            } catch (IllegalArgumentException | DateTimeParseException exception) {
                throw new LicenseFileException("INVALID", "授权文件中的软件码或日期格式无效");
            }
            String username = data.path("username").asText();
            if (username.isBlank()) {
                throw new LicenseFileException("INVALID", "授权文件缺少授权用户名");
            }
            if (!installationId.equals(softwareCode)) {
                throw new LicenseFileException("MACHINE_MISMATCH", "授权文件与本机软件码不匹配");
            }
            if (today.isBefore(validFrom)) {
                throw new LicenseFileException("NOT_YET_VALID", "授权尚未到生效日期 " + validFrom);
            }
            if (today.isAfter(validUntil)) {
                throw new LicenseFileException("EXPIRED", "授权文件已于 " + validUntil + " 到期");
            }
            Instant expiresAt = validUntil.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusMillis(1);
            String customerName = data.path("customerName").asText(null);
            String edition = data.path("edition").asText("STANDARD");
            String licenseId = data.path("licenseId").asText(data.path("softwareCode").asText());
            return new VerifiedLicense(licenseId, username, customerName, edition, validFrom, validUntil, expiresAt);
        } catch (LicenseFileException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new LicenseFileException("INVALID", "授权文件解析失败");
        }
    }

    private Activation verifyLegacyCode(String code) {
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
            return new Activation(UUID.fromString(fields[1]), Instant.ofEpochSecond(Long.parseLong(fields[2])),
                    customerName);
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

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private record Activation(UUID installationId, Instant expiresAt, String customerName) {
    }

    private record LicenseFileResult(String state, String message, VerifiedLicense verifiedLicense) {
    }

    private record VerifiedLicense(String licenseId, String username, String customerName, String edition,
                                   LocalDate validFrom, LocalDate validUntil, Instant expiresAt) {
    }

    private static final class LicenseFileException extends RuntimeException {
        private final String state;

        private LicenseFileException(String state, String message) {
            super(message);
            this.state = state;
        }
    }

    public record LicenseStatusView(String installationId, String state, long daysRemaining,
                                    Instant trialStartedAt, Instant trialEndsAt, Instant licensedUntil,
                                    String customerName, String username, String contactEmail, String message,
                                    boolean licenseFilePresent, String licenseFileState, String licenseFileName) {
    }
}
