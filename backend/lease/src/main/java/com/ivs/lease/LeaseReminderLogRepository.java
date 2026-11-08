package com.ivs.lease;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.UUID;

public interface LeaseReminderLogRepository extends JpaRepository<LeaseReminderLog, UUID> {
    boolean existsByScheduleIdAndReminderTypeAndReminderDate(
            UUID scheduleId, LeaseReminderType reminderType, LocalDate reminderDate);
}
