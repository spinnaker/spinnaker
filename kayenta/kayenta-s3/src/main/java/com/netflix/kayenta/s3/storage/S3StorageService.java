/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.kayenta.s3.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.netflix.kayenta.aws.security.AwsNamedAccountCredentials;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.index.CanaryConfigIndex;
import com.netflix.kayenta.index.config.CanaryConfigIndexAction;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.kayenta.util.Retry;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@Builder
@Slf4j
public class S3StorageService implements StorageService {

  public final int MAX_RETRIES = 10;
  public final long RETRY_BACKOFF = 1000;

  @NotNull private ObjectMapper objectMapper;

  @NotNull @Singular @Getter private List<String> accountNames;

  @Autowired AccountCredentialsRepository accountCredentialsRepository;

  @Autowired CanaryConfigIndex canaryConfigIndex;

  @Override
  public boolean servicesAccount(String accountName) {
    return accountNames.contains(accountName);
  }

  private final Retry retry = new Retry();

  /** Check to see if the bucket exists, creating it if it is not there. */
  public void ensureBucketExists(String accountName) {
    AwsNamedAccountCredentials credentials =
        accountCredentialsRepository.getRequiredOne(accountName);

    S3Client s3Client = credentials.getS3Client();
    String bucket = credentials.getBucket();
    String region = credentials.getRegion();

    try {
      s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
    } catch (NoSuchBucketException e) {
      if (StringUtils.isEmpty(region)) {
        log.warn("Bucket {} does not exist. Creating it in default region.", bucket);
        s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
      } else {
        log.warn("Bucket {} does not exist. Creating it in region {}.", bucket, region);
        s3Client.createBucket(
            CreateBucketRequest.builder()
                .bucket(bucket)
                .createBucketConfiguration(
                    CreateBucketConfiguration.builder()
                        .locationConstraint(BucketLocationConstraint.fromValue(region))
                        .build())
                .build());
      }
    } catch (S3Exception e) {
      log.error("Could not create bucket {}: {}", bucket, e);
      throw e;
    }
  }

