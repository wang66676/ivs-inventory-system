package com.ivs.platform.operations;

import com.ivs.shared.error.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 在线数据库备份、附件归档与临时库恢复验证。 */
@Service
public class BackupService {
    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneId.of("Asia/Shanghai"));

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final Path backupDir;
    private final Path invoiceDir;
    private final boolean enabled;
    private final int retentionDays;

    public BackupService(JdbcTemplate jdbc, DataSource dataSource,
                         @Value("${ivs.backup.dir:./backups}") String backupDir,
                         @Value("${ivs.invoice.storage-dir:./data/invoice-files}") String invoiceDir,
                         @Value("${ivs.backup.enabled:true}") boolean enabled,
                         @Value("${ivs.backup.retention-days:30}") int retentionDays) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.backupDir = Paths.get(backupDir).toAbsolutePath().normalize();
        this.invoiceDir = Paths.get(invoiceDir).toAbsolutePath().normalize();
        this.enabled = enabled;
        this.retentionDays = Math.max(7, retentionDays);
    }

    public Path backupDirectory() {
        return backupDir;
    }

    public Optional<Path> latestBackup() {
        if (!Files.isDirectory(backupDir)) return Optional.empty();
        try (Stream<Path> files = Files.list(backupDir)) {
            return files.filter(path -> path.getFileName().toString().startsWith("ivs-backup-"))
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.endsWith(".zip") || name.endsWith(".dump");
                    })
                    .max(Comparator.comparing(path -> path.getFileName().toString()));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    @Scheduled(cron = "${ivs.backup.cron:0 30 2 * * *}", zone = "Asia/Shanghai")
    public void scheduledBackup() {
        if (!enabled) return;
        try {
            createBackup();
        } catch (RuntimeException exception) {
            log.error("每日自动备份失败: {}", exception.getMessage(), exception);
        }
    }

    public synchronized BackupResult createBackup() {
        try {
            Files.createDirectories(backupDir);
            assertH2Database();
            String stamp = STAMP.format(Instant.now());
            Path sql = backupDir.resolve("ivs-db-" + stamp + ".sql");
            jdbc.execute("SCRIPT TO '" + sqlPath(sql) + "'");
            boolean verified = verifyRestore(sql);
            if (!verified) {
                Files.deleteIfExists(sql);
                throw new ConflictException("BACKUP_VERIFY_FAILED", "备份恢复验证失败，未生成正式备份包");
            }
            Path bundle = backupDir.resolve("ivs-backup-" + stamp + ".zip");
            int attachments = writeBundle(bundle, sql, stamp, verified);
            Files.deleteIfExists(sql);
            cleanupExpiredBundles();
            BackupResult result = new BackupResult(bundle.getFileName().toString(), bundle.toString(),
                    Files.size(bundle), verified, attachments, Instant.now());
            log.info("在线备份完成并通过恢复验证: {}, 附件 {} 个", result.fileName(), attachments);
            return result;
        } catch (IOException exception) {
            throw new ConflictException("BACKUP_IO_FAILED", "备份文件处理失败：" + exception.getMessage());
        }
    }

    private void assertH2Database() {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            if (product == null || !product.toLowerCase().contains("h2")) {
                throw new ConflictException("BACKUP_EXTERNAL_REQUIRED",
                        "当前数据库为 " + product + "，请使用 scripts/backup-prod.ps1 执行 PostgreSQL 备份");
            }
        } catch (ConflictException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ConflictException("BACKUP_DATABASE_UNAVAILABLE", "无法连接数据库执行备份");
        }
    }

    private boolean verifyRestore(Path sql) {
        String url = "jdbc:h2:mem:ivs_backup_verify_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("RUNSCRIPT FROM '" + sqlPath(sql) + "'");
            try (ResultSet result = statement.executeQuery("select count(*) from flyway_schema_history")) {
                return result.next() && result.getInt(1) > 0;
            }
        } catch (Exception exception) {
            log.error("备份恢复验证失败: {}", exception.getMessage());
            return false;
        }
    }

    private int writeBundle(Path bundle, Path sql, String stamp, boolean verified) throws IOException {
        int attachments = 0;
        try (OutputStream file = Files.newOutputStream(bundle);
             ZipOutputStream zip = new ZipOutputStream(file, StandardCharsets.UTF_8)) {
            addEntry(zip, sql, "database.sql");
            if (Files.isDirectory(invoiceDir)) {
                List<Path> invoiceFiles;
                try (Stream<Path> walk = Files.walk(invoiceDir)) {
                    invoiceFiles = walk.filter(Files::isRegularFile).toList();
                }
                for (Path invoice : invoiceFiles) {
                    String relative = invoiceDir.relativize(invoice).toString().replace('\\', '/');
                    addEntry(zip, invoice, "invoice-files/" + relative);
                    attachments++;
                }
            }
            String manifest = "createdAt=" + Instant.now() + "\nrestoreVerified=" + verified
                    + "\nattachmentFiles=" + attachments + "\nbackupStamp=" + stamp + "\n";
            zip.putNextEntry(new ZipEntry("manifest.txt"));
            zip.write(manifest.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return attachments;
    }

    private static void addEntry(ZipOutputStream zip, Path source, String entryName) throws IOException {
        zip.putNextEntry(new ZipEntry(entryName));
        Files.copy(source, zip);
        zip.closeEntry();
    }

    private static String sqlPath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/').replace("'", "''");
    }

    private void cleanupExpiredBundles() throws IOException {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        try (Stream<Path> files = Files.list(backupDir)) {
            for (Path path : files.filter(item -> item.getFileName().toString().startsWith("ivs-backup-")).toList()) {
                if (Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)) Files.deleteIfExists(path);
            }
        }
    }

    public record BackupResult(String fileName, String path, long sizeBytes, boolean restoreVerified,
                               int attachmentFiles, Instant createdAt) {
    }
}