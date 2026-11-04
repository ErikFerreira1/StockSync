package com.erikferreira.stocksync.integration.adapter;

import com.erikferreira.stocksync.dto.marketplaceListing.*;
import com.erikferreira.stocksync.entity.enums.ListingStatus;
import com.erikferreira.stocksync.integration.MarketplacePublicationPort;
import com.erikferreira.stocksync.service.TokenRefreshService;
import com.erikferreira.stocksync.service.exceptions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.*;
import java.util.stream.StreamSupport;

@Component
@Profile("!demo")
@RequiredArgsConstructor
@Slf4j
public class MercadoLivrePublicationAdapter implements MarketplacePublicationPort {
    private final RestClient mercadoLivreRestClient;
    private final TokenRefreshService tokenRefreshService;
    private final ObjectMapper objectMapper;

    @Override
    public List<PublicationCategoryDTO> searchCategories(Long channelId, String query) {
        String token = tokenRefreshService.getValidAccessToken(channelId);
        JsonNode result = get(token, "/sites/MLB/domain_discovery/search?limit=6&q={query}", query);
        return nodes(result).stream().map(n -> new PublicationCategoryDTO(
                n.path("category_id").asText(), n.path("category_name").asText())).toList();
    }

    @Override
    public PublicationOptionsDTO getOptions(Long channelId, String categoryId) {
        String token = tokenRefreshService.getValidAccessToken(channelId);
        JsonNode user = get(token, "/users/me");
        if (!"MLB".equals(user.path("site_id").asText())) {
            throw invalid("Only Brazilian Mercado Livre accounts are supported");
        }
        JsonNode category = get(token, "/categories/{id}", categoryId);
        JsonNode settings = category.path("settings");
        if (!settings.path("listing_allowed").asBoolean() || !"enabled".equals(settings.path("status").asText())) {
            throw invalid("Category does not allow publication");
        }
        List<PublicationOptionsDTO.Field> attributes = fields(get(token, "/categories/{id}/attributes", categoryId));
        List<PublicationOptionsDTO.Field> terms = fields(get(token, "/categories/{id}/sale_terms", categoryId));
        JsonNode types = get(token, "/users/{id}/available_listing_types?category_id={category}",
                user.path("id").asText(), categoryId);
        List<PublicationOptionsDTO.Value> listingTypes = nodes(types.path("available")).stream()
                .filter(n -> Set.of("gold_special", "gold_pro", "free").contains(n.path("id").asText()))
                .filter(n -> n.path("remaining_listings").isNull() || !n.has("remaining_listings")
                        || n.path("remaining_listings").asInt() > 0)
                .map(n -> new PublicationOptionsDTO.Value(n.path("id").asText(), n.path("name").asText())).toList();
        JsonNode shipping = get(token, "/users/{id}/shipping_preferences", user.path("id").asText());
        JsonNode categoryShipping = get(token, "/categories/{id}/shipping_preferences", categoryId);
        List<String> userModes = nodes(shipping.path("modes")).stream().map(JsonNode::asText).toList();
        boolean mandatoryMe2 = nodes(shipping.path("tags")).stream()
                .anyMatch(n -> "optional_me1_allowed".equals(n.asText()));
        log.info("Publication shipping availability: account modes {}, category modes {}, mandatory ME2 {}, category restricted {}",
                userModes, nodes(categoryShipping.path("logistics")).stream().map(n -> n.path("mode").asText()).toList(),
                mandatoryMe2, categoryShipping.path("restricted").asBoolean());
        List<String> modes = nodes(categoryShipping.path("logistics")).stream().map(n -> n.path("mode").asText())
                .filter(userModes::contains).filter(m -> Set.of("me2", "not_specified").contains(m))
                // "restricted" signals category requirements, not a blanket ban on ME2.
                // The item validator checks those requirements against the actual product attributes.
                .filter(m -> !mandatoryMe2 || "me2".equals(m)).distinct().toList();
        boolean userProducts = nodes(user.path("tags")).stream().anyMatch(n -> "user_product_seller".equals(n.asText()));
        return new PublicationOptionsDTO(categoryId, category.path("name").asText(), userProducts,
                settings.path("max_title_length").asInt(60), settings.path("max_pictures_per_item").asInt(12),
                attributes, terms, listingTypes, modes, shipping.path("local_pick_up").asBoolean());
    }

