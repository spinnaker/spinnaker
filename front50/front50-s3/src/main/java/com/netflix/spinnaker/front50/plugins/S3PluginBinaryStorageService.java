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

import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.netflix.spinnaker.front50.config.S3PluginStorageProperties;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

public class S3PluginBinaryStorageService implements PluginBinaryStorageService {

  private final S3Client s3Client;
  private final S3PluginStorageProperties properties;

  public S3PluginBinaryStorageService(S3Client s3Client, S3PluginStorageProperties properties) {
    this.s3Client = s3Client;
    this.properties = properties;
  }

  @Override
  public void store(@Nonnull String key, @Nonnull byte[] item) {
    String bucket = properties.getBucket();
    String objectKey = buildObjectKey(key);

    try {
      s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(objectKey).build());
      throw new PluginBinaryAlreadyExistsException(key);
    } catch (NoSuchKeyException e) {
      // Object does not exist, so proceed
    } catch (S3Exception e) {
      if (e.statusCode() != 404) {
        throw e;
      }
      // Object does not exist, so proceed
    }

    PutObjectRequest putRequest =
        PutObjectRequest.builder()
            .bucket(properties.getBucket())
            .key(objectKey)
            .contentLength((long) item.length)
            .contentMD5(Base64.getEncoder().encodeToString(Hashing.md5().hashBytes(item).asBytes()))
            .build();

    s3Client.putObject(putRequest, RequestBody.fromBytes(item));
  }

  @Override
  public void delete(@Nonnull String key) {
    s3Client.deleteObject(
        DeleteObjectRequest.builder()
            .bucket(properties.getBucket())
            .key(buildObjectKey(key))
            .build());
  }

  @Nonnull
  @Override
  public List<String> listKeys() {

    ListObjectsV2Request listRequest =
        ListObjectsV2Request.builder()
            .bucket(properties.getBucket())
            .prefix(buildFolder())
            .maxKeys(1000)
            .build();

    List<S3Object> summaries =
        s3Client.listObjectsV2Paginator(listRequest).contents().stream().toList();

    return summaries.stream()
        .map(S3Object::key)
        .filter(k -> k.endsWith(".zip"))
        .collect(Collectors.toList());
  }

  @Nullable
  @Override
  public byte[] load(@Nonnull String key) {
    GetObjectRequest getObjectRequest =
        GetObjectRequest.builder().bucket(properties.getBucket()).key(buildObjectKey(key)).build();
    try (ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getObjectRequest)) {
      return ByteStreams.toByteArray(s3Object);
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        return null;
      }
      throw e;
    } catch (IOException e) {
      throw new SystemException(String.format("Failed to read object contents: %s", key), e);
    }
  }

  private String buildFolder() {
    return (properties.getRootFolder() + "/plugins").replaceAll("//", "/");
  }

  private String buildObjectKey(String key) {
    return (buildFolder() + "/" + key).replaceAll("//", "/");
  }
}
