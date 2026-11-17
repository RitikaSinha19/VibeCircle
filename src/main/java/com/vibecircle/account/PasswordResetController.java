package com.vibecircle.account;

import com.vibecircle.account.dto.ForgotPasswordRequest;
import com.vibecircle.account.dto.MessageResponse;
import com.vibecircle.account.dto.ResetPasswordRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Public (under /api/auth/**) and rate limited per IP. */
@RestController
@RequestMapping("/api/auth")
public class PasswordResetController {

    private final PasswordResetService service;

    public PasswordResetController(PasswordResetService service) {
        this.service = service;
    }

    /** Always 202, whether or not the address belongs to an account. */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse forgot(@Valid @RequestBody ForgotPasswordRequest req) {
        service.requestReset(req.email());
        return new MessageResponse("If that address belongs to an account, a reset link has been sent.");
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody ResetPasswordRequest req) {
        service.reset(req.token(), req.newPassword());
    }
}
