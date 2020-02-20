/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.front50.plugins;

import static java.lang.String.format;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.netflix.spinnaker.front50.config.S3PluginStorageProperties;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class S3PluginBinaryStorageService implements PluginBinaryStorageService {

  private final AmazonS3 amazonS3;
  private final S3PluginStorageProperties properties;

  public S3PluginBinaryStorageService(AmazonS3 amazonS3, S3PluginStorageProperties properties) {
    this.amazonS3 = amazonS3;
    this.properties = properties;
  }

  @Override
  public void store(@Nonnull String key, @Nonnull byte[] item) {
    if (amazonS3.doesObjectExist(properties.getBucket(), buildObjectKey(key))) {
      throw new PluginBinaryAlreadyExistsException(key);
    }

    ObjectMetadata metadata = new ObjectMetadata();
    metadata.setContentLength(item.length);
    metadata.setContentMD5(
        Base64.getEncoder().encodeToString(Hashing.md5().hashBytes(item).asBytes()));

    amazonS3.putObject(
        properties.getBucket(), buildObjectKey(key), new ByteArrayInputStream(item), metadata);
  }

  @Override
  public void delete(@Nonnull String key) {
    amazonS3.deleteObject(properties.getBucket(), buildObjectKey(key));
  }

  @Nonnull
  @Override
  public List<String> listKeys() {
    ObjectListing listing =
        amazonS3.listObjects(
            new ListObjectsRequest(properties.getBucket(), buildFolder(), null, null, 1000));
    List<S3ObjectSummary> summaries = listing.getObjectSummaries();

    while (listing.isTruncated()) {
      listing = amazonS3.listNextBatchOfObjects(listing);
      summaries.addAll(listing.getObjectSummaries());
    }

    return summaries.stream()
        .map(S3ObjectSummary::getKey)
        .filter(k -> k.endsWith(".zip"))
        .collect(Collectors.toList());
  }

  @Nullable
  @Override
  public byte[] load(@Nonnull String key) {
    S3Object object;
    try {
      object = amazonS3.getObject(properties.getBucket(), buildObjectKey(key));
    } catch (AmazonS3Exception e) {
      if (e.getStatusCode() == 404) {
        return null;
      }
      throw e;
    }

    try {
      return ByteStreams.toByteArray(object.getObjectContent());
    } catch (IOException e) {
      throw new SystemException(format("Failed to read object contents: %s", key), e);
    }
  }

  private String buildFolder() {
    return (properties.getRootFolder() + "/plugins").replaceAll("//", "/");
  }

  private String buildObjectKey(String key) {
    return (buildFolder() + "/" + key).replaceAll("//", "/");
  }
}
