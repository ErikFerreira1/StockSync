package com.erikferreira.stocksync.controller;

import com.erikferreira.stocksync.controller.handler.GlobalExceptionHandler;
import com.erikferreira.stocksync.dto.integrationCredential.IntegrationCredentialResponseDTO;
import com.erikferreira.stocksync.dto.inventory.InventoryResponseDTO;
import com.erikferreira.stocksync.dto.marketplaceListing.MarketplaceListingResponseDTO;
import com.erikferreira.stocksync.dto.order.OrderResponseDTO;
import com.erikferreira.stocksync.dto.product.ProductResponseDTO;
import com.erikferreira.stocksync.dto.salesChannel.SalesChannelResponseDTO;
import com.erikferreira.stocksync.dto.stockMovement.StockMovementResponseDTO;
import com.erikferreira.stocksync.dto.syncEvent.SyncEventResponseDTO;
import com.erikferreira.stocksync.dto.user.UserResponseDTO;
import com.erikferreira.stocksync.entity.IntegrationCredential;
import com.erikferreira.stocksync.entity.enums.ChannelType;
import com.erikferreira.stocksync.entity.enums.ListingStatus;
import com.erikferreira.stocksync.entity.enums.MovementType;
import com.erikferreira.stocksync.entity.enums.OrderStatus;
import com.erikferreira.stocksync.entity.enums.SyncStatus;
import com.erikferreira.stocksync.entity.enums.UserRole;
import com.erikferreira.stocksync.service.IntegrationCredentialService;
import com.erikferreira.stocksync.service.InventoryService;
import com.erikferreira.stocksync.service.MarketplaceListingService;
import com.erikferreira.stocksync.service.OrderService;
import com.erikferreira.stocksync.service.OAuthStateService;
import com.erikferreira.stocksync.service.OrderSynchronizationService;
import com.erikferreira.stocksync.service.ProductService;
import com.erikferreira.stocksync.service.SalesChannelService;
import com.erikferreira.stocksync.service.StockMovementService;
import com.erikferreira.stocksync.service.SyncEventService;
import com.erikferreira.stocksync.service.TokenRefreshService;
import com.erikferreira.stocksync.service.UserService;
import com.erikferreira.stocksync.service.exceptions.ResourceNotFoundException;
import com.erikferreira.stocksync.service.exceptions.UsernameAlreadyExistsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ControllerContractTest {

    @Mock private ProductService productService;
    @Mock private InventoryService inventoryService;
    @Mock private SalesChannelService salesChannelService;
    @Mock private MarketplaceListingService listingService;
    @Mock private OrderService orderService;
    @Mock private OrderSynchronizationService orderSynchronizationService;
    @Mock private StockMovementService stockMovementService;
    @Mock private SyncEventService syncEventService;
    @Mock private IntegrationCredentialService credentialService;
    @Mock private TokenRefreshService tokenRefreshService;
    @Mock private UserService userService;
    @Mock private RestClient restClient;
    @Mock private OAuthStateService stateService;

    private MockMvc mvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MercadoLivreAuthController authController = new MercadoLivreAuthController(credentialService, restClient, stateService);
        ReflectionTestUtils.setField(authController, "redirectUri", "http://localhost:8080/mercadolivre/callback");
        mvc = MockMvcBuilders.standaloneSetup(
                        new ProductController(productService),
                        new InventoryController(inventoryService),
                        new SalesChannelController(salesChannelService),
                        new MarketplaceListingController(listingService),
                        new OrderController(orderService),
                        new OrderSynchronizationController(orderSynchronizationService),
                        new StockMovementController(stockMovementService),
                        new SyncEventController(syncEventService),
                        new IntegrationCredentialController(credentialService, tokenRefreshService),
                        new UserController(userService),
                        authController
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
        objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Test
    void productControllerShouldCreateProduct() throws Exception {
        var response = new ProductResponseDTO(1L, "SKU-1", "Product", "Description",
                new BigDecimal("10.00"), true, 5);
        when(productService.insert(any())).thenReturn(response);

        mvc.perform(post("/products").contentType("application/json").content("""
                        {"sku":"SKU-1","name":"Product","description":"Description",
                         "basePrice":10.00,"initialQuantity":5,"minQuantity":1}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/products/1"))
                .andExpect(jsonPath("$.sku").value("SKU-1"));
    }

    @Test
    void productControllerShouldRejectInvalidBody() throws Exception {
        mvc.perform(post("/products").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation error"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void productControllerShouldTranslateNotFoundAndNotExposeHardDelete() throws Exception {
        when(productService.findById(99L)).thenThrow(new ResourceNotFoundException("Product not found"));
        mvc.perform(get("/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mvc.perform(delete("/products/1")).andExpect(status().isMethodNotAllowed());
        verify(productService, never()).hardDelete(any());
    }

    @Test
    void globalExceptionHandlerShouldHideDetailsForUnexpectedException() throws Exception {
        when(productService.findById(1L))
                .thenThrow(new NullPointerException("sensitive internal detail"));

        mvc.perform(get("/products/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal server error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(content().string(not(containsString("NullPointerException"))))
                .andExpect(content().string(not(containsString("sensitive internal detail"))));
    }

    @Test
    void inventoryControllerShouldReturnInventoryAndUpdateMinimum() throws Exception {
        var response = new InventoryResponseDTO(2L, 1L, 10, 2, Instant.now());
        when(inventoryService.findByProduct(1L)).thenReturn(response);
        when(inventoryService.updateMinQuantity(eq(1L), any())).thenReturn(response);

        mvc.perform(get("/inventory/1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.availableQuantity").value(10));
        mvc.perform(patch("/inventory/product/1/min-quantity")
                        .contentType("application/json").content("{\"minQuantity\":2}"))
                .andExpect(status().isOk());
    }

    @Test
    void salesChannelControllerShouldCreateChannel() throws Exception {
        when(salesChannelService.insert(any())).thenReturn(new SalesChannelResponseDTO(
                2L, "Mercado Livre", ChannelType.MERCADO_LIVRE, "https://api.mercadolibre.com", true));
        mvc.perform(post("/sales-channels").contentType("application/json").content("""
                        {"name":"Mercado Livre","type":"MERCADO_LIVRE",
                         "baseUrl":"https://api.mercadolibre.com"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/sales-channels/2"));
    }

    @Test
    void marketplaceListingControllerShouldCreateListing() throws Exception {
        when(listingService.insert(any())).thenReturn(new MarketplaceListingResponseDTO(
                3L, 1L, 2L, "MLB123", "https://produto.mercadolivre.com.br/MLB123",
                ListingStatus.ACTIVE, null));
        mvc.perform(post("/marketplace-listings").contentType("application/json").content("""
                        {"productId":1,"salesChannelId":2,"listingId":"MLB123",
                         "listingUrl":"https://produto.mercadolivre.com.br/MLB123","status":"ACTIVE"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.listingId").value("MLB123"));
    }

    @Test
    void orderControllerShouldCancelOrder() throws Exception {
        when(orderService.cancelOrder(3L)).thenReturn(new OrderResponseDTO(
                3L, 2L, "EXT-1", Instant.now(), OrderStatus.CANCELED, List.of()));
        mvc.perform(patch("/orders/3/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
    }

    @Test
    void synchronizationControllerShouldReturnNoContent() throws Exception {
        mvc.perform(post("/sales-channels/2/orders/synchronize"))
                .andExpect(status().isNoContent());
        verify(orderSynchronizationService).synchronizeOrders(2L);
    }

    @Test
    void stockMovementControllerShouldCreateMovement() throws Exception {
        when(stockMovementService.registerManualMovement(any())).thenReturn(new StockMovementResponseDTO(
                4L, 1L, Instant.now(), 2, MovementType.MANUAL_INCREASE, null, null, "restock"));
        mvc.perform(post("/stock-movement").contentType("application/json").content("""
                        {"productId":1,"quantity":2,"type":"MANUAL_INCREASE","note":"restock"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("MANUAL_INCREASE"));
    }

    @Test
    void syncEventControllerShouldNotExposeManualEventCreation() throws Exception {
        mvc.perform(post("/sync-events").contentType("application/json").content("""
                        {"productId":1,"salesChannelId":2,"status":"SUCCESS"}
                        """))
                .andExpect(status().isNotFound());
        verify(syncEventService, never()).registerEvent(any());
    }

    @Test
    void integrationCredentialControllerShouldRegisterCredential() throws Exception {
        when(credentialService.registerCredential(any())).thenReturn(new IntegrationCredentialResponseDTO(
                6L, 2L, "client", null, false));
        mvc.perform(post("/integration-credentials").contentType("application/json").content("""
                        {"salesChannelId":2,"clientId":"client","clientSecret":"secret"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/integration-credentials/6"));
    }

    @Test
    void userControllerShouldCreateUserWithoutExposingPassword() throws Exception {
        Instant createdAt = Instant.parse("2026-08-31T12:00:00Z");
        when(userService.insert(any())).thenReturn(new UserResponseDTO(
                7L, "admin", UserRole.ADMIN, true, createdAt));

        mvc.perform(post("/users").contentType("application/json").content("""
                        {"username":"admin","password":"password123","role":"ADMIN"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/users/7"))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void userControllerShouldRejectInvalidBody() throws Exception {
        mvc.perform(post("/users").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation error"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void userControllerShouldTranslateDuplicateUsername() throws Exception {
        when(userService.insert(any()))
                .thenThrow(new UsernameAlreadyExistsException("Username already exists: admin"));

        mvc.perform(post("/users").contentType("application/json").content("""
                        {"username":"admin","password":"password123","role":"ADMIN"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Username already exists"))
                .andExpect(jsonPath("$.message").value("Username already exists: admin"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void mercadoLivreAuthControllerShouldRedirectToAuthorizationPage() throws Exception {
        when(stateService.generateState()).thenReturn("browser-token");
        when(stateService.createState(2L, "browser-token")).thenReturn("random-state");
        when(credentialService.getCredentialEntityBySalesChannelId(2L)).thenReturn(
                IntegrationCredential.builder().clientId("client-id").build());
        mvc.perform(get("/mercadolivre/authorize/2"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "https://auth.mercadolivre.com.br/authorization?response_type=code&client_id=client-id"
                                + "&redirect_uri=http://localhost:8080/mercadolivre/callback&state=random-state"));
    }
}
