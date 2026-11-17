package com.vibecircle.account;

/**
 * The one piece you plug a real mail provider into (SMTP, SES, SendGrid...). Define a bean implementing this
 * interface and it replaces the logging fallback automatically.
 */
public interface EmailService {
    void send(String to, String subject, String body);
}
