package com.erikferreira.stocksync.repository;

import com.erikferreira.stocksync.entity.*;
import com.erikferreira.stocksync.entity.enums.*;
import com.erikferreira.stocksync.support.PostgreSQLIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RepositoryIntegrationTest extends PostgreSQLIntegrationTest {

    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private SalesChannelRepository salesChannelRepository;
    @Autowired private IntegrationCredentialRepository credentialRepository;
    @Autowired private MarketplaceListingRepository listingRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private StockMovementRepository movementRepository;
    @Autowired private SyncEventRepository syncEventRepository;
    @Autowired private TestEntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private UserRepository userRepository;

    private Product product;
    private SalesChannel channel;

    @BeforeEach
    void setUp() {
        product = productRepository.save(Product.builder()
                .sku("SKU-" + System.nanoTime()).name("Product").basePrice(BigDecimal.TEN).active(true).build());
        channel = salesChannelRepository.save(SalesChannel.builder()
                .name("ML-" + System.nanoTime()).type(ChannelType.MERCADO_LIVRE)
                .baseUrl("https://api.mercadolibre.com").active(true).build());
    }

    @Test
    void inventoryQueriesShouldFindByProductAndBelowMinimum() {
        inventoryRepository.save(Inventory.builder().product(product).availableQuantity(1).minQuantity(2)
                .updatedAt(Instant.now()).build());

        assertThat(inventoryRepository.findByProductId(product.getId())).isPresent();
        assertThat(inventoryRepository.findAllBelowMinimum())
                .extracting(inventory -> inventory.getProduct().getId())
                .contains(product.getId());
    }

    @Test
    void productPageShouldLoadAssociatedInventory() {
        inventoryRepository.save(Inventory.builder().product(product).availableQuantity(8).minQuantity(2)
                .updatedAt(Instant.now()).build());
        entityManager.flush();
        entityManager.clear();

        Product loaded = productRepository.findAll(PageRequest.of(0, 20)).stream()
                .filter(item -> item.getId().equals(product.getId())).findFirst().orElseThrow();

        assertThat(loaded.getInventory().getAvailableQuantity()).isEqualTo(8);
    }

    @Test
    void productFindAllShouldLoadInventoriesInSingleQuery() {
        inventoryRepository.save(Inventory.builder().product(product).availableQuantity(8).minQuantity(2)
                .updatedAt(Instant.now()).build());
        Product secondProduct = productRepository.save(Product.builder()
                .sku("SKU-N1-" + System.nanoTime()).name("Second product")
                .basePrice(BigDecimal.TEN).active(true).build());
        inventoryRepository.save(Inventory.builder().product(secondProduct).availableQuantity(4).minQuantity(1)
                .updatedAt(Instant.now()).build());
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var result = productRepository.findAll(PageRequest.of(0, 100));
        assertThat(result.getContent())
                .extracting(item -> item.getInventory().getAvailableQuantity())
                .contains(8, 4);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void salesChannelAndCredentialQueriesShouldFindExistingValues() {
        credentialRepository.save(IntegrationCredential.builder().salesChannel(channel)
                .clientId("client").clientSecretEncrypted("secret").build());

        assertThat(salesChannelRepository.existsByName(channel.getName())).isTrue();
        assertThat(salesChannelRepository.existsByNameAndIdNot(channel.getName(), channel.getId() + 1)).isTrue();
        assertThat(credentialRepository.findBySalesChannelId(channel.getId())).isPresent();
    }

    @Test
    void listingQueriesShouldRespectChannelTypeStatusAndActiveChannel() {
        MarketplaceListing listing = listingRepository.save(MarketplaceListing.builder()
                .product(product).salesChannel(channel).listingId("MLB123")
                .status(ListingStatus.ACTIVE).build());

        assertThat(listingRepository.findByListingIdAndSalesChannelType("MLB123", ChannelType.MERCADO_LIVRE))
                .contains(listing);
        assertThat(listingRepository.findByListingIdAndSalesChannelId("MLB123", channel.getId()))
                .contains(listing);
        assertThat(listingRepository.existsBySalesChannelIdAndListingId(channel.getId(), "MLB123")).isTrue();
        assertThat(listingRepository.findByProductIdAndStatusAndSalesChannelActiveTrue(
                product.getId(), ListingStatus.ACTIVE)).containsExactly(listing);
    }

    @Test
    void orderQueriesShouldFetchItemsAndDetectExternalOrder() {
        Order order = orderRepository.save(Order.builder().salesChannel(channel).externalOrderId("EXT-1")
                .orderDate(Instant.now()).status(OrderStatus.PENDING).build());
        orderItemRepository.save(OrderItem.builder().order(order).product(product).quantity(2)
                .unitPrice(BigDecimal.TEN).build());
        entityManager.flush();
        entityManager.clear();
        Order persistedOrder = orderRepository.findById(order.getId()).orElseThrow();

        List<Order> result = orderRepository.fetchItemsForOrders(List.of(persistedOrder));

        assertThat(orderRepository.existsBySalesChannelIdAndExternalOrderId(channel.getId(), "EXT-1")).isTrue();
        assertThat(result).singleElement().satisfies(found -> assertThat(found.getItems()).hasSize(1));
    }

    @Test
    void movementQueryShouldReturnOnlyRequestedProductHistory() {
        movementRepository.save(StockMovement.builder().product(product).occurredAt(Instant.now())
                .quantity(2).type(MovementType.MANUAL_INCREASE).build());

        assertThat(movementRepository.findByProductId(product.getId(), PageRequest.of(0, 10)).getContent())
                .hasSize(1);
    }

    @Test
    void syncEventQueriesShouldFilterAndOrderEvents() {
        SyncEvent older = syncEventRepository.save(SyncEvent.builder().product(product).salesChannel(channel)
                .timestamp(Instant.now().minusSeconds(60)).status(SyncStatus.FAILURE)
                .errorMessage("old").build());
        SyncEvent newer = syncEventRepository.save(SyncEvent.builder().product(product).salesChannel(channel)
                .timestamp(Instant.now()).status(SyncStatus.FAILURE).errorMessage("new").build());

        var history = syncEventRepository.findByProductIdOrderByTimestampDesc(product.getId(), PageRequest.of(0, 10));

        assertThat(history.getContent()).containsExactly(newer, older);
        assertThat(syncEventRepository.findByStatus(SyncStatus.FAILURE, PageRequest.of(0, 10)).getContent())
                .contains(older, newer);
    }

    @Test
    void userQueriesShouldFindByUsernameAndDetectExistence() {
        String username = "user" + System.nanoTime();
        String passwordHash = "$2a$12$VVBXU5K8bqS6o/IezhXRMec2qWqIGklUu2YIXjhXE7lXNLqlPHBcO";

        User user = userRepository.save(User.builder()
                .username(username)
                .passwordHash(passwordHash)
                .role(UserRole.ADMIN)
                .build());

        entityManager.flush();
        entityManager.clear();

        var result = userRepository.findByUsername(user.getUsername());

        assertThat(result).isNotEmpty();
        assertThat(result.get().getUsername()).isEqualTo(user.getUsername());
        assertThat(result.get().getRole()).isEqualTo(user.getRole());
        assertThat(result.get().getCreatedAt()).isNotNull();
        assertThat(result.get().isActive()).isTrue();
        assertThat(userRepository.existsByUsername(user.getUsername())).isTrue();
        assertThat(userRepository.existsByUsername("TestFail")).isFalse();
    }

    @Test
    void userUsernameShouldBeUnique() {
        String username = "duplicate-user-" + System.nanoTime();
        String passwordHash = "$2a$12$VVBXU5K8bqS6o/IezhXRMec2qWqIGklUu2YIXjhXE7lXNLqlPHBcO";

        userRepository.saveAndFlush(User.builder()
                .username(username)
                .passwordHash(passwordHash)
                .role(UserRole.ADMIN)
                .build());

        assertThatThrownBy(() -> userRepository.saveAndFlush(User.builder()
                .username(username)
                .passwordHash(passwordHash)
                .role(UserRole.VIEWER)
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

}
