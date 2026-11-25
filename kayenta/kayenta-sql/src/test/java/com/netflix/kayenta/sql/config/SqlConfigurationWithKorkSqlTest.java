/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.kayenta.sql.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.sql.storage.model.SqlCanaryConfig;
import com.netflix.kayenta.sql.storage.repo.SqlCanaryConfigRepo;
import com.netflix.spinnaker.kork.sql.config.DefaultSqlConfiguration;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Kayenta can run with {@code sql.enabled=true} (Orca's SQL execution repository, built on kork's
 * {@link DefaultSqlConfiguration}) and {@code kayenta.sql.enabled=true} (JPA canary storage) in the
 * same context. kork registers its own {@code DataSource} and a JDBC-only {@code
 * DataSourceTransactionManager}, and both are {@code @ConditionalOnMissingBean} guards for Boot's
 * JPA equivalents. These tests pin down which wiring wins and whether JPA writes still work.
 */
class SqlConfigurationWithKorkSqlTest {

  private static final String H2_URL = "jdbc:h2:mem:kayenta-kork;DB_CLOSE_DELAY=-1";

  @Configuration
  static class ObjectMapperConfig {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withPropertyValues(
              "sql.enabled=true",
              "sql.connectionPools.default.default=true",
              "sql.connectionPools.default.jdbcUrl=" + H2_URL,
              "sql.connectionPools.default.dialect=H2",
              "sql.migration.jdbcUrl=" + H2_URL,
              "sql.migration.dialect=H2",
              "sql.migration.duplicateFileMode=WARN",
              "kayenta.sql.enabled=true",
              "spring.datasource.url=" + H2_URL,
              "spring.jpa.hibernate.ddl-auto=none",
              // DefaultSqlConfiguration enables auto-configuration; this one needs an
              // OkHttpClientProvider that has nothing to do with what is under test.
              "spring.autoconfigure.exclude="
                  + "com.netflix.spinnaker.kork.retrofit.Retrofit2ServiceFactoryAutoConfiguration")
          .withConfiguration(
              AutoConfigurations.of(
                  HibernateJpaAutoConfiguration.class, TransactionAutoConfiguration.class))
          .withConfiguration(
              UserConfigurations.of(
                  ObjectMapperConfig.class, DefaultSqlConfiguration.class, SqlConfiguration.class));

  @Test
  void contextStartsWithOnlyOneDataSource() {
    runner.run(
        ctx -> {
          assertThat(ctx).hasNotFailed();
          assertThat(ctx.getBeansOfType(DataSource.class)).hasSize(1);
        });
  }

  @Test
  void jpaTransactionManagerIsUsedForCanaryStorage() {
    runner.run(
        ctx -> {
          assertThat(ctx).hasNotFailed();
          assertThat(ctx.getBeansOfType(PlatformTransactionManager.class).values())
              .as("JPA repositories need a JpaTransactionManager to bind an EntityManager")
              .anyMatch(tm -> tm instanceof org.springframework.orm.jpa.JpaTransactionManager);
        });
  }

  @Test
  void canSaveAndReadCanaryConfigThroughJpaAlongsideKorkSql() {
    runner.run(
        ctx -> {
          assertThat(ctx).hasNotFailed();
          SqlCanaryConfigRepo repo = ctx.getBean(SqlCanaryConfigRepo.class);

          repo.save(canaryConfig("cfg-1"));

          assertThat(repo.findById("cfg-1")).isPresent();
        });
  }

  /**
   * Control: the same save and read without kork's SQL stack, i.e. how Kayenta's JPA storage ran
   * before Orca's SQL execution repository was an option. Proves the failures above come from the
   * kork wiring and not from this harness.
   */
  @Test
  void canSaveAndReadCanaryConfigThroughJpaWithoutKorkSql() {
    new ApplicationContextRunner()
        .withPropertyValues(
            "kayenta.sql.enabled=true",
            "spring.datasource.url=jdbc:h2:mem:kayenta-control;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto=none",
            "spring.liquibase.change-log=classpath:db/changelog-master.yml")
        .withConfiguration(
            AutoConfigurations.of(
                HibernateJpaAutoConfiguration.class,
                TransactionAutoConfiguration.class,
                LiquibaseAutoConfiguration.class))
        .withConfiguration(UserConfigurations.of(ObjectMapperConfig.class, SqlConfiguration.class))
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              SqlCanaryConfigRepo repo = ctx.getBean(SqlCanaryConfigRepo.class);
              repo.save(canaryConfig("control-1"));
              assertThat(repo.findById("control-1")).isPresent();
            });
  }

  private static SqlCanaryConfig canaryConfig(String id) {
    SqlCanaryConfig config = new SqlCanaryConfig();
    config.setId(id);
    config.setContent("{}");
    config.setCreatedAt(Instant.now());
    config.setUpdatedAt(Instant.now());
    return config;
  }
}
