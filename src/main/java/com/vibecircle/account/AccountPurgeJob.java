package com.vibecircle.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Nightly (03:30 by default; vibecircle.account.purge-cron) erasure of accounts deleted long enough ago. */
@Component
public class AccountPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(AccountPurgeJob.class);
    private static final int MAX_BATCHES = 100;

    private final AccountService service;

    public AccountPurgeJob(AccountService service) {
        this.service = service;
    }

    @Scheduled(cron = "${vibecircle.account.purge-cron:0 30 3 * * *}")
    public int run() {
        int total = 0;
        try {
            for (int i = 0; i < MAX_BATCHES; i++) {
                int purged = service.purgeBatch();
                total += purged;
                if (purged == 0) {
                    break;
                }
            }
            if (total > 0) {
                log.info("Account purge erased {} accounts", total);
            }
        } catch (RuntimeException e) {
            log.warn("Account purge failed; it will retry on the next run", e);
        }
        return total;
    }
}
