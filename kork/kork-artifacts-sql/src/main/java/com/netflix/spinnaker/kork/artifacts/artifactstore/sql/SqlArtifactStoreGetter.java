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
import static com.netflix.spinnaker.kork.artifacts.artifactstore.sql.ArtifactStoreTable.HASH;
import static com.netflix.spinnaker.kork.artifacts.artifactstore.sql.ArtifactStoreTable.TABLE;

import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactDecorator;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactReferenceURI;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreGetter;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.UserPermissionEvaluator;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.extern.log4j.Log4j2;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.web.server.ResponseStatusException;

/** Retrieves artifacts stored as rows by {@link SqlArtifactStoreStorer}. */
@Log4j2
public class SqlArtifactStoreGetter implements ArtifactStoreGetter {
  private final DSLContext jooq;
  private final UserPermissionEvaluator userPermissionEvaluator;

  public SqlArtifactStoreGetter(DSLContext jooq, UserPermissionEvaluator userPermissionEvaluator) {
    this.jooq = jooq;
    this.userPermissionEvaluator = userPermissionEvaluator;
  }

  @Override
  public Artifact get(ArtifactReferenceURI uri, ArtifactDecorator... decorators) {
    List<String> paths = uri.getUriPaths();
    String application = paths.get(0);
    String hash = paths.get(paths.size() - 1);

    hasAuthorization(
        application,
        AuthenticatedRequest.getSpinnakerUser()
            .orElseThrow(
                () -> new NoSuchElementException("Could not authenticate due to missing user id")));

    log.debug("Attempting to get artifact reference={} hash={}", uri.uri(), hash);
    Record record =
        jooq.select(BODY)
            .from(TABLE)
            .where(APPLICATION.eq(application).and(HASH.eq(hash)))
            .fetchOne();

    if (record == null) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "artifact not found: ref=" + uri.uri());
    }

    Artifact.ArtifactBuilder builder =
        Artifact.builder()
            .type(ArtifactTypes.REMOTE_BASE64.getMimeType())
            .reference(record.get(BODY));

    if (decorators != null) {
      for (ArtifactDecorator decorator : decorators) {
        builder = decorator.decorate(builder);
      }
    }

    return builder.build();
  }

  /**
   * Mirrors {@code S3ArtifactStoreGetter.hasAuthorization}, but reads the application directly off
   * the row's own column rather than needing a separate round trip (S3 needs a second
   * GetObjectTagging call to read the same identity).
   */
  private void hasAuthorization(String application, String userId) {
    if (userPermissionEvaluator != null
        && !userPermissionEvaluator.hasPermission(userId, application, "application", "READ")) {
      log.error(
          "Could not authenticate to retrieve artifact user={} applicationOfStoredArtifact={}",
          userId,
          application);
      throw new AuthenticationServiceException(
          userId + " does not have permission to access this artifact");
    }
  }
}
