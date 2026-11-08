package com.ivs.lease;

import com.ivs.lease.api.LeaseApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** 定时生成月租应收并发送到期、逾期提醒；默认每小时执行一次且按天去重。 */
@Component
@ConditionalOnProperty(prefix = "ivs.lease.billing-automation", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class LeaseBillingScheduler {
    private static final Logger log = LoggerFactory.getLogger(LeaseBillingScheduler.class);

    private final LeaseApplicationService service;
    private final LeaseDepreciationService depreciationService;
    private final int reminderDays;

    public LeaseBillingScheduler(LeaseApplicationService service,
                                 LeaseDepreciationService depreciationService,
                                 @Value("${ivs.lease.billing-automation.reminder-days:7}") int reminderDays) {
        this.service = service;
        this.depreciationService = depreciationService;
        this.reminderDays = reminderDays;
    }

    @Scheduled(fixedDelayString = "${ivs.lease.billing-automation.interval-ms:3600000}")
    public void run() {
        depreciationService.ensureAll();
        LeaseApi.AutomationResult result = service.runAutomation(LocalDate.now(), reminderDays);
        if (result.generatedReceivables() > 0 || result.dueSoonReminders() > 0 || result.overdueReminders() > 0) {
            log.info("租赁月租自动化完成：生成应收 {} 笔，即将到期提醒 {} 条，逾期提醒 {} 条",
                    result.generatedReceivables(), result.dueSoonReminders(), result.overdueReminders());
        }
    }
}
