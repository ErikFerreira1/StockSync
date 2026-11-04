package com.erikferreira.stocksync.dto.marketplaceListing;

import java.util.List;

public record PublicationOptionsDTO(String categoryId, String categoryName, boolean userProducts,
        int maxNameLength, int maxPictures, List<Field> attributes, List<Field> saleTerms,
        List<Value> listingTypes, List<String> shippingModes, boolean localPickUpAllowed) {
    public record Value(String id, String name) {}
    public record Field(String id, String name, boolean required, String valueType,
                        List<Value> values, List<String> units, String hint) {}
}
