package com.erikferreira.stocksync.dto.marketplaceListing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.URL;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PublicationRequestDTO(
        @NotNull UUID requestId,
        @NotNull @Positive Long productId,
        @NotNull @Positive Long salesChannelId,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Pattern(regexp = "MLB[0-9]+") String categoryId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @NotBlank @Pattern(regexp = "[a-z_]+") String listingTypeId,
        @NotEmpty @Size(max = 20) List<@NotBlank @URL(protocol = "https") @Size(max = 2000) String> pictures,
        @NotNull @Size(max = 100) List<@Valid Attribute> attributes,
        @NotNull @Size(max = 30) List<@Valid Attribute> saleTerms,
        @NotBlank @Pattern(regexp = "me2|not_specified") String shippingMode,
        boolean freeShipping,
        boolean localPickUp,
        @Size(max = 50000) String description
) {
    public record Attribute(@NotBlank @Pattern(regexp = "[A-Z0-9_]+") String id,
                            @Size(max = 100) String valueId,
                            @Size(max = 255) String valueName) {
        @AssertTrue(message = "Attribute requires a value ID or name")
        public boolean isValuePresent() {
            return valueId != null && !valueId.isBlank() || valueName != null && !valueName.isBlank();
        }
    }
}
