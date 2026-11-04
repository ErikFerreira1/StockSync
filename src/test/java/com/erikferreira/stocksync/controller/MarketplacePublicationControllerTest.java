package com.erikferreira.stocksync.controller;

import com.erikferreira.stocksync.controller.handler.GlobalExceptionHandler;
import com.erikferreira.stocksync.service.MarketplacePublicationService;
import com.erikferreira.stocksync.dto.marketplaceListing.PublicationValidationResponseDTO;
import com.erikferreira.stocksync.service.exceptions.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class MarketplacePublicationControllerTest {
    @Mock MarketplacePublicationService service;
    MockMvc mvc;
    String valid = """
        {"requestId":"00000000-0000-0000-0000-000000000001","productId":1,"salesChannelId":2,
        "name":"Teclado","categoryId":"MLB123","price":149.90,"listingTypeId":"gold_special",
        "pictures":["https://example.com/photo.jpg"],"attributes":[{"id":"BRAND","valueName":"Marca"}],
        "saleTerms":[],"shippingMode":"me2","freeShipping":false,"localPickUp":false,"description":"Descrição"}
        """;

    @BeforeEach void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new MarketplacePublicationController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void validateShouldReturnWarningsWithoutPublishing() throws Exception {
        when(service.validate(any())).thenReturn(new PublicationValidationResponseDTO(List.of("shipping.me2_adoption_mandatory")));
        mvc.perform(post("/marketplace-publications/validate").contentType("application/json").content(valid))
                .andExpect(status().isOk()).andExpect(jsonPath("$.warnings[0]").value("shipping.me2_adoption_mandatory"));
        verify(service).validate(any());
        verify(service, never()).publish(any());
    }

    @Test void invalidPictureAndPriceShouldBeRejectedBeforeCallingService() throws Exception {
        mvc.perform(post("/marketplace-publications").contentType("application/json")
                .content(valid.replace("https://example.com/photo.jpg", "http://example.com/photo.jpg").replace("149.90", "-1")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray());
        verifyNoInteractions(service);
    }

    @Test void attributeWithoutValueShouldFailBeanValidation() throws Exception {
        mvc.perform(post("/marketplace-publications").contentType("application/json")
                .content(valid.replace(",\"valueName\":\"Marca\"", ""))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void marketplaceRejectionShouldReturnActionableIssues() throws Exception {
        doThrow(new PublicationValidationException(List.of("item.attributes.missing_required [MODEL]"))).when(service).validate(any());
        mvc.perform(post("/marketplace-publications/validate").contentType("application/json").content(valid))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.issues[0]").value("item.attributes.missing_required [MODEL]"));
    }

    @Test void pendingAttemptShouldReturnConflict() throws Exception {
        when(service.publish(any())).thenThrow(new PublicationConflictException("Publication pending"));
        mvc.perform(post("/marketplace-publications").contentType("application/json").content(valid))
                .andExpect(status().isConflict());
    }
}
