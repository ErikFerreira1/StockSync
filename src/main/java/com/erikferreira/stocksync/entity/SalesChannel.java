package com.erikferreira.stocksync.entity;

import com.erikferreira.stocksync.entity.enums.ChannelType;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales_channels")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ChannelType type;

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @OneToOne(mappedBy = "salesChannel")
    private IntegrationCredential integrationCredential;

    @Builder.Default
    @OneToMany(mappedBy = "salesChannel")
    private List<MarketplaceListing> marketplaceListings = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "salesChannel")
    private List<Order> orders = new ArrayList<>();
}
