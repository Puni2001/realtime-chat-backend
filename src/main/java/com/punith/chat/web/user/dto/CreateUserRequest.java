package com.punith.chat.web.user.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(
        @NotBlank String phone,
        @NotBlank String displayName
) {}
