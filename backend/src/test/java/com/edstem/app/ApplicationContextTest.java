package com.edstem.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    properties = {
      "spring.liquibase.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "app.catalog.seed.enabled=true"
    })
class ApplicationContextTest {

  private static final String INSERT_PRODUCT =
      "insert into products (id, name, category, price_cents, stock, rating, created_at)"
          + " values (?, 'Test', 'Home', ?, ?, ?, ?)";

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void startsAndAppliesChangesets() {
    Integer applied =
        jdbcTemplate.queryForObject("select count(*) from DATABASECHANGELOG", Integer.class);

    assertThat(applied).isNotNull().isGreaterThanOrEqualTo(4);
  }

  @Test
  void seedsOneHundredProductsAtStartup() {
    Integer products = jdbcTemplate.queryForObject("select count(*) from products", Integer.class);

    assertThat(products).isEqualTo(100);
  }

  @Test
  void theDatabaseRejectsNegativeStock() {
    assertThatThrownBy(() -> insertProduct(100, -1, 4.0))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void theDatabaseRejectsNegativePrice() {
    assertThatThrownBy(() -> insertProduct(-1, 5, 4.0))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void theDatabaseRejectsARatingOutsideZeroToFive() {
    assertThatThrownBy(() -> insertProduct(100, 5, 5.1))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertProduct(100, 5, -0.1))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void insertProduct(long priceCents, int stock, double rating) {
    jdbcTemplate.update(
        INSERT_PRODUCT,
        UUID.randomUUID(),
        priceCents,
        stock,
        rating,
        Timestamp.from(Instant.now()));
  }
}
