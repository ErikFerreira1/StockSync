package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.dto.inventory.AdjustStockDTO;
import com.erikferreira.stocksync.dto.inventory.StockRequestDTO;
import com.erikferreira.stocksync.dto.stockMovement.StockMovementInsertDTO;
import com.erikferreira.stocksync.dto.stockMovement.StockMovementResponseDTO;
import com.erikferreira.stocksync.entity.Product;
import com.erikferreira.stocksync.entity.StockMovement;
import com.erikferreira.stocksync.entity.enums.MovementType;
import com.erikferreira.stocksync.entity.enums.OriginType;
import com.erikferreira.stocksync.factory.ProductFactory;
import com.erikferreira.stocksync.repository.StockMovementRepository;
import com.erikferreira.stocksync.service.exceptions.InvalidMovementOriginException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockMovementServiceTest {

    @Mock
    private StockMovementRepository repository;
    @Mock
    private ProductService productService;
    @Mock
    private InventoryService inventoryService;
    @InjectMocks
    private StockMovementService service;

    private Product product;

    @BeforeEach
    void setUp() {
        product = ProductFactory.createProduct();
    }

    @ParameterizedTest
    @MethodSource("validMovements")
    void registerMovementShouldSaveAndApplyInventoryChange(
            MovementType type,
            OriginType originType,
            Long originId
    ) {
        StockMovementInsertDTO dto = new StockMovementInsertDTO(1L, 3, type, originType, originId, "test");
        when(productService.getProductEntityById(1L)).thenReturn(product);
        when(repository.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            movement.setId(10L);
            return movement;
        });

        StockMovementResponseDTO result = service.registerMovement(dto);

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.type()).isEqualTo(type);
        verify(repository).save(any(StockMovement.class));
        verifyInventoryAction(type);
    }

    @ParameterizedTest
    @EnumSource(value = MovementType.class, names = {"SALE", "CANCELLATION", "REFUND", "RECONCILIATION_CORRECTION"})
    void manualEndpointShouldRejectSystemMovements(MovementType type) {
        var dto = new StockMovementInsertDTO(1L, 2, type, OriginType.ORDER, 5L, "test");

        assertThatThrownBy(() -> service.registerManualMovement(dto))
                .isInstanceOf(InvalidMovementOriginException.class);
        org.mockito.Mockito.verifyNoInteractions(repository, productService, inventoryService);
    }

    @Test
    void registerMovementShouldRejectSaleWithoutOrderOrigin() {
        StockMovementInsertDTO dto = new StockMovementInsertDTO(
                1L, 2, MovementType.SALE, null, null, null);

        assertThatThrownBy(() -> service.registerMovement(dto))
                .isInstanceOf(InvalidMovementOriginException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void registerMovementShouldRejectManualMovementWithOrigin() {
        StockMovementInsertDTO dto = new StockMovementInsertDTO(
                1L, 2, MovementType.MANUAL_INCREASE, OriginType.ORDER, 5L, null);

        assertThatThrownBy(() -> service.registerMovement(dto))
                .isInstanceOf(InvalidMovementOriginException.class);
    }

    @Test
    void registerMovementShouldRejectReconciliationWithWrongOrigin() {
        StockMovementInsertDTO dto = new StockMovementInsertDTO(
                1L, 2, MovementType.RECONCILIATION_CORRECTION, OriginType.ORDER, 5L, null);

        assertThatThrownBy(() -> service.registerMovement(dto))
                .isInstanceOf(InvalidMovementOriginException.class);
    }

    @Test
    void findHistoryByProductShouldMapPage() {
        StockMovement movement = StockMovement.builder()
                .id(10L)
                .product(product)
                .quantity(2)
                .type(MovementType.MANUAL_INCREASE)
                .build();
        PageRequest pageable = PageRequest.of(0, 10);
        when(repository.findByProductId(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(movement), pageable, 1));

        var result = service.findHistoryByProduct(1L, pageable);

        assertThat(result.getContent()).singleElement()
                .satisfies(dto -> assertThat(dto.id()).isEqualTo(10L));
    }

    private static Stream<Arguments> validMovements() {
        return Stream.of(
                Arguments.of(MovementType.SALE, OriginType.ORDER, 99L),
                Arguments.of(MovementType.CANCELLATION, OriginType.ORDER, 99L),
                Arguments.of(MovementType.REFUND, OriginType.ORDER, 99L),
                Arguments.of(MovementType.MANUAL_INCREASE, null, null),
                Arguments.of(MovementType.MANUAL_DECREASE, null, null),
                Arguments.of(MovementType.RECONCILIATION_CORRECTION, OriginType.RECONCILIATION, null)
        );
    }

    private void verifyInventoryAction(MovementType type) {
        switch (type) {
            case SALE, MANUAL_DECREASE ->
                    verify(inventoryService).decreaseStock(new StockRequestDTO(1L, 3));
            case CANCELLATION, REFUND, MANUAL_INCREASE ->
                    verify(inventoryService).increaseStock(new StockRequestDTO(1L, 3));
            case RECONCILIATION_CORRECTION ->
                    verify(inventoryService).adjustStock(new AdjustStockDTO(1L, 3));
        }
    }
}
