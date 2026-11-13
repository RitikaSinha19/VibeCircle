package com.vibecircle.call;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps call state honest: ringing timeouts, maximum duration, and people who vanished or were blocked mid-call.
 * Runs every few seconds; each step is its own short transaction.
 */
@Component
public class CallJanitor {

    private static final Logger log = LoggerFactory.getLogger(CallJanitor.class);

    private final CallService service;

    public CallJanitor(CallService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${vibecircle.calls.janitor-interval-ms:5000}")
    public void tick() {
        runOnce();
    }

    /** Returns how many calls were changed. Public so tests can trigger it without waiting for the timer. */
    public int runOnce() {
        int changed = 0;
        try {
            changed += service.expireRinging();
            changed += service.expireOverlong();
            changed += service.sweepParticipants();
        } catch (RuntimeException e) {
            log.warn("Call janitor failed; it will retry shortly", e);
        }
        return changed;
    }
}
