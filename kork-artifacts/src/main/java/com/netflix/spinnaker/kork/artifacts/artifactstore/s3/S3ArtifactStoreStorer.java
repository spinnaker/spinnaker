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
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactReferenceURI;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreStorer;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreURIBuilder;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.Base64;
import java.util.regex.Pattern;
import lombok.extern.log4j.Log4j2;
import org.apache.http.HttpStatus;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;

/**
 * S3ArtifactStoreStorer will store artifacts in a s3 compatible service
 *
 * <p>Note: It is very important that the S3 bucket has object lock on it to prevent multiple writes
 * {@see https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-lock-overview.html}
 */
@Log4j2
public class S3ArtifactStoreStorer implements ArtifactStoreStorer {
  private final S3Client s3Client;
  private final String bucket;
  private final ArtifactStoreURIBuilder uriBuilder;
  private final Pattern applicationsPattern;

  public S3ArtifactStoreStorer(
      S3Client s3Client,
      String bucket,
      ArtifactStoreURIBuilder uriBuilder,
      String applicationsRegex) {
    this.s3Client = s3Client;
    this.bucket = bucket;
    this.uriBuilder = uriBuilder;
    this.applicationsPattern =
        (applicationsRegex != null)
            ? Pattern.compile(applicationsRegex, Pattern.CASE_INSENSITIVE)
            : null;
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

    if (applicationsPattern != null && !applicationsPattern.matcher(application).matches()) {
      return artifact;
    }

    byte[] referenceBytes;
    try {
      referenceBytes = getReferenceAsBytes(artifact);
    } catch (IllegalArgumentException e) {
      // When this occurs, that means we've run into an embedded/base64 artifact
      // that does not contain base64 in its reference. This can happen a couple
      // of ways with using SpEL within an artifact, manipulating the pipeline
      // JSON directly, etc. So instead of trying to evaluate SpEL here or
      // failing, we will just return the raw artifact, and not store this
      // particular one.
      log.warn("Artifact cannot be stored due to reference not being base64 encoded");
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

    s3Client.putObject(request, RequestBody.fromBytes(referenceBytes));
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
