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

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreConfiguration;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreURIBuilder;
import com.netflix.spinnaker.kork.artifacts.artifactstore.NoopArtifactStoreGetter;
import com.netflix.spinnaker.kork.artifacts.artifactstore.NoopArtifactStoreStorer;
import java.util.UUID;
import liquibase.integration.spring.SpringLiquibase;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Boots the real Spring wiring end to end (unlike {@link SqlArtifactStoreStorerTest}/{@link
 * SqlArtifactStoreGetterTest}, which construct those classes directly), so that a wiring mistake in
 * {@link SqlArtifactStoreConfiguration} itself -- e.g. the {@code SpringLiquibase} bean-collision
 * hazard described in its class javadoc -- would actually be caught.
 */
class SqlArtifactStoreConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(UserConfigurations.of(ArtifactStoreConfiguration.class));

  @AfterEach
  void cleanup() {
    // Same rationale as S3ArtifactStoreConfigurationTest: don't leave lingering state for other
    // tests that assume ArtifactStore.instance is null.
    ReflectionTestUtils.setField(ArtifactStore.class, "instance", null);
  }

  private static String[] sqlProperties() {
    String jdbcUrl = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    return new String[] {
      "artifact-store.type=sql",
      "sql.enabled=true",
      "sql.connection-pools.default.jdbc-url=" + jdbcUrl,
      "sql.connection-pools.default.dialect=H2",
      "sql.migration.jdbc-url=" + jdbcUrl,
      "sql.migration.driver=org.h2.Driver",
    };
  }

  @Test
  void testArtifactStoreSqlEnabled() {
    runner
        .withPropertyValues(sqlProperties())
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(ArtifactStoreURIBuilder.class);
              assertThat(ctx).hasSingleBean(ArtifactStore.class);
              assertThat(ctx).hasSingleBean(SqlArtifactStoreGetter.class);
              assertThat(ctx).hasSingleBean(SqlArtifactStoreStorer.class);
              assertThat(ctx).doesNotHaveBean(NoopArtifactStoreGetter.class);
              assertThat(ctx).doesNotHaveBean(NoopArtifactStoreStorer.class);
              assertThat(ctx).hasBean("artifactStoreDataSource");
              assertThat(ctx).hasBean("artifactStoreJooq");

              // The host's own default SpringLiquibase bean must still exist: this is exactly
              // the collision this class's own migration approach (see its javadoc) has to avoid.
              assertThat(ctx).hasSingleBean(SpringLiquibase.class);

              // The migration actually ran: the table this module owns should be queryable.
              DSLContext jooq = ctx.getBean("artifactStoreJooq", DSLContext.class);
              assertThat(jooq.fetchExists(jooq.select().from(DSL.table("artifact_store"))))
                  .isFalse();
            });
  }
}
