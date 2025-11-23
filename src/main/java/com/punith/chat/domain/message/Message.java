package com.punith.chat.domain.message;

import com.punith.chat.domain.chat.Chat;
import com.punith.chat.domain.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(
        name = "messages",
        indexes = {
                @Index(name = "idx_messages_chat_created_at", columnList = "chat_id, created_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_chat_client_msg_id",
                        columnNames = {"chat_id", "client_msg_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_id", nullable = false)
    private Chat chat;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;


    @Column(name = "client_msg_id", length = 64)
    private String clientMessageId;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
