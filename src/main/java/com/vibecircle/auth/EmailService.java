
package com.vibecircle.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendOtp(String recipientEmail, String otp) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(senderEmail);
        message.setTo(recipientEmail);
        message.setSubject("VibeCircle - Email Verification");

        message.setText(
            "Welcome to VibeCircle!\n\n" +
            "Your email verification OTP is: " + otp + "\n\n" +
            "This OTP is valid for 10 minutes.\n" +
            "Please do not share this code with anyone.\n\n" +
            "Find Your People. Find Your Vibe."
        );

        mailSender.send(message);
    }
}
