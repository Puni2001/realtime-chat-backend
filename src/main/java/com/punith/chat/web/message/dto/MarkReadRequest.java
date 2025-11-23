package com.punith.chat.web.message.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record MarkReadRequest(
        @NotEmpty List<Long> messageIds
) {}
