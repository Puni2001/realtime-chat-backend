package com.punith.chat.domain.message;

import com.punith.chat.domain.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "message_receipts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_message_user",
                        columnNames = {"message_id", "user_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class MessageReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "delivery_ts")
    private OffsetDateTime deliveryTimestamp;

    @Column(name = "read_ts")
    private OffsetDateTime readTimestamp;
}
