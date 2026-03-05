/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.front50.model;

import static net.logstash.logback.argument.StructuredArguments.value;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.netflix.spinnaker.front50.api.model.Timestamped;
import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.jackson.mixins.PipelineMixins;
import com.netflix.spinnaker.front50.jackson.mixins.TimestampedMixins;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.VersioningConfiguration;

public class S3StorageService implements StorageService {
  private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

  private final ObjectMapper objectMapper;
  private final S3Client s3Client;
  private final String bucket;
  private final String rootFolder;
  private final Boolean readOnlyMode;
  private final String region;
  private final Boolean versioning;
  private final Integer maxKeys;
  private final ServerSideEncryption serverSideEncryption;

  public S3StorageService(
      ObjectMapper objectMapper,
      S3Client s3Client,
      String bucket,
      String rootFolder,
      Boolean readOnlyMode,
      String region,
      Boolean versioning,
      Integer maxKeys,
      ServerSideEncryption serverSideEncryption) {
    this.objectMapper =
        new ObjectMapper()
            .addMixIn(Timestamped.class, TimestampedMixins.class)
            .addMixIn(Pipeline.class, PipelineMixins.class);
    this.s3Client = s3Client;
    this.bucket = bucket;
    this.rootFolder = rootFolder;
    this.readOnlyMode = readOnlyMode;
    this.region = region;
    this.versioning = versioning;
    this.maxKeys = maxKeys;
    this.serverSideEncryption = serverSideEncryption;
  }

  public void ensureBucketExists() {
    try {
      s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
    } catch (NoSuchBucketException e) {
      CreateBucketRequest.Builder builder = CreateBucketRequest.builder().bucket(bucket);
      if (region != null && !region.isEmpty()) {
        log.info(
            "Creating bucket {} in region {}", value("bucket", bucket), value("region", region));
        builder.createBucketConfiguration(
            CreateBucketConfiguration.builder().locationConstraint(region).build());
      } else {
        log.info("Creating bucket {} in default region", value("bucket", bucket));
      }
      s3Client.createBucket(builder.build());

      if (versioning) {
        log.info("Enabling versioning of the S3 bucket {}", value("bucket", bucket));
        s3Client.putBucketVersioning(
            PutBucketVersioningRequest.builder()
                .bucket(bucket)
                .versioningConfiguration(
                    VersioningConfiguration.builder()
                        .status(BucketVersioningStatus.ENABLED)
                        .build())
                .build());
      }
    }
  }

  @Override
  public boolean supportsVersioning() {
    return versioning;
  }

