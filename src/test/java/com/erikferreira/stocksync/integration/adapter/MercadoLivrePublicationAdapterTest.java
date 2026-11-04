package com.erikferreira.stocksync.integration.adapter;

import com.erikferreira.stocksync.dto.marketplaceListing.PublicationRequestDTO;
import com.erikferreira.stocksync.entity.enums.ListingStatus;
import com.erikferreira.stocksync.service.TokenRefreshService;
import com.erikferreira.stocksync.service.exceptions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(MockitoExtension.class)
class MercadoLivrePublicationAdapterTest {
    @Mock TokenRefreshService tokens;
    MercadoLivrePublicationAdapter adapter;
    MockRestServiceServer server;
    final String base = "https://api.mercadolibre.com";

    @BeforeEach void setUp() {
        var builder = RestClient.builder().baseUrl(base);
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new MercadoLivrePublicationAdapter(builder.build(), tokens, new ObjectMapper());
        when(tokens.getValidAccessToken(2L)).thenReturn("secret-token");
    }

    PublicationRequestDTO request() {
        return new PublicationRequestDTO(UUID.randomUUID(), 1L, 2L, "Teclado", "MLB123", new BigDecimal("149.90"),
                "gold_special", List.of("https://example.com/keyboard.jpg"),
                List.of(new PublicationRequestDTO.Attribute("BRAND", null, "Marca"),
                        new PublicationRequestDTO.Attribute("ITEM_CONDITION", "2230284", null)), List.of(), "me2", false, false, "Descrição");
    }

    void user(boolean userProducts) {
        server.expect(requestTo(base + "/users/me")).andExpect(header("Authorization", "Bearer secret-token"))
                .andRespond(withSuccess("{\"id\":99,\"site_id\":\"MLB\",\"tags\":"
                        + (userProducts ? "[\"user_product_seller\"]" : "[]") + "}", MediaType.APPLICATION_JSON));
    }

