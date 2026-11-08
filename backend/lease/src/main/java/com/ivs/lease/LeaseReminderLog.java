package com.ivs.lease;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** 按天记录提醒，保证定时任务重复执行时不会产生重复通知。 */
@Entity
@Table(name = "lease_reminder_log", schema = "ivs")
public class LeaseReminderLog {
    @Id
    private UUID id;
    @Column(name = "schedule_id", nullable = false)
    private UUID scheduleId;
    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_type", nullable = false, length = 20)
    private LeaseReminderType reminderType;
    @Column(name = "reminder_date", nullable = false)
    private LocalDate reminderDate;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LeaseReminderLog() {
    }

    public static LeaseReminderLog create(UUID scheduleId, LeaseReminderType reminderType, LocalDate reminderDate) {
        LeaseReminderLog log = new LeaseReminderLog();
        log.id = UUID.randomUUID();
        log.scheduleId = scheduleId;
        log.reminderType = reminderType;
        log.reminderDate = reminderDate;
        log.createdAt = Instant.now();
        return log;
    }

    public UUID getId() { return id; }
    public UUID getScheduleId() { return scheduleId; }
    public LeaseReminderType getReminderType() { return reminderType; }
    public LocalDate getReminderDate() { return reminderDate; }
}
