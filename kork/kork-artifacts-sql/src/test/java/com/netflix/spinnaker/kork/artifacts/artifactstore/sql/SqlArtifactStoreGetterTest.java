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

import static com.netflix.spinnaker.kork.artifacts.artifactstore.sql.ArtifactStoreTable.APPLICATION;
import static com.netflix.spinnaker.kork.artifacts.artifactstore.sql.ArtifactStoreTable.BODY;
import static com.netflix.spinnaker.kork.artifacts.artifactstore.sql.ArtifactStoreTable.CREATED_AT;
import static com.netflix.spinnaker.kork.artifacts.artifactstore.sql.ArtifactStoreTable.HASH;
import static com.netflix.spinnaker.kork.artifacts.artifactstore.sql.ArtifactStoreTable.TABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jooq.SQLDialect.H2;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactReferenceURI;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.UserPermissionEvaluator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Uses a real, in-memory H2 database (via {@link SqlTestUtil}, which also runs this module's own
 * Liquibase changelog) rather than mocking jOOQ's fluent select chain directly: RETURNS_DEEP_STUBS
 * can't reliably resolve jOOQ's generic-heavy return types, and a real query is a better test of
 * the actual SQL anyway.
 */
public class SqlArtifactStoreGetterTest {
  private static final AtomicInteger DB_COUNTER = new AtomicInteger();

  private SqlTestUtil.TestDatabase db;

  @AfterEach
  void tearDown() {
    if (db != null) {
      db.close();
    }
  }

  private DSLContext newDatabase() {
    db =
        SqlTestUtil.initDatabase(
            "jdbc:h2:mem:artifactstoregetter" + DB_COUNTER.incrementAndGet() + ";DB_CLOSE_DELAY=-1",
            H2);
    return db.context;
  }

  @Test
  public void testGetAuthenticatedWithUser() {
    String application = "my-application";
    String hash = "abc123";
    String user = "my-user";
    String body = "aGVsbG8gd29ybGQK";
    AuthenticatedRequest.set(Header.USER, user);

    DSLContext jooq = newDatabase();
    jooq.insertInto(TABLE, APPLICATION, HASH, BODY, CREATED_AT)
        .values(application, hash, body, System.currentTimeMillis())
        .execute();

    UserPermissionEvaluator userPermissionEvaluator = mock(UserPermissionEvaluator.class);
    when(userPermissionEvaluator.hasPermission(
            eq(user), eq(application), eq("application"), eq("READ")))
        .thenReturn(true);

    SqlArtifactStoreGetter getter = new SqlArtifactStoreGetter(jooq, userPermissionEvaluator);

    ArtifactReferenceURI uri = mock(ArtifactReferenceURI.class);
    when(uri.getUriPaths()).thenReturn(List.of(application, hash));

    Artifact artifact = getter.get(uri);

    assertThat(artifact).isNotNull();
    assertThat(artifact.getReference()).isEqualTo(body);
    verify(userPermissionEvaluator)
        .hasPermission(eq(user), eq(application), eq("application"), eq("READ"));
  }

  @Test
  public void testGetDeniedThrows() {
    String application = "my-application";
    String hash = "abc123";
    String user = "my-user";
    AuthenticatedRequest.set(Header.USER, user);

    DSLContext jooq = newDatabase();

    UserPermissionEvaluator userPermissionEvaluator = mock(UserPermissionEvaluator.class);
    when(userPermissionEvaluator.hasPermission(
            eq(user), eq(application), eq("application"), eq("READ")))
        .thenReturn(false);

    SqlArtifactStoreGetter getter = new SqlArtifactStoreGetter(jooq, userPermissionEvaluator);

    ArtifactReferenceURI uri = mock(ArtifactReferenceURI.class);
    when(uri.getUriPaths()).thenReturn(List.of(application, hash));

    assertThatThrownBy(() -> getter.get(uri)).isInstanceOf(AuthenticationServiceException.class);
  }

  @Test
  public void testGetNotFoundThrows() {
    String application = "my-application";
    String hash = "abc123";
    String user = "my-user";
    AuthenticatedRequest.set(Header.USER, user);

    DSLContext jooq = newDatabase();

    // No evaluator configured (null): permission check always passes, same as the S3 getter,
    // so the not-found path is what's under test here.
    SqlArtifactStoreGetter getter = new SqlArtifactStoreGetter(jooq, null);

    ArtifactReferenceURI uri = mock(ArtifactReferenceURI.class);
    when(uri.getUriPaths()).thenReturn(List.of(application, hash));

    assertThatThrownBy(() -> getter.get(uri)).isInstanceOf(ResponseStatusException.class);
  }
}
