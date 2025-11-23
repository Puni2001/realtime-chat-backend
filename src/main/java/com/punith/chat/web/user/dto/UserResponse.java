package com.punith.chat.web.user.dto;

public record UserResponse(
        Long id,
        String phone,
        String displayName
) {}
