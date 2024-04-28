/*
 * Copyright 2023 Apple Inc.
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
package com.netflix.spinnaker.kork.artifacts.artifactstore.s3;

import static com.netflix.spinnaker.kork.artifacts.artifactstore.s3.S3ArtifactStore.ENFORCE_PERMS_KEY;

import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactDecorator;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactReferenceURI;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreGetter;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.UserPermissionEvaluator;
import java.util.Base64;
import java.util.NoSuchElementException;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.authentication.AuthenticationServiceException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.GetObjectTaggingResponse;
import software.amazon.awssdk.services.s3.model.Tag;

/** Retrieve objects from an s3 compatible service */
@Log4j2
public class S3ArtifactStoreGetter implements ArtifactStoreGetter {
  private final S3Client s3Client;
  private final UserPermissionEvaluator userPermissionEvaluator;
  private final String bucket;

  public S3ArtifactStoreGetter(
      S3Client s3Client, UserPermissionEvaluator userPermissionEvaluator, String bucket) {
    this.s3Client = s3Client;
    this.bucket = bucket;
    this.userPermissionEvaluator = userPermissionEvaluator;
  }

  /**
   * get will return the Artifact with the provided id, and will lastly run the {@link
   * ArtifactDecorator} to further populate the artifact for returning
   */
  @Override
  public Artifact get(ArtifactReferenceURI uri, ArtifactDecorator... decorators) {
    hasAuthorization(
        uri,
        AuthenticatedRequest.getSpinnakerUser()
            .orElseThrow(
                () -> new NoSuchElementException("Could not authenticate due to missing user id")));

    GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(uri.paths()).build();

    ResponseBytes<GetObjectResponse> resp = s3Client.getObjectAsBytes(request);
    Artifact.ArtifactBuilder builder =
        Artifact.builder()
            .type(ArtifactTypes.REMOTE_BASE64.getMimeType())
            .reference(Base64.getEncoder().encodeToString(resp.asByteArray()));

    if (decorators == null) {
      return builder.build();
    }

    for (ArtifactDecorator decorator : decorators) {
      builder = decorator.decorate(builder);
    }

    return builder.build();
  }

  /**
   * hasAuthorization will ensure that the user has proper permissions for retrieving the stored
   * artifact
   *
   * @throws AuthenticationServiceException when user does not have correct permissions
   */
  private void hasAuthorization(ArtifactReferenceURI uri, String userId) {
    GetObjectTaggingRequest request =
        GetObjectTaggingRequest.builder().bucket(bucket).key(uri.paths()).build();

    GetObjectTaggingResponse resp = s3Client.getObjectTagging(request);
    Tag tag =
        resp.tagSet().stream()
            .filter(t -> t.key().equals(ENFORCE_PERMS_KEY))
            .findFirst()
            .orElse(null);

    if (tag == null
        || (userPermissionEvaluator != null
            && !userPermissionEvaluator.hasPermission(
                userId, tag.value(), "application", "READ"))) {
      log.error(
          "Could not authenticate to retrieve artifact user={} applicationOfStoredArtifact={}",
          userId,
          (tag == null) ? "(none)" : tag.value());
      throw new AuthenticationServiceException(
          userId + " does not have permission to access this artifact");
    }
  }
}