  @Override
  public <T> T loadObject(String accountName, ObjectType objectType, String objectKey)
      throws IllegalArgumentException, NotFoundException {
    AwsNamedAccountCredentials credentials =
        accountCredentialsRepository.getRequiredOne(accountName);
    S3Client s3Client = credentials.getS3Client();
    String bucket = credentials.getBucket();
    String path;

    try {
      path = resolveSingularPath(objectType, objectKey, credentials, s3Client, bucket);
    } catch (IllegalArgumentException e) {
      throw new NotFoundException(e.getMessage());
    }

    try {
      var response =
          s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(path).build());
      return deserialize(response, (TypeReference<T>) objectType.getTypeReference());
    } catch (S3Exception e) {
      log.error("Failed to load {} {}: {}", objectType.getGroup(), objectKey, e.statusCode());
      if (e.statusCode() == 404) {
        throw new NotFoundException("No file at path " + path + ".");
      }
      throw e;
    } catch (IOException e) {
      throw new IllegalStateException("Unable to deserialize object (key: " + objectKey + ")", e);
    }
  }

  private String resolveSingularPath(
      ObjectType objectType,
      String objectKey,
      AwsNamedAccountCredentials credentials,
      S3Client s3Client,
      String bucket) {
    String rootFolder = daoRoot(credentials, objectType.getGroup()) + "/" + objectKey;
    ListObjectsV2Response listing =
        s3Client.listObjectsV2(
            ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(rootFolder)
                .maxKeys(10000)
                .build());
    List<S3Object> objects = listing.contents();

    if (objects != null && objects.size() == 1) {
      return objects.get(0).key();
    } else {
      throw new IllegalArgumentException(
          "Unable to resolve singular "
              + objectType
              + " at "
              + daoRoot(credentials, objectType.getGroup())
              + '/'
              + objectKey
              + ".");
    }
  }

  private <T> T deserialize(
      software.amazon.awssdk.core.ResponseInputStream<?> inputStream,
      TypeReference<T> typeReference)
      throws IOException {
    return objectMapper.readValue(inputStream, typeReference);
  }

  @Override
  public <T> void storeObject(
      String accountName,
      ObjectType objectType,
      String objectKey,
      T obj,
      String filename,
      boolean isAnUpdate) {
    AwsNamedAccountCredentials credentials =
        accountCredentialsRepository.getRequiredOne(accountName);
    S3Client s3Client = credentials.getS3Client();
    String bucket = credentials.getBucket();
    String group = objectType.getGroup();
    String path = buildS3Key(credentials, objectType, group, objectKey, filename);

    ensureBucketExists(accountName);

    long updatedTimestamp = -1;
    String correlationId = null;
    String canaryConfigSummaryJson = null;
    final String originalPath;

    if (objectType == ObjectType.CANARY_CONFIG) {
      updatedTimestamp = canaryConfigIndex.getRedisTime();

      CanaryConfig canaryConfig = (CanaryConfig) obj;

      checkForDuplicateCanaryConfig(canaryConfig, objectKey, credentials);

      if (isAnUpdate) {
        originalPath = resolveSingularPath(objectType, objectKey, credentials, s3Client, bucket);
      } else {
        originalPath = null;
      }

      correlationId = UUID.randomUUID().toString();

      Map<String, Object> canaryConfigSummary =
          new ImmutableMap.Builder<String, Object>()
              .put("id", objectKey)
              .put("name", canaryConfig.getName())
              .put("updatedTimestamp", updatedTimestamp)
              .put("updatedTimestampIso", Instant.ofEpochMilli(updatedTimestamp).toString())
              .put("applications", canaryConfig.getApplications())
              .build();

      try {
        canaryConfigSummaryJson = objectMapper.writeValueAsString(canaryConfigSummary);
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException(
            "Problem serializing canaryConfigSummary -> " + canaryConfigSummary, e);
      }

      canaryConfigIndex.startPendingUpdate(
          credentials,
          updatedTimestamp + "",
          CanaryConfigIndexAction.UPDATE,
          correlationId,
          canaryConfigSummaryJson);
    } else {
      originalPath = null;
    }

    try {
      byte[] bytes = objectMapper.writeValueAsBytes(obj);
      String md5 =
          new String(org.apache.commons.codec.binary.Base64.encodeBase64(DigestUtils.md5(bytes)));

      final String finalPath = path;
      retry.retry(
          () ->
              s3Client.putObject(
                  PutObjectRequest.builder()
                      .bucket(bucket)
                      .key(finalPath)
                      .contentLength((long) bytes.length)
                      .contentMD5(md5)
                      .build(),
                  RequestBody.fromBytes(bytes)),
          MAX_RETRIES,
          RETRY_BACKOFF);

      if (objectType == ObjectType.CANARY_CONFIG) {
        if (originalPath != null && !originalPath.equals(path)) {
          retry.retry(
              () ->
                  s3Client.deleteObject(
                      DeleteObjectRequest.builder().bucket(bucket).key(originalPath).build()),
              MAX_RETRIES,
              RETRY_BACKOFF);
        }

        canaryConfigIndex.finishPendingUpdate(
            credentials, CanaryConfigIndexAction.UPDATE, correlationId);
      }
    } catch (Exception e) {
      log.error("Update failed on path {}: {}", buildTypedFolder(credentials, group), e);

      if (objectType == ObjectType.CANARY_CONFIG) {
        canaryConfigIndex.removeFailedPendingUpdate(
            credentials,
            updatedTimestamp + "",
            CanaryConfigIndexAction.UPDATE,
            correlationId,
            canaryConfigSummaryJson);
      }

      throw new IllegalArgumentException(e);
    }
  }

  private void checkForDuplicateCanaryConfig(
      CanaryConfig canaryConfig, String canaryConfigId, AwsNamedAccountCredentials credentials) {
    String canaryConfigName = canaryConfig.getName();
    List<String> applications = canaryConfig.getApplications();
    String existingCanaryConfigId =
        canaryConfigIndex.getIdFromName(credentials, canaryConfigName, applications);

    if (!StringUtils.isEmpty(existingCanaryConfigId)
        && !existingCanaryConfigId.equals(canaryConfigId)) {
      throw new IllegalArgumentException(
          "Canary config with name '"
              + canaryConfigName
              + "' already exists in the scope of applications "
              + applications
              + ".");
    }
  }

  @Override
  public void deleteObject(String accountName, ObjectType objectType, String objectKey) {
    AwsNamedAccountCredentials credentials =
        accountCredentialsRepository.getRequiredOne(accountName);
    S3Client s3Client = credentials.getS3Client();
    String bucket = credentials.getBucket();
    String path = resolveSingularPath(objectType, objectKey, credentials, s3Client, bucket);

    long updatedTimestamp = -1;
    String correlationId = null;
    String canaryConfigSummaryJson = null;

    if (objectType == ObjectType.CANARY_CONFIG) {
      updatedTimestamp = canaryConfigIndex.getRedisTime();

      Map<String, Object> existingCanaryConfigSummary =
          canaryConfigIndex.getSummaryFromId(credentials, objectKey);

      if (existingCanaryConfigSummary != null) {
        String canaryConfigName = (String) existingCanaryConfigSummary.get("name");
        List<String> applications = (List<String>) existingCanaryConfigSummary.get("applications");

        correlationId = UUID.randomUUID().toString();

        Map<String, Object> canaryConfigSummary =
            new ImmutableMap.Builder<String, Object>()
                .put("id", objectKey)
                .put("name", canaryConfigName)
                .put("updatedTimestamp", updatedTimestamp)
                .put("updatedTimestampIso", Instant.ofEpochMilli(updatedTimestamp).toString())
                .put("applications", applications)
                .build();

        try {
          canaryConfigSummaryJson = objectMapper.writeValueAsString(canaryConfigSummary);
        } catch (JsonProcessingException e) {
          throw new IllegalArgumentException(
              "Problem serializing canaryConfigSummary -> " + canaryConfigSummary, e);
        }

        canaryConfigIndex.startPendingUpdate(
            credentials,
            updatedTimestamp + "",
            CanaryConfigIndexAction.DELETE,
            correlationId,
            canaryConfigSummaryJson);
      }
    }

    try {
      final String finalPath = path;
      retry.retry(
          () ->
              s3Client.deleteObject(
                  DeleteObjectRequest.builder().bucket(bucket).key(finalPath).build()),
          MAX_RETRIES,
          RETRY_BACKOFF);

      if (correlationId != null) {
        canaryConfigIndex.finishPendingUpdate(
            credentials, CanaryConfigIndexAction.DELETE, correlationId);
      }
    } catch (Exception e) {
      log.error("Failed to delete path {}: {}", path, e);

      if (correlationId != null) {
        canaryConfigIndex.removeFailedPendingUpdate(
            credentials,
            updatedTimestamp + "",
            CanaryConfigIndexAction.DELETE,
            correlationId,
            canaryConfigSummaryJson);
      }

      throw new IllegalArgumentException(e);
    }
  }

  @Override
  public List<Map<String, Object>> listObjectKeys(
      String accountName, ObjectType objectType, List<String> applications, boolean skipIndex) {
    AwsNamedAccountCredentials credentials =
        accountCredentialsRepository.getRequiredOne(accountName);

    if (!skipIndex && objectType == ObjectType.CANARY_CONFIG) {
      Set<Map<String, Object>> canaryConfigSet =
          canaryConfigIndex.getCanaryConfigSummarySet(credentials, applications);

      return Lists.newArrayList(canaryConfigSet);
    } else {
      S3Client s3Client = credentials.getS3Client();
      String bucket = credentials.getBucket();
      String group = objectType.getGroup();
      String prefix = buildTypedFolder(credentials, group);

      ensureBucketExists(accountName);

      int skipToOffset = prefix.length() + 1;
      List<Map<String, Object>> result = new ArrayList<>();

      log.debug("Listing {}", group);

      List<S3Object> summaries = new ArrayList<>();
      ListObjectsV2Request request =
          ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).maxKeys(10000).build();
      ListObjectsV2Response listing;
      do {
        listing = s3Client.listObjectsV2(request);
        summaries.addAll(listing.contents());
        request = request.toBuilder().continuationToken(listing.nextContinuationToken()).build();
      } while (listing.isTruncated());

      for (S3Object summary : summaries) {
        String itemName = summary.key();
        int indexOfLastSlash = itemName.lastIndexOf("/");
        Map<String, Object> objectMetadataMap = new HashMap<>();
        long updatedTimestamp = summary.lastModified().toEpochMilli();

        objectMetadataMap.put("id", itemName.substring(skipToOffset, indexOfLastSlash));
        objectMetadataMap.put("updatedTimestamp", updatedTimestamp);
        objectMetadataMap.put(
            "updatedTimestampIso", Instant.ofEpochMilli(updatedTimestamp).toString());

        if (objectType == ObjectType.CANARY_CONFIG) {
          String name = itemName.substring(indexOfLastSlash + 1);

          if (name.endsWith(".json")) {
            name = name.substring(0, name.length() - 5);
          }

          objectMetadataMap.put("name", name);
        }

        result.add(objectMetadataMap);
      }

      return result;
    }
  }

  private String daoRoot(AwsNamedAccountCredentials credentials, String daoTypeName) {
    return credentials.getRootFolder() + '/' + daoTypeName;
  }

  private String buildS3Key(
      AwsNamedAccountCredentials credentials,
      ObjectType objectType,
      String group,
      String objectKey,
      String metadataFilename) {
    if (metadataFilename == null) {
      metadataFilename = objectType.getDefaultFilename();
    }

    if (objectKey.endsWith(metadataFilename)) {
      return objectKey;
    }

    return (buildTypedFolder(credentials, group) + "/" + objectKey + "/" + metadataFilename)
        .replace("//", "/");
  }

  private String buildTypedFolder(AwsNamedAccountCredentials credentials, String type) {
    return daoRoot(credentials, type).replaceAll("//", "/");
  }
}
