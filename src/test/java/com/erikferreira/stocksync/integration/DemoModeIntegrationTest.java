package com.erikferreira.stocksync.integration;

import com.erikferreira.stocksync.entity.enums.ListingStatus;
import com.erikferreira.stocksync.entity.enums.MovementType;
import com.erikferreira.stocksync.entity.enums.UserRole;
import com.erikferreira.stocksync.integration.adapter.DemoMarketplaceAdapter;
import com.erikferreira.stocksync.repository.MarketplaceListingRepository;
import com.erikferreira.stocksync.repository.ProductRepository;
import com.erikferreira.stocksync.repository.UserRepository;
import com.erikferreira.stocksync.service.ProductService;
import com.erikferreira.stocksync.service.StockMovementService;
import com.erikferreira.stocksync.dto.stockMovement.StockMovementInsertDTO;
import com.erikferreira.stocksync.support.PostgreSQLIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(profiles = {"test", "demo"}, inheritProfiles = false)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DemoModeIntegrationTest extends PostgreSQLIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private MarketplaceListingRepository listingRepository;
    @Autowired private DemoMarketplaceAdapter demoMarketplaceAdapter;
    @Autowired private StockMovementService stockMovementService;
    @Autowired private ProductService productService;

    @Test
    void demoShouldStartWithSafeUserSeedDataSwaggerAndWorkingSynchronization() throws Exception {
        var user = userRepository.findByUsername("demo").orElseThrow();
        var product = productRepository.findBySku("DEMO-USB-C").orElseThrow();
        var listing = listingRepository
                .findByListingIdAndSalesChannelType(
                        "DEMO-MLB-USB-C",
                        com.erikferreira.stocksync.entity.enums.ChannelType.MERCADO_LIVRE)
                .orElseThrow();

        assertThat(user.getRole()).isEqualTo(UserRole.OPERATOR);
        assertThat(user.isActive()).isTrue();
        assertThat(product.getInventory().getAvailableQuantity()).isEqualTo(12);
        assertThat(demoMarketplaceAdapter.getCurrentStock("DEMO-MLB-USB-C")).isEqualTo(12);

        stockMovementService.registerManualMovement(new StockMovementInsertDTO(
                product.getId(),
                1,
                MovementType.MANUAL_DECREASE,
                null,
                null,
                "Demo synchronization"));

        assertThat(demoMarketplaceAdapter.getCurrentStock("DEMO-MLB-USB-C")).isEqualTo(11);

        productService.deactivate(product.getId());
        assertThat(demoMarketplaceAdapter.findListingById("DEMO-MLB-USB-C").status())
                .isEqualTo(ListingStatus.PAUSED);

        productService.activate(product.getId());
        assertThat(demoMarketplaceAdapter.findListingById("DEMO-MLB-USB-C").status())
                .isEqualTo(ListingStatus.ACTIVE);
        assertThat(demoMarketplaceAdapter.getCurrentStock("DEMO-MLB-USB-C")).isEqualTo(11);
        assertThat(listing.getListingId()).isEqualTo("DEMO-MLB-USB-C");

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("StockSync API"))
                .andExpect(jsonPath("$.paths['/demo/marketplace-listings']").exists())
                .andExpect(jsonPath("$.paths['/users']").doesNotExist());

        String token = loginAsDemoUser();
        mockMvc.perform(get("/demo/marketplace-listings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].listingId").exists());

        mockMvc.perform(patch("/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"currentPassword":"stocksync-demo","newPassword":"changed-password"}
                                """))
                .andExpect(status().isForbidden());
    }

    private String loginAsDemoUser() throws Exception {
        String response = mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"demo","password":"stocksync-demo"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return json.get("accessToken").asText();
    }
}
