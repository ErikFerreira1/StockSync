package com.erikferreira.stocksync.config;

import com.erikferreira.stocksync.dto.marketplaceListing.MarketplaceListingInsertDTO;
import com.erikferreira.stocksync.dto.product.ProductInsertDTO;
import com.erikferreira.stocksync.dto.salesChannel.SalesChannelRequestDTO;
import com.erikferreira.stocksync.dto.user.UserInsertDTO;
import com.erikferreira.stocksync.entity.MarketplaceListing;
import com.erikferreira.stocksync.entity.Product;
import com.erikferreira.stocksync.entity.SalesChannel;
import com.erikferreira.stocksync.entity.User;
import com.erikferreira.stocksync.entity.enums.ChannelType;
import com.erikferreira.stocksync.entity.enums.ListingStatus;
import com.erikferreira.stocksync.entity.enums.UserRole;
import com.erikferreira.stocksync.integration.adapter.DemoMarketplaceAdapter;
import com.erikferreira.stocksync.repository.MarketplaceListingRepository;
import com.erikferreira.stocksync.repository.ProductRepository;
import com.erikferreira.stocksync.repository.SalesChannelRepository;
import com.erikferreira.stocksync.repository.UserRepository;
import com.erikferreira.stocksync.service.MarketplaceListingService;
import com.erikferreira.stocksync.service.ProductService;
import com.erikferreira.stocksync.service.SalesChannelService;
import com.erikferreira.stocksync.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Component
@Profile("demo")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.demo.data", name = "enabled", havingValue = "true", matchIfMissing = true)
class DemoDataBootstrap implements ApplicationRunner {

    private static final String CHANNEL_NAME = "Mercado Livre (Simulado)";

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final SalesChannelRepository salesChannelRepository;
    private final MarketplaceListingRepository listingRepository;
    private final UserService userService;
    private final ProductService productService;
    private final SalesChannelService salesChannelService;
    private final MarketplaceListingService listingService;
    private final DemoMarketplaceAdapter demoMarketplaceAdapter;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.demo.username}")
    private String demoUsername;

    @Value("${app.demo.password}")
    private String demoPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        resetDemoUser();
        SalesChannel channel = resetDemoChannel();

        resetDemoListing(
                channel,
                "DEMO-USB-C",
                "Cabo USB-C reforçado",
                "Produto principal para testar sincronização de estoque e status.",
                new BigDecimal("25.00"),
                12,
                3,
                "DEMO-MLB-USB-C");
        resetDemoListing(
                channel,
                "DEMO-KEYBOARD",
                "Teclado mecânico compacto",
                "Produto abaixo do estoque mínimo para demonstrar alertas de inventário.",
                new BigDecimal("149.90"),
                2,
                5,
                "DEMO-MLB-KEYBOARD");
    }

    private void resetDemoUser() {
        User user = userRepository.findByUsername(demoUsername).orElse(null);
        if (user == null) {
            userService.insert(new UserInsertDTO(demoUsername, demoPassword, UserRole.OPERATOR));
            return;
        }

        user.setPasswordHash(passwordEncoder.encode(demoPassword));
        user.setRole(UserRole.OPERATOR);
        user.setActive(true);
        user.setAuthVersion(user.getAuthVersion() + 1);
        userRepository.save(user);
    }

    private SalesChannel resetDemoChannel() {
        SalesChannel channel = salesChannelRepository.findByName(CHANNEL_NAME).orElse(null);
        if (channel == null) {
            var response = salesChannelService.insert(new SalesChannelRequestDTO(
                    CHANNEL_NAME,
                    ChannelType.MERCADO_LIVRE,
                    "https://demo.stocksync.local"));
            return salesChannelRepository.findById(response.id()).orElseThrow();
        }

        channel.setType(ChannelType.MERCADO_LIVRE);
        channel.setBaseUrl("https://demo.stocksync.local");
        channel.setActive(true);
        return salesChannelRepository.save(channel);
    }

    private void resetDemoListing(
            SalesChannel channel,
            String sku,
            String name,
            String description,
            BigDecimal price,
            int quantity,
            int minQuantity,
            String listingId) {
        Product product = productRepository.findBySku(sku).orElse(null);
        if (product == null) {
            var response = productService.insert(new ProductInsertDTO(
                    sku,
                    name,
                    description,
                    price,
                    quantity,
                    minQuantity));
            product = productRepository.findById(response.id()).orElseThrow();
        } else {
            product.setName(name);
            product.setDescription(description);
            product.setBasePrice(price);
            product.setActive(true);
            product.getInventory().setAvailableQuantity(quantity);
            product.getInventory().setMinQuantity(minQuantity);
            product.getInventory().setUpdatedAt(Instant.now());
            product = productRepository.save(product);
        }

        MarketplaceListing listing = listingRepository
                .findByListingIdAndSalesChannelId(listingId, channel.getId())
                .orElse(null);
        if (listing == null) {
            var response = listingService.insert(new MarketplaceListingInsertDTO(
                    product.getId(),
                    channel.getId(),
                    listingId,
                    "https://demo.stocksync.local/listings/" + listingId,
                    ListingStatus.ACTIVE));
            listing = listingRepository.findById(response.id()).orElseThrow();
        } else {
            listing.setStatus(ListingStatus.ACTIVE);
            listing.setPausedByProductDeactivation(false);
            listing.setLastSyncedAt(null);
            listing = listingRepository.save(listing);
        }

        demoMarketplaceAdapter.registerListing(
                listing.getListingId(),
                product.getId(),
                product.getName(),
                product.getBasePrice(),
                quantity,
                listing.getStatus());
    }
}
