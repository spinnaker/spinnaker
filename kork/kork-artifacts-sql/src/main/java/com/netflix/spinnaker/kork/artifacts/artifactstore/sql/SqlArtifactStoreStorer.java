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

import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactDecorator;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactReferenceURI;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreStorer;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreURIBuilder;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.log4j.Log4j2;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.exception.SQLDialectNotSupportedException;

/**
 * SqlArtifactStoreStorer will store artifacts as rows in a SQL database, so operators without (or
 * who don't want) an S3-compatible object store can still use the artifact/entity store.
 *
 * <p>Content is addressed by {@code (application, hash)}, the same identity {@link
 * ArtifactStoreURIBuilder} already builds for the S3 backend, so identical content stored more than
 * once collapses to a single row rather than growing the table unbounded.
 */
@Log4j2
public class SqlArtifactStoreStorer implements ArtifactStoreStorer {
  private final DSLContext jooq;
  private final ArtifactStoreURIBuilder uriBuilder;
  private final Pattern applicationsPattern;

  public SqlArtifactStoreStorer(
      DSLContext jooq, ArtifactStoreURIBuilder uriBuilder, String applicationsRegex) {
    this.jooq = jooq;
    this.uriBuilder = uriBuilder;
    this.applicationsPattern =
        (applicationsRegex != null)
            ? Pattern.compile(applicationsRegex, Pattern.CASE_INSENSITIVE)
            : null;
  }

  @Override
  public Artifact store(Artifact artifact, ArtifactDecorator... decorators) {
    String application = AuthenticatedRequest.getSpinnakerApplication().orElse(null);
    if (application == null) {
      log.warn("failed to retrieve application from request artifact={}", artifact.getName());
      return artifact;
    }

    if (applicationsPattern != null && !applicationsPattern.matcher(application).matches()) {
      return artifact;
    }

    String body;
    try {
      body = getReferenceAsBase64(artifact);
    } catch (IllegalArgumentException e) {
      // Same rationale as the S3 storer: a non-base64 embedded/base64 reference can happen via
      // SpEL manipulation or direct pipeline JSON edits, so skip storage rather than fail.
      log.warn("Artifact cannot be stored due to reference not being base64 encoded");
      return artifact;
    }

    ArtifactReferenceURI ref = uriBuilder.buildArtifactURI(application, artifact);
    List<String> paths = ref.getUriPaths();
    String hash = paths.get(paths.size() - 1);

    Artifact.ArtifactBuilder builder =
        artifact.toBuilder().type(ArtifactTypes.REMOTE_BASE64.getMimeType()).reference(ref.uri());
    for (ArtifactDecorator decorator : decorators) {
      builder = decorator.decorate(builder);
    }
    Artifact remoteArtifact = builder.build();

    try {
      insertIgnoringConflicts(application, hash, body);
    } catch (DataAccessException e) {
      throw new RuntimeException(storeErrorMessage(ref.uri(), e), e);
    }
    return remoteArtifact;
  }

  /**
   * Inserts the row if it doesn't already exist. Content is addressed by (application, hash), so an
   * existing row necessarily has identical content and doesn't need to be overwritten.
   *
   * <p>jOOQ's "ignore duplicates on conflict" syntax isn't portable across dialects (MySQL's
   * onDuplicateKeyIgnore() throws SQLDialectNotSupportedException on Postgres, and vice versa for
   * onConflictDoNothing()), so try one and fall back to the other, mirroring the pattern
   * front50-sql's SqlStorageService already uses for the same reason.
   */
  private void insertIgnoringConflicts(String application, String hash, String body) {
    long createdAt = System.currentTimeMillis();
    try {
      jooq.insertInto(TABLE, APPLICATION, HASH, BODY, CREATED_AT)
          .values(application, hash, body, createdAt)
          .onDuplicateKeyIgnore()
          .execute();
    } catch (SQLDialectNotSupportedException e) {
      jooq.insertInto(TABLE, APPLICATION, HASH, BODY, CREATED_AT)
          .values(application, hash, body, createdAt)
          .onConflictDoNothing()
          .execute();
    }
  }

  private String storeErrorMessage(String uri, Exception e) {
    return String.format("artifact failed to be stored: ref=%s: %s", uri, e.getMessage());
  }

  /**
   * Unlike the S3 storer (which stores raw bytes), rows here store text, so the reference is kept
   * base64-encoded rather than decoded. When the reference isn't already base64 (a non-base64
   * remote-ish artifact type), it's base64-encoded here so the column always holds text.
   */
  private String getReferenceAsBase64(Artifact artifact) {
    String reference = artifact.getReference();
    if (reference == null) {
      throw new IllegalArgumentException("reference cannot be null");
    }

    String type = artifact.getType();
    if (type != null && type.endsWith("/base64")) {
      // Validate it's actually base64 before accepting it, same as the S3 storer.
      Base64.getDecoder().decode(reference);
      return reference;
    }

    return Base64.getEncoder().encodeToString(reference.getBytes(StandardCharsets.UTF_8));
  }
}
