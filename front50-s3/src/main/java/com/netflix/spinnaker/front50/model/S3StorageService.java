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

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.util.StringUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.netflix.spinnaker.front50.exception.NotFoundException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class S3StorageService implements StorageService {
  private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

  private final ObjectMapper objectMapper;
  private final AmazonS3 amazonS3;
  private final String bucket;
  private final String rootFolder;
  private final Boolean readOnlyMode;
  private final String region;
  private final Boolean versioning;
  private final Integer maxKeys;

  public S3StorageService(
      ObjectMapper objectMapper,
      AmazonS3 amazonS3,
      String bucket,
      String rootFolder,
      Boolean readOnlyMode,
      String region,
      Boolean versioning,
      Integer maxKeys) {
    this.objectMapper = objectMapper;
    this.amazonS3 = amazonS3;
    this.bucket = bucket;
    this.rootFolder = rootFolder;
    this.readOnlyMode = readOnlyMode;
    this.region = region;
    this.versioning = versioning;
    this.maxKeys = maxKeys;
  }

  @Override
  public void ensureBucketExists() {
    HeadBucketRequest request = new HeadBucketRequest(bucket);
    try {
      amazonS3.headBucket(request);
    } catch (AmazonServiceException e) {
      if (e.getStatusCode() == 404) {
        if (StringUtils.isNullOrEmpty(region)) {
          log.info("Creating bucket {} in default region", value("bucket", bucket));
          amazonS3.createBucket(bucket);
        } else {
          log.info(
              "Creating bucket {} in region {}", value("bucket", bucket), value("region", region));
          amazonS3.createBucket(bucket, region);
        }

        if (versioning) {
          log.info("Enabling versioning of the S3 bucket {}", value("bucket", bucket));
          BucketVersioningConfiguration configuration =
              new BucketVersioningConfiguration().withStatus("Enabled");

          SetBucketVersioningConfigurationRequest setBucketVersioningConfigurationRequest =
              new SetBucketVersioningConfigurationRequest(bucket, configuration);

          amazonS3.setBucketVersioningConfiguration(setBucketVersioningConfigurationRequest);
        }

      } else {
        throw e;
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
      S3Object s3Object =
          amazonS3.getObject(
              bucket, buildS3Key(objectType.group, objectKey, objectType.defaultMetadataFilename));
      T item = deserialize(s3Object, (Class<T>) objectType.clazz);
      item.setLastModified(s3Object.getObjectMetadata().getLastModified().getTime());
      return item;
    } catch (AmazonS3Exception e) {
      if (e.getStatusCode() == 404) {
        throw new NotFoundException("Object not found (key: " + objectKey + ")");
      }
      throw e;
    } catch (IOException e) {
      throw new IllegalStateException("Unable to deserialize object (key: " + objectKey + ")", e);
    }
  }

  @Override
  public void deleteObject(ObjectType objectType, String objectKey) {
    if (readOnlyMode) {
      throw new ReadOnlyModeException();
    }
    amazonS3.deleteObject(
        bucket, buildS3Key(objectType.group, objectKey, objectType.defaultMetadataFilename));
    writeLastModified(objectType.group);
  }

  public void bulkDeleteObjects(ObjectType objectType, Collection<String> objectKeys) {
    if (readOnlyMode) {
      throw new ReadOnlyModeException();
    }

    // s3 supports bulk delete for a maximum of 1000 object keys
    Lists.partition(new ArrayList<>(objectKeys), 1000)
        .forEach(
            keys -> {
              amazonS3.deleteObjects(
                  new DeleteObjectsRequest(bucket)
                      .withKeys(
                          keys.stream()
                              .map(
                                  k ->
                                      new DeleteObjectsRequest.KeyVersion(
                                          buildS3Key(
                                              objectType.group,
                                              k,
                                              objectType.defaultMetadataFilename)))
                              .collect(Collectors.toList())));
            });
  }

  @Override
  public <T extends Timestamped> void storeObject(ObjectType objectType, String objectKey, T item) {
    if (readOnlyMode) {
      throw new ReadOnlyModeException();
    }
    try {
      byte[] bytes = objectMapper.writeValueAsBytes(item);

      ObjectMetadata objectMetadata = new ObjectMetadata();
      objectMetadata.setContentLength(bytes.length);
      objectMetadata.setContentMD5(
          new String(org.apache.commons.codec.binary.Base64.encodeBase64(DigestUtils.md5(bytes))));

      amazonS3.putObject(
          bucket,
          buildS3Key(objectType.group, objectKey, objectType.defaultMetadataFilename),
          new ByteArrayInputStream(bytes),
          objectMetadata);
      writeLastModified(objectType.group);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public Map<String, Long> listObjectKeys(ObjectType objectType) {
    long startTime = System.currentTimeMillis();
    ObjectListing bucketListing =
        amazonS3.listObjects(
            new ListObjectsRequest(
                bucket, buildTypedFolder(rootFolder, objectType.group), null, null, maxKeys));
    List<S3ObjectSummary> summaries = bucketListing.getObjectSummaries();

    while (bucketListing.isTruncated()) {
      bucketListing = amazonS3.listNextBatchOfObjects(bucketListing);
      summaries.addAll(bucketListing.getObjectSummaries());
    }

    log.debug(
        "Took {}ms to fetch {} object keys for {}",
        value("fetchTime", (System.currentTimeMillis() - startTime)),
        summaries.size(),
        value("type", objectType));

    return summaries.stream()
        .filter(s -> filterS3ObjectSummary(s, objectType.defaultMetadataFilename))
        .collect(
            Collectors.toMap(
                (s -> buildObjectKey(objectType, s.getKey())),
                (s -> s.getLastModified().getTime())));
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
      VersionListing versionListing =
          amazonS3.listVersions(
              new ListVersionsRequest(
                  bucket,
                  buildS3Key(objectType.group, objectKey, objectType.defaultMetadataFilename),
                  null,
                  null,
                  null,
                  maxResults));
      return versionListing.getVersionSummaries().stream()
          .map(
              s3VersionSummary -> {
                try {
                  S3Object s3Object =
                      amazonS3.getObject(
                          new GetObjectRequest(
                              bucket,
                              buildS3Key(
                                  objectType.group, objectKey, objectType.defaultMetadataFilename),
                              s3VersionSummary.getVersionId()));
                  T item = deserialize(s3Object, (Class<T>) objectType.clazz);
                  item.setLastModified(s3Object.getObjectMetadata().getLastModified().getTime());
                  return item;
                } catch (IOException e) {
                  throw new IllegalStateException(e);
                }
              })
          .collect(Collectors.toList());
    } catch (AmazonS3Exception e) {
      if (e.getStatusCode() == 404) {
        throw new NotFoundException(
            String.format("No item found with id of %s", objectKey.toLowerCase()));
      }

      throw e;
    }
  }

  @Override
  public long getLastModified(ObjectType objectType) {
    try {
      Map<String, Long> lastModified =
          objectMapper.readValue(
              amazonS3
                  .getObject(
                      bucket,
                      buildTypedFolder(rootFolder, objectType.group) + "/last-modified.json")
                  .getObjectContent(),
              Map.class);

      return lastModified.get("lastModified");
    } catch (Exception e) {
      return 0L;
    }
  }

  @Override
  public long getHealthIntervalMillis() {
    return Duration.ofSeconds(2).toMillis();
  }

  private void writeLastModified(String group) {
    if (readOnlyMode) {
      throw new ReadOnlyModeException();
    }
    try {
      byte[] bytes =
          objectMapper.writeValueAsBytes(
              Collections.singletonMap("lastModified", System.currentTimeMillis()));

      ObjectMetadata objectMetadata = new ObjectMetadata();
      objectMetadata.setContentLength(bytes.length);
      objectMetadata.setContentMD5(
          new String(org.apache.commons.codec.binary.Base64.encodeBase64(DigestUtils.md5(bytes))));

      amazonS3.putObject(
          bucket,
          buildTypedFolder(rootFolder, group) + "/last-modified.json",
          new ByteArrayInputStream(bytes),
          objectMetadata);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private <T extends Timestamped> T deserialize(S3Object s3Object, Class<T> clazz)
      throws IOException {
    return objectMapper.readValue(s3Object.getObjectContent(), clazz);
  }

  private boolean filterS3ObjectSummary(S3ObjectSummary s3ObjectSummary, String metadataFilename) {
    return s3ObjectSummary.getKey().endsWith(metadataFilename);
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
