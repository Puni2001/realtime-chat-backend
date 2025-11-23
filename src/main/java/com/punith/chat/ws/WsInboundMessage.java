package com.punith.chat.ws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WsInboundMessage {
    public String type;
    public Long chatId;
    public String body;
    public String clientMessageId;
    public List<Long> messageIds;
}
