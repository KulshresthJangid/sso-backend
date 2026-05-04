package com.sso.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Email(message = "Must be a valid email")
        String email,

        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        String orgRole   // "ORG_ADMIN" or "ORG_MEMBER" — defaults to ORG_MEMBER
) {}
