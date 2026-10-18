package com.ivs.inventory.stock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 锁货超时自动释放（默认开启，可通过 ivs.inventory.lock-release.enabled=false 关闭）。 */
@Component
@ConditionalOnProperty(prefix = "ivs.inventory.lock-release", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class StockLockScheduler {

    private static final Logger log = LoggerFactory.getLogger(StockLockScheduler.class);

    private final StockService stockService;

    public StockLockScheduler(StockService stockService) {
        this.stockService = stockService;
    }

    @Scheduled(fixedDelayString = "${ivs.inventory.lock-release.interval-ms:60000}")
    public void releaseExpiredLocks() {
        int released = stockService.releaseExpired();
        if (released > 0) {
            log.info("已自动释放超时库存锁定 {} 条", released);
        }
    }
}