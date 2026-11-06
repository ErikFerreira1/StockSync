package com.erikferreira.stocksync.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class OrderStockDeductionMigrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test void migrationMustRecoverHistoricalDeductionsWithoutChangingStockOrPaymentStatus() {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(source).target("11").load().migrate();
        var jdbc = new JdbcTemplate(source);
        jdbc.update("INSERT INTO sales_channels(id,name,type) VALUES(1,'Migration','OTHER')");
        jdbc.update("INSERT INTO products(id,sku,name,base_price) VALUES(1,'MIGRATION','Product',10)");
        jdbc.update("INSERT INTO inventory(product_id,available_quantity) VALUES(1,7)");
        jdbc.update("INSERT INTO orders(id,sales_channel_id,external_order_id,order_date,status) VALUES "
                + "(1,1,'DEDUCTED',CURRENT_TIMESTAMP,'PENDING'),(2,1,'RESTORED',CURRENT_TIMESTAMP,'CANCELED'),"
                + "(3,1,'NO-MOVEMENT',CURRENT_TIMESTAMP,'PENDING')");
        jdbc.update("INSERT INTO stock_movements(product_id,occurred_at,quantity,type,origin_type,origin_id) VALUES "
                + "(1,CURRENT_TIMESTAMP,3,'SALE','ORDER',1),(1,CURRENT_TIMESTAMP,3,'SALE','ORDER',2),"
                + "(1,CURRENT_TIMESTAMP,3,'CANCELLATION','ORDER',2)");
        Flyway.configure().dataSource(source).load().migrate();
        assertThat(jdbc.queryForObject("SELECT stock_deducted FROM orders WHERE id=1", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT stock_deducted FROM orders WHERE id=2", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT stock_deducted FROM orders WHERE id=3", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=1", String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT available_quantity FROM inventory WHERE product_id=1", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements", Integer.class)).isEqualTo(3);
    }
}
