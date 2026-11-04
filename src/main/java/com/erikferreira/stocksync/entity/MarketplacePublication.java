package com.erikferreira.stocksync.entity;

import jakarta.persistence.*;
import lombok.*;
import com.erikferreira.stocksync.entity.enums.ListingStatus;
import com.erikferreira.stocksync.entity.enums.PublicationState;
import java.util.UUID;

@Entity
@Table(name = "marketplace_publications")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MarketplacePublication {
    @Id private UUID requestId;
    @Column(nullable = false, length = 64) private String requestHash;
    @Column(nullable = false) private Long productId;
    @Column(nullable = false) private Long salesChannelId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30) private PublicationState state;
    private String externalId;
    @Column(length = 1000) private String externalUrl;
    @Enumerated(EnumType.STRING) private ListingStatus externalStatus;
    private Long listingId;
    private boolean descriptionSaved;
}
