package com.punith.chat.web.message.dto;

import jakarta.validation.constraints.NotBlank;

public record SendMessageRequest(
        @NotBlank String body,
        String clientMessageId
) {}