    @Test void shouldPublishUserProductWithStockPhotosAndAttributesAndNoTitle() {
        user(true);
        server.expect(requestTo(base + "/items")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer secret-token"))
                .andExpect(content().json("""
                    {"family_name":"Teclado","category_id":"MLB123","price":149.90,"currency_id":"BRL",
                    "available_quantity":7,"listing_type_id":"gold_special","buying_mode":"buy_it_now",
                    "pictures":[{"source":"https://example.com/keyboard.jpg"}],
                    "attributes":[{"id":"BRAND","value_name":"Marca"},{"id":"ITEM_CONDITION","value_id":"2230284"}],
                    "shipping":{"mode":"me2","free_shipping":false,"local_pick_up":false},"channels":["marketplace"]}
                    """))
                .andExpect(jsonPath("$.title").doesNotExist()).andExpect(jsonPath("$.description").doesNotExist())
                .andRespond(withSuccess("{\"id\":\"MLB987\",\"permalink\":\"https://example.com/item\",\"status\":\"active\"}", MediaType.APPLICATION_JSON));
        var result = adapter.publish(request(), 7);
        assertThat(result.id()).isEqualTo("MLB987");
        assertThat(result.status()).isEqualTo(ListingStatus.ACTIVE);
        server.verify();
    }

    @Test void legacySellerShouldSendTitleAndValidateWithoutPublishing() {
        user(false);
        server.expect(requestTo(base + "/items/validate")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.title").value("Teclado")).andExpect(jsonPath("$.family_name").doesNotExist())
                .andRespond(withNoContent());
        adapter.validate(request(), 3);
        server.verify();
    }

    @Test void rejectionShouldExposeSafeCodesAndRequiredAttributeNamesWithoutRemoteSecrets() {
        user(true);
        server.expect(requestTo(base + "/items/validate")).andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body("""
            {"error":"validation_error","message":"secret-token","cause":[
            {"type":"error","code":"item.attributes.missing_required","message":"Missing [BRAND, MODEL] secret-token"},
            {"type":"warning","code":"ignored.warning","message":"secret-token"}]}
            """));
        assertThatThrownBy(() -> adapter.validate(request(), 3)).isInstanceOfSatisfying(PublicationValidationException.class,
                ex -> assertThat(ex.getIssues()).containsExactly("item.attributes.missing_required: Missing [BRAND, MODEL] [REDACTED]"))
                .hasMessageNotContaining("secret-token");
        server.verify();
    }

    @Test void serverFailureShouldKeepOutcomeUnknownWithoutLeakingBody() {
        user(true);
        server.expect(requestTo(base + "/items")).andRespond(withServerError().body("secret-token"));
        assertThatThrownBy(() -> adapter.publish(request(), 3)).isInstanceOf(MarketplaceIntegrationException.class)
                .hasMessageNotContaining("secret-token").hasCause(null);
    }

    @Test void malformedCreatedResponseShouldBeUncertain() {
        user(true);
        server.expect(requestTo(base + "/items")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> adapter.publish(request(), 3)).isInstanceOf(MarketplaceIntegrationException.class);
    }

    @Test void descriptionShouldUseSeparateAuthenticatedEndpoint() {
        server.expect(requestTo(base + "/items/MLB987/description")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer secret-token"))
                .andExpect(content().json("{\"plain_text\":\"Descrição\"}")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        adapter.addDescription(2L, "MLB987", "Descrição");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void optionsShouldKeepRestrictedMe2AndRespectMandatoryAccountPreferences(boolean mandatoryMe2) {
        user(true);
        server.expect(requestTo(base + "/categories/MLB123")).andRespond(withSuccess("""
            {"name":"Teclados","settings":{"listing_allowed":true,"status":"enabled","max_title_length":60,
            "max_pictures_per_item":10,"shipping_modes":null}}
            """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(base + "/categories/MLB123/attributes")).andRespond(withSuccess("""
            [{"id":"BRAND","name":"Marca","tags":{"required":true},"value_type":"string","values":[]},
            {"id":"HIDDEN","tags":{"hidden":true}},
            {"id":"ITEM_CONDITION","name":"Condição","tags":{"hidden":true},"value_type":"list",
            "values":[{"id":"2230284","name":"Novo"}]}]
            """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(base + "/categories/MLB123/sale_terms")).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(base + "/users/99/available_listing_types?category_id=MLB123")).andRespond(withSuccess("""
            {"available":[{"id":"gold_special","name":"Clássico","remaining_listings":null},
            {"id":"bronze","name":"Bronze","remaining_listings":null},
            {"id":"free","name":"Grátis","remaining_listings":0}]}
            """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(base + "/users/99/shipping_preferences")).andRespond(withSuccess(
                "{\"modes\":[\"me2\",\"custom\",\"not_specified\"],\"local_pick_up\":true,\"tags\":"
                        + (mandatoryMe2 ? "[\"optional_me1_allowed\"]" : "[]") + "}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(base + "/categories/MLB123/shipping_preferences")).andRespond(withSuccess(
                "{\"logistics\":[{\"mode\":\"me2\"},{\"mode\":\"custom\"},{\"mode\":\"not_specified\"}],\"restricted\":true}", MediaType.APPLICATION_JSON));
        var options = adapter.getOptions(2L, "MLB123");
        assertThat(options.userProducts()).isTrue();
        assertThat(options.attributes()).extracting(f -> f.id()).containsExactly("BRAND", "ITEM_CONDITION");
        assertThat(options.attributes()).allSatisfy(f -> assertThat(f.required()).isTrue());
        assertThat(options.shippingModes()).containsExactlyElementsOf(mandatoryMe2 ? List.of("me2") : List.of("me2", "not_specified"));
        assertThat(options.listingTypes()).singleElement().satisfies(t -> assertThat(t.id()).isEqualTo("gold_special"));
        assertThat(options.localPickUpAllowed()).isTrue();
        server.verify();
    }

    @Test void categorySearchShouldEncodeQueryAndReturnSuggestions() {
        server.expect(requestTo(base + "/sites/MLB/domain_discovery/search?limit=6&q=teclado%20gamer"))
                .andRespond(withSuccess("[{\"category_id\":\"MLB123\",\"category_name\":\"Teclados\"}]", MediaType.APPLICATION_JSON));
        assertThat(adapter.searchCategories(2L, "teclado gamer")).singleElement().satisfies(c -> assertThat(c.id()).isEqualTo("MLB123"));
        server.verify();
    }

    @Test void requestTimeoutResponseShouldRemainUncertainRatherThanAllowNewPublication() {
        user(true);
        server.expect(requestTo(base + "/items")).andRespond(withStatus(HttpStatus.REQUEST_TIMEOUT));
        assertThatThrownBy(() -> adapter.publish(request(), 3)).isInstanceOf(MarketplaceIntegrationException.class);
    }

    @Test void moderatedListingShouldNotBeReportedAsActive() {
        user(true);
        server.expect(requestTo(base + "/items")).andRespond(withSuccess(
                "{\"id\":\"MLB987\",\"status\":\"under_review\"}", MediaType.APPLICATION_JSON));
        assertThat(adapter.publish(request(), 3).status()).isEqualTo(ListingStatus.INACTIVE);
    }

    @Test void genericValidationCodeShouldKeepActualAttributeError() {
        user(true);
        server.expect(requestTo(base + "/items/validate")).andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body("""
            {"error":"validation_error","cause":[{"type":"error","cause_id":3510,"code":"validation_error",
            "message":"Attribute [ITEM_CONDITION] is not valid, item values [invalid]."}]}
            """));
        assertThatThrownBy(() -> adapter.validate(request(), 3)).isInstanceOfSatisfying(PublicationValidationException.class,
                ex -> assertThat(ex.getIssues()).containsExactly("validation_error: Attribute [ITEM_CONDITION] is not valid, item values [invalid]."));
    }

    @Test void causeWithoutCodeShouldKeepAccountRequirementInsteadOfGenericError() {
        user(true);
        server.expect(requestTo(base + "/items/validate")).andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body("""
            {"error":"validation_error","message":"Validation error","cause":[
            {"department":"moderations","references":["shipping.modes"],"message":"ME2 adoption is mandatory for the user"}]}
            """));
        assertThatThrownBy(() -> adapter.validate(request(), 3)).isInstanceOfSatisfying(PublicationValidationException.class,
                ex -> assertThat(ex.getIssues()).containsExactly("ME2 adoption is mandatory for the user"));
    }

    @Test void errorDetailsMustRedactTokensSecretsAndAuthorizationValues() {
        user(true);
        server.expect(requestTo(base + "/items/validate")).andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body("""
            {"error":"validation_error","cause":[{"type":"error","code":"validation_error",
            "message":"Invalid attribute. secret-token access_token=other-secret client_secret=app-secret refresh_token=refresh-secret APP_USR-123-456 Bearer another-token"}]}
            """));
        assertThatThrownBy(() -> adapter.validate(request(), 3)).isInstanceOfSatisfying(PublicationValidationException.class,
                ex -> assertThat(String.join(" ", ex.getIssues())).contains("Invalid attribute.", "[REDACTED]")
                        .doesNotContain("secret-token", "other-secret", "app-secret", "refresh-secret", "APP_USR-123", "another-token"));
    }

    @Test void validationWithOnlyWarningsShouldAllowReviewAndPreserveExplanations() {
        user(true);
        server.expect(requestTo(base + "/items/validate")).andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body("""
            {"error":"validation_error","message":"Validation error","cause":[
            {"type":"warning","code":"shipping.lost_me1_by_user","message":"User has not mode me1"},
            {"type":"warning","code":"shipping.me2_adoption_mandatory","message":"ME2 adoption is mandatory for the user"}]}
            """));
        assertThat(adapter.validate(request(), 3)).containsExactly("shipping.lost_me1_by_user: User has not mode me1",
                "shipping.me2_adoption_mandatory: ME2 adoption is mandatory for the user");
        server.verify();
    }

    @Test void creatingAnItemMustNotTreatWarningOnlyRejectionAsSuccessfulCreation() {
        user(true);
        server.expect(requestTo(base + "/items")).andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body("""
            {"error":"validation_error","cause":[{"type":"warning","code":"shipping.me2_adoption_mandatory","message":"ME2 required"}]}
            """));
        assertThatThrownBy(() -> adapter.publish(request(), 3)).isInstanceOf(PublicationValidationException.class);
    }

    @Test void validationWithEmptyOrUnknownCausesMustRemainBlocked() {
        user(true);
        server.expect(requestTo(base + "/items/validate")).andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"validation_error\",\"cause\":[]}"));
        assertThatThrownBy(() -> adapter.validate(request(), 3)).isInstanceOf(PublicationValidationException.class);
    }
}
