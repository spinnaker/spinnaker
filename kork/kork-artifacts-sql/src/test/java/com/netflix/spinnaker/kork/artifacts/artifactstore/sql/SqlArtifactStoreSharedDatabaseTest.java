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

import static com.netflix.spinnaker.kork.artifacts.artifactstore.sql.ArtifactStoreTable.HASH;
import static com.netflix.spinnaker.kork.artifacts.artifactstore.sql.ArtifactStoreTable.TABLE;
import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactReferenceURI;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreURISHA256Builder;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import liquibase.integration.spring.SpringLiquibase;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Entity storage is only useful because more than one service uses it - e.g. clouddriver stores a
 * manifest and orca reads it back later in the same pipeline. For the SQL backend that only works
 * if every participating service is configured to point at the *same* physical database, which
 * means two independent processes, each with their own connection pool, end up doing schema
 * migration and reads/writes against one shared database concurrently.
 *
 * <p>This simulates exactly that: two independent {@link HikariDataSource} pools (standing in for
 * clouddriver's and orca's own separate pools) pointed at one MySQL container, to verify neither
 * Liquibase's migration lock nor concurrent inserts on the shared table cause a real problem.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqlArtifactStoreSharedDatabaseTest {
  private SqlTestUtil.TestDatabase sharedDatabase;
  private HikariDataSource serviceADataSource;
  private HikariDataSource serviceBDataSource;
  private DSLContext serviceAJooq;
  private DSLContext serviceBJooq;

  @BeforeAll
  void setUp() {
    sharedDatabase = SqlTestUtil.initTcMysqlDatabase();
    HikariDataSource underlying = sharedDatabase.dataSource;

    serviceADataSource = poolPointedAt(underlying, "service-a");
    serviceBDataSource = poolPointedAt(underlying, "service-b");
    serviceAJooq = jooq(serviceADataSource);
    serviceBJooq = jooq(serviceBDataSource);
  }

  @AfterAll
  void tearDown() {
    serviceADataSource.close();
    serviceBDataSource.close();
    sharedDatabase.close();
  }

  @AfterEach
  void cleanup() {
    SqlTestUtil.cleanupDb(sharedDatabase.context);
  }

  private static HikariDataSource poolPointedAt(HikariDataSource underlying, String poolName) {
    HikariConfig config = new HikariConfig();
    config.setPoolName(poolName);
    config.setJdbcUrl(underlying.getJdbcUrl());
    config.setUsername(underlying.getUsername());
    config.setPassword(underlying.getPassword());
    config.setMaximumPoolSize(5);
    return new HikariDataSource(config);
  }

  private static DSLContext jooq(HikariDataSource dataSource) {
    return DSL.using(dataSource, SQLDialect.MYSQL);
  }

  private static void migrate(HikariDataSource dataSource) {
    SpringLiquibase liquibase = new SpringLiquibase();
    liquibase.setDataSource(dataSource);
    liquibase.setChangeLog("classpath:db/artifact-store-changelog-master.yml");
    try {
      liquibase.afterPropertiesSet();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void independentPoolsBothSuccessfullyMigrateTheSharedDatabase() {
    // Simulates two services (or two replicas of the same service) each migrating the shared
    // database on their own startup. Liquibase's own DATABASECHANGELOGLOCK/DATABASECHANGELOG
    // tables are what make the second migration here a safe no-op rather than a duplicate-table
    // error, which is exactly what protects real clouddriver/orca deployments where both
    // processes migrate the same shared database independently.
    //
    // This runs the two migrations sequentially rather than on concurrent threads: Liquibase's
    // own Scope mechanism isn't safe to invoke concurrently from two threads in one JVM (a
    // Liquibase-internal issue, confirmed separately), which doesn't reflect the real topology
    // anyway -- clouddriver and orca are always separate JVMs/processes, never two threads
    // racing inside one process.
    migrate(serviceADataSource);
    migrate(serviceBDataSource);

    assertThat(serviceAJooq.fetchExists(serviceAJooq.select().from(DSL.table("artifact_store"))))
        .isFalse();
  }

  @Test
  void artifactStoredByOneServiceIsReadableByAnother() {
    migrate(serviceADataSource);

    AuthenticatedRequest.set(Header.APPLICATION, "my-application");
    AuthenticatedRequest.set(Header.USER, "my-user");

    // "clouddriver" stores it...
    SqlArtifactStoreStorer storerOnServiceA =
        new SqlArtifactStoreStorer(serviceAJooq, new ArtifactStoreURISHA256Builder(), null);
    String reference =
        Base64.getEncoder()
            .encodeToString("cross-service content".getBytes(StandardCharsets.UTF_8));
    Artifact stored =
        storerOnServiceA.store(
            Artifact.builder()
                .type(ArtifactTypes.EMBEDDED_MAP_BASE64.getMimeType())
                .reference(reference)
                .build());

    // ...and "orca", with its own entirely separate connection pool, reads it back.
    SqlArtifactStoreGetter getterOnServiceB = new SqlArtifactStoreGetter(serviceBJooq, null);
    ArtifactReferenceURI uri = ArtifactReferenceURI.parse(stored.getReference());
    Artifact fetched = getterOnServiceB.get(uri);

    assertThat(fetched.getReference()).isEqualTo(reference);
  }

  @Test
  void concurrentIdenticalStoresFromTwoServicesCollapseToOneRow() throws Exception {
    migrate(serviceADataSource);

    AuthenticatedRequest.set(Header.APPLICATION, "my-application");
    AuthenticatedRequest.set(Header.USER, "my-user");

    SqlArtifactStoreStorer storerOnServiceA =
        new SqlArtifactStoreStorer(serviceAJooq, new ArtifactStoreURISHA256Builder(), null);
    SqlArtifactStoreStorer storerOnServiceB =
        new SqlArtifactStoreStorer(serviceBJooq, new ArtifactStoreURISHA256Builder(), null);

    String reference =
        Base64.getEncoder()
            .encodeToString("racing on the same content".getBytes(StandardCharsets.UTF_8));
    Artifact artifact =
        Artifact.builder()
            .type(ArtifactTypes.EMBEDDED_MAP_BASE64.getMimeType())
            .reference(reference)
            .build();

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      // AuthenticatedRequest's application/user are stored in an MDC-backed context that each new
      // thread needs to set for itself.
      List<Callable<Artifact>> stores =
          List.of(
              () -> {
                AuthenticatedRequest.set(Header.APPLICATION, "my-application");
                AuthenticatedRequest.set(Header.USER, "my-user");
                return storerOnServiceA.store(artifact);
              },
              () -> {
                AuthenticatedRequest.set(Header.APPLICATION, "my-application");
                AuthenticatedRequest.set(Header.USER, "my-user");
                return storerOnServiceB.store(artifact);
              });

      List<Future<Artifact>> results = executor.invokeAll(stores, 1, TimeUnit.MINUTES);
      Artifact first = results.get(0).get();
      Artifact second = results.get(1).get();
      assertThat(second.getReference()).isEqualTo(first.getReference());

      String hash = ArtifactReferenceURI.parse(first.getReference()).getUriPaths().get(1);
      int rowCount =
          serviceAJooq.selectCount().from(TABLE).where(HASH.eq(hash)).fetchOne(0, int.class);
      assertThat(rowCount).isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }
}
