package com.erikferreira.stocksync.integration;

import com.erikferreira.stocksync.dto.product.ProductInsertDTO;
import com.erikferreira.stocksync.service.ProductService;
import com.erikferreira.stocksync.support.PostgreSQLIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.test.timezone-regression=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TimestampTimezoneIntegrationTest extends PostgreSQLIntegrationTest {

    private static final ZoneId RECIFE = ZoneId.of("America/Recife");
    private static final TimeZone ORIGINAL_TIME_ZONE = TimeZone.getDefault();

    static {
        TimeZone.setDefault(TimeZone.getTimeZone(RECIFE));
    }

    @Autowired
    private ProductService productService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterAll
    static void restoreDefaultTimeZone() {
        TimeZone.setDefault(ORIGINAL_TIME_ZONE);
    }

    @Test
    void inventoryTimestampShouldPreserveInstantAcrossDifferentTimeZones() throws Exception {
        String serverDefaultTimeZone = POSTGRESQL.execInContainer(
                "psql",
                "-U", POSTGRESQL.getUsername(),
                "-d", POSTGRESQL.getDatabaseName(),
                "-tAc", "SHOW TIMEZONE"
        ).getStdout().trim();
        String jdbcSessionTimeZone = jdbcTemplate.queryForObject("SHOW TIMEZONE", String.class);
        String columnType = jdbcTemplate.queryForObject("""
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'inventory'
                  AND column_name = 'updated_at'
                """, String.class);

        Instant beforeInsert = Instant.now();
        var product = productService.insert(new ProductInsertDTO(
                "SKU-TIMEZONE-" + System.nanoTime(),
                "Timezone product",
                null,
                BigDecimal.TEN,
                1,
                0
        ));
        Instant afterInsert = Instant.now();

        OffsetDateTime storedTimestamp = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM inventory WHERE product_id = ?",
                OffsetDateTime.class,
                product.id()
        );

        assertThat(serverDefaultTimeZone).containsIgnoringCase("UTC");
        assertThat(jdbcSessionTimeZone).isEqualTo("America/Recife");
        assertThat(columnType).isEqualTo("timestamp with time zone");
        assertThat(storedTimestamp.toInstant())
                .isBetween(beforeInsert.minusSeconds(1), afterInsert.plusSeconds(1));
    }
}
