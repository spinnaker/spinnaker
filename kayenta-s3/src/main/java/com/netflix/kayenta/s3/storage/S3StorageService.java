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

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.HeadBucketRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.aws.security.AwsNamedAccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.constraints.NotNull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Builder
@Slf4j
public class S3StorageService implements StorageService {

  @NotNull
  private ObjectMapper objectMapper;

  @NotNull
  @Singular
  @Getter
  private List<String> accountNames;

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository;

  @Override
  public boolean servicesAccount(String accountName) {
    return accountNames.contains(accountName);
  }

  /**
   * Check to see if the bucket exists, creating it if it is not there.
   */
  public void ensureBucketExists(String accountName) {
    AwsNamedAccountCredentials credentials = (AwsNamedAccountCredentials) accountCredentialsRepository
      .getOne(accountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));

    AmazonS3 amazonS3 = credentials.getAmazonS3();
    String bucket = credentials.getBucket();
    String region = credentials.getRegion();

    HeadBucketRequest request = new HeadBucketRequest(bucket);

    try {
      amazonS3.headBucket(request);
    } catch (AmazonServiceException e) {
      if (e.getStatusCode() == 404) {
        if (com.amazonaws.util.StringUtils.isNullOrEmpty(region)) {
          log.warn("Bucket {} does not exist. Creating it in default region.", bucket);
          amazonS3.createBucket(bucket);
        } else {
          log.warn("Bucket {} does not exist. Creating it in region {}.", bucket, region);
          amazonS3.createBucket(bucket, region);
        }
      } else {
        log.error("Could not create bucket {}: {}", bucket, e);
        throw e;
      }
    }
  }

  @Override
  public <T> T loadObject(String accountName, ObjectType objectType, String objectKey) throws IllegalArgumentException {
    AwsNamedAccountCredentials credentials = (AwsNamedAccountCredentials) accountCredentialsRepository
      .getOne(accountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));
    AmazonS3 amazonS3 = credentials.getAmazonS3();
    String bucket = credentials.getBucket();
    String rootFolder = credentials.getRootFolder();
    String group = objectType.getGroup();
    String path = buildS3Key(rootFolder, group, objectKey, objectType.getDefaultFilename());

    try {
      S3Object s3Object = amazonS3.getObject(bucket, path);

      return deserialize(s3Object, objectType.getTypeReference());
    } catch (AmazonS3Exception e) {
      log.error("Failed to load {} {}: {}", objectType.getGroup(), objectKey, e.getStatusCode());
      if (e.getStatusCode() == 404) {
        throw new IllegalArgumentException("No file at path " + path + ".");
      }
      throw e;
    } catch (IOException e) {
      throw new IllegalStateException("Unable to deserialize object (key: " + objectKey + ")", e);
    }
  }

  private <T> T deserialize(S3Object s3Object, TypeReference typeReference) throws IOException {
    return objectMapper.readValue(s3Object.getObjectContent(), typeReference);
  }

  @Override
  public <T> void storeObject(String accountName, ObjectType objectType, String objectKey, T obj) {
    AwsNamedAccountCredentials credentials = (AwsNamedAccountCredentials) accountCredentialsRepository
      .getOne(accountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));
    AmazonS3 amazonS3 = credentials.getAmazonS3();
    String bucket = credentials.getBucket();
    String rootFolder = credentials.getRootFolder();
    String group = objectType.getGroup();

    ensureBucketExists(accountName);

    try {
      byte[] bytes = objectMapper.writeValueAsBytes(obj);

      ObjectMetadata objectMetadata = new ObjectMetadata();
      objectMetadata.setContentLength(bytes.length);
      objectMetadata.setContentMD5(new String(org.apache.commons.codec.binary.Base64.encodeBase64(DigestUtils.md5(bytes))));

      amazonS3.putObject(
        bucket,
        buildS3Key(rootFolder, group, objectKey, objectType.getDefaultFilename()),
        new ByteArrayInputStream(bytes),
        objectMetadata
      );
    } catch (JsonProcessingException e) {
      log.error("Update failed on path {}: {}", buildTypedFolder(rootFolder, group), e);
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void deleteObject(String accountName, ObjectType objectType, String objectKey) {
    AwsNamedAccountCredentials credentials = (AwsNamedAccountCredentials) accountCredentialsRepository
      .getOne(accountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));
    AmazonS3 amazonS3 = credentials.getAmazonS3();
    String bucket = credentials.getBucket();
    String rootFolder = credentials.getRootFolder();
    String group = objectType.getGroup();
    String path = buildS3Key(rootFolder, group, objectKey, objectType.getDefaultFilename());

    amazonS3.deleteObject(bucket, path);
  }

  @Override
  public List<Map<String, Object>> listObjectKeys(String accountName, ObjectType objectType) {
    AwsNamedAccountCredentials credentials = (AwsNamedAccountCredentials) accountCredentialsRepository
      .getOne(accountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));
    AmazonS3 amazonS3 = credentials.getAmazonS3();
    String bucket = credentials.getBucket();
    String rootFolder = credentials.getRootFolder();
    String group = objectType.getGroup();
    String prefix = buildTypedFolder(rootFolder, group);
    String filename = objectType.getDefaultFilename();

    List<Map<String, Object>> result = new ArrayList<>();

    log.debug("Listing {}", group);

    ObjectListing bucketListing = amazonS3.listObjects(
      new ListObjectsRequest(bucket, prefix, null, null, 10000)
    );

    List<S3ObjectSummary> summaries = bucketListing.getObjectSummaries();

    while (bucketListing.isTruncated()) {
      bucketListing = amazonS3.listNextBatchOfObjects(bucketListing);
      summaries.addAll(bucketListing.getObjectSummaries());
    }

    if (summaries != null) {
      for (S3ObjectSummary summary : summaries) {
        String name = summary.getKey();

        if (name.endsWith(filename)) {
          String localName = buildObjectKey(rootFolder, objectType, name);
          Map<String, Object> objectMetadataMap = new HashMap<String, Object>();
          long updatedTimestamp = summary.getLastModified().getTime();

          objectMetadataMap.put("name", localName);
          objectMetadataMap.put("updatedTimestamp", updatedTimestamp);
          objectMetadataMap.put("updatedTimestampIso", Instant.ofEpochMilli(updatedTimestamp).toString());
          result.add(objectMetadataMap);
        }
      }
    }

    return result;
  }

  private String buildS3Key(String rootFolder, String group, String objectKey, String metadataFilename) {
    if (objectKey.endsWith(metadataFilename)) {
      return objectKey;
    }

    return (buildTypedFolder(rootFolder, group) + "/" + objectKey.toLowerCase() + "/" + metadataFilename).replace("//", "/");
  }

  private String buildObjectKey(String rootFolder, ObjectType objectType, String s3Key) {
    return s3Key
      .replaceAll(buildTypedFolder(rootFolder, objectType.getGroup()) + "/", "")
      .replaceAll("/" + objectType.getDefaultFilename(), "");
  }

  private String buildTypedFolder(String rootFolder, String type) {
    return (rootFolder + "/" + type).replaceAll("//", "/");
  }
}