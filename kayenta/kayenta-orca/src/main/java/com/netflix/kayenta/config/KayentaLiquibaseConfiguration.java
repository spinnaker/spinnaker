/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.kayenta.config;

import java.util.Set;
import liquibase.integration.spring.SpringLiquibase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Runs database migrations only when Kayenta owns a SQL schema.
 *
 * <p>When {@code sql.enabled=true}, Kayenta loads Orca's {@code
 * com.netflix.spinnaker.config.SqlConfiguration} through the {@code com.netflix.spinnaker.config}
 * component scan in {@code ApplicationConfiguration}. That configuration imports kork's {@code
 * DefaultSqlConfiguration}, which registers a {@code liquibase} bean. The bean runs {@code
 * classpath:db/changelog-master.yml} and kork's {@code db/healthcheck.yml} against {@code
 * sql.migration}, a datasource that is separate from the pool used to persist executions.
 *
 * <p>Kayenta does not depend on {@code orca-sql-migration}, so Orca's changelog is not on the
 * classpath. {@code db/healthcheck.yml} still is, and the healthcheck table is no longer read at
 * runtime. When Kayenta stores executions in the Orca database and stores canary data elsewhere
 * ({@code kayenta.sql.enabled} is not set), there is nothing for Kayenta to migrate, but kork's
 * bean still runs and can fail with {@code Table 'healthcheck' already exists} on that shared
 * database.
 *
 * <p>kork's bean has no off switch, so this replaces it before initialization:
 *
 * <ul>
 *   <li>{@code kayenta.sql.enabled} is not true: Kayenta owns no schema. {@code liquibase} and
 *       {@code secondaryLiquibase} are replaced with a no-op, and the Orca database is not
 *       migrated.
 *   <li>{@code kayenta.sql.enabled=true}: this configuration does nothing. kork migrates the canary
 *       changelog against {@code sql.migration}, which should point at the Kayenta database.
 *       Execution persistence uses {@code sql.connectionPools} / {@code sql.connectionPool} and is
 *       never a migration target.
 * </ul>
 *
 * <p>Active only when {@code sql.enabled=true}. A {@link BeanPostProcessor} keeps the bean name, so
 * {@code @DependsOn("liquibase")} on kork's {@code dataSource} still resolves, and the no-op path
 * does not open a connection.
 */
@Configuration
@ConditionalOnProperty("sql.enabled")
public class KayentaLiquibaseConfiguration {

  /**
   * Registered as a static {@code @Bean} so the post-processor exists before the {@code liquibase}
   * bean it guards.
   */
  @Bean
  public static BeanPostProcessor kayentaLiquibaseGuard(Environment environment) {
    return new KayentaLiquibaseGuard(environment);
  }

  static class KayentaLiquibaseGuard implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(KayentaLiquibaseGuard.class);
    private static final Set<String> LIQUIBASE_BEAN_NAMES =
        Set.of("liquibase", "secondaryLiquibase");

    private final boolean kayentaSqlEnabled;

    KayentaLiquibaseGuard(Environment environment) {
      this.kayentaSqlEnabled =
          environment.getProperty("kayenta.sql.enabled", Boolean.class, Boolean.FALSE);
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
      if (kayentaSqlEnabled
          || !(bean instanceof SpringLiquibase)
          || !LIQUIBASE_BEAN_NAMES.contains(beanName)) {
        return bean;
      }

      log.info(
          "kayenta.sql.enabled is not true; replacing '{}' with a no-op Liquibase so Kayenta runs "
              + "no database migrations. Set kayenta.sql.enabled=true and point sql.migration at "
              + "the Kayenta database to run canary migrations.",
          beanName);

      SpringLiquibase noop = new SpringLiquibase();
      noop.setShouldRun(false);
      return noop;
    }
  }
}
