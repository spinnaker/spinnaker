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

import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactDecorator;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactReferenceURI;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreURIBuilder;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.Base64;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;
import lombok.extern.log4j.Log4j2;
import org.apache.http.HttpStatus;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.GetObjectTaggingResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;

/**
 * S3ArtifactStore will store artifacts in a s3 compatible service
 *
 * <p>Note: It is very important that the S3 bucket has object lock on it to prevent multiple writes
 * {@see https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-lock-overview.html}
 */
@Log4j2
public class S3ArtifactStore extends ArtifactStore {
  private final S3Client s3Client;
  private final PermissionEvaluator permissionEvaluator;
  private final String bucket;
  private final ArtifactStoreURIBuilder uriBuilder;
  private final String applicationsRegex;
  private static final String ENFORCE_PERMS_KEY = "application";

  public S3ArtifactStore(
      S3Client s3Client,
      PermissionEvaluator permissionEvaluator,
      String bucket,
      ArtifactStoreURIBuilder uriBuilder,
      String applicationsRegex) {
    this.s3Client = s3Client;
    this.bucket = bucket;
    this.permissionEvaluator = permissionEvaluator;
    this.uriBuilder = uriBuilder;
    this.applicationsRegex = applicationsRegex;
  }

  /**
   * Will store the artifact using the {@link #s3Client} in some {@link #bucket}
   *
   * <p>This method also persists "permissions" by storing the execution id that made the original
   * store call. In the event a service wants to retrieve said artifact, they will also need to
   * provide the proper execution id
   */
  @Override
  public Artifact store(Artifact artifact) {
    String application = AuthenticatedRequest.getSpinnakerApplication().orElse(null);
    if (application == null) {
      log.warn("failed to retrieve application from request artifact={}", artifact.getName());
      return artifact;
    }

    if (applicationsRegex != null && !Pattern.matches(applicationsRegex, application)) {
      return artifact;
    }

    ArtifactReferenceURI ref = uriBuilder.buildArtifactURI(application, artifact);
    Artifact remoteArtifact =
        artifact.toBuilder()
            .type(ArtifactTypes.REMOTE_BASE64.getMimeType())
            .reference(ref.uri())
            .build();

    if (objectExists(ref)) {
      return remoteArtifact;
    }

    // purpose of tagging is to ensure some sort of identity is persisted to
    // enforce permissions when retrieving the artifact
    Tag accountTag = Tag.builder().key(ENFORCE_PERMS_KEY).value(application).build();

    PutObjectRequest request =
        PutObjectRequest.builder()
            .bucket(bucket)
            .key(ref.paths())
            .tagging(Tagging.builder().tagSet(accountTag).build())
            .build();

    s3Client.putObject(request, RequestBody.fromBytes(getReferenceAsBytes(artifact)));
    return remoteArtifact;
  }

  private byte[] getReferenceAsBytes(Artifact artifact) {
    String reference = artifact.getReference();
    if (reference == null) {
      throw new IllegalArgumentException("reference cannot be null");
    }

    String type = artifact.getType();
    if (type != null && type.endsWith("/base64")) {
      return Base64.getDecoder().decode(reference);
    }

    return reference.getBytes();
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
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    if (tag == null
        || (permissionEvaluator != null
            && !permissionEvaluator.hasPermission(auth, tag.value(), "application", "READ"))) {
      log.error(
          "Could not authenticate to retrieve artifact user={} applicationOfStoredArtifact={}",
          userId,
          (tag == null) ? "(none)" : tag.value());
      throw new AuthenticationServiceException(
          userId + " does not have permission to access this artifact");
    }
  }

  /**
   * Helper method to check whether the object exists. This is not thread safe, nor would it help in
   * a distributed system due to how S3 works (no conditional statements). If preventing multiple
   * writes of the same object is important, another filestore/db needs to be used, possibly
   * dynamodb.
   */
  private boolean objectExists(ArtifactReferenceURI uri) {
    HeadObjectRequest request = HeadObjectRequest.builder().bucket(bucket).key(uri.paths()).build();
    try {
      s3Client.headObject(request);
      log.debug("Artifact exists. No need to store. reference={}", uri.uri());
      return true;
    } catch (NoSuchKeyException e) {
      // pretty gross that we need to use exceptions as control flow, but the
      // java SDK doesn't have any other way of check if an object exists in s3
      log.info("Artifact does not exist reference={}", uri.uri());
      return false;
    } catch (S3Exception e) {
      int statusCode = e.statusCode();
      log.error(
          "Artifact store failed head object request statusCode={} reference={}",
          statusCode,
          uri.uri());

      if (statusCode != 0) {
        // due to this being a HEAD request, there is no message giving a clear
        // indication of what failed. Rather than seeing a useful message back
        // to gate, we instead see just null. To alleviate this, we wrap the
        // exception with a more meaningful message
        throw new SpinnakerException(buildHeadObjectExceptionMessage(e), e);
      }

      throw new SpinnakerException("S3 head object failed", e);
    }
  }

  /**
   * S3's head object can only return 400, 403, and 404, and based on the HTTP status code, we will
   * return the appropriate message back
   */
  private static String buildHeadObjectExceptionMessage(S3Exception e) {
    switch (e.statusCode()) {
      case HttpStatus.SC_FORBIDDEN:
        return "Failed to query artifact due to IAM permissions either on the bucket or object";
      case HttpStatus.SC_BAD_REQUEST:
        return "Failed to query artifact due to invalid request";
      default:
        return String.format("Failed to query artifact: %d", e.statusCode());
    }
  }
}
