package com.punith.chat.web.chat.dto;

import java.util.List;

public record ChatResponse(
        Long id,
        boolean isGroup,
        String title,
        List<Long> participantIds
) {}
