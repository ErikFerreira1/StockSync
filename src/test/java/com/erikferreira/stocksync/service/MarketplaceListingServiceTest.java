package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.dto.marketplaceListing.MarketplaceListingInsertDTO;
import com.erikferreira.stocksync.entity.MarketplaceListing;
import com.erikferreira.stocksync.entity.Product;
import com.erikferreira.stocksync.entity.SalesChannel;
import com.erikferreira.stocksync.entity.enums.ChannelType;
import com.erikferreira.stocksync.entity.enums.ListingStatus;
import com.erikferreira.stocksync.factory.ProductFactory;
import com.erikferreira.stocksync.repository.MarketplaceListingRepository;
import com.erikferreira.stocksync.service.exceptions.InvalidSalesChannelException;
import com.erikferreira.stocksync.service.exceptions.MarketplaceListingAlreadyExistsException;
import com.erikferreira.stocksync.service.exceptions.MarketplaceListingNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketplaceListingServiceTest {

    @Mock
    private MarketplaceListingRepository repository;
    @Mock
    private ProductService productService;
    @Mock
    private SalesChannelService salesChannelService;
    @InjectMocks
    private MarketplaceListingService service;

    private Product product;
    private SalesChannel channel;
    private MarketplaceListing listing;

    @BeforeEach
    void setUp() {
        product = ProductFactory.createProduct();
        channel = SalesChannel.builder().id(2L).type(ChannelType.MERCADO_LIVRE).active(true).build();
        listing = MarketplaceListing.builder().id(3L).product(product).salesChannel(channel)
                .listingId("MLB123").listingUrl("https://produto.mercadolivre.com.br/MLB123")
                .status(ListingStatus.ACTIVE).build();
    }

    @Test
    void findAllPagedShouldMapListings() {
        var pageable = PageRequest.of(0, 10);
        when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(listing), pageable, 1));
        assertThat(service.findAllPaged(pageable).getContent()).singleElement()
                .satisfies(dto -> assertThat(dto.listingId()).isEqualTo("MLB123"));
    }

    @Test
    void findByIdShouldThrowWhenMissing() {
        when(repository.findById(3L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(3L))
                .isInstanceOf(MarketplaceListingNotFoundException.class);
    }

    @Test
    void insertShouldSaveMercadoLivreListing() {
        var dto = request();
        when(productService.getProductEntityById(1L)).thenReturn(product);
        when(salesChannelService.getSalesChannelEntityById(2L)).thenReturn(channel);
        when(repository.existsBySalesChannelIdAndListingId(2L, "MLB123")).thenReturn(false);
        when(repository.save(any(MarketplaceListing.class))).thenAnswer(invocation -> {
            MarketplaceListing saved = invocation.getArgument(0);
            saved.setId(3L);
            return saved;
        });

        var result = service.insert(dto);

        assertThat(result.id()).isEqualTo(3L);
        assertThat(result.productId()).isEqualTo(1L);
        assertThat(result.salesChannelId()).isEqualTo(2L);
    }

    @Test
    void insertShouldRejectUnsupportedChannel() {
        channel.setType(ChannelType.SHOPEE);
        when(productService.getProductEntityById(1L)).thenReturn(product);
        when(salesChannelService.getSalesChannelEntityById(2L)).thenReturn(channel);

        assertThatThrownBy(() -> service.insert(request()))
                .isInstanceOf(InvalidSalesChannelException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void insertShouldRejectDuplicateListing() {
        when(productService.getProductEntityById(1L)).thenReturn(product);
        when(salesChannelService.getSalesChannelEntityById(2L)).thenReturn(channel);
        when(repository.existsBySalesChannelIdAndListingId(2L, "MLB123")).thenReturn(true);

        assertThatThrownBy(() -> service.insert(request()))
                .isInstanceOf(MarketplaceListingAlreadyExistsException.class);
    }

    @Test
    void getByListingAndChannelShouldReturnEntityOrThrow() {
        when(repository.findByListingIdAndSalesChannelId("MLB123", 2L)).thenReturn(Optional.of(listing));
        assertThat(service.getByListingIdAndSalesChannelId("MLB123", 2L)).isSameAs(listing);

        when(repository.findByListingIdAndSalesChannelId("missing", 2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getByListingIdAndSalesChannelId("missing", 2L))
                .isInstanceOf(MarketplaceListingNotFoundException.class);
    }

    private MarketplaceListingInsertDTO request() {
        return new MarketplaceListingInsertDTO(
                1L, 2L, "MLB123", "https://produto.mercadolivre.com.br/MLB123", ListingStatus.ACTIVE);
    }
}
