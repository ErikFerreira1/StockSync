package com.erikferreira.stocksync.integration;

import com.erikferreira.stocksync.dto.marketplaceListing.*;
import com.erikferreira.stocksync.dto.product.ProductInsertDTO;
import com.erikferreira.stocksync.dto.salesChannel.SalesChannelRequestDTO;
import com.erikferreira.stocksync.entity.enums.*;
import com.erikferreira.stocksync.integration.MarketplacePublicationPort.PublishedListing;
import com.erikferreira.stocksync.repository.*;
import com.erikferreira.stocksync.service.*;
import com.erikferreira.stocksync.service.exceptions.*;
import com.erikferreira.stocksync.support.PostgreSQLIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MarketplacePublicationIntegrationTest extends PostgreSQLIntegrationTest {
    @MockitoBean MarketplacePublicationPort port;
    @MockitoBean MarketplaceIntegrationPort stockPort;
    @Autowired MarketplacePublicationService service;
    @Autowired ProductService products;
    @Autowired SalesChannelService channels;
    @Autowired MarketplacePublicationRepository publications;
    @Autowired MarketplaceListingRepository listings;
    @Autowired InventoryService inventory;
    @Autowired PublicationJournalService journal;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    PublicationRequestDTO request;

    @BeforeEach void setUp() {
        var suffix = UUID.randomUUID().toString();
        var product = products.insert(new ProductInsertDTO("PUB-" + suffix, "Teclado", "Descrição", BigDecimal.TEN, 7, 0));
        var channel = channels.insert(new SalesChannelRequestDTO("ML-" + suffix, ChannelType.MERCADO_LIVRE, "https://www.mercadolivre.com.br"));
        request = new PublicationRequestDTO(UUID.randomUUID(), product.id(), channel.id(), "Teclado", "MLB123", BigDecimal.TEN,
                "gold_special", List.of("https://example.com/photo.jpg"),
                List.of(new PublicationRequestDTO.Attribute("BRAND", null, "Marca")), List.of(), "me2", false, false, "Descrição");
        lenient().when(port.getOptions(channel.id(), "MLB123")).thenReturn(new PublicationOptionsDTO("MLB123", "Teclados", true, 60, 10,
                List.of(new PublicationOptionsDTO.Field("BRAND", "Marca", true, "string", List.of(), List.of(), "")),
                List.of(), List.of(new PublicationOptionsDTO.Value("gold_special", "Clássico")), List.of("me2"), false));
    }

    PublishedListing remote() { return new PublishedListing("MLB" + System.nanoTime(), "https://example.com/item", ListingStatus.ACTIVE); }

    @Test void repeatedRequestShouldReturnSameLocalListingAndOnlyPublishOnce() {
        var remote = remote();
        when(port.publish(request, 7)).thenReturn(remote);
        var first = service.publish(request);
        var second = service.publish(request);
        assertThat(second.listing().id()).isEqualTo(first.listing().id());
        assertThat(listings.findById(first.listing().id())).isPresent();
        assertThat(publications.findById(request.requestId()).orElseThrow().getState()).isEqualTo(PublicationState.LINKED);
        assertThat(inventory.getAvailableQuantity(request.productId())).isEqualTo(7);
        verify(port, times(1)).publish(request, 7);
        verifyNoInteractions(stockPort);
    }

    @Test void timeoutMustSurviveTransactionAndBlockAnotherExternalPost() {
        when(port.publish(request, 7)).thenThrow(new MarketplaceIntegrationException("Unknown outcome"));
        assertThatThrownBy(() -> service.publish(request)).isInstanceOf(MarketplaceIntegrationException.class);
        assertThat(publications.findById(request.requestId()).orElseThrow().getState()).isEqualTo(PublicationState.STARTED);
        assertThatThrownBy(() -> service.publish(request)).isInstanceOf(PublicationConflictException.class);
        verify(port, times(1)).publish(request, 7);
    }

    @Test void remoteIdentityShouldAllowRecoveryAfterLocalLinkFailureWithoutRepublishing() {
        var remote = remote();
        when(port.publish(request, 7)).thenAnswer(invocation -> {
            // Simulate the response being persisted but processing stopping before a local link.
            return remote;
        });
        doThrow(new MarketplaceIntegrationException("Description unavailable")).when(port)
                .addDescription(request.salesChannelId(), remote.id(), "Descrição");
        var first = service.publish(request);
        var saved = publications.findById(request.requestId()).orElseThrow();
        saved.setListingId(null);
        saved.setState(PublicationState.CREATED);
        publications.saveAndFlush(saved);
        var recovered = service.publish(request);
        assertThat(recovered.listing().id()).isEqualTo(first.listing().id());
        assertThat(recovered.warnings()).containsExactly("description_not_saved");
        verify(port, times(1)).publish(request, 7);
    }

    @Test void concurrentRequestsWithSameKeyShouldCreateOnlyOneExternalListing() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var remote = remote();
        when(port.publish(request, 7)).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Test timed out");
            return remote;
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<PublicationResponseDTO> first = executor.submit(() -> service.publish(request));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> service.publish(request)).isInstanceOf(PublicationConflictException.class);
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).listing().listingId()).isEqualTo(remote.id());
            verify(port, times(1)).publish(request, 7);
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test void viewerAndAnonymousMustNotPublishOrReadAccountOptions() throws Exception {
        mvc.perform(post("/marketplace-publications").with(user("viewer").roles("VIEWER"))
                .contentType("application/json").content(mapper.writeValueAsString(request))).andExpect(status().isForbidden());
        mvc.perform(get("/marketplace-publications/options").with(user("viewer").roles("VIEWER"))
                .param("salesChannelId", request.salesChannelId().toString()).param("categoryId", "MLB123")).andExpect(status().isForbidden());
        mvc.perform(post("/marketplace-publications").contentType("application/json").content(mapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(port);
    }

    @Test void operatorShouldPublishAndReceiveRealExternalId() throws Exception {
        var remote = remote();
        when(port.publish(request, 7)).thenReturn(remote);
        mvc.perform(post("/marketplace-publications").with(user("operator").roles("OPERATOR"))
                .contentType("application/json").content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.listing.listingId").value(remote.id()));
    }

    @Test void invalidQueryParametersShouldReturnBadRequestBeforeMarketplaceCall() throws Exception {
        mvc.perform(get("/marketplace-publications/options").with(user("operator").roles("OPERATOR"))
                .param("salesChannelId", "-1").param("categoryId", "not-a-category")).andExpect(status().isBadRequest());
        verifyNoInteractions(port);
    }

    @Test void simultaneousReservationsShouldHaveExactlyOneWinner() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> reserve = () -> {
            start.await(5, TimeUnit.SECONDS);
            try { journal.reserve(request, "a".repeat(64)); return true; }
            catch (PublicationConflictException exception) { return false; }
        };
        try {
            var first = executor.submit(reserve);
            var second = executor.submit(reserve);
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(publications.findById(request.requestId())).isPresent();
        } finally { executor.shutdownNow(); }
    }
}