    @Override
    public List<String> validate(PublicationRequestDTO request, int quantity) {
        String token = tokenRefreshService.getValidAccessToken(request.salesChannelId());
        JsonNode result = post(token, "/items/validate", payload(request, quantity, token));
        if (result == null) return List.of();
        return nodes(result.path("cause")).stream().filter(n -> "warning".equals(n.path("type").asText()))
                .map(n -> safeRemoteText(n.path("code").asText() + ": " + n.path("message").asText(), token)).toList();
    }

    @Override
    public PublishedListing publish(PublicationRequestDTO request, int quantity) {
        String token = tokenRefreshService.getValidAccessToken(request.salesChannelId());
        JsonNode item = post(token, "/items", payload(request, quantity, token));
        if (item == null || !item.path("id").asText().matches("MLB[0-9]+")) {
            throw new MarketplaceIntegrationException("Publication outcome is unknown; check Mercado Livre before trying again");
        }
        ListingStatus status = switch (item.path("status").asText()) {
            case "active" -> ListingStatus.ACTIVE;
            case "paused" -> ListingStatus.PAUSED;
            case "closed" -> ListingStatus.CLOSED;
            default -> ListingStatus.INACTIVE;
        };
        return new PublishedListing(item.path("id").asText(), item.path("permalink").asText(null), status);
    }

    @Override
    public void addDescription(Long channelId, String listingId, String description) {
        post(tokenRefreshService.getValidAccessToken(channelId), "/items/" + listingId + "/description",
                objectMapper.createObjectNode().put("plain_text", description));
    }

    private ObjectNode payload(PublicationRequestDTO request, int quantity, String token) {
        JsonNode user = get(token, "/users/me");
        boolean userProducts = nodes(user.path("tags")).stream().anyMatch(n -> "user_product_seller".equals(n.asText()));
        ObjectNode body = objectMapper.createObjectNode();
        body.put(userProducts ? "family_name" : "title", request.name());
        body.put("category_id", request.categoryId());
        body.put("price", request.price());
        body.put("currency_id", "BRL");
        body.put("available_quantity", quantity);
        body.put("buying_mode", "buy_it_now");
        body.put("listing_type_id", request.listingTypeId());
        body.putArray("channels").add("marketplace");
        request.pictures().forEach(url -> body.withArray("pictures").addObject().put("source", url));
        addAttributes(body, "attributes", request.attributes());
        addAttributes(body, "sale_terms", request.saleTerms());
        body.putObject("shipping").put("mode", request.shippingMode())
                .put("free_shipping", request.freeShipping()).put("local_pick_up", request.localPickUp());
        return body;
    }

    private void addAttributes(ObjectNode body, String key, List<PublicationRequestDTO.Attribute> attributes) {
        var array = body.putArray(key);
        for (var attribute : attributes) {
            var value = array.addObject().put("id", attribute.id());
            if (attribute.valueId() != null && !attribute.valueId().isBlank()) value.put("value_id", attribute.valueId());
            else value.put("value_name", attribute.valueName());
        }
    }

    private JsonNode get(String token, String path, Object... args) {
        try {
            JsonNode result = mercadoLivreRestClient.get().uri(path, args)
                    .headers(h -> h.setBearerAuth(token)).retrieve().body(JsonNode.class);
            if (result == null) throw new MarketplaceIntegrationException("Empty Mercado Livre response");
            return result;
        } catch (RestClientException exception) {
            // Never propagate remote bodies or token-bearing exceptions to logs/API clients.
            throw new MarketplaceIntegrationException("Could not load Mercado Livre publication options");
        }
    }

