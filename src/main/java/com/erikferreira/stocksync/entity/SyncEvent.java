package com.erikferreira.stocksync.entity;

import com.erikferreira.stocksync.entity.enums.SyncStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "sync_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(optional = false)
    @JoinColumn(name = "sales_channel_id", nullable = false)
    private SalesChannel salesChannel;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "external_order_id")
    private String externalOrderId;

    @Column(name = "event_timestamp", nullable = false)
    private Instant timestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SyncStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Builder.Default
    @Column(nullable = false)
    private Integer attempts = 1;
}
