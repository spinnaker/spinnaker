/*
 * Copyright 2026 Apple Inc.
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
package com.netflix.spinnaker.kork.artifacts.artifactstore.sql;

import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreConfigurationProperties;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreGetter;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreStorer;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreURIBuilder;
import com.netflix.spinnaker.kork.artifacts.artifactstore.entities.EntityStoreConfiguration;
import com.netflix.spinnaker.kork.sql.JooqSqlCommentAppender;
import com.netflix.spinnaker.kork.sql.JooqToSpringExceptionTransformer;
import com.netflix.spinnaker.kork.sql.config.ConnectionPoolProperties;
import com.netflix.spinnaker.kork.sql.config.DataSourceFactory;
import com.netflix.spinnaker.kork.sql.config.DefaultSqlConfiguration;
import com.netflix.spinnaker.kork.sql.config.SqlProperties;
import com.netflix.spinnaker.kork.sql.telemetry.JooqSlowQueryLogger;
import com.netflix.spinnaker.security.UserPermissionEvaluator;
import java.util.Optional;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.jooq.DSLContext;
import org.jooq.impl.DataSourceConnectionProvider;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.DefaultDSLContext;
import org.jooq.impl.DefaultExecuteListenerProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

/**
 * Wires a SQL-backed artifact store, for operators who don't have (or don't want) an S3-compatible
 * object store available. This requires the host service to already have SQL persistence enabled
 * ({@code sql.enabled: true}), since it reuses that setup's connection pools rather than inventing
 * its own.
 *
 * <p>By default the artifact store lives in the host's default connection pool, i.e. the same
 * database as the service's own SQL persistence. Setting {@code artifact-store.sql.connectionPool}
 * to the name of a different pool under {@code sql.connection-pools} stores artifacts in a separate
 * database instead.
 *
 * <p>Schema migrations for the {@code artifact_store} table are self-managed: whichever connection
 * pool was selected gets its own, independently-scoped Liquibase run against a uniquely-named
 * changelog, rather than requiring every host service to add this module's changelog to its own.
 * This runs the migration directly (not as a registered {@link SpringLiquibase} bean): {@link
 * DefaultSqlConfiguration}'s own liquibase bean is
 * {@code @ConditionalOnMissingBean(SpringLiquibase.class)}, so a second bean of that type here --
 * even pointed at a different changelog -- could suppress the host service's own migration
 * depending on configuration-processing order.
 */
@Configuration
@Import({DefaultSqlConfiguration.class, EntityStoreConfiguration.class})
@ConditionalOnProperty(name = "artifact-store.type", havingValue = "sql")
public class SqlArtifactStoreConfiguration {

  @Bean(name = "artifactStoreDataSource")
  public DataSource artifactStoreDataSource(
      ArtifactStoreConfigurationProperties properties,
      SqlProperties sqlProperties,
      DataSourceFactory dataSourceFactory,
      DataSource dataSource,
      @Value("${sql.read-only:false}") boolean sqlReadOnly) {
    String poolName = properties.getSql().getConnectionPool();
    DataSource artifactStoreDataSource;
    if (poolName == null) {
      // No override: reuse the host's own default pool/database.
      artifactStoreDataSource = dataSource;
    } else {
      ConnectionPoolProperties pool = sqlProperties.getConnectionPools().get(poolName);
      if (pool == null) {
        throw new IllegalStateException(
            "artifact-store.sql.connectionPool '"
                + poolName
                + "' is not defined under sql.connection-pools");
      }
      artifactStoreDataSource = dataSourceFactory.build("artifact-store-" + poolName, pool);
    }

    migrate(artifactStoreDataSource, sqlReadOnly);
    return artifactStoreDataSource;
  }

  /**
   * Runs this module's schema migration directly against the resolved DataSource, rather than
   * registering a Spring-managed {@link SpringLiquibase} bean (see class javadoc for why).
   */
  private static void migrate(DataSource dataSource, boolean sqlReadOnly) {
    SpringLiquibase liquibase = new SpringLiquibase();
    liquibase.setDataSource(dataSource);
    // A uniquely-named file, not the conventional "db/changelog-master.yml" every kork-sql-based
    // service already uses for its own migrations: this module now lives on the runtime classpath
    // of services like clouddriver-web/orca-web alongside their own changelog-master.yml, and a
    // single-resource classpath lookup for that generic name would be ambiguous between the two.
    liquibase.setChangeLog("classpath:db/artifact-store-changelog-master.yml");
    liquibase.setShouldRun(!sqlReadOnly);
    try {
      liquibase.afterPropertiesSet();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to run artifact-store schema migration", e);
    }
  }

  @Bean(name = "artifactStoreJooq")
  public DSLContext artifactStoreJooq(
      @Qualifier("artifactStoreDataSource") DataSource artifactStoreDataSource,
      ArtifactStoreConfigurationProperties properties,
      SqlProperties sqlProperties) {
    String poolName = properties.getSql().getConnectionPool();
    ConnectionPoolProperties pool =
        poolName == null
            ? sqlProperties.getDefaultConnectionPoolProperties()
            : sqlProperties.getConnectionPools().get(poolName);

    DataSourceConnectionProvider connectionProvider =
        new DataSourceConnectionProvider(
            new TransactionAwareDataSourceProxy(artifactStoreDataSource));
    DefaultConfiguration configuration = new DefaultConfiguration();
    configuration.set(
        DefaultExecuteListenerProvider.providers(
            new JooqToSpringExceptionTransformer(),
            new JooqSqlCommentAppender(),
            new JooqSlowQueryLogger(1L)));
    configuration.set(connectionProvider);
    configuration.setSQLDialect(pool.getDialect());
    return new DefaultDSLContext(configuration);
  }

  @Bean
  public ArtifactStoreStorer artifactStoreStorer(
      @Qualifier("artifactStoreJooq") DSLContext artifactStoreJooq,
      ArtifactStoreURIBuilder artifactStoreURIBuilder,
      ArtifactStoreConfigurationProperties properties) {
    return new SqlArtifactStoreStorer(
        artifactStoreJooq, artifactStoreURIBuilder, properties.getApplicationsRegex());
  }

  @Bean
  public ArtifactStoreGetter artifactStoreGetter(
      @Qualifier("artifactStoreJooq") DSLContext artifactStoreJooq,
      Optional<UserPermissionEvaluator> userPermissionEvaluator) {
    return new SqlArtifactStoreGetter(artifactStoreJooq, userPermissionEvaluator.orElse(null));
  }
}