    private JsonNode post(String token, String path, JsonNode body) {
        try {
            return mercadoLivreRestClient.post().uri(path).headers(h -> h.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            if ("/items/validate".equals(path) && exception.getStatusCode().value() == 400) {
                try {
                    JsonNode result = objectMapper.readTree(exception.getResponseBodyAsByteArray());
                    List<JsonNode> causes = nodes(result.path("cause"));
                    if ("validation_error".equals(result.path("error").asText()) && !causes.isEmpty()
                            && causes.stream().allMatch(n -> "warning".equals(n.path("type").asText()))) {
                        // Mercado Livre marks warnings as non-blocking. Keep them for explicit review.
                        // This applies only to validation: creating an item still requires its returned ID.
                        return result;
                    }
                } catch (java.io.IOException ignored) { /* Malformed bodies remain rejected. */ }
            }
            if (Set.of(400, 401, 403, 404, 405, 413, 415, 422, 429).contains(exception.getStatusCode().value())) {
                throw rejected(exception, token);
            }
            throw new MarketplaceIntegrationException("Mercado Livre did not confirm publication; check the account before retrying");
        } catch (RestClientException exception) {
            throw new MarketplaceIntegrationException("Mercado Livre did not confirm publication; check the account before retrying");
        }
    }

    private PublicationValidationException rejected(RestClientResponseException exception, String token) {
        List<String> issues = new ArrayList<>();
        try {
            JsonNode response = objectMapper.readTree(exception.getResponseBodyAsByteArray());
            List<String> warnings = new ArrayList<>();
            for (JsonNode cause : nodes(response.path("cause"))) {
                String code = cause.path("code").asText();
                String message = safeRemoteText(cause.path("message").asText(), token);
                String safeCode = code.matches("[a-zA-Z0-9_.-]{1,150}") ? safeRemoteText(code, token) : "";
                List<String> target = "warning".equals(cause.path("type").asText()) ? warnings : issues;
                // Some Mercado Livre validations only provide a message/references, without a code.
                if (!message.isBlank()) target.add(safeCode.isBlank() ? message : safeCode + ": " + message);
                else if (!safeCode.isBlank()) target.add(safeCode);
            }
            if (issues.isEmpty()) issues.addAll(warnings);
            if (issues.isEmpty()) {
                List<String> fields = new ArrayList<>();
                response.fieldNames().forEachRemaining(fields::add);
                log.warn("Publication rejection has no detailed causes: HTTP {}, fields {}, cause type {}",
                        exception.getStatusCode().value(), fields, response.path("cause").getNodeType());
            }
            String message = safeRemoteText(response.path("message").asText(), token);
            if (issues.isEmpty() && !message.isBlank() && !"Validation error".equalsIgnoreCase(message)) issues.add(message);
            if (issues.isEmpty() && response.path("error").asText().matches("[a-zA-Z0-9_.-]{1,150}")) {
                issues.add(safeRemoteText(response.path("error").asText(), token));
            }
        } catch (Exception ignored) { /* Return a safe fallback for malformed remote errors. */ }
        if (issues.isEmpty()) issues.add("Mercado Livre rejected publication data (HTTP " + exception.getStatusCode().value() + ")");
        return new PublicationValidationException(issues);
    }

    private String safeRemoteText(String text, String token) {
        String safe = token == null || token.isBlank() ? text : text.replace(token, "[REDACTED]");
        safe = safe.replaceAll("(?i)(?:APP_USR|TEST)-[A-Za-z0-9_-]+", "[REDACTED]")
                .replaceAll("(?i)Bearer\\s+[^\\s\"',;]+", "Bearer [REDACTED]")
                .replaceAll("(?i)(access_token|refresh_token|client_secret|authorization)([\\s\"':=]+)[^\\s\"',;}]+", "$1=[REDACTED]")
                .replaceAll("[\\p{Cntrl}]", " ");
        return safe.substring(0, Math.min(600, safe.length())).trim();
    }

    private List<PublicationOptionsDTO.Field> fields(JsonNode response) {
        return nodes(response).stream().filter(n -> !n.path("tags").path("read_only").asBoolean()
                        && (!n.path("tags").path("hidden").asBoolean() || "ITEM_CONDITION".equals(n.path("id").asText())))
                .map(n -> new PublicationOptionsDTO.Field(n.path("id").asText(), n.path("name").asText(),
                        n.path("tags").path("required").asBoolean() || n.path("tags").path("catalog_required").asBoolean()
                                || "ITEM_CONDITION".equals(n.path("id").asText()),
                        n.path("value_type").asText(), nodes(n.path("values")).stream()
                        .map(v -> new PublicationOptionsDTO.Value(v.path("id").asText(), v.path("name").asText())).toList(),
                        nodes(n.path("allowed_units")).stream().map(v -> v.path("id").asText()).toList(),
                        n.path("hint").asText(""))).toList();
    }

    private static List<JsonNode> nodes(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        return StreamSupport.stream(node.spliterator(), false).toList();
    }

    private static PublicationValidationException invalid(String issue) {
        return new PublicationValidationException(List.of(issue));
    }
}
