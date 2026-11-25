/*
 * Copyright 2023 Armory, Inc.
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

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@ConditionalOnProperty("kayenta.sql.enabled")
@ComponentScan({"com.netflix.kayenta.sql"})
@EntityScan({"com.netflix.kayenta.sql"})
@EnableJpaRepositories(
    basePackages = {"com.netflix.kayenta.sql"},
    transactionManagerRef = SqlConfiguration.JPA_TRANSACTION_MANAGER)
@Import(DataSourceAutoConfiguration.class)
public class SqlConfiguration {

  /**
   * When {@code sql.enabled=true}, kork registers a JDBC-only {@code DataSourceTransactionManager}
   * named {@code transactionManager}. Boot's JPA transaction manager then backs off, and the JPA
   * repositories would resolve kork's by that default name. Their writes would not be flushed.
   * Naming ours explicitly keeps JPA on a {@link JpaTransactionManager} whether or not kork's SQL
   * stack is present, and leaves kork's transaction manager alone for jOOQ.
   */
  static final String JPA_TRANSACTION_MANAGER = "kayentaJpaTransactionManager";

  @Bean(JPA_TRANSACTION_MANAGER)
  public PlatformTransactionManager kayentaJpaTransactionManager(
      EntityManagerFactory entityManagerFactory) {
    return new JpaTransactionManager(entityManagerFactory);
  }
}
