package com.punith.chat.web.chat.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateChatRequest(
        @NotNull Boolean isGroup,
        String title,
        List<Long> participantIds
) {}
