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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Exercises {@link SqlArtifactStoreStorer}/{@link SqlArtifactStoreGetter} against a real database,
 * since the dialect-specific "insert, ignoring conflicts" branch (onDuplicateKeyIgnore vs
 * onConflictDoNothing) can't be trusted from mocks alone. Concrete MySQL/Postgres subclasses run
 * one container at a time (rather than both simultaneously for the whole class) to avoid exhausting
 * resources on a constrained Docker host.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractSqlArtifactStoreIntegrationTest {
  private SqlTestUtil.TestDatabase db;

  protected abstract SqlTestUtil.TestDatabase createDatabase();

  @BeforeAll
  void setUpDatabase() {
    db = createDatabase();
    SpringLiquibase liquibase = new SpringLiquibase();
    liquibase.setDataSource(db.dataSource);
    liquibase.setChangeLog("classpath:db/changelog-master.yml");
    try {
      liquibase.afterPropertiesSet();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @AfterAll
  void tearDownDatabase() {
    db.close();
  }

  @AfterEach
  void cleanup() {
    SqlTestUtil.cleanupDb(db.context);
  }

  @Test
  void storesAndRetrievesRoundTrip() {
    AuthenticatedRequest.set(Header.APPLICATION, "my-application");
    AuthenticatedRequest.set(Header.USER, "my-user");

    SqlArtifactStoreStorer storer =
        new SqlArtifactStoreStorer(db.context, new ArtifactStoreURISHA256Builder(), null);
    SqlArtifactStoreGetter getter = new SqlArtifactStoreGetter(db.context, null);

    String reference =
        Base64.getEncoder().encodeToString("hello world".getBytes(StandardCharsets.UTF_8));
    Artifact stored =
        storer.store(
            Artifact.builder()
                .type(ArtifactTypes.EMBEDDED_MAP_BASE64.getMimeType())
                .reference(reference)
                .build());

    assertThat(stored.getType()).isEqualTo(ArtifactTypes.REMOTE_BASE64.getMimeType());

    ArtifactReferenceURI uri = ArtifactReferenceURI.parse(stored.getReference());
    Artifact fetched = getter.get(uri);
    assertThat(fetched.getReference()).isEqualTo(reference);
  }

  @Test
  void storingIdenticalContentTwiceCollapsesToOneRow() {
    AuthenticatedRequest.set(Header.APPLICATION, "my-application");
    AuthenticatedRequest.set(Header.USER, "my-user");

    SqlArtifactStoreStorer storer =
        new SqlArtifactStoreStorer(db.context, new ArtifactStoreURISHA256Builder(), null);

    String reference =
        Base64.getEncoder().encodeToString("duplicate content".getBytes(StandardCharsets.UTF_8));
    Artifact artifact =
        Artifact.builder()
            .type(ArtifactTypes.EMBEDDED_MAP_BASE64.getMimeType())
            .reference(reference)
            .build();

    Artifact first = storer.store(artifact);
    Artifact second = storer.store(artifact);

    assertThat(second.getReference()).isEqualTo(first.getReference());

    String hash = ArtifactReferenceURI.parse(first.getReference()).getUriPaths().get(1);
    int rowCount = db.context.selectCount().from(TABLE).where(HASH.eq(hash)).fetchOne(0, int.class);
    assertThat(rowCount).isEqualTo(1);
  }

  @Test
  void sameContentDifferentApplicationsGetsSeparateRows() {
    AuthenticatedRequest.set(Header.USER, "my-user");

    SqlArtifactStoreStorer storer =
        new SqlArtifactStoreStorer(db.context, new ArtifactStoreURISHA256Builder(), null);

    String reference =
        Base64.getEncoder().encodeToString("shared content".getBytes(StandardCharsets.UTF_8));
    Artifact artifact =
        Artifact.builder()
            .type(ArtifactTypes.EMBEDDED_MAP_BASE64.getMimeType())
            .reference(reference)
            .build();

    AuthenticatedRequest.set(Header.APPLICATION, "app-one");
    Artifact storedForAppOne = storer.store(artifact);

    AuthenticatedRequest.set(Header.APPLICATION, "app-two");
    Artifact storedForAppTwo = storer.store(artifact);

    String hash = ArtifactReferenceURI.parse(storedForAppOne.getReference()).getUriPaths().get(1);
    int rowCount = db.context.selectCount().from(TABLE).where(HASH.eq(hash)).fetchOne(0, int.class);
    assertThat(rowCount).isEqualTo(2);
    assertThat(storedForAppOne.getReference()).isNotEqualTo(storedForAppTwo.getReference());
  }
}
