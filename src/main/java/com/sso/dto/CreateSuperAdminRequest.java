package com.sso.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSuperAdminRequest(
        @NotBlank @Email(message = "Must be a valid email")
        String email,

        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters")
        String password
) {}
