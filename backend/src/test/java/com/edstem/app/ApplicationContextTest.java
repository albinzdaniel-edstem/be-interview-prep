package com.edstem.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    properties = {"spring.liquibase.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
class ApplicationContextTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void startsAndAppliesChangesets() {
    Integer applied =
        jdbcTemplate.queryForObject("select count(*) from DATABASECHANGELOG", Integer.class);

    assertThat(applied).isNotNull();
  }
}
