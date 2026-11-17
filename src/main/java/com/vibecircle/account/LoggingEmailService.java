package com.vibecircle.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback used when no real EmailService exists. In development it logs the whole message (so you can copy the
 * reset link). With includeBody = false (production) it logs only that an email was skipped, because a reset
 * link in a log file is a credential.
 */
public class LoggingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);
    private final boolean includeBody;

    public LoggingEmailService(boolean includeBody) {
        this.includeBody = includeBody;
    }

    @Override
    public void send(String to, String subject, String body) {
        if (includeBody) {
            log.info("DEV EMAIL to={} subject=\"{}\"\n{}", to, subject, body);
        } else {
            log.warn("No EmailService configured: NOT sending \"{}\". Provide an EmailService bean.", subject);
        }
    }
}