  @Override
  public <T extends Timestamped> T loadObject(ObjectType objectType, String objectKey)
      throws NotFoundException {
    try {
      GetObjectRequest getObjectRequest =
          GetObjectRequest.builder()
              .bucket(bucket)
              .key(buildS3Key(objectType.group, objectKey, objectType.defaultMetadataFilename))
              .build();
      ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getObjectRequest);
      T item = deserialize(s3Object, (Class<T>) objectType.clazz);
      item.setLastModified(s3Object.response().lastModified().toEpochMilli());
      return item;
    } catch (NoSuchKeyException e) {
      throw new NotFoundException("Object not found (key: " + objectKey + ")");
    } catch (IOException e) {
      throw new IllegalStateException("Unable to deserialize object (key: " + objectKey + ")", e);
    }
  }

  @Override
  public void deleteObject(ObjectType objectType, String objectKey) {
    if (readOnlyMode) {
      throw new ReadOnlyModeException();
    }
    s3Client.deleteObject(
        DeleteObjectRequest.builder()
            .bucket(bucket)
            .key(buildS3Key(objectType.group, objectKey, objectType.defaultMetadataFilename))
            .build());
  }

  public void bulkDeleteObjects(ObjectType objectType, Collection<String> objectKeys) {
    if (readOnlyMode) {
      throw new ReadOnlyModeException();
    }

    // s3 supports bulk delete for a maximum of 1000 object keys
    Lists.partition(new ArrayList<>(objectKeys), 1000)
        .forEach(
            batch -> {
              DeleteObjectsRequest request =
                  DeleteObjectsRequest.builder()
                      .bucket(bucket)
                      .delete(
                          Delete.builder()
                              .objects(
                                  batch.stream()
                                      .map(
                                          k ->
                                              ObjectIdentifier.builder()
                                                  .key(
                                                      buildS3Key(
                                                          objectType.group,
                                                          k,
                                                          objectType.defaultMetadataFilename))
                                                  .build())
                                      .toList())
                              .build())
                      .build();

              s3Client.deleteObjects(request);
            });
  }

  @Override
  public <T extends Timestamped> void storeObject(ObjectType objectType, String objectKey, T item) {
    if (readOnlyMode) {
      throw new ReadOnlyModeException();
    }
    try {
      byte[] bytes = objectMapper.writeValueAsBytes(item);
      String contentMd5 = Base64.getEncoder().encodeToString(DigestUtils.md5(bytes));

      PutObjectRequest.Builder putReqBuilder =
          PutObjectRequest.builder()
              .bucket(bucket)
              .key(buildS3Key(objectType.group, objectKey, objectType.defaultMetadataFilename))
              .contentLength((long) bytes.length)
              .contentMD5(contentMd5);

      setServerSideEncryption(putReqBuilder);

      s3Client.putObject(putReqBuilder.build(), RequestBody.fromBytes(bytes));
      writeLastModified(objectType.group);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public Map<String, Long> listObjectKeys(ObjectType objectType) {
    long startTime = System.currentTimeMillis();
    ListObjectsV2Request listRequest =
        ListObjectsV2Request.builder()
            .bucket(bucket)
            .prefix(buildTypedFolder(rootFolder, objectType.group))
            .maxKeys(maxKeys)
            .build();

    List<S3Object> summaries =
        s3Client.listObjectsV2Paginator(listRequest).contents().stream().toList();

    log.debug(
        "Took {}ms to fetch {} object keys for {}",
        value("fetchTime", (System.currentTimeMillis() - startTime)),
        summaries.size(),
        value("type", objectType));

    return summaries.stream()
        .filter(s -> filterS3ObjectSummary(s, objectType.defaultMetadataFilename))
        .collect(
            Collectors.toMap(
                (s -> buildObjectKey(objectType, s.key())),
                (s -> s.lastModified().toEpochMilli())));
  }

  @Override
  public <T extends Timestamped> Collection<T> listObjectVersions(
      ObjectType objectType, String objectKey, int maxResults) throws NotFoundException {
    if (maxResults == 1) {
      List<T> results = new ArrayList<>();
      results.add(loadObject(objectType, objectKey));
      return results;
    }

    try {
      ListObjectVersionsRequest versionRequest =
          ListObjectVersionsRequest.builder()
              .bucket(bucket)
              .prefix(buildS3Key(objectType.group, objectKey, objectType.defaultMetadataFilename))
              .maxKeys(maxResults)
              .build();
      ListObjectVersionsResponse versionListing = s3Client.listObjectVersions(versionRequest);

      return versionListing.versions().stream()
          .map(
              s3VersionSummary -> {
                try {
                  GetObjectRequest getObjectRequest =
                      GetObjectRequest.builder()
                          .bucket(bucket)
                          .key(
                              buildS3Key(
                                  objectType.group, objectKey, objectType.defaultMetadataFilename))
                          .versionId(s3VersionSummary.versionId())
                          .build();
                  ResponseInputStream<GetObjectResponse> s3Object =
                      s3Client.getObject(getObjectRequest);
                  T item = deserialize(s3Object, (Class<T>) objectType.clazz);
                  item.setLastModified(s3Object.response().lastModified().toEpochMilli());
                  return item;
                } catch (IOException e) {
                  throw new IllegalStateException(e);
                }
              })
          .collect(Collectors.toList());
    } catch (NoSuchKeyException e) {
      throw new NotFoundException(
          String.format("No item found with id of %s", objectKey.toLowerCase()));
    }
  }

  @Override
  public long getLastModified(ObjectType objectType) {
    try {
      GetObjectRequest getObjectRequest =
          GetObjectRequest.builder()
              .bucket(bucket)
              .key(buildTypedFolder(rootFolder, objectType.group) + "/last-modified.json")
              .build();
      ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getObjectRequest);
      Map<String, Long> lastModified = objectMapper.readValue(s3Object, new TypeReference<>() {});
      return lastModified.get("lastModified");
    } catch (Exception e) {
      return 0L;
    }
  }

  @Override
  public long getHealthIntervalMillis() {
    return Duration.ofSeconds(2).toMillis();
  }

  public enum ServerSideEncryption {
    AWSKMS,
    AES256
  }

  private void writeLastModified(String group) {
    if (readOnlyMode) {
      throw new ReadOnlyModeException();
    }
    try {
      byte[] bytes =
          objectMapper.writeValueAsBytes(
              Collections.singletonMap("lastModified", System.currentTimeMillis()));

      String contentMD5 = Base64.getEncoder().encodeToString(DigestUtils.md5(bytes));

      PutObjectRequest.Builder putReqBuilder =
          PutObjectRequest.builder()
              .bucket(bucket)
              .key(buildTypedFolder(rootFolder, group) + "/last-modified.json")
              .contentLength((long) bytes.length)
              .contentMD5(contentMD5);

      setServerSideEncryption(putReqBuilder);

      s3Client.putObject(putReqBuilder.build(), RequestBody.fromBytes(bytes));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private void setServerSideEncryption(PutObjectRequest.Builder putReqBuilder) {
    if (serverSideEncryption != null) {
      if (serverSideEncryption.equals(ServerSideEncryption.AES256)) {
        putReqBuilder.serverSideEncryption(ServerSideEncryption.AES256.name());
      } else if (serverSideEncryption.equals(ServerSideEncryption.AWSKMS)) {
        putReqBuilder.serverSideEncryption(ServerSideEncryption.AWSKMS.name());
      }
    }
  }

  private <T extends Timestamped> T deserialize(
      ResponseInputStream<GetObjectResponse> s3Object, Class<T> clazz) throws IOException {
    return objectMapper.readValue(s3Object, clazz);
  }

  private boolean filterS3ObjectSummary(S3Object s3ObjectSummary, String metadataFilename) {
    return s3ObjectSummary.key().endsWith(metadataFilename);
  }

  private String buildS3Key(String group, String objectKey, String metadataFilename) {
    if (objectKey.endsWith(metadataFilename)) {
      return objectKey;
    }

    return (buildTypedFolder(rootFolder, group)
            + "/"
            + objectKey.toLowerCase()
            + "/"
            + metadataFilename)
        .replace("//", "/");
  }

  private String buildObjectKey(ObjectType objectType, String s3Key) {
    return s3Key
        .replaceAll(buildTypedFolder(rootFolder, objectType.group) + "/", "")
        .replaceAll("/" + objectType.defaultMetadataFilename, "");
  }

  private static String buildTypedFolder(String rootFolder, String type) {
    return (rootFolder + "/" + type).replaceAll("//", "/");
  }
}
