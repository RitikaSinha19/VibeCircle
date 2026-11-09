package com.vibecircle.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** identifier = username or email */
public record LoginRequest(@NotBlank String identifier, @NotBlank String password) {
}
