package com.vibecircle.auth.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record RegisterRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_.]{3,30}$",
                message = "3-30 characters: letters, digits, underscore or dot") String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72, message = "must be 8-72 characters") String password,
        @NotBlank @Size(max = 100) String fullName,
        @NotNull @Past LocalDate dateOfBirth) {
}
